package com.opendreamcore.client.render;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 页面动画推进：吃页面 options.animations 段（元素 id → 关键帧列表），
 * 每 tick 算一遍各元素的当前状态。支持的写法：
 * {property: opacity, from: 0, to: 1, duration: 500, easing: quad_out}
 * {preset: breathe|spin|wave, duration: 2400}
 * easing 只有几种常用的：linear/quad_out/quad_in/sine_inout，
 * 老壳手搓插值，够表现"淡入/呼吸/摇摆"就行。
 * 状态存内存不落盘——页面关了动画就没了，重开重放，这正是想要的。
 * 循环语义与现代端 AnimationEngine 对齐：普通段默认播一遍定格在终点，
 * 写了 loop:true 才转圈；pingpong 也只在 loop:true 时生效，
 * 不然入场淡入会回绕砸到透明度 0，整个元素一闪一闪的。
 */
public final class PageAnimations {

    /** 单元素单帧动画状态：dx/dy 偏移（世界单位）、scaleMul 缩放倍率、alphaMul 透明度倍率、rotZ 旋转度。 */
    public static final class State {
        public double dx;
        public double dy;
        public double scaleMul = 1.0;
        public double alphaMul = 1.0;
        public double rotZ;
    }

    /** key = 页面 id，value = 该页动画时钟起点（毫秒）。页面关闭时清掉。 */
    private static final Map<String, Long> PAGE_CLOCK = new ConcurrentHashMap<>();

    private PageAnimations() {
    }

    /** 页面打开时把时钟归零：动画从头放，不是接着上次的尾巴。 */
    public static void resetPage(String pageId) {
        PAGE_CLOCK.put(String.valueOf(pageId), System.currentTimeMillis());
    }

    /** 页面在场但还没起过时钟（渲染首帧）时补一个起点；渲染层每帧调用。 */
    public static void resetPageIfAbsent(String pageId, long now) {
        PAGE_CLOCK.putIfAbsent(String.valueOf(pageId), now);
    }

    /** 页面动画时钟起点；没起过钟时返回当前时间（调用方一般会先 resetPageIfAbsent）。 */
    public static long pageOpen(String pageId) {
        Long t = PAGE_CLOCK.get(String.valueOf(pageId));
        return t == null ? System.currentTimeMillis() : t;
    }

    public static void dropPage(String pageId) {
        PAGE_CLOCK.remove(String.valueOf(pageId));
    }

    /**
     * 算元素当前动画状态。没有声明的段按"无动画"处理。
     * 普通段默认播一遍定格终点；写了 loop:true 才循环，pingpong 得搭配 loop 用。
     * preset 段默认循环（呼吸/旋转这类本来就是周期效果）。
     */
    public static State tick(String pageId, String elementId, Object animations, long now) {
        State s = new State();
        if (animations == null || elementId == null) {
            return s;
        }
        Object declared = null;
        if (animations instanceof Map) {
            declared = ((Map<?, ?>) animations).get(elementId);
        }
        if (!(declared instanceof List)) {
            return s;
        }
        Long start = PAGE_CLOCK.get(String.valueOf(pageId));
        if (start == null) {
            start = now;
            PAGE_CLOCK.put(String.valueOf(pageId), start);
        }
        long elapsed = now - start;
        for (Object seg : (List<?>) declared) {
            if (!(seg instanceof Map<?, ?>)) {
                continue;
            }
            Map<?, ?> m = (Map<?, ?>) seg;
            long dur = (long) num(m.get("duration"), 1000);
            if (dur <= 0) {
                continue;
            }
            Object preset = m.get("preset");
            if (preset != null) {
                boolean loop = flag(m, "loop", true); // preset 默认循环，这是周期效果的本分
                double t = loopProgress(elapsed, dur, num(m.get("delay"), 0), loop, flag(m, "pingpong", false));
                if (t < 0) {
                    continue; // 还在 delay 里
                }
                applyPreset(s, String.valueOf(preset), t);
                continue;
            }
            String prop = str(m.get("property"));
            double from = num(m.get("from"), 0);
            double to = num(m.get("to"), 1);
            double t = loopProgress(elapsed, dur, num(m.get("delay"), 0),
                    flag(m, "loop", false), flag(m, "pingpong", false));
            double v;
            if (t < 0) {
                v = from; // 还在 delay 里：先按起点值定格，淡入元素得先隐着
            } else {
                v = from + (to - from) * ease(String.valueOf(m.get("easing")), t);
            }
            if ("opacity".equals(prop)) {
                s.alphaMul *= clamp01(v);
            } else if ("scale".equals(prop)) {
                s.scaleMul *= v;
            } else if ("x".equals(prop)) {
                s.dx += v;
            } else if ("y".equals(prop)) {
                s.dy += v;
            }
        }
        return s;
    }

    private static void applyPreset(State s, String preset, double t) {
        if ("breathe".equals(preset)) {
            // 呼吸：透明度 0.55~1 正弦起伏，顺带轻微缩放，招牌灯箱的感觉
            double sine = 0.5 - 0.5 * Math.cos(t * Math.PI * 2);
            s.alphaMul *= 0.55 + 0.45 * sine;
            s.scaleMul *= 1.0 + 0.04 * sine;
        } else if ("spin".equals(preset)) {
            // 旋转：一整圈匀速转（billboard 面板上是绕 z 转，徽章图标正好）
            s.rotZ += t * 360.0;
        } else if ("wave".equals(preset)) {
            // 摇摆：左右各 0.02 世界单位晃 + 轻微透明起伏，提示文字常用
            double sine = Math.sin(t * Math.PI * 2);
            s.dx += 0.02 * sine;
            s.alphaMul *= 0.8 + 0.2 * (0.5 + 0.5 * Math.cos(t * Math.PI * 2));
        }
    }

    /**
     * 段进度：支持 delay 前摇；返回 -1 表示还没开始。
     * 不循环时夹在 0..1 播完定格；pingpong 用三角波来回摆。
     */
    private static double loopProgress(long elapsed, long dur, double delay, boolean loop, boolean pingpong) {
        double eff = elapsed - delay;
        if (eff < 0) {
            return -1;
        }
        if (!loop) {
            double t = eff / (double) dur;
            return t > 1 ? 1 : t;
        }
        if (pingpong) {
            double t = (eff % (dur * 2)) / (double) dur;
            return t > 1 ? 2 - t : t;
        }
        return (eff % dur) / (double) dur;
    }

    private static double ease(String name, double t) {
        if ("quad_out".equals(name)) {
            return 1 - (1 - t) * (1 - t);
        }
        if ("quad_in".equals(name)) {
            return t * t;
        }
        if ("sine_inout".equals(name)) {
            return 0.5 - 0.5 * Math.cos(t * Math.PI);
        }
        return t; // linear
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    /** 取布尔字段：真布尔直接用，字符串认 "true"，没写给默认值。 */
    private static boolean flag(Map<?, ?> m, String key, boolean fallback) {
        Object v = m.get(key);
        if (v instanceof Boolean) {
            return (Boolean) v;
        }
        if (v != null) {
            return "true".equalsIgnoreCase(String.valueOf(v));
        }
        return fallback;
    }

    private static double num(Object v, double fallback) {
        if (v instanceof Number) {
            return ((Number) v).doubleValue();
        }
        if (v != null) {
            try {
                return Double.parseDouble(String.valueOf(v));
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }
}
