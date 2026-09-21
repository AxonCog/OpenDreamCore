package com.opendreamcore.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.opendreamcore.client.resources.LooseResourceLoader;
import com.opendreamcore.client.visual.VisualArmorLayer;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(EquipmentLayerRenderer.class)
public abstract class MixinEquipmentRenderer {

    @Shadow
    public void renderLayers(EquipmentClientInfo.LayerType layerType, ResourceKey key, Model model,
                             ItemStack stack, PoseStack pose, MultiBufferSource buffer, int light,
                             ResourceLocation rl) {
    }

    // 直接包裹 renderLayers 方法本身，不依赖 renderArmorPiece 内部的调用点——
    // NeoForge patch 改动 renderArmorPiece 实现体时 @Redirect 会扫 0 个调用点崩，
    // @WrapMethod 只要方法签名在（mojmap 名跨 patch 稳定）就能注入。
    @WrapMethod(method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;"
            + "Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;"
            + "Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V")
    private void opendreamcore$swapLayers(EquipmentClientInfo.LayerType layerType,
                                          ResourceKey key, Model model, ItemStack stack,
                                          PoseStack pose, MultiBufferSource buffer, int light,
                                          Operation<Void> original) {
        String tex = null;
        if (stack != null) {
            Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
            String slot = equippable == null ? null : slotName(equippable.slot());
            String type = VisualArmorLayer.ODC_CUR_ENTITY.get();
            if (slot != null && type != null) {
                tex = VisualArmorLayer.textureFor(type, slot);
            }
        }
        if (tex == null) {
            original.call(layerType, key, model, stack, pose, buffer, light);
            return;
        }
        ResourceLocation rl = LooseResourceLoader.lookup(tex);
        if (rl == null) {
            original.call(layerType, key, model, stack, pose, buffer, light);
            return;
        }
        this.renderLayers(layerType, key, model, stack, pose, buffer, light, rl);
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