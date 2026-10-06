package com.opendreamcore.mixin;

import com.opendreamcore.model.bake.BakedModel;
import com.opendreamcore.model.bake.ModelAssetStore;
import com.opendreamcore.model.bake.ModelLog;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.opendreamcore.client.model.EntityModelReplacer;
import com.opendreamcore.client.model.GenModelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * gen-1.21.4-1.21.8 实体模型替换：命中规则 → 画自己的模型并取消原版身体渲染。
 *
 * <p>1.21.4 起实体渲染改 RenderState 架构，LivingEntityRenderer 已没有
 * (LivingEntity, FF, ...) 入口；改挂 EntityRenderDispatcher.render(Entity, DDD, F, ...)，
 * 这是 1.21.4/1.21.8 仍然存在、且每实体每帧必经的入口。HEAD 处 PoseStack 尚未
 * translate 到实体位置，自绘前先 translate(x, y, z)（z 减 0.01 抢层与 1.21.1 版一致）。
 * 朝向用 yBodyRot 插值复刻（等价于原版身体 yaw 段 180 - yaw）。
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class OdcEntityModelMixin {

    @Inject(method = "render(Lnet/minecraft/world/entity/Entity;DDDF"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), cancellable = true)
    private void odc$modelReplace(Entity entity, double x, double y, double z,
                                  float partialTick, PoseStack poseStack,
                                  MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        if (!(entity instanceof LivingEntity living)) {
            return;
        }
        try {
            EntityModelReplacer.Hit hit = EntityModelReplacer.resolve(
                    GenModelRenderer.typeId(living), GenModelRenderer.nameOf(living));
            if (hit == null) {
                return;
            }
            float yaw = Mth.rotLerp(partialTick, living.yBodyRotO, living.yBodyRot);
            poseStack.pushPose();
            poseStack.translate(x, y, z);
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - Mth.wrapDegrees(yaw)));
            GenModelRenderer.render(poseStack, buffer, hit, packedLight);
            poseStack.popPose();
            ci.cancel();
        } catch (Throwable t) {
            ModelLog.warn("实体模型替换失败: " + t);
        }
    }
}
