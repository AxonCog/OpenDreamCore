package com.opendreamcore.glyph;

import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.font.SheetGlyphInfo;
import com.opendreamcore.client.glyph.OdcGlyphGeometry;
import com.opendreamcore.client.resources.LooseResourceLoader;
import com.opendreamcore.client.visual.VisualFontReplace;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.gui.font.glyphs.SpecialGlyphs;

import java.util.function.Function;

/**
 * 单字贴图 / GIF 字符的字形信息（1.21.8 代）。
 *
 * 原版拿到它之后：排版宽度走 getAdvance，落笔字形走 bake。GIF 的动画由「每次取用都
 * 按当前帧重新构造字形对象」实现——纹理始终是同一张帧表，运行期只换顶点 UV，
 * 不重新上传纹理，动画与渲染驱动层完全解耦。
 */
public final class OdcGlyphInfo implements GlyphInfo {

    private final int codePoint;
    private final VisualFontReplace.CharGlyph glyph;

    public OdcGlyphInfo(int codePoint, VisualFontReplace.CharGlyph glyph) {
        this.codePoint = codePoint;
        this.glyph = glyph;
    }

    @Override
    public float getAdvance() {
        // 排版步进：配置写了就用配置值，否则取贴图原生帧宽（未就绪时退一个字符宽）
        if (glyph.fontWidth() > 0) {
            return glyph.fontWidth();
        }
        if (glyph.width() > 0) {
            return glyph.width();
        }
        LooseResourceLoader.SheetInfo si = LooseResourceLoader.sheetOf(glyph.texture());
        if (si != null && si.frameW() > 0) {
            return si.frameW();
        }
        return OdcGlyphGeometry.FALLBACK_ADVANCE;
    }

    @Override
    public BakedGlyph bake(Function<SheetGlyphInfo, BakedGlyph> function) {
        BakedGlyph baked = OdcGlyphCache.bitmap(codePoint, glyph);
        if (baked != null) {
            return baked;
        }
        // 构造失败（贴图未就绪、尺寸未知等）时退回原版缺失字形。
        // 调用方拿到本对象后会直接把 bake 的返回值当字形对象使用，不做 null 检查，
        // 返回 null 会向上传播成空字形——这里必须给出一个可绘制的原版字形。
        return SpecialGlyphs.MISSING.bake(function);
    }
}
