package com.opendreamcore.page;

import com.opendreamcore.config.ConfigParseException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * import 模板复用：把另一个页面的元素/变量内联进当前页面（配置加载期展开）。
 * 纯 ConfigIR 层操作，客户端与服务端页面加载共用。
 *
 * 用法一（元素级，可出现在顶层或任意 children 中）：
 *   my_card:
 *     type: import
 *     page: card_tpl        # 目标页面 id（文件名）
 *     prefix: tpl_          # 元素 id 前缀（默认 目标页id_）
 *     x: 10                 # 数字偏移（可选，仅数字生效）
 *     y: 20
 *     vars: {name: 张三}     # 覆盖目标页变量（可选）
 *
 * 用法二（页面级，顶层 imports 列表）：
 *   imports:
 *     - page: common_header
 *       prefix: hdr_
 *
 * 规则：
 * - 目标页元素全部内联（id 加前缀避免冲突），变量并入（本页已有键优先）
 * - 嵌套 import 递归展开；循环引用抛 ConfigParseException
 */
public final class PageImporter {

    /** 页面源：按页面 id 取 ConfigIR（Map 树）；未找到返回 null。 */
    public interface PageSource {
        Map<String, Object> load(String pageId);
    }

    public static final String TYPE_IMPORT = "import";

    private PageImporter() {
    }

    /** 展开页面里所有 import 引用（顶层 imports 列表 + 任意位置的 import 元素）。 */
    public static Map<String, Object> expand(Map<String, Object> ir, PageSource source) {
        return expand(ir, source, new HashSet<>());
    }

    private static Map<String, Object> expand(Map<String, Object> ir, PageSource source, Set<String> stack) {
        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> pageImports = new ArrayList<>();
        for (Map.Entry<String, Object> entry : ir.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if ("imports".equals(key)) {
                // 页面级导入列表：{page, prefix, vars} 或纯字符串页面 id
                if (value instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> m) {
                            pageImports.add(asMap(m));
                        } else if (item != null) {
                            Map<String, Object> single = new LinkedHashMap<>();
                            single.put("page", String.valueOf(item));
                            pageImports.add(single);
                        }
                    }
                }
                continue;
            }
            if (isImportElement(value)) {
                applyPageImport(out, asMap(value), source, stack);
            } else {
                out.put(key, expandValue(value, source, stack));
            }
        }
        for (Map<String, Object> imp : pageImports) {
            applyPageImport(out, imp, source, stack);
        }
        return out;
    }

    /** 递归展开 Map/List/标量里的 import 元素（children 里也支持）。 */
    private static Object expandValue(Object value, PageSource source, Set<String> stack) {
        if (value instanceof Map<?, ?> m) {
            Map<String, Object> map = asMap(m);
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : map.entrySet()) {
                if ("children".equals(e.getKey()) && e.getValue() instanceof Map<?, ?> children) {
                    // children 容器：import 元素就地展开为多个兄弟元素
                    out.put("children", expandChildren(asMap(children), source, stack));
                } else if (isImportElement(e.getValue())) {
                    applyPageImport(out, asMap(e.getValue()), source, stack);
                } else {
                    out.put(e.getKey(), expandValue(e.getValue(), source, stack));
                }
            }
            return out;
        }
        if (value instanceof List<?> list) {
            List<Object> out = new ArrayList<>();
            for (Object item : list) {
                out.add(expandValue(item, source, stack));
            }
            return out;
        }
        return value;
    }

    private static Map<String, Object> expandChildren(Map<String, Object> children,
                                                      PageSource source, Set<String> stack) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : children.entrySet()) {
            Object v = e.getValue();
            if (isImportElement(v)) {
                Map<String, Object> container = new LinkedHashMap<>();
                applyPageImport(container, asMap(v), source, stack);
                // 内联元素并入 children（保留前缀后的 id）
                for (Map.Entry<String, Object> c : container.entrySet()) {
                    if (isElement(c.getValue())) {
                        out.put(c.getKey(), c.getValue());
                    }
                }
                continue;
            }
            out.put(e.getKey(), expandValue(v, source, stack));
        }
        return out;
    }

    /** 把一个 import 定义（元素级或 imports 列表项）展开进容器。 */
    private static void applyPageImport(Map<String, Object> out, Map<String, Object> imp,
                                        PageSource source, Set<String> stack) {
        String pageId = str(imp.get("page"));
        if (pageId == null || pageId.isBlank()) {
            throw new ConfigParseException("import 缺少 page（目标页面 id）", 0, 0);
        }
        if (stack.contains(pageId)) {
            throw new ConfigParseException("import 循环引用: " + pageId, 0, 0);
        }
        Map<String, Object> targetIr = source.load(pageId);
        if (targetIr == null) {
            throw new ConfigParseException("import 目标页面不存在: " + pageId, 0, 0);
        }
        stack.add(pageId);
        try {
            Map<String, Object> expanded = expand(targetIr, source, stack);
            String prefix = str(imp.get("prefix"));
            if (prefix == null || prefix.isBlank()) {
                prefix = pageId + "_";
            }
            double offsetX = num(imp.get("x"), 0);
            double offsetY = num(imp.get("y"), 0);
            // 变量：目标页缺省变量并入（本页已有键优先），import 上的 vars 覆盖一切
            for (Map.Entry<String, Object> e : expanded.entrySet()) {
                if (!isElement(e.getValue()) && !out.containsKey(e.getKey())) {
                    out.put(e.getKey(), e.getValue());
                }
            }
            Object varsRaw = imp.get("vars");
            if (varsRaw instanceof Map<?, ?> vars) {
                for (Map.Entry<?, ?> e : vars.entrySet()) {
                    out.put(String.valueOf(e.getKey()), e.getValue());
                }
            }
            // 元素：id 加前缀 + 数字 x/y 偏移
            for (Map.Entry<String, Object> e : expanded.entrySet()) {
                if (isElement(e.getValue())) {
                    out.put(prefix + e.getKey(), offsetElement(asMap(e.getValue()), offsetX, offsetY, prefix));
                }
            }
        } finally {
            stack.remove(pageId);
        }
    }

    /** 递归给导入元素加 id 前缀 + 数字 x/y 偏移（children 键名同样加前缀）。 */
    private static Map<String, Object> offsetElement(Map<String, Object> element, double dx, double dy, String prefix) {
        Map<String, Object> out = new LinkedHashMap<>(element);
        if (dx != 0 && element.get("x") instanceof Number n) {
            out.put("x", n.doubleValue() + dx);
        }
        if (dy != 0 && element.get("y") instanceof Number n) {
            out.put("y", n.doubleValue() + dy);
        }
        Object childrenRaw = element.get("children");
        if (childrenRaw instanceof Map<?, ?> children) {
            Map<String, Object> newChildren = new LinkedHashMap<>();
            for (Map.Entry<?, ?> c : children.entrySet()) {
                Object cv = c.getValue();
                if (cv instanceof Map<?, ?> cm) {
                    newChildren.put(prefix + c.getKey(), offsetElement(asMap(cm), dx, dy, prefix));
                } else {
                    newChildren.put(String.valueOf(c.getKey()), cv);
                }
            }
            out.put("children", newChildren);
        }
        return out;
    }

    private static boolean isImportElement(Object value) {
        return value instanceof Map<?, ?> map && TYPE_IMPORT.equals(str(map.get("type")));
    }

    /** 顶层/children 里"元素"判定（与 PageSchema 一致：map 且含 type）。 */
    private static boolean isElement(Object value) {
        return value instanceof Map<?, ?> map && map.containsKey("type");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object v) {
        return (Map<String, Object>) v;
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static double num(Object v, double fallback) {
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        return fallback;
    }
}
