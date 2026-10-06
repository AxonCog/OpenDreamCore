package com.opendreamcore.mixin;

import com.opendreamcore.client.camera.CameraFovHook;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * FOV 注入（M1.5，gen2+gen3：1.21.4 / 1.21.8 / 1.21.11）。
 *
 * <p>与 gen1（{@code camera-fov-1.20-1.21.1}）唯一的区别是返回类型：
 * 这三代 {@code GameRenderer.getFov(Camera,float,boolean)} 已从 double 收窄为 float
 * （字段也从 {@code fov/oldFov} 改名 {@code fovModifier/oldFovModifier}，javap 实测）。
 * 混入描述符与回调泛型随之变化，逻辑一字不差。
 */
@Mixin(GameRenderer.class)
public abstract class OdcFovMixin {

    @Inject(method = "getFov(Lnet/minecraft/client/Camera;FZ)F", at = @At("TAIL"))
    private void odc$fovMul(Camera camera, float partialTick, boolean useFov,
                            CallbackInfoReturnable<Float> cir) {
        float f = CameraFovHook.factor();
        if (f != 1.0F) {
            cir.setReturnValue(cir.getReturnValueF() * f);
        }
    }
}
