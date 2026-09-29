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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HumanoidArmorLayer.class)
public abstract class MixinArmorLayer {

    private static final ThreadLocal<String> ODC_TYPE = new ThreadLocal<>();
    private static final ThreadLocal<String> ODC_SLOT = new ThreadLocal<>();

    /** 记下本次护甲渲染的实体类型与槽位，供后续贴图查询使用。 */
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

    /**
     * 护甲贴图定位返回前换成自定义贴图。
     * 这里挂在返回值上而不是替换内部的缓存查询调用：只要目标方法签名不变就能注入成功，
     * 不受方法体里调用指令写法变化影响；没有对应自定义贴图时原值原样返回。
     */
    @Inject(method = "getArmorLocation(Lnet/minecraft/world/item/ArmorItem;ZLjava/lang/String;)"
            + "Lnet/minecraft/resources/ResourceLocation;",
            at = @At("RETURN"), cancellable = true)
    private void opendreamcore$swap(ArmorItem item, boolean inner, String suffix,
                                    CallbackInfoReturnable<ResourceLocation> cir) {
        String type = ODC_TYPE.get();
        String slot = ODC_SLOT.get();
        if (type == null || slot == null) {
            return;
        }
        String tex = VisualArmorLayer.textureFor(type, slot);
        if (tex == null) {
            return;
        }
        ResourceLocation ours = LooseResourceLoader.lookup(tex);
        if (ours != null) {
            cir.setReturnValue(ours);
        }
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
