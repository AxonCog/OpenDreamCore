package com.opendreamcore.legacy;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.NoSuchElementException;

/**
 * 原版协议桥：1.7.10 的 Forge 客户端在插件服务器上收不到任何非原版的
 * plugin message——NetHandlerPlayClient 只认 MC| 那几个内置通道，其余
 * 直接丢，FML 通道又只在 Forge 服务器上存在。跟 1.12.2 一样往下沉一层，
 * 在 Netty 管线里自己截包：packet_handler（NetworkManager）前面挂个入站
 * 监听，S3FPacketCustomPayload 抓下来自己分，不再让它往上走。
 *
 * 跟 1.12.2 比，1.7.10 的 NetworkManager 抠门得多：channel 字段 private、
 * 没有公共 sendPacket、主线程调度叫 func_152344_a，下行包叫
 * S3FPacketCustomPayload、上行叫 C17PacketCustomPayload。发送走
 * getNetHandler().addToSendQueue，管线要从 channel 字段里捞（反射一把，
 * 启动期捞一次缓存住）。
 */
public final class VanillaBridge1710 {

    /** 与插件端约定好的通道名，13 字符，1.13 前的 20 字符限制内。 */
    public static final String CHANNEL = "opendreamcore";

    /** 管线里的钩子名，带后缀防跟其他 mod 的钩子撞名。 */
    private static final String HANDLER_NAME = "odc_bridge_1710_"
            + Integer.toHexString(VanillaBridge1710.class.hashCode());

    /** NetworkManager.channel 是 private，反射捞一次缓存住。 */
    private static volatile Field channelField;

    /** 下行分片重组：一条连接一个重组器（分片按帧头 transferId 聚合）。 */
    private static final com.opendreamcore.protocol.LegacyFraming.Assembler ASSEMBLER =
            new com.opendreamcore.protocol.LegacyFraming.Assembler();

    private VanillaBridge1710() {
    }

    /**
     * 进服后调用：往当前连接的 Netty 管线插入解码钩子。管线每次进服
     * 都是新的，所以每条连接都要挂一遍；已挂过（按名字查）就跳过。
     */
    public static void install() {
        Channel channel = connection();
        if (channel == null) {
            return;
        }
        if (channel.pipeline().get(HANDLER_NAME) != null) {
            return;
        }
        try {
            channel.pipeline().addBefore("packet_handler", HANDLER_NAME, new Inbound());
            OdcLegacy1710.LOGGER.info("[ODC] 解码钩子已挂进管线");
        } catch (NoSuchElementException e) {
            // 连接正在拆管线，下个 tick 会再试
        }
    }

    /** 当前连接的 Netty channel；没进服返回 null。 */
    private static Channel connection() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
        if (mc.getNetHandler() == null || mc.theWorld == null) {
            return null;
        }
        return managerChannel(mc.getNetHandler().getNetworkManager());
    }

    /** 反射捞 NetworkManager.channel，捞一次缓存住。 */
    private static Channel managerChannel(net.minecraft.network.NetworkManager manager) {
        Field f = channelField;
        if (f == null) {
            try {
                f = net.minecraft.network.NetworkManager.class.getDeclaredField("channel");
                f.setAccessible(true);
                channelField = f;
            } catch (ReflectiveOperationException e) {
                OdcLegacy1710.LOGGER.warn("[ODC] 拿不到 NetworkManager.channel: {}", e.toString());
                return null;
            }
        }
        try {
            return (Channel) f.get(manager);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    /** 入站钩子：只关心 opendreamcore 通道的 custom payload，其余照常放行。 */
    private static class Inbound extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (!(msg instanceof net.minecraft.network.play.server.S3FPacketCustomPayload)) {
                ctx.fireChannelRead(msg);
                return;
            }
            net.minecraft.network.play.server.S3FPacketCustomPayload packet =
                    (net.minecraft.network.play.server.S3FPacketCustomPayload) msg;
            if (!CHANNEL.equals(packet.func_149169_c())) {
                ctx.fireChannelRead(msg);
                return;
            }
            // 自己吃掉，不往上传（上层会把不认识的通道丢掉）
            try {
                byte[] raw = packet.func_149168_d();
                com.opendreamcore.protocol.LegacyFraming.Frame frame =
                        com.opendreamcore.protocol.LegacyFraming.parse(raw);
                if (frame == null) {
                    OdcLegacy1710.LOGGER.warn("[ODC] 协议帧解析失败（{} 字节）", raw.length);
                    return;
                }
                byte[] assembled = ASSEMBLER.offer(frame);
                if (assembled == null) {
                    return; // 分片未齐
                }
                final String path = frame.path;
                // Netty 线程收到，甩回主线程再分发
                net.minecraft.client.Minecraft.getMinecraft().func_152344_a(() ->
                        ClientHooks1710.onServerMessage(path, assembled));
            } catch (Exception e) {
                OdcLegacy1710.LOGGER.warn("[ODC] 协议载荷解析失败: {}", e.toString());
            }
        }
    }

    // 服务端能不能往这条通道发包，取决于客户端有没有报备过（REGISTER）。
    // Bukkit 那边 addChannel 只认客户端主动声明，服务端单方面发是一堆死包。
    // 通道名是 REGISTER——跟 1.12.2 一样是反编译 PlayerConnection 才确认的，
    // 那边只认 REGISTER/UNREGISTER 两个大写名
    public static void registerChannels() {
        net.minecraft.client.network.NetHandlerPlayClient handler =
                net.minecraft.client.Minecraft.getMinecraft().getNetHandler();
        if (handler == null) {
            return;
        }
        // Bukkit 按 \0 分隔多通道，尾部补一个零保安全（单通道也不吃亏）
        byte[] name = CHANNEL.getBytes(StandardCharsets.UTF_8);
        byte[] payload = new byte[name.length + 1];
        System.arraycopy(name, 0, payload, 0, name.length);
        handler.addToSendQueue(new net.minecraft.network.play.client.C17PacketCustomPayload(
                "REGISTER", payload));
        OdcLegacy1710.LOGGER.info("[ODC] 已向服务器报备通道 {}（听不听得见看插件日志）", CHANNEL);
    }

    /**
     * 上行发送：path+data 走共享分片帧（大件自动切，单包装下就是一帧），
     * 直接 C17PacketCustomPayload 塞进网络管线，vanilla 侧收到后转给
     * Bukkit messenger。没进服就丢弃。
     */
    public static void sendToServer(String path, byte[] data) {
        net.minecraft.client.network.NetHandlerPlayClient handler =
                net.minecraft.client.Minecraft.getMinecraft().getNetHandler();
        if (handler == null) {
            return;
        }
        for (byte[] frame : com.opendreamcore.protocol.LegacyFraming.frames(path, data)) {
            handler.addToSendQueue(new net.minecraft.network.play.client.C17PacketCustomPayload(
                    CHANNEL, frame));
        }
    }
}
