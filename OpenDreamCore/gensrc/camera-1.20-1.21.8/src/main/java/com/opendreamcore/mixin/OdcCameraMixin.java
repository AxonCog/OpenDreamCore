package com.opendreamcore.mixin;

import com.opendreamcore.camera.CameraAccess;
import com.opendreamcore.camera.OdcCameraDriver;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 相机接管（M1.4，代际 gen1+gen2：1.20.1 / 1.21.1 / 1.21.4 / 1.21.8）。
 *
 * <p>三个事实决定了它长这样（六版 merged jar 逐个 javap 实测，见
 * {@code tools/camera-api-probe}）：
 * <ol>
 *   <li>{@code Camera.detached} 是 <b>private 字段</b>、{@code setPosition(DDD)}/{@code setRotation(FF)}
 *       是 <b>protected 方法</b>——共享层直调不了、反射又在本项目生产环境必然失败
 *       （Fabric 的 loom 不重映射字符串形式的 MC 名），所以只能由混入用
 *       {@code @Accessor}/{@code @Invoker} 生成实现，再以 {@link CameraAccess} 的形式露给共享层。</li>
 *   <li>原版每帧在 {@code setup} <b>开头</b>就把 {@code detached} 与姿态按「是否第三人称」重算一遍，
 *       因此我们的写入必须在 {@code setup} 的 <b>TAIL</b>——早一步都会被覆盖回去。</li>
 *   <li>姿态要按渲染帧给（不是 tick）：{@code partialTick} 正好是 {@code setup} 的最后一个形参，
 *       原样转给驱动器做帧内插值，与临时实体路线把旧值写进 {@code setOldPosAndRot} 是同一目的。</li>
 * </ol>
 *
 * <p>本代 {@code setup} 的首个形参是 {@code BlockGetter}；1.21.11 改成了 {@code Level}，
 * 26.1.2 直接删掉了 {@code setup}——所以这一份源码只挂这四个目标。
 */
@Mixin(Camera.class)
public abstract class OdcCameraMixin implements CameraAccess {

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

    /**
     * 原版算完之后把我们的姿态压上去。没接管时驱动器一个字段都不写（它先比对相机身份）。
     */
    @Inject(method = "setup(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/entity/Entity;ZZF)V",
            at = @At("TAIL"))
    private void odc$driveDetached(BlockGetter level, Entity entity, boolean detached, boolean mirrored,
                                   float partialTick, CallbackInfo ci) {
        OdcCameraDriver.get().onFrame((CameraAccess) this, partialTick);
    }
}
