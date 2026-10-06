package com.opendreamcore.mixin;

import com.opendreamcore.model1212.ModelRenderer1212;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 实体静态模型替换（1.12.2）：RenderLivingBase.doRender HEAD 可取消，
 * 命中 Model 规则就整体接管（固定管线画烘焙模型并取消原版）。
 *
 * <p>方法名写 stable_39 MCP 名（refmap 由构建期 mixin AP 生成，见既有 MixinNameTag 同款）。
 */
@Mixin(RenderLivingBase.class)
public abstract class OdcModelMixin1212<T extends EntityLivingBase> {

    @Inject(method = "doRender(Lnet/minecraft/entity/Entity;DDDFF)V",
            at = @At("HEAD"), cancellable = true)
    private void odc$modelReplace(Entity entity, double x, double y, double z,
                                  float entityYaw, float partialTicks, CallbackInfo ci) {
        if (ModelRenderer1212.tryRender(entity, x, y, z, entityYaw, partialTicks)) {
            ci.cancel();
        }
    }
}
