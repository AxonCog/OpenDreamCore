package com.opendreamcore.model.anim;

/**
 * 姿态混合（三段分离之二）：crossfade 与叠加层。
 *
 *  - lerpInto：两姿态按 u 逐分量插值（切换动画的过渡混合）；
 *  - addInto：叠加层（bedrock blend_weight 语义）：pos/rot 直接加权累加，
 *    scale 乘法混合（1 + (add-1)*w），避免叠层把体型越乘越大时跳变。
 */
public final class AnimBlender {

    private AnimBlender() {
    }

    /** 新姿态。 */
    public static AnimEval.Pose fresh() {
        return new AnimEval.Pose();
    }

    /** out = lerp(a, b, u)（u 夹在 [0,1]；out 可与 a/b 同对象）。 */
    public static void lerpInto(AnimEval.Pose a, AnimEval.Pose b, double u, AnimEval.Pose out) {
        u = Math.min(Math.max(u, 0), 1);
        mix(a.rot, b.rot, u, out.rot);
        mix(a.pos, b.pos, u, out.pos);
        mix(a.scale, b.scale, u, out.scale);
    }

    /** out = base ⊕ add*w：rot/pos 加权累加，scale 乘法混合（out 不可与 base/add 同对象）。 */
    public static void addInto(AnimEval.Pose base, AnimEval.Pose add, double w, AnimEval.Pose out) {
        w = Math.min(Math.max(w, 0), 1);
        for (int i = 0; i < 3; i++) {
            out.rot[i] = base.rot[i] + add.rot[i] * w;
            out.pos[i] = base.pos[i] + add.pos[i] * w;
            out.scale[i] = base.scale[i] * (1 + (add.scale[i] - 1) * w);
        }
    }

    private static void mix(double[] a, double[] b, double u, double[] out) {
        for (int i = 0; i < 3; i++) {
            out[i] = a[i] + (b[i] - a[i]) * u;
        }
    }

    /** 全零旋转位移 + 全 1 缩放（叠加无意义，跳过省一次遍历）。 */
    public static boolean isNeutral(AnimEval.Pose p) {
        return p.rot[0] == 0 && p.rot[1] == 0 && p.rot[2] == 0
                && p.pos[0] == 0 && p.pos[1] == 0 && p.pos[2] == 0
                && p.scale[0] == 1 && p.scale[1] == 1 && p.scale[2] == 1;
    }

    /**
     * 姿态表叠加层（物理姿态/一次性动作叠在循环底姿上）：layer 按权重 additive
     * 进 base（base 被修改；layer 不动）。单骨骼语义同 addInto，但按骨骼名走表、
     * 中性骨骼直接跳过不新增条目。
     */
    public static void add(java.util.Map<String, AnimEval.Pose> base,
                           java.util.Map<String, AnimEval.Pose> layer, double weight) {
        if (base == null || layer == null || layer.isEmpty() || weight == 0) {
            return;
        }
        for (java.util.Map.Entry<String, AnimEval.Pose> e : layer.entrySet()) {
            AnimEval.Pose lp = e.getValue();
            if (lp == null || isNeutral(lp)) {
                continue;
            }
            AnimEval.Pose bp = base.get(e.getKey());
            if (bp == null) {
                bp = fresh();
                base.put(e.getKey(), bp);
            }
            double w = Math.min(Math.max(weight, 0), 1);
            for (int i = 0; i < 3; i++) {
                bp.rot[i] += lp.rot[i] * w;
                bp.pos[i] += lp.pos[i] * w;
                bp.scale[i] *= 1 + (lp.scale[i] - 1) * w;
            }
        }
    }
}
