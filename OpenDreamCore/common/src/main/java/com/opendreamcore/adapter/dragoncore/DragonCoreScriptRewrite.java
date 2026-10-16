package com.opendreamcore.adapter.dragoncore;

import com.opendreamcore.adapter.dreamcore.LegacyExpressionRewriter;
import com.opendreamcore.script.MethodRegistry;
import com.opendreamcore.util.J8;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 龙核脚本改写：旧写法 → DreamLang 等价写法。
 *
 * 龙核页面上跑的老脚本有几样 DreamLang 不惯的写法，这里统一掰直：
 *   - `方法.取玩家名(`  → `取玩家名(`（LegacyMethods 早把龙核一堆方法注册成
 *                          顶级方法了，去掉命名空间前缀直接能用；不是顶级方法的
 *                          名字一个都不动，别把人家自造的局部函数误伤）；
 *   - `界面变量.X = …` / `用户变量.X = …` → 复用 LegacyExpressionRewriter 的
 *                          老手段，把作用域变量转成页面变量再说。
 *
 * 核心执行器未改动——语法自定义全部由适配器完成。
 */
public final class DragonCoreScriptRewrite {

    private DragonCoreScriptRewrite() {
    }

    /** `方法.标识符(` 这个形态（标识符 = 中英数下划线）。 */
    /** 仅在方法名左侧不是标识符时匹配，中文字符不适用 Java \b 的 ASCII 边界语义。 */
    private static final Pattern METHOD_NS = Pattern.compile(
            "(?<![A-Za-z0-9_\\u4e00-\\u9fa5])方法\\.([A-Za-z0-9_\\u4e00-\\u9fa5]+)\\s*\\(");
    /** 旧脚本允许省略括号的零参调用；集合故意保守，避免把属性/自定义函数当调用。 */
    private static final Set<String> ZERO_ARG_METHODS = J8.set(
            "关闭界面", "取屏幕高度", "取屏幕宽度", "取滚轮值", "取当前按下键",
            "取当前时间格式化", "播放声音", "取界面存活时间", "取当前时间", "取玩家名");

    /** 零参调用后允许出现的语句/表达式边界；不跨标识符直接替换。 */
    private static final Pattern ZERO_ARG = Pattern.compile(
            "方法\\.([A-Za-z0-9_\\u4e00-\\u9fa5]+)(?=\\s*(?:$|[;；,，)\\]}:+\\-*/%<>=&|?]))",
            Pattern.MULTILINE);

    private static final Pattern METHOD_BARE = Pattern.compile(
            "(?<![A-Za-z0-9_\\u4e00-\\u9fa5])方法\\.([A-Za-z0-9_\\u4e00-\\u9fa5]+)(?=\\s*(?:$|[;；]))",
            Pattern.MULTILINE);

    /** 过一遍改写：先改作用域变量，再补安全零参调用，最后还原方法别名。 */
    public static String rewrite(String script) {
        if (script == null) {
            return null;
        }
        String out = LegacyExpressionRewriter.rewrite(script);
        out = addZeroArgParens(out);
        out = addBareMethodParens(out);
        return uncloakMethodNamespace(out);
    }

    private static String addBareMethodParens(String in) {
        Matcher m = METHOD_BARE.matcher(in);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String method = m.group(1);
            if (MethodRegistry.contains(method)) {
                m.appendReplacement(sb, Matcher.quoteReplacement("方法." + method + "()"));
            } else {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** 仅对已登记且明确属于旧零参名单的方法补括号；未知函数和属性保持原文。 */
    private static String addZeroArgParens(String in) {
        if (in == null || !in.contains("方法.")) {
            return in;
        }
        Matcher m = ZERO_ARG.matcher(in);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String method = m.group(1);
            if (ZERO_ARG_METHODS.contains(method) && MethodRegistry.contains(method)) {
                m.appendReplacement(sb, Matcher.quoteReplacement("方法." + method + "()"));
            } else {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** 只有 MethodRegistry 真认识的名字才敢把 `方法.` 摘掉，别的原样给人自己写。 */
    private static String uncloakMethodNamespace(String in) {
        if (in == null || !in.contains("方法.")) {
            return in;
        }
        Matcher m = METHOD_NS.matcher(in);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String method = m.group(1);
            if (MethodRegistry.contains(method)) {
                m.appendReplacement(sb, Matcher.quoteReplacement(method + "("));
            } else {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }
}