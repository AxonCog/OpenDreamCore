package com.opendreamcore.legacy;

import com.mojang.blaze3d.vertex.IVertexBuilder;
import net.minecraft.client.gui.fonts.IGlyph;
import net.minecraft.client.gui.fonts.TexturedGlyph;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.vector.Matrix4f;

/**
 * 替换字形（1.16.5）：把一张替换贴图包装成这代字体层认的字形对象。
 *
 * 这代的字形不是一个类包办，而是拆成两半：
 *   画    —— TexturedGlyph，贴图、uv、四边留白全在构造时定死，render 只读这些量；
 *   量宽  —— IGlyph，只回答推进宽度与粗体/阴影偏移。
 * 所以这里各给一个实现，两边都从同一条规则的同一次解析结果算出来，口径天然一致，
 * 不会出现「量出来一个宽、画出来另一个宽」的错位。
 *
 * 贴图不走原版字形图集：图集在字体重载时一次烘死，运行时塞不进新图，因此用
 * 自建渲染类型（{@link LegacyGlyphRenderTypes}）直接绑我们的贴图。
 *
 * frames 是贴图横向排的帧数：静态图 1 帧，动图按当前帧号切 uv，于是动图自然
 * 就动起来了——字形对象每次取用都按当时的帧号重新构造，不需要另外维护状态。
 */
final class LegacyGlyphFactory {

    private LegacyGlyphFactory() {
    }

    /**
     * 造一个替换字形。frame 为当前帧号（静态图恒 0），frames 为总帧数（静态图 1）。
     *
     * 贴图是横排帧表，横向 uv 按 frame/frames 归一化，纵向整条用满。
     * frameW / frameH 决定字形占多宽多高。
     *
     * 四边留白的口径：原版 TexturedGlyph.render 里纵向上边算的是 up - 3、
     * 下边算的是 down - 3，横向就是 left / right。原版位图字形按 up = 0、
     * down = 像素高 走，画出来顶边正好落在文字基线那一档。这里沿用同一口径，
     * 于是替换字和原版字混在同一行时上下不打架。
     */
    static TexturedGlyph glyph(ResourceLocation texture, int frame, int frames,
                               int frameW, int frameH) {
        int total = Math.max(1, frames);
        int idx = Math.max(0, Math.min(frame, total - 1));
        float w = Math.max(1, frameW);
        float h = Math.max(1, frameH);
        float u0 = (float) idx / (float) total;
        float u1 = (float) (idx + 1) / (float) total;
        RenderType type = LegacyGlyphRenderTypes.text(texture);
        return new TexturedGlyph(type, type, u0, u1, 0.0F, 1.0F, 0.0F, w, 0.0F, h);
    }

    /**
     * 字形宽度信息：推进宽度跟着替换贴图走。
     *
     * 粗体与阴影偏移沿用这代接口的默认口径（各一像素），所以这里只实现推进宽度，
     * 其余三个方法吃接口默认值即可——颜色、粗体、斜体、阴影这些样式语义全在原版
     * 绘制链里，我们一个字都不用重写。
     */
    static IGlyph info(final float advance) {
        return new IGlyph() {
            @Override
            public float getAdvance() {
                return advance;
            }
        };
    }

    /**
     * 兜底字形：所有几何量都是零，原版 render 会算出零面积，等于什么都不画。
     *
     * 只在真正出意外时用（贴图在接管之前就核过能解析，正常走不到）。渲染线程上
     * 宁可少画一个字符，也不能让异常顺着顶点批处理往外炸。这里复用该条规则自己的
     * 贴图，不凭空引一个可能压根不存在的资源位置。
     */
    static TexturedGlyph empty(ResourceLocation texture) {
        RenderType type = LegacyGlyphRenderTypes.text(texture);
        return new TexturedGlyph(type, type, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
    }
}
