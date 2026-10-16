package com.opendreamcore.mixin;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.opendreamcore.model1165.LegacyItemModel1165;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.client.renderer.model.ItemCameraTransforms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** ItemRenderer's static entry covers both dropped and held contexts after camera transforms. */
@Mixin(ItemRenderer.class)
public abstract class LegacyItemModelWorld1165 {
    @Inject(method = "func_229110_a_(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/renderer/model/ItemCameraTransforms$TransformType;IILcom/mojang/blaze3d/matrix/MatrixStack;Lnet/minecraft/client/renderer/IRenderTypeBuffer;)V", at = @At("HEAD"), cancellable = true)
    private void odc$itemModelWorld(ItemStack stack, ItemCameraTransforms.TransformType type, int light, int overlay,
                                    MatrixStack pose, IRenderTypeBuffer buffers, CallbackInfo ci) {
        if (LegacyItemModel1165.tryWorld(stack, pose, buffers, light, overlay)) ci.cancel();
    }
}
