package com.opendreamcore.mixin;

import com.opendreamcore.model1710.ModelRenderer1710;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 实体静态模型替换（1.7.10）：RendererLivingEntity.doRender（SRG func_76986_a）HEAD 可取消，
 * 命中 Model 规则就整体接管（固定管线画烘焙模型并取消原版）。
 *
 * <p>方法名写 MCP 名 doRender（与既有 MixinNameTag/1.12.2 同口径，refmap 由构建期 AP 生成）；
 * 泛型与 1.7.10 的 doRender(T,double,double,double,float,float) 对齐。
 */
@Mixin(RendererLivingEntity.class)
public abstract class OdcModelMixin1710<T extends EntityLivingBase> {

    @Inject(method = "doRender(Lnet/minecraft/entity/Entity;DDDFF)V",
            at = @At("HEAD"), cancellable = true)
    private void odc$modelReplace(Entity entity, double x, double y, double z,
                                  float entityYaw, float partialTicks, CallbackInfo ci) {
        if (ModelRenderer1710.tryRender(entity, x, y, z, entityYaw, partialTicks)) {
            ci.cancel();
        }
    }
}
