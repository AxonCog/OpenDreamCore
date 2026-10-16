package com.opendreamcore.plugin.page;

import com.opendreamcore.adapter.AdapterRegistry;
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
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端页面仓库：递归加载 plugins/OpenDreamCore/UI/ 下的 .yaml/.yml。
 * 页面 ID 为相对目录路径（不含扩展名）；下发保留已加载的 YAML 原文。
 */
public final class ServerPageManager {

    private final OpenDreamCorePlugin plugin;
    private final Object virtualLock = new Object();

    private final Map<String, Page> pages = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> irst = new ConcurrentHashMap<>();
    private final Map<String, String> diskYaml = new ConcurrentHashMap<>();
    /** Runtime pages registered by extension plugins; kept separate from disk pages. */
    private final Map<String, Page> virtualPages = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> virtualIrs = new ConcurrentHashMap<>();
    private final Map<String, String> virtualYaml = new ConcurrentHashMap<>();
    /** 实际生效的页面源文件（同 ID 的重复扩展名按扫描顺序覆盖）。 */
    private final Map<String, Path> sourceFiles = new ConcurrentHashMap<>();
    private final Map<String, Map<String, double[]>> worldPositions = new ConcurrentHashMap<>();
    private Path uiDir;

    /** 返回 load() 实际采用的源文件；不存在或未成功解析时返回 null。 */
    public Path sourceFile(String pageId) {
        return pageId == null ? null : sourceFiles.get(pageId);
    }

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
    /** Release extension-owned pages when the plugin is disabled. */
    public void clearVirtual() {
        synchronized (virtualLock) {
            virtualPages.clear();
            virtualIrs.clear();
            virtualYaml.clear();
        }
    }

    /** 扫描页面目录（不存在就创建，不报错）。 */
    public void load() {
        pages.clear();
        sourceFiles.clear();
        irst.clear();
        diskYaml.clear();
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
        Path root = uiDir.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(uiDir)) {
            plugin.getLogger().warning("UI 目录是符号链接，拒绝加载");
            return;
        }
        try (var stream = Files.walk(uiDir)) {
            files = stream.filter(p -> !Files.isSymbolicLink(p) && Files.isRegularFile(p,
                            java.nio.file.LinkOption.NOFOLLOW_LINKS))
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                        return name.endsWith(".yaml") || name.endsWith(".yml");
                    }).sorted().collect(java.util.stream.Collectors.toList());
        } catch (IOException | java.io.UncheckedIOException e) {
            plugin.getLogger().warning("UI 目录读取失败: " + e);
            return;
        }
        // 两阶段：先解析全部 IR（import 模板跨页面解析需要全量），再逐个展开构建
        Map<String, Map<String, Object>> parsed = new java.util.LinkedHashMap<>();
        for (Path file : files) {
            try {
                String id = root.relativize(file.toAbsolutePath().normalize()).toString()
                        .replace('\\', '/').replaceFirst("(?i)\\.(yaml|yml)$", "");
                Path safeFile = safePagePath(root, id, file.getFileName().toString()
                        .substring(file.getFileName().toString().lastIndexOf('.')));
                String yaml = new String(Files.readAllBytes(safeFile), StandardCharsets.UTF_8);
                com.opendreamcore.config.ConfigParser adapter = AdapterRegistry.detect(yaml);
                Map<String, Object> parsedIr = adapter == null ? new YamlParser().parse(yaml) : adapter.parse(yaml);
                parsed.put(id, parsedIr);
                diskYaml.put(id, adapter == null ? yaml : new org.yaml.snakeyaml.Yaml(dumpOptions()).dump(parsedIr));
                sourceFiles.put(id, safeFile);
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
                irst.put(entry.getKey(), expanded);
                pages.put(page.id() == null ? entry.getKey() : page.id(), page);
                plugin.getLogger().info("页面已加载: " + (page.id() == null ? entry.getKey() : page.id())
                        + " (" + page.elements().size() + " 元素"
                        + (com.opendreamcore.config.GuiCompiler.isFlat(entry.getValue()) ? "，扁平语法" : "") + ")");
            } catch (Exception e) {
                irst.remove(entry.getKey());
                diskYaml.remove(entry.getKey());
                sourceFiles.remove(entry.getKey());
                plugin.getLogger().warning("页面加载失败 " + entry.getKey() + ": " + e.getMessage());
            }
        }
        loadWorldPositions();
        applyWorldPositions();
    }

    /** Normalize an externally supplied page ID and reject paths escaping the UI root. */
    public static String normalizePageId(String pageId) {
        if (pageId == null || pageId.trim().isEmpty()) {
            throw new IllegalArgumentException("页面 id 不能为空");
        }
        String raw = pageId.trim().replace('\\', '/');
        Path parsed;
        try {
            parsed = java.nio.file.Paths.get(raw);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("页面 id 非法", e);
        }
        if (parsed.isAbsolute()) {
            throw new IllegalArgumentException("页面 id 不得为绝对路径");
        }
        Path normalized = parsed.normalize();
        if (normalized.toString().isEmpty() || ".".equals(normalized.toString())
                || normalized.startsWith("..")) {
            throw new IllegalArgumentException("页面 id 路径越界");
        }
        String normalizedId = normalized.toString().replace('\\', '/');
        if (!normalizedId.equals(raw)) {
            throw new IllegalArgumentException("页面 id 必须为规范化相对路径");
        }
        for (Path segment : normalized) {
            com.opendreamcore.protocol.message.EditorLease.requireSafePageId(segment.toString());
        }
        return normalizedId;
    }

    static Path safePagePath(Path root, String pageId, String suffix) {
        String normalizedId = normalizePageId(pageId);
        Path absoluteRoot = root.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(root) || Files.isSymbolicLink(absoluteRoot)) {
            throw new IllegalArgumentException("页面根目录不可为符号链接");
        }
        Path file = absoluteRoot.resolve(normalizedId + suffix).normalize();
        if (!file.startsWith(absoluteRoot) || Files.isSymbolicLink(file)) {
            throw new IllegalArgumentException("页面路径越界或为符号链接");
        }
        Path parent = file.getParent();
        while (parent != null && parent.startsWith(absoluteRoot) && !parent.equals(absoluteRoot)) {
            if (Files.isSymbolicLink(parent)) {
                throw new IllegalArgumentException("页面路径包含符号链接");
            }
            parent = parent.getParent();
        }
        return file;
    }

    private static Map<String, Object> parseAuto(String yaml) {
        com.opendreamcore.config.ConfigParser parser = AdapterRegistry.detect(yaml);
        return parser == null ? new YamlParser().parse(yaml) : parser.parse(yaml);
    }

    // 世界面板位置持久化（拖拽落点 overlay）

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
            String json = new String(java.nio.file.Files.readAllBytes(file), java.nio.charset.StandardCharsets.UTF_8);
            com.google.gson.JsonObject root = new com.google.gson.JsonParser().parse(json).getAsJsonObject();
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
                        raw instanceof java.util.Map<?, ?> m ? (java.util.Map<?, ?>) m : java.util.Collections.emptyMap());
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
            Path target = worldPositionsFile();
            Path temp = target.resolveSibling(target.getFileName() + ".tmp");
            java.nio.file.Files.write(temp, new com.google.gson.GsonBuilder()
                    .setPrettyPrinting().create().toJson(out).getBytes(StandardCharsets.UTF_8));
            java.nio.file.Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
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
        if (pageId == null) return null;
        final String id;
        try {
            id = normalizePageId(pageId);
        } catch (IllegalArgumentException e) {
            return null;
        }
        Map<String, Object> ir;
        synchronized (virtualLock) {
            ir = virtualIrs.get(id);
        }
        if (ir == null) ir = irst.get(id);
        if (ir == null) return null;
        if (!com.opendreamcore.config.GuiCompiler.isFlat(ir)) return yamlOf(id);
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
                com.opendreamcore.script.engine.Scope scope = new com.opendreamcore.script.engine.Scope();
                if (player != null) {
                    scope.assignPlayer("name", player.getName());
                    scope.assignPlayer("uuid", player.getUniqueId().toString());
                    scope.assignPlayer("health", (double) player.getHealth());
                    scope.assignPlayer("level", (double) player.getLevel());
                }
                Object result = com.opendreamcore.script.engine.DreamLang.evaluate(expr, scope);
                return result instanceof Boolean b ? b : result != null;
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        public String resolve(String text) {
            // 页面占位符"本地优先"：客户端能自己解析的（player.* / query.* / system.* / color.* /
            // entity.* / item.* / vars.* / global.* / lang.*）一律留原文下发，客户端每帧本地解析——
            // 血量/等级/坐标/TPS 才是"活"的，服务端编译一次只会冻结成死值。
            // 服务端只负责客户端拿不到的 PAPI（%..% ）。
            return papi(player, text);
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

    /** Register a runtime YAML page without writing to the UI directory. */
    public boolean registerVirtual(String pageId, String yaml) {
        if (yaml == null) return false;
        final String id;
        try {
            id = normalizePageId(pageId);
        } catch (IllegalArgumentException e) {
            return false;
        }
        try {
            Map<String, Object> parsed = parseAuto(yaml);
            Map<String, Object> expanded = com.opendreamcore.page.PageImporter.expand(parsed, this::lookupIr);
            Map<String, Object> ir = com.opendreamcore.config.GuiCompiler.isFlat(expanded)
                    ? com.opendreamcore.config.GuiCompiler.compile(expanded, LOAD_ALL_KEEP) : expanded;
            Page page = PageSchema.build(id, ir);
            synchronized (virtualLock) {
                virtualIrs.put(id, expanded);
                virtualYaml.put(id, yaml);
                virtualPages.put(id, page);
            }
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("虚拟页面注册失败 " + id + ": " + e.getMessage());
            return false;
        }
    }

    /** Register a runtime page from an input stream; the stream is consumed and closed. */
    public boolean registerVirtual(String pageId, java.io.InputStream input) {
        if (input == null) return false;
        try {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
            return registerVirtual(pageId, new String(bytes.toByteArray(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            return false;
        } finally {
            try { input.close(); } catch (IOException ignored) { }
        }
    }

    private Map<String, Object> lookupIr(String id) {
        synchronized (virtualLock) {
            Map<String, Object> value = virtualIrs.get(id);
            return value != null ? value : irst.get(id);
        }
    }

    public boolean unregisterVirtual(String pageId) {
        if (pageId == null) return false;
        synchronized (virtualLock) {
            boolean removed = virtualPages.remove(pageId) != null;
            removed |= virtualIrs.remove(pageId) != null;
            removed |= virtualYaml.remove(pageId) != null;
            return removed;
        }
    }

    public boolean hasVirtual(String pageId) {
        if (pageId == null) return false;
        synchronized (virtualLock) {
            return virtualPages.containsKey(pageId);
        }
    }

    public Page get(String pageId) {
        synchronized (virtualLock) {
            Page virtual = virtualPages.get(pageId);
            if (virtual != null) return virtual;
        }
        return pages.get(pageId);
    }

    /** 所有已加载页面（磁盘与扩展插件虚拟页面，虚拟页覆盖同 ID 磁盘页）。 */
    public java.util.Collection<Page> allPages() {
        java.util.Map<String, Page> result = new java.util.LinkedHashMap<>(pages);
        synchronized (virtualLock) {
            result.putAll(virtualPages);
        }
        return result.values();
    }

    public String yamlOf(String pageId) {
        if (pageId == null) return null;
        // IDs are registry keys, never untrusted filesystem paths (including virtual IDs).
        synchronized (virtualLock) {
            String runtime = virtualYaml.get(pageId);
            if (runtime != null) return runtime;
        }
        return diskYaml.get(pageId);
    }

    /** 按 match 找第一个命中的页面（进服/开容器时调用）。 */
    public Page match(String target, String title) {
        return match(target, title, null);
    }

    /** 按 match 找第一个命中的页面；player 非空时 when 表达式可用 player.xxx。 */
    public Page match(String target, String title, org.bukkit.entity.Player player) {
        return allPages().stream()
                .filter(p -> p.match() != null && matches(p.match(), target, title, player))
                .sorted((a, b) -> Integer.compare(b.match().priority(), a.match().priority()))
                .findFirst()
                .orElse(null);
    }

    private static boolean matches(com.opendreamcore.page.Match match, String target, String title,
                                   org.bukkit.entity.Player player) {
        String m = match.target();
        if (m == null) {
            return false;
        }
        // 特殊 match 目标：按 display 模式匹配（与客户端 LocalPageManager 一致）
        boolean targetMatches;
        if (m.equalsIgnoreCase("hud")) {
            targetMatches = true; // target/title 由调用方指定为 "hud"
        } else if (m.equalsIgnoreCase("world")) {
            targetMatches = true;
        } else if (m.equalsIgnoreCase("screen")) {
            targetMatches = true;
        } else if (m.equalsIgnoreCase("inventory") || m.equalsIgnoreCase("player")) {
            targetMatches = target != null && (target.equalsIgnoreCase("inventory")
                    || target.equalsIgnoreCase("player")
                    || target.equalsIgnoreCase("minecraft:chest")
                    || target.equalsIgnoreCase("minecraft:crafting_table"));
        } else if (m.contains(":")) {
            targetMatches = target != null && m.equalsIgnoreCase(target);
        } else {
            targetMatches = title != null && m.equalsIgnoreCase(title);
        }
        if (!targetMatches) {
            return false;
        }
        if (match.when() != null && !match.when().trim().isEmpty()) {
            // 表达式条件：DreamLang 求值（player.xxx + 页面变量）
            try {
                com.opendreamcore.script.engine.Scope scope = new com.opendreamcore.script.engine.Scope();
                if (player != null) {
                    scope.assignPlayer("name", player.getName());
                    scope.assignPlayer("uuid", player.getUniqueId().toString());
                    scope.assignPlayer("health", (double) player.getHealth());
                    scope.assignPlayer("level", (double) player.getLevel());
                }
                Object result = com.opendreamcore.script.engine.DreamLang.evaluate(match.when(), scope);
                if (!(result instanceof Boolean b) || !b) {
                    return false;
                }
            } catch (Exception e) {
                return false; // 表达式出错不匹配
            }
        }
        return true;
    }

    public List<String> ids() {
        List<String> ids = new ArrayList<>();
        for (Page page : allPages()) ids.add(page.id());
        ids.sort(String::compareTo);
        return ids;
    }
}
