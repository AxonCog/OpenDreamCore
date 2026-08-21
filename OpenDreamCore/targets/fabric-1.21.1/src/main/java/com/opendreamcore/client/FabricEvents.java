package com.opendreamcore.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.opendreamcore.page.Page;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;

/**
 * Fabric 侧事件：网络发送注入、进服握手、本地页面、HUD/全息渲染、容器替换、/odc 命令。
 */
public final class FabricEvents {

    private FabricEvents() {
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

    public static void register() {
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
        final org.lwjgl.glfw.GLFWScrollCallbackI[] prevScroll = new org.lwjgl.glfw.GLFWScrollCallbackI[1];
        prevScroll[0] = org.lwjgl.glfw.GLFW.glfwSetScrollCallback(
                net.minecraft.client.Minecraft.getInstance().getWindow().getWindow(),
                (win, dx, dy) -> {
                    if (!ClientController.get().consumeWorldScroll(dx, dy) && prevScroll[0] != null) {
                        prevScroll[0].invoke(win, dx, dy);
                    }
                });
        // 本地文件监听：UI/fonts 目录自动热重载
        new UiFileWatcher().start();
        // 窗口 branding：OpenDreamCore/branding/title.txt + icon.png
        WindowBranding.apply();
        // 网络发送：Fabric 走 ClientPlayNetworking.send（SentPayload 带 type）
        ClientController.get().setSender(com.opendreamcore.network.FabricChannel::send);
        // 进服：位置记忆 + 编辑数据 + ready（本地 UI 加载延迟到 handleReadyAck）
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            ClientController.get().loadPositions();
            ClientController.get().elementEdits().load();
            ClientController.get().markLogin();
            ClientController.get().sendReady();
            ClientController.get().requestTooltips();
        });

        // HUD 常驻渲染（+ 世界面板屏幕外箭头）
        HudRenderCallback.EVENT.register((g, tickDelta) -> {
            ClientController.get().renderHud(g);
            ClientController.get().renderWorldArrows(g, Minecraft.getInstance().gameRenderer.getMainCamera());
        });

        // 客户端 tick：键鼠绑定边沿检测（按下上报 KEY 事件给服务端）
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(
                client -> ClientController.get().tickBindings());

        // 聊天消息进 chat_display 缓存（转 legacy 格式串保留颜色）
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register(
                (message, overlay) -> ClientController.get().addChatMessage(LegacyText.toLegacy(message)));

        // 世界全息（实体渲染后）
        WorldRenderEvents.AFTER_ENTITIES.register(context ->
                ClientController.get().renderWorld(context.camera(),
                        context.tickCounter().getGameTimeDeltaPartialTick(false)));

        // 容器替换：原版容器打开后，命中本地 match 就换掉
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen cs) {
                String target;
                if (cs instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen) {
                    target = "inventory";
                } else {
                    try {
                        var id = net.minecraft.core.registries.BuiltInRegistries.MENU.getKey(cs.getMenu().getType());
                        target = id == null ? null : id.toString();
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

// /odc 客户端命令：单人世界执行本地操作；连接服务器时转发给服务端执行
ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
dispatcher.register(ClientCommandManager.literal("odc")
                    .then(ClientCommandManager.literal("open")
                            .then(ClientCommandManager.argument("page", StringArgumentType.greedyString())
                                    .executes(ctx -> {
                                        String page = StringArgumentType.getString(ctx, "page");
                                        if (forwardToServerIfConnected(ctx, "open " + page)) {
                                            return 1;
                                        }
                                        Page p = ClientController.get().localPages().get(page);
                                        if (p == null) {
                                            ctx.getSource().sendError(Component.literal("没有这个页面: " + page));
                                            return 0;
                                        }
                                        ClientController.get().open(p);
                                        return 1;
                                    })))
                    .then(ClientCommandManager.literal("close")
                            .executes(ctx -> {
                                if (forwardToServerIfConnected(ctx, "close")) {
                                    return 1;
                                }
                                ClientController.get().close();
                                return 1;
                            }))
                    .then(ClientCommandManager.literal("hud")
                            .executes(ctx -> {
                                if (forwardToServerIfConnected(ctx, "hud")) {
                                    return 1;
                                }
                                var controller = ClientController.get();
                                if (controller.isHudOpen()) {
                                    controller.closeHud();
                                    ctx.getSource().sendFeedback(Component.literal("HUD 已关闭"));
                                } else {
                                    controller.autoMountHud();
                                    ctx.getSource().sendFeedback(Component.literal(
                                            controller.isHudOpen() ? "HUD 已挂载" : "没有 match: hud 的本地页面"));
                                }
                                return 1;
                            }))
                    .then(ClientCommandManager.literal("edit")
                            // /odc edit <pageId> — 打开页面 + 进入游戏内编辑模式（文件不存在则创建 .yaml）
                            // pageId 支持子路径：hud/help → OpenDreamCore/UI/hud/help.yaml
                            .then(ClientCommandManager.argument("page", StringArgumentType.string())
                                    .executes(ctx -> {
                                        String pageId = StringArgumentType.getString(ctx, "page");
                                        return handleEditPage(ctx, pageId, false, null);
                                    })
                                    // /odc edit <pageId> external — 外置编辑器打开
                                    .then(ClientCommandManager.literal("external")
                                            .executes(ctx -> {
                                                String pageId = StringArgumentType.getString(ctx, "page");
                                                return handleEditPage(ctx, pageId, true, null);
                                            }))
                                    // /odc edit <pageId> with <editor> — 指定编辑器打开
                                    .then(ClientCommandManager.literal("with")
                                            .then(ClientCommandManager.argument("editor", StringArgumentType.string())
                                                    .executes(ctx -> {
                                                        String pageId = StringArgumentType.getString(ctx, "page");
                                                        String editor = StringArgumentType.getString(ctx, "editor");
                                                        return handleEditPage(ctx, pageId, true, editor);
                                                    }))))
                            // /odc edit on [pageId] — 进入编辑模式（可带页面 id）
                            .then(ClientCommandManager.literal("on")
                                    .executes(ctx -> {
                                        // /odc edit on — 当前页面进入编辑
                                        ClientController.get().toggleEdit(true);
                                        ctx.getSource().sendFeedback(Component.literal("§a编辑模式已开启§f（拖动元素改位置 | Del删除 | Ctrl+C复制 | [ ]调Z | Ctrl+E导出YAML）"));
                                        return 1;
                                    })
                                    .then(ClientCommandManager.argument("page", StringArgumentType.string())
                                            .executes(ctx -> {
                                                // /odc edit on <pageId> — 打开指定页面 + 进入编辑
                                                String pageId = StringArgumentType.getString(ctx, "page");
                                                return handleEditPage(ctx, pageId, false, null);
                                            })
                                            .then(ClientCommandManager.literal("external")
                                                    .executes(ctx -> {
                                                        String pageId = StringArgumentType.getString(ctx, "page");
                                                        return handleEditPage(ctx, pageId, true, null);
                                                    }))
                                            .then(ClientCommandManager.literal("with")
                                                    .then(ClientCommandManager.argument("editor", StringArgumentType.string())
                                                            .executes(ctx -> {
                                                                String pageId = StringArgumentType.getString(ctx, "page");
                                                                String editor = StringArgumentType.getString(ctx, "editor");
                                                                return handleEditPage(ctx, pageId, true, editor);
                                                            })))))
                            // /odc edit off [pageId] — 退出编辑模式（可带页面 id 先打开再关闭编辑）
                            .then(ClientCommandManager.literal("off")
                                    .executes(ctx -> {
                                        ClientController.get().toggleEdit(false);
                                        ctx.getSource().sendFeedback(Component.literal("编辑模式已关闭"));
                                        return 1;
                                    })
                                    .then(ClientCommandManager.argument("page", StringArgumentType.string())
                                            .executes(ctx -> {
                                                // /odc edit off <pageId> — 打开指定页面（不进入编辑）
                                                String pageId = StringArgumentType.getString(ctx, "page");
                                                return handleEditPage(ctx, pageId, false, null);
                                            })))
                            // /odc edit hud — HUD 编辑模式
                            .then(ClientCommandManager.literal("hud")
                                    .executes(ctx -> {
                                        var controller = ClientController.get();
                                        if (controller.isHudEditMode()) {
                                            controller.setHudEditMode(false);
                                            ctx.getSource().sendFeedback(Component.literal("HUD 编辑模式已关闭"));
                                        } else {
                                            if (!controller.isHudOpen()) {
                                                controller.autoMountHud();
                                            }
                                            if (controller.isHudOpen()) {
                                                controller.setHudEditMode(true);
                                                ctx.getSource().sendFeedback(Component.literal(
                                                        "§aHUD 编辑模式已开启§f（拖动元素改位置 | ESC退出）"));
                                            } else {
                                                ctx.getSource().sendError(Component.literal("没有挂载的 HUD 页面（先 /odc hud）"));
                                                return 0;
                                            }
                                        }
                                        return 1;
                                    }))
                            // /odc edit save — 保存编辑
                            .then(ClientCommandManager.literal("save")
                                    .executes(ctx -> {
                                        ClientController.get().saveEdits();
                                        return 1;
                                    }))
                            // /odc edit lease <page> — 服务端编辑租约
                            .then(ClientCommandManager.literal("lease")
                                    .then(ClientCommandManager.argument("page", StringArgumentType.greedyString())
                                            .executes(ctx -> {
                                                String page = StringArgumentType.getString(ctx, "page");
                                                ClientController.get().requestLease(page);
                                                return 1;
                                            })))
                            // /odc edit release <page> — 释放服务端编辑租约
                            .then(ClientCommandManager.literal("release")
                                    .then(ClientCommandManager.argument("page", StringArgumentType.greedyString())
                                            .executes(ctx -> {
                                                String page = StringArgumentType.getString(ctx, "page");
                                                ClientController.get().releaseLease(page);
                                                return 1;
                                            })))
                            // /odc edit export — 导出当前页面 YAML
                            .then(ClientCommandManager.literal("export")
                                    .executes(ctx -> {
                                        var controller = ClientController.get();
                                        com.opendreamcore.page.Page current = controller.currentPage();
                                        if (current == null) {
                                            ctx.getSource().sendError(Component.literal("没有打开的页面"));
                                            return 0;
                                        }
                                        try {
                                            String yaml = com.opendreamcore.page.PageExporter.toYaml(current);
                                            String id = current.id() == null ? "page" : current.id();
                                            java.nio.file.Path file = Minecraft.getInstance().gameDirectory.toPath()
                                                    .resolve("OpenDreamCore").resolve("UI").resolve(id + "_export.yaml");
                                            java.nio.file.Files.createDirectories(file.getParent());
                                            java.nio.file.Files.writeString(file, yaml);
                                            ctx.getSource().sendFeedback(Component.literal(
                                                    "§a页面已导出: §f" + file));
                                        } catch (Exception e) {
                                            ctx.getSource().sendError(Component.literal("导出失败: " + e));
                                        }
                                        return 1;
                                    }))
                            // /odc edit（无参数）— 显示帮助
                            .executes(ctx -> {
                                ctx.getSource().sendFeedback(Component.literal(
                                        "§e=== OpenDreamCore 编辑器 ===\n" +
                                        "§f/odc edit <页面id> §7→ 打开页面 + 进入游戏内编辑\n" +
                                        "§f/odc edit <页面id> external §7→ 外置编辑器打开 YAML\n" +
                                        "§f/odc edit <页面id> with <编辑器> §7→ 指定编辑器(如 code, notepad++)\n" +
                                        "§f/odc edit hud §7→ HUD 编辑模式\n" +
                                        "§f/odc edit on/off §7→ 切换当前页面编辑模式\n" +
                                        "§f/odc edit save §7→ 保存编辑\n" +
                                        "§f/odc edit export §7→ 导出当前页面 YAML\n" +
                                        "§f/odc edit lease/release <页面> §7→ 服务端编辑租约\n" +
                                        "§7保存 YAML 后游戏自动热重载"));
                                return 1;
                            }))
                    .then(ClientCommandManager.literal("reload")
                            .executes(ctx -> {
                                if (forwardToServerIfConnected(ctx, "reload")) {
                                    return 1;
                                }
                                var controller = ClientController.get();
                                java.nio.file.Path uiDir = Minecraft.getInstance().gameDirectory.toPath()
                                        .resolve("OpenDreamCore").resolve("UI");
                                controller.localPages().load(uiDir);
                                if (controller.isOpen()) {
                                    controller.refreshCurrent();
                                }
                                WindowBranding.reload(); // 窗口标题/图标热重载
                                ctx.getSource().sendFeedback(Component.literal("本地页面已重载"));
                                return 1;
                            }))
                    .then(ClientCommandManager.literal("list")
                            .executes(ctx -> {
                                if (forwardToServerIfConnected(ctx, "list")) {
                                    return 1;
                                }
                                var ids = ClientController.get().localPages().ids();
                                ctx.getSource().sendFeedback(Component.literal(
                                        "本地页面 (" + ids.size() + "): " + String.join(", ", ids)));
                                return 1;
                            }))
                    .executes(ctx -> {
                        if (forwardToServerIfConnected(ctx, "")) {
                            return 1;
                        }
                        ctx.getSource().sendFeedback(Component.literal(
                                "用法: /odc open <页面id> | close | hud | list"));
                        return 1;
                    }));
        });
    }

    /**
     * 连接服务器时将 /odc 命令转发到服务端执行（单人世界返回 false 走本地逻辑）。
     * 直接发送 ServerboundChatCommandPacket 绕过客户端命令调度器，防止 /odc 自匹配导致无限递归。
     */
    private static boolean forwardToServerIfConnected(com.mojang.brigadier.context.CommandContext<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> ctx,
                                                       String subCommand) {
        var conn = Minecraft.getInstance().getConnection();
        if (conn == null) {
            return false; // 单人世界：走本地命令
        }
        // 直接发送命令协议包到服务端（绕过客户端命令调度器，避免 /odc 自匹配递归）
        String cmd = subCommand.isEmpty() ? "odc" : "odc " + subCommand;
        conn.send(new net.minecraft.network.protocol.game.ServerboundChatCommandPacket(cmd));
        return true;
    }

    /**
     * /odc edit <pageId> 核心逻辑：
     * 1. 确保 YAML 文件存在（不存在则创建模板）
     * 2. 确保页面已加载（reload 触发）
     * 3. 打开页面
     * 4. external=true 时用外置编辑器打开 YAML
     * 5. external=false 时进入游戏内编辑模式
     */
    private static int handleEditPage(com.mojang.brigadier.context.CommandContext<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> ctx,
                                      String pageId, boolean external, String editorCmd) {
        var controller = ClientController.get();
        // 1. 确保 YAML 文件存在
        java.nio.file.Path file = ExternalEditor.findFile(pageId);
        boolean created = !java.nio.file.Files.exists(file);
        if (created) {
            ExternalEditor.ensureFile(pageId);
            // 重载本地页面（加载新文件）
            java.nio.file.Path uiDir = Minecraft.getInstance().gameDirectory.toPath()
                    .resolve("OpenDreamCore").resolve("UI");
            controller.localPages().load(uiDir);
        }
        // 2. 查找页面：当前已打开同名页面（服务端下发或本地）时，优先编辑打开中的实例，
        //    避免用本地文件（可能是新建模板）替换掉正在显示的页面内容
        com.opendreamcore.page.Page openPage = controller.isOpen() ? controller.currentPage() : null;
        boolean editOpenInstance = openPage != null
                && pageId.equals(openPage.id() == null ? "" : openPage.id());
        com.opendreamcore.page.Page page = editOpenInstance ? openPage : controller.localPages().get(pageId);
        if (page == null) {
            ctx.getSource().sendError(Component.literal(
                    "§c页面加载失败: §f" + pageId + " §7(检查 YAML 语法)"));
            return 0;
        }
        // 3. 打开页面（编辑已打开实例时跳过：不重复 open / 不替换会话）
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
                ctx.getSource().sendFeedback(Component.literal(
                        "§a外置编辑器已打开: §f" + file + "\n" +
                        "§7保存后游戏自动热重载"));
            } else {
                ctx.getSource().sendError(Component.literal(
                        "§c无法打开外置编辑器 §7(试试 /odc edit " + pageId + " with code)"));
            }
            // 同时进入游戏内编辑模式（双窗口协作）
            controller.toggleEdit(true);
        } else {
            controller.toggleEdit(true);
            ctx.getSource().sendFeedback(Component.literal(
                    "§a编辑模式已开启: §f" + pageId + "\n" +
                    "§7拖动元素 | Del删除 | Ctrl+C复制 | [ ]调Z | Ctrl+E导出YAML\n" +
                    "§7/odc edit " + pageId + " external §8→ 外置编辑器"));
        }
        return 1;
    }
}
