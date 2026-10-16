package com.opendreamcore.camera;

/**
 * 相机演出的纯数学效果层（震屏 + FOV 平滑）。
 *
 * <p>只做算术、不 import 任何 MC 类——这是它能进共享层并被 JDK8 测试宿主直接覆盖的前提；
 * 「摸 MC」的那半边在客户端的 {@code CameraFx}。独立开关由调用方保证：
 * strength≤0 / shakeTicks≤0 时震屏是零向量，kfFov 为空且 stretch=1 时 FOV 因子恒为 1。
 */
public final class CameraEffects {

    private CameraEffects() {
    }

    /**
     * 震屏：位置 + 旋转噪声，线性衰减到 0。
     *
     * @param strength   初始强度（位置噪声幅度，米；旋转噪声为其 2 倍度数）
     * @param totalTicks 总衰减 tick 数，≤0 视为不震
     * @param tick       已播放 tick 数（可带小数，渲染帧插值友好）
     * @param seed       相位种子（用规则 id 的 hash 派生，保证同规则每次播放可复现）
     * @return {dx, dy, dz, dYaw度, dPitch度}，全部已含衰减包络
     */
    public static double[] shake(double strength, int totalTicks, double tick, double seed) {
        if (strength <= 0.0 || totalTicks <= 0 || tick < 0 || tick >= totalTicks) {
            return new double[]{0.0, 0.0, 0.0, 0.0, 0.0};
        }
        double left = 1.0 - tick / totalTicks;
        double a = strength * left;
        double t = tick * 0.35 + seed;
        return new double[]{
                Math.sin(t * 2.1) * a,
                Math.sin(t * 2.7 + 1.7) * a * 0.6,
                Math.sin(t * 2.4 + 3.1) * a,
                Math.sin(t * 1.9 + 4.3) * a * 2.0,
                Math.sin(t * 2.2 + 5.9) * a * 2.0,
        };
    }

    /**
     * FOV 因子：混入侧把原版视野乘上这个值。
     *
     * <p>目标视野 = 关键帧 fov（绝对度数，演出用「推镜」时直接写 30/100 这类值）优先；
     * 没写关键帧 fov 时用 {@code baseFov × stretch}（视野拉伸倍率）。进/出过渡期间
     * 因子从 1（即不干预）平滑滑到目标，两个包络取 min，保证进、出都不跳变。
     *
     * @param kfFov   本 tick 关键帧采出的 fov；null = 未指定
     * @param stretch 视野拉伸倍率，1.0 = 不拉伸
     * @param baseFov 原版当前设置的基础视野（度）
     * @param inFrac  进过渡进度 0→1（无进过渡时恒 1）
     * @param outFrac 出过渡剩余 1→0（未在出过渡时恒 1）
     */
    public static double fovFactor(Double kfFov, double stretch, double baseFov,
                                   double inFrac, double outFrac) {
        if (baseFov <= 0.0) {
            return 1.0;
        }
        boolean stretchActive = Math.abs(stretch - 1.0) > 1.0E-9;
        if (kfFov == null && !stretchActive) {
            return 1.0;
        }
        double target = kfFov != null ? kfFov.doubleValue() : baseFov * stretch;
        double k = smoothstep(clamp01(Math.min(inFrac, outFrac)));
        double f = target / baseFov;
        return 1.0 + (f - 1.0) * k;
    }

    /** 平滑缓动（smoothstep）：端点导数为 0，比线性更不容易被肉眼看出拐点。 */
    static double smoothstep(double x) {
        return x * x * (3.0 - 2.0 * x);
    }

    static double clamp01(double x) {
        return x < 0.0 ? 0.0 : (x > 1.0 ? 1.0 : x);
    }

    /**
     * 纯几何：从 from 看向 to 的 [yaw, pitch]（度，MC 语义：yaw=-90 面向 +X；pitch 向下为正）。
     * Camera.lookAt 运行期实时求朝向用；from=to（水平距离为 0 且 dy=0）时返回 {0, 0}。
     */
    public static double[] lookAngles(double[] from, double[] to) {
        double dx = to[0] - from[0];
        double dy = to[1] - from[1];
        double dz = to[2] - from[2];
        double horiz = Math.sqrt(dx * dx + dz * dz);
        if (horiz < 1.0E-9 && Math.abs(dy) < 1.0E-9) {
            return new double[]{0.0, 0.0};
        }
        double yaw = Math.toDegrees(Math.atan2(dz, dx)) - 90.0;
        double pitch = horiz < 1.0E-9 ? (dy > 0 ? -90.0 : 90.0)
                : -Math.toDegrees(Math.atan2(dy, horiz));
        return new double[]{yaw, pitch};
    }
}
