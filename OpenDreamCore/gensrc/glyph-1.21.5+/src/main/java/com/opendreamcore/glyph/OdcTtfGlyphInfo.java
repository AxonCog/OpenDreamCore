package com.opendreamcore.glyph;

import com.mojang.blaze3d.font.GlyphInfo;
import com.opendreamcore.client.TtfGlyphSource;

/**
 * 全局 TTF 字形信息（1.21.11 / 26.1.2 两代共用）。
 *
 * 字体来源是共享层的字模缓存（软渲染已在内存），本类只把字体自身的推进宽度交给排版，
 * 避免用位图宽推进导致字距忽宽忽窄。字形对象在字形缓存层产生。
 */
public final class OdcTtfGlyphInfo implements GlyphInfo {

    private final TtfGlyphSource.Glyph glyph;

    public OdcTtfGlyphInfo(TtfGlyphSource.Glyph glyph) {
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
}
