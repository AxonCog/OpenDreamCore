package com.opendreamcore.camera;

/**
 * 相机姿态写入面。
 *
 * <p><b>为什么必须要有这层接口</b>：原版 {@code Camera} 的 {@code detached} 是
 * {@code private} 字段，{@code setPosition}/{@code setRotation} 是 {@code protected} 方法
 * （1.20.1 / 1.21.1 / 1.21.4 / 1.21.8 / 1.21.11 / 26.1.2 六个版本逐个 javap 实证）。
 * 于是共享层（common/，零 MC 依赖）**没有任何合法途径**直接写相机；而按 MC 名反射在本项目的
 * 生产环境必然失败（loom 不重映射 {@code Class.forName}/{@code getMethod} 的字符串，
 * 见 0.1.2.2 的「按 MC 名反射全部改直调」整改）。
 *
 * <p>解法：把「能对相机做的三件事」定成接口，**由 per-target 的混入实现**
 * （{@code com.opendreamcore.mixin.OdcCameraMixin} 用 {@code @Accessor}/{@code @Invoker}
 * 生成实现，见各 gensrc/camera-* 源码树），共享层的驱动只认这个接口。
 * 好处：①拿到 private/protected 的能力；②共享层保持零 MC 依赖、可用假实现单测；
 * ③混入没注册/被别的模组替换掉 Camera 时，{@code instanceof} 自然失败，上层回退到临时实体路线。
 */
public interface CameraAccess {

    /** 写 {@code Camera.detached}（原版每帧在 setup 开头用「是否第三人称」重写它）。 */
    void odcCameraSetDetached(boolean detached);

    /** 写相机世界坐标（原版 {@code Camera.setPosition(DDD)}，protected）。 */
    void odcCameraSetPosition(double x, double y, double z);

    /** 写相机朝向（原版 {@code Camera.setRotation(yRot, xRot)}，protected）。 */
    void odcCameraSetRotation(float yRot, float xRot);
}
