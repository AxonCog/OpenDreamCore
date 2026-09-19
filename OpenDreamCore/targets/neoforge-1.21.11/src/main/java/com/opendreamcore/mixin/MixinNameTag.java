package com.opendreamcore.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.opendreamcore.client.visual.VisualNameTags;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 头顶名牌（1.21.11）：这代名字渲染已改名 submitNameTag(state,...)（javap
 * 实测无 renderNameTag）。命中规则就用 SubmitNodeStorage.submitText 自绘
 * （文字+背景+描边三色），顺手 cancel 掉原版；collector 实际是 SubmitNodeStorage。
 */
@Mixin(EntityRenderer.class)
public abstract class MixinNameTag {

    @Inject(method = "submitNameTag(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/SubmitNodeCollector;"
            + "Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
            at = @At("HEAD"), cancellable = true)
    private void opendreamcore$replaceNameTag(EntityRenderState state, PoseStack pose,
                                              SubmitNodeCollector collector,
                                              CameraRenderState camera, CallbackInfo ci) {
        if (state == null || state.entityType == null || state.nameTag == null
                || state.nameTagAttachment == null || !(collector instanceof SubmitNodeStorage storage)) {
            return;
        }
        String type = BuiltInRegistries.ENTITY_TYPE.getKey(state.entityType).toString();
        VisualNameTags.TagStyle style = VisualNameTags.styleFor(type, state.nameTag.getString());
        if (style == null) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        pose.pushPose();
        pose.translate((float) state.nameTagAttachment.x,
                (float) state.nameTagAttachment.y, (float) state.nameTagAttachment.z);
        pose.mulPose(camera.orientation);
        float s = 0.025F;
        pose.scale(-s, -s, s);
        String text = state.nameTag.getString();
        int tw = font.width(text);
        storage.submitText(pose, -tw / 2.0F, -font.lineHeight / 2.0F,
                state.nameTag.getVisualOrderText(), false, Font.DisplayMode.NORMAL,
                0xF000F0, style.textColor(), style.bgColor(), style.borderColor());
        pose.popPose();
        ci.cancel();
    }
}
