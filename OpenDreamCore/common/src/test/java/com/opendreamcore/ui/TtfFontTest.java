package com.opendreamcore.ui;

import org.junit.jupiter.api.Test;

import java.awt.Font;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TTF 字体封装测试：加载/测量/字形渲染（用 AWT 系统字体，无文件依赖）。
 */
class TtfFontTest {

    private static TtfFont font() {
        return new TtfFont("test", new Font(Font.SANS_SERIF, Font.PLAIN, TtfFont.SOURCE_SIZE));
    }

    @Test
    void advanceAndWidth() {
        TtfFont font = font();
        assertTrue(font.advance('A') > 0, "字母应有宽度");
        assertEquals(font.advance('A') + font.advance('B'), font.textWidth("AB"));
        assertTrue(font.lineHeight() >= font.ascent(), "行高不小于基线高");
        assertTrue(font.ascent() > 0);
    }

    @Test
    void renderGlyphProducesImage() {
        TtfFont font = font();
        BufferedImage a = font.renderGlyph('A');
        assertNotNull(a);
        assertTrue(a.getHeight() >= TtfFont.SOURCE_SIZE, "字形图像高度 ≥ 渲染源字号（含 descent）");
        assertTrue(a.getWidth() > 0);
        // 至少有一个不透明像素（字形画出来了）
        boolean opaque = false;
        for (int y = 0; y < a.getHeight() && !opaque; y++) {
            for (int x = 0; x < a.getWidth(); x++) {
                if (((a.getRGB(x, y) >>> 24) & 0xFF) > 0) {
                    opaque = true;
                    break;
                }
            }
        }
        assertTrue(opaque, "字形图像应包含不透明像素");
    }

    @Test
    void blankCharsNoGlyph() {
        TtfFont font = font();
        assertNull(font.renderGlyph(' '));
        assertNull(font.renderGlyph('\n'));
        assertNull(font.renderGlyph('\t'));
    }

    @Test
    void chineseGlyphRenders() {
        TtfFont font = font();
        BufferedImage img = font.renderGlyph('中');
        assertNotNull(img);
        assertTrue(img.getWidth() > 0);
    }
}
