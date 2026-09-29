package com.opendreamcore.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.opendreamcore.client.visual.VisualNameTags;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 头顶名牌（HeadTag）：实体名字渲染时查规则，命中就自绘名牌（billboard
 * 背景条 + 文字）顶掉原版名字。没规则原版照旧。
 * renderNameTag 声明在 EntityRenderer（javap 实测），目标就盯它。
 */
@Mixin(EntityRenderer.class)
public abstract class MixinNameTag {

    @Inject(method = "renderNameTag(Lnet/minecraft/world/entity/Entity;"
            + "Lnet/minecraft/network/chat/Component;"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), cancellable = true)
    private void opendreamcore$replaceNameTag(Entity entity, Component displayName,
                                              PoseStack pose, MultiBufferSource buffer,
                                              int light, CallbackInfo ci) {
        VisualNameTags.Hit hit = VisualNameTags.hitFor(entity);
        if (hit == null) {
            return; // 没规则命中，原版名牌照旧
        }
        // 名字条一律就地画（HEAD 起就是这条路径）：整页内容只**叠加**在名牌之上。
        // 以前只在 fullHud=false 时画 —— 整页路径一旦构建/布局/上屏任一步失败
        // （页面元素没写世界坐标 hologram、布局落在画布外、渲染时机不对…），
        // 原版名牌已被 cancel、页面又画不出来，名牌就整体消失。实机回归的根因。
        drawTag(entity, displayName, pose, buffer, light, hit.style());
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
        // 背景条：半透明色块。
        // 用世界语义的文本背景类型（textBackgroundSeeThrough），不能用 RenderType.gui()。
        // gui() 是 GUI 语义：不写深度、不带雾、按 GUI 裁剪与光照规则处理；在实体渲染阶段
        // 用它画世界几何，开光影时（Iris/Oculus 按渲染类型把几何分派进各自的 gbuffer 程序）
        // 会被归进 GUI 类而非世界类，名牌背景就会错位、穿透或整体消失。两者顶点格式都是
        // POSITION_COLOR，所以下面顶点写法不用改，只换类型。
        var quad = buffer.getBuffer(RenderType.textBackgroundSeeThrough());
        int bg = style.bgColor();
        float a = ((bg >>> 24) & 0xFF) / 255.0F;
        float r = ((bg >>> 16) & 0xFF) / 255.0F;
        float g = ((bg >>> 8) & 0xFF) / 255.0F;
        float b = (bg & 0xFF) / 255.0F;
        float left = -fw / 2.0F;
        float top = -fh / 2.0F;
        // 1.20.1 的 VertexConsumer 是老链 vertex/color/endVertex（1.21 才改 addVertex/setColor）
        quad.vertex(matrix, left, top, 0.0F).color(r, g, b, a).endVertex();
        quad.vertex(matrix, left, top + fh, 0.0F).color(r, g, b, a).endVertex();
        quad.vertex(matrix, left + fw, top + fh, 0.0F).color(r, g, b, a).endVertex();
        quad.vertex(matrix, left + fw, top, 0.0F).color(r, g, b, a).endVertex();
        // 文字（白底黑边配色：规则文字色）
        font.drawInBatch(text, -tw / 2.0F, -font.lineHeight / 2.0F,
                style.textColor(), false, matrix, buffer, Font.DisplayMode.NORMAL, 0, light);
        pose.popPose();
    }
}