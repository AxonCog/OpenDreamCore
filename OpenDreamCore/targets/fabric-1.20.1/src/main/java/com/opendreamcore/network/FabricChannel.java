package com.opendreamcore.network;

import com.mojang.logging.LogUtils;
import com.opendreamcore.client.ClientController;
import com.opendreamcore.protocol.OdcByteArrayBuf;
import com.opendreamcore.protocol.OdcByteBuf;
import com.opendreamcore.protocol.Protocol;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

/**
 * Fabric 1.20.1 通道：旧版 channel API（send(ResourceLocation, buf) + registerGlobalReceiver）。
 * 1.20.1 没有 payload 系统，客户端直接发 vanilla custom payload（channel + 原始字节），
 * 服务端 Paper 由 Bukkit messenger 接收；下发方向同理。
 */
public final class FabricChannel {

    public static final Logger LOGGER = LogUtils.getLogger();

    private FabricChannel() {
    }

    private static ResourceLocation channel(String path) {
        return new ResourceLocation(Protocol.NAMESPACE, path); // 1.20.1 无 fromNamespaceAndPath
    }

    /** 客户端侧注册所有接收通道。 */
    public static void registerClient() {
        register(Protocol.READY_ACK, data -> {
            var ack = com.opendreamcore.protocol.message.ReadyAck.decode(reader(data));
            ClientController.get().handleReadyAck(ack);
        });
        register(Protocol.PAGE_CONTROL, data -> {
            var control = com.opendreamcore.protocol.message.PageControl.decode(reader(data));
            ClientController.get().handlePageControl(control);
        });
        register(Protocol.PAGE_SYNC, data -> {
            var sync = com.opendreamcore.protocol.message.PageSync.decode(reader(data));
            ClientController.get().storeServerPage(sync);
        });
        register(Protocol.VISUAL_RULES, data ->
                ClientController.get().handleVisualRules(
                        com.opendreamcore.protocol.message.VisualRulesSync.decode(reader(data))));
        register(Protocol.CLOUD_MANIFEST, data ->
                ClientController.get().cloud().handleManifest(
                        com.opendreamcore.protocol.message.CloudManifest.decode(reader(data))));
        register(Protocol.CLOUD_FILE, data ->
                ClientController.get().cloud().handleFile(
                        com.opendreamcore.protocol.message.CloudFile.decode(reader(data))));
        register(Protocol.CLOUD_DELETE, data ->
                ClientController.get().cloud().handleDelete(
                        com.opendreamcore.protocol.message.CloudDelete.decode(reader(data))));
        register(Protocol.CLOUD_DONE, data ->
                ClientController.get().cloud().handleDone(
                        com.opendreamcore.protocol.message.CloudDone.decode(reader(data))));
        register(Protocol.STATE_PATCH, data ->
                ClientController.get().handleStatePatch(
                        com.opendreamcore.protocol.message.StatePatch.decode(reader(data))));
        register(Protocol.TOOLTIP_REGISTRY, data ->
                ClientController.get().tooltips().handleRegistry(
                        com.opendreamcore.protocol.message.TooltipRegistry.decode(reader(data))));
        register(Protocol.GLOBAL_STATE, data ->
                ClientController.get().handleGlobalState(
                        com.opendreamcore.protocol.message.GlobalState.decode(reader(data))));
        register(Protocol.PAGE_LAYOUT, data ->
                ClientController.get().handlePageLayout(
                        com.opendreamcore.protocol.message.PageLayout.decode(reader(data))));
        register(Protocol.EDITOR_LEASE, data ->
                ClientController.get().handleLease(
                        com.opendreamcore.protocol.message.EditorLease.decode(reader(data))));
        register(Protocol.WINDOW_TITLE, data ->
                ClientController.get().handleWindowTitle(
                        com.opendreamcore.protocol.message.WindowTitlePush.decode(reader(data))));
        // 分片通道：凑包与换名路由都在 register 的进口逻辑里，这里挂个位子让接收器注册上
        register(Protocol.CHUNK, data -> { });
    }

    /** 通道名 → 处理人。分片凑齐换回真实通道名后，得靠这张表找对真正的处理人。 */
    private static final java.util.Map<String, java.util.function.Consumer<byte[]>> HANDLERS =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** 注册一个接收通道：缓冲必须同步读完（netty 线程），处理丢到渲染线程，进口先过分片分拣。 */
    private static void register(String path, java.util.function.Consumer<byte[]> handler) {
        HANDLERS.put(path, handler);
        ClientPlayNetworking.registerGlobalReceiver(channel(path), (client, netHandler, buf, responseSender) -> {
            byte[] data = new byte[buf.readableBytes()];
            buf.readBytes(data);
            client.execute(() -> {
                try {
                    ClientController.ChunkResult routed = ClientController.get().routeInbound(path, data);
                    if (routed == null) {
                        // 分片还没凑齐，等下一帧；坏帧则已被丢弃
                        return;
                    }
                    java.util.function.Consumer<byte[]> real = HANDLERS.get(routed.path());
                    if (real != null) {
                        real.accept(routed.payload());
                    }
                } catch (Exception e) {
                    LOGGER.warn("通道处理失败 {}: {}", path, e.toString());
                }
            });
        });
    }

    private static OdcByteBuf reader(byte[] data) {
        return new OdcByteArrayBuf(data);
    }

    /** 发送协议消息（ClientController.UiSender 实现）。 */
    public static void send(String channelPath, byte[] bytes) {
        // minecraft:register：1.20.1 的 fabric（networking-api 1.3.x）没有 RegistrationPayload 类，
        // 载荷格式同 AbstractChanneledNetworkAddon.createRegistrationPacket：
        // NUL 分隔的 channel toString（US-ASCII）。ClientPlayNetworking.send(id, buf)
        // 在该版即直发 vanilla custom payload（不校验命名空间），与 fabric 自身首发注册包一致。
        if (channelPath.equals("minecraft:register")) {
            if (net.minecraft.client.Minecraft.getInstance().getConnection() == null) {
                return;
            }
            StringBuilder sb = new StringBuilder();
            for (String c : Protocol.CLIENTBOUND_CHANNELS) {
                if (sb.length() > 0) {
                    sb.append((char) 0);
                }
                int j = c.indexOf(':');
                sb.append(j >= 0 ? c : Protocol.NAMESPACE + ':' + c);
            }
            ClientPlayNetworking.send(new ResourceLocation("minecraft", "register"),
                    new FriendlyByteBuf(Unpooled.wrappedBuffer(
                            sb.toString().getBytes(java.nio.charset.StandardCharsets.US_ASCII))));
            return;
        }
        // 通道名可能带完整命名空间，必须拆分，否则整串塞进 path 会因非法字符抛异常
        ResourceLocation id;
        int i = channelPath.indexOf(':');
        if (i >= 0) {
            id = new ResourceLocation(channelPath.substring(0, i), channelPath.substring(i + 1));
        } else {
            id = new ResourceLocation(Protocol.NAMESPACE, channelPath);
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
        ClientPlayNetworking.send(id, buf);
    }
}
