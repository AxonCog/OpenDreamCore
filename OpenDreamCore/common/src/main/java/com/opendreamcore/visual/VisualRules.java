package com.opendreamcore.visual;

import com.opendreamcore.util.J8;

import com.opendreamcore.config.YamlParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * 视觉规则双形态加载器。
 *
 * 每个视觉系统一个名字（ItemIcon/HeadTag/FontConfig…），同时支持两种形态：
 *   根文件   ItemIcon.yml   —— 一个文件多条规则，map 键 = 规则 id（PC 用法）
 *   同名目录 ItemIcon/      —— 一文件一规则，文件名 = 规则 id（规模化管理）
 * 两者可共存：先载根文件，再扫同名文件夹，同 id 时文件夹覆盖根文件。
 *
 * 两形态都缺失时，自动从 {@link VisualTemplates} 生成注释完整的默认示例——
 * 首次体验零门槛，用户照着改就能跑。
 *
 * 全部产出统一的"规则 IR"：Map<id, Map<String,Object>>，
 * 各系统的解释器再从 IR 读自己的键——加载与解释解耦，换格式不换逻辑。
 */
public final class VisualRules {

    private static final Logger LOGGER = Logger.getLogger(VisualRules.class.getName());

    private VisualRules() {
    }

    /**
     * 双形态装载一个系统。
     *
     * dataRoot：插件数据根（plugins/OpenDreamCore）
     * system：系统名（PascalCase，如 ItemIcon；决定根文件与目录名）
     * 返回：规则表：id → 规则 IR；解析失败的单文件 warn 跳过
     */
    public static Map<String, Map<String, Object>> load(Path dataRoot, String system) {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        Path rootFile = dataRoot.resolve(system + ".yml");
        Path dir = dataRoot.resolve(system);

        // 根文件先行
        if (Files.isRegularFile(rootFile)) {
            Map<String, Object> raw = readRoot(rootFile, system);
            if (raw != null) {
                if (WHOLE_FILE_SYSTEMS.contains(system) && looksLikeWholeFilePage(raw)) {
                    // 整份 HeadTag.yml = 一个头顶页。误当「每键一规则」时 entity/Functions/血条底
                    // 会各变成一条都布局不出元素的规则，而 Mixin 已经交出原版名牌 → 名牌全没。
                    out.put(system, raw);
                } else {
                    out.putAll(parseRoot(raw, system));
                }
            }
        }
        // 同名文件夹后扫，同 id 覆盖根文件的条目
        if (Files.isDirectory(dir)) {
            try (Stream<Path> files = Files.walk(dir)) {
                for (Path file : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
                    String name = file.getFileName().toString().toLowerCase();
                    if (!(name.endsWith(".yml") || name.endsWith(".yaml"))) {
                        continue;
                    }
                    String relId = dir.relativize(file).toString().replace('\\', '/');
                    relId = relId.replaceAll("\\.(ya?ml)$", "");
                    try {
                        String text = J8.readString(file, StandardCharsets.UTF_8);
                        Map<String, Object> ir = new YamlParser().parse(text);
                        if (ir != null && !ir.isEmpty()) {
                            out.put(relId, ir);
                        }
                    } catch (Exception e) {
                        LOGGER.warning(() -> "[OpenDreamCore][visual] " + system + " 规则文件解析失败 "
                                + file.getFileName() + ": " + e);
                    }
                }
            } catch (IOException e) {
                LOGGER.warning(() -> "[OpenDreamCore][visual] " + system + " 目录不可读: " + e);
            }
        }
        return out;
    }

    /** 两形态都不存在时生成默认示例；返回是否实际写出。 */
    public static boolean ensureDefault(Path dataRoot, String system, String templateYaml) {
        Path rootFile = dataRoot.resolve(system + ".yml");
        Path dir = dataRoot.resolve(system);
        if (Files.exists(rootFile) || Files.exists(dir)) {
            return false;
        }
        try {
            Files.createDirectories(dataRoot);
            J8.writeString(rootFile, templateYaml, StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            LOGGER.warning(() -> "[OpenDreamCore][visual] 默认示例写出失败 " + system + ": " + e);
            return false;
        }
    }

    /** 根文件解析：map 键 = 规则 id。 */
    private static Map<String, Map<String, Object>> parseRoot(Path file, String system) {
        Map<String, Object> raw = readRoot(file, system);
        return raw == null ? new LinkedHashMap<String, Map<String, Object>>() : parseRoot(raw, system);
    }

    /** 读根文件并解析成 map（失败返回 null，记日志）。 */
    private static Map<String, Object> readRoot(Path file, String system) {
        try {
            String text = J8.readString(file, StandardCharsets.UTF_8);
            Map<String, Object> raw = new YamlParser().parse(text);
            return raw == null ? new LinkedHashMap<String, Object>() : raw;
        } catch (Exception e) {
            LOGGER.warning(() -> "[OpenDreamCore][visual] " + system + " 根文件解析失败: " + e);
            return null;
        }
    }

    private static Map<String, Map<String, Object>> parseRoot(Map<String, Object> raw, String system) {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : raw.entrySet()) {
            if (e.getValue() instanceof Map<?, ?> m && !m.isEmpty()) {
                Map<String, Object> copy = new LinkedHashMap<>();
                for (Map.Entry<?, ?> de : m.entrySet()) {
                    copy.put(String.valueOf(de.getKey()), de.getValue());
                }
                out.put(e.getKey(), copy);
            }
        }
        return out;
    }

    /**
     * 根文件是否就是「一页」（而非「多规则集合」）。
     *
     * <p>三个信号任一命中即是：
     * ① 顶层出现非 map 值（entity: zombie / distance: 64 这类页面级标量）；
     * ② 顶层出现页面级保留键（Functions/match/display/world/…）；
     * ③ 顶层某个 map 元素自身带 type（说明它就是页面元素，不是规则容器）。
     * 全不命中 → 按旧语义「每个顶层键一条规则」（多规则单文件写法照旧可用）。
     */
    static boolean looksLikeWholeFilePage(Map<String, Object> raw) {
        for (Map.Entry<String, Object> e : raw.entrySet()) {
            Object v = e.getValue();
            if (!(v instanceof Map<?, ?> m)) {
                return true;
            }
            if (PAGE_LEVEL_KEYS.contains(e.getKey())) {
                return true;
            }
            if (m.containsKey("type") || m.containsKey("hologram")) {
                return true;
            }
        }
        return false;
    }

    /** 「整文件即一条规则」的系统（龙核/老壳的 HeadTag/Blood 语义）。 */
    private static final java.util.Set<String> WHOLE_FILE_SYSTEMS = J8.set("HeadTag", "Blood");

    /** 页面级保留键（出现在顶层即说明这文件本身就是一个页面）。 */
    private static final java.util.Set<String> PAGE_LEVEL_KEYS = J8.set(
            "Functions", "match", "title", "display", "world", "through", "background",
            "hideVanilla", "hideVanillaList", "animations", "draggable", "allowEscClose",
            "theme", "design", "class", "style",
            "entity", "实体", "contains", "包含", "distance", "offsetX", "offsetY", "y");

    /** 列出某系统目录下全部资产相对路径（调试/清单用）。 */
    public static List<String> listAssets(Path dir) {
        List<String> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return out;
        }
        try (Stream<Path> stream = Files.walk(dir)) {
            stream.filter(Files::isRegularFile).forEach(p ->
                    out.add(dir.relativize(p).toString().replace('\\', '/')));
        } catch (IOException ignored) {
        }
        return out;
    }
}
