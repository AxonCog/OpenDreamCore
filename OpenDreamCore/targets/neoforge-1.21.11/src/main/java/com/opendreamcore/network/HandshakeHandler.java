package com.opendreamcore.network;

import com.opendreamcore.OpenDreamCore;
import com.opendreamcore.client.ClientController;
import com.opendreamcore.protocol.OdcByteArrayBuf;
import com.opendreamcore.protocol.OdcByteBuf;
import com.opendreamcore.protocol.Protocol;
import com.opendreamcore.protocol.message.CloudDelete;
import com.opendreamcore.protocol.message.CloudDone;
import com.opendreamcore.protocol.message.CloudFile;
import com.opendreamcore.protocol.message.CloudManifest;
import com.opendreamcore.protocol.message.EditorLease;
import com.opendreamcore.protocol.message.GlobalState;
import com.opendreamcore.protocol.message.PageControl;
import com.opendreamcore.protocol.message.PageLayout;
import com.opendreamcore.protocol.message.PageSync;
import com.opendreamcore.protocol.message.Ready;
import com.opendreamcore.protocol.message.ReadyAck;
import com.opendreamcore.protocol.message.StatePatch;
import com.opendreamcore.protocol.message.TooltipRegistry;
import com.opendreamcore.protocol.message.UiEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 握手与页面控制：
 * 客户端进服发 ready（协议版本 + 模组版本 + 能力），服务端回 ready_ack；
 * 服务端下发 page_control 开关页面；客户端上报 ui_event。
 * 服务端方向（ready 接收/ack 回复）在 Paper 插件侧实现，这里只做客户端侧收发。
 */
public final class HandshakeHandler {

    private static final int CAPABILITIES = Protocol.CAPABILITY_LOCAL_UI | Protocol.CAPABILITY_CLOUD;

    private HandshakeHandler() {
    }

    /** 客户端侧：收到服务端 ready_ack。 */
    public static void handleReadyAck(RawPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                ReadyAck ack = ReadyAck.decode(reader(payload));
                ClientController.get().handleReadyAck(ack);
            } catch (Exception e) {
                OpenDreamCore.LOGGER.warn("ready_ack 解析失败: {}", e.toString());
            }
        });
    }

    /** 客户端侧：收到服务端 page_control。 */
    public static void handlePageControl(RawPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                PageControl control = PageControl.decode(reader(payload));
                ClientController.get().handlePageControl(control);
            } catch (Exception e) {
                OpenDreamCore.LOGGER.warn("page_control 解析失败: {}", e.toString());
            }
        });
    }

    /** 客户端侧：收到服务端 page_sync（页面 YAML 入库）。 */
    public static void handlePageSync(RawPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                PageSync sync = PageSync.decode(reader(payload));
                ClientController.get().storeServerPage(sync);
            } catch (Exception e) {
                OpenDreamCore.LOGGER.warn("page_sync 解析失败: {}", e.toString());
            }
        });
    }

    /** 客户端侧：云资源清单到达。 */
    public static void handleCloudManifest(RawPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                ClientController.get().cloud().handleManifest(CloudManifest.decode(reader(payload)));
            } catch (Exception e) {
                OpenDreamCore.LOGGER.warn("cloud_manifest 解析失败: {}", e.toString());
            }
        });
    }

    /** 客户端侧：云资源文件到达。 */
    public static void handleCloudFile(RawPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                ClientController.get().cloud().handleFile(CloudFile.decode(reader(payload)));
            } catch (Exception e) {
                OpenDreamCore.LOGGER.warn("cloud_file 解析失败: {}", e.toString());
            }
        });
    }

    /** 客户端侧：云资源删除。 */
    public static void handleCloudDelete(RawPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                ClientController.get().cloud().handleDelete(CloudDelete.decode(reader(payload)));
            } catch (Exception e) {
                OpenDreamCore.LOGGER.warn("cloud_delete 解析失败: {}", e.toString());
            }
        });
    }

    /** 客户端侧：云资源同步完成。 */
    public static void handleCloudDone(RawPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                ClientController.get().cloud().handleDone(CloudDone.decode(reader(payload)));
            } catch (Exception e) {
                OpenDreamCore.LOGGER.warn("cloud_done 解析失败: {}", e.toString());
            }
        });
    }

    /** 客户端侧：状态补丁（页面变量刷新）。 */
    public static void handleStatePatch(RawPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                ClientController.get().handleStatePatch(StatePatch.decode(reader(payload)));
            } catch (Exception e) {
                OpenDreamCore.LOGGER.warn("state_patch 解析失败: {}", e.toString());
            }
        });
    }

    /** 客户端侧：服务端 tooltip 注册表。 */
    public static void handleTooltipRegistry(RawPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                ClientController.get().tooltips().handleRegistry(TooltipRegistry.decode(reader(payload)));
            } catch (Exception e) {
                OpenDreamCore.LOGGER.warn("tooltip_registry 解析失败: {}", e.toString());
            }
        });
    }

    /** 客户端侧：服务端全局状态。 */
    public static void handleGlobalState(RawPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                ClientController.get().handleGlobalState(GlobalState.decode(reader(payload)));
            } catch (Exception e) {
                OpenDreamCore.LOGGER.warn("global_state 解析失败: {}", e.toString());
            }
        });
    }

    /** 服务端方向：收到客户端 tooltip 重传请求（Paper 插件另行实现）。 */
    public static void handleTooltipResync(RawPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> OpenDreamCore.LOGGER.info("收到 tooltip_resync"));
    }

    /** 页面布局：C→S 保存（服务端方向，插件实现）；S→C 广播（客户端应用）。 */
    public static void handlePageLayout(RawPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                ClientController.get().handlePageLayout(PageLayout.decode(reader(payload)));
            } catch (Exception e) {
                OpenDreamCore.LOGGER.warn("page_layout 解析失败: {}", e.toString());
            }
        });
    }

    /** 编辑租约：C→S 请求（服务端授予）；S→C 回执（客户端记录）。 */
    public static void handleLease(RawPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                ClientController.get().handleLease(EditorLease.decode(reader(payload)));
            } catch (Exception e) {
                OpenDreamCore.LOGGER.warn("editor_lease 解析失败: {}", e.toString());
            }
        });
    }

    /** 客户端侧：服务端容器内容同步（chest_slot/container 组件数据源）。 */
    public static void handleContainerSync(RawPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                ClientController.get().handleContainerSync(
                        com.opendreamcore.protocol.message.ContainerSync.decode(reader(payload)));
            } catch (Exception e) {
                OpenDreamCore.LOGGER.warn("container_sync 解析失败: {}", e.toString());
            }
        });
    }

    /** 客户端侧：服务端聊天通道消息（chat_display channel 数据源）。 */
    public static void handleChatMessage(RawPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                ClientController.get().handleChatMessage(
                        com.opendreamcore.protocol.message.ChatMessage.decode(reader(payload)));
            } catch (Exception e) {
                OpenDreamCore.LOGGER.warn("chat_message 解析失败: {}", e.toString());
            }
        });
    }

    /** 客户端侧：服务端屏幕特效指令（震动/闪屏/过渡）。 */
    public static void handleUiEffect(RawPayload payload, IPayloadContext context) {        context.enqueueWork(() -> {
            try {
                ClientController.get().applyEffect(
                        com.opendreamcore.protocol.message.UiEffect.decode(reader(payload)));
            } catch (Exception e) {
                OpenDreamCore.LOGGER.warn("ui_effect 解析失败: {}", e.toString());
            }
        });
    }

    /** 客户端侧：服务端 Boss 条同步。 */
    public static void handleBossBar(RawPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                ClientController.get().handleBossBar(
                        com.opendreamcore.protocol.message.BossBarSync.decode(reader(payload)));
            } catch (Exception e) {
                OpenDreamCore.LOGGER.warn("boss_bar 解析失败: {}", e.toString());
            }
        });
    }

    /** 客户端侧：服务端名牌同步。 */
    public static void handleNameTag(RawPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                ClientController.get().handleNameTag(
                        com.opendreamcore.protocol.message.NameTagSync.decode(reader(payload)));
            } catch (Exception e) {
                OpenDreamCore.LOGGER.warn("name_tag 解析失败: {}", e.toString());
            }
        });
    }

    /** 客户端侧：服务端物品提示同步。 */
    public static void handleItemTip(RawPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                ClientController.get().handleItemTip(
                        com.opendreamcore.protocol.message.ItemTipSync.decode(reader(payload)));
            } catch (Exception e) {
                OpenDreamCore.LOGGER.warn("item_tip 解析失败: {}", e.toString());
            }
        });
    }

    /** 客户端侧：服务端 HUD 同步（个人/全局/静态三型）。 */
    public static void handleHudSync(RawPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                ClientController.get().handleHudSync(
                        com.opendreamcore.protocol.message.HudSync.decode(reader(payload)));
            } catch (Exception e) {
                OpenDreamCore.LOGGER.warn("hud_sync 解析失败: {}", e.toString());
            }
        });
    }

    /** 客户端侧：服务端背景音乐指令。 */
    public static void handleMusicSync(RawPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                ClientController.get().handleMusicSync(
                        com.opendreamcore.protocol.message.MusicSync.decode(reader(payload)));
            } catch (Exception e) {
                OpenDreamCore.LOGGER.warn("music 解析失败: {}", e.toString());
            }
        });
    }

    /** 客户端侧：服务端配置下发（写入 odc.properties）。 */
    public static void handleConfigPush(RawPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                ClientController.get().handleConfigPush(
                        com.opendreamcore.protocol.message.ConfigPush.decode(reader(payload)));
            } catch (Exception e) {
                OpenDreamCore.LOGGER.warn("config_push 解析失败: {}", e.toString());
            }
        });
    }

    /** 客户端侧：服务端窗口标题下发（覆盖/还原本地 branding）。 */
    public static void handleWindowTitle(RawPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                ClientController.get().handleWindowTitle(
                        com.opendreamcore.protocol.message.WindowTitlePush.decode(reader(payload)));
            } catch (Exception e) {
                OpenDreamCore.LOGGER.warn("window_title 解析失败: {}", e.toString());
            }
        });
    }

    /** 客户端侧：服务端动画触发。 */
    public static void handleUiAnimation(RawPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                ClientController.get().handleUiAnimation(
                        com.opendreamcore.protocol.message.UiAnimation.decode(reader(payload)));
            } catch (Exception e) {
                OpenDreamCore.LOGGER.warn("ui_animation 解析失败: {}", e.toString());
            }
        });
    }

    /** 客户端侧：服务端世界页签同步（强制切换激活页签）。 */
    public static void handleWorldTab(RawPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                ClientController.get().handleWorldTab(
                        com.opendreamcore.protocol.message.WorldTabSync.decode(reader(payload)));
            } catch (Exception e) {
                OpenDreamCore.LOGGER.warn("world_tab 解析失败: {}", e.toString());
            }
        });
    }

    /** 客户端侧：服务端世界元素状态同步（可见性/可用性）。 */
    public static void handleWorldElementState(RawPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                ClientController.get().handleWorldElementState(
                        com.opendreamcore.protocol.message.WorldElementState.decode(reader(payload)));
            } catch (Exception e) {
                OpenDreamCore.LOGGER.warn("world_element_state 解析失败: {}", e.toString());
            }
        });
    }

    /** 客户端侧：世界布局保存回执（烘焙成功/失败）。 */
    public static void handleWorldSaveAck(RawPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                ClientController.get().handleWorldSaveAck(
                        com.opendreamcore.protocol.message.WorldSaveAck.decode(reader(payload)));
            } catch (Exception e) {
                OpenDreamCore.LOGGER.warn("editor_world_ack 解析失败: {}", e.toString());
            }
        });
    }

    /** 服务端方向：收到客户端页面关闭通知（Paper 插件另行实现，这里仅记录）。 */
    public static void handlePageClose(RawPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                com.opendreamcore.protocol.message.PageClose close =
                        com.opendreamcore.protocol.message.PageClose.decode(reader(payload));
                OpenDreamCore.LOGGER.info("收到 page_close: {}", close.sessionId());
            } catch (Exception e) {
                OpenDreamCore.LOGGER.warn("page_close 解析失败: {}", e.toString());
            }
        });
    }

    /** 服务端方向（本 mod 不跑在服务端时不会触发；Paper 插件另行实现）。 */
    public static void handleReady(RawPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                Ready ready = Ready.decode(reader(payload));
                OpenDreamCore.LOGGER.info("收到 ready: 协议 v{}，模组 {}", ready.protocolVersion(), ready.modVersion());
                ReadyAck ack = new ReadyAck(Protocol.VERSION, "0.1.0", CAPABILITIES, new byte[0]);
                OdcByteArrayBuf out = new OdcByteArrayBuf();
                ack.encode(out);
                context.reply(RawPayload.of(UiChannel.readyAckType(), out.toByteArray()));
            } catch (Exception e) {
                OpenDreamCore.LOGGER.warn("ready 处理失败: {}", e.toString());
            }
        });
    }

    /** 服务端方向：收到客户端 ui_event（Paper 插件另行实现，这里仅记录）。 */
    public static void handleUiEvent(RawPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                UiEvent event = UiEvent.decode(reader(payload));
                OpenDreamCore.LOGGER.info("收到 ui_event: {} {} {}", event.sessionId(), event.elementId(), event.trigger());
            } catch (Exception e) {
                OpenDreamCore.LOGGER.warn("ui_event 解析失败: {}", e.toString());
            }
        });
    }

    /** 服务端方向：收到客户端 cloud_diff（Paper 插件另行实现）。 */
    public static void handleCloudDiff(RawPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> OpenDreamCore.LOGGER.info("收到 cloud_diff"));
    }

    /** 自定义双向通道（custom_packet）：客户端侧收到 S→C → 分发订阅者；服务端方向由 Paper 插件实现。 */
    public static void handleCustomPacket(RawPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                com.opendreamcore.protocol.message.CustomPacket packet =
                        com.opendreamcore.protocol.message.CustomPacket.decode(reader(payload));
                ClientController.get().handleCustomPacket(packet.channel(), packet.payload());
            } catch (Exception e) {
                OpenDreamCore.LOGGER.warn("custom_packet 解析失败: {}", e.toString());
            }
        });
    }

    private static OdcByteBuf reader(RawPayload payload) {
        return new OdcByteArrayBuf(payload.bytes());
    }
}
