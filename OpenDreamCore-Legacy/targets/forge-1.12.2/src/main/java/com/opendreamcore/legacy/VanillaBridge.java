package com.opendreamcore.legacy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.CPacketCustomPayload;
import net.minecraft.network.play.server.SPacketCustomPayload;

import java.nio.charset.StandardCharsets;
import java.util.NoSuchElementException;

/**
 * 原版协议桥：1.12.2 的 Forge 客户端在插件服务器上收不到任何非原版的
 * plugin message——NetHandlerPlayClient.handleCustomPayload 只认 MC| 前缀
 * 那几个，其余直接 return 丢弃（反编译证实），FML 通道又只在 Forge 服务器
 * 上存在。要跟插件服务器通信只能往下沉一层，在 Netty 管线里自己截包：
 * packet_handler（NetworkManager）前面挂个入站监听，SPacketCustomPayload
 * 抓下来自己分，不再让它往上走。
 *
 * 通道名只有一个 opendreamcore（老服通道名上限 20 字符），消息类型
 * （path）骑在载荷里：[int pathLen][path][int dataLen][data]，跟插件端
 * 老服模式同一段编码。管线布局是 timeout→splitter→decoder→prepender→
 * encoder→packet_handler，decoder 之后已是解码好的 Packet 对象，钩子
 * 挂在 packet_handler 前正好收到 SPacketCustomPayload。
 */
public final class VanillaBridge {

    /** 与插件端约定好的通道名，13 字符，1.13 前的 20 字符限制内。 */
    public static final String CHANNEL = "opendreamcore";

    /** 管线里的钩子名，带随机后缀防跟其他 mod 的钩子撞名。 */
    private static final String HANDLER_NAME = "odc_bridge_" + Integer.toHexString(VanillaBridge.class.hashCode());

    /** 下行分片重组：一条连接一个重组器（分片按帧头 transferId 聚合）。 */
    private static final com.opendreamcore.protocol.LegacyFraming.Assembler ASSEMBLER =
            new com.opendreamcore.protocol.LegacyFraming.Assembler();

    private VanillaBridge() {
    }

    /**
     * 进服后调用：往当前连接的 Netty 管线插入解码钩子。管线每次进服
     * 都是新的，所以每条连接都要挂一遍；已挂过（按名字查）就跳过。
     */
    public static void install() {
        NetworkManager manager = connection();
        if (manager == null || manager.isLocalChannel()) {
            return;
        }
        if (manager.channel() == null || manager.channel().pipeline().get(HANDLER_NAME) != null) {
            return;
        }
        try {
            manager.channel().pipeline().addBefore("packet_handler", HANDLER_NAME, new Inbound());
            OdcLegacy.LOGGER.info("[ODC] 解码钩子已挂进管线");
        } catch (NoSuchElementException e) {
            // 连接正在拆管线，下个 tick 会再试
        }
    }

    /** 连接断了撤标记，下次进服重新挂（名字不变，新管线自然查不到旧钩子）。 */
    public static void onDisconnect() {
        // 占位：钩子随管线销毁，这里只留给以后需要清理静态状态时用
    }

    /** 当前连接的网络栈；没进服返回 null。单机（局域网内部直连）不支持协议桥。 */
    private static NetworkManager connection() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.getConnection() == null || mc.world == null || !(mc.world instanceof WorldClient)) {
            return null;
        }
        return mc.getConnection().getNetworkManager();
    }

    /** 入站钩子：只关心 opendreamcore 通道的 custom payload，其余照常放行。 */
    private static class Inbound extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (!(msg instanceof SPacketCustomPayload)) {
                ctx.fireChannelRead(msg);
                return;
            }
            SPacketCustomPayload packet = (SPacketCustomPayload) msg;
            if (!CHANNEL.equals(packet.getChannelName())) {
                ctx.fireChannelRead(msg);
                return;
            }
            // 自己吃掉，不往上传（上层 handleCustomPayload 会把不认识的通道丢掉）
            try {
                // 拷成堆字节数组再解帧：getBufferData 底下可能是直接缓冲，array() 会甩不支持
                PacketBuffer payload = packet.getBufferData();
                byte[] raw = new byte[payload.readableBytes()];
                payload.readBytes(raw);
                com.opendreamcore.protocol.LegacyFraming.Frame frame =
                        com.opendreamcore.protocol.LegacyFraming.parse(raw);
                if (frame == null) {
                    OdcLegacy.LOGGER.warn("[ODC] 协议帧解析失败（{} 字节）", raw.length);
                    return;
                }
                byte[] assembled = ASSEMBLER.offer(frame);
                if (assembled == null) {
                    return; // 分片未齐
                }
                final String path = frame.path;
                // Netty 线程收到，甩回主线程再分发
                Minecraft.getMinecraft().addScheduledTask(() ->
                        ClientHooks.onServerMessage(path, assembled));
            } catch (Exception e) {
                OdcLegacy.LOGGER.warn("[ODC] 协议载荷解析失败: {}", e.toString());
            }
        }
    }

    // 服务端能不能往这条通道发包，取决于客户端有没有报备过（REGISTER）。
    // Bukkit 那边 addChannel 只认客户端主动声明，服务端单方面发是一堆死包。
    // 注意通道名是 REGISTER 不是 MC|Register——后反编译 v1_12_R1
    // PlayerConnection 才发现的，vanilla 里根本没有 MC|Register 这号通道
    public static void registerChannels() {
        NetworkManager manager = connection();
        if (manager == null) {
            return;
        }
        // Bukkit 按 \0 分隔多通道，尾部补一个零保安全（单通道也不吃亏）
        byte[] name = CHANNEL.getBytes(StandardCharsets.UTF_8);
        byte[] payload = new byte[name.length + 1];
        System.arraycopy(name, 0, payload, 0, name.length);
        manager.sendPacket(new CPacketCustomPayload("REGISTER", new PacketBuffer(Unpooled.wrappedBuffer(payload))));
        OdcLegacy.LOGGER.info("[ODC] 已向服务器报备通道 {}（听不听得见看插件日志）", CHANNEL);
    }

    /**
     * 上行发送：path+data 走共享分片帧（大件自动切，单包装下就是一帧），
     * 直接 CPacketCustomPayload 塞进网络管线，vanilla 侧收到后转给
     * Bukkit messenger。没进服就丢弃。
     */
    public static void sendToServer(String path, byte[] data) {
        NetworkManager manager = connection();
        if (manager == null) {
            return;
        }
        for (byte[] frame : com.opendreamcore.protocol.LegacyFraming.frames(path, data)) {
            ByteBuf buf = Unpooled.wrappedBuffer(frame);
            manager.sendPacket(new CPacketCustomPayload(CHANNEL, new PacketBuffer(buf)));
        }
    }
}
