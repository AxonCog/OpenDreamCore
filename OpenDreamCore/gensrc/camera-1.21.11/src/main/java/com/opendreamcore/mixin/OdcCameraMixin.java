package com.opendreamcore.mixin;

import com.opendreamcore.camera.CameraAccess;
import com.opendreamcore.camera.OdcCameraDriver;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 相机接管（M1.4，代际 gen3：1.21.11）。
 *
 * <p>与 gen1+gen2 那份的**唯一区别**就是 {@code Camera.setup} 的首个形参类型：
 * {@code BlockGetter → Level}（1.21.11 实测；同代 {@code ResourceLocation} 也改名了
 * {@code Identifier}，但本混入不碰资源引用，所以只改这一处描述符）。
 * 其余设计理由见 {@code gensrc/camera-1.20-1.21.8} 那份的同名注释。
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

    /** 同 gen1+gen2，只是首个形参换成了 {@code Level}。 */
    @Inject(method = "setup(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;ZZF)V",
            at = @At("TAIL"))
    private void odc$driveDetached(Level level, Entity entity, boolean detached, boolean mirrored,
                                   float partialTick, CallbackInfo ci) {
        OdcCameraDriver.get().onFrame((CameraAccess) this, partialTick);
    }
}
