package com.opendreamcore.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.opendreamcore.client.render.OdcItemIconSubmit;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 手持替换（26.1.2）：物品渲染器整体搬到 {@code ItemInHandRenderer}，
 * 这里的 renderItem 是拿得到 ItemStack + PoseStack + 收集器的提交点。
 */
@Mixin(ItemInHandRenderer.class)
public abstract class MixinItemHeldSubmit {

    @Inject(method = "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
            at = @At("HEAD"), cancellable = true)
    private void opendreamcore$itemIconInHand(LivingEntity entity, ItemStack stack,
                                              ItemDisplayContext context, PoseStack pose,
                                              SubmitNodeCollector collector, int light,
                                              CallbackInfo ci) {
        if (OdcItemIconSubmit.submit(stack, pose, collector)) {
            ci.cancel();
        }
    }
}
