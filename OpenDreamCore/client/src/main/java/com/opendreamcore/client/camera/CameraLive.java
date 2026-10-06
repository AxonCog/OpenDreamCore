package com.opendreamcore.client.camera;

import com.opendreamcore.camera.CameraEffects;

/**
 * 运镜实时改写（M1.6）：脚本 Camera.设置位置/看向/设置视场/震屏/过渡 的落点。
 *
 * <p>全部静态易失字段，单写单读（tick 线程写、tick/渲染线程读），volatile 够用。
 * 播放未开始时设置的是「预埋」（下一次播放立即生效）；播放中设置立即生效；
 * 恢复/中断/结束由 {@link CameraFx#reset} 统一清空。nextIn/nextOut 是跨播放的预设，
 * 只被 {@link #applyNextTransition} 消费一次，不随 reset 清（否则 play 前置的
 * restoreNow 会把它抹掉）。
 */
public final class CameraLive {

    private static volatile double[] livePos;
    private static volatile double[] liveLook;
    private static volatile Double liveFov;
    private static volatile long burstStartNanos;
    private static volatile double burstStrength;
    private static volatile int burstTicks;
    private static volatile double nextIn = -1.0;
    private static volatile double nextOut = -1.0;

    private CameraLive() {
    }

    /** 姿态改写：setPos 绝对覆盖机位，lookAt 覆盖朝向。出过渡以路径为准，不掺和。 */
    static void applyPose(CameraActive a, double[] pos, double[] rot) {
        if (a.outro) {
            return;
        }
        double[] p = livePos;
        if (p != null) {
            pos[0] = p[0];
            pos[1] = p[1];
            pos[2] = p[2];
        }
        double[] look = liveLook;
        if (look != null) {
            double[] la = CameraEffects.lookAngles(pos, look);
            rot[0] = la[0];
            rot[1] = la[1];
        }
    }

    /** FOV 覆盖：liveFov 是绝对度数，换算成对原版 baseFov 的因子。 */
    static float applyFov(float factor, double baseFov) {
        Double lf = liveFov;
        if (lf == null || baseFov <= 0.0) {
            return factor;
        }
        return (float) (lf.doubleValue() / baseFov);
    }

    /** 脚本震屏：与规则震屏独立，按真实时间衰减（触发瞬间的 nanoTime 起算）。 */
    static void applyBurst(double[] pos, double[] rot) {
        if (burstStrength <= 0.0 || burstTicks <= 0 || burstStartNanos == 0L) {
            return;
        }
        double sec = (System.nanoTime() - burstStartNanos) / 1.0E9;
        if (sec * 20.0 > burstTicks) {
            return;
        }
        double[] sh = CameraEffects.shake(burstStrength, burstTicks, sec * 20.0, 0.5);
        for (int i = 0; i < 3; i++) {
            pos[i] += sh[i];
        }
        rot[0] += sh[3];
        rot[1] += sh[4];
    }

    /** 消费型：把预设的进/出过渡秒数写进刚建好的 CameraActive，然后清预设。 */
    static void applyNextTransition(CameraActive a) {
        if (nextIn >= 0.0) {
            a.inSeconds = nextIn;
        }
        if (nextOut >= 0.0) {
            a.outSeconds = nextOut;
        }
        nextIn = -1.0;
        nextOut = -1.0;
    }

    /** 播放态清空（restoreNow/dropState 经 CameraFx.reset 调）。 */
    static void reset() {
        livePos = null;
        liveLook = null;
        liveFov = null;
        burstStartNanos = 0L;
        burstStrength = 0.0;
        burstTicks = 0;
    }

    // ---- 脚本入口（CameraMethods 调） ----

    public static void setLivePos(double x, double y, double z) {
        livePos = new double[]{x, y, z};
    }

    public static void clearLivePos() {
        livePos = null;
    }

    public static void setLiveLook(double x, double y, double z) {
        liveLook = new double[]{x, y, z};
    }

    public static void clearLiveLook() {
        liveLook = null;
    }

    public static void setLiveFov(Double degrees) {
        liveFov = degrees;
    }

    public static void burst(double strength, int ticks) {
        burstStrength = strength;
        burstTicks = ticks;
        burstStartNanos = System.nanoTime();
    }

    public static void setNextTransition(double inSeconds, double outSeconds) {
        nextIn = Math.max(0.0, inSeconds);
        nextOut = Math.max(0.0, outSeconds);
    }
}
