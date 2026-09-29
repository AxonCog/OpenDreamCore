package com.opendreamcore.glyph;

import com.mojang.blaze3d.font.GlyphInfo;
import com.opendreamcore.client.glyph.OdcGlyphGeometry;
import com.opendreamcore.client.resources.LooseResourceLoader;
import com.opendreamcore.client.visual.VisualFontReplace;

/**
 * 位图 / GIF 替换字符的字形信息（1.21.11 / 26.1.2 两代共用）。
 *
 * 这两代的字形信息只承担「排版推进宽度」这一件事——绘制实例由字形对象自己产出。
 * 所以本类只回答步进：规则里写了就用规则的，没写就用贴图原生帧宽；贴图还没解析到
 * 时退到一个标准字符宽，避免排版在半就绪状态下抖动。
 *
 * 这两代的接口没有 bake 环节（图集只服务原版字形），因此不需要实现图集入库方法。
 */
public final class OdcGlyphInfo implements GlyphInfo {

    private final VisualFontReplace.CharGlyph glyph;

    public OdcGlyphInfo(VisualFontReplace.CharGlyph glyph) {
        this.glyph = glyph;
    }

    @Override
    public float getAdvance() {
        if (glyph.width() > 0) {
            return glyph.width();
        }
        try {
            LooseResourceLoader.SheetInfo sheet = LooseResourceLoader.sheetOf(glyph.texture());
            if (sheet != null && sheet.frameW() > 0) {
                return sheet.frameW();
            }
            LooseResourceLoader.Size size = LooseResourceLoader.sizeOf(glyph.texture());
            if (size != null && size.width() > 0) {
                return size.width();
            }
        } catch (Throwable t) {
            // 贴图信息一时取不到：用兜底步进，绝不抛到排版路径
        }
        return OdcGlyphGeometry.FALLBACK_ADVANCE;
    }
}
