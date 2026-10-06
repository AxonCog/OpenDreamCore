package com.opendreamcore.mixin;

import com.opendreamcore.client.camera.CameraFovHook;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * FOV 注入（M1.5，gen4：26.1.2）。
 *
 * <p>这一代视野计算整体搬进了 {@code Camera}：{@code GameRenderer} 里已经没有任何
 * fov 成员（javap 实测），取而代之的是 {@code Camera.fovModifier/oldFovModifier/fov} 与
 * 每帧的 {@code calculateFov(float)}（partialTick → 本帧视野，含效果修正）。
 * 所以注入点从 GameRenderer 换成 Camera.calculateFov 的 TAIL，乘法逻辑不变。
 */
@Mixin(Camera.class)
public abstract class OdcFovMixin {

    @Inject(method = "calculateFov(F)F", at = @At("TAIL"))
    private void odc$fovMul(float partialTick, CallbackInfoReturnable<Float> cir) {
        float f = CameraFovHook.factor();
        if (f != 1.0F) {
            cir.setReturnValue(cir.getReturnValueF() * f);
        }
    }
}
