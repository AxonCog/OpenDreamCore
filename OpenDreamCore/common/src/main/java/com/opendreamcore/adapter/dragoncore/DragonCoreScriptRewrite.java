package com.opendreamcore.adapter.dragoncore;

import com.opendreamcore.adapter.dreamcore.LegacyExpressionRewriter;
import com.opendreamcore.script.MethodRegistry;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 龙核脚本方言改写（住在本文件夹的"翻译腔"负责把老话说成 DreamLang 的样）。
 *
 * 龙核页面上跑的老脚本有几样 DreamLang 不惯的写法，这里统一掰直：
 *   - `方法.取玩家名(`  → `取玩家名(`（LegacyMethods 早把龙核一堆方法注册成
 *                          顶级方法了，卸掉命名空间马甲直接能用；不是顶级方法的
 *                          名字一个都不动，别把人家自造的局部函数误伤）；
 *   - `界面变量.X = …` / `用户变量.X = …` → 复用 LegacyExpressionRewriter 的
 *                          老手段，把作用域变量转成页面变量再说。
 *
 * 核心执行器一个字没改——语法自定义全靠适配器在门口白话一遍。
 */
public final class DragonCoreScriptRewrite {

    private DragonCoreScriptRewrite() {
    }

    /** `方法.标识符(` 这个形态（标识符 = 中英数下划线）。 */
    private static final Pattern METHOD_NS = Pattern.compile(
            "\\b方法\\.([A-Za-z0-9_\\u4e00-\\u9fa5]+)\\s*\\(");

    /** 过一遍方言：作用域变量先改，方法马甲再卸。 */
    public static String rewrite(String script) {
        if (script == null) {
            return null;
        }
        String out = LegacyExpressionRewriter.rewrite(script);
        return uncloakMethodNamespace(out);
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