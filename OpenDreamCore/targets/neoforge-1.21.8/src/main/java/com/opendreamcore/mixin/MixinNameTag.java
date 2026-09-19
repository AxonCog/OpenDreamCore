package com.opendreamcore.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.opendreamcore.client.visual.VisualNameTags;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 头顶名牌（1.21.8+）：这代实体渲染走 state 体系，renderNameTag 收
 * EntityRenderState（entityType 在 state 里）。命中规则就自绘顶掉，
 * 背景色交给 Font.drawInBatch 的背景参数，不用单独 quad。
 */
@Mixin(EntityRenderer.class)
public abstract class MixinNameTag {

    @Inject(method = "renderNameTag(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;"
            + "Lnet/minecraft/network/chat/Component;"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), cancellable = true)
    private void opendreamcore$replaceNameTag(EntityRenderState state, Component displayName,
                                              PoseStack pose, MultiBufferSource buffer,
                                              int light, CallbackInfo ci) {
        String type = state.entityType == null ? ""
                : BuiltInRegistries.ENTITY_TYPE.getKey(state.entityType).toString();
        VisualNameTags.TagStyle style = VisualNameTags.styleFor(type, displayName.getString());
        if (style == null) {
            return;
        }
        drawTag(state, displayName, pose, buffer, light, style);
        ci.cancel();
    }

    private static void drawTag(EntityRenderState state, Component name, PoseStack pose,
                                MultiBufferSource buffer, int light,
                                VisualNameTags.TagStyle style) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        pose.pushPose();
        pose.translate(0.0F, (float) (state.y + 0.5F), 0.0F);
        pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        float s = 0.025F;
        pose.scale(-s, -s, s);
        String text = name.getString();
        int tw = font.width(text);
        var matrix = pose.last().pose();
        font.drawInBatch(text, -tw / 2.0F, -font.lineHeight / 2.0F,
                style.textColor(), false, matrix, buffer, Font.DisplayMode.NORMAL,
                style.bgColor(), light);
        pose.popPose();
    }
}