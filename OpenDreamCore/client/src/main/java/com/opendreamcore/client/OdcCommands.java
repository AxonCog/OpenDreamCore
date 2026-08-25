package com.opendreamcore.client;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.network.chat.Component;
import com.opendreamcore.page.Page;
import net.minecraft.client.Minecraft;

import java.nio.file.Path;

/**
 * /odc 客户端命令树（共享定义）。
 * 全版本一个链路：分支结构、执行体、反馈文案都在这里；
 * 各 target 只需把 buildRoot().build() 注册进自家 dispatcher（泛型原生擦除，直接强转）。
 * 反馈走聊天栏（不依赖平台 Source 差异）；连服时子命令转发服务端。
 */
public final class OdcCommands {

    private OdcCommands() {
    }

    // ---- 反馈：统一走聊天栏 ----
    private static void line(String msg) {
        // 高版本 addMessage 签名带 GuiMessageTag 可变参数，走反射兼容各版本
        try {
            var chat = Minecraft.getInstance().gui.getChat();
            var comp = Component.literal(msg);
            for (var m : chat.getClass().getMethods()) {
                if (m.getName().equals("addMessage")) {
                    Class<?>[] ps = m.getParameterTypes();
                    if (ps.length == 1 && ps[0].isAssignableFrom(comp.getClass())) {
                        m.invoke(chat, comp);
                        return;
                    }
                }
            }
            chat.getClass().getMethod("addMessage", comp.getClass()).invoke(chat, comp);
        } catch (Throwable ignored) {
        }
    }

    private static void err(String msg) {
        line("§c" + msg);
    }

    private static void ok(String msg) {
        line(msg);
    }

    /** 构建完整 /odc 命令树。 */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static LiteralArgumentBuilder buildRoot() {
        var cc = ClientController.get();
        return (LiteralArgumentBuilder) ((LiteralArgumentBuilder)
                LiteralArgumentBuilder.literal("odc"))
                // ---- open <page> ----
                .then(LiteralArgumentBuilder.literal("open")
                        .then(RequiredArgumentBuilder.argument("page", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    String id = StringArgumentType.getString(ctx, "page");
                                    if (cc.tryForwardOdcCommand("open " + id)) {
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    Page page = cc.localPages().get(id);
                                    if (page == null) {
                                        err("没有这个页面: " + id);
                                        return 0;
                                    }
                                    cc.open(page);
                                    return Command.SINGLE_SUCCESS;
                                })))
                // ---- close ----
                .then(LiteralArgumentBuilder.literal("close")
                        .executes(ctx -> {
                            if (cc.tryForwardOdcCommand("close")) {
                                return Command.SINGLE_SUCCESS;
                            }
                            cc.close();
                            return Command.SINGLE_SUCCESS;
                        }))
                // ---- hud ----
                .then(LiteralArgumentBuilder.literal("hud")
                        .executes(ctx -> {
                            if (cc.tryForwardOdcCommand("hud")) {
                                return Command.SINGLE_SUCCESS;
                            }
                            if (cc.isHudOpen()) {
                                cc.closeHud();
                                ok("HUD 已关闭");
                            } else {
                                cc.autoMountHud();
                                ok(cc.isHudOpen() ? "HUD 已挂载" : "没有 match: hud 的本地页面");
                            }
                            return Command.SINGLE_SUCCESS;
                        }))
                // ---- edit 子树 ----
                .then(buildEditSubtree())
                // ---- reload ----
                .then(LiteralArgumentBuilder.literal("reload")
                        .executes(ctx -> {
                            if (cc.tryForwardOdcCommand("reload")) {
                                return Command.SINGLE_SUCCESS;
                            }
                            Path uiDir = Minecraft.getInstance().gameDirectory.toPath()
                                    .resolve("OpenDreamCore").resolve("UI");
                            cc.localPages().load(uiDir);
                            if (cc.isOpen()) {
                                cc.refreshCurrent();
                            }
                            WindowBranding.reload();
                            ok("本地页面已重载");
                            return Command.SINGLE_SUCCESS;
                        }))
                // ---- list ----
                .then(LiteralArgumentBuilder.literal("list")
                        .executes(ctx -> {
                            var msg = cc.isServerMode()
                                    ? "服务器页面 (" + cc.serverPageIds().size() + "): "
                                      + String.join(", ", cc.serverPageIds())
                                    : "本地页面 (" + cc.localPages().ids().size() + "): "
                                      + String.join(", ", cc.localPages().ids());
                            ok(msg);
                            return Command.SINGLE_SUCCESS;
                        }))
                // ---- 根帮助 ----
                .executes(ctx -> {
                    ok("""
                            §e=== OpenDreamCore ===§r
                            §f/odc open/close/hud/list/reload §7— 页面与常驻控制
                            §f/odc edit §7— 编辑器帮助
                            §7单人世界执行本地逻辑；连服自动转发服务端""");
                    return Command.SINGLE_SUCCESS;
                });
    }

    /** /odc edit 子树。 */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static LiteralArgumentBuilder buildEditSubtree() {
        var cc = ClientController.get();
        return (LiteralArgumentBuilder) ((LiteralArgumentBuilder)
                LiteralArgumentBuilder.literal("edit"))
                // edit <pageId> [external|with <editor>]
                .then(RequiredArgumentBuilder.argument("page", StringArgumentType.string())
                        .executes(ctx -> handleEditPage(StringArgumentType.getString(ctx, "page"), false, null))
                        .then(LiteralArgumentBuilder.literal("external")
                                .executes(ctx -> handleEditPage(
                                        StringArgumentType.getString(ctx, "page"), true, null)))
                        .then(LiteralArgumentBuilder.literal("with")
                                .then(RequiredArgumentBuilder.argument("editor", StringArgumentType.string())
                                        .executes(ctx -> handleEditPage(
                                                StringArgumentType.getString(ctx, "page"), true,
                                                StringArgumentType.getString(ctx, "editor"))))))
                // edit on [pageId] ...
                .then(LiteralArgumentBuilder.literal("on")
                        .executes(ctx -> {
                            cc.toggleEdit(true);
                            ok("§a编辑模式已开启§f（拖动元素改位置 | Del删除 | Ctrl+C复制 | [ ]调Z | Ctrl+E导出YAML）");
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(RequiredArgumentBuilder.argument("page", StringArgumentType.string())
                                .executes(ctx -> handleEditPage(
                                        StringArgumentType.getString(ctx, "page"), false, null))
                                .then(LiteralArgumentBuilder.literal("external")
                                        .executes(ctx -> handleEditPage(
                                                StringArgumentType.getString(ctx, "page"), true, null)))
                                .then(LiteralArgumentBuilder.literal("with")
                                        .then(RequiredArgumentBuilder.argument("editor", StringArgumentType.string())
                                                .executes(ctx -> handleEditPage(
                                                        StringArgumentType.getString(ctx, "page"), true,
                                                        StringArgumentType.getString(ctx, "editor")))))))
                // edit off [pageId]
                .then(LiteralArgumentBuilder.literal("off")
                        .executes(ctx -> {
                            cc.toggleEdit(false);
                            ok("编辑模式已关闭");
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(RequiredArgumentBuilder.argument("page", StringArgumentType.string())
                                .executes(ctx -> handleEditPage(
                                        StringArgumentType.getString(ctx, "page"), false, null))))
                // edit hud
                .then(LiteralArgumentBuilder.literal("hud")
                        .executes(ctx -> {
                            if (cc.isHudEditMode()) {
                                cc.setHudEditMode(false);
                                ok("HUD 编辑模式已关闭");
                            } else {
                                if (!cc.isHudOpen()) {
                                    cc.autoMountHud();
                                }
                                if (cc.isHudOpen()) {
                                    cc.setHudEditMode(true);
                                    ok("§aHUD 编辑模式已开启§f（拖动元素改位置 | ESC退出）");
                                } else {
                                    err("没有挂载的 HUD 页面（先 /odc hud）");
                                    return 0;
                                }
                            }
                            return Command.SINGLE_SUCCESS;
                        }))
                // edit save
                .then(LiteralArgumentBuilder.literal("save")
                        .executes(ctx -> {
                            cc.saveEdits();
                            return Command.SINGLE_SUCCESS;
                        }))
                // edit lease/release
                .then(LiteralArgumentBuilder.literal("lease")
                        .then(RequiredArgumentBuilder.argument("page", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    cc.requestLease(StringArgumentType.getString(ctx, "page"));
                                    return Command.SINGLE_SUCCESS;
                                })))
                .then(LiteralArgumentBuilder.literal("release")
                        .then(RequiredArgumentBuilder.argument("page", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    cc.releaseLease(StringArgumentType.getString(ctx, "page"));
                                    return Command.SINGLE_SUCCESS;
                                })))
                // edit export
                .then(LiteralArgumentBuilder.literal("export")
                        .executes(ctx -> {
                            Page current = cc.currentPage();
                            if (current == null) {
                                err("没有打开的页面");
                                return 0;
                            }
                            try {
                                String yaml = com.opendreamcore.page.PageExporter.toYaml(current);
                                String id = current.id() == null ? "page" : current.id();
                                Path file = Minecraft.getInstance().gameDirectory.toPath()
                                        .resolve("OpenDreamCore").resolve("UI").resolve(id + "_export.yaml");
                                java.nio.file.Files.createDirectories(file.getParent());
                                java.nio.file.Files.writeString(file, yaml);
                                ok("§a页面已导出: §f" + file);
                            } catch (Exception e) {
                                err("导出失败: " + e);
                            }
                            return Command.SINGLE_SUCCESS;
                        }))
                // edit（无参数）帮助
                .executes(ctx -> {
                    ok("""
                            §e=== OpenDreamCore 编辑器 ===§r
                            §f/odc edit <页面id> §7→ 打开页面 + 进入游戏内编辑
                            §f/odc edit <页面id> external §7→ 外置编辑器打开 YAML
                            §f/odc edit <页面id> with <编辑器> §7→ 指定编辑器(如 code, notepad++)
                            §f/odc edit hud §7→ HUD 编辑模式
                            §f/odc edit on/off §7→ 切换当前页面编辑模式
                            §f/odc edit save §7→ 保存编辑
                            §f/odc edit export §7→ 导出当前页面 YAML
                            §f/odc edit lease/release <页面> §7→ 服务端编辑租约
                            §7保存 YAML 后游戏自动热重载""");
                    return Command.SINGLE_SUCCESS;
                });
    }

    /**
     * /odc edit <pageId> 核心逻辑：
     * 打开目标页（优先当前实例 > 服务端下发 > 本地文件）+ 进入编辑模式或外置编辑器打开。
     */
    @SuppressWarnings("unchecked")
    private static int handleEditPage(String pageId, boolean external, String editorCmd) {
        var controller = ClientController.get();
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
                Path uiDir = Minecraft.getInstance().gameDirectory.toPath()
                        .resolve("OpenDreamCore").resolve("UI");
                controller.localPages().load(uiDir);
                page = controller.localPages().get(pageId);
            }
            if (page == null) {
                err("§c页面加载失败: §f" + pageId + " §7(检查 YAML 语法)");
                return 0;
            }
        }
        // 打开页面（编辑已打开实例时跳过：不重复 open / 不替换会话）
        if (!editOpenInstance && (!controller.isOpen() || controller.currentPage() != page)) {
            controller.open(page);
        }
        // 外置编辑器 or 游戏内编辑
        if (external) {
            boolean opened = editorCmd != null && !editorCmd.isBlank()
                    ? ExternalEditor.openWith(editorCmd, pageId)
                    : ExternalEditor.open(pageId);
            if (opened) {
                ok("§a外置编辑器已打开: §f" + file + "\n§7保存后游戏自动热重载");
            } else {
                err("§c无法打开外置编辑器 §7(试试 /odc edit " + pageId + " with code)");
            }
            // 双窗口协作：同时进入游戏内编辑模式
            controller.toggleEdit(true);
        } else {
            controller.toggleEdit(true);
            ok("§a编辑模式已开启: §f" + pageId + "\n"
                    + "§7拖动元素 | Del删除 | Ctrl+C复制 | [ ]调Z | Ctrl+E导出YAML\n"
                    + "§7/odc edit " + pageId + " external §8→ 外置编辑器");
        }
        return Command.SINGLE_SUCCESS;
    }
}
