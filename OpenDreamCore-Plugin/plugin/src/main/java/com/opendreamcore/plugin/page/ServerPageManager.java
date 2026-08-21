package com.opendreamcore.plugin.page;

import com.opendreamcore.config.PageSchema;
import com.opendreamcore.config.YamlParser;
import com.opendreamcore.page.Page;
import com.opendreamcore.plugin.OpenDreamCorePlugin;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端页面仓库：加载 plugins/OpenDreamCore/UI/*.yaml。
 * 页面目录热加载（reload 命令）；下发时把 YAML 原文推给客户端。
 */
public final class ServerPageManager {

    private final OpenDreamCorePlugin plugin;
    private final Map<String, Page> pages = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> irst = new ConcurrentHashMap<>();
    /** 世界面板位置覆盖（拖拽落点持久化）：页面 id → 元素 id → [x, y, z]。 */
    private final Map<String, Map<String, double[]>> worldPositions = new ConcurrentHashMap<>();
    private Path uiDir;

    /** 加载阶段用的编译上下文：保留所有元素（condition 不剔除），字符串原样返回。
     *  条件剔除和占位符替换只在 compiledYaml() 下发时按玩家上下文做。 */
    private static final com.opendreamcore.config.GuiCompiler.Context LOAD_ALL_KEEP =
            new com.opendreamcore.config.GuiCompiler.Context() {
                @Override public boolean condition(String expr) { return true; }
                @Override public String resolve(String text) { return text; }
            };

    public ServerPageManager(OpenDreamCorePlugin plugin) {
        this.plugin = plugin;
    }

    /** 扫描页面目录（不存在就创建，不报错）。 */
    public void load() {
        pages.clear();
        uiDir = plugin.getDataFolder().toPath().resolve("UI");
        if (!Files.isDirectory(uiDir)) {
            try {
                Files.createDirectories(uiDir);
            } catch (IOException e) {
                plugin.getLogger().warning("UI 目录创建失败: " + e);
                return;
            }
        }
        List<Path> files;
        try (var stream = Files.list(uiDir)) {
            files = stream.filter(p -> {
                String name = p.getFileName().toString();
                return name.endsWith(".yaml") || name.endsWith(".yml");
            }).sorted().toList();
        } catch (IOException e) {
            plugin.getLogger().warning("UI 目录读取失败: " + e);
            return;
        }
        if (files.isEmpty()) {
            try (var stream = Files.list(uiDir)) {
                files = stream.filter(p -> {
                    String name = p.getFileName().toString();
                    return name.endsWith(".yaml") || name.endsWith(".yml");
                }).sorted().toList();
            } catch (IOException ignored) {
                files = List.of();
            }
        }
        // 两阶段：先解析全部 IR（import 模板跨页面解析需要全量），再逐个展开构建
        irst.clear();
        Map<String, Map<String, Object>> parsed = new java.util.LinkedHashMap<>();
        for (Path file : files) {
            try {
                String yaml = Files.readString(file, StandardCharsets.UTF_8);
                String id = file.getFileName().toString().replaceFirst("\\.(yaml|yml)$", "");
                parsed.put(id, new YamlParser().parse(yaml));
            } catch (Exception e) {
                plugin.getLogger().warning("页面解析失败 " + file.getFileName() + ": " + e.getMessage());
            }
        }
        irst.putAll(parsed);
        for (Map.Entry<String, Map<String, Object>> entry : parsed.entrySet()) {
            try {
                Map<String, Object> expanded = com.opendreamcore.page.PageImporter.expand(entry.getValue(), parsed::get);
                // 扁平语法（elements/lines）需要先经 GuiCompiler 编译成标准嵌套 IR，
                // 否则 PageSchema.build 不认识 elements 列表，元素会全部丢失。
                Map<String, Object> ir = com.opendreamcore.config.GuiCompiler.isFlat(expanded)
                        ? com.opendreamcore.config.GuiCompiler.compile(expanded, LOAD_ALL_KEEP)
                        : expanded;
                Page page = PageSchema.build(entry.getKey(), ir);
                pages.put(page.id() == null ? entry.getKey() : page.id(), page);
                plugin.getLogger().info("页面已加载: " + (page.id() == null ? entry.getKey() : page.id())
                        + " (" + page.elements().size() + " 元素"
                        + (com.opendreamcore.config.GuiCompiler.isFlat(entry.getValue()) ? "，扁平语法" : "") + ")");
            } catch (Exception e) {
                plugin.getLogger().warning("页面加载失败 " + entry.getKey() + ": " + e.getMessage());
            }
        }
        loadWorldPositions();
        applyWorldPositions();
    }

    // ========== 世界面板位置持久化（拖拽落点 overlay） ==========

    /** 世界位置文件：plugins/OpenDreamCore/world_positions.json。 */
    private Path worldPositionsFile() {
        return plugin.getDataFolder().toPath().resolve("world_positions.json");
    }

    /** 启动/重载时加载位置覆盖。 */
    public void loadWorldPositions() {
        worldPositions.clear();
        Path file = worldPositionsFile();
        if (!java.nio.file.Files.isRegularFile(file)) {
            return;
        }
        try {
            String json = java.nio.file.Files.readString(file, StandardCharsets.UTF_8);
            com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            root.entrySet().forEach(pageEntry -> {
                Map<String, double[]> positions = new ConcurrentHashMap<>();
                pageEntry.getValue().getAsJsonObject().entrySet().forEach(elEntry -> {
                    var arr = elEntry.getValue().getAsJsonArray();
                    positions.put(elEntry.getKey(), new double[]{
                            arr.get(0).getAsDouble(), arr.get(1).getAsDouble(), arr.get(2).getAsDouble()});
                });
                worldPositions.put(pageEntry.getKey(), positions);
            });
            plugin.getLogger().info("世界面板位置已加载: " + worldPositions.size() + " 页");
        } catch (Exception e) {
            plugin.getLogger().warning("世界面板位置加载失败: " + e);
        }
    }

    /** 把已保存的位置应用到全部页面元素（重启后拖拽结果仍在）。 */
    public void applyWorldPositions() {
        for (Map.Entry<String, Map<String, double[]>> pageEntry : worldPositions.entrySet()) {
            Page page = pages.get(pageEntry.getKey());
            if (page == null) {
                continue;
            }
            pageEntry.getValue().forEach((elementId, pos) -> {
                com.opendreamcore.page.Element element = findElement(page, elementId);
                if (element == null) {
                    return;
                }
                Object raw = element.props().get("hologram");
                java.util.Map<Object, Object> holo = new java.util.LinkedHashMap<>(
                        raw instanceof java.util.Map<?, ?> m ? (java.util.Map<?, ?>) m : java.util.Map.of());
                holo.put("x", pos[0]);
                holo.put("y", pos[1]);
                holo.put("z", pos[2]);
                element.props().put("hologram", holo);
            });
        }
    }

    /** 保存单个元素位置（拖拽落点/脚本移动后调用，立即落盘）。 */
    public void saveWorldPosition(String pageId, String elementId, double x, double y, double z) {
        worldPositions.computeIfAbsent(pageId, k -> new ConcurrentHashMap<>())
                .put(elementId, new double[]{x, y, z});
        persistWorldPositions();
    }

    /** 当前位置覆盖（/odc world list 用）。 */
    public Map<String, Map<String, double[]>> worldPositions() {
        return worldPositions;
    }

    /** 清除位置覆盖（null = 全部；否则按页面/元素）。 */
    public void resetWorldPositions(String pageId, String elementId) {
        if (pageId == null) {
            worldPositions.clear();
        } else if (elementId == null) {
            worldPositions.remove(pageId);
        } else {
            Map<String, double[]> positions = worldPositions.get(pageId);
            if (positions != null) {
                positions.remove(elementId);
            }
        }
        persistWorldPositions();
    }

    private void persistWorldPositions() {
        try {
            var out = new com.google.gson.JsonObject();
            worldPositions.forEach((pid, positions) -> {
                var pageObj = new com.google.gson.JsonObject();
                positions.forEach((eid, pos) -> {
                    var arr = new com.google.gson.JsonArray();
                    arr.add(pos[0]);
                    arr.add(pos[1]);
                    arr.add(pos[2]);
                    pageObj.add(eid, arr);
                });
                out.add(pid, pageObj);
            });
            java.nio.file.Files.writeString(worldPositionsFile(),
                    new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(out),
                    StandardCharsets.UTF_8);
        } catch (Exception e) {
            plugin.getLogger().warning("世界面板位置保存失败: " + e);
        }
    }

    /** 页面元素递归查找（位置应用用）。 */
    private static com.opendreamcore.page.Element findElement(Page page, String id) {
        for (com.opendreamcore.page.Element e : page.elements()) {
            com.opendreamcore.page.Element found = findElement(e, id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static com.opendreamcore.page.Element findElement(com.opendreamcore.page.Element element, String id) {
        if (element.id().equals(id)) {
            return element;
        }
        for (com.opendreamcore.page.Element child : element.children()) {
            com.opendreamcore.page.Element found = findElement(child, id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /**
     * 页面下发内容（按玩家编译）：
     * 扁平语法（elements/lines）→ GuiCompiler 编译（条件剔除 + 占位符/PAPI 替换）→ YAML 序列化；
     * 标准嵌套语法 → 原文下发（占位符由客户端按各自上下文解析）。
     */
    public String compiledYaml(String pageId, org.bukkit.entity.Player player) {
        Map<String, Object> ir = irst.get(pageId);
        if (ir == null) {
            return null;
        }
        if (!com.opendreamcore.config.GuiCompiler.isFlat(ir)) {
            return yamlOf(pageId);
        }
        Map<String, Object> compiled = com.opendreamcore.config.GuiCompiler.compile(ir, new GuiContext(player));
        return new org.yaml.snakeyaml.Yaml(dumpOptions()).dump(compiled);
    }

    /** 序列化选项：全部字符串加引号（"true"/数字等不会被重解析成布尔/数字）。 */
    private static org.yaml.snakeyaml.DumperOptions dumpOptions() {
        org.yaml.snakeyaml.DumperOptions options = new org.yaml.snakeyaml.DumperOptions();
        options.setDefaultScalarStyle(org.yaml.snakeyaml.DumperOptions.ScalarStyle.DOUBLE_QUOTED);
        options.setIndent(2);
        return options;
    }

    /** 编译上下文：条件用 DreamLang 求值（player.xxx 注入）；字符串做占位符 + PAPI 替换。 */
    private static final class GuiContext implements com.opendreamcore.config.GuiCompiler.Context {
        private final org.bukkit.entity.Player player;

        GuiContext(org.bukkit.entity.Player player) {
            this.player = player;
        }

        @Override
        public boolean condition(String expr) {
            try {
                com.opendreamcore.script.Scope scope = new com.opendreamcore.script.Scope();
                if (player != null) {
                    scope.assignPlayer("name", player.getName());
                    scope.assignPlayer("uuid", player.getUniqueId().toString());
                    scope.assignPlayer("health", (double) player.getHealth());
                    scope.assignPlayer("level", (double) player.getLevel());
                }
                Object result = com.opendreamcore.script.DreamLang.evaluate(expr, scope);
                return result instanceof Boolean b ? b : result != null;
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        public String resolve(String text) {
            String resolved = com.opendreamcore.plugin.server.ServerPlaceholders.resolveFor(player, text);
            return papi(player, resolved);
        }

        /** PlaceholderAPI 可选集成（装了才生效；%token% 语法）。 */
        private static String papi(org.bukkit.entity.Player player, String text) {
            if (text == null || !text.contains("%")) {
                return text;
            }
            try {
                Class<?> api = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                Object result = api.getMethod("setPlaceholders",
                                org.bukkit.entity.Player.class, String.class)
                        .invoke(null, player, text);
                return result == null ? text : String.valueOf(result);
            } catch (Throwable ignored) {
                return text;
            }
        }
    }

    public Page get(String pageId) {
        return pages.get(pageId);
    }

    /** 所有已加载页面（用于进服扫描下发）。 */
    public java.util.Collection<Page> allPages() {
        return pages.values();
    }

    /** 页面 YAML 原文（下发用；保留注释与原始排版）。 */
    public String yamlOf(String pageId) {
        Page page = pages.get(pageId);
        if (page == null) {
            return null;
        }
        // 尝试 .yaml 和 .yml 两种后缀
        for (String suffix : new String[]{".yaml", ".yml"}) {
            Path file = uiDir.resolve(pageId + suffix);
            if (Files.isRegularFile(file)) {
                try {
                    return Files.readString(file, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    return null;
                }
            }
        }
        return null;
    }

    /** 按 match 找第一个命中的页面（进服/开容器时调用）。 */
    public Page match(String target, String title) {
        return match(target, title, null);
    }

    /** 按 match 找第一个命中的页面；player 非空时 when 表达式可用 player.xxx。 */
    public Page match(String target, String title, org.bukkit.entity.Player player) {
        return pages.values().stream()
                .filter(p -> p.match() != null && matches(p.match(), target, title, player))
                .sorted((a, b) -> Integer.compare(b.match().priority(), a.match().priority()))
                .findFirst()
                .orElse(null);
    }

    private static boolean matches(com.opendreamcore.page.Match match, String target, String title,
                                   org.bukkit.entity.Player player) {
        if (match.when() != null && !match.when().isBlank()) {
            // 表达式条件：DreamLang 求值（player.xxx + 页面变量）
            try {
                com.opendreamcore.script.Scope scope = new com.opendreamcore.script.Scope();
                if (player != null) {
                    scope.assignPlayer("name", player.getName());
                    scope.assignPlayer("uuid", player.getUniqueId().toString());
                    scope.assignPlayer("health", (double) player.getHealth());
                    scope.assignPlayer("level", (double) player.getLevel());
                }
                Object result = com.opendreamcore.script.DreamLang.evaluate(match.when(), scope);
                if (!(result instanceof Boolean b) || !b) {
                    return false;
                }
            } catch (Exception e) {
                return false; // 表达式出错不匹配
            }
        }
        String m = match.target();
        if (m == null) {
            return false;
        }
        // 特殊 match 目标：按 display 模式匹配（与客户端 LocalPageManager 一致）
        if (m.equalsIgnoreCase("hud")) {
            return true; // target/title 由调用方指定为 "hud"
        }
        if (m.equalsIgnoreCase("world")) {
            return true;
        }
        if (m.equalsIgnoreCase("screen")) {
            return true;
        }
        if (m.equalsIgnoreCase("inventory") || m.equalsIgnoreCase("player")) {
            return target != null && (target.equalsIgnoreCase("inventory")
                    || target.equalsIgnoreCase("player")
                    || target.equalsIgnoreCase("minecraft:chest")
                    || target.equalsIgnoreCase("minecraft:crafting_table"));
        }
        if (m.contains(":")) {
            return target != null && m.equalsIgnoreCase(target);
        }
        return title != null && m.equalsIgnoreCase(title);
    }

    public List<String> ids() {
        List<String> ids = new ArrayList<>(pages.keySet());
        ids.sort(String::compareTo);
        return ids;
    }
}
