package com.opendreamcore.client.camera;

import com.opendreamcore.camera.CameraRule;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * M1.9 路径数据模型：点/视角/显式时间/缓动/时长/循环/插值 + 撤销栈 + 规则表互转。
 *
 * <p>纯数据与数学，不碰 MC——玩家取点、消息文案、预览/落盘编排都在
 * {@link CameraEditSession} 门面（反臃肿 R1：单类 ≤500 行）。
 * 规则表键名与 {@code CameraRule.parse} 一一对应（type/keyframes[t,pos,rot,ease]/duration/loop/smooth）。
 */
final class CameraPathModel {

    private final List<double[]> points = new ArrayList<>();
    private final List<double[]> rots = new ArrayList<>();
    private final List<Double> times = new ArrayList<>();
    private final List<String> eases = new ArrayList<>();
    private double duration;
    private boolean loop;
    private boolean smooth = true;
    private final Undo undo = new Undo();

    // ---------------------------------------------------------------- 装载

    void reset() {
        points.clear();
        rots.clear();
        times.clear();
        eases.clear();
        duration = 0;
        loop = false;
        smooth = true;
        undo.clear();
    }

    /** 从既有 PATH 规则装载（继续编辑）。 */
    void loadRule(CameraRule old) {
        points.clear();
        rots.clear();
        times.clear();
        eases.clear();
        for (CameraRule.Keyframe k : old.keyframes) {
            if (k.pos == null) {
                continue;
            }
            points.add(new double[]{k.pos[0], k.pos[1], k.pos[2]});
            rots.add(k.rot == null ? null : new double[]{k.rot[0], k.rot[1]});
            times.add(k.t);
            eases.add(k.ease);
        }
        duration = old.duration;
        loop = old.loop;
        smooth = old.smooth;
    }

    /** 从解析好的导入数据装载（importYaml 用）。 */
    void load(PathData d) {
        points.clear();
        points.addAll(d.points);
        rots.clear();
        rots.addAll(d.rots);
        times.clear();
        times.addAll(d.times);
        eases.clear();
        eases.addAll(d.eases);
        duration = d.duration;
        loop = d.loop;
        smooth = d.smooth;
    }

    // ---------------------------------------------------------------- 编辑操作

    /** 记点（位置 + 视角，可空视角）。 */
    void addPoint(double[] pos, double[] rot) {
        pushUndo();
        points.add(pos.clone());
        rots.add(rot == null ? null : rot.clone());
        times.add(null);
        eases.add(null);
    }

    /** 插入到 0 基下标（后点顺延）。 */
    void insertAt(int i, double[] pos, double[] rot) {
        pushUndo();
        points.add(i, pos.clone());
        rots.add(i, rot == null ? null : rot.clone());
        times.add(i, null);
        eases.add(i, null);
    }

    /** 删除 0 基下标。 */
    void removeAt(int i) {
        pushUndo();
        points.remove(i);
        rots.remove(i);
        times.remove(i);
        eases.remove(i);
    }

    void clearPoints() {
        pushUndo();
        points.clear();
        rots.clear();
        times.clear();
        eases.clear();
    }

    /** 解析对齐目标 Y（first=第 1 点 / avg=平均 / 数字）；非法或点数不足返回 null。 */
    Double resolveAlign(String mode) {
        if (points.size() < 2 || mode == null) {
            return null;
        }
        String m = mode.trim();
        if ("first".equalsIgnoreCase(m)) {
            return points.get(0)[1];
        }
        if ("avg".equalsIgnoreCase(m)) {
            double sum = 0;
            for (double[] p : points) {
                sum += p[1];
            }
            return sum / points.size();
        }
        try {
            return Double.parseDouble(m);
        } catch (Exception e) {
            return null;
        }
    }

    /** 对齐到指定 Y（压一次撤销栈）。 */
    void alignTo(double y) {
        pushUndo();
        for (double[] p : points) {
            p[1] = y;
        }
    }

    /** 镜像：mx=沿 X 轴翻（ax 为轴），否则沿 Z（az 为轴）；压一次撤销栈。 */
    void mirror(boolean mx, double ax, double az) {
        pushUndo();
        for (double[] p : points) {
            if (mx) {
                p[0] = 2 * ax - p[0];
            } else {
                p[2] = 2 * az - p[2];
            }
        }
    }

    /** 拖拽用：直接改第 i 个点（0 基，压栈由拖拽方在按下时做）。 */
    void setPoint(int i, double x, double y, double z) {
        double[] p = points.get(i);
        p[0] = x;
        p[1] = y;
        p[2] = z;
    }

    /** 改第 i 个点（0 基）的显式时间；自动档改手动锁值。 */
    void setKeyframeTime(int i, double t) {
        if (i >= 0 && i < points.size()) {
            pushUndo();
            padTimes();
            times.set(i, Math.max(0, round3(t)));
        }
    }

    /** 拖拽中连续改时间（撤销栈由拖拽开始的那次 pushUndo 负责）。 */
    void dragKeyframeTime(int i, double t) {
        if (i >= 0 && i < points.size()) {
            padTimes();
            times.set(i, Math.max(0, round3(t)));
        }
    }

    /** 右键循环缓动：跟随 → smooth → linear → held → 跟随；返回新值。 */
    String cycleEase(int i) {
        if (i < 0 || i >= points.size()) {
            return null;
        }
        pushUndo();
        while (eases.size() < points.size()) {
            eases.add(null);
        }
        String cur = eases.get(i);
        String next = cur == null ? "smooth"
                : "smooth".equals(cur) ? "linear" : "linear".equals(cur) ? "held" : null;
        eases.set(i, next);
        return next;
    }

    void setDuration(double seconds) {
        pushUndo();
        duration = Math.max(0, seconds);
    }

    void setLoop(boolean on) {
        pushUndo();
        loop = on;
    }

    void setSmooth(boolean on) {
        pushUndo();
        smooth = on;
    }

    // ---------------------------------------------------------------- 规则表互转

    /** 组装规则表（与 {@code CameraRule.parse} 键名一一对应）。 */
    Map<String, Object> toRuleMap() {
        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("type", "path");
        int n = points.size();
        List<Object> frames = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Map<String, Object> k = new LinkedHashMap<>();
            Double explicit = i < times.size() ? times.get(i) : null;
            double t = explicit != null ? explicit
                    : (n <= 1 ? 0 : (duration > 0 ? duration * i / (n - 1) : i * 1.0));
            k.put("t", round3(t));
            double[] p = points.get(i);
            k.put("pos", new Object[]{round3(p[0]), round3(p[1]), round3(p[2])});
            double[] r = rots.get(i);
            if (r != null) {
                k.put("rot", new Object[]{round3(r[0]), round3(r[1])});
            }
            String ease = i < eases.size() ? eases.get(i) : null;
            if (ease != null) {
                k.put("ease", ease);
            }
            frames.add(k);
        }
        rule.put("keyframes", frames);
        if (duration > 0) {
            rule.put("duration", round3(duration));
        }
        if (loop) {
            rule.put("loop", true);
        }
        rule.put("smooth", smooth);
        rule.put("transition", new Object[]{10, 10});
        return rule;
    }

    /** 解析规则表（兼容 {id:规则} 单键包裹）；有效关键帧 &lt;2 返回 null。 */
    @SuppressWarnings("unchecked")
    static PathData parseRuleMap(Map<String, Object> mIn) {
        Map<String, Object> m = mIn;
        if (!(m.get("keyframes") instanceof List) && m.size() == 1) {
            Object only = m.values().iterator().next();
            if (only instanceof Map) {
                m = (Map<String, Object>) only;
            }
        }
        List<double[]> np = new ArrayList<>();
        List<double[]> nr = new ArrayList<>();
        List<Double> nt = new ArrayList<>();
        List<String> ne = new ArrayList<>();
        Object kf = m.get("keyframes");
        if (kf instanceof List) {
            for (Object e : (List<?>) kf) {
                if (!(e instanceof Map)) {
                    continue;
                }
                Map<String, Object> k = (Map<String, Object>) e;
                Object posO = k.get("pos");
                if (!(posO instanceof List) || ((List<?>) posO).size() < 3) {
                    continue;
                }
                List<?> pl = (List<?>) posO;
                np.add(new double[]{num(pl.get(0)), num(pl.get(1)), num(pl.get(2))});
                Object rotO = k.get("rot");
                if (rotO instanceof List && ((List<?>) rotO).size() >= 2) {
                    List<?> rl = (List<?>) rotO;
                    nr.add(new double[]{num(rl.get(0)), num(rl.get(1))});
                } else {
                    nr.add(null);
                }
                nt.add(k.get("t") instanceof Number tn ? tn.doubleValue() : null);
                Object eo = k.get("ease");
                ne.add(eo instanceof String es && !es.isEmpty()
                        ? es.toLowerCase(Locale.ROOT) : null);
            }
        }
        if (np.size() < 2) {
            return null;
        }
        double dur = m.get("duration") instanceof Number dn ? dn.doubleValue() : 0;
        boolean lp = Boolean.TRUE.equals(m.get("loop")) || "true".equals(String.valueOf(m.get("loop")));
        boolean sm = !"false".equals(String.valueOf(m.get("smooth")));
        return new PathData(np, nr, nt, ne, dur, lp, sm);
    }

    /** 路径数据快照（points/rots/times/eases 等长；后三列元素可空）。 */
    static final class PathData {
        final List<double[]> points;
        final List<double[]> rots;
        final List<Double> times;
        final List<String> eases;
        final double duration;
        final boolean loop;
        final boolean smooth;

        PathData(List<double[]> points, List<double[]> rots, List<Double> times,
                 List<String> eases, double duration, boolean loop, boolean smooth) {
            this.points = points;
            this.rots = rots;
            this.times = times;
            this.eases = eases;
            this.duration = duration;
            this.loop = loop;
            this.smooth = smooth;
        }
    }

    // ---------------------------------------------------------------- 读视图

    int pointsCount() {
        return points.size();
    }

    double[] pointAt(int i) {
        double[] p = points.get(i);
        return new double[]{p[0], p[1], p[2]};
    }

    List<double[]> pointsView() {
        return java.util.Collections.unmodifiableList(points);
    }

    /** 第 i 个点（1 基序号）的视角；越界返回 null。 */
    double[] rotOf(int i) {
        return i >= 1 && i <= rots.size() ? rots.get(i - 1) : null;
    }

    List<Double> timesView() {
        return java.util.Collections.unmodifiableList(times);
    }

    List<String> easesView() {
        return java.util.Collections.unmodifiableList(eases);
    }

    double durationValue() {
        return duration;
    }

    boolean loopEnabled() {
        return loop;
    }

    boolean smoothInterp() {
        return smooth;
    }

    // ---------------------------------------------------------------- 撤销/重做

    /** 任何改变点列的操作前调用。 */
    void pushUndo() {
        undo.push(points, rots, times, eases);
    }

    boolean undo() {
        Snap s = undo.popUndo(points, rots, times, eases);
        if (s == null) {
            return false;
        }
        apply(s);
        return true;
    }

    boolean redo() {
        Snap s = undo.popRedo(points, rots, times, eases);
        if (s == null) {
            return false;
        }
        apply(s);
        return true;
    }

    int undoDepth() {
        return undo.depth();
    }

    private void apply(Snap s) {
        points.clear();
        points.addAll(s.toPts());
        rots.clear();
        rots.addAll(s.toRts());
        times.clear();
        times.addAll(s.toTms());
        eases.clear();
        eases.addAll(s.toEas());
    }

    private void padTimes() {
        while (times.size() < points.size()) {
            times.add(null);
        }
    }

    private static double num(Object o) {
        return o instanceof Number nn ? nn.doubleValue() : 0;
    }

    private static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    /** 快照：点列 + 视角列（null 视角记 NaN）+ 显式时间 + 缓动。 */
    private static final class Snap {
        final double[][] pts;
        final double[][] rts;
        final Double[] tms;
        final String[] eas;

        Snap(List<double[]> pts, List<double[]> rts, List<Double> tms, List<String> eas) {
            this.pts = new double[pts.size()][];
            for (int i = 0; i < pts.size(); i++) {
                this.pts[i] = pts.get(i).clone();
            }
            this.rts = new double[rts.size()][];
            for (int i = 0; i < rts.size(); i++) {
                double[] r = rts.get(i);
                this.rts[i] = r == null ? new double[]{Double.NaN, Double.NaN} : r.clone();
            }
            this.tms = tms.toArray(new Double[0]);
            this.eas = eas.toArray(new String[0]);
        }

        List<double[]> toPts() {
            List<double[]> out = new ArrayList<>(pts.length);
            for (double[] p : pts) {
                out.add(p.clone());
            }
            return out;
        }

        List<double[]> toRts() {
            List<double[]> out = new ArrayList<>(rts.length);
            for (double[] r : rts) {
                out.add(Double.isNaN(r[0]) ? null : r.clone());
            }
            return out;
        }

        List<Double> toTms() {
            return new ArrayList<>(Arrays.asList(tms));
        }

        List<String> toEas() {
            return new ArrayList<>(Arrays.asList(eas));
        }
    }

    /** 撤销/重做双栈（上限 64 步；新压栈清空重做栈）。 */
    private static final class Undo {
        private final ArrayDeque<Snap> undoStack = new ArrayDeque<>();
        private final ArrayDeque<Snap> redoStack = new ArrayDeque<>();

        void push(List<double[]> pts, List<double[]> rts, List<Double> tms, List<String> eas) {
            undoStack.push(new Snap(pts, rts, tms, eas));
            if (undoStack.size() > 64) {
                undoStack.removeLast();
            }
            redoStack.clear();
        }

        /** 撤销：当前态入重做栈，返回被恢复的快照；空返回 null。 */
        Snap popUndo(List<double[]> pts, List<double[]> rts, List<Double> tms, List<String> eas) {
            if (undoStack.isEmpty()) {
                return null;
            }
            redoStack.push(new Snap(pts, rts, tms, eas));
            return undoStack.pop();
        }

        /** 重做：当前态入撤销栈，返回被恢复的快照；空返回 null。 */
        Snap popRedo(List<double[]> pts, List<double[]> rts, List<Double> tms, List<String> eas) {
            if (redoStack.isEmpty()) {
                return null;
            }
            undoStack.push(new Snap(pts, rts, tms, eas));
            return redoStack.pop();
        }

        void clear() {
            undoStack.clear();
            redoStack.clear();
        }

        int depth() {
            return undoStack.size();
        }
    }
}
