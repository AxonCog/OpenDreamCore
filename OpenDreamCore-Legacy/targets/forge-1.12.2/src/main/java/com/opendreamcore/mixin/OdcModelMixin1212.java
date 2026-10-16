package com.opendreamcore.mixin;

import com.opendreamcore.model1212.ModelRenderer1212;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replace only RenderLivingBase.renderModel; doRender still renders all layers. */
@Mixin(RenderLivingBase.class)
public abstract class OdcModelMixin1212<T extends EntityLivingBase> {

    @Inject(method = "renderModel(Lnet/minecraft/entity/EntityLivingBase;FFFFFF)V",
            at = @At("HEAD"), cancellable = true)
    private void odc$modelReplace(EntityLivingBase entity, float limbSwing,
                                  float limbSwingAmount, float ageInTicks,
                                  float netHeadYaw, float headPitch, float scale,
                                  CallbackInfo ci) {
        if (ModelRenderer1212.renderBody(entity, ageInTicks - entity.ticksExisted)) {
            ci.cancel();
        }
    }
}
