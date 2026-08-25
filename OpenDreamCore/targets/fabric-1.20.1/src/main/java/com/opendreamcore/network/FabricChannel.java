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
    }

    /** 注册一个接收通道：缓冲必须同步读完（netty 线程），处理丢到渲染线程。 */
    private static void register(String path, java.util.function.Consumer<byte[]> handler) {
        ClientPlayNetworking.registerGlobalReceiver(channel(path), (client, netHandler, buf, responseSender) -> {
            byte[] data = new byte[buf.readableBytes()];
            buf.readBytes(data);
            client.execute(() -> {
                try {
                    handler.accept(data);
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
        if (net.minecraft.client.Minecraft.getInstance().getConnection() != null) {
            // 通道名可能带完整命名空间（如 minecraft:register），必须拆分，否则整串塞进 path 会因非法字符抛异常
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
}
