package com.opendreamcore.glyph;

import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.font.SheetGlyphInfo;
import com.opendreamcore.client.TtfGlyphSource;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.gui.font.glyphs.SpecialGlyphs;

import java.util.function.Function;

/**
 * 全局字体字形信息（1.21.8 代）。
 *
 * 字模来源是共享层的字体光栅缓存，本类只把字体自身的度量交给排版、把位图交给绘制，
 * 不自造宽度也不改基线。取不到字模就返回 null，由调用方放行原版字体。
 */
public final class OdcTtfGlyphInfo implements GlyphInfo {

    private final int codePoint;
    private final TtfGlyphSource.Glyph glyph;

    public OdcTtfGlyphInfo(int codePoint, TtfGlyphSource.Glyph glyph) {
        this.codePoint = codePoint;
        this.glyph = glyph;
    }

    /** 字模（供字形缓存构造字形对象时复用，避免重复光栅）。 */
    public TtfGlyphSource.Glyph glyph() {
        return glyph;
    }

    @Override
    public float getAdvance() {
        return Math.max(1.0F, glyph.advance());
    }

    @Override
    public BakedGlyph bake(Function<SheetGlyphInfo, BakedGlyph> function) {
        BakedGlyph baked = OdcGlyphCache.ttf(codePoint, glyph);
        if (baked != null) {
            return baked;
        }
        // 同上：字模表面构造失败也要给出可绘制的原版字形，不能把 null 交给调用方
        return SpecialGlyphs.MISSING.bake(function);
    }
}
