package com.opendreamcore.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.opendreamcore.client.resources.LooseResourceLoader;
import com.opendreamcore.client.visual.VisualArmorLayer;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.Equippable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(HumanoidArmorLayer.class)
public abstract class MixinArmorLayer {

    @Redirect(method = "renderArmorPiece(Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/SubmitNodeCollector;"
            + "Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/EquipmentSlot;"
            + "ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/layers/EquipmentLayerRenderer;"
                            + "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;"
                            + "Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;"
                            + "Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;"
                            + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;II)V"))
    private void opendreamcore$swapLayers(EquipmentLayerRenderer renderer,
                                          EquipmentClientInfo.LayerType layerType,
                                          ResourceKey key, Model model, Object stateRaw,
                                          ItemStack stack, PoseStack pose,
                                          SubmitNodeCollector collector, int a, int b) {
        String tex = null;
        if (stateRaw instanceof EntityRenderState stateMeanwhile && stack != null) {
            Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
            String slot = equippable == null ? null : slotName(equippable.slot());
            if (slot != null && stateMeanwhile.entityType != null) {
                String type = BuiltInRegistries.ENTITY_TYPE.getKey(stateMeanwhile.entityType).toString();
                tex = VisualArmorLayer.textureFor(type, slot);
            }
        }
        if (tex == null) {
            renderer.renderLayers(layerType, key, model, stateRaw, stack, pose, collector, a, b);
            return;
        }
        Identifier id = LooseResourceLoader.lookup(tex);
        if (id == null) {
            renderer.renderLayers(layerType, key, model, stateRaw, stack, pose, collector, a, b);
            return;
        }
        renderer.renderLayers(layerType, key, model, stateRaw, stack, pose, collector, a, id, b, b);
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
