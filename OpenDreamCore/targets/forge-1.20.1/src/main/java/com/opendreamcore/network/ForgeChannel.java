package com.opendreamcore.network;

import com.opendreamcore.OpenDreamCore;
import com.opendreamcore.client.ClientController;
import com.opendreamcore.protocol.OdcByteArrayBuf;
import com.opendreamcore.protocol.OdcByteBuf;
import com.opendreamcore.protocol.Protocol;
import com.opendreamcore.protocol.message.BossBarSync;
import com.opendreamcore.protocol.message.ChatMessage;
import com.opendreamcore.protocol.message.CloudDelete;
import com.opendreamcore.protocol.message.CloudDone;
import com.opendreamcore.protocol.message.CloudFile;
import com.opendreamcore.protocol.message.CloudManifest;
import com.opendreamcore.protocol.message.ConfigPush;
import com.opendreamcore.protocol.message.ContainerSync;
import com.opendreamcore.protocol.message.CustomPacket;
import com.opendreamcore.protocol.message.EditorLease;
import com.opendreamcore.protocol.message.GlobalState;
import com.opendreamcore.protocol.message.HudSync;
import com.opendreamcore.protocol.message.ItemTipSync;
import com.opendreamcore.protocol.message.MusicSync;
import com.opendreamcore.protocol.message.NameTagSync;
import com.opendreamcore.protocol.message.PageControl;
import com.opendreamcore.protocol.message.PageLayout;
import com.opendreamcore.protocol.message.PageSync;
import com.opendreamcore.protocol.message.ReadyAck;
import com.opendreamcore.protocol.message.StatePatch;
import com.opendreamcore.protocol.message.TooltipRegistry;
import com.opendreamcore.protocol.message.UiAnimation;
import com.opendreamcore.protocol.message.UiEffect;
import com.opendreamcore.protocol.message.WorldElementState;
import com.opendreamcore.protocol.message.WorldSaveAck;
import com.opendreamcore.protocol.message.WorldTabSync;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Forge 1.20.1 协议通道：
 * 发送 = 直接构造 ServerboundCustomPayloadPacket 走 Connection.send（vanilla 路径，
 * 线格式 = 通道名 + 协议消息完整二进制，与 NeoForge sendRaw / Fabric send 完全一致，
 * Paper 服务端的 Bukkit messenger 可直接兜底接收）。
 * 接收 = ClientPacketListenerMixin 拦 handleCustomPayload 后路由到 {@link #dispatch}。
 */
public final class ForgeChannel {

    private static final Map<String, Consumer<byte[]>> HANDLERS = new ConcurrentHashMap<>();

    private ForgeChannel() {
    }

    private static ResourceLocation channel(String path) {
        // minecraft: 保留通道（如 register 声明）不带 opendreamcore 前缀，否则路径含冒号非法
        if (path.startsWith("minecraft:")) {
            return new ResourceLocation("minecraft", path.substring("minecraft:".length()));
        }
        return new ResourceLocation(Protocol.NAMESPACE, path);
    }

    /** 注册全部客户端接收通道（ClientSetup 时调用一次）。 */
    public static void registerHandlers() {
        register(Protocol.READY_ACK, data -> {
            var ack = ReadyAck.decode(reader(data));
            ClientController.get().handleReadyAck(ack);
        });
        register(Protocol.PAGE_CONTROL, data ->
                ClientController.get().handlePageControl(PageControl.decode(reader(data))));
        register(Protocol.PAGE_SYNC, data ->
                ClientController.get().storeServerPage(PageSync.decode(reader(data))));
        register(Protocol.CLOUD_MANIFEST, data ->
                ClientController.get().cloud().handleManifest(CloudManifest.decode(reader(data))));
        register(Protocol.CLOUD_FILE, data ->
                ClientController.get().cloud().handleFile(CloudFile.decode(reader(data))));
        register(Protocol.CLOUD_DELETE, data ->
                ClientController.get().cloud().handleDelete(CloudDelete.decode(reader(data))));
        register(Protocol.CLOUD_DONE, data ->
                ClientController.get().cloud().handleDone(CloudDone.decode(reader(data))));
        register(Protocol.STATE_PATCH, data ->
                ClientController.get().handleStatePatch(StatePatch.decode(reader(data))));
        register(Protocol.TOOLTIP_REGISTRY, data ->
                ClientController.get().tooltips().handleRegistry(TooltipRegistry.decode(reader(data))));
        register(Protocol.GLOBAL_STATE, data ->
                ClientController.get().handleGlobalState(GlobalState.decode(reader(data))));
        register(Protocol.PAGE_LAYOUT, data ->
                ClientController.get().handlePageLayout(PageLayout.decode(reader(data))));
        register(Protocol.EDITOR_LEASE, data ->
                ClientController.get().handleLease(EditorLease.decode(reader(data))));
        register(Protocol.CONTAINER_SYNC, data ->
                ClientController.get().handleContainerSync(ContainerSync.decode(reader(data))));
        register(Protocol.CHAT_MESSAGE, data ->
                ClientController.get().handleChatMessage(ChatMessage.decode(reader(data))));
        register(Protocol.UI_EFFECT, data ->
                ClientController.get().applyEffect(UiEffect.decode(reader(data))));
        register(Protocol.BOSS_BAR, data ->
                ClientController.get().handleBossBar(BossBarSync.decode(reader(data))));
        register(Protocol.NAME_TAG, data ->
                ClientController.get().handleNameTag(NameTagSync.decode(reader(data))));
        register(Protocol.ITEM_TIP, data ->
                ClientController.get().handleItemTip(ItemTipSync.decode(reader(data))));
        register(Protocol.HUD_SYNC, data ->
                ClientController.get().handleHudSync(HudSync.decode(reader(data))));
        register(Protocol.MUSIC, data ->
                ClientController.get().handleMusicSync(MusicSync.decode(reader(data))));
        register(Protocol.CONFIG_PUSH, data ->
                ClientController.get().handleConfigPush(ConfigPush.decode(reader(data))));
        register(Protocol.UI_ANIMATION, data ->
                ClientController.get().handleUiAnimation(UiAnimation.decode(reader(data))));
        register(Protocol.WORLD_TAB, data ->
                ClientController.get().handleWorldTab(WorldTabSync.decode(reader(data))));
        register(Protocol.WORLD_ELEMENT_STATE, data ->
                ClientController.get().handleWorldElementState(WorldElementState.decode(reader(data))));
        register(Protocol.EDITOR_WORLD_ACK, data ->
                ClientController.get().handleWorldSaveAck(WorldSaveAck.decode(reader(data))));
        register(Protocol.CUSTOM_PACKET, data -> {
            CustomPacket packet = CustomPacket.decode(reader(data));
            ClientController.get().handleCustomPacket(packet.channel(), packet.payload());
        });
        register(Protocol.WINDOW_TITLE, data ->
                ClientController.get().handleWindowTitle(
                        com.opendreamcore.protocol.message.WindowTitlePush.decode(reader(data))));
    }

    private static void register(String path, Consumer<byte[]> handler) {
        HANDLERS.put(path, handler);
    }

    /** Mixin 入口：按通道路径分发（已在客户端线程）。 */
    public static void dispatch(String path, byte[] data) {
        Consumer<byte[]> handler = HANDLERS.get(path);
        if (handler == null) {
            return;
        }
        try {
            handler.accept(data);
        } catch (Exception e) {
            OpenDreamCore.LOGGER.warn("通道处理失败 {}: {}", path, e.toString());
        }
    }

    /**
     * 发送协议消息（ClientController.UiSender 实现）。
     * 直发 vanilla ServerboundCustomPayloadPacket，不经 Forge SimpleChannel（无消息判别头）。
     */
    public static void send(String channelPath, byte[] bytes) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
            mc.getConnection().send(new ServerboundCustomPayloadPacket(channel(channelPath), buf));
        }
    }

    private static OdcByteBuf reader(byte[] data) {
        return new OdcByteArrayBuf(data);
    }
}
