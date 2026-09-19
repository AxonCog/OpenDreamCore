package com.opendreamcore.ui.theme;

import com.opendreamcore.util.J8;

import com.opendreamcore.config.ConfigParseException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 主题解析。扁平为主，三种高级写法也认，和平铺共存：
 *
 * 嵌套规则（CSS Nesting 风格，默认后代组合，& 引用父选择器，> 显式子代）：
 * <pre>
 * ".card":
 *   background: {...}
 *   ".title":     { fontSize: 12 }        # = .card .title
 *   ">.badge":    { x: -4 }               # = .card > .badge
 *   "&:hover":    { background: ... }     # = .card:hover
 * </pre>
 *
 * 响应式块（@media 式，条件是窗口宽高）：
 * <pre>
 * "@max-width 854":                      # 顶层：里面必须写选择器
 *   button: { height: 14 }
 * ".card":
 *   "@max-width 500": { paddingAll: 4 }  # 规则内：直接就是声明，目标=父选择器
 * </pre>
 *
 * 多选择器逗号组："text, label"。
 *
 * 单条规则解析失败仅告警跳过，不中断整表加载——坏一条不能连累一页。
 */
public final class ThemeParser {

    public static final String KEY_SCHEMA = "schema";
    public static final String KEY_VARS = "vars";

    private static final int MAX_NEST_DEPTH = 8;

    private ThemeParser() {
    }

    /** ConfigIR → Theme。name 用于诊断与日志。 */
    public static Theme parse(String name, Map<String, Object> ir) {
        if (ir == null || ir.isEmpty()) {
            return Theme.EMPTY;
        }
        Map<String, Object> vars = new LinkedHashMap<>();
        Object varsRaw = ir.get(KEY_VARS);
        if (varsRaw instanceof Map<?, ?> vm) {
            for (Map.Entry<?, ?> e : vm.entrySet()) {
                if (e.getValue() != null) {
                    vars.put(String.valueOf(e.getKey()), e.getValue());
                }
            }
        } else if (varsRaw != null) {
            throw new ConfigParseException("主题 " + name + " 的 vars 段必须是映射", 0, 0);
        }

        List<StyleRule> rules = new ArrayList<>();
        int[] order = {0};
        for (Map.Entry<String, Object> entry : ir.entrySet()) {
            String key = entry.getKey();
            if (KEY_SCHEMA.equals(key) || KEY_VARS.equals(key)) {
                continue;
            }
            expandRule(name, key, null, null, entry.getValue(), rules, order, 0);
        }
        return new Theme(name, vars, rules);
    }

    // 递归展开：嵌套 / 媒体块在这里长出完整的规则

    /**
     * parentSelector：上层选择器原文（顶层为 null）
     * inheritedMedia：从上层 @媒体块继承的条件（可为 null）
     */
    private static void expandRule(String themeName, String keyText,
                                   String parentSelector, MediaQuery inheritedMedia,
                                   Object value, List<StyleRule> out,
                                   int[] order, int depth) {
        if (value == null) {
            return;
        }
        if (depth > MAX_NEST_DEPTH) {
            warn(themeName, "嵌套超过 " + MAX_NEST_DEPTH + " 层，放弃展开: " + keyText);
            return;
        }

        // —— @媒体键：解析条件，把值里的内容挂上条件继续展开 ——
        if (keyText.startsWith("@")) {
            MediaQuery media = MediaQuery.parse(keyText);
            if (media == null) {
                warn(themeName, "无法解析响应式条件 \"" + keyText + "\"，已跳过");
                return;
            }
            MediaQuery effective = MediaQuery.merge(inheritedMedia, media);
            if (!(value instanceof Map<?, ?> block)) {
                warn(themeName, "@" + " 块的值必须是映射: " + keyText);
                return;
            }
            for (Map.Entry<?, ?> e : block.entrySet()) {
                String childKey = String.valueOf(e.getKey());
                if (isSelectorKey(childKey)) {
                    expandRule(themeName, resolveNested(parentSelector, childKey),
                            parentSelector, effective, e.getValue(), out, order, depth + 1);
                } else if (parentSelector != null) {
                    // 规则内嵌 @媒体块且直接写声明 → 目标就是父选择器
                    addDeclarations(themeName, parentSelector, effective,
                            single(childKey, e.getValue()), out, order);
                } else {
                    // 顶层 @块的契约：里面所有键都是选择器（裸 tag 名也算）
                    expandRule(themeName, childKey, null, effective, e.getValue(), out, order, depth + 1);
                }
            }
            return;
        }

        // —— 普通键：值必须是声明映射；内部可能再藏嵌套/媒体 ——
        if (!(value instanceof Map<?, ?> declMap)) {
            warn(themeName, "规则 \"" + keyText + "\" 的值必须是映射，已跳过");
            return;
        }
        String fullSelector = parentSelector == null ? keyText
                : resolveNested(parentSelector, keyText);
        List<String> selectorTexts = splitSelectors(fullSelector);

        Map<String, Map<String, Object>> own = new LinkedHashMap<>();
        boolean anyValid = false;
        for (String sel : selectorTexts) {
            StyleSelector selector = StyleSelector.parse(sel);
            if (selector == null) {
                warn(themeName, "无法解析选择器 \"" + sel + "\"，已跳过");
                continue;
            }
            anyValid = true;
            // 每个并列选择器独立成规则，共享同一声明序起点（同权重按书写顺序决胜）
            for (Map.Entry<?, ?> d : declMap.entrySet()) {
                String prop = String.valueOf(d.getKey()).trim();
                if (!prop.isEmpty() && d.getValue() != null && !isSelectorKey(prop)) {
                    own.computeIfAbsent(sel, k -> new LinkedHashMap<>())
                            .put(prop, d.getValue());
                }
            }
        }
        if (!anyValid) {
            return;
        }
        for (Map.Entry<String, Map<String, Object>> e : own.entrySet()) {
            StyleSelector selector = StyleSelector.parse(e.getKey());
            if (selector != null) {
                out.add(new StyleRule(selector, e.getValue(),
                        order[0]++, themeName, inheritedMedia));
            }
        }

        // 继续挖嵌套：选择器样子的键往下传
        for (Map.Entry<?, ?> d : declMap.entrySet()) {
            String childKey = String.valueOf(d.getKey()).trim();
            if (isSelectorKey(childKey)) {
                String base = firstValid(selectorTexts); // 嵌套挂在第一个并列选择器下（与 CSS 行为一致）
                expandRule(themeName, childKey, base, inheritedMedia, d.getValue(), out, order, depth + 1);
            }
        }
    }

    /** 单属性便捷封装。 */
    private static void addDeclarations(String themeName, String selector, MediaQuery media,
                                        Map<String, Object> decls, List<StyleRule> out, int[] order) {
        for (Map.Entry<String, Map<String, Object>> sel : splitInto(selector, decls).entrySet()) {
            StyleSelector parsed = StyleSelector.parse(sel.getKey());
            if (parsed != null) {
                out.add(new StyleRule(parsed, sel.getValue(), order[0]++, themeName, media));
            }
        }
    }

    private static Map<String, Map<String, Object>> splitInto(String selector, Map<String, Object> decls) {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        out.put(selector, decls);
        return out;
    }

    private static Map<String, Object> single(String k, Object v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(k, v);
        return m;
    }

    private static String firstValid(List<String> selectors) {
        return selectors.get(0);
    }

    /**
     * 嵌套键 → 完整选择器：
     *   "&..."      → & 替换为父选择器原文（"&:hover"、"& > .x" 都行）
     *   "> xxx"     → 父 + 直接子代
     *   其他         → 父 + 后代（空格）
     * 顶层（无父）时去掉组合符裸用。
     */
    private static String resolveNested(String parent, String key) {
        String t = key.trim();
        if (parent == null) {
            return t.startsWith(">") ? t.substring(1).trim() : t.replace('&', ' ').trim();
        }
        if (t.startsWith("&")) {
            return t.replace("&", parent);
        }
        if (t.startsWith(">")) {
            return parent + " > " + t.substring(1).trim();
        }
        return parent + " " + t;
    }

    /**
     * 键是不是"选择器样子"。判定从宽：
     * ./#/@/&/ 开头、含空格或 > 或 :，都算；其余视为属性名。
     * 属性名是 camelCase 单词（paddingAll），天然不会撞车。
     */
    private static boolean isSelectorKey(String key) {
        if (key == null || J8.isBlank(key)) {
            return false;
        }
        char c0 = key.charAt(0);
        if (c0 == '.' || c0 == '#' || c0 == '@' || c0 == '&') {
            return true;
        }
        return key.contains(" ") || key.contains(">") || key.contains(":");
    }

    private static void warn(String themeName, String msg) {
        System.out.println("[OpenDreamCore][theme] 主题 " + themeName + ": " + msg);
    }

    /** 按顶层逗号拆分选择器组（括号/引号内的逗号不拆）。 */
    private static List<String> splitSelectors(String raw) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int depth = 0;
        boolean quoted = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '"') {
                quoted = !quoted;
                continue;
            }
            if (!quoted) {
                if (c == '[' || c == '(') {
                    depth++;
                } else if (c == ']' || c == ')') {
                    depth--;
                } else if (c == ',' && depth == 0) {
                    if (cur.length() != 0) {
                        out.add(cur.toString().trim());
                        cur.setLength(0);
                    }
                    continue;
                }
            }
            cur.append(c);
        }
        if (cur.length() != 0) {
            out.add(cur.toString().trim());
        }
        return out;
    }
}
