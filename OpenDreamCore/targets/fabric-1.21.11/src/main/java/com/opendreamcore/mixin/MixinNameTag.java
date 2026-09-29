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
import net.minecraft.world.entity.Entity;
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

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Entity;"
            + "Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V",
            at = @At("HEAD"))
    private void opendreamcore$captureEntityType(Entity entity, EntityRenderState state,
                                                 float partialTick, CallbackInfo ci) {
        VisualNameTags.setCurrentEntity(entity); // state 体系：实体经这里传给 hitFor
    }

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
        Entity current = VisualNameTags.currentEntity();
        VisualNameTags.Hit hit = current == null ? null : VisualNameTags.hitFor(current);
        if (hit == null) {
            return; // 没规则命中，原版名牌照旧
        }
        // 名字条一律就地画（HEAD 起就是这条路径）：整页内容只**叠加**在名牌之上。
        // 以前只在 fullHud=false 时画 —— 整页路径一旦构建/布局/上屏任一步失败
        // （页面元素没写世界坐标 hologram、布局落在画布外、渲染时机不对…），
        // 原版名牌已被 cancel、页面又画不出来，名牌就整体消失。实机回归的根因。
        drawStyleTag(state, pose, storage, camera, hit.style());
        ci.cancel();
    }

    /** 纯样式名牌：文字 + 背景 + 描边三色直接交给 SubmitNodeStorage。 */
    private static void drawStyleTag(EntityRenderState state, PoseStack pose,
                                     SubmitNodeStorage storage, CameraRenderState camera,
                                     VisualNameTags.TagStyle style) {
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
    }
}
