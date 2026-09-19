package com.opendreamcore.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.opendreamcore.client.resources.LooseResourceLoader;
import com.opendreamcore.client.visual.VisualArmorLayer;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.Equippable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(HumanoidArmorLayer.class)
public abstract class MixinArmorLayer {

    @Redirect(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;I"
            + "Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/layers/EquipmentLayerRenderer;"
                            + "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;"
                            + "Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;"
                            + "Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;"
                            + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private void opendreamcore$swapLayers(EquipmentLayerRenderer renderer,
                                          EquipmentClientInfo.LayerType layerType,
                                          ResourceKey key, Model model, ItemStack stack,
                                          PoseStack pose, MultiBufferSource buffer, int light) {
        String tex = null;
        if (stack != null) {
            Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
            String slot = equippable == null ? null : slotName(equippable.slot());
            if (slot != null) {
                tex = VisualArmorLayer.textureForGlobal(slot);
            }
        }
        if (tex == null) {
            renderer.renderLayers(layerType, key, model, stack, pose, buffer, light);
            return;
        }
        ResourceLocation rl = LooseResourceLoader.lookup(tex);
        if (rl == null) {
            renderer.renderLayers(layerType, key, model, stack, pose, buffer, light);
            return;
        }
        renderer.renderLayers(layerType, key, model, stack, pose, buffer, light, rl);
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
