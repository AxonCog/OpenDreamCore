package com.opendreamcore.mixin;

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
import net.minecraft.world.item.ArmorItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.function.Function;

@Mixin(HumanoidArmorLayer.class)
public abstract class MixinArmorLayer {

    private static final ThreadLocal<String> ODC_TYPE = new ThreadLocal<>();
    private static final ThreadLocal<String> ODC_SLOT = new ThreadLocal<>();

    @Inject(method = "renderArmorPiece(Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;"
            + "Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;"
            + "ILnet/minecraft/client/model/HumanoidModel;)V", at = @At(value = "HEAD", remap = false))
    private void opendreamcore$ctx(PoseStack pose, MultiBufferSource buffer, LivingEntity entity,
                                  EquipmentSlot slot, int light, HumanoidModel<?> model, CallbackInfo ci) {
        String type = null;
        if (entity != null && entity.getType() != null) {
            type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        }
        ODC_TYPE.set(type);
        ODC_SLOT.set(slotName(slot));
    }

    @Redirect(remap = false, method = "renderModel(Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;I"
            + "Lnet/minecraft/world/item/ArmorItem;Lnet/minecraft/client/model/HumanoidModel;"
            + "ZFFFLjava/lang/String;)V",
            at = @At(value = "INVOKE",
                    target = "Ljava/util/Map;computeIfAbsent(Ljava/lang/String;Ljava/util/function/Function;)Ljava/lang/Object;", remap = false))
    private Object opendreamcore$swap(Map<String, ResourceLocation> cache, String key,
                                     Function<String, ResourceLocation> fn) {
        String tex = null;
        String type = ODC_TYPE.get();
        String slot = ODC_SLOT.get();
        if (type != null && slot != null) {
            tex = VisualArmorLayer.textureFor(type, slot);
        }
        if (tex == null) {
            return cache.computeIfAbsent(key, fn);
        }
        ResourceLocation ours = LooseResourceLoader.lookup(tex);
        return ours != null ? ours : cache.computeIfAbsent(key, fn);
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
