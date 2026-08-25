package com.opendreamcore.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.Command;
import com.opendreamcore.page.DisplayMode;
import com.opendreamcore.page.Page;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;

/**
 * Forge 1.20.1 客户端事件：进服握手、HUD/世界渲染、容器替换、聊天、tick、客户端命令。
 * 1.20.1 无 RenderGuiLayerEvent（NeoForge 1.21+ 才有）→ hideVanilla 仅支持整层取消，
 * 逐层隐藏为加载器能力差异（见规划文档 B6 备注）。
 */
public final class ClientEvents {

    static {
        com.opendreamcore.client.ClientController.setClientVersion("0.1.1");
    }

    private ClientEvents() {
    }

    /** 客户端 tick：标题每帧推进（主菜单也刷新）；键鼠绑定边沿检测仅在进世界后。 */
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        try {
            WindowBranding.tick();
        } catch (Exception ignored) {
        }
        if (Minecraft.getInstance().player != null) {
            try {
                ClientController.get().tickBindings();
            } catch (Exception ignored) {
                // tick 内异常不拖垮游戏
            }
        }
    }

    /** 收到聊天消息：转 legacy 格式串进 chat_display 缓存。 */
    public static void onChatReceived(ClientChatReceivedEvent event) {
        ClientController.get().addChatMessage(LegacyText.toLegacy(event.getMessage()));
    }

    /** 世界全息渲染（实体渲染后一帧）。1.20.1 的 partialTick 直接是 float。 */
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            // 防崩兜底：世界面板绘制异常只丢当帧，不允许炸掉整个游戏
            try {
                var controller = ClientController.get();
                float partialTick = event.getPartialTick();
                controller.renderWorld(event.getCamera(), partialTick);
                controller.renderNameTags(event.getCamera(), partialTick);
            } catch (Throwable t) {
                ClientController.LOGGER.warn("世界渲染异常（已跳过本帧）: {}", t.toString());
                // 强制闭合可能残留的 Tesselator 缓冲
                try {
                    var b = com.mojang.blaze3d.vertex.Tesselator.getInstance().getBuilder();
                    if ((boolean) b.getClass().getMethod("building").invoke(b)) {
                        b.end();
                    }
                } catch (Throwable ignored) { }
            }
        }
    }

    /** 容器打开匹配：开箱子/背包时按 match 找本地页面（CONTAINER 模式替换原版界面）。 */
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (!(event.getScreen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen cs)) {
            return;
        }
        String target;
        if (cs instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen) {
            target = "inventory";
        } else {
            try {
                var menuType = cs.getMenu().getType();
                var id = net.minecraft.core.registries.BuiltInRegistries.MENU.getKey(menuType);
                target = id == null ? null : id.toString();
            } catch (UnsupportedOperationException e) {
                target = cs.getMenu().getClass().getSimpleName();
            }
        }
        String title = cs.getTitle().getString();
        if (target == null) {
            return;
        }
        var page = ClientController.get().localPages()
                .match(target, title, DisplayMode.CONTAINER);
        if (page == null && title != null) {
            page = ClientController.get().localPages()
                    .match(title, title, DisplayMode.CONTAINER);
        }
        if (page != null) {
            event.setCanceled(true);
            ClientController.get().open(page);
        }
    }

    /** HUD 常驻渲染。容器页打开时取消原版 HUD 整层（1.20.1 无逐层事件）。 */
    public static void onRenderGui(RenderGuiEvent.Pre event) {
        var mc = Minecraft.getInstance();
        if (mc.screen instanceof OdcScreen odc
                && odc.page().displayMode() == DisplayMode.CONTAINER) {
            event.setCanceled(true);
            return;
        }
        var controller = ClientController.get();
        controller.renderHud(event.getGuiGraphics());
        HudLogo.render(event.getGuiGraphics()); // D2 logo_hud
        controller.renderWorldArrows(event.getGuiGraphics(), mc.gameRenderer.getMainCamera());
    }

    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        // 本地位置记忆和编辑数据（始终加载）；本地页面延迟到 handleReadyAck 按服务端配置加载
        var controller = ClientController.get();
        controller.loadPositions();
        controller.elementEdits().load();
        controller.markLogin();
        // 服务端标题缓存预载（首包前生效，消除空窗）
        controller.preloadServerTitle();
        controller.sendReady();
        controller.requestTooltips();
    }

    /** 断线/退出服务器：解除服务端标题覆盖，还原本地 branding。 */
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientController.get().clearServerTitle();
    }

    /** 客户端命令树：/odc open|close|hud|edit|list。 */
    public static void onRegisterCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register((com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack>)
                com.opendreamcore.client.OdcCommands.buildRoot());
    }

    /** 客户端可用的脚本方法（服务端裁决类方法在插件侧注册）。入口在 ClientSetup 调用。 */
    public static void registerScriptMethods() {
        com.opendreamcore.script.MethodRegistry.registerOrReplace("发送消息", args -> {
            if (args.length > 0 && args[0] != null) {
                Minecraft.getInstance().player.displayClientMessage(
                        Component.literal(String.valueOf(args[0])), false);
            }
            return null;
        });
        com.opendreamcore.script.MethodRegistry.registerOrReplace("关闭页面", args -> {
            ClientController.get().close();
            return null;
        });
        com.opendreamcore.script.MethodRegistry.registerOrReplace("打开页面", args -> {
            if (args.length > 0 && args[0] != null) {
                ClientController.get().open(ClientController.get().localPages().get(String.valueOf(args[0])));
            }
            return null;
        });
        com.opendreamcore.script.MethodRegistry.registerOrReplace("设置变量", args -> {
            if (args.length >= 2 && args[0] != null) {
                ClientController.get().setPageVar(String.valueOf(args[0]), args[1]);
            }
            return null;
        });
        com.opendreamcore.script.MethodRegistry.registerOrReplace("刷新", args -> {
            ClientController.get().refreshCurrent();
            return null;
        });
    }

    /**
     * 连接服务器时将 /odc 命令转发到服务端执行（单人世界返回 false 走本地逻辑）。
     * 直接发送命令协议包绕过客户端命令调度器，防止 /odc 自匹配导致无限递归。
     */
    private static boolean forwardToServerIfConnected(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx,
                                                      String subCommand)  {
        // 一链路：转发实现在共享树 ClientController，版本差异由其内部反射吸收
        return ClientController.get().tryForwardOdcCommand(subCommand);
    }
}
