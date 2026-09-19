package com.opendreamcore.ui.theme;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 主题变量解析：把声明值里的 {name} 换成 vars 段定义的值。
 * 变量自身也可引用其它变量，深度护栏防循环。
 * 仅作用于主题层；页面数据插值仍走页面自身的 {{vars.xxx}} 体系，互不干扰。
 */
public final class ThemeVars {

    private static final Pattern REF = Pattern.compile("\\{([A-Za-z0-9_.\\-]+)}");
    private static final int MAX_DEPTH = 16;

    private ThemeVars() {
    }

    /** 对任意结构（Map/List/String/标量）做变量替换，返回新结构（不可变输入不被修改）。 */
    public static Object resolve(Object value, Map<String, Object> vars) {
        return resolve(value, vars, 0);
    }

    private static Object resolve(Object value, Map<String, Object> vars, int depth) {
        if (depth > MAX_DEPTH) {
            return value; // 循环引用护栏：原样返回
        }
        if (value instanceof String s) {
            return resolveString(s, vars, depth);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                out.put(String.valueOf(e.getKey()), resolve(e.getValue(), vars, depth + 1));
            }
            return out;
        }
        if (value instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            for (Object item : list) {
                out.add(resolve(item, vars, depth + 1));
            }
            return out;
        }
        return value;
    }

    /** 字符串级替换；整串恰好是一个引用时直接替换为变量的原始类型值。 */
    private static Object resolveString(String s, Map<String, Object> vars, int depth) {
        Matcher whole = Pattern.compile("^\\{([A-Za-z0-9_.\\-]+)}$").matcher(s.trim());
        if (whole.matches()) {
            Object v = vars.get(whole.group(1));
            if (v != null) {
                return resolve(v, vars, depth + 1); // 整串引用：保留原始类型（数字/布尔）
            }
            return s;
        }
        Matcher m = REF.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            Object v = vars.get(m.group(1));
            m.appendReplacement(sb, v == null ? Matcher.quoteReplacement(m.group(0))
                    : Matcher.quoteReplacement(String.valueOf(v)));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
