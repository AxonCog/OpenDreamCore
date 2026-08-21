package com.opendreamcore.config;

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
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        this.yaml = new Yaml(new SafeConstructor(options));
    }

    @Override
    public String format() {
        return "yaml";
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> parse(String text) throws ConfigParseException {
        try {
            Object root = yaml.load(text);
            if (root == null) {
                return Map.of();
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
