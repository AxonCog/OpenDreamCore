package com.opendreamcore.config;

import com.opendreamcore.util.J8;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.Mark;
import org.yaml.snakeyaml.error.MarkedYAMLException;

import java.util.Map;

/**
 * 默认 YAML 解析器。SafeConstructor 防反序列化攻击；
 * 解析错误带行列号。
 */
public final class YamlParser implements ConfigParser {

    private final Yaml yaml;

    public YamlParser() {
        this(false);
    }

    /** 旧格式兼容模式：龙核配置里有重复键（Bukkit 语义后者覆盖），严格模式直接拒收。 */
    public static YamlParser lenient() {
        return new YamlParser(true);
    }

    private YamlParser(boolean allowDuplicateKeys) {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(allowDuplicateKeys);
        this.yaml = new Yaml(new SafeConstructor(options));
    }

    @Override
    public String format() {
        return "yaml";
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> parse(String text) throws ConfigParseException {
        if (text != null && !text.isEmpty() && text.charAt(0) == '\uFEFF') {
            // Windows 记事本写的配置常带 BOM，不刹掉首个键名就多个隐形前缀
            text = text.substring(1);
        }
        try {
            Object root = yaml.load(text);
            if (root == null) {
                return J8.map();
            }
            if (!(root instanceof Map)) {
                throw new ConfigParseException("配置根必须是键值表", 1, 1);
            }
            return (Map<String, Object>) root;
        } catch (MarkedYAMLException e) {
            Mark mark = e.getProblemMark();
            int line = mark == null ? -1 : mark.getLine() + 1;
            int column = mark == null ? -1 : mark.getColumn() + 1;
            throw new ConfigParseException("YAML 解析失败: " + e.getProblem(), line, column);
        } catch (RuntimeException e) {
            throw new ConfigParseException("YAML 解析失败: " + e.getMessage(), e);
        }
    }
}
