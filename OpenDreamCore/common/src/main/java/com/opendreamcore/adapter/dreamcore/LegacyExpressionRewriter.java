package com.opendreamcore.adapter.dreamcore;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 旧版作用域变量/动态变量 表达式改写器。
 *
 * 旧方言（菜单.yml）在新执行器里无法解析的部分：
 *   界面变量.X = 表达式;      → Screen.设置变量("odc_ui_X", 表达式);
 *   用户变量.X = 表达式;      → Screen.设置变量("odc_user_X", 表达式);
 *   variable.X = 表达式;      → Screen.设置变量("odc_dyn_X", 表达式);
 *   读取：界面变量.X / 用户变量.X / variable.X → 对应裸页面变量名
 *   （LayoutEngine 把页面变量平铺进求值环境，裸名可直接引用）。
 *
 * 只对"表达式/脚本类"的字符串应用（调用方传白名单键），避免污染纯文本内容。
 */
public final class LegacyExpressionRewriter {

    private LegacyExpressionRewriter() {
    }

    /** 旧作用域前缀 → 新页面变量名前缀。 */
    private static final Map<String, String> SCOPE_PREFIX = Map.of(
            "界面变量", "odc_ui_",
            "用户变量", "odc_user_",
            "variable", "odc_dyn_",
            "Variable", "odc_dyn_");

    /** 标识符段：中英数下划线。 */
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_\\u4e00-\\u9fa5]+");

    /** 赋值行：[作用域.]名 = 表达式;? （行模式，表达式到行尾/分号） */
    private static final Pattern ASSIGNMENT = Pattern.compile(
            "^([ \\t]*)(界面变量|用户变量|[Vv]ariable)\\.([A-Za-z0-9_\\u4e00-\\u9fa5]+)\\s*=\\s*(.+?);?[ \\t]*$",
            Pattern.MULTILINE);

    /** 作用域读引用。 */
    private static final Pattern READ = Pattern.compile(
            "(界面变量|用户变量|[Vv]ariable)\\.([A-Za-z0-9_\\u4e00-\\u9fa5]+)");

    /** 快速判定：不含任何作用域记号则原样返回。 */
    public static boolean touches(String s) {
        if (s == null) {
            return false;
        }
        return s.contains("界面变量.") || s.contains("用户变量.")
                || s.contains("variable.") || s.contains("Variable.");
    }

    /** 改写一段脚本：先转赋值行，再替换剩余读取点。 */
    public static String rewrite(String script) {
        if (!touches(script)) {
            return script;
        }
        String out = script;
        // 1) 赋值行 → Screen.设置变量(...)
        Matcher m = ASSIGNMENT.matcher(out);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String var = varName(m.group(2), m.group(3));
            // 表达式内部的作用域读也一并改写
            String expr = rewriteReads(m.group(4));
            m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(
                    m.group(1) + "Screen.设置变量(\"" + var + "\", " + expr + ");"));
        }
        m.appendTail(sb);
        out = sb.toString();
        // 2) 剩余读取点
        out = rewriteReads(out);
        return out;
    }

    /** 仅替换读取引用（赋值行内部表达式用）。 */
    private static String rewriteReads(String in) {
        if (in == null || !touches(in)) {
            return in;
        }
        Matcher m = READ.matcher(in);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(varName(m.group(1), m.group(2))));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String varName(String scope, String name) {
        String p = SCOPE_PREFIX.getOrDefault(scope, "odc_dyn_");
        return p + name;
    }

    /**
     * 元素属性白名单改写：仅这些键的字符串值视为表达式参与改写
     * （布局七件套 + 旧滚动区四键 + 图片资源路径——菜单.yml 的贴图路径是变量引用）。
     * spec 子对象（image/chest_slot 等）一层内同样生效。
     */
    public static void rewriteElementExpressions(Map<String, Object> el) {
        for (String key : EXPRESSION_KEYS) {
            Object v = el.get(key);
            if (v instanceof String s && touches(s)) {
                el.put(key, rewrite(s));
            }
        }
        for (Object v : el.values()) {
            if (v instanceof Map<?, ?> spec) {
                for (Map.Entry<?, ?> e : spec.entrySet()) {
                    if (e.getValue() instanceof String s && touches(s)) {
                        @SuppressWarnings("unchecked")
                        Map<Object, Object> mutable = (Map<Object, Object>) spec;
                        mutable.put(e.getKey(), rewrite(s));
                    }
                }
            }
        }
    }

    private static final java.util.Set<String> EXPRESSION_KEYS = java.util.Set.of(
            "x", "y", "width", "height", "z", "scale", "opacity", "rotation",
            "limitX", "limitY", "limitWidth", "limitHeight", "maxDistanceY", "maxDistanceX",
            "src", "hoverSrc");
}
