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

    /** 收/发共用 payload：自带 type。
     *  注意：codec 按 type 解析后会把实例直接喂给编码器，收发必须是同一个类
     *  （与 NeoForge 侧 network/RawPayload 同构），否则编码期 ClassCastException 断连。 */
    public record RawPayload(Type<RawPayload> type, byte[] bytes) implements CustomPacketPayload {
        @Override
        public Type<RawPayload> type() {
            return type;
        }

        public static RawPayload of(Type<RawPayload> type, byte[] bytes) {
            return new RawPayload(type, bytes);
        }
    }

    private static StreamCodec<FriendlyByteBuf, RawPayload> rawCodec(CustomPacketPayload.Type<RawPayload> type) {
        return StreamCodec.of((buf, payload) -> buf.writeBytes(payload.bytes()),
                buf -> {
                    byte[] data = new byte[buf.readableBytes()];
                    buf.readBytes(data);
                    return new RawPayload(type, data);
                });
    }

    /** 注册一个 C2S 通道 codec（客户端只发不收）。 */
    private static void c2s(String path) {
        var type = typeFor(path);
        PayloadTypeRegistry.playC2S().register(type, rawCodec(type));
    }

    /** 注册一个 S2C 通道 codec（客户端只收不发）。 */
    private static void s2c(String path) {
        var type = typeFor(path);
        PayloadTypeRegistry.playS2C().register(type, rawCodec(type));
    }

    public static CustomPacketPayload.Type<RawPayload> typeFor(String path) {
        return new CustomPacketPayload.Type<>(channel(path));
    }

    private static ResourceLocation channel(String path) {
        return ResourceLocation.fromNamespaceAndPath(Protocol.NAMESPACE, path);
    }

    /** 客户端侧：注册全部通道的 codec + 接收 handler。 */
    public static void registerClient() {
        // 全部 opendreamcore:* 通道按协议常量双向注册 codec：
        // - 发送方向漏注册会在编码期落入 vanilla DiscardedPayload 兜底 → ClassCastException 断连
        //   （page_close / editor_world 曾因此断连）
        // - 接收方向未挂 handler 的通道收到后静默忽略，多注册无害；
        //   新增通道只需在 Protocol 里加常量，无需再维护这份清单
        for (java.lang.reflect.Field f : Protocol.class.getDeclaredFields()) {
            if (f.getType() != String.class
                    || !java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                continue;
            }
            try {
                String path = (String) f.get(null);
                var type = typeFor(path);
                var codec = rawCodec(type);
                PayloadTypeRegistry.playC2S().register(type, codec);
                PayloadTypeRegistry.playS2C().register(typeFor(path), codec);
            } catch (IllegalAccessException ignored) {
            }
        }

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
        register(Protocol.WINDOW_TITLE, data ->
                ClientController.get().handleWindowTitle(
                        com.opendreamcore.protocol.message.WindowTitlePush.decode(reader(data))));
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
            // 通道名可能带完整命名空间（如 minecraft:register），必须拆分，否则整串塞进 path 会因非法字符抛异常
            ResourceLocation id;
            int i = channelPath.indexOf(':');
            if (i >= 0) {
                id = ResourceLocation.fromNamespaceAndPath(channelPath.substring(0, i), channelPath.substring(i + 1));
            } else {
                id = ResourceLocation.fromNamespaceAndPath(Protocol.NAMESPACE, channelPath);
            }
            ClientPlayNetworking.send(RawPayload.of(typeFor(channelPath), bytes));
        }
    }
}
