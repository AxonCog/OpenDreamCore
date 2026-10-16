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

    /** 采样整只动画：骨骼名 → 姿态写入 out（复用 out 里已有 Pose；骨骼消失时回收键）。 */
    public static void sample(AnimationIR anim, double time, MolangEval.Variables vars,
                              java.util.Map<String, Pose> out) {
        if (anim == null) {
            out.clear();
            return;
        }
        Workspace ws = workspace();
        ws.timeVars.bind(vars, effectiveTime(anim, time));
        try {
            final double eff = ws.timeVars.time;
            for (java.util.Map.Entry<String, AnimationIR.BoneAnim> e : anim.bones.entrySet()) {
                AnimationIR.BoneAnim ba = e.getValue();
                if (ba == null || (isEmpty(ba.pos) && isEmpty(ba.rot) && isEmpty(ba.scale))) {
                    continue;
                }
                Pose p = out.get(e.getKey());
                if (p == null) {
                    p = new Pose();
                    out.put(e.getKey(), p);
                }
                p.reset();
                if (!isEmpty(ba.rot)) {
                    double[] r = sampleTrack(ba.rot, eff, ws.timeVars, ws);
                    p.rot[0] = r[0]; p.rot[1] = r[1]; p.rot[2] = r[2];
                }
                if (!isEmpty(ba.pos)) {
                    double[] v = sampleTrack(ba.pos, eff, ws.timeVars, ws);
                    p.pos[0] = v[0]; p.pos[1] = v[1]; p.pos[2] = v[2];
                }
                if (!isEmpty(ba.scale)) {
                    double[] s = sampleTrack(ba.scale, eff, ws.timeVars, ws);
                    p.scale[0] = s[0]; p.scale[1] = s[1]; p.scale[2] = s[2];
                }
            }
            out.keySet().retainAll(anim.bones.keySet());
            java.util.Iterator<java.util.Map.Entry<String, Pose>> it = out.entrySet().iterator();
            while (it.hasNext()) {
                AnimationIR.BoneAnim ba = anim.bones.get(it.next().getKey());
                if (ba == null || (isEmpty(ba.pos) && isEmpty(ba.rot) && isEmpty(ba.scale))) {
                    it.remove();
                }
            }
        } finally {
            releaseWorkspace(ws);
        }
    }

    private static boolean isEmpty(AnimationIR.Track track) {
        return track == null || track.keys.isEmpty();
    }

    /** 时间语义：once/default 夹在 [0,length]；loop 回绕；hold 播完后停在末帧；reverse 先翻转时间轴。 */
    public static double effectiveTime(AnimationIR anim, double time) {
        if (anim.length <= 0) {
            return Math.max(0, time);
        }
        double t;
        String mode = anim.loopMode;
        if (AnimationIR.LOOP_LOOP.equals(mode)) {
            t = time % anim.length;
            if (t < 0) {
                t += anim.length;
            }
        } else {
            t = Math.min(Math.max(time, 0), anim.length);
        }
        if (anim.reverse && anim.length > 0) {
            t = anim.length - t;
        }
        return t;
    }

    /** 每线程工作区：避免静态共享数组在并发/嵌套求值时互相覆盖。 */
    private static final ThreadLocal<Workspace> WORKSPACE = new ThreadLocal<Workspace>();

    static double[] sampleTrack(AnimationIR.Track track, double t, MolangEval.Variables vars) {
        Workspace ws = workspace();
        try {
            return sampleTrack(track, t, vars, ws);
        } finally {
            releaseWorkspace(ws);
        }
    }

    private static double[] sampleTrack(AnimationIR.Track track, double t,
                                        MolangEval.Variables vars, Workspace ws) {
        java.util.List<AnimationIR.Keyframe> keys = track.keys;
        if (keys.isEmpty()) {
            ws.out[0] = ws.out[1] = ws.out[2] = 0;
            return ws.out;
        }
        AnimationIR.Keyframe first = keys.get(0);
        AnimationIR.Keyframe last = keys.get(keys.size() - 1);
        if (t <= first.time) {
            return t < first.time ? preInto(first, vars, ws.out) : valInto(first, vars, ws.out);
        }
        if (t >= last.time) {
            return valInto(last, vars, ws.out);
        }
        for (int i = 0; i < keys.size() - 1; i++) {
            AnimationIR.Keyframe a = keys.get(i);
            AnimationIR.Keyframe b = keys.get(i + 1);
            if (t == b.time) {
                return valInto(b, vars, ws.out);
            }
            if (t >= a.time && t < b.time) {
                double span = b.time - a.time;
                double u = span <= 0 ? 1 : (t - a.time) / span;
                String interp = b.interp == null ? "linear" : b.interp;
                if ("step".equalsIgnoreCase(interp)) {
                    return valInto(a, vars, ws.out);
                }
                String easing = b.preValue != null ? b.preEasing : b.easing;
                double[] easingArgs = b.preValue != null ? b.preEasingArgs : b.easingArgs;
                if (easing != null && !easing.isEmpty()) {
                    u = Easing.apply(easing, u, easingArgs);
                }
                if ("bezier".equalsIgnoreCase(interp)) {
                    return bezierInto(a, b, u, vars, ws);
                }
                valInto(a, vars, ws.va);
                preInto(b, vars, ws.vb);
                if ("catmullrom".equalsIgnoreCase(interp)
                        || "catmull_rom".equalsIgnoreCase(interp)
                        || "smooth".equalsIgnoreCase(interp)) {
                    AnimationIR.Keyframe before = i > 0 ? keys.get(i - 1) : a;
                    AnimationIR.Keyframe after = i + 2 < keys.size() ? keys.get(i + 2) : b;
                    valInto(before, vars, ws.vc);
                    preInto(after, vars, ws.vd);
                    double u2 = u * u;
                    double u3 = u2 * u;
                    for (int k = 0; k < 3; k++) {
                        ws.out[k] = 0.5 * (2 * ws.va[k]
                                + (-ws.vc[k] + ws.vb[k]) * u
                                + (2 * ws.vc[k] - 5 * ws.va[k] + 4 * ws.vb[k] - ws.vd[k]) * u2
                                + (-ws.vc[k] + 3 * ws.va[k] - 3 * ws.vb[k] + ws.vd[k]) * u3);
                    }
                } else {
                    for (int k = 0; k < 3; k++) {
                        ws.out[k] = ws.va[k] + (ws.vb[k] - ws.va[k]) * u;
                    }
                }
                return ws.out;
            }
        }
        return valInto(last, vars, ws.out);
    }

    /** 三次贝塞尔写入工作区（控制点为绝对坐标）。 */
    private static double[] bezierInto(AnimationIR.Keyframe a, AnimationIR.Keyframe b,
                                       double u, MolangEval.Variables vars, Workspace ws) {
        valInto(a, vars, ws.va);
        preInto(b, vars, ws.vb);
        double[] p0 = ws.va;
        double[] p3 = ws.vb;
        double[] p1 = a.bezierOut != null ? a.bezierOut : p0;
        double[] p2 = b.bezierIn != null ? b.bezierIn : p3;
        double iu = 1 - u;
        for (int i = 0; i < 3; i++) {
            ws.out[i] = iu * iu * iu * p0[i] + 3 * iu * iu * u * p1[i]
                    + 3 * iu * u * u * p2[i] + u * u * u * p3[i];
        }
        return ws.out;
    }

    /** 关键帧表达式和常量写入指定数组。 */
    private static double[] valInto(AnimationIR.Keyframe kf, MolangEval.Variables vars, double[] dst) {
        return valInto(kf.value, kf.expr, vars, dst);
    }

    private static double[] preInto(AnimationIR.Keyframe kf, MolangEval.Variables vars, double[] dst) {
        return kf.preValue == null ? valInto(kf, vars, dst)
                : valInto(kf.preValue, kf.preExpr, vars, dst);
    }

    private static double[] valInto(double[] value, String[] expr, MolangEval.Variables vars, double[] dst) {
        for (int i = 0; i < 3; i++) {
            String e = expr == null ? null : expr[i];
            dst[i] = e != null && !e.isEmpty() ? MOLANG.eval(e, vars) : value[i];
        }
        return dst;
    }

    private static Workspace workspace() {
        Workspace ws = WORKSPACE.get();
        if (ws == null) {
            ws = new Workspace();
            WORKSPACE.set(ws);
        }
        while (ws.busy) {
            if (ws.next == null) {
                ws.next = new Workspace(); // 每线程每个嵌套深度只分配一次。
            }
            ws = ws.next;
        }
        ws.busy = true;
        return ws;
    }

    private static void releaseWorkspace(Workspace ws) {
        ws.busy = false;
    }

    private static final class Workspace {
        final double[] out = new double[3];
        final double[] va = new double[3];
        final double[] vb = new double[3];
        final double[] vc = new double[3];
        final double[] vd = new double[3];
        final TimeVars timeVars = new TimeVars();
        boolean busy;
        Workspace next;
    }

    private static final class TimeVars implements MolangEval.Variables {
        MolangEval.Variables base;
        double time;

        void bind(MolangEval.Variables b, double t) {
            base = b;
            time = t;
        }

        @Override
        public double get(String name) {
            if ("query.anim_time".equals(name)) {
                return time;
            }
            return base != null ? base.get(name) : 0;
        }
    }

}
