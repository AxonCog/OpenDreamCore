package com.opendreamcore.model.molang;

import java.util.HashMap;
import java.util.Map;

/**
 * molang 子集求值器（SafeMath：白名单函数、无反射、无循环、深度受限）。
 *
 * 支持：数值 / 变量（query.* / variable.* / temp.*）/ 四则与 % / 一元负号 /
 * 比较与逻辑（< <= > >= == != && ||）/ 三目 ?: / 括号 /
 * math 函数白名单：abs sin cos tan asin acos atan sqrt pow floor ceil round clamp mod exp random.pi。
 * 未知函数/变量回退 0（或 strict=true 时抛出）。错误永不向上抛（非 strict 默认回退 0）。
 */
public final class MolangEval {

    /** 变量提供器：query.anim_time 等由动画层注入。 */
    public interface Variables {
        double get(String name);
    }

    private static final double PI = Math.PI;
    private static final int MAX_DEPTH = 48;

    private final Map<String, Fn> fns = new HashMap<String, Fn>();
    private final boolean strict;

    public MolangEval() {
        this(false);
    }

    public MolangEval(boolean strict) {
        this.strict = strict;
        reg("abs", 1, a -> Math.abs(a[0]));
        reg("sin", 1, a -> Math.sin(Math.toRadians(a[0])));
        reg("cos", 1, a -> Math.cos(Math.toRadians(a[0])));
        reg("tan", 1, a -> Math.tan(Math.toRadians(a[0])));
        reg("asin", 1, a -> Math.toDegrees(Math.asin(clamp1(a[0]))));
        reg("acos", 1, a -> Math.toDegrees(Math.acos(clamp1(a[0]))));
        reg("atan", 1, a -> Math.toDegrees(Math.atan(a[0])));
        reg("sqrt", 1, a -> Math.sqrt(Math.max(0, a[0])));
        reg("pow", 2, a -> Math.pow(a[0], a[1]));
        reg("floor", 1, a -> Math.floor(a[0]));
        reg("ceil", 1, a -> Math.ceil(a[0]));
        reg("round", 1, a -> Math.round(a[0]));
        reg("exp", 1, a -> Math.exp(a[0]));
        reg("mod", 2, a -> a[1] == 0 ? 0 : a[0] % a[1]);
        reg("clamp", 3, a -> Math.min(Math.max(a[0], a[1]), a[2]));
        reg("random", 2, a -> a[0] + Math.random() * (a[1] - a[0]));
    }

    private interface Fn {
        double apply(double[] args);
    }

    private void reg(String name, int arity, Fn fn) {
        fns.put(name, args -> {
            if (args.length != arity) {
                throw new IllegalArgumentException(name + " 参数数应为 " + arity);
            }
            return fn.apply(args);
        });
    }

    private static double clamp1(double v) {
        return Math.min(Math.max(v, -1), 1);
    }

    /** 便捷求值：无变量。失败（非 strict）返回 0。 */
    public double eval(String expr) {
        return eval(expr, name -> 0);
    }

    /** 求值：失败（非 strict）返回 0，strict 抛出。 */
    public double eval(String expr, Variables vars) {
        try {
            Parser p = new Parser(expr, vars);
            double v = p.ternary(0);
            if (!p.eof()) {
                throw new IllegalArgumentException("表达式末尾有多余内容");
            }
            return v;
        } catch (RuntimeException e) {
            if (strict) {
                throw e;
            }
            return 0;
        }
    }

    // ---------------------------------------------------------------- 递归下降

    private final class Parser {
        private final String s;
        private final Variables vars;
        private int i;

        Parser(String s, Variables vars) {
            this.s = s;
            this.vars = vars;
        }

        boolean eof() {
            skip();
            return i >= s.length();
        }

        private void skip() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
        }

        private char peek() {
            skip();
            return i < s.length() ? s.charAt(i) : '\0';
        }

        double ternary(int depth) {
            if (depth > MAX_DEPTH) {
                throw new IllegalArgumentException("表达式过深");
            }
            double cond = or(depth + 1);
            if (peek() == '?') {
                i++;
                double a = ternary(depth + 1);
                if (peek() != ':') {
                    throw new IllegalArgumentException("三目缺少 :");
                }
                i++;
                double b = ternary(depth + 1);
                return cond != 0 ? a : b;
            }
            return cond;
        }

        private double or(int d) {
            double v = and(d + 1);
            while (peek() == '|' && i + 1 < s.length() && s.charAt(i + 1) == '|') {
                i += 2;
                double r = and(d + 1);
                v = (v != 0 || r != 0) ? 1 : 0;
            }
            return v;
        }

        private double and(int d) {
            double v = cmp(d + 1);
            while (peek() == '&' && i + 1 < s.length() && s.charAt(i + 1) == '&') {
                i += 2;
                double r = cmp(d + 1);
                v = (v != 0 && r != 0) ? 1 : 0;
            }
            return v;
        }

        private double cmp(int d) {
            double v = add(d + 1);
            skip();
            if (i + 1 < s.length()) {
                char a = s.charAt(i);
                char b = s.charAt(i + 1);
                if (a == '<' && b == '=') {
                    i += 2;
                    return v <= add(d + 1) ? 1 : 0;
                }
                if (a == '>' && b == '=') {
                    i += 2;
                    return v >= add(d + 1) ? 1 : 0;
                }
                if (a == '=' && b == '=') {
                    i += 2;
                    return v == add(d + 1) ? 1 : 0;
                }
                if (a == '!' && b == '=') {
                    i += 2;
                    return v != add(d + 1) ? 1 : 0;
                }
            }
            if (i < s.length() && (s.charAt(i) == '<' || s.charAt(i) == '>')) {
                char a = s.charAt(i);
                i++;
                return a == '<' ? (v < add(d + 1) ? 1 : 0) : (v > add(d + 1) ? 1 : 0);
            }
            return v;
        }

        private double add(int d) {
            double v = mul(d + 1);
            while (true) {
                char c = peek();
                if (c == '+') {
                    i++;
                    v += mul(d + 1);
                } else if (c == '-') {
                    i++;
                    v -= mul(d + 1);
                } else {
                    return v;
                }
            }
        }

        private double mul(int d) {
            double v = unary(d + 1);
            while (true) {
                char c = peek();
                if (c == '*') {
                    i++;
                    v *= unary(d + 1);
                } else if (c == '/') {
                    i++;
                    double r = unary(d + 1);
                    v = r == 0 ? 0 : v / r;
                } else if (c == '%') {
                    i++;
                    double r = unary(d + 1);
                    v = r == 0 ? 0 : v % r;
                } else {
                    return v;
                }
            }
        }

        private double unary(int d) {
            char c = peek();
            if (c == '-') {
                i++;
                return -unary(d + 1);
            }
            if (c == '+') {
                i++;
                return unary(d + 1);
            }
            return atom(d + 1);
        }

        private double atom(int d) {
            if (d > MAX_DEPTH) {
                throw new IllegalArgumentException("表达式过深");
            }
            skip();
            if (i >= s.length()) {
                throw new IllegalArgumentException("表达式提前结束");
            }
            char c = s.charAt(i);
            if (c == '(') {
                i++;
                double v = ternary(d + 1);
                if (peek() != ')') {
                    throw new IllegalArgumentException("缺少右括号");
                }
                i++;
                return v;
            }
            if (c == '.' && i + 1 < s.length() && s.charAt(i + 1) == 'p' && s.startsWith(".pi", i)) {
                i += 3;
                return PI;
            }
            if (Character.isDigit(c)) {
                return number();
            }
            if (Character.isJavaIdentifierStart(c)) {
                return identifier(d);
            }
            throw new IllegalArgumentException("无法识别的字符: " + c);
        }

        private double number() {
            int start = i;
            while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.')) {
                i++;
            }
            return Double.parseDouble(s.substring(start, i));
        }

        private double identifier(int d) {
            int start = i;
            while (i < s.length() && (Character.isJavaIdentifierPart(s.charAt(i)) || s.charAt(i) == '.')) {
                i++;
            }
            String name = s.substring(start, i);
            skip();
            if (i < s.length() && s.charAt(i) == '(') {
                i++;
                java.util.List<Double> args = new java.util.ArrayList<Double>();
                if (peek() != ')') {
                    args.add(ternary(d + 1));
                    while (peek() == ',') {
                        i++;
                        args.add(ternary(d + 1));
                    }
                }
                if (peek() != ')') {
                    throw new IllegalArgumentException("函数缺少右括号: " + name);
                }
                i++;
                Fn fn = fns.get(name);
                if (fn == null && name.regionMatches(true, 0, "math.", 0, 5)) {
                    fn = fns.get(name.substring(5));
                }
                if (fn == null) {
                    throw new IllegalArgumentException("未知函数: " + name);
                }
                double[] a = new double[args.size()];
                for (int k = 0; k < a.length; k++) {
                    a[k] = args.get(k);
                }
                return fn.apply(a);
            }
            // 变量：math.pi / query.* / variable.* / temp.*
            if (name.equalsIgnoreCase("math.pi")) {
                return PI;
            }
            if (name.equalsIgnoreCase("math.e")) {
                return Math.E;
            }
            return vars.get(name);
        }
    }
}
