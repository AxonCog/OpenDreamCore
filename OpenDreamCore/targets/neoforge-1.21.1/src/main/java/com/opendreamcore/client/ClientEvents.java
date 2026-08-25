package com.opendreamcore.client;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.opendreamcore.OpenDreamCore;
import com.opendreamcore.page.Page;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.nio.file.Path;

/**
 * 客户端侧事件：进服握手、本地页面目录加载、/odc 调试命令。
 * game bus 事件在新版 NeoForge 里不能用注解订阅，统一在这里手动挂。
 */
@EventBusSubscriber(modid = OpenDreamCore.MODID, value = Dist.CLIENT)
public final class ClientEvents {

    static {
        com.opendreamcore.client.ClientController.setClientVersion("0.1.1");
    }

    private ClientEvents() {
    }

    @SubscribeEvent
    public static void onClientSetup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
        com.opendreamcore.script.CommonMethods.registerAll();
        ClientPlaceholders.registerAll();
        registerScriptMethods();
        ClientMethods.registerAll();
        // 网络发送：绕过 NeoForge checkPacket（连接非 NeoForge 服务端时 optional payload 会被拦截）
        // 直接构造 ServerboundCustomPayloadPacket 走 Connection.send，走 vanilla 路径
        ClientController.get().setSender((path, bytes) -> {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.getConnection() != null) {
                com.opendreamcore.network.UiChannel.sendRaw(
                        mc.getConnection().getConnection(),
                        path, bytes);
            }
        });
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
        final org.lwjgl.glfw.GLFWScrollCallbackI[] prevScroll = new org.lwjgl.glfw.GLFWScrollCallbackI[1];
        prevScroll[0] = org.lwjgl.glfw.GLFW.glfwSetScrollCallback(
                net.minecraft.client.Minecraft.getInstance().getWindow().getWindow(),
                (win, dx, dy) -> {
                    if (!ClientController.get().consumeWorldScroll(dx, dy) && prevScroll[0] != null) {
                        prevScroll[0].invoke(win, dx, dy);
                    }
                });
        NeoForge.EVENT_BUS.addListener(ClientEvents::onLoggingIn);
        NeoForge.EVENT_BUS.addListener(ClientEvents::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(ClientEvents::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(ClientEvents::onRenderGui);
        NeoForge.EVENT_BUS.addListener(ClientEvents::onRenderGuiLayer);
        NeoForge.EVENT_BUS.addListener(ClientEvents::onScreenOpening);
        NeoForge.EVENT_BUS.addListener(ClientEvents::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(ClientEvents::onChatReceived);
        NeoForge.EVENT_BUS.addListener(ClientEvents::onClientTick);
        // 本地文件监听：UI/fonts 目录自动热重载
        new UiFileWatcher().start();
        // 窗口 branding：OpenDreamCore/branding/title.txt + icon.png
        // 材质包注入 SPI：/odc pack 使用
        com.opendreamcore.client.spi.ResourcePackInjector.register(new NeoForgePackInjector());
        WindowBranding.apply();
    }

    /** 客户端 tick：键鼠绑定边沿检测（按下上报 KEY 事件给服务端）。 */
    public static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Pre event) {
        ClientController.get().tickBindings();
        WindowBranding.tick(); // title.json 打字机/轮播推进
    }

    /** 收到聊天消息：转 legacy 格式串进 chat_display 缓存（颜色码保留，服务端聊天也能显示在自定义界面）。 */
    public static void onChatReceived(net.neoforged.neoforge.client.event.ClientChatReceivedEvent event) {
        ClientController.get().addChatMessage(LegacyText.toLegacy(event.getMessage()));
    }

    /** 世界全息渲染（实体渲染后一帧）。 */
    public static void onRenderLevel(net.neoforged.neoforge.client.event.RenderLevelStageEvent event) {
        if (event.getStage() == net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            ClientController.get().renderWorld(event.getCamera(), event.getPartialTick().getGameTimeDeltaPartialTick(false));
            ClientController.get().renderNameTags(event.getCamera(), event.getPartialTick().getGameTimeDeltaPartialTick(false));
        }
    }

    /**
     * 容器打开匹配：开箱子/背包时按 match 找本地页面（CONTAINER 模式替换原版界面）。
     * 服务端页面由插件在服务端匹配下发（这里只管本地）。
     */
    public static void onScreenOpening(net.neoforged.neoforge.client.event.ScreenEvent.Opening event) {
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
                // 某些菜单（如 InventoryMenu）不支持 getType()，用类名兜底
                target = cs.getMenu().getClass().getSimpleName();
            }
        }
        String title = cs.getTitle().getString();
        if (target == null) {
            return;
        }
        var page = ClientController.get().localPages().match(target, title, com.opendreamcore.page.DisplayMode.CONTAINER);
        if (page == null && title != null) {
            page = ClientController.get().localPages().match(title, title, com.opendreamcore.page.DisplayMode.CONTAINER);
        }
        if (page != null) {
            event.setCanceled(true);
            ClientController.get().open(page);
        }
    }

    /** HUD 常驻渲染（页面在 HUD 上叠加）。 */
    public static void onRenderGui(net.neoforged.neoforge.client.event.RenderGuiEvent.Pre event) {
        // 容器页面打开时阻止原版 HUD 渲染（快捷栏物品贴图/经验条等不再穿透自定义 UI）
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.screen instanceof com.opendreamcore.client.OdcScreen odc
                && odc.page().displayMode() == com.opendreamcore.page.DisplayMode.CONTAINER) {
            event.setCanceled(true);
            return;
        }
        ClientController.get().renderHud(event.getGuiGraphics());
        ClientController.get().renderWorldArrows(event.getGuiGraphics(),
                Minecraft.getInstance().gameRenderer.getMainCamera());
        HudLogo.render(event.getGuiGraphics()); // D2 logo_hud
    }

    /** hideVanilla 页面选项：逐层取消原版 HUD 渲染（RenderGuiLayerEvent，层名 = VanillaGuiLayers）。 */
    public static void onRenderGuiLayer(net.neoforged.neoforge.client.event.RenderGuiLayerEvent.Pre event) {
        // 容器页面打开时取消所有原版 HUD 层（快捷栏物品贴图不再穿透）
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.screen instanceof com.opendreamcore.client.OdcScreen odc
                && odc.page().displayMode() == com.opendreamcore.page.DisplayMode.CONTAINER) {
            event.setCanceled(true);
            return;
        }
        if (ClientController.get().isVanillaLayerHidden(event.getName().toString())) {
            event.setCanceled(true);
        }
    }

    /** 客户端可用的脚本方法（服务端裁决类方法在插件侧注册）。 */
    private static void registerScriptMethods() {
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

    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        // 本地位置记忆和编辑数据（始终加载）
        ClientController.get().loadPositions();
        ClientController.get().elementEdits().load();
        ClientController.get().markLogin();
        // 服务端标题缓存预载（首包前生效，消除空窗）
        ClientController.get().preloadServerTitle();
        ClientController.get().sendReady();
        ClientController.get().requestTooltips();
        // 本地 UI 加载延迟到 handleReadyAck（根据服务端 allow-local-ui 配置决定）
    }

    /** 断线/退出服务器：解除服务端标题覆盖，还原本地 branding。 */
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientController.get().clearServerTitle();
    }

    public static void onRegisterCommands(RegisterClientCommandsEvent event) {
        // 客户端 /odc 命令：单人世界执行本地操作；连接服务器时转发给服务端执行
        event.getDispatcher().register((com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack>)
                com.opendreamcore.client.OdcCommands.buildRoot());
    }

    /**
     * /odc edit <pageId> 核心逻辑：
     * 1. 确保 YAML 文件存在（不存在则创建模板）
     * 2. 确保页面已加载（reload 触发）
     * 3. 打开页面
     * 4. external=true 时用外置编辑器打开 YAML
     * 5. external=false 时进入游戏内编辑模式
     */
    private static int handleEditPage(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx,
                                      String pageId, boolean external, String editorCmd) {
        var controller = ClientController.get();
        // 1. 查找页面（优先级：当前打开实例 > 服务端下发 > 本地文件）
        //    服务端页面（如加密下发的 shop）本地无 YAML 也能直接编辑
        java.nio.file.Path file = ExternalEditor.findFile(pageId);
        Page openPage = controller.isOpen() ? controller.currentPage() : null;
        boolean editOpenInstance = openPage != null
                && pageId.equals(openPage.id() == null ? "" : openPage.id());
        Page serverPage = editOpenInstance ? null : controller.serverPage(pageId);
        Page page = editOpenInstance ? openPage
                : serverPage != null ? serverPage
                : controller.localPages().get(pageId);
        if (page == null) {
            // 本地也没有 → 创建模板文件后重试一次
            boolean created = !java.nio.file.Files.exists(file);
            if (created) {
                ExternalEditor.ensureFile(pageId);
                java.nio.file.Path uiDir = net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath()
                        .resolve("OpenDreamCore").resolve("UI");
                controller.localPages().load(uiDir);
                page = controller.localPages().get(pageId);
            }
            if (page == null) {
                ctx.getSource().sendFailure(Component.literal(
                        "§c页面加载失败: §f" + pageId + " §7(检查 YAML 语法)"));
                return 0;
            }
        }
        // 2. 打开页面（编辑已打开实例时跳过：不重复 open / 不替换会话）
        if (!editOpenInstance && (!controller.isOpen() || controller.currentPage() != page)) {
            controller.open(page);
        }
        // 4. 外置编辑器 or 游戏内编辑
        if (external) {
            boolean ok;
            if (editorCmd != null && !editorCmd.isBlank()) {
                ok = ExternalEditor.openWith(editorCmd, pageId);
            } else {
                ok = ExternalEditor.open(pageId);
            }
            if (ok) {
                ctx.getSource().sendSuccess(() -> Component.literal(
                        "§a外置编辑器已打开: §f" + file + "\n" +
                        "§7保存后游戏自动热重载"), false);
            } else {
                ctx.getSource().sendFailure(Component.literal(
                        "§c无法打开外置编辑器 §7(试试 /odc edit " + pageId + " with code)"));
            }
            // 同时进入游戏内编辑模式（双窗口协作）
            controller.toggleEdit(true);
        } else {
            controller.toggleEdit(true);
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "§a编辑模式已开启: §f" + pageId + "\n" +
                    "§7拖动元素 | Del删除 | Ctrl+C复制 | [ ]调Z | Ctrl+E导出YAML\n" +
                    "§7/odc edit " + pageId + " external §8→ 外置编辑器"), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    /**
     * 连接服务器时将 /odc 命令转发到服务端执行（单人世界返回 false 走本地逻辑）。
     * 直接发送 ServerboundChatCommandPacket 绕过客户端命令调度器，防止 /odc 自匹配导致无限递归。
     */
    private static boolean forwardToServerIfConnected(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx,
                                                       String subCommand)  {
        // 一链路：转发实现在共享树 ClientController，版本差异由其内部反射吸收
        return ClientController.get().tryForwardOdcCommand(subCommand);
    }
}
