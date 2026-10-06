package com.opendreamcore.mixin;

import com.opendreamcore.model.bake.BakedModel;
import com.opendreamcore.model.bake.ModelAssetStore;
import com.opendreamcore.model.bake.ModelLog;

import com.mojang.blaze3d.vertex.PoseStack;
import com.opendreamcore.client.model.EntityModelReplacer;
import com.opendreamcore.client.model.GenModelRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * gen-1.21.11/26.1.2（submit/RenderState 管线）实体模型替换。
 *
 * <p>extractRenderState(TAIL) 解析规则并把 Hit 暂存到线程内配对桥（渲染线程顺序
 * extract→submit 执行，配对稳定）；submit(HEAD) 命中 → 用渲染器自己的
 * getRenderType(state,...) 取实体渲染类型，即时绘制我们的模型后取消原版 submit。
 * 静态 MVP：不处理图层（盔甲/手持），随取消一并跳过；姿态只做 180 度朝向约定。
 */
@Mixin(LivingEntityRenderer.class)
public abstract class OdcEntityModelMixin<T extends LivingEntity, S extends LivingEntityRenderState> {

    /** extract→submit 的线程内配对桥。 */
    private static final ThreadLocal<EntityModelReplacer.Hit> PENDING =
            new ThreadLocal<EntityModelReplacer.Hit>();

    @Shadow
    protected abstract RenderType getRenderType(S state, boolean bodyVisible, boolean translucent, boolean glowing);

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V",
            at = @At("TAIL"))
    private void odc$extract(Entity entity, EntityRenderState state, float partialTick, CallbackInfo ci) {
        EntityModelReplacer.Hit hit = null;
        try {
            hit = EntityModelReplacer.resolve(GenModelRenderer.typeId(entity), GenModelRenderer.nameOf(entity));
        } catch (Throwable ignored) {
        }
        PENDING.set(hit);
    }

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;net.minecraft.client.renderer.state.level.CameraRenderState;)V",
            at = @At("HEAD"), cancellable = true)
    private void odc$submit(S state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
        EntityModelReplacer.Hit hit = PENDING.get();
        PENDING.remove();
        if (hit == null) {
            return;
        }
        try {
            RenderType rt = getRenderType(state, false, false, false);
            if (rt == null) {
                ci.cancel();
                return;
            }
            MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
            poseStack.pushPose();
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0f));
            GenModelRenderer.render(poseStack, buffers, rt, hit, 0xF000F0);
            poseStack.popPose();
            buffers.endBatch(rt);
            ci.cancel();
        } catch (Throwable t) {
            com.opendreamcore.model.bake.ModelLog.warn("实体模型替换失败(submit): " + t);
        }
    }
}
