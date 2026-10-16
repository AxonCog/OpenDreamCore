package com.opendreamcore.model.anim;

/**
 * 动画时钟：真实帧间推进 → 采样时间。
 *
 *  - 单帧上限：卡顿/断点恢复时单帧 delta 超过 0.25s 的部分直接截断（防动画瞬移）；
 *  - 冻结看门狗：超 1s 没有任何 tick（窗口失焦/后台限帧），恢复后强制按 clamp 步进
 *    而不是一次性追帧——动画从中断处继续，观感上只是暂停了一下；
 *  - 纯 Java 8，无 MC 依赖；每实体一个实例（可变状态，渲染线程独占使用）。
 */
public final class AnimationClock {

    /** 单帧推进上限（秒）。 */
    public static final double MAX_DELTA = 0.25;
    /** 判定冻结的静默阈值（秒）。 */
    public static final double FREEZE_AFTER = 1.0;

    private double time;
    private double sinceLast;

    /** 推进一帧：delta 为真实帧间隔；返回采样时间。 */
    public double tick(double delta) {
        if (delta < 0) {
            delta = 0;
        }
        sinceLast = 0;
        if (delta > MAX_DELTA) {
            delta = MAX_DELTA;
        }
        time += delta;
        return time;
    }

    /** 跳到绝对时间（切动画/ seek 用）。 */
    public void seek(double t) {
        time = Math.max(0, t);
        sinceLast = 0;
    }

    /** 当前采样时间。 */
    public double time() {
        return time;
    }

    /** 距上次 tick 的静默秒数（由外部按帧调 noteGap 判冻结）。 */
    public double gap() {
        return sinceLast;
    }

    /** 外部记一次静默帧（胶水每帧调用，delta 传真实帧间隔；冻结超阈值后首次 tick 自动限幅）。 */
    public void noteGap(double delta) {
        sinceLast += Math.max(0, delta);
    }

    /** 冻结判定：静默超阈值。 */
    public boolean frozen() {
        return sinceLast > FREEZE_AFTER;
    }

    /** 重置到 0。 */
    public void reset() {
        time = 0;
        sinceLast = 0;
    }
}
