package com.opendreamcore.script;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 富文本解析测试：16 色码 / RGB 两种写法 / 格式码 / 混合。
 */
class RichTextTest {

    @Test
    void plainTextSingleSegment() {
        List<RichText.Segment> segments = RichText.parse("你好世界");
        assertEquals(1, segments.size());
        assertEquals("你好世界", segments.get(0).text());
        assertEquals(0xFFFFFF, segments.get(0).color());
    }

    @Test
    void colorCodes() {
        List<RichText.Segment> segments = RichText.parse("§c红§a绿");
        assertEquals(2, segments.size());
        assertEquals("红", segments.get(0).text());
        assertEquals(0xFF5555, segments.get(0).color());
        assertEquals("绿", segments.get(1).text());
        assertEquals(0x55FF55, segments.get(1).color());
    }

    @Test
    void ampersandCodes() {
        List<RichText.Segment> segments = RichText.parse("&6金&f白");
        assertEquals(2, segments.size());
        assertEquals(0xFFAA00, segments.get(0).color());
        assertEquals(0xFFFFFF, segments.get(1).color());
    }

    @Test
    void rgbCompactBungee() {
        List<RichText.Segment> segments = RichText.parse("&#FF8800橙");
        assertEquals(1, segments.size());
        assertEquals("橙", segments.get(0).text());
        assertEquals(0xFF8800, segments.get(0).color());
    }

    @Test
    void rgbTraditionalPrefixed() {
        // §x§F§F§8§8§0§0
        List<RichText.Segment> segments = RichText.parse("§x§F§F§8§8§0§0橙");
        assertEquals(1, segments.size());
        assertEquals(0xFF8800, segments.get(0).color());
        assertEquals("橙", segments.get(0).text());
    }

    @Test
    void formatCodesTracked() {
        List<RichText.Segment> segments = RichText.parse("§l粗体§r普通");
        assertEquals(2, segments.size());
        assertTrue(segments.get(0).bold());
        assertEquals("粗体", segments.get(0).text());
        assertFalse(segments.get(1).bold());
        assertEquals(0xFFFFFF, segments.get(1).color(), "§r 重置颜色");
    }

    @Test
    void mixedInlineMultiColor() {
        List<RichText.Segment> segments = RichText.parse("§a[系统] §f玩家 §b点击了");
        assertEquals(3, segments.size());
        assertEquals(0x55FF55, segments.get(0).color());
        assertEquals("[系统] ", segments.get(0).text());
        assertEquals(0x55FFFF, segments.get(2).color());
        assertEquals("点击了", segments.get(2).text());
    }

    @Test
    void literalAmpersandKept() {
        List<RichText.Segment> segments = RichText.parse("100% & 50%");
        assertEquals(1, segments.size());
        assertEquals("100% & 50%", segments.get(0).text());
    }

    @Test
    void emptyInput() {
        assertTrue(RichText.parse("").isEmpty());
        assertTrue(RichText.parse(null).isEmpty());
    }
}
