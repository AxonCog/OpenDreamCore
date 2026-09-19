package com.opendreamcore.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.opendreamcore.page.Page;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;

/**
 * Fabric 侧事件：网络发送注入、进服握手、本地页面、HUD/全息渲染、容器替换、codc 命令。
 */
public final class FabricEvents {


    private FabricEvents() {
    }

    /** 客户端可用的脚本方法（服务端裁决类方法在插件侧注册）。 */
    private static void registerScriptMethods() {
        com.opendreamcore.script.MethodRegistry.registerOrReplace("发送消息", args -> {
            if (args.length > 0 && args[0] != null) {
                Minecraft.getInstance().player.sendSystemMessage(
                        Component.literal(String.valueOf(args[0])));
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
        // 单机动态演示：改当前页面变量并刷新（服务端场景用 Screen.更新状态 走 state_patch）
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

    public static void register() {
        // 实体渲染桥（entity/model 组件 GUI 渲染）
        com.opendreamcore.client.entity.EntityViews.register(
                new com.opendreamcore.client.entity.EntityRenderBridgeImpl());
        // 物品 3D 展示桥（item_model 组件）
        com.opendreamcore.client.entity.ItemModelViews.register(
                new com.opendreamcore.client.entity.ItemModelRenderBridgeImpl());
        registerScriptMethods();
        ClientPlaceholders.registerAll(); // 占位符（脚本方法/CommonMethods 已在入口注册）
        // 文本自动高度测量（text.autoHeight / text.wrap → 布局按字体折行算高度）
        com.opendreamcore.ui.LayoutEngine.setTextAutoHeight((content, maxWidth, vars, lineHeight, fallback) -> {
            if (content == null || content.isEmpty()) {
                return fallback;
            }
            var mc = net.minecraft.client.Minecraft.getInstance();
            String resolved = UiRenderer.interpolate(null, content, vars);
            int maxPx = (int) Math.min(1_000_000, Math.max(8, maxWidth));
            String[] lines = UiRenderer.wrapLinesFlat(mc.font, resolved, maxPx);
            return Math.max(1, lines.length) * Math.max(1, lineHeight);
        });
        // 世界编辑 Alt+滚轮整体缩放（链式滚轮回调：消费时不再传给 vanilla 热键栏）
        // 注意：Fabric 的 client entrypoint 在 Minecraft 构造期间执行，此时窗口尚未创建
        // （Minecraft.getWindow() 返回 null），直接取 GLFW 句柄会 NPE。
        // 必须等 CLIENT_STARTED（客户端完全启动、窗口就绪）后再挂回调与应用 branding。
        final org.lwjgl.glfw.GLFWScrollCallbackI[] prevScroll = new org.lwjgl.glfw.GLFWScrollCallbackI[1];
        ClientLifecycleEvents.CLIENT_STARTED.register(mc -> {
            com.opendreamcore.client.spi.ResourcePackInjector.register(new FabricPackInjector());
            prevScroll[0] = org.lwjgl.glfw.GLFW.glfwSetScrollCallback(
                    mc.getWindow().handle(),
                    (win, dx, dy) -> {
                        if (!ClientController.get().consumeWorldScroll(dx, dy) && prevScroll[0] != null) {
                            prevScroll[0].invoke(win, dx, dy);
                        }
                    });
            // 本地文件监听：UI/fonts 目录自动热重载
            new UiFileWatcher().start();
            // 窗口 branding：OpenDreamCore/branding/title.txt + icon.png
            WindowBranding.apply();
        });
        // 网络发送：Fabric 走 ClientPlayNetworking.send（RawPayload 自带 type）
        ClientController.get().setSender(com.opendreamcore.network.FabricChannel::send);
        // 进服：位置记忆 + 编辑数据 + ready（本地 UI 加载延迟到 handleReadyAck）
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            ClientController.get().loadPositions();
            ClientController.get().elementEdits().load();
            ClientController.get().markLogin();
            // 服务端标题缓存预载（首包前生效，消除空窗）
            ClientController.get().preloadServerTitle();
            ClientController.get().sendReady();
            ClientController.get().requestTooltips();
        });

        // 断线：解除服务端标题覆盖，还原本地 branding
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                ClientController.get().clearServerTitle());

        // HUD 常驻渲染（+ 世界面板屏幕外箭头）。26.x：HudRenderCallback 移除 → HudElementRegistry
        net.minecraft.resources.Identifier hudId = net.minecraft.resources.Identifier.fromNamespaceAndPath("opendreamcore", "hud");
        HudElementRegistry.addLast(hudId, (g, deltaTracker) -> {
            ClientController.get().renderHud(g);
            ClientController.get().renderWorldArrows(g, Minecraft.getInstance().gameRenderer.getMainCamera());
        });

        // 客户端 tick：键鼠绑定边沿检测（按下上报 KEY 事件给服务端）
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(
                client -> {
                    try {
                        com.opendreamcore.client.WindowBranding.tick(); // 标题每帧推进（主菜单也刷新）
                    } catch (Exception ignored) {
                    }
                    if (client.player != null) {
                        try {
                            ClientController.get().tickBindings();
                        } catch (Exception ignored) {
                        }
                    }
                });

        // 聊天消息进 chat_display 缓存（转 legacy 格式串保留颜色）
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register(
                (message, overlay) -> ClientController.get().addChatMessage(LegacyText.toLegacy(message)));

        // 世界全息 + 名牌。26.x：WorldRenderEvents 移除 → LevelRenderEvents（实体/不透明特征后）
        LevelRenderEvents.AFTER_SOLID_FEATURES.register(context -> {
            var camera = context.gameRenderer().getMainCamera();
            float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
            ClientController.get().renderWorld(camera, partialTick);
            ClientController.get().renderNameTags(camera, partialTick);
        });

        // 容器替换：原版容器打开后，命中本地 match 就换掉
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen cs) {
                String target;
                if (cs instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen) {
                    target = "inventory";
                } else {
                    try {
try {
                            var id = net.minecraft.core.registries.BuiltInRegistries.MENU.getKey(cs.getMenu().getType());
                            target = id == null ? null : id.toString();
                        } catch (Throwable menuTypeFail) {
                            // 部分模组容器未注册菜单类型，getType 会抛异常；只按标题匹配
                            target = null;
                        }
                    } catch (UnsupportedOperationException e) {
                        target = cs.getMenu().getClass().getSimpleName();
                    }
                }
                String title = cs.getTitle().getString();
                if (target == null) {
                    return;
                }
                Page page = ClientController.get().localPages()
                        .match(target, title, com.opendreamcore.page.DisplayMode.CONTAINER);
                if (page == null && title != null) {
                    page = ClientController.get().localPages()
                            .match(title, title, com.opendreamcore.page.DisplayMode.CONTAINER);
                }
                if (page != null) {
                    Minecraft.getInstance().setScreen(null);
                    ClientController.get().open(page);
                }
            }
        });

// codc 客户端命令：全部本地执行，不碰服务器的 /odc
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            // 命令树全走注册表：核心的 codc 和附属自注的命令一起遍历塞进来
            com.opendreamcore.client.OdcCommands.registerAll(dispatcher);
        });
    }

}
