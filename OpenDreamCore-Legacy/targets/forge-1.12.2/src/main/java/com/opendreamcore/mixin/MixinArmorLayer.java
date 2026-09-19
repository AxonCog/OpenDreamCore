package com.opendreamcore.mixin;

import com.opendreamcore.client.LooseTextureLoader;
import com.opendreamcore.client.visual.LegacyVisualArmorLayer;
import net.minecraft.client.renderer.entity.layers.LayerArmorBase;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 盔甲层贴图覆写（1.12.2）：LayerArmorBase.getArmorResource 出点拦下，
 * 按实体+槽位命中规则换贴图路径。
 */
@Mixin(LayerArmorBase.class)
public abstract class MixinArmorLayer {

    @Inject(method = "getArmorResource(Lnet/minecraft/entity/Entity;"
            + "Lnet/minecraft/item/ItemStack;"
            + "Lnet/minecraft/inventory/EntityEquipmentSlot;Ljava/lang/String;)"
            + "Lnet/minecraft/util/ResourceLocation;",
            at = @At("HEAD"), cancellable = true)
    private void opendreamcore$armor(Entity entity, ItemStack stack, EntityEquipmentSlot slot,
                                     String type, CallbackInfoReturnable<ResourceLocation> cir) {
        if (entity == null) {
            return;
        }
        String typeId = EntityList.getEntityString(entity);
        String tex = LegacyVisualArmorLayer.textureFor(typeId == null ? "" : typeId, slotName(slot));
        if (tex == null) {
            return;
        }
        cir.setReturnValue(new ResourceLocation(LooseTextureLoader.lookup(tex)));
    }

    private static String slotName(EntityEquipmentSlot slot) {
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