package com.opendreamcore.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.opendreamcore.client.render.OdcItemIconSubmit;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 手持替换（1.21.11）：submit 管线里唯一「拿得到 ItemStack + PoseStack + 收集器」的点。
 * 命中 ItemIcon 规则就提交自定义贴图四边形并 cancel 原版提交；没命中原版一行不改。
 */
@Mixin(ItemInHandLayer.class)
public abstract class MixinItemHeldSubmit {

    @Inject(method = "submitArmWithItem(Lnet/minecraft/client/renderer/entity/state/ArmedEntityRenderState;Lnet/minecraft/client/renderer/item/ItemStackRenderState;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
            at = @At("HEAD"), cancellable = true)
    private void opendreamcore$itemIconInHand(ArmedEntityRenderState entityState,
                                              ItemStackRenderState itemState, ItemStack stack,
                                              HumanoidArm arm, PoseStack pose,
                                              SubmitNodeCollector collector, int light,
                                              CallbackInfo ci) {
        if (OdcItemIconSubmit.submit(stack, pose, collector)) {
            ci.cancel();
        }
    }
}
