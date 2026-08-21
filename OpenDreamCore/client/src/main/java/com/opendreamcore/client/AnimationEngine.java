package com.opendreamcore.client;

import com.opendreamcore.script.Easing;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 元素动画引擎。
 *
 * 用法一：自动播放（key = 元素 id，页面打开即播）：
 *   animations:
 *     title_text:
 *       - property: y
 *         from: -40
 *         to: 10
 *         duration: 700
 *         easing: bounce
 *
 * 用法二：命名动画（条目带 target，脚本用 Screen.播放动画("名称") 触发）：
 *   animations:
 *     title_bounce:
 *       - target: title_text
 *         property: y
 *         from: -40
 *         to: 10
 *         duration: 700
 *         easing: bounce
 *
 * 属性：x / y / scale / opacity / rotation（位置/缩放/透明度/旋转角，度）
 * 路径动画：property: path + points: [[x,y],...]（分段线性插值）
 * 往返：pingpong: true（loop 时来回摆，breathe/pulse 用）
 * 预置特效：preset: blink|breathe|pulse|pop|elastic|bounce|spin|shake|wave|swing|flash
 *   | slide_left|slide_right|slide_up|slide_down|fade_in|fade_out
 *   | fade_in_up|fade_in_down|fade_out_down|zoom_in|zoom_out
 *   （to/amplitude 调幅度，duration 调时长；多 Def 组合自动衔接）
 * 延迟：delay: 毫秒（序列动画用）
 */
public final class AnimationEngine {

    /** 单条动画定义。 */
    public record Def(String elementId, String property, double from, double to,
                      int duration, Easing.Type easing, boolean loop, boolean pingpong,
                      double[][] points, int delay) {
    }

    /** 运行状态：定义 + 开始时间。 */
    private record State(Def def, long startMs) {
    }

    private static final AnimationEngine INSTANCE = new AnimationEngine();

    /**
     * 自动播放/脚本触发按"页面作用域 + 元素 id"隔离：屏幕/HUD/世界页面同时渲染时，
     * 同名元素 id 的动画互不串扰（tick/offset 传 scope = 页面 id）。
     * 命名动画（namedAnimations）按名称全局注册（同名后注册覆盖，脚本触发用）。
     */
    private final Map<String, List<State>> autoStates = new ConcurrentHashMap<>();
    /** 命名动画：名称 → 定义列表（脚本触发用）。 */
    private final Map<String, List<Def>> namedAnimations = new ConcurrentHashMap<>();
    /** 脚本触发：作用域键 → 状态列表。 */
    private final Map<String, List<State>> triggeredStates = new ConcurrentHashMap<>();
    private final Map<String, Long> seen = new ConcurrentHashMap<>();
    private final Map<String, Long> namedSeen = new ConcurrentHashMap<>();

    // 暂停支持：元素 id → 暂停时刻（恢复时补回；暂停对所有作用域生效）
    private final Map<String, Long> pausedAt = new ConcurrentHashMap<>();

    /** 作用域键：scope + "\u0001" + elementId（scope 空 = 全局/旧行为）。 */
    private static String key(String elementId, String scope) {
        return scope == null || scope.isEmpty() ? elementId : scope + "\u0001" + elementId;
    }

    private AnimationEngine() {
    }

    public static AnimationEngine get() {
        return INSTANCE;
    }

    /** 每帧调用：收集页面动画定义（自动播放 + 命名注册），新定义启动自动动画。 */
    public void tick(Map<String, Object> pageOptions) {
        tick(null, pageOptions);
    }

    public void tick(String scope, Map<String, Object> pageOptions) {
        tick(scope, pageOptions, null);
    }

    /** vars 非空时 from/to 支持表达式（"vars.coin" 等，动画数值由页面变量驱动）。 */
    public void tick(String scope, Map<String, Object> pageOptions, Map<String, Object> vars) {
        if (pageOptions == null) {
            return;
        }
        Object raw = pageOptions.get("animations");
        if (!(raw instanceof Map<?, ?> animations)) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Map.Entry<?, ?> entry : animations.entrySet()) {
            String key = String.valueOf(entry.getKey());
            if (!(entry.getValue() instanceof List<?> defs)) {
                continue;
            }
            for (Object defRaw : defs) {
                if (!(defRaw instanceof Map<?, ?> def)) {
                    continue;
                }
                String target = str(def.get("target"), null);
                if (target != null) {
                    // 命名动画：注册，不自动播放（重复定义去重：同名同内容只注册一次）
                    String namedKey = key + ":" + def.hashCode() + ":" + defs.hashCode();
                    if (namedSeen.containsKey(namedKey) && namedSeen.get(namedKey) > now - 60000) {
                        continue;
                    }
                    namedSeen.put(namedKey, now);
                    for (Def d : parseDefs(target, def, vars)) {
                        namedAnimations.computeIfAbsent(key, k -> new ArrayList<>()).add(d);
                    }
                } else {
                    // 自动播放：key = 元素 id；先查去重键再解析（每帧 tick 零分配）
                    String stateKey = AnimationEngine.key(key, scope);
                    String seenKey = stateKey + ":" + def.hashCode() + ":" + defs.hashCode();
                    if (seen.containsKey(seenKey) && seen.get(seenKey) > now - 60000) {
                        continue;
                    }
                    seen.put(seenKey, now);
                    for (Def d : parseDefs(key, def, vars)) {
                        autoStates.computeIfAbsent(stateKey, k -> new ArrayList<>())
                                .add(new State(d, now + d.delay()));
                    }
                }
            }
        }
    }

    /** 解析条目 → 定义列表（预置特效展开为多条）。 */
    private static List<Def> parseDefs(String elementId, Map<?, ?> def, Map<String, Object> vars) {
        String preset = str(def.get("preset"), null);
        if (preset != null) {
            return presetDefs(elementId, preset, def);
        }
        Def d = parseDef(elementId, def, vars);
        return d == null ? List.of() : List.of(d);
    }

    /** 预置特效展开（多 Def 组合；to/amplitude 可调幅度，duration 可调时长）。 */
    private static List<Def> presetDefs(String elementId, String preset, Map<?, ?> def) {
        int duration = (int) num(def.get("duration"), 0);
        switch (preset) {
            case "blink" -> {
                return List.of(new Def(elementId, "opacity", 1, 0,
                        duration > 0 ? duration : 400, Easing.Type.QUAD_IN_OUT, true, false, null, 0));
            }
            case "breathe" -> {
                // 振幅可配：to（缺省 1.06，hologram.breathe 直达时传 1 + amplitude）
                double to = num(def.get("to"), 1.06);
                return List.of(new Def(elementId, "scale", 1, to,
                        duration > 0 ? duration : 1500, Easing.Type.SINE_IN_OUT, true, true, null, 0));
            }
            case "pulse" -> {
                return List.of(new Def(elementId, "scale", 0.94, 1.06,
                        duration > 0 ? duration : 500, Easing.Type.QUAD_IN_OUT, true, true, null, 0));
            }
            case "pop" -> {
                // 弹出：1 → to(1.15) → 1（两段，第二段延迟半程）
                double to = num(def.get("to"), num(def.get("amplitude"), 0.15)) + 1;
                int dur = duration > 0 ? duration : 240;
                return List.of(
                        new Def(elementId, "scale", 1, to, dur / 2, Easing.Type.QUAD_OUT, false, false, null, 0),
                        new Def(elementId, "scale", to, 1, dur / 2, Easing.Type.QUAD_IN, false, false, null, dur / 2));
            }
            case "elastic" -> {
                // 弹簧入场：0.5 → 1（ELASTIC 回弹）
                return List.of(new Def(elementId, "scale", 0.5, 1,
                        duration > 0 ? duration : 700, Easing.Type.ELASTIC_OUT, false, false, null, 0));
            }
            case "bounce" -> {
                // 落地弹跳：y -24 → 0（BOUNCE_OUT）
                return List.of(new Def(elementId, "y", -24, 0,
                        duration > 0 ? duration : 700, Easing.Type.BOUNCE_OUT, false, false, null, 0));
            }
            case "spin" -> {
                return List.of(new Def(elementId, "rotation", 0, 360,
                        duration > 0 ? duration : 1800, Easing.Type.LINEAR, true, false, null, 0));
            }
            case "shake" -> {
                // 抖动：x 往返，幅度 to/amplitude（缺省 5px）
                double amp = num(def.get("to"), num(def.get("amplitude"), 5));
                return List.of(new Def(elementId, "x", -amp, amp,
                        duration > 0 ? duration : 120, Easing.Type.LINEAR, true, true, null, 0));
            }
            case "wave" -> {
                // 波浪：rotation 往返摆动（to/amplitude = 角度，缺省 10°）
                double amp = num(def.get("to"), num(def.get("amplitude"), 10));
                return List.of(new Def(elementId, "rotation", -amp, amp,
                        duration > 0 ? duration : 800, Easing.Type.SINE_IN_OUT, true, true, null, 0));
            }
            case "swing" -> {
                // 钟摆：0 → -15 → 15 → 0（三段衔接，to/amplitude 可调角度）
                double amp = num(def.get("to"), num(def.get("amplitude"), 15));
                int dur = duration > 0 ? duration : 600;
                int seg = Math.max(60, dur / 3);
                return List.of(
                        new Def(elementId, "rotation", 0, -amp, seg, Easing.Type.QUAD_OUT, false, false, null, 0),
                        new Def(elementId, "rotation", -amp, amp, seg, Easing.Type.SINE_IN_OUT, false, false, null, seg),
                        new Def(elementId, "rotation", amp, 0, seg, Easing.Type.QUAD_IN, false, false, null, seg * 2));
            }
            case "flash" -> {
                // 闪烁两次：1 → 0.15 → 1 → 0.15 → 1
                int dur = duration > 0 ? duration : 420;
                int seg = Math.max(60, dur / 4);
                return List.of(
                        new Def(elementId, "opacity", 1, 0.15, seg, Easing.Type.QUAD_IN_OUT, false, false, null, 0),
                        new Def(elementId, "opacity", 0.15, 1, seg, Easing.Type.QUAD_IN_OUT, false, false, null, seg),
                        new Def(elementId, "opacity", 1, 0.15, seg, Easing.Type.QUAD_IN_OUT, false, false, null, seg * 2),
                        new Def(elementId, "opacity", 0.15, 1, seg, Easing.Type.QUAD_IN_OUT, false, false, null, seg * 3));
            }
            case "slide_left" -> {
                return List.of(new Def(elementId, "x", -300, 0,
                        duration > 0 ? duration : 500, Easing.Type.QUAD_OUT, false, false, null, 0));
            }
            case "slide_right" -> {
                return List.of(new Def(elementId, "x", 300, 0,
                        duration > 0 ? duration : 500, Easing.Type.QUAD_OUT, false, false, null, 0));
            }
            case "slide_up" -> {
                return List.of(new Def(elementId, "y", -40, 0,
                        duration > 0 ? duration : 500, Easing.Type.QUAD_OUT, false, false, null, 0));
            }
            case "slide_down" -> {
                return List.of(new Def(elementId, "y", 40, 0,
                        duration > 0 ? duration : 500, Easing.Type.QUAD_OUT, false, false, null, 0));
            }
            case "fade_in" -> {
                return List.of(new Def(elementId, "opacity", 0, 1,
                        duration > 0 ? duration : 400, Easing.Type.QUAD_OUT, false, false, null, 0));
            }
            case "fade_out" -> {
                return List.of(new Def(elementId, "opacity", 1, 0,
                        duration > 0 ? duration : 400, Easing.Type.QUAD_IN, false, false, null, 0));
            }
            case "fade_in_up" -> {
                // 上浮入场：透明度 + 位移组合
                int dur = duration > 0 ? duration : 500;
                return List.of(
                        new Def(elementId, "opacity", 0, 1, dur, Easing.Type.QUAD_OUT, false, false, null, 0),
                        new Def(elementId, "y", 20, 0, dur, Easing.Type.QUAD_OUT, false, false, null, 0));
            }
            case "fade_in_down" -> {
                int dur = duration > 0 ? duration : 500;
                return List.of(
                        new Def(elementId, "opacity", 0, 1, dur, Easing.Type.QUAD_OUT, false, false, null, 0),
                        new Def(elementId, "y", -20, 0, dur, Easing.Type.QUAD_OUT, false, false, null, 0));
            }
            case "fade_out_down" -> {
                int dur = duration > 0 ? duration : 400;
                return List.of(
                        new Def(elementId, "opacity", 1, 0, dur, Easing.Type.QUAD_IN, false, false, null, 0),
                        new Def(elementId, "y", 0, 20, dur, Easing.Type.QUAD_IN, false, false, null, 0));
            }
            case "zoom_in" -> {
                return List.of(new Def(elementId, "scale", 0.6, 1,
                        duration > 0 ? duration : 400, Easing.Type.QUAD_OUT, false, false, null, 0));
            }
            case "zoom_out" -> {
                int dur = duration > 0 ? duration : 400;
                return List.of(
                        new Def(elementId, "scale", 1, 0.5, dur, Easing.Type.QUAD_IN, false, false, null, 0),
                        new Def(elementId, "opacity", 1, 0, dur, Easing.Type.QUAD_IN, false, false, null, 0));
            }
            default -> {
                return List.of();
            }
        }
    }

    private static Def parseDef(String elementId, Map<?, ?> def, Map<String, Object> vars) {
        double[][] points = null;
        Object pointsRaw = def.get("points");
        if (pointsRaw instanceof List<?> list) {
            List<double[]> pts = new ArrayList<>();
            for (Object p : list) {
                if (p instanceof List<?> pair && pair.size() >= 2) {
                    pts.add(new double[]{animNum(pair.get(0), 0, vars), animNum(pair.get(1), 0, vars)});
                }
            }
            if (!pts.isEmpty()) {
                points = pts.toArray(new double[0][]);
            }
        }
        return new Def(elementId,
                str(def.get("property"), "y"),
                animNum(def.get("from"), 0, vars),
                animNum(def.get("to"), 0, vars),
                (int) num(def.get("duration"), 500),
                easing(str(def.get("easing"), "linear")),
                bool(def.get("loop"), false),
                bool(def.get("pingpong"), false),
                points,
                (int) num(def.get("delay"), 0));
    }

    /** 动画数值：数字直用；字符串按表达式求值（vars 作用域；失败按数字解析）。 */
    private static double animNum(Object v, double fallback, Map<String, Object> vars) {
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        if (v != null) {
            String s = String.valueOf(v).trim();
            if (s.matches("-?\\d+(\\.\\d+)?")) {
                return Double.parseDouble(s);
            }
            if (vars != null) {
                try {
                    com.opendreamcore.script.Scope scope = new com.opendreamcore.script.Scope();
                    vars.forEach(scope::assignVar);
                    Object r = com.opendreamcore.script.DreamLang.evaluate(s, scope);
                    if (r instanceof Number n) {
                        return n.doubleValue();
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return fallback;
    }

    /** 脚本触发命名动画（Screen.播放动画）；重触发时替换同属性旧动画（避免叠加）。 */
    public void play(String animationName) {
        List<Def> defs = namedAnimations.get(animationName);
        if (defs == null || defs.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Def def : defs) {
            List<State> list = triggeredStates.computeIfAbsent(def.elementId(), k -> new ArrayList<>());
            // 同元素同属性旧动画移除（特效重触发替换；不同属性并存如 fade_in_up 的 opacity+y）
            list.removeIf(s -> s.def().property().equals(def.property()));
            list.add(new State(def, now + def.delay()));
        }
    }

    /** 依次播放多个命名动画（前一个 duration 完播后播下一个）。 */
    public void playSequence(String... names) {
        long cursor = System.currentTimeMillis();
        for (String name : names) {
            List<Def> defs = namedAnimations.get(name);
            if (defs == null || defs.isEmpty()) {
                continue;
            }
            long start = cursor;
            long maxDur = 0;
            for (Def def : defs) {
                triggeredStates.computeIfAbsent(def.elementId(), k -> new ArrayList<>())
                        .add(new State(def, start + def.delay()));
                maxDur = Math.max(maxDur, (long) def.duration() + def.delay());
            }
            cursor += maxDur;
        }
    }

    /** 按压反馈（hit）：元素按下时启动 scale 回弹动画（按页面作用域隔离）。 */
    public void press(String elementId, String scope, double targetScale, int duration) {
        String k = key(elementId, scope);
        Def def = new Def(elementId, "scale", 1, targetScale, duration, Easing.Type.QUAD_OUT, false, false, null, 0);
        // 回弹：先压到 targetScale 再回 1（用两段，第二段延迟 duration）
        triggeredStates.computeIfAbsent(k, kk -> new ArrayList<>())
                .add(new State(def, System.currentTimeMillis()));
        Def back = new Def(elementId, "scale", targetScale, 1, duration, Easing.Type.QUAD_IN_OUT, false, false, null, duration);
        triggeredStates.computeIfAbsent(k, kk -> new ArrayList<>())
                .add(new State(back, System.currentTimeMillis()));
    }

    /** 暂停指定元素动画（进度冻结在暂停时刻；所有作用域生效）。 */
    public void pause(String elementId) {
        pausedAt.putIfAbsent(elementId, System.currentTimeMillis());
    }

    /** 恢复暂停的动画（开始时间顺延暂停时长，进度继续走）。 */
    public void resume(String elementId) {
        Long paused = pausedAt.remove(elementId);
        if (paused == null) {
            return;
        }
        long delta = System.currentTimeMillis() - paused;
        shiftStates(autoStates.get(key(elementId, null)), delta);
        shiftStates(triggeredStates.get(key(elementId, null)), delta);
        // 所有作用域都顺延
        for (Map.Entry<String, List<State>> e : autoStates.entrySet()) {
            if (e.getKey().endsWith("\u0001" + elementId)) {
                shiftStates(e.getValue(), delta);
            }
        }
        for (Map.Entry<String, List<State>> e : triggeredStates.entrySet()) {
            if (e.getKey().endsWith("\u0001" + elementId)) {
                shiftStates(e.getValue(), delta);
            }
        }
    }

    private static void shiftStates(List<State> states, long delta) {
        if (states == null) {
            return;
        }
        // State 不可变，重建列表
        List<State> rebuilt = new ArrayList<>();
        for (State s : states) {
            rebuilt.add(new State(s.def(), s.startMs() + delta));
        }
        states.clear();
        states.addAll(rebuilt);
    }

    /** 停止指定元素的全部动画（null = 全部；所有作用域生效）。 */
    public void stop(String elementId) {
        if (elementId == null) {
            autoStates.clear();
            triggeredStates.clear();
            pausedAt.clear();
            return;
        }
        autoStates.remove(key(elementId, null));
        triggeredStates.remove(key(elementId, null));
        autoStates.entrySet().removeIf(e -> e.getKey().endsWith("\u0001" + elementId));
        triggeredStates.entrySet().removeIf(e -> e.getKey().endsWith("\u0001" + elementId));
        pausedAt.remove(elementId);
    }

    /** 当前动画偏移：{dx, dy, scale, alpha, rotationDeg}；无动画返回 null。 */
    public double[] offset(String elementId) {
        return offset(elementId, null);
    }

    public double[] offset(String elementId, String scope) {
        double[] a = new double[]{0, 0, 1, 1, 0};
        // 暂停：进度按暂停时刻冻结
        Long paused = pausedAt.get(elementId);
        long freezeAt = paused == null ? Long.MAX_VALUE : paused;
        boolean any = accumulate(autoStates.get(key(elementId, scope)), a, freezeAt)
                | accumulate(triggeredStates.get(key(elementId, scope)), a, freezeAt);
        return any ? a : null;
    }

    private boolean accumulate(List<State> list, double[] acc, long freezeAt) {
        if (list == null || list.isEmpty()) {
            return false;
        }
        boolean any = false;
        for (State state : list) {
            Def def = state.def();
            long now = Math.min(System.currentTimeMillis(), freezeAt);
            double p = (now - state.startMs()) / (double) Math.max(def.duration(), 1);
            if (def.loop()) {
                p = p % 1.0;
            }
            if (p >= 1) {
                if (!def.loop()) {
                    continue; // 播完不参与
                }
                p = p - Math.floor(p);
            }
            if (def.pingpong()) {
                p = (p % 2.0);
                if (p > 1) {
                    p = 2 - p; // 往返：0→1→0
                }
            }
            double progress = Easing.apply(def.easing(), Math.max(0, Math.min(1, p)));
            if (def.points() != null && def.points().length > 1) {
                // 路径动画：分段线性插值
                double[] pos = pathPoint(def.points(), progress);
                acc[0] += pos[0];
                acc[1] += pos[1];
                any = true;
                continue;
            }
            double value = def.from() + (def.to() - def.from()) * progress;
            switch (def.property()) {
                case "x" -> {
                    acc[0] += value;
                    any = true;
                }
                case "y" -> {
                    acc[1] += value;
                    any = true;
                }
                case "scale" -> {
                    acc[2] *= value;
                    any = true;
                }
                case "opacity" -> {
                    acc[3] *= value;
                    any = true;
                }
                case "rotation" -> {
                    acc[4] += value;
                    any = true;
                }
                default -> {
                }
            }
        }
        return any;
    }

    /** 路径点分段线性插值（progress 0→1）。 */
    private static double[] pathPoint(double[][] points, double progress) {
        int segs = points.length - 1;
        double scaled = Math.max(0, Math.min(1, progress)) * segs;
        int idx = Math.min(segs - 1, (int) scaled);
        double t = scaled - idx;
        double[] a = points[idx];
        double[] b = points[idx + 1];
        return new double[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t};
    }

    /** 页面重开时清空动画状态。 */
    public void reset() {
        autoStates.clear();
        triggeredStates.clear();
        namedAnimations.clear();
        seen.clear();
        namedSeen.clear();
        pausedAt.clear();
    }

    private static String str(Object v, String fallback) {
        return v == null ? fallback : String.valueOf(v);
    }

    private static double num(Object v, double fallback) {
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        if (v != null) {
            try {
                return Double.parseDouble(String.valueOf(v));
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private static boolean bool(Object v, boolean fallback) {
        if (v instanceof Boolean b) {
            return b;
        }
        return v != null && Boolean.parseBoolean(String.valueOf(v));
    }

    private static Easing.Type easing(String name) {
        try {
            return Easing.Type.valueOf(name.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Easing.Type.LINEAR;
        }
    }
}
