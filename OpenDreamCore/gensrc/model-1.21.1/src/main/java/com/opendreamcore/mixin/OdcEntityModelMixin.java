package com.opendreamcore.mixin;

import com.opendreamcore.model.bake.BakedModel;
import com.opendreamcore.model.bake.ModelAssetStore;
import com.opendreamcore.model.bake.ModelLog;

import com.mojang.blaze3d.vertex.PoseStack;
import com.opendreamcore.client.model.EntityModelReplacer;
import com.opendreamcore.client.model.GenModelRenderer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * gen-1.21.1 实体模型替换：命中规则 → 画自己的模型并取消原版身体渲染。
 *
 * <p>旋转：HEAD 处原版还没做 setupRotations，这里复刻其 yaw 段（180 - yaw），
 * 与原版身体朝向一致。图层（盔甲/手持）随身体取消一并跳过（静态 MVP 语义）。
 */
@Mixin(LivingEntityRenderer.class)
public abstract class OdcEntityModelMixin<T extends LivingEntity, M extends EntityModel<T>> {

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), cancellable = true)
    private void odc$modelReplace(T entity, float entityYaw, float partialTick, PoseStack poseStack,
                                  MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        try {
            EntityModelReplacer.Hit hit = EntityModelReplacer.resolve(
                    GenModelRenderer.typeId(entity), GenModelRenderer.nameOf(entity));
            if (hit == null) {
                return;
            }
            poseStack.pushPose();
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0f - Mth.wrapDegrees(entityYaw)));
            GenModelRenderer.render(poseStack, buffer, hit, packedLight);
            poseStack.popPose();
            ci.cancel();
        } catch (Throwable t) {
            com.opendreamcore.model.bake.ModelLog.warn("实体模型替换失败: " + t);
        }
    }
}
