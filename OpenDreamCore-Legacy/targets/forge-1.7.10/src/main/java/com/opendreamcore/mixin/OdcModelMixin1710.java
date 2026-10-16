package com.opendreamcore.mixin;

import com.opendreamcore.model1710.ModelRenderer1710;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replace only RendererLivingEntity.renderModel; vanilla layers remain in doRender. */
@Mixin(RendererLivingEntity.class)
public abstract class OdcModelMixin1710<T extends EntityLivingBase> {

    @Inject(method = "renderModel(Lnet/minecraft/entity/EntityLivingBase;FFFFFF)V",
            at = @At("HEAD"), cancellable = true)
    private void odc$modelReplace(EntityLivingBase entity, float limbSwing,
                                  float limbSwingAmount, float ageInTicks,
                                  float netHeadYaw, float headPitch, float scale,
                                  CallbackInfo ci) {
        if (ModelRenderer1710.renderBody(entity, ageInTicks - entity.ticksExisted)) {
            ci.cancel();
        }
    }
}
