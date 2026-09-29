package com.opendreamcore.legacy;

import com.opendreamcore.client.visual.LegacyFontReplace;
import com.opendreamcore.client.visual.LegacyTtfSource;
import net.minecraft.client.gui.fonts.IGlyph;
import net.minecraft.client.gui.fonts.TexturedGlyph;
import net.minecraft.util.ResourceLocation;

/**
 * 字符替换规则与 1.16.5 字体层之间的桥。
 *
 * 这代的原版字体把「量宽度」与「画字形」收口在两个公开出口上：宽度问
 * getGlyphInfo，绘制要 getGlyph。居中、右对齐、按宽度裁切这些逻辑全都拿这两个
 * 出口的结果去算，所以只要这两处返回我们的替换字形，绘制与量宽两条路径就一次性
 * 全接管，而且彼此口径天然一致，不必像早期版本那样逐个重写量宽与裁切方法。
 *
 * 字形来源有两条，优先级固定：单字/区间/正则配的贴图规则优先，没命中再看全局字体。
 * 全局字体是运行期一个字一个字光栅出来的，尺寸与推进宽度都随目标字号走，所以它的
 * 字形宽高直接用光栅结果的像素尺寸、推进宽度用光栅算出的量，两者同源，不会错位。
 *
 * isReplaced 只做命中判定，给注入点当快路径短路用，不解析贴图、不造对象；
 * glyph / info 才真正造对象，任何环节出意外都返回 null，调用方拿到 null 就当
 * 没命中、放行原版字形——渲染线程上宁可少替换一个字符，也不能把异常带进批处理。
 *
 * 动图不用在这里维护帧号：规则层取条目时已经按当时的帧钟定过帧（见规则层的定帧
 * 逻辑），我们拿到的 u / totalFrames 就是当下该显示的那一帧。
 */
public final class LegacyFontGlyphs {

    private LegacyFontGlyphs() {
    }

    /**
     * 该码位是否配了替换规则。字体层每画一个字符都会问一次，所以这里只做判定，
     * 不碰贴图解析、也不造对象。
     */
    public static boolean isReplaced(int codePoint) {
        return rule(codePoint) != null || ttf(codePoint) != null;
    }

    /**
     * 替换字形。没命中、贴图解析不出来、或构造途中抛了异常，一律给 null，
     * 由调用方回退原版字形。
     */
    public static TexturedGlyph glyph(int codePoint) {
        LegacyFontReplace.Glyph g = rule(codePoint);
        if (g != null) {
            ResourceLocation rl = resolve(g.texture);
            if (rl == null) {
                return null;
            }
            try {
                // u 对单字与正则来说是当前帧号，对区间来说是字符在区间里的序号；
                // totalFrames 恰好是两者的横向等分数，切 uv 用同一套公式。
                return LegacyGlyphFactory.glyph(rl, g.u, g.totalFrames, g.frameW, g.frameH);
            } catch (Throwable t) {
                return null;
            }
        }
        // 贴图规则没命中，落到全局字体：把光栅出来的字位图整张当一个字形，
        // 宽高取位图像素尺寸、帧数固定 1（uv 走满 0..1）。
        LegacyTtfSource.Glyph tg = ttf(codePoint);
        if (tg == null) {
            return null;
        }
        try {
            String rl = LegacyTtfSource.textureOf(tg);
            if (rl == null) {
                return null;
            }
            return LegacyGlyphFactory.glyph(
                    new ResourceLocation(rl), 0, 1, tg.image.getWidth(), tg.image.getHeight());
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 替换字形的宽度信息。贴图解析不出来时与 glyph 一样给 null，
     * 这样「量出来的宽」和「画出来的宽」永远同源，不会一个替换一个不替换。
     */
    public static IGlyph info(int codePoint) {
        LegacyFontReplace.Glyph g = rule(codePoint);
        if (g != null && resolve(g.texture) != null) {
            try {
                int advance = g.fontWidth > 0 ? g.fontWidth : g.frameW;
                return LegacyGlyphFactory.info(Math.max(1, advance));
            } catch (Throwable t) {
                return null;
            }
        }
        // 全局字体：推进宽度取光栅时算出的同一个值，跟上面画出来的字位图同源，
        // 量宽与绘制不会一个走替换一个走原版。
        LegacyTtfSource.Glyph tg = ttf(codePoint);
        if (tg == null) {
            return null;
        }
        try {
            return LegacyGlyphFactory.info(Math.max(1, tg.advance));
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 全局字体字形。全局字体没开、没配路径、或光栅失败，一律 null 放行原版字形。
     */
    private static LegacyTtfSource.Glyph ttf(int codePoint) {
        if (codePoint < 0 || codePoint > 0xFFFF || !LegacyTtfSource.enabled()) {
            return null;
        }
        try {
            return LegacyTtfSource.get((char) codePoint);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 查规则；越界码位、没有规则、规则表本身出异常，都折成 null。 */
    private static LegacyFontReplace.Glyph rule(int codePoint) {
        // 这代的字体层以 int 码位为键，规则表按 char 建表，超出基本平面的码位一律放行
        if (codePoint < 0 || codePoint > 0xFFFF || !LegacyFontReplace.hasAny()) {
            return null;
        }
        try {
            return LegacyFontReplace.glyphFor((char) codePoint);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 贴图名转纹理位置：先查散装贴图注册表（中文名、本地目录图都走这条），
     * 带命名空间的原样用，剩下的补本模组命名空间。与其余远古版本同一套语义。
     */
    private static ResourceLocation resolve(String texture) {
        if (texture == null || texture.trim().isEmpty()) {
            return null;
        }
        try {
            String loose = com.opendreamcore.client.LooseTextureLoader.lookup(texture);
            if (loose != null) {
                return new ResourceLocation(loose);
            }
            if (texture.indexOf(':') > 0) {
                return new ResourceLocation(texture);
            }
            return new ResourceLocation("opendreamcore", texture);
        } catch (Throwable t) {
            return null;
        }
    }
}
