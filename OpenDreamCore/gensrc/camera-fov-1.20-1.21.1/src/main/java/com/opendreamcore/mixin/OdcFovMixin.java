package com.opendreamcore.mixin;

import com.opendreamcore.client.camera.CameraFovHook;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * FOV 注入（M1.5，gen1：1.20.1 / 1.21.1）。
 *
 * <p>原版每帧在 {@code GameRenderer.getFov(Camera,float,boolean)} 里算出
 * 「设置值 + 效果修正」的最终视野，这里在 TAIL 把返回值乘上演出因子
 * （{@code CameraFx.publishFov} 每 tick 发布；不播放 = 1，纯直通）。
 *
 * <p>本代 {@code getFov} 返回 <b>double</b>（javap 实测）；1.21.4 起收窄为 float，
 * 那部分在 {@code gensrc/camera-fov-1.21.4-1.21.11}。
 */
@Mixin(GameRenderer.class)
public abstract class OdcFovMixin {

    @Inject(method = "getFov(Lnet/minecraft/client/Camera;FZ)D", at = @At("TAIL"))
    private void odc$fovMul(Camera camera, float partialTick, boolean useFov,
                            CallbackInfoReturnable<Double> cir) {
        float f = CameraFovHook.factor();
        if (f != 1.0F) {
            cir.setReturnValue(cir.getReturnValueD() * f);
        }
    }
}
