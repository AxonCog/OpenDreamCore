package com.opendreamcore.mixin;

import com.opendreamcore.client.LooseTextureLoader;
import com.opendreamcore.client.visual.LegacyVisualArmorLayer;
import net.minecraft.client.renderer.entity.layers.BipedArmorLayer;
import net.minecraft.entity.Entity;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 盔甲层贴图覆写（1.16.5）：BipedArmorLayer.getArmorResource 出点拦下，按
 * 实体类型+槽位命中 ArmorLayer 规则就换自定义贴图路径。换路径不碰渲染，
 * 冲突最省。
 */
@Mixin(BipedArmorLayer.class)
public abstract class MixinArmorLayer {

    @Inject(method = "getArmorResource(Lnet/minecraft/entity/Entity;"
            + "Lnet/minecraft/item/ItemStack;"
            + "Lnet/minecraft/inventory/EquipmentSlotType;Ljava/lang/String;)"
            + "Lnet/minecraft/util/ResourceLocation;",
            at = @At("HEAD"), cancellable = true)
    private void opendreamcore$armor(Entity entity, ItemStack stack, EquipmentSlotType slot,
                                     String type, CallbackInfoReturnable<ResourceLocation> cir) {
        if (entity == null) {
            return;
        }
        String typeId = ForgeRegistries.ENTITIES.getKey(entity.getType()) == null ? ""
                : ForgeRegistries.ENTITIES.getKey(entity.getType()).toString();
        String tex = LegacyVisualArmorLayer.textureFor(typeId, slotName(slot));
        if (tex == null) {
            return;
        }
        String loose = LooseTextureLoader.lookup(tex);
        cir.setReturnValue(new ResourceLocation(loose));
    }

    private static String slotName(EquipmentSlotType slot) {
        switch (slot) {
            case HEAD:
                return "helmet";
            case CHEST:
                return "chest";
            case LEGS:
                return "legs";
            case FEET:
                return "feet";
            default:
                return "layer";
        }
    }
}