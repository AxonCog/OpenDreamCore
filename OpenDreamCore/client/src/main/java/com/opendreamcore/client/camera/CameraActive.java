package com.opendreamcore.client.camera;

import com.opendreamcore.camera.CameraRule;
import com.opendreamcore.camera.CameraTimeline;
import net.minecraft.world.entity.Entity;

/**
 * 一次播放的全部运行态（从 CameraPlayback 外提的包内类：反臃肿 R1，CameraPlayback 主类要压回 500 行内）。
 *
 * <p>字段全部包内可见——只被同包的 {@link CameraPlayback} 读写，不暴露给任何其他包。
 */
/** 一次播放的全部运行态。 */
final class CameraActive {
    final CameraRule rule;
    /** 临时实体路线用的相机载体；M1.4 直接驱动时为 null（那条路不需要实体）。 */
    final Entity body;
    final CameraStateStack.Entry backup;
    final Object level;
    double inSeconds;
    double outSeconds;
    final double pathSeconds;
    final boolean autoStop;
    final boolean pinPlayer;
    final boolean hideBody;
    /** M1.4：本次播放走 detached 直接驱动（true 时 body 为 null，姿态直接写进原版相机）。 */
    final boolean detached;
    final double[] fromPos = new double[3];
    final double[] fromRot = new double[2];
    double elapsed;
    boolean outro;
    double outroElapsed;
    double[] outroFromPos;
    double[] outroFromRot;
    /**
     * 直接驱动用的帧内插值缓存：tick 采样出 prev/cur 两帧姿态，渲染帧按 partialTick 混合。
     * 不插值的话 20tps 的更新在 144fps 下就是一顿一顿的——与临时实体路线把旧值写进
     * setOldPosAndRot/yHeadRotO 是同一个问题的两种解法。
     */
    double[] prevPos;
    double[] prevRot;
    double[] curPos;
    double[] curRot;

    /** 本 tick 关键帧采出的 fov（度）；关键帧没写 fov 时为 null（M1.5 FOV 平滑用）。 */
    Double curFov;
    /** M1.6：t≤0 的事件点是否已在首 tick 补发过（一次性）。 */
    boolean startupFired;

    CameraActive(CameraRule rule, Entity body, Object level, CameraStateStack.Entry backup, boolean detached) {
        this.rule = rule;
        this.body = body;
        this.backup = backup;
        this.level = level;
        this.detached = detached;
        this.inSeconds = Math.max(0, rule.transitionIn) / 20.0;
        this.outSeconds = Math.max(0, rule.transitionOut) / 20.0;
        this.pathSeconds = CameraTimeline.totalSeconds(rule);
        this.autoStop = CameraTimeline.autoStops(rule);
        this.pinPlayer = rule.lockInput || "hide_body".equalsIgnoreCase(rule.player);
        this.hideBody = "hide_body".equalsIgnoreCase(rule.player);
    }
}
