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
 * /codc 客户端命令树（共享定义）。
 * 全版本一个链路：分支结构、执行体、反馈文案都在这里；
 * 各 target 只需把 buildRoot().build() 注册进自家 dispatcher（泛型原生擦除，直接强转）。
 * 反馈走聊天栏（不依赖平台 Source 差异）。
 *
 * 名字这条线别拧：模组端叫 codc，只在本地动作，碰都不碰网络；
 * /odc 是服务器插件的地盘，想驱动服务器页面就去服务器那边装插件，
 * 客户端别自作主张替人家转发——以前转发+拦截那套把 TAB 补全弄出双份，
 * 还害得聊天历史抽风，早该切割干净了。
 */
public final class OdcCommands {

    private OdcCommands() {
    }

    static {
        // 核心自己也走命令注册表，跟附属模组一个待遇：先进表，后由注册点统一遍历取树
        com.opendreamcore.client.api.CommandRegistry.register("codc", OdcCommands::buildRoot);
        // /odc：客户端本地占位，进服后原样转发给服务器插件执行（单机提示用 /codc）
        com.opendreamcore.client.api.CommandRegistry.register("odc", OdcCommands::buildServerForwardRoot);
    }

    /**
     * 把命令注册表里登记的树全塞进自家 dispatcher。
     * brigadier 泛型擦除后 register(LiteralArgumentBuilder) 直接调即可，
     * 不再反射猜方法（各版本签名都是这一个，猜反而是事故源）。
     * 返回成功注册的条数，0 说明表是空的（附属没注、核心也没注到）。
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static int registerAll(com.mojang.brigadier.CommandDispatcher dispatcher) {
        if (dispatcher == null) {
            return 0;
        }
        int n = 0;
        for (var node : com.opendreamcore.client.api.CommandRegistry.nodes()) {
            try {
                dispatcher.register(node.build());
                n++;
            } catch (Throwable t) {
                ClientController.LOGGER.warn("命令 {} 注册失败: {}", node.name(), t.toString());
            }
        }
        return n;
    }

    // 反馈：统一走聊天栏（displayClientMessage 全版本稳定，别碰 addMessage 的可变参数反射）
    private static void line(String msg) {
        var player = Minecraft.getInstance().player;
        if (player != null) {
            try {
                player.displayClientMessage(Component.literal(msg), false);
                return;
            } catch (Throwable ignored) {
                // 某些老版本签名不同，fallthrough 到反射兜底
            }
        }
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
        } catch (Throwable ignored) {
        }
    }

    private static void err(String msg) {
        line("§c" + msg);
    }

    private static void ok(String msg) {
        line(msg);
    }

    /**
     * /odc 服务器转发命令：客户端本地把整条原样发回服务器插件执行。
     * 不加这个，上服裸敲 /odc 会在客户端本地解析就报"未知或不完整的命令"
     * （服务器命令树根节点在客户端不可执行），子命令也不会自动补全。
     * 转发只此一次、不叠加 vanilla 再发一遍，不会双份执行。
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static LiteralArgumentBuilder buildServerForwardRoot() {
        return (LiteralArgumentBuilder) ((LiteralArgumentBuilder) LiteralArgumentBuilder.literal("odc"))
                .executes(ctx -> forwardOdc(""))
                .then(RequiredArgumentBuilder.argument("args", StringArgumentType.greedyString())
                        .executes(ctx -> forwardOdc(StringArgumentType.getString(ctx, "args"))));
    }

    /** 转发护栏：sendCommand 兜底路径在 NeoForge 会再进客户端命令树，靠它截断防递归。 */
    private static volatile boolean forwarding;

    private static void logForward(String full) {
        try {
            ClientController.LOGGER.info("[odc] 客户端转发服务器命令: /{}", full);
        } catch (Throwable ignored) {
        }
    }

    private static int forwardOdc(String args) {
        if (forwarding) {
            return 0; // 已在转发中（sendCommand 递归回来的那一脚），直接放弃避免栈溢出
        }
        var mc = Minecraft.getInstance();
        var conn = mc.getConnection();
        if (conn == null) {
            // 未连服：/odc 降级成本地命令（open/close/hud/list/reload 照常生效），
            // 别让玩家敲 /odc 半天没反应——连上服务器后同一句话会转发给插件
            return execLocalOdc(args);
        }
        forwarding = true;
        try {
            String full = "odc" + (args == null || args.isEmpty() ? "" : " " + args);
            logForward(full);
            sendChatCommandRaw(conn, full);
            ok("§7[OpenDreamCore] §f已发送 /" + full + " → 服务器");
            return Command.SINGLE_SUCCESS;
        } catch (Throwable t) {
            // 反射发包失败兜底：退回 sendCommand（NeoForge 会再进客户端命令树，护栏截断不递归）
            try {
                conn.sendCommand("odc" + (args == null || args.isEmpty() ? "" : " " + args));
                ok("§7[OpenDreamCore] §f已发送 /odc" + (args == null || args.isEmpty() ? "" : " " + args) + " → 服务器");
                return Command.SINGLE_SUCCESS;
            } catch (Throwable t2) {
                err("odc 转发失败: " + t2.toString());
                return 0;
            }
        } finally {
            forwarding = false;
        }
    }

        /** 未连服时 /odc 的本地降级：与 /codc 相同的页面/常驻控制动作。 */
    private static int execLocalOdc(String args) {
        if (args == null || args.isBlank()) {
            ok("§e=== OpenDreamCore ===§r\n§f/odc open|close|hud|list|reload|state §7— 本地动作（连服后自动转服务器插件执行）");
            return Command.SINGLE_SUCCESS;
        }
        String[] p = args.trim().split("\\s+", 2);
        var cc = ClientController.get();
        switch (p[0]) {
            case "open" -> {
                if (p.length < 2 || p[1].trim().isEmpty()) {
                    err("用法: /odc open <页面>");
                    return 0;
                }
                Page page = cc.localPages().get(p[1].trim());
                if (page == null) {
                    err("没有这个页面: " + p[1].trim());
                    return 0;
                }
                cc.open(page);
            }
            case "close" -> cc.close();
            case "hud" -> {
                if (cc.isHudOpen()) {
                    cc.closeHud();
                    ok("HUD 已关闭");
                } else {
                    cc.autoMountHud();
                    ok(cc.isHudOpen() ? "HUD 已挂载" : "没有 match: hud 的本地页面");
                }
            }
            case "list" -> {
                var msg = cc.isServerMode()
                        ? "服务器页面 (" + cc.serverPageIds().size() + "): " + String.join(", ", cc.serverPageIds())
                        : "本地页面 (" + cc.localPages().ids().size() + "): " + String.join(", ", cc.localPages().ids());
                ok(msg);
            }
            case "reload" -> cc.reloadAll();
            case "state" -> {
                for (String s : Diagnostics.state()) {
                    ok(s);
                }
            }
            default -> ok("§7/odc 子命令: open|close|hud|list|reload|state（本地动作）；连服后 /odc 整条转发服务器插件执行");
        }
        return Command.SINGLE_SUCCESS;
    }

    /**
     * 直发聊天命令包绕开客户端命令分发（sendCommand 会再进我们的树无限递归）。
     * 跨版本：新版 ServerboundChatCommandPacket 有 (String) 单参构造；
     * 1.20.1 及更早是 record 五参 (String, Instant, long, ArgumentSignatures, LastSeenMessages.Update)，
     * 反射挑一个能用的，别在共享树里写死构造器签名。
     */
    private static void sendChatCommandRaw(Object conn, String command) throws Exception {
        Class<?> cls = null;
        for (String cn : new String[]{
                "net.minecraft.network.protocol.game.ServerboundChatCommandPacket", // mojmap
                "net.minecraft.network.packet.c2s.play.CommandExecutionC2SPacket"}) { // yarn
            try {
                cls = Class.forName(cn);
                break;
            } catch (ClassNotFoundException ignored) {
            }
        }
        if (cls == null) {
            throw new IllegalStateException("ServerboundChatCommandPacket 找不到（mojmap/yarn 均无）");
        }
        java.lang.reflect.Constructor<?> single = null;
        java.lang.reflect.Constructor<?> any = null;
        for (var c : cls.getConstructors()) {
            if (any == null) {
                any = c;
            }
            if (c.getParameterCount() == 1 && c.getParameterTypes()[0] == String.class) {
                single = c;
                break;
            }
        }
        java.lang.reflect.Constructor<?> ctor = single != null ? single : any;
        if (ctor == null) {
            throw new IllegalStateException("ServerboundChatCommandPacket 没有可用构造器");
        }
        Object packet;
        if (ctor == single) {
            packet = ctor.newInstance(command);
        } else {
            // 老版 record：command + 时间戳 + 盐 + 空签名 + 空已读回执（类名 mojmap/yarn 双候选）
            Class<?> sigCls = forNameAny("net.minecraft.commands.arguments.ArgumentSignatures",
                    "net.minecraft.network.message.ArgumentSignatures");
            Class<?> luCls = forNameAny("net.minecraft.network.chat.LastSeenMessages$Update",
                    "net.minecraft.network.message.LastSeenMessages$Update");
            packet = ctor.newInstance(command, java.time.Instant.now(),
                    new java.util.Random().nextLong(),
                    sigCls.getField("EMPTY").get(null),
                    luCls.getField("EMPTY").get(null));
        }
        Object connection = conn.getClass().getMethod("getConnection").invoke(conn);
        connection.getClass().getMethod("send", net.minecraft.network.protocol.Packet.class)
                .invoke(connection, packet);
    }

    /** 按候选类名逐个找（mojmap/yarn 映射差异都覆盖）。 */
    private static Class<?> forNameAny(String... names) throws ClassNotFoundException {
        ClassNotFoundException last = null;
        for (String n : names) {
            try {
                return Class.forName(n);
            } catch (ClassNotFoundException e) {
                last = e;
            }
        }
        throw last;
    }

    /** 构建完整 /codc 命令树。 */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static LiteralArgumentBuilder buildRoot() {
        var cc = ClientController.get();
        return (LiteralArgumentBuilder) ((LiteralArgumentBuilder)
                LiteralArgumentBuilder.literal("codc"))
                // open <page>
                .then(LiteralArgumentBuilder.literal("open")
                        .then(RequiredArgumentBuilder.argument("page", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    String id = StringArgumentType.getString(ctx, "page");
                                    Page page = cc.localPages().get(id);
                                    if (page == null) {
                                        err("没有这个页面: " + id);
                                        return 0;
                                    }
                                    cc.open(page);
                                    return Command.SINGLE_SUCCESS;
                                })))
                // close
                .then(LiteralArgumentBuilder.literal("close")
                        .executes(ctx -> {
                            cc.close();
                            return Command.SINGLE_SUCCESS;
                        }))
                // hud
                .then(LiteralArgumentBuilder.literal("hud")
                        .executes(ctx -> {
                            if (cc.isHudOpen()) {
                                cc.closeHud();
                                ok("HUD 已关闭");
                            } else {
                                cc.autoMountHud();
                                ok(cc.isHudOpen() ? "HUD 已挂载" : "没有 match: hud 的本地页面");
                            }
                            return Command.SINGLE_SUCCESS;
                        }))
                // edit 子树
                .then(buildEditSubtree())
                // reload（客户端本地全量：页面/字体/视觉规则/主题/标题；材质包归 Ctrl+R）
                .then(LiteralArgumentBuilder.literal("reload")
                        .executes(ctx -> {
                            cc.reloadAll();
                            return Command.SINGLE_SUCCESS;
                        }))
                // list
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
                // state：本地会话/页面一览，纯本地不发包装
                .then(LiteralArgumentBuilder.literal("state")
                        .executes(ctx -> {
                            for (String line : Diagnostics.state()) ok(line);
                            return Command.SINGLE_SUCCESS;
                        }))
                // dump <page>：本地页面 YAML 转存，方便离线比对
                .then(LiteralArgumentBuilder.literal("dump")
                        .then(RequiredArgumentBuilder.argument("page", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    String msg = Diagnostics.dump(StringArgumentType.getString(ctx, "page"));
                                    if (msg.startsWith("§c")) err(msg); else ok(msg);
                                    return Command.SINGLE_SUCCESS;
                                })))
                // 根帮助
                .executes(ctx -> {
                    ok("""
                            §e=== OpenDreamCore ===§r
                            §f/codc open/close/hud/list/reload §7— 页面与常驻控制（全本地）
                            §f/codc edit §7— 编辑器帮助
                            §f/codc state/dump §7— 本地诊断与页面转存
                            §7本命令只动本地页面；服务器页面走插件的 /odc""");
                    return Command.SINGLE_SUCCESS;
                });
    }

    /** /codc edit 子树：编辑器相关的都挤在这（语义和老 OpenDreamEditor 命令一致）。 */
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
                                    err("没有挂载的 HUD 页面（先 /codc hud）");
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
                            §f/codc edit <页面id> §7→ 打开页面 + 进入游戏内编辑
                            §f/codc edit <页面id> external §7→ 外置编辑器打开 YAML
                            §f/codc edit <页面id> with <编辑器> §7→ 指定编辑器(如 code, notepad++)
                            §f/codc edit hud §7→ HUD 编辑模式
                            §f/codc edit on/off §7→ 切换当前页面编辑模式
                            §f/codc edit save §7→ 保存编辑
                            §f/codc edit export §7→ 导出当前页面 YAML
                            §f/codc edit lease/release <页面> §7→ 服务端编辑租约
                            §7保存 YAML 后游戏自动热重载""");
                    return Command.SINGLE_SUCCESS;
                });
    }

    /**
     * /codc edit <pageId> 核心逻辑：
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
                err("§c无法打开外置编辑器 §7(试试 /codc edit " + pageId + " with code)");
            }
            // 双窗口协作：同时进入游戏内编辑模式
            controller.toggleEdit(true);
        } else {
            controller.toggleEdit(true);
            ok("§a编辑模式已开启: §f" + pageId + "\n"
                    + "§7拖动元素 | Del删除 | Ctrl+C复制 | [ ]调Z | Ctrl+E导出YAML\n"
                    + "§7/codc edit " + pageId + " external §8→ 外置编辑器");
        }
        return Command.SINGLE_SUCCESS;
    }
}
