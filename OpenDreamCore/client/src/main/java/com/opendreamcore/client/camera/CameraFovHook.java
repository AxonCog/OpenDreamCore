package com.opendreamcore.client.camera;

/**
 * FOV 因子中转：tick 侧（{@code CameraFx.publishFov}）每 tick 发布一次，
 * FOV 混入（各代 {@code OdcFovMixin}）在渲染帧读取并乘到原版视野上。
 *
 * <p>volatile 只保证可见性，不做原子复合——混入读到的永远是「最近一次完整发布的因子」，
 * 一个 tick 内被刷新多次也没关系。播放未开始 / 已恢复时恒为 1（混入侧据此直通）。
 */
public final class CameraFovHook {

    private CameraFovHook() {
    }

    private static volatile float factor = 1.0F;

    /** 渲染帧读取：当前演出因子（1 = 原样直通）。 */
    public static float factor() {
        return factor;
    }

    /** tick 侧发布。 */
    public static void publish(float f) {
        factor = f;
    }

    /** 恢复/中断/结束时归一——否则混入会把最后一次的因子永远留在渲染路径上。 */
    public static void reset() {
        factor = 1.0F;
    }
}
