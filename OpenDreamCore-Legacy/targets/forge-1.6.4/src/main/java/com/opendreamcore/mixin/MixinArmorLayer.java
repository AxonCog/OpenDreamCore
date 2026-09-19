package com.opendreamcore.mixin;

import com.opendreamcore.client.LooseTextureLoader;
import com.opendreamcore.client.visual.LegacyVisualArmorLayer;
import net.minecraft.client.renderer.entity.RenderBiped;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 盔甲层贴图覆写（1.6.4）：RenderBiped.getArmorResource 出点拦下，按
 * 实体+槽位（0帽1胸2腿3鞋）命中规则换贴图路径。方法本身是 static。
 */
@Mixin(RenderBiped.class)
public abstract class MixinArmorLayer {

    @Inject(method = "getArmorResource(Lnet/minecraft/entity/Entity;"
            + "Lnet/minecraft/item/ItemStack;ILjava/lang/String;)"
            + "Lnet/minecraft/util/ResourceLocation;",
            at = @At("HEAD"), cancellable = true)
    private static void opendreamcore$armor(Entity entity, ItemStack stack, int slot,
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

    private static String slotName(int slot) {
        switch (slot) {
            case 0:
                return "helmet";
            case 1:
                return "chest";
            case 2:
                return "legs";
            case 3:
                return "feet";
            default:
                return "layer";
        }
    }
}