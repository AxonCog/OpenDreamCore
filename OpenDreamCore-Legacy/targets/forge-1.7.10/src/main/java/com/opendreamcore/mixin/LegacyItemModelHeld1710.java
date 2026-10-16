package com.opendreamcore.mixin;

import com.opendreamcore.model1710.LegacyItemModel1710;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** First/third-person held stack entry, leaving arm rendering untouched. */
@Mixin(ItemRenderer.class)
public abstract class LegacyItemModelHeld1710 {
    @Inject(method = "renderItem(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;I)V", at = @At("HEAD"), cancellable = true)
    private void odc$itemModelHeld(EntityLivingBase holder, ItemStack stack, int pass, CallbackInfo ci) {
        if (pass == 0 && LegacyItemModel1710.tryWorld(stack)) ci.cancel();
    }
}
