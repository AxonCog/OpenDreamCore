package com.opendreamcore.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.opendreamcore.client.visual.ItemIconWorld;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ItemIcon 的世界侧入口（1.21.8 代）。手持链路实测（javap ItemInHandRenderer）：
 * 第一/第三人称手持调
 * {@code renderStatic(LivingEntity, ItemStack, ItemDisplayContext, PoseStack,
 * MultiBufferSource, Level, int, int, int)}（LivingEntity 前缀的 9 参，
 * 1.21.8 去掉了 leftHand 布尔）；掉落物/展示框走
 * {@code renderStatic(ItemStack, …8 参)}。两处都挂才全覆盖。
 */
@Mixin(ItemRenderer.class)
public abstract class MixinItemHeld {

    /** 手持路径（1.21.8：LivingEntity 前缀、无 leftHand）。 */
    @Inject(method = "renderStatic(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/level/Level;III)V",
            at = @At("HEAD"), cancellable = true)
    private void opendreamcore$itemIconHeld(LivingEntity entity, ItemStack stack, ItemDisplayContext context,
                                            PoseStack pose, MultiBufferSource buffers,
                                            Level level, int seed, int light, int overlay,
                                            CallbackInfo ci) {
        // GUI 容器图标也走这条 render：不替换（GUI 是屏幕空间，画世界卡片会整格空白），
        // 只替换手持/掉落等实体场景。
        if (context == ItemDisplayContext.GUI) {
            return;
        }
        if (ItemIconWorld.tryDraw(stack, pose, buffers, light, overlay)) {
            ci.cancel();
        }
    }

    /** 掉落物 / 展示框路径。 */
    @Inject(method = "renderStatic(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;IILcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/level/Level;I)V",
            at = @At("HEAD"), cancellable = true)
    private void opendreamcore$itemIconInWorld(ItemStack stack, ItemDisplayContext context,
                                               int light, int overlay, PoseStack pose,
                                               MultiBufferSource buffers, Level level, int seed,
                                               CallbackInfo ci) {
        if (ItemIconWorld.tryDraw(stack, pose, buffers, light, overlay)) {
            ci.cancel();
        }
    }
}
