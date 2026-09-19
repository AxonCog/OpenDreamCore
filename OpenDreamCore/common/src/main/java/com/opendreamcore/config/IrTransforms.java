package com.opendreamcore.config;

import com.opendreamcore.script.DreamLang;
import com.opendreamcore.script.Scope;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ConfigIR 声明式变换管线（4.5）。
 *
 * 页面顶层可写 transforms 列表，PageSchema.build 在把 IR 变成页面模型之前
 * 先跑一遍：每条变换用 DreamLang 表达式改写 IR 里任意 map 路径，变换跑完
 * 自己从 IR 摘掉（不会漏进变量表/元素表）。热重载天然成立——本地文件重扫、
 * /codc reload 都走 PageSchema.build，变换会重新执行。
 *
 * 声明式语法（transform 项）：
 *   - field / path: 点路径，改哪（variables.gold / options.hud.baseline）
 *   - set / expr:   DreamLang 表达式，求值结果写回该路径
 *   - when:         可选条件表达式，false/0 跳过本条
 *
 * 上下文（表达式里可直接引用）：
 *   - value：路径当前值
 *   - 页面 variables：裸名（gold）或 vars.gold 都行
 *   - 其余顶层非标准键（extras 透传）：裸名可读
 */
public final class IrTransforms {

    private IrTransforms() {
    }

    /** 标准页面键（PageSchema 会消费的），剩下的算 extras。 */
    private static final java.util.Set<String> STANDARD_KEYS;

    static {
        java.util.Set<String> s = new java.util.HashSet<>();
        for (String k : new String[]{"match", "title", "display", "Functions",
                "variables", "elements", "options", "transforms",
                "allowEscClose", "background", "through", "hideVanilla",
                "hideVanillaList", "animations", "world", "draggable",
                "theme", "design", "class", "style", "imports"}) {
            s.add(k);
        }
        STANDARD_KEYS = java.util.Collections.unmodifiableSet(s);
    }

    /**
     * 跑完直接返回同一份 IR（内容已改，transforms 键已摘）。
     * 单条变换出错只跳过该条，不炸整页。
     */
    public static Map<String, Object> apply(Map<String, Object> ir) {
        if (ir == null) {
            return ir;
        }
        Object raw = ir.get("transforms");
        if (!(raw instanceof List) && !(raw instanceof Map)) {
            return ir;
        }
        List<?> list = raw instanceof List
                ? (List<?>) raw
                : java.util.Collections.singletonList(raw);
        if (list.isEmpty()) {
            ir.remove("transforms");
            return ir;
        }
        for (Object item : list) {
            if (item instanceof Map) {
                applyOne(ir, (Map<?, ?>) item);
            }
        }
        ir.remove("transforms");
        return ir;
    }
private static void applyOne(Map<String, Object> ir, Map<?, ?> spec) {
        Object whenRaw = spec.get("when");
        Object fieldRaw = spec.get("field");
        if (fieldRaw == null) {
            fieldRaw = spec.get("path");
        }
        Object setRaw = spec.get("set");
        if (setRaw == null) {
            setRaw = spec.get("expr");
        }
        if (fieldRaw == null) {
            return;
        }
        String field = String.valueOf(fieldRaw).trim();
        String set = setRaw == null ? "" : String.valueOf(setRaw);
        if (field.isEmpty() || set.isEmpty()) {
            return;
        }
        try {
            Scope scope = buildScope(ir);
            if (whenRaw != null && !truthy(DreamLang.evaluate(String.valueOf(whenRaw), scope))) {
                return;
            }
            Object current = getPath(ir, field);
            scope.declare("value", current);
            Object result = DreamLang.evaluate(set, scope);
            setPath(ir, field, result);
        } catch (Throwable ignored) {
            // 单条变换出错只跳过该条，不炸整页（热重载也不该被一条坏变换卡死）
        }
    }

    /** 变换上下文：页面变量 + 顶层 extras 全部平铺成裸名（vars.x 与 x 都可读）。 */
    private static Scope buildScope(Map<String, Object> ir) {
        Scope scope = new Scope();
        for (Map.Entry<String, Object> e : ir.entrySet()) {
            String k = e.getKey();
            if (k == null || k.isEmpty() || "transforms".equals(k)) {
                continue;
            }
            if ("variables".equals(k)) {
                if (e.getValue() instanceof Map) {
                    for (Map.Entry<?, ?> v : ((Map<?, ?>) e.getValue()).entrySet()) {
                        scope.assignVar(String.valueOf(v.getKey()), v.getValue());
                    }
                }
                continue;
            }
            if (STANDARD_KEYS.contains(k)) {
                continue;
            }
            // extras 透传：非标准顶层键在变换表达式里按裸名可读
            scope.assignVar(k, e.getValue());
        }
        return scope;
    }

    /** 点路径取：variables.gold / options.hud.baseline；$. 前缀可带可不带。 */
    private static Object getPath(Map<String, Object> root, String path) {
        if (path.startsWith("$.")) {
            path = path.substring(2);
        } else if (path.startsWith("/")) {
            path = path.substring(1);
        }
        Object cur = root;
        for (String seg : path.split("\\.")) {
            if (cur instanceof Map) {
                cur = ((Map<?, ?>) cur).get(seg);
            } else {
                return null;
            }
        }
        return cur;
    }

    /** 点路径写（中间缺层自动补 map）。 */
    private static void setPath(Map<String, Object> root, String path, Object value) {
        if (path.startsWith("$.")) {
            path = path.substring(2);
        }
        String[] segs = path.split("\\.");
        Map<String, Object> cur = root;
        for (int i = 0; i < segs.length - 1; i++) {
            Object nxt = cur.get(segs[i]);
            if (!(nxt instanceof Map)) {
                Map<String, Object> created = new LinkedHashMap<>();
                cur.put(segs[i], created);
                nxt = created;
            }
            cur = (Map<String, Object>) nxt;
        }
        cur.put(segs[segs.length - 1], value);
    }

    private static boolean truthy(Object v) {
        if (v == null) {
            return false;
        }
        if (v instanceof Boolean) {
            return (Boolean) v;
        }
        if (v instanceof Number) {
            return ((Number) v).doubleValue() != 0;
        }
        String s = String.valueOf(v).trim().toLowerCase(java.util.Locale.ROOT);
        return s.equals("true") || s.equals("1") || s.equals("yes") || s.equals("on");
    }
}