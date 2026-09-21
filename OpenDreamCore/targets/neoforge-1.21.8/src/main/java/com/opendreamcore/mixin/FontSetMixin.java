package com.opendreamcore.mixin;

import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.font.SheetGlyphInfo;
import com.opendreamcore.client.resources.LooseResourceLoader;
import com.opendreamcore.client.visual.ReplaceFontProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.GlyphRenderTypes;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Function;

/**
 * FontSet 字形层替换：
 * getGlyphInfo（聊天/两段式 prepareText 走这里）命中时返回自定义 GlyphInfo，
 * 其 bake() 产出彩色 BakedGlyph（我们的贴图）；getGlyph（烘培缓存）同样拦截。
 * 原版字体管线全局走替换字形，宽度由 getAdvance 参与排版。
 */
@Mixin(FontSet.class)
public abstract class FontSetMixin {

    @Inject(method = "getGlyphInfo(IZ)Lcom/mojang/blaze3d/font/GlyphInfo;",
            at = @At("HEAD"), cancellable = true)
    private void odc$replaceGlyphInfo(int codePoint, boolean random, CallbackInfoReturnable<GlyphInfo> cir) {
        if (!ReplaceFontProvider.usable(codePoint)) {
            return;
        }
        cir.setReturnValue(new OdcGlyphInfo(ReplaceFontProvider.get(codePoint)));
    }

    @Inject(method = "getGlyph(I)Lnet/minecraft/client/gui/font/glyphs/BakedGlyph;",
            at = @At("HEAD"), cancellable = true)
    private void odc$replaceGlyph(int codePoint, CallbackInfoReturnable<BakedGlyph> cir) {
        if (!ReplaceFontProvider.usable(codePoint)) {
            return;
        }
        Object cached = ReplaceFontProvider.cachedBaked(codePoint);
        if (cached instanceof BakedGlyph bg) {
            cir.setReturnValue(bg);
            return;
        }
        BakedGlyph baked = bakeGlyph(ReplaceFontProvider.get(codePoint));
        if (baked != null) {
            ReplaceFontProvider.cacheBaked(codePoint, baked);
            cir.setReturnValue(baked);
        }
    }

    /** 自定义 GlyphInfo：宽度取替换字形宽度，bake 产出自定义 BakedGlyph。 */
    static final class OdcGlyphInfo implements GlyphInfo {
        private final ReplaceFontProvider.ReplaceFontGlyph g;

        OdcGlyphInfo(ReplaceFontProvider.ReplaceFontGlyph g) {
            this.g = g;
        }

        @Override
        public float getAdvance() {
            return g.width();
        }

        @Override
        public BakedGlyph bake(Function<SheetGlyphInfo, BakedGlyph> function) {
            return bakeGlyph(g);
        }
    }

    /** 自定义 BakedGlyph：覆写 renderType 返回我们贴图的 RenderType（DreamCore ReplaceFontGlyphRenderer 同款思路）——
     *  GlyphVisitor 用 glyph.renderType(mode) 取 buffer，不覆写则用默认字体图集纹理，自定义贴图渲染空白。 */
    static final class OdcBakedGlyph extends BakedGlyph {
        private final ResourceLocation rl;
        private final com.opendreamcore.client.GifPlayer gif;

        OdcBakedGlyph(GlyphRenderTypes types, com.mojang.blaze3d.textures.GpuTextureView view,
                      float left, float top, float right, float bottom,
                      float u0, float v0, float u1, float v1, ResourceLocation rl,
                      String srcFile, com.opendreamcore.client.GifPlayer gif) {
            // 1.21.8 构造顺序：uv 在前 (u0,u1,v0,v1)、几何在后 (left,right,up,down)
            super(types, view, u0, u1, v0, v1, left, right, top, bottom);
            this.rl = rl;
            this.gif = gif;
        }

        @Override
        public net.minecraft.client.renderer.RenderType renderType(Font.DisplayMode mode) {
            return net.minecraft.client.renderer.RenderType.text(rl);
        }

        @Override
        public net.minecraft.client.renderer.RenderType renderType(Font.DisplayMode mode, boolean shadow) {
            return net.minecraft.client.renderer.RenderType.text(rl);
        }
    }

    private static BakedGlyph bakeGlyph(ReplaceFontProvider.ReplaceFontGlyph g) {
        try {
            // gif 字形 bake 帧表纹理（全帧拼一张，运行期只切 uv）；其它图走静态 lookup
            com.opendreamcore.client.GifPlayer gif = LooseResourceLoader.gifPlayerOf(g.texture());
            ResourceLocation rl = gif != null ? gif.sheetTexture() : null;
            if (rl == null) {
                rl = LooseResourceLoader.lookup(g.texture());
            }
            if (rl == null) {
                return null; // 贴图未解析到：回退原版字形，不吞字
            }
            AbstractTexture tex = Minecraft.getInstance().getTextureManager().getTexture(rl);
            if (tex == null || tex.getTextureView() == null) {
                return null;
            }
            GlyphRenderTypes types = GlyphRenderTypes.createForColorTexture(rl);
            // 字形垂直位置：prepareText 的 y = 行顶，基线 ≈ y+7；图 8px 覆盖基线附近（-1..height-1）
            float top = -1.0F;
            float bottom = g.height() - 1.0F;
            return new OdcBakedGlyph(types, tex.getTextureView(),
                    0.0F, top, g.width(), bottom,
                    0.0F, 0.0F, 1.0F, 1.0F, rl, g.texture(), gif);
        } catch (Throwable t) {
            return null;
        }
    }
}