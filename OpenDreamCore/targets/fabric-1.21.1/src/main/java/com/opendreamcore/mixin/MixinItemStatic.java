package com.opendreamcore.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.opendreamcore.client.visual.ItemIconWorld;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ItemIcon 的掉落物/展示框入口：{@code ItemRenderer.renderStatic}。
 * 手持不经它、GUI 也不走它，命中规则 cancel 只影响世界里的物品实体。
 */
@Mixin(ItemRenderer.class)
public abstract class MixinItemStatic {

    @Inject(method = "renderStatic(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;IILcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/level/Level;I)V",
            at = @At("HEAD"), cancellable = true)
    private void opendreamcore$itemIconInWorld(ItemStack stack, ItemDisplayContext context,
                                               int light, int overlay, PoseStack pose,
                                               MultiBufferSource buffers,
                                               net.minecraft.world.level.Level level,
                                               int seed, CallbackInfo ci) {
        if (ItemIconWorld.tryDraw(stack, pose, buffers, light, overlay)) {
            ci.cancel();
        }
    }
}
