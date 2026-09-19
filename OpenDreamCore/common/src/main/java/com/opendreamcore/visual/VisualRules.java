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
            out.putAll(parseRoot(rootFile, system));
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
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        try {
            String text = J8.readString(file, StandardCharsets.UTF_8);
            Map<String, Object> raw = new YamlParser().parse(text);
            if (raw == null) {
                return out;
            }
            for (Map.Entry<String, Object> e : raw.entrySet()) {
                if (e.getValue() instanceof Map<?, ?> m && !m.isEmpty()) {
                    Map<String, Object> copy = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> de : m.entrySet()) {
                        copy.put(String.valueOf(de.getKey()), de.getValue());
                    }
                    out.put(e.getKey(), copy);
                }
            }
        } catch (Exception e) {
            LOGGER.warning(() -> "[OpenDreamCore][visual] " + system + " 根文件解析失败: " + e);
        }
        return out;
    }

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
