package com.opendreamcore.plugin.command;

import com.opendreamcore.plugin.OpenDreamCorePlugin;
import com.opendreamcore.plugin.network.ProtocolListener;
import com.opendreamcore.plugin.page.ServerPageManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * /odc 命令：open/close/list/reload。
 */
public final class OdcCommand implements CommandExecutor, TabCompleter {

    private final OpenDreamCorePlugin plugin;
    private final ServerPageManager pages;
    private final ProtocolListener network;

    public OdcCommand(OpenDreamCorePlugin plugin, ServerPageManager pages, ProtocolListener network) {
        this.plugin = plugin;
        this.pages = pages;
        this.network = network;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sender.sendMessage("用法: /odc open <页面id> | close | list | reload");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "version" -> {
                if (args.length > 1) {
                    // /odc version <玩家名> — 查看指定玩家的客户端版本
                    org.bukkit.entity.Player target = Bukkit.getPlayerExact(args[1]);
                    if (target == null) {
                        sender.sendMessage("§c玩家不在线: " + args[1]);
                        return true;
                    }
                    var info = network.clientVersionOf(target);
                    if (info == null) {
                        sender.sendMessage("§7" + target.getName() + " §7— §8未握手（客户端未安装 OpenDreamCore）");
                    } else {
                        String sv = plugin.getDescription().getVersion();
                        boolean protoOk = info.protocolVersion() == com.opendreamcore.protocol.Protocol.VERSION;
                        boolean modOk = info.modVersion().equals(sv);
                        sender.sendMessage("§a[OpenDreamCore] §f" + target.getName());
                        sender.sendMessage("§7客户端版本: §" + (modOk ? "a" : "c") + info.modVersion());
                        sender.sendMessage("§7协议版本: §" + (protoOk ? "a" : "c") + "v" + info.protocolVersion());
                        sender.sendMessage("§7服务端版本: §a" + sv + " §7(协议 v" + com.opendreamcore.protocol.Protocol.VERSION + ")");
                        if (protoOk && modOk) {
                            sender.sendMessage("§a✓ 版本匹配");
                        } else if (!protoOk) {
                            sender.sendMessage("§c✗ 协议不匹配");
                        } else {
                            sender.sendMessage("§e⚠ 模组版本不同");
                        }
                    }
                } else {
                    // /odc version — 全部在线玩家版本概览
                    for (String line : network.versionOverview().split("\n")) {
                        sender.sendMessage(line);
                    }
                }
            }
            case "open" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("只有玩家能打开页面");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage("用法: /odc open <页面id>");
                    return true;
                }
                String id = args[1];
                String yaml = pages.compiledYaml(id, player);
                if (yaml == null) {
                    sender.sendMessage("没有这个页面: " + id + "（用 /odc list 查看）");
                    return true;
                }
                network.openPage(player, id, yaml);
                sender.sendMessage("已下发页面 " + id);
            }
            case "close" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("只有玩家能关闭页面");
                    return true;
                }
                network.closePage(player);
                sender.sendMessage("已下发关闭指令");
            }
            case "list" -> sender.sendMessage("服务端页面 (" + pages.ids().size() + "): "
                    + String.join(", ", pages.ids()));
            case "stats" -> {
                for (String line : plugin.statsOverview().split("\n")) {
                    sender.sendMessage(line);
                }
            }
            case "reload" -> {
                pages.load();
                sender.sendMessage("页面已重载: " + pages.ids().size() + " 个");
            }
            case "world" -> {
                if (args.length < 2) {
                    sender.sendMessage("用法: /odc world list [页面] | reset [页面] [元素] | template <页面id> <board|menu|shop>");
                    return true;
                }
                switch (args[1].toLowerCase()) {
                    case "template" -> {
                        // 世界面板预设模板一键生成（board/menu/shop），生成后可 /odc edit world 微调
                        if (args.length < 3) {
                            sender.sendMessage("用法: /odc world template <页面id> <board|menu|shop>");
                            return true;
                        }
                        String pageId = args[2];
                        String tpl = args.length > 3 ? args[3].toLowerCase() : "board";
                        if (!"board".equals(tpl) && !"menu".equals(tpl) && !"shop".equals(tpl)) {
                            sender.sendMessage("未知模板: " + args[3] + "（可选 board/menu/shop）");
                            return true;
                        }
                        if (pages.ids().contains(pageId)) {
                            sender.sendMessage("页面已存在: " + pageId);
                            return true;
                        }
                        Path uiDir = plugin.getDataFolder().toPath().resolve("UI");
                        if (com.opendreamcore.plugin.page.WorldTemplates.write(uiDir, pageId, tpl)) {
                            pages.load();
                            sender.sendMessage("已生成世界页面 " + pageId + "（模板 " + tpl + "）");
                            sender.sendMessage("进入 WYSIWYG 编辑: /odc edit world " + pageId + " [玩家]");
                        } else {
                            sender.sendMessage("页面文件写入失败（" + pageId + ".yaml 可能已存在）");
                        }
                    }
                    case "list" -> {
                        var positions = pages.worldPositions();
                        if (positions.isEmpty()) {
                            sender.sendMessage("世界面板位置覆盖为空（拖拽元素后出现）");
                            return true;
                        }
                        for (var pageEntry : positions.entrySet()) {
                            if (args.length > 2 && !args[2].equals(pageEntry.getKey())) {
                                continue;
                            }
                            for (var elEntry : pageEntry.getValue().entrySet()) {
                                double[] p = elEntry.getValue();
                                sender.sendMessage(pageEntry.getKey() + " / " + elEntry.getKey()
                                        + " -> " + String.format("%.2f, %.2f, %.2f", p[0], p[1], p[2]));
                            }
                        }
                    }
                    case "reset" -> {
                        String pageId = args.length > 2 ? args[2] : null;
                        String elementId = args.length > 3 ? args[3] : null;
                        // 受影响页面（重置前记录，用于重发）
                        java.util.List<String> affected = new java.util.ArrayList<>();
                        if (pageId == null) {
                            affected.addAll(pages.worldPositions().keySet());
                        } else {
                            affected.add(pageId);
                        }
                        pages.resetWorldPositions(pageId, elementId);
                        pages.load(); // 从 YAML 重载（清除覆盖后回到原始位置）
                        sender.sendMessage("世界面板位置已重置"
                                + (pageId == null ? "（全部）" : " (" + pageId
                                + (elementId == null ? "" : " / " + elementId) + ")"));
                        // 重发给当前打开受影响页面的玩家
                        for (Player online : Bukkit.getOnlinePlayers()) {
                            String open = network.openPageId(online);
                            if (open != null && affected.contains(open)) {
                                String yaml = pages.compiledYaml(open, online);
                                if (yaml != null) {
                                    network.openPage(online, open, yaml);
                                }
                            }
                        }
                    }
                    default -> sender.sendMessage("未知 world 子命令: " + args[1]);
                }
            }
            case "edit" -> {
                if (args.length < 2) {
                    sender.sendMessage("用法: /odc edit list | grant <页面> [玩家] | world <页面> [玩家] | revoke <页面>");
                    return true;
                }
                switch (args[1].toLowerCase()) {
                    case "list" -> sender.sendMessage(plugin.editorOverview());
                    case "grant" -> {
                        if (args.length < 3) {
                            sender.sendMessage("用法: /odc edit grant <页面> [玩家]");
                            return true;
                        }
                        sender.sendMessage(plugin.editorGrant(args[2],
                                args.length > 3 ? args[3] : sender.getName()));
                    }
                    case "world" -> {
                        // 世界面板 WYSIWYG 编辑：授予租约 + 打开世界页面（客户端进入编辑模式）
                        if (args.length < 3) {
                            sender.sendMessage("用法: /odc edit world <页面> [玩家]");
                            return true;
                        }
                        String pageId = args[2];
                        String playerName = args.length > 3 ? args[3]
                                : (sender instanceof Player p ? p.getName() : null);
                        if (playerName == null) {
                            sender.sendMessage("控制台必须指定玩家");
                            return true;
                        }
                        if (!pages.ids().contains(pageId)) {
                            sender.sendMessage("没有这个页面: " + pageId);
                            return true;
                        }
                        org.bukkit.entity.Player target = Bukkit.getPlayerExact(playerName);
                        if (target == null) {
                            sender.sendMessage("玩家不在线: " + playerName);
                            return true;
                        }
                        String result = plugin.editorGrant(pageId, playerName);
                        sender.sendMessage(result);
                        if (result.startsWith("已授予")) {
                            plugin.openPage(target, pageId);
                            sender.sendMessage("已打开 " + pageId + " 并进入世界编辑模式（拖拽元素 → 工具栏保存写回页面文件）");
                        }
                    }
                    case "revoke" -> {
                        if (args.length < 3) {
                            sender.sendMessage("用法: /odc edit revoke <页面>");
                            return true;
                        }
                        sender.sendMessage(plugin.editorRevoke(args[2], sender.getName()));
                    }
                    default -> sender.sendMessage("未知编辑子命令: " + args[1]);
                }
            }
            default -> sender.sendMessage("未知子命令: " + args[0]);
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String sub : new String[]{"open", "close", "list", "reload", "world", "edit", "stats", "version"}) {
                if (sub.startsWith(args[0])) {
                    out.add(sub);
                }
            }
        } else if (args.length == 2 && "version".equals(args[0])) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                    out.add(online.getName());
                }
            }
        } else if (args.length == 2 && "open".equals(args[0])) {
            for (String id : pages.ids()) {
                if (id.startsWith(args[1])) {
                    out.add(id);
                }
            }
        } else if (args.length == 2 && "edit".equals(args[0])) {
            for (String sub : new String[]{"list", "grant", "world", "revoke"}) {
                if (sub.startsWith(args[1])) {
                    out.add(sub);
                }
            }
        } else if (args.length == 3 && "edit".equals(args[0])
                && ("grant".equals(args[1]) || "world".equals(args[1]))) {
            for (String id : pages.ids()) {
                if (id.startsWith(args[2])) {
                    out.add(id);
                }
            }
        } else if (args.length == 2 && "world".equals(args[0])) {
            for (String sub : new String[]{"list", "reset", "template"}) {
                if (sub.startsWith(args[1])) {
                    out.add(sub);
                }
            }
        } else if (args.length == 3 && "world".equals(args[0]) && "template".equals(args[1])) {
            for (String tpl : new String[]{"board", "menu", "shop"}) {
                if (tpl.startsWith(args[2])) {
                    out.add(tpl);
                }
            }
        }
        return out;
    }
}
