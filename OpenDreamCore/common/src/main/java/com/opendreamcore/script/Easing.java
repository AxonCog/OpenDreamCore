package com.opendreamcore.script;

/**
 * 缓动函数库（零依赖）：进度 p(0→1) → 缓动后的值(0→1)。
 * 动画系统的数学基础，客户端/服务端共用。
 */
public final class Easing {

    public enum Type {
        LINEAR,
        QUAD_IN, QUAD_OUT, QUAD_IN_OUT,
        CUBIC_IN, CUBIC_OUT, CUBIC_IN_OUT,
        SINE_IN, SINE_OUT, SINE_IN_OUT,
        ELASTIC_IN, ELASTIC_OUT,
        BOUNCE_OUT
    }

    private Easing() {
    }

    /** 默认线性。 */
    public static double apply(Type type, double p) {
        if (type == null) {
            return p;
        }
        return switch (type) {
            case LINEAR -> p;
            case QUAD_IN -> p * p;
            case QUAD_OUT -> p * (2 - p);
            case QUAD_IN_OUT -> p < 0.5 ? 2 * p * p : -1 + (4 - 2 * p) * p;
            case CUBIC_IN -> p * p * p;
            case CUBIC_OUT -> (p -= 1) * p * p + 1;
            case CUBIC_IN_OUT -> p < 0.5 ? 4 * p * p * p : (p - 1) * (2 * p - 2) * (2 * p - 2) + 1;
            case SINE_IN -> 1 - Math.cos(p * Math.PI / 2);
            case SINE_OUT -> Math.sin(p * Math.PI / 2);
            case SINE_IN_OUT -> 0.5 * (1 - Math.cos(Math.PI * p));
            case ELASTIC_IN -> elasticIn(p);
            case ELASTIC_OUT -> elasticOut(p);
            case BOUNCE_OUT -> bounceOut(p);
        };
    }

    private static double elasticIn(double p) {
        if (p == 0 || p == 1) {
            return p;
        }
        return -Math.pow(2, 10 * (p - 1)) * Math.sin((p - 1.1) * 5 * Math.PI);
    }

    private static double elasticOut(double p) {
        if (p == 0 || p == 1) {
            return p;
        }
        return Math.pow(2, -10 * p) * Math.sin((p - 0.1) * 5 * Math.PI) + 1;
    }

    private static double bounceOut(double p) {
        if (p < 1 / 2.75) {
            return 7.5625 * p * p;
        }
        if (p < 2 / 2.75) {
            p -= 1.5 / 2.75;
            return 7.5625 * p * p + 0.75;
        }
        if (p < 2.5 / 2.75) {
            p -= 2.25 / 2.75;
            return 7.5625 * p * p + 0.9375;
        }
        p -= 2.625 / 2.75;
        return 7.5625 * p * p + 0.984375;
    }
}
