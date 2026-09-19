package com.opendreamcore.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.opendreamcore.client.visual.VisualNameTags;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 头顶名牌（HeadTag）：实体名字渲染时查规则，命中就自绘名牌（billboard
 * 背景条 + 文字）顶掉原版名字。没规则原版照旧。
 * renderNameTag 声明在 EntityRenderer（父类），目标就盯它。
 */
@Mixin(EntityRenderer.class)
public abstract class MixinNameTag {

    @Inject(method = "renderNameTag(Lnet/minecraft/world/entity/Entity;"
            + "Lnet/minecraft/network/chat/Component;"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;IF)V",
            at = @At("HEAD"), cancellable = true)
    private void opendreamcore$replaceNameTag(Entity entity, Component displayName,
                                              PoseStack pose, MultiBufferSource buffer,
                                              int light, float partialTick, CallbackInfo ci) {
        String type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        VisualNameTags.TagStyle style = VisualNameTags.styleFor(type,
                entity.getName().getString());
        if (style == null) {
            return;
        }
        drawTag(entity, displayName, pose, buffer, light, style);
        ci.cancel();
    }

    /** 名牌自绘：实体头顶 billboard，背景条 + 文字（样式取规则）。 */
    private static void drawTag(Entity entity, Component name, PoseStack pose,
                                MultiBufferSource buffer, int light,
                                VisualNameTags.TagStyle style) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        pose.pushPose();
        pose.translate(0.0F, entity.getBbHeight() + 0.5F, 0.0F);
        pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        float s = 0.025F;
        pose.scale(-s, -s, s);
        String text = name.getString();
        int tw = font.width(text);
        int pad = 3;
        int fw = tw + pad * 2;
        int fh = font.lineHeight + pad * 2;
        var matrix = pose.last().pose();
        // 背景条：半透明色块
        var quad = buffer.getBuffer(RenderType.gui());
        int bg = style.bgColor();
        float a = ((bg >>> 24) & 0xFF) / 255.0F;
        float r = ((bg >>> 16) & 0xFF) / 255.0F;
        float g = ((bg >>> 8) & 0xFF) / 255.0F;
        float b = (bg & 0xFF) / 255.0F;
        float left = -fw / 2.0F;
        float top = -fh / 2.0F;
        quad.addVertex(matrix, left, top, 0.0F).setColor(r, g, b, a);
        quad.addVertex(matrix, left, top + fh, 0.0F).setColor(r, g, b, a);
        quad.addVertex(matrix, left + fw, top + fh, 0.0F).setColor(r, g, b, a);
        quad.addVertex(matrix, left + fw, top, 0.0F).setColor(r, g, b, a);
        // 文字（白底黑边配色：规则文字色）
        font.drawInBatch(text, -tw / 2.0F, -font.lineHeight / 2.0F,
                style.textColor(), false, matrix, buffer, Font.DisplayMode.NORMAL, 0, light);
        pose.popPose();
    }
}