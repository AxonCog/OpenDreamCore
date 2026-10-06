package com.opendreamcore.mixin;

import com.opendreamcore.camera.CameraAccess;
import com.opendreamcore.camera.OdcCameraDriver;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 相机接管（M1.4，代际 gen4：26.1.2）。
 *
 * <p>这一代 {@code Camera.setup} <b>被删掉了</b>（实测方法从 25 个涨到 48 个，但 setup 不在其中），
 * 每帧的驱动点变成 {@code update(DeltaTracker)} / {@code tick()} / {@code alignWithEntity(float)}：
 * <ul>
 *   <li>只注入 {@code update(DeltaTracker)} 与 {@code tick()} 的 TAIL，两者都写一遍。
 *       同帧内谁后跑谁生效，两份都写保证「原版算完之后我们收尾」这个顺序，不会出现
 *       只挂了其中一个而另一个把姿态覆盖回去的情况。</li>
 *   <li>姿态插值：本代暂时按 {@code partialTick = 1.0} 提交（即「本 tick 的最终姿态」）。
 *       原因很实在：26.1.2 的 {@code DeltaTracker} 尚无可安全直调取 partialTick 的成员
 *       （方法名未在多版本上对齐过，猜错就是该目标编译失败），而错误的 partialTick 比
 *       不做插值更糟。代价是 26.1.2 上运镜按 tick 分辨率呈现（20tps）；
 *       待该代 API 定稳后在这里换成真实 partialTick 即可，其它版本不受影响。</li>
 * </ul>
 *
 * <p>{@code detached} 字段与 {@code setPosition}/{@code setRotation} 在本代仍然可用（实测），
 * 所以 @Accessor/@Invoker 的部分与前两代完全一致。
 */
@Mixin(Camera.class)
public abstract class OdcCameraMixin implements CameraAccess {

    /** 本代暂用的帧内插值系数：见类注释（不用直接调 DeltaTracker，避免版本 API 猜错）。 */
    private static final float ODC_PARTIAL_TICK = 1.0F;

    /** 写 private 字段 {@code Camera.detached}。 */
    @Accessor("detached")
    abstract void odc$setDetached(boolean detached);

    /** 调 protected 方法 {@code Camera.setPosition(DDD)}。 */
    @Invoker("setPosition")
    abstract void odc$setPosition(double x, double y, double z);

    /** 调 protected 方法 {@code Camera.setRotation(FF)}。 */
    @Invoker("setRotation")
    abstract void odc$setRotation(float yRot, float xRot);

    @Override
    public void odcCameraSetDetached(boolean detached) {
        odc$setDetached(detached);
    }

    @Override
    public void odcCameraSetPosition(double x, double y, double z) {
        odc$setPosition(x, y, z);
    }

    @Override
    public void odcCameraSetRotation(float yRot, float xRot) {
        odc$setRotation(yRot, xRot);
    }

    /** 每帧驱动点之一：{@code update(DeltaTracker)} 之后。 */
    @Inject(method = "update(Lnet/minecraft/client/DeltaTracker;)V", at = @At("TAIL"))
    private void odc$driveDetachedUpdate(DeltaTracker deltaTracker, CallbackInfo ci) {
        OdcCameraDriver.get().onFrame((CameraAccess) this, ODC_PARTIAL_TICK);
    }

    /** 每帧驱动点之二：{@code tick()} 之后（部分路径只调它）。 */
    @Inject(method = "tick()V", at = @At("TAIL"))
    private void odc$driveDetachedTick(CallbackInfo ci) {
        OdcCameraDriver.get().onFrame((CameraAccess) this, ODC_PARTIAL_TICK);
    }
}
