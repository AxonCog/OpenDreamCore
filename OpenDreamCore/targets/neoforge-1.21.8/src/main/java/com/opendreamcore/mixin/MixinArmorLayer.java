package com.opendreamcore.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.opendreamcore.client.visual.VisualArmorLayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidArmorLayer.class)
public abstract class MixinArmorLayer {

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;I"
            + "Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V",
            at = @At("HEAD"))
    private void opendreamcore$startRender(PoseStack pose, MultiBufferSource buffer, int light,
                                           HumanoidRenderState state, float p1, float p2,
                                           CallbackInfo ci) {
        VisualArmorLayer.ODC_CUR_ENTITY.set(state == null || state.entityType == null ? null
                : BuiltInRegistries.ENTITY_TYPE.getKey(state.entityType).toString());
    }
}
