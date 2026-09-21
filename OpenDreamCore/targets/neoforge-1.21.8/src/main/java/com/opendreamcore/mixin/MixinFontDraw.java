package com.opendreamcore.mixin;

import com.opendreamcore.client.visual.GlyphRenderer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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

    /** 委托 GlyphRenderer 拆段绘制（普通段原版、命中段贴图 quad、贴图缺失回退原版）。 */
    private boolean renderReplaced(String text, float x, float y, int color, boolean shadow,
                                   Matrix4f matrix, MultiBufferSource buffer, Font.DisplayMode mode,
                                   int colorBg, int packedLight) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        return com.opendreamcore.client.visual.GlyphRenderer.renderBuffer(
                (Font) (Object) this, text, x, y, color, shadow, matrix, buffer, mode, colorBg, packedLight);
    }
}