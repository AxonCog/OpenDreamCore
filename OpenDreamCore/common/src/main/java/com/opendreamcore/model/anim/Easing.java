package com.opendreamcore.model.anim;

/**
 * 关键帧缓动函数全集：覆盖 bedrock 官方 easing 名称与 geckolib EasingType 的全部变体。
 *
 * 命名兼容（同一形状多个叫法）：
 *  - bedrock 写 "easing": "sine_in"/"ease_in_sine"（ease_ 前缀可省可带，函数体 _in/_out/_in_out）；
 *  - 形状族：sine, quad(可省略名=ease_in 原始语义), cubic, quart, quint,
 *    back, bounce, circle, elastic, expo, smooth(=in_out sine 变体), hold；
 *  - ease_in 无后缀=quad，ease_in_out 无后缀=quad，smooth=sine in_out；
 *  - back/elastic 支持 easingArgs（bedrock 允许幅度参数，缺省 back=1.70158, elastic=1 周期因子）。
 *
 * 输入 u 不在此夹取（调用方保证 [0,1]）；未知名称回 linear（宽容语义）。
 */
public final class Easing {

    private Easing() {
    }

    /** 主入口：按名称与参数缓动 u。 */
    public static double apply(String name, double u, double[] args) {
        if (name == null) {
            return u;
        }
        String s = name.trim().toLowerCase(java.util.Locale.ROOT);
        if (s.isEmpty() || "linear".equals(s)) {
            return u;
        }
        if ("step".equals(s) || "hold".equals(s)) {
            return u >= 1 ? 1 : 0;
        }
        if ("smooth".equals(s)) {
            return inOutSine(u);
        }
        // 拆族与方向："ease_in_out_sine" → 族 sine，方向 in_out；"sine_out" → sine/out
        String body = s.startsWith("ease_") ? s.substring(5) : s;
        if (body.equals("in")) {
            return u * u;
        }
        if (body.equals("out")) {
            return 1 - (1 - u) * (1 - u);
        }
        if (body.equals("in_out")) {
            return u < 0.5 ? 2 * u * u : 1 - 2 * (1 - u) * (1 - u);
        }
        String family;
        int dir; // 0=in, 1=out, 2=in_out
        // 两种命名序都收：family_direction（sine_in）与 direction_family（in_sine，bedrock ease_in_sine）
        if (body.startsWith("in_out_")) {
            family = body.substring(7);
            dir = 2;
        } else if (body.startsWith("out_")) {
            family = body.substring(4);
            dir = 1;
        } else if (body.startsWith("in_")) {
            family = body.substring(3);
            dir = 0;
        } else if (body.endsWith("_in_out")) {
            family = body.substring(0, body.length() - 7);
            dir = 2;
        } else if (body.endsWith("_out")) {
            family = body.substring(0, body.length() - 4);
            dir = 1;
        } else if (body.endsWith("_in")) {
            family = body.substring(0, body.length() - 3);
            dir = 0;
        } else {
            // 无方向后缀（如 "back"/"elastic"）按 in 处理
            family = body;
            dir = 0;
        }
        double amp = (args != null && args.length > 0) ? args[0] : 0;
        return dispatch(family, u, dir, amp);
    }

    private static double dispatch(String family, double u, int dir, double amp) {
        switch (family) {
            case "sine":
                return dir == 0 ? inSine(u) : dir == 1 ? outSine(u) : inOutSine(u);
            case "quad":
                return dir == 0 ? u * u : dir == 1 ? 1 - (1 - u) * (1 - u)
                        : u < 0.5 ? 2 * u * u : 1 - 2 * (1 - u) * (1 - u);
            case "cubic":
                return dir == 0 ? u * u * u : dir == 1 ? 1 - pow3(1 - u)
                        : u < 0.5 ? 4 * u * u * u : 1 - pow3(2 - 2 * u) / 2;
            case "quart":
                return dir == 0 ? pow4(u) : dir == 1 ? 1 - pow4(1 - u)
                        : u < 0.5 ? 8 * pow4(u) : 1 - pow4(2 - 2 * u) / 2;
            case "quint":
                return dir == 0 ? pow5(u) : dir == 1 ? 1 - pow5(1 - u)
                        : u < 0.5 ? 16 * pow5(u) : 1 - pow5(2 - 2 * u) / 2;
            case "expo":
                return dir == 0 ? (u == 0 ? 0 : Math.pow(2, 10 * u - 10))
                        : dir == 1 ? (u == 1 ? 1 : 1 - Math.pow(2, -10 * u))
                        : u == 0 ? 0 : u == 1 ? 1
                        : u < 0.5 ? Math.pow(2, 20 * u - 10) / 2
                        : (2 - Math.pow(2, -20 * u + 10)) / 2;
            case "circle":
                return dir == 0 ? 1 - Math.sqrt(1 - u * u)
                        : dir == 1 ? Math.sqrt(1 - (1 - u) * (1 - u))
                        : u < 0.5 ? (1 - Math.sqrt(1 - 4 * u * u)) / 2
                        : (Math.sqrt(1 - pow2(2 - 2 * u)) + 1) / 2;
            case "back": {
                double c = amp > 0 ? amp : 1.70158;
                double c2 = c * 1.525;
                return dir == 0 ? backIn(u, c) : dir == 1 ? 1 - backIn(1 - u, c)
                        : u < 0.5 ? pow2(2 * u) * ((c2 + 1) * 2 * u - c2) / 2
                        : (pow2(2 * u - 2) * ((c2 + 1) * (2 * u - 2) + c2) + 2) / 2;
            }
            case "bounce":
                return dir == 0 ? 1 - bounceOut(1 - u)
                        : dir == 1 ? bounceOut(u)
                        : u < 0.5 ? (1 - bounceOut(1 - 2 * u)) / 2
                        : (bounceOut(2 * u - 1) + 1) / 2;
            case "elastic": {
                if (u == 0 || u == 1) {
                    return u;
                }
                double k = amp > 0 ? amp : 1.0; // 周期因子：越大抖得越密
                if (dir == 0) {
                    return -Math.pow(2, 10 * u - 10) * Math.sin((10 * u - 10.75) * 2 * Math.PI / (2 + k));
                }
                if (dir == 1) {
                    return Math.pow(2, -10 * u) * Math.sin((10 * u - 0.75) * 2 * Math.PI / (2 + k)) + 1;
                }
                return u < 0.5
                        ? -Math.pow(2, 20 * u - 10) * Math.sin((20 * u - 11.125) * 2 * Math.PI / (4 + k)) / 2
                        : Math.pow(2, -20 * u + 10) * Math.sin((20 * u - 11.125) * 2 * Math.PI / (4 + k)) / 2 + 1;
            }
            default:
                return u; // 未知族宽容回 linear
        }
    }

    private static double inSine(double u) {
        return 1 - Math.cos(u * Math.PI / 2);
    }

    private static double outSine(double u) {
        return Math.sin(u * Math.PI / 2);
    }

    private static double inOutSine(double u) {
        return -(Math.cos(Math.PI * u) - 1) / 2;
    }

    private static double backIn(double u, double c) {
        return (c + 1) * u * u * u - c * u * u;
    }

    private static double bounceOut(double u) {
        double n1 = 7.5625;
        double d1 = 2.75;
        if (u < 1 / d1) {
            return n1 * u * u;
        }
        if (u < 2 / d1) {
            double t = u - 1.5 / d1;
            return n1 * t * t + 0.75;
        }
        if (u < 2.5 / d1) {
            double t = u - 2.25 / d1;
            return n1 * t * t + 0.9375;
        }
        double t = u - 2.625 / d1;
        return n1 * t * t + 0.984375;
    }

    private static double pow2(double v) {
        return v * v;
    }

    private static double pow3(double v) {
        return v * v * v;
    }

    private static double pow4(double v) {
        double v2 = v * v;
        return v2 * v2;
    }

    private static double pow5(double v) {
        double v2 = v * v;
        return v2 * v2 * v;
    }
}
