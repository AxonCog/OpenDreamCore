package com.opendreamcore.network;

import com.opendreamcore.protocol.Protocol;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 客户端全部协议通道的统一注册。
 * 用 common* 而非 play*：common 阶段注册的 payload 在连接非 NeoForge 服务端（Fabric/Paper/原版）时
 * 仍可正常发送（optional() + common 不受 NetworkRegistry.checkPacket 的 play 阶段通道协商限制）。
 * 服务端是 Paper 插件（无 NeoForge）时，未注册通道由 Paper 的 custom payload 事件兜底。
 */
public final class UiChannel {

    private UiChannel() {
    }

    public static CustomPacketPayload.Type<RawPayload> readyType() {
        return new CustomPacketPayload.Type<>(channel(Protocol.READY));
    }

    public static CustomPacketPayload.Type<RawPayload> readyAckType() {
        return new CustomPacketPayload.Type<>(channel(Protocol.READY_ACK));
    }

    public static CustomPacketPayload.Type<RawPayload> pageControlType() {
        return new CustomPacketPayload.Type<>(channel(Protocol.PAGE_CONTROL));
    }

    public static CustomPacketPayload.Type<RawPayload> pageSyncType() {
        return new CustomPacketPayload.Type<>(channel(Protocol.PAGE_SYNC));
    }

    public static CustomPacketPayload.Type<RawPayload> uiEventType() {
        return new CustomPacketPayload.Type<>(channel(Protocol.UI_EVENT));
    }

    public static CustomPacketPayload.Type<RawPayload> cloudManifestType() {
        return new CustomPacketPayload.Type<>(channel(Protocol.CLOUD_MANIFEST));
    }

    public static CustomPacketPayload.Type<RawPayload> cloudFileType() {
        return new CustomPacketPayload.Type<>(channel(Protocol.CLOUD_FILE));
    }

    public static CustomPacketPayload.Type<RawPayload> cloudDeleteType() {
        return new CustomPacketPayload.Type<>(channel(Protocol.CLOUD_DELETE));
    }

    public static CustomPacketPayload.Type<RawPayload> cloudDoneType() {
        return new CustomPacketPayload.Type<>(channel(Protocol.CLOUD_DONE));
    }

    public static CustomPacketPayload.Type<RawPayload> cloudDiffType() {
        return new CustomPacketPayload.Type<>(channel(Protocol.CLOUD_DIFF));
    }

    public static CustomPacketPayload.Type<RawPayload> statePatchType() {
        return new CustomPacketPayload.Type<>(channel(Protocol.STATE_PATCH));
    }

    public static CustomPacketPayload.Type<RawPayload> tooltipRegistryType() {
        return new CustomPacketPayload.Type<>(channel(Protocol.TOOLTIP_REGISTRY));
    }

    public static CustomPacketPayload.Type<RawPayload> tooltipResyncType() {
        return new CustomPacketPayload.Type<>(channel(Protocol.TOOLTIP_RESYNC));
    }

    public static CustomPacketPayload.Type<RawPayload> globalStateType() {
        return new CustomPacketPayload.Type<>(channel(Protocol.GLOBAL_STATE));
    }

    public static CustomPacketPayload.Type<RawPayload> pageLayoutType() {
        return new CustomPacketPayload.Type<>(channel(Protocol.PAGE_LAYOUT));
    }

    public static CustomPacketPayload.Type<RawPayload> editorLeaseType() {
        return new CustomPacketPayload.Type<>(channel(Protocol.EDITOR_LEASE));
    }

    public static CustomPacketPayload.Type<RawPayload> containerSyncType() {
        return new CustomPacketPayload.Type<>(channel(Protocol.CONTAINER_SYNC));
    }

    public static CustomPacketPayload.Type<RawPayload> pageCloseType() {
        return new CustomPacketPayload.Type<>(channel(Protocol.PAGE_CLOSE));
    }

    public static CustomPacketPayload.Type<RawPayload> chatMessageType() {
        return new CustomPacketPayload.Type<>(channel(Protocol.CHAT_MESSAGE));
    }

    public static CustomPacketPayload.Type<RawPayload> uiEffectType() {
        return new CustomPacketPayload.Type<>(channel(Protocol.UI_EFFECT));
    }

    public static CustomPacketPayload.Type<RawPayload> bossBarType() {
        return new CustomPacketPayload.Type<>(channel(Protocol.BOSS_BAR));
    }

    public static CustomPacketPayload.Type<RawPayload> nameTagType() {
        return new CustomPacketPayload.Type<>(channel(Protocol.NAME_TAG));
    }

    public static CustomPacketPayload.Type<RawPayload> itemTipType() {
        return new CustomPacketPayload.Type<>(channel(Protocol.ITEM_TIP));
    }

    public static CustomPacketPayload.Type<RawPayload> hudSyncType() {
        return new CustomPacketPayload.Type<>(channel(Protocol.HUD_SYNC));
    }

    public static CustomPacketPayload.Type<RawPayload> musicType() {
        return new CustomPacketPayload.Type<>(channel(Protocol.MUSIC));
    }

    public static CustomPacketPayload.Type<RawPayload> configPushType() {
        return new CustomPacketPayload.Type<>(channel(Protocol.CONFIG_PUSH));
    }

    public static CustomPacketPayload.Type<RawPayload> uiAnimationType() {
        return new CustomPacketPayload.Type<>(channel(Protocol.UI_ANIMATION));
    }

    public static CustomPacketPayload.Type<RawPayload> worldTabType() {
        return new CustomPacketPayload.Type<>(channel(Protocol.WORLD_TAB));
    }

    public static CustomPacketPayload.Type<RawPayload> worldElementStateType() {
        return new CustomPacketPayload.Type<>(channel(Protocol.WORLD_ELEMENT_STATE));
    }

    public static CustomPacketPayload.Type<RawPayload> windowTitleType() {
        return new CustomPacketPayload.Type<>(channel(Protocol.WINDOW_TITLE));
    }

    public static CustomPacketPayload.Type<RawPayload> editorWorldAckType() {
        return new CustomPacketPayload.Type<>(channel(Protocol.EDITOR_WORLD_ACK));
    }

    public static CustomPacketPayload.Type<RawPayload> customPacketType() {
        return new CustomPacketPayload.Type<>(channel(Protocol.CUSTOM_PACKET));
    }

    private static net.minecraft.resources.Identifier channel(String path) {
        // 完整通道名（如 minecraft:register）必须按 ns:path 拆分，整串塞进 path 会因冒号非法抛异常
        int i = path.indexOf(':');
        if (i >= 0) {
            return net.minecraft.resources.Identifier.fromNamespaceAndPath(
                    path.substring(0, i), path.substring(i + 1));
        }
        return net.minecraft.resources.Identifier.fromNamespaceAndPath(Protocol.NAMESPACE, path);
    }

    /** 通道路径 → payload type（发送侧用；未注册的通道也允许（Paper 侧由 messenger 兜底）。 */
    public static CustomPacketPayload.Type<RawPayload> typeFor(String path) {
        return new CustomPacketPayload.Type<>(channel(path));
    }

    /**
     * 直接构造 ServerboundCustomPayloadPacket 发送，绕过 NeoForge 的 checkPacket。
     * NeoForge 的 ClientCommonPacketListenerImpl.send(CustomPacketPayload) 会调用
     * NetworkRegistry.checkPacket 检查 payload 是否被服务端协商确认——连接非 NeoForge
     * 服务端（Fabric/Paper/原版）时 optional() 的 payload 不会被确认，导致抛异常断连。
     * 直接走 Connection.send(ServerboundCustomPayloadPacket) 是原版 vanilla 路径，不经过 checkPacket。
     */
    public static void sendRaw(Connection connection, String channelPath, byte[] bytes) {
        // minecraft:register 是已知类型，原版编解码按 ID 分发并强转对应 Payload 类；
        // 必须用专用 MinecraftRegisterPayload 承载，塞 RawPayload 会 ClassCastException
        if (channelPath.equals("minecraft:register")) {
            java.util.Set<net.minecraft.resources.Identifier> channels = new java.util.LinkedHashSet<>();
            for (String c : Protocol.CLIENTBOUND_CHANNELS) {
                int i = c.indexOf(':');
                channels.add(i >= 0
                        ? net.minecraft.resources.Identifier.fromNamespaceAndPath(c.substring(0, i), c.substring(i + 1))
                        : net.minecraft.resources.Identifier.fromNamespaceAndPath(Protocol.NAMESPACE, c));
            }
            connection.send(new ServerboundCustomPayloadPacket(
                    new net.neoforged.neoforge.network.payload.MinecraftRegisterPayload(channels)));
            return;
        }
        CustomPacketPayload.Type<RawPayload> type = typeFor(channelPath);
        RawPayload payload = RawPayload.of(type, bytes);
        connection.send(new ServerboundCustomPayloadPacket(payload));
    }

    /** 原始字节编解码：包体 = 协议消息的完整二进制。type 在解码时注入，确保 type() 始终返回非 null。 */
    private static StreamCodec<ByteBuf, RawPayload> rawCodec(CustomPacketPayload.Type<RawPayload> type) {
        return StreamCodec.of((buf, payload) -> buf.writeBytes(payload.bytes()),
                buf -> {
                    byte[] data = new byte[buf.readableBytes()];
                    buf.readBytes(data);
                    return RawPayload.received(type, data);
                });
    }

    public static void registerPayloads(PayloadRegistrar registrar) {
        // 用 common* 而非 play*：连接非 NeoForge 服务端时 optional() + common 不受 checkPacket 限制
        registrar.commonToServer(readyType(), rawCodec(readyType()), HandshakeHandler::handleReady);
        registrar.commonToServer(uiEventType(), rawCodec(uiEventType()), HandshakeHandler::handleUiEvent);
        registrar.commonToServer(cloudDiffType(), rawCodec(cloudDiffType()), HandshakeHandler::handleCloudDiff);
        registrar.commonToServer(tooltipResyncType(), rawCodec(tooltipResyncType()), HandshakeHandler::handleTooltipResync);
        registrar.commonToServer(pageCloseType(), rawCodec(pageCloseType()), HandshakeHandler::handlePageClose);
        registrar.commonToClient(readyAckType(), rawCodec(readyAckType()), HandshakeHandler::handleReadyAck);
        registrar.commonToClient(pageControlType(), rawCodec(pageControlType()), HandshakeHandler::handlePageControl);
        registrar.commonToClient(pageSyncType(), rawCodec(pageSyncType()), HandshakeHandler::handlePageSync);
        registrar.commonToClient(cloudManifestType(), rawCodec(cloudManifestType()), HandshakeHandler::handleCloudManifest);
        registrar.commonToClient(cloudFileType(), rawCodec(cloudFileType()), HandshakeHandler::handleCloudFile);
        registrar.commonToClient(cloudDeleteType(), rawCodec(cloudDeleteType()), HandshakeHandler::handleCloudDelete);
        registrar.commonToClient(cloudDoneType(), rawCodec(cloudDoneType()), HandshakeHandler::handleCloudDone);
        registrar.commonToClient(statePatchType(), rawCodec(statePatchType()), HandshakeHandler::handleStatePatch);
        registrar.commonToClient(tooltipRegistryType(), rawCodec(tooltipRegistryType()), HandshakeHandler::handleTooltipRegistry);
        registrar.commonToClient(globalStateType(), rawCodec(globalStateType()), HandshakeHandler::handleGlobalState);
        registrar.commonToClient(containerSyncType(), rawCodec(containerSyncType()), HandshakeHandler::handleContainerSync);
        registrar.commonToClient(chatMessageType(), rawCodec(chatMessageType()), HandshakeHandler::handleChatMessage);
        registrar.commonToClient(uiEffectType(), rawCodec(uiEffectType()), HandshakeHandler::handleUiEffect);
        registrar.commonToClient(bossBarType(), rawCodec(bossBarType()), HandshakeHandler::handleBossBar);
        registrar.commonToClient(nameTagType(), rawCodec(nameTagType()), HandshakeHandler::handleNameTag);
        registrar.commonToClient(itemTipType(), rawCodec(itemTipType()), HandshakeHandler::handleItemTip);
        registrar.commonToClient(hudSyncType(), rawCodec(hudSyncType()), HandshakeHandler::handleHudSync);
        registrar.commonToClient(musicType(), rawCodec(musicType()), HandshakeHandler::handleMusicSync);
        registrar.commonToClient(configPushType(), rawCodec(configPushType()), HandshakeHandler::handleConfigPush);
        registrar.commonToClient(uiAnimationType(), rawCodec(uiAnimationType()), HandshakeHandler::handleUiAnimation);
        registrar.commonToClient(worldTabType(), rawCodec(worldTabType()), HandshakeHandler::handleWorldTab);
        registrar.commonToClient(worldElementStateType(), rawCodec(worldElementStateType()), HandshakeHandler::handleWorldElementState);
        registrar.commonToClient(windowTitleType(), rawCodec(windowTitleType()), HandshakeHandler::handleWindowTitle);
        registrar.commonToClient(editorWorldAckType(), rawCodec(editorWorldAckType()), HandshakeHandler::handleWorldSaveAck);
        // 双向 payload：用四参重载（客户端 handler + 服务端 handler 分开传）。
        // 单参重载只注册一侧，21.11 客户端校验会报 missing client-side handlers；
        // 同 ID 注册两次也不允许（Cannot register payload ... already registered）。
        registrar.commonBidirectional(pageLayoutType(), rawCodec(pageLayoutType()),
                HandshakeHandler::handlePageLayout, HandshakeHandler::handlePageLayout);
        registrar.commonBidirectional(editorLeaseType(), rawCodec(editorLeaseType()),
                HandshakeHandler::handleLease, HandshakeHandler::handleLease);
        registrar.commonBidirectional(customPacketType(), rawCodec(customPacketType()),
                HandshakeHandler::handleCustomPacket, HandshakeHandler::handleCustomPacket);
    }
}
