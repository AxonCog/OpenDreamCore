package com.opendreamcore.mixin;

import com.opendreamcore.model1212.LegacyItemModel1212;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla has already applied the camera/held/drop transform at this common mesh entry. */
@Mixin(RenderItem.class)
public abstract class LegacyItemModelWorld1212 {
    @Inject(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/client/renderer/block/model/ItemCameraTransforms$TransformType;Z)V", at = @At("HEAD"), cancellable = true)
    private void odc$itemModelWorld(ItemStack stack, EntityLivingBase owner,
                                    ItemCameraTransforms.TransformType type, boolean leftHand,
                                    CallbackInfo ci) {
        if (LegacyItemModel1212.tryWorld(stack)) ci.cancel();
    }
}
