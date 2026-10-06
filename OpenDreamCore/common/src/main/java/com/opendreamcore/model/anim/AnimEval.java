package com.opendreamcore.model.anim;

import com.opendreamcore.model.ir.AnimationIR;
import com.opendreamcore.model.molang.MolangEval;

/**
 * 动画采样（三段分离之一：表达式/插值求值）。
 *
 * 时间语义：loop 动画按 length 取模回绕；非 loop 夹在 [0, length]。
 * 关键帧插值（目标关键帧的 interp 决定到达方式）：
 *  - step：保持前一关键帧值（表达式关键帧按前一帧表达式求值）；
 *  - linear：逐分量线性（欧拉角按普通 lerp，不绕最短角）；
 *  - bezier：三次贝塞尔 P0=前帧值，P1=前帧 bezierOut（空=终点），P2=本帧 bezierIn（空=终点），P3=本帧值；
 *  - 其他（smooth/catmullrom 等）回退 linear（molang_strict=false 的同款宽容策略）。
 * molang：query.anim_time 由本类注入（=回绕后的采样时间），未知变量回 0，求值失败回 0。
 */
public final class AnimEval {

    /** 共享宽容求值器（molang_strict=false：未知函数/变量回 0）。 */
    static final MolangEval MOLANG = new MolangEval(false);

    /** 单骨骼姿态（rot 单位度，scale 缺省 1）。 */
    public static final class Pose {
        public final double[] rot = new double[3];
        public final double[] pos = new double[3];
        public final double[] scale = {1, 1, 1};

        public void reset() {
            rot[0] = rot[1] = rot[2] = 0;
            pos[0] = pos[1] = pos[2] = 0;
            scale[0] = scale[1] = scale[2] = 1;
        }
    }

    private AnimEval() {
    }

    /** 采样整只动画：骨骼名 → 姿态写入 out（out 会先清空再逐骨骼填）。 */
    public static void sample(AnimationIR anim, double time, MolangEval.Variables vars,
                              java.util.Map<String, Pose> out) {
        out.clear();
        if (anim == null) {
            return;
        }
        final double eff = effectiveTime(anim, time);
        MolangEval.Variables v = wrapAnimTime(vars, eff);
        for (java.util.Map.Entry<String, AnimationIR.BoneAnim> e : anim.bones.entrySet()) {
            AnimationIR.BoneAnim ba = e.getValue();
            if (ba == null || (ba.pos == null && ba.rot == null && ba.scale == null)) {
                continue;
            }
            Pose p = new Pose();
            if (ba.rot != null) {
                double[] r = sampleTrack(ba.rot, eff, v);
                p.rot[0] = r[0];
                p.rot[1] = r[1];
                p.rot[2] = r[2];
            }
            if (ba.pos != null) {
                double[] t = sampleTrack(ba.pos, eff, v);
                p.pos[0] = t[0];
                p.pos[1] = t[1];
                p.pos[2] = t[2];
            }
            if (ba.scale != null) {
                double[] s = sampleTrack(ba.scale, eff, v);
                p.scale[0] = s[0];
                p.scale[1] = s[1];
                p.scale[2] = s[2];
            }
            out.put(e.getKey(), p);
        }
    }

    /** loop 回绕 / 非 loop 夹取。 */
    public static double effectiveTime(AnimationIR anim, double time) {
        if (anim.length <= 0) {
            return Math.max(0, time);
        }
        if (anim.loop) {
            double t = time % anim.length;
            return t < 0 ? t + anim.length : t;
        }
        return Math.min(Math.max(time, 0), anim.length);
    }

    /** 三分量采样；无关键帧返回全 0（scale 通道调用方自行按 1 起步——这里 rot/pos/scale 统一 0，scale 由 sample 处理）。 */
    static double[] sampleTrack(AnimationIR.Track track, double t, MolangEval.Variables vars) {
        java.util.List<AnimationIR.Keyframe> keys = track.keys;
        if (keys.isEmpty()) {
            return new double[3];
        }
        AnimationIR.Keyframe first = keys.get(0);
        AnimationIR.Keyframe last = keys.get(keys.size() - 1);
        if (t <= first.time) {
            return val(first, vars);
        }
        if (t >= last.time) {
            return val(last, vars);
        }
        for (int i = 0; i < keys.size() - 1; i++) {
            AnimationIR.Keyframe a = keys.get(i);
            AnimationIR.Keyframe b = keys.get(i + 1);
            if (t >= a.time && t <= b.time) {
                double span = b.time - a.time;
                double u = span <= 0 ? 1 : (t - a.time) / span;
                String interp = b.interp == null ? "linear" : b.interp;
                if ("step".equals(interp)) {
                    return val(a, vars);
                }
                if ("bezier".equals(interp)) {
                    return bezier(a, b, u, vars);
                }
                return lerp3(val(a, vars), val(b, vars), u);
            }
        }
        return val(last, vars);
    }

    /** 逐分量线性。 */
    static double[] lerp3(double[] a, double[] b, double u) {
        return new double[]{
                a[0] + (b[0] - a[0]) * u,
                a[1] + (b[1] - a[1]) * u,
                a[2] + (b[2] - a[2]) * u};
    }

    /** 三次贝塞尔（控制点绝对坐标；空控制点回退端点 → 退化为 linear）。 */
    static double[] bezier(AnimationIR.Keyframe a, AnimationIR.Keyframe b, double u,
                           MolangEval.Variables vars) {
        double[] p0 = val(a, vars);
        double[] p3 = val(b, vars);
        double[] p1 = a.bezierOut != null ? a.bezierOut : p0;
        double[] p2 = b.bezierIn != null ? b.bezierIn : p3;
        double[] out = new double[3];
        for (int i = 0; i < 3; i++) {
            double iu = 1 - u;
            out[i] = iu * iu * iu * p0[i] + 3 * iu * iu * u * p1[i]
                    + 3 * iu * u * u * p2[i] + u * u * u * p3[i];
        }
        return out;
    }

    /** 关键帧分量值：表达式优先（失败回 0，同 molang 宽容语义）。 */
    static double[] val(AnimationIR.Keyframe kf, MolangEval.Variables vars) {
        double[] out = new double[3];
        for (int i = 0; i < 3; i++) {
            String expr = kf.expr[i];
            if (expr != null && !expr.isEmpty()) {
                out[i] = MOLANG.eval(expr, vars);
            } else {
                out[i] = kf.value[i];
            }
        }
        return out;
    }

    /** 注入 query.anim_time（调用方同名变量被采样时间覆盖）。 */
    static MolangEval.Variables wrapAnimTime(final MolangEval.Variables vars, final double animTime) {
        return new MolangEval.Variables() {
            @Override
            public double get(String name) {
                if ("query.anim_time".equals(name)) {
                    return animTime;
                }
                return vars == null ? 0 : vars.get(name);
            }
        };
    }
}
