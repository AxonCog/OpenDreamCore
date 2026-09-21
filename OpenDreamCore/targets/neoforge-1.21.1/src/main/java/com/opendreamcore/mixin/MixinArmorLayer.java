package com.opendreamcore.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.opendreamcore.client.resources.LooseResourceLoader;
import com.opendreamcore.client.visual.VisualArmorLayer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidArmorLayer.class)
public abstract class MixinArmorLayer {

    private static final ThreadLocal<String> ODC_TYPE = new ThreadLocal<>();
    private static final ThreadLocal<String> ODC_SLOT = new ThreadLocal<>();

    @Inject(method = "renderArmorPiece(Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;"
            + "Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;"
            + "ILnet/minecraft/client/model/HumanoidModel;)V", at = @At("HEAD"))
    private void opendreamcore$ctx(PoseStack pose, MultiBufferSource buffer, LivingEntity entity,
                                  EquipmentSlot slot, int light, HumanoidModel<?> model, CallbackInfo ci) {
        String type = null;
        if (entity != null && entity.getType() != null) {
            type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        }
        ODC_TYPE.set(type);
        ODC_SLOT.set(slotName(slot));
    }

    // 直接包裹 renderModel 方法本身，不依赖 renderArmorPiece 内部的调用点——
    // NeoForge 21.1.251 等后续 patch 改动 renderArmorPiece 实现体时 @Redirect 会扫 0 个调用点崩，
    // @WrapMethod 只要方法签名在（mojmap 名跨 patch 稳定）就能注入。
    @WrapMethod(method = "renderModel(Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;"
            + "ILnet/minecraft/client/model/HumanoidModel;ILnet/minecraft/resources/ResourceLocation;)V")
    private void opendreamcore$swap(PoseStack pose, MultiBufferSource buffer, int light,
                                    HumanoidModel<?> model, int darkness, ResourceLocation rl,
                                    Operation<Void> original) {
        String tex = null;
        String type = ODC_TYPE.get();
        String slot = ODC_SLOT.get();
        if (type != null && slot != null) {
            tex = VisualArmorLayer.textureFor(type, slot);
        }
        if (tex == null) {
            original.call(pose, buffer, light, model, darkness, rl);
            return;
        }
        ResourceLocation ours = LooseResourceLoader.lookup(tex);
        if (ours == null) {
            original.call(pose, buffer, light, model, darkness, rl);
            return;
        }
        original.call(pose, buffer, light, model, darkness, ours);
    }

    private static String slotName(EquipmentSlot slot) {
        switch (slot) {
            case HEAD: return "helmet";
            case CHEST: return "chest";
            case LEGS: return "legs";
            case FEET: return "feet";
            default: return null;
        }
    }
}
