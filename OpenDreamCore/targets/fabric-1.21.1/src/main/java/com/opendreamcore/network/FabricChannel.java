package com.opendreamcore.network;

import com.mojang.logging.LogUtils;
import com.opendreamcore.client.ClientController;
import com.opendreamcore.protocol.OdcByteArrayBuf;
import com.opendreamcore.protocol.OdcByteBuf;
import com.opendreamcore.protocol.Protocol;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

/**
 * Fabric 通道：payload API（PayloadTypeRegistry 注册 codec + ClientPlayNetworking）。
 * 发送走 vanilla custom payload（channel + 原始字节），服务端 Paper 的
 * DiscardedPayload 保留数据并转给 Bukkit messenger；接收方向同理。
 */
public final class FabricChannel {

    public static final Logger LOGGER = LogUtils.getLogger();

    private FabricChannel() {
    }

    /** 收包用 payload：type 由注册处指定。 */
    public record RawPayload(byte[] bytes) implements CustomPacketPayload {
        @Override
        public Type<? extends CustomPacketPayload> type() {
            throw new UnsupportedOperationException("type 由注册处指定");
        }
    }

    /** 发包用 payload：带 type。 */
    public record SentPayload(Type<RawPayload> type, byte[] bytes) implements CustomPacketPayload {
    }

    private static StreamCodec<FriendlyByteBuf, RawPayload> rawCodec() {
        return StreamCodec.of((buf, payload) -> buf.writeBytes(payload.bytes()),
                buf -> {
                    byte[] data = new byte[buf.readableBytes()];
                    buf.readBytes(data);
                    return new RawPayload(data);
                });
    }

    public static CustomPacketPayload.Type<RawPayload> typeFor(String path) {
        return new CustomPacketPayload.Type<>(channel(path));
    }

    private static ResourceLocation channel(String path) {
        return ResourceLocation.fromNamespaceAndPath(Protocol.NAMESPACE, path);
    }

    /** 客户端侧：注册全部通道的 codec + 接收 handler。 */
    public static void registerClient() {
        // C2S（发送方向）：ready / ui_event / cloud_diff
        PayloadTypeRegistry.playC2S().register(typeFor(Protocol.READY), rawCodec());
        PayloadTypeRegistry.playC2S().register(typeFor(Protocol.UI_EVENT), rawCodec());
        PayloadTypeRegistry.playC2S().register(typeFor(Protocol.CLOUD_DIFF), rawCodec());
        PayloadTypeRegistry.playC2S().register(typeFor(Protocol.TOOLTIP_RESYNC), rawCodec());
        PayloadTypeRegistry.playC2S().register(typeFor(Protocol.PAGE_LAYOUT), rawCodec());
        PayloadTypeRegistry.playC2S().register(typeFor(Protocol.EDITOR_LEASE), rawCodec());
        PayloadTypeRegistry.playC2S().register(typeFor(Protocol.CUSTOM_PACKET), rawCodec());
        // S2C（接收方向）
        PayloadTypeRegistry.playS2C().register(typeFor(Protocol.READY_ACK), rawCodec());
        PayloadTypeRegistry.playS2C().register(typeFor(Protocol.PAGE_CONTROL), rawCodec());
        PayloadTypeRegistry.playS2C().register(typeFor(Protocol.PAGE_SYNC), rawCodec());
        PayloadTypeRegistry.playS2C().register(typeFor(Protocol.CLOUD_MANIFEST), rawCodec());
        PayloadTypeRegistry.playS2C().register(typeFor(Protocol.CLOUD_FILE), rawCodec());
        PayloadTypeRegistry.playS2C().register(typeFor(Protocol.CLOUD_DELETE), rawCodec());
        PayloadTypeRegistry.playS2C().register(typeFor(Protocol.CLOUD_DONE), rawCodec());
        PayloadTypeRegistry.playS2C().register(typeFor(Protocol.STATE_PATCH), rawCodec());
        PayloadTypeRegistry.playS2C().register(typeFor(Protocol.TOOLTIP_REGISTRY), rawCodec());
        PayloadTypeRegistry.playS2C().register(typeFor(Protocol.GLOBAL_STATE), rawCodec());
        PayloadTypeRegistry.playS2C().register(typeFor(Protocol.PAGE_LAYOUT), rawCodec());
        PayloadTypeRegistry.playS2C().register(typeFor(Protocol.EDITOR_LEASE), rawCodec());
        PayloadTypeRegistry.playS2C().register(typeFor(Protocol.CONTAINER_SYNC), rawCodec());
        PayloadTypeRegistry.playS2C().register(typeFor(Protocol.CHAT_MESSAGE), rawCodec());
        PayloadTypeRegistry.playS2C().register(typeFor(Protocol.UI_EFFECT), rawCodec());
        PayloadTypeRegistry.playS2C().register(typeFor(Protocol.BOSS_BAR), rawCodec());
        PayloadTypeRegistry.playS2C().register(typeFor(Protocol.NAME_TAG), rawCodec());
        PayloadTypeRegistry.playS2C().register(typeFor(Protocol.ITEM_TIP), rawCodec());
        PayloadTypeRegistry.playS2C().register(typeFor(Protocol.HUD_SYNC), rawCodec());
        PayloadTypeRegistry.playS2C().register(typeFor(Protocol.MUSIC), rawCodec());
        PayloadTypeRegistry.playS2C().register(typeFor(Protocol.CONFIG_PUSH), rawCodec());
        PayloadTypeRegistry.playS2C().register(typeFor(Protocol.UI_ANIMATION), rawCodec());
        PayloadTypeRegistry.playS2C().register(typeFor(Protocol.WORLD_TAB), rawCodec());
        PayloadTypeRegistry.playS2C().register(typeFor(Protocol.WORLD_ELEMENT_STATE), rawCodec());
        PayloadTypeRegistry.playS2C().register(typeFor(Protocol.EDITOR_WORLD_ACK), rawCodec());
        PayloadTypeRegistry.playS2C().register(typeFor(Protocol.CUSTOM_PACKET), rawCodec());

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
        register(Protocol.CONTAINER_SYNC, data ->
                ClientController.get().handleContainerSync(
                        com.opendreamcore.protocol.message.ContainerSync.decode(reader(data))));
        register(Protocol.CHAT_MESSAGE, data ->
                ClientController.get().handleChatMessage(
                        com.opendreamcore.protocol.message.ChatMessage.decode(reader(data))));
        register(Protocol.UI_EFFECT, data ->
                ClientController.get().applyEffect(
                        com.opendreamcore.protocol.message.UiEffect.decode(reader(data))));
        register(Protocol.BOSS_BAR, data ->
                ClientController.get().handleBossBar(
                        com.opendreamcore.protocol.message.BossBarSync.decode(reader(data))));
        register(Protocol.NAME_TAG, data ->
                ClientController.get().handleNameTag(
                        com.opendreamcore.protocol.message.NameTagSync.decode(reader(data))));
        register(Protocol.ITEM_TIP, data ->
                ClientController.get().handleItemTip(
                        com.opendreamcore.protocol.message.ItemTipSync.decode(reader(data))));
        register(Protocol.HUD_SYNC, data ->
                ClientController.get().handleHudSync(
                        com.opendreamcore.protocol.message.HudSync.decode(reader(data))));
        register(Protocol.MUSIC, data ->
                ClientController.get().handleMusicSync(
                        com.opendreamcore.protocol.message.MusicSync.decode(reader(data))));
        register(Protocol.CONFIG_PUSH, data ->
                ClientController.get().handleConfigPush(
                        com.opendreamcore.protocol.message.ConfigPush.decode(reader(data))));
        register(Protocol.UI_ANIMATION, data ->
                ClientController.get().handleUiAnimation(
                        com.opendreamcore.protocol.message.UiAnimation.decode(reader(data))));
        register(Protocol.WORLD_TAB, data ->
                ClientController.get().handleWorldTab(
                        com.opendreamcore.protocol.message.WorldTabSync.decode(reader(data))));
        register(Protocol.WORLD_ELEMENT_STATE, data ->
                ClientController.get().handleWorldElementState(
                        com.opendreamcore.protocol.message.WorldElementState.decode(reader(data))));
        register(Protocol.EDITOR_WORLD_ACK, data ->
                ClientController.get().handleWorldSaveAck(
                        com.opendreamcore.protocol.message.WorldSaveAck.decode(reader(data))));
        register(Protocol.CUSTOM_PACKET, data -> {
            var packet = com.opendreamcore.protocol.message.CustomPacket.decode(reader(data));
            ClientController.get().handleCustomPacket(packet.channel(), packet.payload());
        });
    }

    /** 注册一个接收通道：处理丢到渲染线程。 */
    private static void register(String path, java.util.function.Consumer<byte[]> handler) {
        ClientPlayNetworking.registerGlobalReceiver(typeFor(path), (payload, ctx) ->
                ctx.client().execute(() -> {
                    try {
                        handler.accept(payload.bytes());
                    } catch (Exception e) {
                        LOGGER.warn("通道处理失败 {}: {}", path, e.toString());
                    }
                }));
    }

    private static OdcByteBuf reader(byte[] data) {
        return new OdcByteArrayBuf(data);
    }

    /** 发送协议消息（ClientController.UiSender 实现）。 */
    public static void send(String channelPath, byte[] bytes) {
        if (net.minecraft.client.Minecraft.getInstance().getConnection() != null) {
            ClientPlayNetworking.send(new SentPayload(typeFor(channelPath), bytes));
        }
    }
}
