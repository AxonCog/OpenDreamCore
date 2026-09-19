package com.opendreamcore.network;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.CCustomPayloadPacket;
import net.minecraft.network.play.server.SCustomPayloadPlayPacket;
import net.minecraft.util.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.util.NoSuchElementException;

/**
 * 原版协议桥：1.16.5 的 Forge 客户端在插件服务器上收不到任何非原版的
 * plugin message——handleCustomPayload 只认 BRAND/注册过的 FML 通道，
 * 其余直接丢，SimpleChannel 又只在 Forge 服务器上存在。跟 1.12.2 一样
 * 往下沉一层，在 Netty 管线里自己截包：packet_handler（NetworkManager）
 * 前面挂个入站监听，SCustomPayloadPlayPacket 抓下来自己分，不再往上走。
 *
 * 跟 1.12.2 老服单通道不一样，1.16.5 走 modern 模式协议：下行多通道
 * opendreamcore:xxx（大件切 chunk 通道），报备用 minecraft:register
 * 声明全量通道。握手前的 READY 照旧走 FML SimpleChannel——万一哪天
 * 接了 Forge 服务器它还在，插件服务器上发不出去也无害。
 */
public final class VanillaBridge165 {

    /** 命名空间，跟 Protocol.NAMESPACE 一致（那边在 jdk8 树，这边直接引用）。 */
    private static final String NAMESPACE = com.opendreamcore.protocol.Protocol.NAMESPACE;

    /** 分片通道名（不带命名空间），帧头里带真实业务通道名。 */
    private static final String CHUNK_PATH = com.opendreamcore.protocol.Protocol.CHUNK;

    /** 管线里的钩子名，带后缀防跟其他 mod 的钩子撞名。 */
    private static final String HANDLER_NAME = "odc_bridge_165_"
            + Integer.toHexString(VanillaBridge165.class.hashCode());

    /** 下行分片重组：一条连接一个重组器（分片按帧头 transferId 聚合）。 */
    private static final com.opendreamcore.protocol.LegacyFraming.Assembler ASSEMBLER =
            new com.opendreamcore.protocol.LegacyFraming.Assembler();

    private VanillaBridge165() {
    }

    /**
     * 进服后调用：往当前连接的 Netty 管线插入解码钩子。管线每次进服
     * 都是新的，所以每条连接都要挂一遍；已挂过（按名字查）就跳过。
     */
    public static void install() {
        NetworkManager manager = connection();
        if (manager == null || manager.isMemoryConnection()) {
            return;
        }
        Channel channel = manager.channel();
        if (channel == null || channel.pipeline().get(HANDLER_NAME) != null) {
            return;
        }
        try {
            channel.pipeline().addBefore("packet_handler", HANDLER_NAME, new Inbound());
            com.opendreamcore.OdcLegacy.LOGGER.info("[ODC] 解码钩子已挂进管线");
        } catch (NoSuchElementException e) {
            // 连接正在拆管线，下个 tick 会再试
        }
    }

    /** 当前连接的网络栈；没进服返回 null。 */
    private static NetworkManager connection() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.getConnection() == null) {
            return null;
        }
        return mc.getConnection().getConnection();
    }

    /** 入站钩子：只关心 opendreamcore 命名空间的 custom payload，其余照常放行。 */
    private static class Inbound extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (!(msg instanceof SCustomPayloadPlayPacket)) {
                ctx.fireChannelRead(msg);
                return;
            }
            SCustomPayloadPlayPacket packet = (SCustomPayloadPlayPacket) msg;
            ResourceLocation id = packet.getIdentifier();
            if (id == null || !NAMESPACE.equals(id.getNamespace())) {
                ctx.fireChannelRead(msg);
                return;
            }
            // 自己吃掉，不往上传（上层会把不认识的通道丢掉）
            try {
                PacketBuffer payload = packet.getData();
                byte[] raw = new byte[payload.readableBytes()];
                payload.readBytes(raw);
                String path = id.getPath();
                byte[] data = raw;
                if (CHUNK_PATH.equals(path)) {
                    // 大件分片帧：解析帧头凑齐后按帧内真实通道名分发
                    com.opendreamcore.protocol.LegacyFraming.Frame frame =
                            com.opendreamcore.protocol.LegacyFraming.parse(raw);
                    if (frame == null) {
                        com.opendreamcore.OdcLegacy.LOGGER.warn("[ODC] 分片帧解析失败（{} 字节）", raw.length);
                        return;
                    }
                    byte[] assembled = ASSEMBLER.offer(frame);
                    if (assembled == null) {
                        return; // 分片未齐
                    }
                    path = frame.path;
                    data = assembled;
                }
                final String fPath = path;
                final byte[] fData = data;
                // Netty 线程收到，甩回主线程再分发
                net.minecraft.client.Minecraft.getInstance().execute(() ->
                        dispatch(fPath, fData));
            } catch (Exception e) {
                com.opendreamcore.OdcLegacy.LOGGER.warn("[ODC] 协议载荷解析失败: {}", e.toString());
            }
        }
    }

    /** 主线程分发：统一走 ClientHooks1165 的 dispatcher 入口。 */
    private static void dispatch(String path, byte[] data) {
        com.opendreamcore.ClientHooks1165.onServerMessage(path, data);
    }

    /**
     * 声明下行通道：Bukkit 只往客户端声明过的通道发包，声明用
     * minecraft:register（1.13+ 的名字），载荷 NUL 分隔全量通道名。
     * 必须早于 READY——插件收到 READY 即刻下发页面/标题，晚了全丢。
     */
    public static void registerChannels() {
        NetworkManager manager = connection();
        if (manager == null) {
            return;
        }
        byte[] payload = com.opendreamcore.protocol.Protocol.clientboundRegisterPayload();
        manager.send(new CCustomPayloadPacket(
                new ResourceLocation("minecraft", "register"),
                new PacketBuffer(io.netty.buffer.Unpooled.wrappedBuffer(payload))));
        com.opendreamcore.OdcLegacy.LOGGER.info("[ODC] 已向服务器声明 {} 条下行通道", 
                com.opendreamcore.protocol.Protocol.CLIENTBOUND_CHANNELS.length);
    }

    /** 上行发送：path+data 走共享分片帧，大件自动切 chunk 通道。 */
    public static void sendToServer(String path, byte[] data) {
        NetworkManager manager = connection();
        if (manager == null) {
            return;
        }
        for (byte[] frame : com.opendreamcore.protocol.LegacyFraming.frames(path, data)) {
            manager.send(new CCustomPayloadPacket(
                    new ResourceLocation(NAMESPACE, CHUNK_PATH),
                    new PacketBuffer(io.netty.buffer.Unpooled.wrappedBuffer(frame))));
        }
    }
}
