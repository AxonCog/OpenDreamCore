package com.opendreamcore.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.opendreamcore.client.visual.ItemIconWorld;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ItemIcon 的手持入口：挂 {@code ItemInHandRenderer.renderItem}（第一/第三人称
 * 画手中物品的唯一入口）。不挂 {@code ItemRenderer.render}——那条路 GUI 容器
 * 图标也走，cancel 会把背包里的图标整格吞空（实机 14:15/15:08 教训）；而
 * renderItem 手持专属，cancel 只影响手中物品。手臂模型在 renderArm 里，
 * 从不经 renderItem，cancel 不伤手臂。
 */
@Mixin(ItemInHandRenderer.class)
public abstract class MixinItemHeld {

    @Inject(method = "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;"
            + "Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), cancellable = true)
    private void opendreamcore$itemIconHeld(LivingEntity entity, ItemStack stack,
                                            ItemDisplayContext context, boolean leftHand,
                                            PoseStack pose, MultiBufferSource buffers,
                                            int light, CallbackInfo ci) {
        if (ItemIconWorld.tryDraw(stack, pose, buffers, light, 0,
                context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND || context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND)) {
            ci.cancel();
        }
    }
}
