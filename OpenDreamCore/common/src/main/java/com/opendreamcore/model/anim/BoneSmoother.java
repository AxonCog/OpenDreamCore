package com.opendreamcore.model.anim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 骨骼弹簧趋近器（渲染层丝滑的核心）：目标姿态不直接上屏，先经弹簧追帧。
 *
 *  - 半隐式欧拉：vel += (target-cur)·k·dt；vel 阻尼；cur += vel·dt；
 *    k 由 smooth 系数换算（smooth∈(0,1]，越大越快，0=直通不建任何状态）；
 *  - 到位检测：位置差与速度同时小于 eps 时落定（cur=target, vel=0），
 *    落定后的帧不再积分，静态实体零成本；
 *  - 姿态与速度对象内部复用（骨骼集合稳定时不分配新对象）；
 *  - 纯 Java 8、无 MC 依赖；每实体一个实例，渲染线程独占。
 */
public final class BoneSmoother {

    /** 落定阈值（角度/位移/缩放共用量纲级 eps）。 */
    private static final double SETTLE_EPS = 0.005;
    /** 速度落定阈值。 */
    private static final double VEL_EPS = 0.02;
    /** spring 刚度上界（smooth=1 时每秒收敛率对应值）。 */
    private static final double K_MAX = 260.0;

    private double smooth;
    private final Map<String, AnimEval.Pose> cur = new HashMap<String, AnimEval.Pose>();
    private final Map<String, AnimEval.Pose> vel = new HashMap<String, AnimEval.Pose>();
    private final List<String> scratch = new ArrayList<String>();

    public BoneSmoother(double smooth) {
        this.smooth = clamp(smooth);
    }

    public void setSmooth(double s) {
        this.smooth = clamp(s);
    }

    public double smooth() {
        return smooth;
    }

    private static double clamp(double s) {
        if (s < 0) {
            return 0;
        }
        return s > 1 ? 1 : s;
    }

    /** 直通模式（smooth=0）。 */
    public boolean passthrough() {
        return smooth <= 0;
    }

    /**
     * 推进一帧：target 为本帧目标姿态表，结果写入 out（out 对象复用调用方的池）。
     * dt 为帧间隔（秒），越界自动限幅（负数按 0，超 0.25 按 0.25——与 AnimationClock 同口径）。
     */
    public void step(Map<String, AnimEval.Pose> target, double dt, Map<String, AnimEval.Pose> out) {
        if (dt < 0) {
            dt = 0;
        }
        if (dt > AnimationClock.MAX_DELTA) {
            dt = AnimationClock.MAX_DELTA;
        }
        if (passthrough()) {
            copyAll(target, cur);
            copyAll(target, out);
            return;
        }
        double k = K_MAX * smooth * smooth + 8.0 * smooth; // smooth 0.35 → k≈59（手感调优值）
        scratch.clear();
        scratch.addAll(target.keySet());
        // 消亡骨骼：上帧有、本帧无 → 直接并入 out（自然收敛到静止）
        for (String bone : cur.keySet()) {
            if (!target.containsKey(bone)) {
                scratch.add(bone);
            }
        }
        for (String bone : scratch) {
            AnimEval.Pose t = target.get(bone);
            AnimEval.Pose c = cur.get(bone);
            if (c == null) {
                c = new AnimEval.Pose(); // hot-ok：每骨骼生命周期一次，进 map 复用
                cur.put(bone, c);
            }
            AnimEval.Pose v = vel.get(bone);
            if (v == null) {
                v = new AnimEval.Pose(); // hot-ok：每骨骼生命周期一次，进 map 复用
                vel.put(bone, v);
            }
            AnimEval.Pose o = out.get(bone);
            if (o == null) {
                o = new AnimEval.Pose(); // hot-ok：每骨骼生命周期一次，进 map 复用
                out.put(bone, o);
            }
            if (t == null) {
                // 骨骼消亡：目标回零位（rot0/pos0/scale1），继续弹簧收敛
                t = NEUTRAL;
            }
            integrate(t, c, v, k, dt);
            if (settled(t, c, v)) {
                settle(t, c, v);
            }
            copy(c, o);
        }
    }

    /** 是否已产出过姿态（首个动画帧之前没有可快照的历史）。 */
    public boolean hasOutput() {
        return !cur.isEmpty();
    }

    /** 抄一份当前内部姿态（过渡快照用，目标表复用调用方的池）。 */
    public void snapshot(Map<String, AnimEval.Pose> out) {
        for (Map.Entry<String, AnimEval.Pose> e : cur.entrySet()) {
            AnimEval.Pose o = out.get(e.getKey());
            if (o == null) {
                o = new AnimEval.Pose(); // hot-ok：每骨骼生命周期一次，进 map 复用
                out.put(e.getKey(), o);
            }
            copy(e.getValue(), o);
        }
    }

    private static final AnimEval.Pose NEUTRAL = new AnimEval.Pose();

    private static void integrate(AnimEval.Pose t, AnimEval.Pose c, AnimEval.Pose v,
                                  double k, double dt) {
        spring(t.rot, c.rot, v.rot, k, dt);
        spring(t.pos, c.pos, v.pos, k, dt);
        spring(t.scale, c.scale, v.scale, k, dt);
    }

    private static void spring(double[] target, double[] value, double[] vel, double k, double dt) {
        for (int i = 0; i < 3; i++) {
            vel[i] += (target[i] - value[i]) * k * dt;
            vel[i] *= 1.0 - Math.min(0.9, 12.0 * dt); // 阻尼随 dt 缩放（帧率无关的近似）
            value[i] += vel[i] * dt;
        }
    }

    private static boolean settled(AnimEval.Pose t, AnimEval.Pose c, AnimEval.Pose v) {
        return settled(t.rot, c.rot, v.rot) && settled(t.pos, c.pos, v.pos)
                && settled(t.scale, c.scale, v.scale);
    }

    private static boolean settled(double[] t, double[] c, double[] v) {
        for (int i = 0; i < 3; i++) {
            if (Math.abs(t[i] - c[i]) > SETTLE_EPS || Math.abs(v[i]) > VEL_EPS) {
                return false;
            }
        }
        return true;
    }

    private static void settle(AnimEval.Pose t, AnimEval.Pose c, AnimEval.Pose v) {
        System.arraycopy(t.rot, 0, c.rot, 0, 3);
        System.arraycopy(t.pos, 0, c.pos, 0, 3);
        System.arraycopy(t.scale, 0, c.scale, 0, 3);
        zero(v.rot);
        zero(v.pos);
        zero(v.scale);
    }

    private static void zero(double[] a) {
        a[0] = a[1] = a[2] = 0;
    }

    private static void copy(AnimEval.Pose from, AnimEval.Pose to) {
        System.arraycopy(from.rot, 0, to.rot, 0, 3);
        System.arraycopy(from.pos, 0, to.pos, 0, 3);
        System.arraycopy(from.scale, 0, to.scale, 0, 3);
    }

    private static void copyAll(Map<String, AnimEval.Pose> from, Map<String, AnimEval.Pose> to) {
        to.clear();
        for (Map.Entry<String, AnimEval.Pose> e : from.entrySet()) {
            AnimEval.Pose p = new AnimEval.Pose(); // hot-ok：每骨骼生命周期一次，进 map 复用
            copy(e.getValue(), p);
            to.put(e.getKey(), p);
        }
    }

    /** 是否全部骨骼落定（外层可用于跳过重绘等优化）。 */
    public boolean allSettled(Map<String, AnimEval.Pose> target) {
        if (passthrough()) {
            return true;
        }
        for (Map.Entry<String, AnimEval.Pose> e : target.entrySet()) {
            AnimEval.Pose c = cur.get(e.getKey());
            AnimEval.Pose v = vel.get(e.getKey());
            if (c == null || v == null || !settled(e.getValue(), c, v)) {
                return false;
            }
        }
        return true;
    }
}
