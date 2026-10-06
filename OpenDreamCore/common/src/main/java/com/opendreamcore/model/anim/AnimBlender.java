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
}
