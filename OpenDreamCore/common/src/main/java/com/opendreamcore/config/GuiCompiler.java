package com.opendreamcore.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 扁平语法 → 标准嵌套 IR 编译器。
 * 语法迁移自23年梦想核心与26年梦想核心正式版。
 * 作者：梦幻 QQ:2496599413
 *
 * 扁平语法：
 *   title/display/match 等顶层键照常
 *   variables → 页面变量（平铺）
 *   functions → Functions 生命周期
 *   options → 页面选项（平铺到顶层）
 *   elements → 元素列表（id 省略自动 el_1/el_2...）
 *   lines → 纵向自动排布（y 自动叠加）
 *
 * condition 编译期条件剔除，resolve 做占位符替换（actions 除外）。
 * type 为空时从 id 后缀推断（TypeInferrer）。
 */
public final class GuiCompiler {

    public static final String KEY_ELEMENTS = "elements";
    public static final String KEY_LINES = "lines";
    public static final String KEY_VARIABLES = "variables";
    public static final String KEY_FUNCTIONS = "functions";
    public static final String KEY_OPTIONS = "options";
    public static final String KEY_CONDITION = "condition";
    public static final String KEY_ID = "id";
    public static final String KEY_ACTIONS = "actions";
    public static final String KEY_CHILDREN = "children";

    /** 编译上下文 */
    public interface Context {
    /** 条件表达式求值，false 剔除元素 */
        boolean condition(String expr);

    /** 编译期字符串替换（占位符/PAPI），原样返回也行 */
        String resolve(String text);
    }

    private GuiCompiler() {
    }

    /** 是扁平语法（有 elements 或 lines） */
    public static boolean isFlat(Map<String, Object> ir) {
        return ir != null && (ir.containsKey(KEY_ELEMENTS) || ir.containsKey(KEY_LINES));
    }

    /** 编译扁平 IR → 标准嵌套 IR */
    public static Map<String, Object> compile(Map<String, Object> ir, Context ctx) {
        if (ir == null) {
            throw new ConfigParseException("页面配置为空", 1, 1);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : ir.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (KEY_ELEMENTS.equals(key) || KEY_LINES.equals(key) || "lineSpacing".equals(key)) {
                continue;
            }
            if (KEY_VARIABLES.equals(key)) {
                if (value instanceof Map<?, ?> vars) {
                    for (Map.Entry<?, ?> v : vars.entrySet()) {
                        out.put(String.valueOf(v.getKey()), v.getValue());
                    }
                }
                continue;
            }
            if (KEY_FUNCTIONS.equals(key)) {
                out.put("Functions", value);
                continue;
            }
            if (KEY_OPTIONS.equals(key)) {
                if (value instanceof Map<?, ?> opts) {
                    for (Map.Entry<?, ?> o : opts.entrySet()) {
                        out.put(String.valueOf(o.getKey()), o.getValue());
                    }
                }
                continue;
            }
            out.put(key, value);
        }
        // 元素：elements 原样编译；lines 纵向自动排布
        Object elementsRaw = ir.get(KEY_ELEMENTS);
        if (elementsRaw instanceof List<?> elements) {
            int[] counter = {1};
            for (Object raw : elements) {
                if (!(raw instanceof Map<?, ?> m)) {
                    continue;
                }
                Map<String, Object> el = asMap(m);
                if (shouldDrop(el, ctx)) {
                    continue;
                }
                out.put(pickId(el, counter), compileElement(el, ctx));
            }
        }
        Object linesRaw = ir.get(KEY_LINES);
        if (linesRaw instanceof List<?> lines) {
            int[] counter = {1};
            double spacing = num(ir.get("lineSpacing"), 4);
            double cursor = 0;
            for (Object raw : lines) {
                if (!(raw instanceof Map<?, ?> m)) {
                    continue;
                }
                Map<String, Object> el = new LinkedHashMap<>(asMap(m));
                if (shouldDrop(el, ctx)) {
                    continue;
                }
                double h = num(el.get("height"), 20);
                el.put("y", cursor);
                el.putIfAbsent("x", 0);
                el.putIfAbsent("width", "window.width");
                out.put(pickId(el, counter), compileElement(el, ctx));
                cursor += h + spacing;
            }
        }
        return out;
    }

    /** 元素编译：condition/id 剔除，children 递归，字符串属性过 resolve（actions 除外）
     *  type 为空时从 id 后缀推断 */
    private static Map<String, Object> compileElement(Map<String, Object> el, Context ctx) {
        Map<String, Object> out = new LinkedHashMap<>();
        // type 为空时从 id 推断；有值时走别名映射
        Object typeVal = el.get("type");
        if (typeVal == null || String.valueOf(typeVal).isBlank()) {
            String inferred = TypeInferrer.infer(String.valueOf(el.get(KEY_ID)));
            if (inferred != null) {
                out.put("type", inferred);
            }
        } else {
            // 显式 type 走别名映射（texture → image, label → text）
            out.put("type", TypeInferrer.resolveAlias(String.valueOf(typeVal)));
        }
        for (Map.Entry<String, Object> entry : el.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (KEY_ID.equals(key) || KEY_CONDITION.equals(key)) {
                continue;
            }
            if (KEY_CHILDREN.equals(key)) {
                out.put(key, compileChildren(value, ctx));
                continue;
            }
            if (KEY_ACTIONS.equals(key)) {
                out.put(key, value); // 脚本不替换
                continue;
            }
            if ("type".equals(key)) {
                // type 已在前面推断注入，跳过原始值
                continue;
            }
            out.put(key, resolveValue(value, ctx));
        }
        return out;
    }

    /** children：Map 嵌套或 List 扁平列表 */
    private static Map<String, Object> compileChildren(Object raw, Context ctx) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (raw instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getValue() instanceof Map<?, ?> m)) {
                    continue;
                }
                Map<String, Object> child = asMap(m);
                if (shouldDrop(child, ctx)) {
                    continue;
                }
                out.put(String.valueOf(entry.getKey()), compileElement(child, ctx));
            }
        } else if (raw instanceof List<?> list) {
            int[] counter = {1};
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> m)) {
                    continue;
                }
                Map<String, Object> child = asMap(m);
                if (shouldDrop(child, ctx)) {
                    continue;
                }
                out.put(pickId(child, counter), compileElement(child, ctx));
            }
        }
        return out;
    }

    private static boolean shouldDrop(Map<String, Object> el, Context ctx) {
        Object condition = el.get(KEY_CONDITION);
        if (condition == null || String.valueOf(condition).isBlank()) {
            return false;
        }
        return ctx == null || !ctx.condition(String.valueOf(condition));
    }

    private static String pickId(Map<String, Object> el, int[] counter) {
        int n = counter[0]++;
        Object id = el.get(KEY_ID);
        if (id != null && !String.valueOf(id).isBlank()) {
            return String.valueOf(id);
        }
        return "el_" + n;
    }

    private static Object resolveValue(Object v, Context ctx) {
        if (v instanceof String s) {
            return ctx == null ? s : ctx.resolve(s);
        }
        if (v instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                out.put(String.valueOf(e.getKey()), resolveValue(e.getValue(), ctx));
            }
            return out;
        }
        if (v instanceof List<?> l) {
            List<Object> out = new ArrayList<>();
            for (Object o : l) {
                out.add(resolveValue(o, ctx));
            }
            return out;
        }
        return v;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object v) {
        return (Map<String, Object>) v;
    }

    private static double num(Object v, double fallback) {
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        return fallback;
    }
}
