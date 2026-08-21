package com.opendreamcore.page;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 页面导出（编辑器"存 YAML"）：Page 模型 → ConfigIR → YAML 文本。
 * 与 PageSchema 逆过程：变量平铺、元素嵌套、Functions/选项保留。
 * 序列化选项全引号标量，避免 "true"/数字被重解析成布尔/数字。
 */
public final class PageExporter {

    private PageExporter() {
    }

    /** Page → ConfigIR（Map 树）。 */
    public static Map<String, Object> toIr(Page page) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (page.match() != null && page.match().target() != null) {
            out.put("match", page.match().target());
        }
        if (page.title() != null && !page.title().isBlank()) {
            out.put("title", page.title());
        }
        if (page.displayMode() != null) {
            out.put("display", page.displayMode().id());
        }
        page.variables().forEach(out::put);
        for (Element element : page.elements()) {
            out.put(element.id(), elementToMap(element));
        }
        if (page.functions() != null && !page.functions().isEmpty()) {
            out.put("Functions", new LinkedHashMap<>(page.functions()));
        }
        if (page.options() != null) {
            for (Map.Entry<String, Object> entry : page.options().entrySet()) {
                if (!out.containsKey(entry.getKey())) {
                    out.put(entry.getKey(), entry.getValue());
                }
            }
        }
        return out;
    }

    /** Page → YAML 文本（全引号标量）。 */
    public static String toYaml(Page page) {
        return new Yaml(dumpOptions()).dump(toIr(page));
    }

    private static Map<String, Object> elementToMap(Element element) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("type", element.type());
        Layout layout = element.layout();
        if (layout != null) {
            if (layout.x() != null) {
                out.put("x", layout.x());
            }
            if (layout.y() != null) {
                out.put("y", layout.y());
            }
            if (layout.width() != null) {
                out.put("width", layout.width());
            }
            if (layout.height() != null) {
                out.put("height", layout.height());
            }
        }
        if (element.visibleWhen() != null) {
            out.put("visibleWhen", element.visibleWhen());
        }
        if (element.enabledWhen() != null) {
            out.put("enabledWhen", element.enabledWhen());
        }
        // 组件属性展开到元素级（与 PageSchema 解析一致）
        element.props().forEach(out::put);
        if (!element.children().isEmpty()) {
            Map<String, Object> children = new LinkedHashMap<>();
            for (Element child : element.children()) {
                children.put(child.id(), elementToMap(child));
            }
            out.put("children", children);
        }
        if (!element.actions().isEmpty()) {
            out.put("actions", new LinkedHashMap<>(element.actions()));
        }
        return out;
    }

    private static DumperOptions dumpOptions() {
        DumperOptions options = new DumperOptions();
        options.setDefaultScalarStyle(DumperOptions.ScalarStyle.DOUBLE_QUOTED);
        options.setIndent(2);
        return options;
    }
}
