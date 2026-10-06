package com.opendreamcore.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.opendreamcore.client.visual.VisualNameTags;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 规则匹配前置到"实体被渲染"这一层（EntityRenderDispatcher.render HEAD，
 * 每个进入渲染的实体都过，不管它有没有自定义名字）。原来挂在
 * EntityRenderer.renderNameTag 上——原版只在"该画名牌"时才调它
 * （自定义名/准星指向），没名字的生物根本不走，规则全部失活
 * （2026-10-03 实机诊断：hook 30 秒只调 1 次）。名牌接管/样式仍由
 * MixinNameTag 读本帧匹配结果，匹配每实体每帧只做一次。
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class MixinEntityDispatcher {

    @Inject(method = "render(Lnet/minecraft/world/entity/Entity;DDDF"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"))
    private void opendreamcore$claimRenderedEntity(Entity entity, double x, double y, double z,
                                                   float partialTick, PoseStack pose,
                                                   MultiBufferSource buffer, int light,
                                                   CallbackInfo ci) {
        VisualNameTags.claimFor(entity);
    }
}
