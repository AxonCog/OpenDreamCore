package com.opendreamcore.plugin.server;

import com.opendreamcore.plugin.OpenDreamCorePlugin;
import com.opendreamcore.protocol.message.TooltipRegistry;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * tooltip 注册表。
 * 语法迁移自23年梦想核心与26年梦想核心正式版。
 * 作者：梦幻 QQ:2496599413
 *
 * 加载来源：
 * 1. tooltip/ 文件夹（多文件，按文件名排序，后覆盖先）
 * 2. tooltips.yml（旧单文件，向后兼容）
 *
 * 文件内容：键 = 元素 id，值 = 文本或样式对象
 * 样式对象：{text, color, background, border, width, permission}
 * permission 非空时仅持有该权限的玩家可见
 */
public final class TooltipManager {

    private final OpenDreamCorePlugin plugin;
    private final Map<String, TooltipRegistry.Entry> tooltips = new ConcurrentHashMap<>();

    public TooltipManager(OpenDreamCorePlugin plugin) {
        this.plugin = plugin;
    }

    /** 先扫 tooltip/ 文件夹，再兼容旧 tooltips.yml */
    public void load() {
        tooltips.clear();
        File dir = new File(plugin.getDataFolder(), "tooltip");
        if (dir.isDirectory()) {
            File[] files = dir.listFiles((d, name) ->
                    name.endsWith(".yml") || name.endsWith(".yaml"));
            if (files != null) {
                java.util.Arrays.sort(files, java.util.Comparator.comparing(File::getName));
                for (File file : files) {
                    loadFile(file);
                }
            }
        }
        // 兼容旧单文件 tooltips.yml
        File legacy = new File(plugin.getDataFolder(), "tooltips.yml");
        if (legacy.isFile()) {
            loadFile(legacy);
        }
        if (!dir.exists()) {
            dir.mkdirs();
        }
        plugin.getLogger().info("tooltip 已加载 " + tooltips.size() + " 条"
                + (dir.isDirectory() ? "（tooltip/ 文件夹）" : ""));
    }

    /** 从单个 YAML 文件加载 tooltip（重复 key 覆盖） */
    private void loadFile(File file) {
        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            for (String key : config.getKeys(false)) {
                Object raw = config.get(key);
                if (raw instanceof Map<?, ?> m) {
                    Object textV = m.get("text");
                    if (textV == null) {
                        textV = m.get("content");
                    }
                    tooltips.put(key, new TooltipRegistry.Entry(key,
                            String.valueOf(textV == null ? "" : textV),
                            str(m.get("color")), str(m.get("background")),
                            str(m.get("border")),
                            m.get("width") instanceof Number n ? n.doubleValue() : 0,
                            str(m.get("permission"))));
                } else {
                    tooltips.put(key, new TooltipRegistry.Entry(key, String.valueOf(raw)));
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("tooltip 文件加载失败 " + file.getName() + ": " + e);
        }
    }

    private static String str(Object v) {
        if (v == null) {
            return null;
        }
        String s = String.valueOf(v);
        return s.isEmpty() ? null : s;
    }

    /** 注册纯文本 tooltip */
    public void register(String elementId, String text) {
        tooltips.put(elementId, new TooltipRegistry.Entry(elementId, text));
    }

    /** 注册带样式 tooltip，permission 非空 = 权限白名单 */
    public void registerStyled(String elementId, String text, String color, String background,
                               String border, double width, String permission) {
        tooltips.put(elementId, new TooltipRegistry.Entry(elementId, text, color, background,
                border, width, permission));
    }

    public void unregister(String elementId) {
        tooltips.remove(elementId);
    }

    /** 注册的条目（无则 null） */
    public TooltipRegistry.Entry entryOf(String elementId) {
        return tooltips.get(elementId);
    }

    /** 注册的提示文本（无则 null） */
    public String tooltipOf(String elementId) {
        TooltipRegistry.Entry entry = tooltips.get(elementId);
        return entry == null ? null : entry.text();
    }

    /** 构建全量注册表消息，按玩家过滤权限白名单 */
    public TooltipRegistry buildRegistry(Player player) {
        List<TooltipRegistry.Entry> entries = new ArrayList<>();
        tooltips.forEach((id, entry) -> {
            if (entry.permission() != null && !entry.permission().isEmpty()
                    && !player.hasPermission(entry.permission())) {
                return;
            }
            entries.add(entry);
        });
        return new TooltipRegistry(entries);
    }
}
