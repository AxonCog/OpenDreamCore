package com.opendreamcore.mixin;

import com.opendreamcore.client.resources.LooseResourceLoader;
import com.opendreamcore.client.visual.VisualFontReplace;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.21.8+ 全局字符替换：这代字体管线两段化了（prepareText 收集 → 统一渲染），
 * 老的 StringRenderOutput.accept 被 PreparedTextBuilder 顶掉。但 drawInBatch
 * 还是唯一渲染收口（带 Matrix4f + MultiBufferSource + packedLight），
 * 直接在这拆段：普通段递归回原方法，命中字符画彩色贴图 quad。
 *
 * 样式降级说明：FormattedCharSequence 转 String 后会丢内联样式对象，
 * §x 颜色码不受影响；纯文本路径（UI/书/箱子名）零损失。
 */
@Mixin(Font.class)
public abstract class MixinFontDraw {

    @Inject(method = "drawInBatch(Ljava/lang/String;FFIZLorg/joml/Matrix4f;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;"
            + "Lnet/minecraft/client/gui/Font$DisplayMode;II)V",
            at = @At("HEAD"), cancellable = true)
    private void opendreamcore$replaceGlyph(String text, float x, float y, int color, boolean shadow,
                                            Matrix4f matrix, MultiBufferSource buffer, Font.DisplayMode mode,
                                            int colorBg, int packedLight, CallbackInfo ci) {
        if (renderReplaced(text, x, y, color, shadow, matrix, buffer, mode, colorBg, packedLight)) {
            ci.cancel();
        }
    }

    /**
     * 聊天/书籍/按钮走的 FormattedCharSequence 重载：1.21.8 聊天不走 String 重载，
     * 不在这拦聊天里的命名字符永远是原版字形。先摊平成纯文本再复用同一套替换绘制。
     */
    @Inject(method = "drawInBatch(Lnet/minecraft/util/FormattedCharSequence;FFIZLorg/joml/Matrix4f;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;"
            + "Lnet/minecraft/client/gui/Font$DisplayMode;II)V",
            at = @At("HEAD"), cancellable = true)
    private void opendreamcore$replaceGlyphSeq(net.minecraft.util.FormattedCharSequence text, float x, float y,
                                               int color, boolean shadow, Matrix4f matrix,
                                               MultiBufferSource buffer, Font.DisplayMode mode,
                                               int colorBg, int packedLight, CallbackInfo ci) {
        if (text == null) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        text.accept((index, style, codePoint) -> {
            sb.appendCodePoint(codePoint);
            return true;
        });
        if (renderReplaced(sb.toString(), x, y, color, shadow, matrix, buffer, mode, colorBg, packedLight)) {
            ci.cancel();
        }
    }

    /** Component 重载同款兜底（牌子/原版按钮等直接传组件的路径）。 */
    @Inject(method = "drawInBatch(Lnet/minecraft/network/chat/Component;FFIZLorg/joml/Matrix4f;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;"
            + "Lnet/minecraft/client/gui/Font$DisplayMode;II)V",
            at = @At("HEAD"), cancellable = true)
    private void opendreamcore$replaceGlyphComponent(net.minecraft.network.chat.Component text, float x, float y,
                                                     int color, boolean shadow, Matrix4f matrix,
                                                     MultiBufferSource buffer, Font.DisplayMode mode,
                                                     int colorBg, int packedLight, CallbackInfo ci) {
        if (renderReplaced(text == null ? null : text.getString(), x, y, color, shadow,
                matrix, buffer, mode, colorBg, packedLight)) {
            ci.cancel();
        }
    }

    /**
     * 真替换：普通段递归回原方法（这段不含命名字符，不会再次进本方法），
     * 命中字符画彩色贴图 quad。返回 true = 本次绘制已被接管，调用方该 cancel。
     */
    private boolean renderReplaced(String text, float x, float y, int color, boolean shadow,
                                   Matrix4f matrix, MultiBufferSource buffer, Font.DisplayMode mode,
                                   int colorBg, int packedLight) {
        if (text == null || text.isEmpty() || !VisualFontReplace.hasAny() || !containsReplaced(text)) {
            return false;
        }
        float fx = x;
        int i = 0;
        StringBuilder seg = new StringBuilder();
        while (i < text.length()) {
            char c = text.charAt(i);
            VisualFontReplace.CharGlyph g = VisualFontReplace.glyphFor(c);
            if (g == null) {
                seg.append(c);
                i++;
                continue;
            }
            if (seg.length() > 0) {
                ((Font) (Object) this).drawInBatch(seg.toString(), fx, y, color, shadow,
                        matrix, buffer, mode, colorBg, packedLight);
                seg.setLength(0);
            }
            drawGlyph(g, fx, y, matrix, buffer, packedLight);
            fx += g.fontWidth() + (shadow ? 1.0F : 0.0F);
            i++;
        }
        if (seg.length() > 0) {
            ((Font) (Object) this).drawInBatch(seg.toString(), fx, y, color, shadow,
                    matrix, buffer, mode, colorBg, packedLight);
        }
        return true;
    }

    private static boolean containsReplaced(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (VisualFontReplace.glyphFor(text.charAt(i)) != null) {
                return true;
            }
        }
        return false;
    }

    /** 彩色贴图 quad：uv 按实际贴图像素归一化（与 TextElements.blit 同语义）。 */
    private static void drawGlyph(VisualFontReplace.CharGlyph g, float x, float y,
                                  Matrix4f matrix, MultiBufferSource buffer, int packedLight) {
        ResourceLocation rl = LooseResourceLoader.lookup(g.texture());
        if (rl == null) {
            return;
        }
        var consumer = buffer.getBuffer(RenderType.text(rl));
        float w = Math.max(1, g.frameW());
        float h = Math.max(1, g.frameH());
        LooseResourceLoader.Size size = LooseResourceLoader.sizeOf(g.texture());
        float texW = size != null && size.width() > 0 ? size.width() : Math.max(1, g.frameW());
        float texH = size != null && size.height() > 0 ? size.height() : Math.max(1, g.frameH());
        float u0 = g.u() / texW;
        float v0 = g.v() / texH;
        float u1 = (g.u() + g.frameW()) / texW;
        float v1 = (g.v() + g.frameH()) / texH;
        float z = 0.0F;
        consumer.addVertex(matrix, x, y, z).setUv(u0, v0).setColor(255, 255, 255, 255).setLight(packedLight);
        consumer.addVertex(matrix, x, y + h, z).setUv(u0, v1).setColor(255, 255, 255, 255).setLight(packedLight);
        consumer.addVertex(matrix, x + w, y + h, z).setUv(u1, v1).setColor(255, 255, 255, 255).setLight(packedLight);
        consumer.addVertex(matrix, x + w, y, z).setUv(u1, v0).setColor(255, 255, 255, 255).setLight(packedLight);
    }
}