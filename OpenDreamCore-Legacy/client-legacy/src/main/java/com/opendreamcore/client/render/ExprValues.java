package com.opendreamcore.client.render;

import com.opendreamcore.script.engine.DreamLang;
import com.opendreamcore.script.engine.Scope;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 元素级属性的表达式求值。
 *
 * <p>为什么单独一层：页布局的 x/y/width/height 走 {@link PageRenderer} 自己的 evalNum，
 * 但元素 props（rect 的 width/height、progress 的 value、count、opacity…）统一经
 * {@link Painters#num(Object)} 读，那条路过去只认字面量——写成 {@code "2 * health_ratio"}
 * 会被当成普通字符串解析失败，宽度直接退化成 0（老版本血条不伸缩的根因）。
 *
 * <p>环境由绘制方 push：{@link PageRenderer} 每个元素 push 一次（含实体实时变量、
 * 全局变量、window/parent 命名空间、页面变量），语义与高版本 LayoutEngine.scopeOf 一致。
 */
public final class ExprValues {

    private ExprValues() {
    }

    private static final ThreadLocal<Deque<Map<String, Object>>> STACK = new ThreadLocal<Deque<Map<String, Object>>>() {
        @Override
        protected Deque<Map<String, Object>> initialValue() {
            return new ArrayDeque<Map<String, Object>>();
        }
    };

    /** 压入一层作用域（绘制一个元素前调）。 */
    public static void push(Map<String, Object> env) {
        if (env != null) {
            STACK.get().push(env);
        }
    }

    /** 弹出当前作用域（元素画完调，含提前 return 的路径）。 */
    public static void pop() {
        Deque<Map<String, Object>> d = STACK.get();
        if (!d.isEmpty()) {
            d.pop();
        }
    }

    /** 当前作用域；没压过返回 null。 */
    public static Map<String, Object> current() {
        Deque<Map<String, Object>> d = STACK.get();
        return d.isEmpty() ? null : d.peek();
    }

    /** 字面量优先；不是字面量就用当前作用域求值。 */
    public static double num(Object v) {
        return num(v, 0);
    }

    public static double num(Object v, double fallback) {
        return num(v, current(), fallback);
    }

    /**
     * 数字解析：数字直接取；字符串先试字面量，再按表达式求值；其余走 fallback。
     *
     * @param env 求值作用域，为 null 时不做表达式（调用方保证已 push）
     */
    public static double num(Object v, Map<String, Object> env, double fallback) {
        if (v instanceof Number) {
            return ((Number) v).doubleValue();
        }
        if (v == null) {
            return fallback;
        }
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) {
            return fallback;
        }
        try {
            if (s.matches("-?\\d+(\\.\\d+)?")) {
                return Double.parseDouble(s);
            }
            if (env == null) {
                return fallback;
            }
            return evaluate(s, env, fallback);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    /** 表达式求值：window/parent/this 走局部命名空间（成员链 window.width 才通），
     *  其余进 vars（裸名/vars.xxx 都能引用）——和高版本 LayoutEngine.scopeOf 同款。 */
    private static double evaluate(String s, Map<String, Object> env, double fallback) {
        Scope scope = new Scope();
        for (Map.Entry<String, Object> en : env.entrySet()) {
            String k = en.getKey();
            if ("window".equals(k) || "parent".equals(k) || "this".equals(k)) {
                scope.assign(k, en.getValue());
            } else {
                scope.assignVar(k, en.getValue());
            }
        }
        Object out = DreamLang.evaluate(s, scope);
        if (out instanceof Number) {
            return ((Number) out).doubleValue();
        }
        return fallback;
    }

    /** 供调用方自建作用域用（避免到处 new LinkedHashMap）。 */
    public static Map<String, Object> newEnv() {
        return new LinkedHashMap<String, Object>();
    }
}