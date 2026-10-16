package com.opendreamcore.adapter.dreamcore;

import com.opendreamcore.util.J8;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 旧版作用域变量/动态变量 表达式改写器。
 *
 *   界面变量.X = 表达式;      → vars["odc_ui_X"] = 表达式;
 *   用户变量.X = 表达式;      → vars["odc_user_X"] = 表达式;
 *   variable.X = 表达式;      → vars["odc_dyn_X"] = 表达式;
 *   局部变量.X                → 局部变量["X"]（DreamLang 当前 Scope 的局部容器）；
 *   读取：界面变量.X / 用户变量.X / variable.X → vars["odc_*_X"]
 *   DragonCore 的单个 & 拼接运算 → DreamLang 的 +（&& 保持逻辑运算）。
 *   三元分支 `? return x : return y` → `? x : y`。
 *   （Scope 页面变量通过 vars 命名空间共享，布局环境再按页面变量发布）。
 * 只对"表达式/脚本类"的字符串应用（调用方传白名单键），避免污染纯文本内容。
 */
public final class LegacyExpressionRewriter {

    private LegacyExpressionRewriter() {
    }

    /** 旧作用域前缀 → 新页面变量名前缀。 */
    private static final Map<String, String> SCOPE_PREFIX = J8.map(
            "界面变量", "odc_ui_",
            "用户变量", "odc_user_",
            "variable", "odc_dyn_",
            "Variable", "odc_dyn_");

    /** 标识符段：中英数下划线。 */
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_\\u4e00-\\u9fa5]+");

    /** 旧作用域读写统一改成当前 Scope 的命名空间下标访问。 */
    private static final Pattern SCOPE = Pattern.compile("界面变量|用户变量|[Vv]ariable|局部变量");

    /** 快速判定：不含任何作用域记号则原样返回。 */
    public static boolean touches(String s) {
        if (s == null) {
            return false;
        }
        return s.contains("界面变量.") || s.contains("用户变量.")
                || s.contains("variable.") || s.contains("Variable.")
                || s.contains("局部变量.");
    }

    /**
     * 改写一段脚本。扫描器只处理引号外的作用域访问，因而不会改动字符串中的
     * 色码、路径或示例文本；直接替换访问目标也保留原有分号和多语句边界。
     */
    public static String rewrite(String script) {
        if (script == null || script.isEmpty()) {
            return script;
        }
        StringBuilder out = new StringBuilder(script.length() + 16);
        boolean single = false;
        boolean dbl = false;
        boolean escaped = false;
        for (int i = 0; i < script.length();) {
            char ch = script.charAt(i);
            if (single || dbl) {
                out.append(ch);
                if (escaped) {
                    escaped = false;
                } else if (ch == '\\') {
                    escaped = true;
                } else if ((single && ch == '\'') || (dbl && ch == '"')) {
                    single = false;
                    dbl = false;
                }
                i++;
                continue;
            }
            if (ch == '\'') {
                single = true;
                out.append(ch);
                i++;
                continue;
            }
            if (ch == '"') {
                dbl = true;
                out.append(ch);
                i++;
                continue;
            }
            if ((i > 0 && Character.isJavaIdentifierPart(script.charAt(i - 1)))) {
                out.append(ch);
                i++;
                continue;
            }
            Matcher scope = SCOPE.matcher(script);
            scope.region(i, script.length());
            if (scope.lookingAt()) {
                String name = scope.group();
                int dot = i + name.length();
                if (dot < script.length() && script.charAt(dot) == '.') {
                    Matcher segment = NAME.matcher(script);
                    segment.region(dot + 1, script.length());
                    if (segment.lookingAt()) {
                        String key = segment.group();
                        if ("局部变量".equals(name)) {
                            out.append("局部变量[\"").append(key).append("\"]");
                        } else {
                            out.append("vars[\"").append(varName(name, key)).append("\"]");
                        }
                        i = dot + 1 + key.length();
                        continue;
                    }
                }
            }
            out.append(ch);
            i++;
        }
        return rewriteSingleAmpersands(rewriteTernaryReturns(out.toString()));
    }

    /** DragonCore 用单个 & 做文本拼接；双 && 必须保持逻辑短路运算。 */
    private static String rewriteSingleAmpersands(String script) {
        StringBuilder out = new StringBuilder(script.length());
        boolean single = false;
        boolean dbl = false;
        boolean escaped = false;
        for (int i = 0; i < script.length(); i++) {
            char ch = script.charAt(i);
            if (single || dbl) {
                out.append(ch);
                if (escaped) escaped = false;
                else if (ch == '\\') escaped = true;
                else if ((single && ch == '\'') || (dbl && ch == '"')) { single = false; dbl = false; }
                continue;
            }
            if (ch == '\'') { single = true; out.append(ch); continue; }
            if (ch == '"') { dbl = true; out.append(ch); continue; }
            if (ch == '&' && (i == 0 || script.charAt(i - 1) != '&')
                    && (i + 1 >= script.length() || script.charAt(i + 1) != '&')) out.append('+');
            else out.append(ch);
        }
        return out.toString();
    }

    /** 删除旧 DragonCore 三元分支中的 return；普通语句 return 保持不变。 */
    private static String rewriteTernaryReturns(String script) {
        StringBuilder out = new StringBuilder(script.length());
        boolean single = false;
        boolean dbl = false;
        boolean escaped = false;
        for (int i = 0; i < script.length();) {
            char ch = script.charAt(i);
            if (single || dbl) {
                out.append(ch);
                if (escaped) escaped = false;
                else if (ch == '\\') escaped = true;
                else if ((single && ch == '\'') || (dbl && ch == '"')) { single = false; dbl = false; }
                i++;
                continue;
            }
            if (ch == '\'') { single = true; out.append(ch); i++; continue; }
            if (ch == '"') { dbl = true; out.append(ch); i++; continue; }
            if (startsWord(script, i, "return") && ternaryBranchPrefix(out)) {
                i += 6;
                while (i < script.length() && Character.isWhitespace(script.charAt(i))) i++;
                continue;
            }
            out.append(ch);
            i++;
        }
        return out.toString();
    }

    private static boolean startsWord(String text, int at, String word) {
        if (!text.regionMatches(at, word, 0, word.length())) return false;
        int end = at + word.length();
        return (at == 0 || !Character.isJavaIdentifierPart(text.charAt(at - 1)))
                && (end == text.length() || !Character.isJavaIdentifierPart(text.charAt(end)));
    }

    private static boolean ternaryBranchPrefix(CharSequence text) {
        for (int i = text.length() - 1; i >= 0; i--) {
            char ch = text.charAt(i);
            if (Character.isWhitespace(ch)) continue;
            return ch == '?' || ch == ':';
        }
        return false;
    }

    private static String varName(String scope, String name) {
        String p = SCOPE_PREFIX.get(scope);
        return (p == null ? "odc_dyn_" : p) + name;
    }

    /**
     * 元素属性白名单改写：仅这些键的字符串值视为表达式参与改写
     * （布局七件套 + 旧滚动区四键 + 图片资源路径——菜单.yml 的贴图路径是变量引用）。
     * spec 子对象（image/chest_slot 等）一层内同样生效。
     */
    public static void rewriteElementExpressions(Map<String, Object> el) {
        for (String key : EXPRESSION_KEYS) {
            Object v = el.get(key);
            if (v instanceof String s) {
                el.put(key, rewrite(s));
            }
        }
        for (Object v : el.values()) {
            if (v instanceof Map<?, ?> spec) {
                for (Map.Entry<?, ?> e : spec.entrySet()) {
                    if (e.getValue() instanceof String s && EXPRESSION_KEYS.contains(e.getKey())) {
                        @SuppressWarnings("unchecked")
                        Map<Object, Object> mutable = (Map<Object, Object>) spec;
                        mutable.put(e.getKey(), rewrite(s));
                    }
                }
            }
        }
    }

    private static final java.util.Set<String> EXPRESSION_KEYS = J8.set(
            "x", "y", "width", "height", "z", "scale", "opacity", "rotation",
            "limitX", "limitY", "limitWidth", "limitHeight", "maxDistanceY", "maxDistanceX",
            "src", "hoverSrc", "visibleWhen", "enabledWhen");
}
