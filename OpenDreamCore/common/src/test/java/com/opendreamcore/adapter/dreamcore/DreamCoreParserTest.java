package com.opendreamcore.adapter.dreamcore;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DreamCore 旧语法 → 标准 ConfigIR 转换测试。
 * 样例取自用户实际配置 菜单.yml 的真实写法片段。
 * 如果你就是自己配置哪里有问题可以去这里你写一个test看看转换结果是否符合预期。
 * 如果不行就去github给我发布issues
 * 一般没问题，因为我已经在这里测试过了。
 */
class DreamCoreParserTest {

    @Test
    void detectsLegacyByInterfaceVariable() {
        assertTrue(DreamCoreParser.isDreamCoreFormat("界面变量.x = 1"));
        assertFalse(DreamCoreParser.isDreamCoreFormat("title: 新语法"));
    }

    @Test
    void textureElementMapsToImageWithHover() {
        Map<String, Object> out = new DreamCoreParser().parse("""
                match: 龙核菜单
                背景:
                  type: 'texture'
                  x: "(w-背景.width)/1.8"
                  y: 10
                  width: h*1.4
                  height: 100
                  texture: 用户变量.壁纸存储
                  textureHovered: "菜单/背景高亮.png"
                  alpha: "方法.取界面存活时间/700"
                  tip:
                  - '金币'
                  - '点击充值'
                """);
        assertEquals("龙核菜单", out.get("match"));
        assertTrue(out.get("背景") instanceof Map<?, ?>, "顶层元素摊平为 id → 元素");
        @SuppressWarnings("unchecked")
        Map<String, Object> bg = (Map<String, Object>) out.get("背景");
        assertEquals("image", bg.get("type"), "texture → image");
        // texture 平铺值 → image.src（作用域引用改写为页面变量裸名）；表达式属性原样保留
        assertTrue(bg.get("image") instanceof Map<?, ?> img
                && "odc_user_壁纸存储".equals(((Map<?, ?>) img).get("src")), "texture → image.src（作用域读改写）");
        assertTrue(bg.get("image") instanceof Map<?, ?> img2
                && "菜单/背景高亮.png".equals(((Map<?, ?>) img2).get("hoverSrc")), "textureHovered → image.hoverSrc");
        assertEquals("方法.取界面存活时间/700", bg.get("opacity"), "alpha → opacity（表达式原样）");
        assertTrue(bg.get("tooltip") instanceof List<?> && bg.get("tip") == null, "tip → tooltip");
    }

    @Test
    void slotElementMapsIdentifierAndDrawBackground() {
        Map<String, Object> out = new DreamCoreParser().parse("""
                精灵相册:
                  type: 'slot'
                  x: 10
                  y: 20
                  width: 30
                  height: 30
                  identifier: 'container_6'
                  drawBackground: false
                """);
        @SuppressWarnings("unchecked")
        Map<String, Object> slot = (Map<String, Object>) out.get("精灵相册");
        assertEquals("chest_slot", slot.get("type"), "slot → chest_slot");
        assertTrue(slot.get("chest_slot") instanceof Map<?, ?> cs
                && Integer.valueOf(6).equals(((Map<?, ?>) cs).get("slot")), "container_6 → slot=6");
        assertTrue(slot.get("chest_slot") instanceof Map<?, ?> cs2
                && Boolean.FALSE.equals(((Map<?, ?>) cs2).get("showSlot")), "drawBackground:false → showSlot:false");
        assertNull(slot.get("identifier"));
        assertNull(slot.get("drawBackground"));
    }

    @Test
    void containerIndexParsing() {
        assertEquals(6, DreamCoreParser.containerIndex("container_6"));
        assertEquals(11, DreamCoreParser.containerIndex("container_11"));
        assertEquals(3, DreamCoreParser.containerIndex("Container-3"));
        assertEquals(9, DreamCoreParser.containerIndex("9"));
        assertEquals(-1, DreamCoreParser.containerIndex("引导槽位"));
        assertEquals(-1, DreamCoreParser.containerIndex(null));
    }
}
