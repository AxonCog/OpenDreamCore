package com.opendreamcore.ui.theme;

import com.opendreamcore.util.J8;

import com.opendreamcore.config.JsonParser;
import com.opendreamcore.config.YamlParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * 主题库。整套主题体系源自梦想引擎的设计启发，这里是它的进程级单例：主题文件解析成 Theme 后登记于此，页面编译（PageSchema.build）
 * 按页面声明的主题名取用。服务端与客户端单机两条加载路径共用本类，
 * 平台版本层零感知——这就是"common 改一处、全版本受益"的落点。
 *
 * 约定目录：OpenDreamCore/themes/*.yml|*.yaml|*.json，文件名即主题名；
 * default 主题缺省生效，页面可用 theme: <名字> 显式选择其它主题。
 */
public final class ThemeLibrary {

    public static final String DEFAULT_THEME = "default";

    private static final ThemeLibrary INSTANCE = new ThemeLibrary();

    /** 获取进程级主题库实例。 */
    public static ThemeLibrary get() {
        return INSTANCE;
    }

    private final Map<String, Theme> themes = new ConcurrentHashMap<>();

    private static final Logger LOGGER = Logger.getLogger(ThemeLibrary.class.getName());

    private ThemeLibrary() {
    }

    /** 登记或替换同名主题。 */
    public void register(Theme theme) {
        if (theme != null && !Theme.EMPTY.equals(theme)) {
            themes.put(theme.name(), theme);
        }
    }

    /**
     * 直接从 YAML 文本登记主题——附属插件最顺手的入口。
     * 插件资源里带一份 theme.yml，启动时一行代码就能给玩家提供新皮肤：
     *
     * <pre>{@code
     * String yaml = J8.readString(dataFolder.resolve("themes/gold.yml"));
     * ThemeLibrary.get().registerYaml("gold", yaml);
     * }</pre>
     */
    public boolean registerYaml(String name, String yamlText) {
        try {
            register(ThemeParser.parse(name, new YamlParser().parse(yamlText)));
            return true;
        } catch (Exception e) {
            // 附属提供的坏主题不该炸了主流程，记一笔就过
            LOGGER.warning(() -> "[OpenDreamCore][theme] 附属注册主题失败 " + name + ": " + e);
            return false;
        }
    }

    /** 已登记的主题名清单（无序）。 */
    public java.util.Set<String> names() {
        return java.util.Collections.unmodifiableSet(themes.keySet());
    }

    /** 摘掉一个主题（附属注销用）；不存在时静默。 */
    public void remove(String name) {
        themes.remove(name);
    }

    /** 按名取主题；不存在返回 null。 */
    public Theme named(String name) {
        if (name == null || J8.isBlank(name)) {
            return themes.get(DEFAULT_THEME);
        }
        return themes.get(name.trim());
    }

    /** 页面实际生效的主题链：支持逗号分隔多主题（如 "default,server_patch"），
     *  按声明序依次应用，后者只填前者没填到的属性；
     *  任一名字缺失则静默跳过（不炸页面），全部缺失回退 default。 */
    public List<Theme> resolveFor(String pageThemeName) {
        List<Theme> out = new ArrayList<>(2);
        if (pageThemeName != null && !J8.isBlank(pageThemeName)) {
            for (String name : pageThemeName.split(",")) {
                Theme t = themes.get(name.trim());
                if (t != null && !out.contains(t)) {
                    out.add(t);
                }
            }
            if (!out.isEmpty()) {
                return out;
            }
        }
        // 页面没声明或声明落空：回退 default
        Theme fallback = themes.get(DEFAULT_THEME);
        if (fallback != null) {
            out.add(fallback);
        }
        return out;
    }

    /** 清空重载前调用。 */
    public void clear() {
        themes.clear();
    }

    public int size() {
        return themes.size();
    }

    /**
     * 从目录加载全部主题文件（递归），返回成功数量。
     * 单文件失败仅告警跳过；热重载方在 UI 目录监听之外另行挂接 themes 目录即可。
     */
    public int loadFromDir(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) {
            return 0;
        }
        int count = 0;
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path file : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
                String name = file.getFileName().toString().toLowerCase();
                if (!(name.endsWith(".yml") || name.endsWith(".yaml") || name.endsWith(".json"))) {
                    continue;
                }
                try {
                    String text = J8.readString(file, StandardCharsets.UTF_8);
                    Map<String, Object> ir = name.endsWith(".json")
                            ? new JsonParser().parse(text)
                            : new YamlParser().parse(text);
                    String themeName = baseName(file.getFileName().toString());
                    register(ThemeParser.parse(themeName, ir));
                    count++;
                } catch (Exception e) {
                    LOGGER.warning(() -> "[OpenDreamCore][theme] 主题文件加载失败 "
                            + file + ": " + e);
                }
            }
        } catch (IOException e) {
            LOGGER.warning(() -> "[OpenDreamCore][theme] 主题目录不可读 " + dir + ": " + e);
        }
        return count;
    }

    /** 文件名去扩展名（主题名）。 */
    static String baseName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? fileName : fileName.substring(0, dot);
    }
}
