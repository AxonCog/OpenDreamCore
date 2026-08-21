package com.opendreamcore.plugin.server;

import com.opendreamcore.plugin.OpenDreamCorePlugin;
import com.opendreamcore.plugin.page.ServerPageManager;
import com.opendreamcore.protocol.message.EditorLease;
import org.bukkit.entity.Player;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 编辑器租约管理：同一页面同时只允许一个编辑者。
 * 保存时校验租约，写回 UI/<page>.yaml 并热重载。
 */
public final class EditorManager {

    private static final long LEASE_MS = 10 * 60 * 1000; // 10 分钟自动过期

    private final OpenDreamCorePlugin plugin;
    private final ServerPageManager pages;
    private final Map<String, Lease> leases = new ConcurrentHashMap<>();

    /** 租约：持有者 + 过期时间。 */
    private record Lease(String holder, long expiresAt) {
        boolean expired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    public EditorManager(OpenDreamCorePlugin plugin, ServerPageManager pages) {
        this.plugin = plugin;
        this.pages = pages;
    }

    /** 请求租约：没人持有（或已过期）就授予，否则拒绝。 */
    public EditorLease request(Player player, String pageId) {
        Lease current = leases.get(pageId);
        if (current != null && !current.expired() && !current.holder().equals(player.getName())) {
            return new EditorLease(EditorLease.Action.DENY, pageId, current.holder());
        }
        leases.put(pageId, new Lease(player.getName(), System.currentTimeMillis() + LEASE_MS));
        plugin.getLogger().info("编辑租约授予: " + player.getName() + " -> " + pageId);
        return new EditorLease(EditorLease.Action.GRANT, pageId, player.getName());
    }

    /** 释放租约。 */
    public void release(Player player, String pageId) {
        Lease current = leases.get(pageId);
        if (current != null && current.holder().equals(player.getName())) {
            leases.remove(pageId);
            plugin.getLogger().info("编辑租约释放: " + player.getName() + " -> " + pageId);
        }
    }

    /** 保存：校验持有者后写回页面文件并重载。 */
    public boolean save(Player player, String pageId, String yaml) {
        Lease current = leases.get(pageId);
        if (current == null || current.expired() || !current.holder().equals(player.getName())) {
            plugin.getLogger().warning("保存被拒（无租约）: " + player.getName() + " -> " + pageId);
            return false;
        }
        try {
            Path file = plugin.getDataFolder().toPath().resolve("UI").resolve(pageId + ".yaml");
            Files.createDirectories(file.getParent());
            Files.writeString(file, yaml, StandardCharsets.UTF_8);
            pages.load(); // 热重载
            plugin.getLogger().info("页面已保存: " + pageId + "（by " + player.getName() + "）");
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("页面保存失败 " + pageId + ": " + e);
            return false;
        }
    }

    /** 当前租约概览（/odc edit list）。 */
    public String overview() {
        StringBuilder sb = new StringBuilder();
        leases.forEach((pageId, lease) -> sb.append(pageId).append(" <- ").append(lease.holder())
                .append(lease.expired() ? "（已过期）" : "").append("\n"));
        return sb.length() == 0 ? "没有活跃租约" : sb.toString();
    }

    // ---------- 布局覆盖（元素位置编辑） ----------

    private static final String LAYOUT_SUFFIX = ".layout.json";

    /** 保存布局补丁（校验租约），返回是否成功。 */
    public boolean saveLayout(Player player, String pageId, java.util.List<com.opendreamcore.protocol.message.PageLayout.Entry> entries) {
        Lease current = leases.get(pageId);
        if (current == null || current.expired() || !current.holder().equals(player.getName())) {
            plugin.getLogger().warning("布局保存被拒（无租约）: " + player.getName() + " -> " + pageId);
            return false;
        }
        try {
            var root = new com.google.gson.JsonObject();
            for (var entry : entries) {
                var pos = new com.google.gson.JsonObject();
                pos.addProperty("x", entry.x());
                pos.addProperty("y", entry.y());
                root.add(entry.elementId(), pos);
            }
            Path file = plugin.getDataFolder().toPath().resolve("UI").resolve(pageId + LAYOUT_SUFFIX);
            Files.createDirectories(file.getParent());
            Files.writeString(file, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(root),
                    StandardCharsets.UTF_8);
            plugin.getLogger().info("布局已保存: " + pageId + "（by " + player.getName() + "，" + entries.size() + " 项）");
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("布局保存失败 " + pageId + ": " + e);
            return false;
        }
    }

    /** 读取页面布局覆盖（无则空列表）。 */
    public java.util.List<com.opendreamcore.protocol.message.PageLayout.Entry> loadLayout(String pageId) {
        java.util.List<com.opendreamcore.protocol.message.PageLayout.Entry> entries = new java.util.ArrayList<>();
        try {
            Path file = plugin.getDataFolder().toPath().resolve("UI").resolve(pageId + LAYOUT_SUFFIX);
            if (!Files.isRegularFile(file)) {
                return entries;
            }
            var root = com.google.gson.JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            root.entrySet().forEach(entry -> {
                var pos = entry.getValue().getAsJsonObject();
                entries.add(new com.opendreamcore.protocol.message.PageLayout.Entry(entry.getKey(),
                        pos.get("x").getAsDouble(), pos.get("y").getAsDouble()));
            });
        } catch (Exception e) {
            plugin.getLogger().warning("布局读取失败 " + pageId + ": " + e);
        }
        return entries;
    }

    // ---------- 世界面板 WYSIWYG 编辑（写回页面 YAML） ----------

    /**
     * 保存世界布局（校验租约）：把元素 hologram.x/y/z 手术式写回 UI/&lt;page&gt;.yaml
     * （保留注释与其余格式），同时清除这些元素的 world_positions 覆盖，然后热重载。
     * optionsProps：页面级选项点路径（world.background.color 等）同样烘焙进 options 块（缺级自动创建）。
     * 返回写入的元素数 + 选项键数（0 = 全部失败）。
     */
    public int saveWorldLayout(Player player, String pageId,
                               java.util.List<com.opendreamcore.protocol.message.WorldLayout.Entry> entries,
                               java.util.Map<String, String> optionsProps, String pageTitle,
                               java.util.Map<String, String> variablesProps) {
        Lease current = leases.get(pageId);
        if (current == null || current.expired() || !current.holder().equals(player.getName())) {
            plugin.getLogger().warning("世界布局保存被拒（无租约）: " + player.getName() + " -> " + pageId);
            return 0;
        }
        boolean hasEntries = entries != null && !entries.isEmpty();
        boolean hasOptions = optionsProps != null && !optionsProps.isEmpty();
        boolean hasTitle = pageTitle != null && !pageTitle.isBlank();
        boolean hasVars = variablesProps != null && !variablesProps.isEmpty();
        if (!hasEntries && !hasOptions && !hasTitle && !hasVars) {
            return 0;
        }
        int baked = bakeIntoYaml(pageId, entries, optionsProps, pageTitle, variablesProps);
        if (baked > 0) {
            // 清除这些元素的运行时覆盖 → YAML 烘焙值成为新默认（/odc world reset 也不会回退）
            if (hasEntries) {
                for (var entry : entries) {
                    pages.resetWorldPositions(pageId, entry.elementId());
                }
            }
            pages.load(); // 热重载（重新解析 YAML + 应用剩余覆盖）
            plugin.getLogger().info("世界布局已写入页面: " + pageId + "（by " + player.getName()
                    + "，" + baked + " 项/键）");
        }
        return baked;
    }

    /** 把条目的位置/属性/增删 + 页面级选项 + 页面标题 + 页面变量手术式写进页面 YAML，返回实际写入数。 */
    private int bakeIntoYaml(String pageId, java.util.List<com.opendreamcore.protocol.message.WorldLayout.Entry> entries,
                             java.util.Map<String, String> optionsProps, String pageTitle,
                             java.util.Map<String, String> variablesProps) {
        try {
            Path file = plugin.getDataFolder().toPath().resolve("UI").resolve(pageId + ".yaml");
            if (!Files.isRegularFile(file)) {
                plugin.getLogger().warning("页面文件不存在，无法烘焙: " + file);
                return 0;
            }
            java.util.List<String> lines = new java.util.ArrayList<>(Files.readAllLines(file, StandardCharsets.UTF_8));
            int changed = 0;
            if (entries != null) {
                for (var entry : entries) {
                    if ("1".equals(entry.props().get("__delete__"))) {
                        // 删除元素（整块，含其子块）
                        if (deleteElement(lines, entry.elementId())) {
                            pages.resetWorldPositions(pageId, entry.elementId());
                            changed++;
                        }
                        continue;
                    }
                    String create = entry.props().get("__create__");
                    if (create != null && !create.isBlank()) {
                        // 新增元素（客户端生成的相对缩进 0 的列表项 YAML 块）
                        if (insertElement(lines, create)) {
                            changed++;
                        }
                        continue;
                    }
                    if (bakeElement(lines, entry)) {
                        changed++;
                    }
                }
            }
            if (optionsProps != null) {
                for (java.util.Map.Entry<String, String> prop : optionsProps.entrySet()) {
                    if (bakeOptionsProp(lines, prop.getKey(), prop.getValue())) {
                        changed++;
                    }
                }
            }
            if (pageTitle != null && !pageTitle.isBlank() && bakePageTitle(lines, pageTitle)) {
                changed++;
            }
            if (variablesProps != null) {
                for (java.util.Map.Entry<String, String> prop : variablesProps.entrySet()) {
                    if (bakeVariableProp(lines, prop.getKey(), prop.getValue())) {
                        changed++;
                    }
                }
            }
            if (changed > 0) {
                Files.writeString(file, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
            }
            return changed;
        } catch (Exception e) {
            plugin.getLogger().warning("世界布局烘焙失败 " + pageId + ": " + e);
            return 0;
        }
    }

    /** 页面变量烘焙：在顶层 variables 块内设置/删除变量键（无 variables 块则创建）。 */
    private static boolean bakeVariableProp(java.util.List<String> lines, String key, String value) {
        if (key == null || key.isBlank() || key.length() > 64 || key.indexOf(':') >= 0) {
            return false;
        }
        int varIdx = findKeyLine(lines, 0, lines.size(), "variables");
        if (varIdx < 0) {
            int anchor = 0;
            while (anchor < lines.size() && (lines.get(anchor).isBlank()
                    || lines.get(anchor).trim().startsWith("#"))) {
                anchor++;
            }
            if (anchor >= lines.size()) {
                lines.add("variables:");
                varIdx = lines.size() - 1;
            } else {
                int topInd = indentOf(lines.get(anchor));
                lines.add(anchor, " ".repeat(topInd) + "variables:");
                varIdx = anchor;
            }
        } else if (indentOf(lines.get(varIdx)) != 0) {
            return false; // 嵌套的 variables 键不视为页面级
        }
        int varInd = indentOf(lines.get(varIdx));
        int lo = varIdx + 1;
        int hi = blockEnd(lines, varIdx, lines.size());
        int keyIdx = findKeyLine(lines, lo, hi, key);
        if ("__unset__".equals(value)) {
            return keyIdx >= 0 && unsetProp(lines, lo, hi, key);
        }
        if (keyIdx >= 0) {
            return setKeyValue(lines, keyIdx, key, value);
        }
        // 追加到 variables 块尾
        int at = insertAfterBlock(lines, varIdx, hi);
        lines.add(at, " ".repeat(varInd + 2) + key + ": " + yamlValue(value));
        return true;
    }

    /** 页面标题烘焙：替换顶层 title 键值；缺失则在第一个顶层键行前插入。 */
    private static boolean bakePageTitle(java.util.List<String> lines, String title) {
        int titleIdx = -1;
        for (int i = 0; i < lines.size(); i++) {
            String t = lines.get(i).trim();
            if (t.isEmpty() || t.startsWith("#")) {
                continue;
            }
            if (indentOf(lines.get(i)) == 0 && (t.startsWith("title:") || t.startsWith("title :"))) {
                titleIdx = i;
                break;
            }
        }
        if (titleIdx >= 0) {
            lines.set(titleIdx, "title: " + yamlValue(title));
            return true;
        }
        int anchor = 0;
        while (anchor < lines.size() && (lines.get(anchor).isBlank()
                || lines.get(anchor).trim().startsWith("#"))) {
            anchor++;
        }
        if (anchor >= lines.size()) {
            lines.add("title: " + yamlValue(title));
        } else {
            lines.add(anchor, "title: " + yamlValue(title));
        }
        return true;
    }

    /**
     * 页面级选项烘焙：点路径（world.background.color / world.alpha / world.follow）在 options 块内
     * 逐级下钻，缺级自动创建；__unset__ = 删除该键。返回是否修改。
     */
    private static boolean bakeOptionsProp(java.util.List<String> lines, String path, String value) {
        String[] seg = path.split("\\.");
        if (seg.length == 0) {
            return false;
        }
        // 定位顶层 options 块（无则创建，插在第一个顶层键行前）
        int optIdx = findKeyLine(lines, 0, lines.size(), "options");
        if (optIdx < 0) {
            int anchor = 0;
            while (anchor < lines.size() && (lines.get(anchor).isBlank()
                    || lines.get(anchor).trim().startsWith("#"))) {
                anchor++;
            }
            if (anchor >= lines.size()) {
                lines.add("options:");
                optIdx = lines.size() - 1;
            } else {
                int topInd = indentOf(lines.get(anchor));
                lines.add(anchor, " ".repeat(topInd) + "options:");
                optIdx = anchor;
            }
        } else if (indentOf(lines.get(optIdx)) != 0) {
            return false; // 嵌套的 options 键不视为页面级
        }
        int optInd = indentOf(lines.get(optIdx));
        int lo = optIdx + 1;
        int hi = blockEnd(lines, optIdx, lines.size());
        int parentIdx = optIdx;
        int parentInd = optInd;
        for (int s = 0; s < seg.length; s++) {
            int idx = findKeyLine(lines, lo, hi, seg[s]);
            if (idx < 0) {
                // 缺级创建：标量插在父块末尾，容器紧跟父键行
                String pad = " ".repeat(parentInd + 2);
                if (s == seg.length - 1) {
                    int at = insertAfterBlock(lines, parentIdx, hi);
                    lines.add(at, pad + seg[s] + ": " + yamlValue(value));
                    return true;
                }
                lines.add(parentIdx + 1, pad + seg[s] + ":");
                parentIdx = parentIdx + 1;
                parentInd = indentOf(lines.get(parentIdx));
                lo = parentIdx + 1;
                hi = blockEnd(lines, parentIdx, lines.size());
                continue;
            }
            if (s == seg.length - 1) {
                if ("__unset__".equals(value)) {
                    return unsetProp(lines, lo, hi, seg[s]);
                }
                if (value.indexOf('|') >= 0) {
                    return setListValue(lines, idx, seg[s], value);
                }
                return setKeyValue(lines, idx, seg[s], value);
            }
            String body = lines.get(idx).trim().substring(seg[s].length()).trim();
            if (body.indexOf('{') >= 0) {
                // 行内 map：仅支持最后一级替换（嵌套下钻不支持，跳过）
                return s == seg.length - 2
                        && replaceInlineKey(lines, idx, seg[s + 1],
                        value.indexOf('|') >= 0 ? flowListYaml(value) : value);
            }
            if (!body.isEmpty()) {
                return false; // 标量无法下钻
            }
            lo = idx + 1;
            hi = blockEnd(lines, idx, hi);
            parentIdx = idx;
            parentInd = indentOf(lines.get(idx));
        }
        return false;
    }

    /** 块内插入点：父块最后一个子行之后（父块为空 = 父键行后）。 */
    private static int insertAfterBlock(java.util.List<String> lines, int parentIdx, int limit) {
        int last = parentIdx;
        int ind = indentOf(lines.get(parentIdx));
        for (int i = parentIdx + 1; i < limit; i++) {
            String t = lines.get(i).trim();
            if (t.isEmpty() || t.startsWith("#")) {
                continue;
            }
            if (indentOf(lines.get(i)) > ind) {
                last = i;
            } else {
                break;
            }
        }
        return last + 1;
    }

    /** 删除元素块：[start, end) 整段移除（end 为下一个兄弟起点，保留）。 */
    private static boolean deleteElement(java.util.List<String> lines, String elementId) {
        int start = findElementStart(lines, elementId);
        if (start < 0) {
            return false;
        }
        int end = elementEnd(lines, start);
        lines.subList(start, end).clear();
        return true;
    }

    /**
     * 插入元素（仅扁平语法 elements: 列表）：把相对缩进 0 的列表项块
     * （"- id: xxx\n  type: ..."）插到列表末尾（下一个顶层键之前）。
     */
    private static boolean insertElement(java.util.List<String> lines, String block) {
        int elementsIdx = -1;
        for (int i = 0; i < lines.size(); i++) {
            String t = lines.get(i).trim();
            if (t.startsWith("elements:") && !t.startsWith("- ")) {
                elementsIdx = i;
                break;
            }
        }
        if (elementsIdx < 0) {
            return false; // 非扁平语法（嵌套）不支持插入
        }
        String firstLine = block.lines().findFirst().orElse("").trim();
        if (!firstLine.startsWith("- id:") && !firstLine.startsWith("-id:")) {
            return false; // 块格式非法
        }
        int base = indentOf(lines.get(elementsIdx)) + 2; // 列表项基准缩进
        // 插入点：elements 块内最后一项之后（下一个缩进 <= elements 缩进的顶层键行）
        int insertAt = lines.size();
        int elementsIndent = indentOf(lines.get(elementsIdx));
        for (int i = elementsIdx + 1; i < lines.size(); i++) {
            String t = lines.get(i).trim();
            if (t.isEmpty() || t.startsWith("#")) {
                continue;
            }
            if (indentOf(lines.get(i)) <= elementsIndent) {
                insertAt = i;
                break;
            }
        }
        java.util.List<String> newLines = new java.util.ArrayList<>();
        for (String bl : block.split("\n", -1)) {
            if (bl.isBlank()) {
                continue;
            }
            newLines.add(" ".repeat(base) + bl);
        }
        lines.addAll(insertAt, newLines);
        return true;
    }

    /** 找元素块起点：扁平语法 `- id: xxx` 或嵌套语法顶层 `xxx:`。 */
    private static int findElementStart(java.util.List<String> lines, String elementId) {
        java.util.Set<String> reserved = java.util.Set.of("title", "options", "variables", "functions",
                "elements", "animations", "match", "display", "world", "hud", "lines");
        for (int i = 0; i < lines.size(); i++) {
            String t = lines.get(i).trim();
            if (t.startsWith("#") || t.isEmpty()) {
                continue;
            }
            // 扁平：- id: xxx（id 后有空格/# 结尾）
            if (t.matches("-\\s*id:\\s*" + java.util.regex.Pattern.quote(elementId) + "(\\s|#|$)")) {
                return i;
            }
            // 嵌套：顶层 key:（排除保留键与行内注释）
            if (t.matches(java.util.regex.Pattern.quote(elementId) + ":\\s*(#.*)?$")
                    && !reserved.contains(elementId)) {
                return i;
            }
        }
        return -1;
    }

    /** 元素块结束：下一行是同级新元素/顶层键（非空非注释）即停。 */
    private static int elementEnd(java.util.List<String> lines, int start) {
        int startIndent = indentOf(lines.get(start));
        for (int i = start + 1; i < lines.size(); i++) {
            String t = lines.get(i).trim();
            if (t.isEmpty() || t.startsWith("#")) {
                continue;
            }
            int ind = indentOf(lines.get(i));
            if (ind <= startIndent) {
                // 同缩进新兄弟：扁平 `- ...` 或顶层 `key:`
                if (t.startsWith("- ") || t.startsWith("-") || ind == 0) {
                    return i;
                }
            }
        }
        return lines.size();
    }

    /** 在元素块内烘焙一个条目（位置 + 属性编辑），返回是否修改。 */
    private static boolean bakeElement(java.util.List<String> lines,
                                       com.opendreamcore.protocol.message.WorldLayout.Entry entry) {
        int start = findElementStart(lines, entry.elementId());
        if (start < 0) {
            return false;
        }
        int end = elementEnd(lines, start);
        boolean changed = false;
        if (hasHologram(lines, start, end)) {
            changed = bakePosition(lines, start, end, entry);
        }
        if (!entry.props().isEmpty()) {
            for (java.util.Map.Entry<String, String> prop : entry.props().entrySet()) {
                if ("__unset__".equals(prop.getValue())) {
                    // 删除键（解组/移除属性）：行内 map 移除该键 / 标量行整行删除
                    if (unsetProp(lines, start, end, prop.getKey())) {
                        changed = true;
                    }
                    continue;
                }
                if (prop.getKey().startsWith("actions.")
                        && insertActionsBlock(lines, start, end, prop.getKey(), prop.getValue())) {
                    changed = true;
                    continue;
                }
                if (bakeProp(lines, start, end, prop.getKey(), prop.getValue())) {
                    changed = true;
                }
            }
        }
        return changed;
    }

    /** 元素块内插入缺失的 actions 块（首次绑定动作脚本；已存在 → false 交给 bakeProp 下钻）。 */
    private static boolean insertActionsBlock(java.util.List<String> lines, int start, int end,
                                              String key, String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String actionKey = key.substring("actions.".length());
        if (actionKey.isEmpty()) {
            return false;
        }
        for (int i = start; i < end; i++) {
            if (lines.get(i).trim().startsWith("actions:")) {
                return false;
            }
        }
        int anchor = start;
        for (int i = start; i < end; i++) {
            String t = lines.get(i);
            if (!t.isBlank() && !t.trim().startsWith("#")) {
                anchor = i;
            }
        }
        int ind = indentOf(lines.get(start));
        lines.add(anchor + 1, " ".repeat(ind + 2) + actionKey + ": " + yamlValue(value));
        lines.add(anchor + 1, " ".repeat(ind) + "actions:");
        return true;
    }

    /** 删除属性键（__unset__ 约定）：行内 map 引号感知移除 / 标量行整行删除。 */
    private static boolean unsetProp(java.util.List<String> lines, int start, int end, String key) {
        int idx = findKeyLine(lines, start, end, key);
        if (idx < 0) {
            return false;
        }
        String line = lines.get(idx);
        int c0 = line.indexOf('{');
        if (c0 >= 0) {
            int c1 = line.lastIndexOf('}');
            if (c1 <= c0) {
                return false;
            }
            java.util.List<String> parts = splitFlow(line.substring(c0 + 1, c1));
            StringBuilder sb = new StringBuilder();
            boolean removed = false;
            for (String part : parts) {
                String p = part.trim();
                int colon = p.indexOf(':');
                String k = colon >= 0 ? p.substring(0, colon).trim() : "";
                if (k.equals(key)) {
                    removed = true;
                    continue;
                }
                if (!p.isEmpty()) {
                    sb.append(p).append(", ");
                }
            }
            if (!removed) {
                return false;
            }
            if (sb.length() == 0) {
                lines.set(idx, line.substring(0, c0 + 1) + " " + line.substring(c1));
            } else {
                lines.set(idx, line.substring(0, c0 + 1) + " "
                        + sb.substring(0, sb.length() - 2) + " " + line.substring(c1));
            }
            return true;
        }
        // 标量/块子行：整行删除
        lines.remove(idx);
        return true;
    }

    private static boolean hasHologram(java.util.List<String> lines, int start, int end) {
        for (int i = start; i < end; i++) {
            if (lines.get(i).trim().startsWith("hologram:")) {
                return true;
            }
        }
        return false;
    }

    /** 位置烘焙：元素 hologram.x/y/z 手术式写回（行内 map 或块式）。 */
    private static boolean bakePosition(java.util.List<String> lines, int start, int end,
                                        com.opendreamcore.protocol.message.WorldLayout.Entry entry) {
        int holoIdx = -1;
        for (int i = start; i < end; i++) {
            String t = lines.get(i).trim();
            if (t.startsWith("hologram:")) {
                holoIdx = i;
                break;
            }
        }
        if (holoIdx < 0) {
            return false;
        }
        String holoLine = lines.get(holoIdx);
        int holoIndent = indentOf(holoLine);
        String body = holoLine.trim().substring("hologram:".length()).trim();
        if (body.isEmpty()) {
            // 块式：hologram: 后跟缩进的 x:/y:/z:
            boolean any = false;
            for (int i = holoIdx + 1; i < end; i++) {
                String l = lines.get(i);
                if (l.isBlank()) {
                    continue;
                }
                if (indentOf(l) <= holoIndent) {
                    break;
                }
                String t = l.trim();
                String axis = axisOf(t);
                if (axis != null) {
                    lines.set(i, l.substring(0, indentOf(l)) + axis + ": " + fmt(axisVal(entry, axis)));
                    any = true;
                }
            }
            if (!any) {
                // 没有 x/y/z 键：在 hologram: 后补三行
                String pad = " ".repeat(holoIndent + 2);
                lines.add(holoIdx + 1, pad + "x: " + fmt(entry.x()));
                lines.add(holoIdx + 2, pad + "y: " + fmt(entry.y()));
                lines.add(holoIdx + 3, pad + "z: " + fmt(entry.z()));
            }
            return true;
        }
        // 行内流式：hologram: {x: 0, y: -1.5, z: 0, scale: 0.03}
        int c0 = holoLine.indexOf('{');
        int c1 = holoLine.lastIndexOf('}');
        if (c0 < 0 || c1 <= c0) {
            return false;
        }
        String inner = holoLine.substring(c0 + 1, c1);
        java.util.List<String> parts = splitFlow(inner);
        StringBuilder sb = new StringBuilder();
        boolean any = false;
        for (String part : parts) {
            String p = part.trim();
            String axis = axisOf(p);
            if (axis != null) {
                sb.append(axis).append(": ").append(fmt(axisVal(entry, axis))).append(", ");
                any = true;
            } else if (!p.isEmpty()) {
                sb.append(p).append(", ");
            }
        }
        if (!any) {
            // 行内没有 x/y/z：补到末尾
            sb.append("x: ").append(fmt(entry.x())).append(", y: ").append(fmt(entry.y()))
                    .append(", z: ").append(fmt(entry.z())).append(", ");
            any = true;
        }
        String rebuilt = holoLine.substring(0, c0 + 1) + " "
                + sb.substring(0, sb.length() - 2) + " " + holoLine.substring(c1);
        lines.set(holoIdx, rebuilt);
        return true;
    }

    /**
     * 属性烘焙：点路径（text.content / text.color / hologram.scale）逐级下钻，
     * 最终键替换值（行内 map 引号感知 + 块式子行都支持）；找不到键则返回 false。
     */
    private static boolean bakeProp(java.util.List<String> lines, int start, int end, String path, String value) {
        String[] seg = path.split("\\.");
        if (seg.length == 0) {
            return false;
        }
        int lo = start, hi = end;
        for (int s = 0; s < seg.length; s++) {
            int idx = findKeyLine(lines, lo, hi, seg[s]);
            if (idx < 0) {
                return false;
            }
            if (s == seg.length - 1) {
                return setKeyValue(lines, idx, seg[s], value);
            }
            // 中间段：必须是 map —— 行内 map（含 {）或块式（行尾空）
            String body = lines.get(idx).trim().substring(seg[s].length()).trim();
            if (body.indexOf('{') >= 0) {
                // 行内 map：剩余只有一级时直接在该行替换
                if (s == seg.length - 2) {
                    return replaceInlineKey(lines, idx, seg[s + 1], value);
                }
                return false;
            }
            if (!body.isEmpty()) {
                return false; // key: 标量，无法下钻
            }
            lo = idx + 1;
            hi = blockEnd(lines, idx, hi);
        }
        return false;
    }

    /** 在 [lo, hi) 找 `key:` 行（行内 map / 块式 / 标量都算），无则 -1。 */
    private static int findKeyLine(java.util.List<String> lines, int lo, int hi, String key) {
        for (int i = lo; i < hi; i++) {
            String t = lines.get(i).trim();
            if (t.startsWith(key + ":") || t.startsWith(key + " :")) {
                return i;
            }
        }
        return -1;
    }

    /** 块式 map 的下边界：第一个缩进不超过父行的非空非注释行。 */
    private static int blockEnd(java.util.List<String> lines, int parentIdx, int limit) {
        int ind = indentOf(lines.get(parentIdx));
        for (int i = parentIdx + 1; i < limit; i++) {
            String t = lines.get(i).trim();
            if (t.isEmpty() || t.startsWith("#")) {
                continue;
            }
            if (indentOf(lines.get(i)) <= ind) {
                return i;
            }
        }
        return limit;
    }

    /** 设置键的值：行内 map 替换/附加；标量行整行替换值；块式 map 不写。 */
    private static boolean setKeyValue(java.util.List<String> lines, int idx, String key, String value) {
        String line = lines.get(idx);
        String body = line.trim().substring(key.length()).trim();
        if (body.indexOf('{') >= 0) {
            return replaceInlineKey(lines, idx, key, value);
        }
        if (body.isEmpty()) {
            return false; // 块式 map（键是容器）→ 不写
        }
        int ind = indentOf(line);
        lines.set(idx, line.substring(0, ind) + key + ": " + yamlValue(value));
        return true;
    }

    /** 行内 map 中替换指定键（找不到则附加在 { 后），返回是否成功。 */
    private static boolean replaceInlineKey(java.util.List<String> lines, int idx, String key, String value) {
        String line = lines.get(idx);
        int c0 = line.indexOf('{');
        int c1 = line.lastIndexOf('}');
        if (c0 < 0 || c1 <= c0) {
            return false;
        }
        java.util.List<String> parts = splitFlow(line.substring(c0 + 1, c1));
        StringBuilder sb = new StringBuilder();
        boolean any = false;
        for (String part : parts) {
            String p = part.trim();
            int colon = p.indexOf(':');
            String k = colon >= 0 ? p.substring(0, colon).trim() : "";
            if (k.equals(key)) {
                sb.append(key).append(": ").append(yamlValue(value)).append(", ");
                any = true;
            } else if (!p.isEmpty()) {
                sb.append(p).append(", ");
            }
        }
        if (!any) {
            // 行内 map 无此键：附加在 { 后（保留注释与其余键）
            lines.set(idx, line.substring(0, c0 + 1) + " " + key + ": " + yamlValue(value) + ","
                    + line.substring(c0 + 1));
            return true;
        }
        lines.set(idx, line.substring(0, c0 + 1) + " "
                + sb.substring(0, sb.length() - 2) + " " + line.substring(c1));
        return true;
    }

    /** 行内 map 引号感知分割（逗号在引号内不分割）。 */
    private static java.util.List<String> splitFlow(String inner) {
        java.util.List<String> out = new java.util.ArrayList<>();
        StringBuilder cur = new StringBuilder();
        char quote = 0;
        for (int i = 0; i < inner.length(); i++) {
            char ch = inner.charAt(i);
            if (quote != 0) {
                cur.append(ch);
                if (ch == quote && (i == 0 || inner.charAt(i - 1) != '\\')) {
                    quote = 0;
                }
                continue;
            }
            if (ch == '\'' || ch == '"') {
                quote = ch;
                cur.append(ch);
                continue;
            }
            if (ch == ',') {
                out.add(cur.toString());
                cur = new StringBuilder();
                continue;
            }
            cur.append(ch);
        }
        out.add(cur.toString());
        return out;
    }

    /** 列表值烘焙（客户端 | 分隔约定）：块式列表转流式单行并删原子行；标量/行内 map 直接替换。 */
    private static boolean setListValue(java.util.List<String> lines, int idx, String key, String value) {
        String line = lines.get(idx);
        String body = line.trim().substring(key.length()).trim();
        String flow = flowListYaml(value);
        if (body.indexOf('{') >= 0) {
            return replaceInlineKey(lines, idx, key, flow);
        }
        int ind = indentOf(line);
        if (body.isEmpty()) {
            // 块式列表（options: 下挂 - 子行）→ 流式单行 + 删除原子行
            lines.set(idx, line.substring(0, ind) + key + ": " + flow);
            int end = blockEnd(lines, idx, lines.size());
            if (end > idx + 1) {
                lines.subList(idx + 1, end).clear();
            }
            return true;
        }
        lines.set(idx, line.substring(0, ind) + key + ": " + flow);
        return true;
    }

    /** 客户端 | 分隔列表 → YAML 流式列表（元素按 yamlValue 规则转义）。 */
    private static String flowListYaml(String v) {
        java.util.List<String> items = new java.util.ArrayList<>();
        for (String part : v.split("[|,]")) {
            String t = part.trim();
            if (!t.isEmpty()) {
                items.add(yamlValue(t));
            }
        }
        return "[" + String.join(", ", items) + "]";
    }

    /** YAML 标量安全转义：数字/颜色 hex/布尔裸放，{ 开头视为行内 map 原样写入，其余双引号转义。 */
    private static String yamlValue(String v) {
        if (v == null) {
            v = "";
        }
        if (v.matches("-?\\d+(\\.\\d+)?")) {
            return v;
        }
        if (v.matches("#[0-9A-Fa-f]{6,8}")) {
            return v;
        }
        if ("true".equals(v) || "false".equals(v)) {
            return v;
        }
        if (v.startsWith("{")) {
            return v; // 客户端传的原始行内 map（描边等复合属性）
        }
        return "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    /** 行首缩进（空格数）。 */
    private static int indentOf(String line) {
        int n = 0;
        while (n < line.length() && line.charAt(n) == ' ') {
            n++;
        }
        return n;
    }

    /** 键: 值 行 → 键（x/y/z），否则 null。 */
    private static String axisOf(String trimmed) {
        if (trimmed.startsWith("x:") || trimmed.startsWith("x :")) {
            return "x";
        }
        if (trimmed.startsWith("y:") || trimmed.startsWith("y :")) {
            return "y";
        }
        if (trimmed.startsWith("z:") || trimmed.startsWith("z :")) {
            return "z";
        }
        return null;
    }

    private static double axisVal(com.opendreamcore.protocol.message.WorldLayout.Entry entry, String axis) {
        return switch (axis) {
            case "x" -> entry.x();
            case "y" -> entry.y();
            default -> entry.z();
        };
    }

    /** 数值格式：整数不带小数点，最多 3 位小数。 */
    private static String fmt(double v) {
        double r = Math.round(v * 1000) / 1000.0;
        return r == Math.floor(r) && !Double.isInfinite(r) ? String.valueOf((long) r) : String.valueOf(r);
    }
}
