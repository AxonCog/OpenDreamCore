package com.opendreamcore.mixin;

import com.opendreamcore.client.resources.LooseResourceLoader;
import com.opendreamcore.client.visual.VisualFontReplace;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.1.2 全局字符替换：GUI 文本全走 GuiGraphicsExtractor.text，mixin 拆段——
 * 普通段递归回原 text，命中字符用 9 参 blit（直接绑 Identifier 纹理画 quad）。
 * 替换贴图走 LooseResourceLoader 注册的 opendreamcore:loose/* 纹理。
 */
@Mixin(GuiGraphicsExtractor.class)
public abstract class MixinGuiText {

    @Inject(method = "text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V",
            at = @At("HEAD"), cancellable = true)
    private void opendreamcore$replaceGlyph(Font font, String text, int x, int y, int color,
                                            CallbackInfo ci) {
        if (text == null || text.isEmpty() || !VisualFontReplace.hasAny() || !containsReplaced(text)) {
            return;
        }
        float fx = x;
        int i = 0;
        StringBuilder seg = new StringBuilder();
        GuiGraphicsExtractor self = (GuiGraphicsExtractor) (Object) this;
        while (i < text.length()) {
            char c = text.charAt(i);
            VisualFontReplace.CharGlyph g = VisualFontReplace.glyphFor(c);
            if (g == null) {
                seg.append(c);
                i++;
                continue;
            }
            if (seg.length() > 0) {
                self.text(font, seg.toString(), (int) fx, y, color);
                seg.setLength(0);
            }
            drawGlyph(g, (int) fx, y, self);
            fx += g.fontWidth() + 1.0F;
            i++;
        }
        if (seg.length() > 0) {
            self.text(font, seg.toString(), (int) fx, y, color);
        }
        ci.cancel();
    }

    private static boolean containsReplaced(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (VisualFontReplace.glyphFor(text.charAt(i)) != null) {
                return true;
            }
        }
        return false;
    }

    /** 9 参 blit：直接绑 Identifier 纹理画 quad，uv 传 0-1 比例。 */
    private static void drawGlyph(VisualFontReplace.CharGlyph g, int x, int y, GuiGraphicsExtractor gge) {
        Identifier rl = LooseResourceLoader.lookup(g.texture());
        if (rl == null) {
            return;
        }
        int w = Math.max(1, g.frameW());
        int h = Math.max(1, g.frameH());
        LooseResourceLoader.Size size = LooseResourceLoader.sizeOf(g.texture());
        float texW = size != null && size.width() > 0 ? size.width() : Math.max(1, g.frameW());
        float texH = size != null && size.height() > 0 ? size.height() : Math.max(1, g.frameH());
        float u0 = g.u() / texW;
        float v0 = g.v() / texH;
        float u1 = (g.u() + g.frameW()) / texW;
        float v1 = (g.v() + g.frameH()) / texH;
        gge.blit(rl, x, y, w, h, u0, v0, u1, v1);
    }
}