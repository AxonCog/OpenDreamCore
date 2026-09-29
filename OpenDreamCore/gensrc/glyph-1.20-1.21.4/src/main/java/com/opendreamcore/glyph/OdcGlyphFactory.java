package com.opendreamcore.glyph;

import com.opendreamcore.client.glyph.OdcGlyphGeometry;
import com.opendreamcore.client.resources.LooseResourceLoader;
import com.opendreamcore.client.visual.VisualFontReplace;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.font.GlyphRenderTypes;
import net.minecraft.resources.ResourceLocation;

/**
 * 替换字形的构建工厂（1.20.1 / 1.21.1 / 1.21.4 三代共用）。
 *
 * 只做一件事：把一条配置规则翻译成一次字形构造——定位纹理、切出当前帧的像素矩形、
 * 算出字形框、装配渲染类型。命中判定和缓存都不在这里。
 *
 * 这三代没有纹理视图句柄，纹理就绪判据用「纹理管理器里查得到这张纹理」。
 *
 * 统一约定：任何一步失败（纹理没解析到、纹理未注册、尺寸未知、构造抛异常）都返回
 * null，调用方拿到 null 就不接管，交给原版字形渲染。因此「贴图还没就绪」只表现为
 * 该字暂时用原版字体，不会出现空字形、不可见顶点或渲染线程异常。
 */
public final class OdcGlyphFactory {

    private OdcGlyphFactory() {
    }

    /** 单字贴图 / GIF 字形。 */
    public static OdcBakedGlyph bitmap(VisualFontReplace.CharGlyph glyph) {
        try {
            // sheetOf 一次给出纹理与帧信息：本地/远程、静态/GIF 都走同一条路
            LooseResourceLoader.SheetInfo si = LooseResourceLoader.sheetOf(glyph.texture());
            if (si == null || si.rl() == null) {
                return null;
            }
            if (!registered(si.rl())) {
                return null; // 纹理尚未就绪：回退原版字形，注册完成后下一帧自动接管
            }
            int frames = Math.max(si.frames(), 1);
            int frameW = Math.max(si.frameW(), 1);
            int frameH = Math.max(si.frameH(), 1);
            // 帧表横向等分：整张纹理宽 = 单帧宽 × 帧数
            int texW = frameW * frames;
            int texH = frameH;

            OdcGlyphGeometry.Slice slice = OdcGlyphGeometry.sliceOf(
                    glyph.u(), glyph.v(), frameW, frameH, si.frame(), frames, texW, texH);
            OdcGlyphGeometry geom = OdcGlyphGeometry.bitmap(
                    glyph.width(), glyph.height(), glyph.fontWidth(), slice);

            float u0 = OdcGlyphGeometry.norm(slice.x(), texW);
            float u1 = OdcGlyphGeometry.norm(slice.x() + slice.w(), texW);
            float v0 = OdcGlyphGeometry.norm(slice.y(), texH);
            float v1 = OdcGlyphGeometry.norm(slice.y() + slice.h(), texH);

            GlyphRenderTypes types = GlyphRenderTypes.createForColorTexture(si.rl());
            return new OdcBakedGlyph(types, u0, u1, v0, v1,
                    geom.left(), geom.right(), geom.up(), geom.down());
        } catch (Throwable t) {
            return null;
        }
    }

    /** 全局字体字形：软渲染位图按字体度量贴到基线上。 */
    public static OdcBakedGlyph ttf(com.opendreamcore.client.TtfGlyphSource.Glyph glyph) {
        try {
            ResourceLocation rl = com.opendreamcore.client.TtfGlyphSource.textureFor(glyph);
            if (rl == null || !registered(rl)) {
                return null;
            }
            OdcGlyphGeometry geom = OdcGlyphGeometry.ttf(
                    glyph.image().getWidth(), glyph.image().getHeight(),
                    glyph.top(), glyph.advance());
            GlyphRenderTypes types = GlyphRenderTypes.createForColorTexture(rl);
            return new OdcBakedGlyph(types, 0.0F, 1.0F, 0.0F, 1.0F,
                    geom.left(), geom.right(), geom.up(), geom.down());
        } catch (Throwable t) {
            return null;
        }
    }

    /** 纹理是否已在纹理管理器里注册（本代可用的就绪判据）。 */
    private static boolean registered(ResourceLocation rl) {
        return Minecraft.getInstance().getTextureManager().getTexture(rl) != null;
    }
}
