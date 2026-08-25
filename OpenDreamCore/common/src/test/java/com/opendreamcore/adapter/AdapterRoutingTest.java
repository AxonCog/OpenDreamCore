package com.opendreamcore.adapter;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * E4：格式检测单一来源——所有调用方经 AdapterRegistry.detect 路由，
 * DreamCoreParser 自注册 + 强特征判定（大写 Functions/旧类型名/作用域变量等）。
 */
class AdapterRoutingTest {

    /** 龙核旧格式特征：大写 Functions 块 + 旧类型名 Texture。 */
    private static final String LEGACY = """
            match: "龙核菜单"
            Functions:
              keyPress: "(1>0)?{方法.测试记录('A');}:0"
            背景图:
              type: Texture
              texture: "菜单/背景.png"
            """;

    /** 标准新格式：小写 functions（或无）、标准类型名——不得误判为旧格式。 */
    private static final String STANDARD = """
            match: shop
            title_text:
              type: text
              x: 0
              text:
                content: hi
            """;

    @Test
    void legacyDetectedAndRouted() {
        var parser = AdapterRegistry.detect(LEGACY);
        assertNotNull(parser, "旧格式应被检测命中");
        assertEquals("dreamcore", parser.format());
        // transform 摊平回顶层：背景图 保留，Texture 已映射为 image
        Map<?, ?> ir = parser.parse(LEGACY);
        assertTrue(ir.containsKey("背景图"), "元素应摊平回顶层键");
        var el = (Map<?, ?>) ir.get("背景图");
        assertEquals("image", String.valueOf(el.get("type")), "旧类型 Texture 应映射为 image");
    }

    @Test
    void standardNotMisrouted() {
        assertNull(AdapterRegistry.detect(STANDARD), "标准嵌套语法不应判为 dreamcore");
    }

    @Test
    void garbageFallsBackToNull() {
        assertNull(AdapterRegistry.detect(":: 不是 yaml ::{"));
        assertNull(AdapterRegistry.detect(""), "空文本不命中任何适配器");
    }

    @Test
    void detectIsIdempotentAcrossCalls() {
        assertSame(AdapterRegistry.detect(LEGACY), AdapterRegistry.detect(LEGACY),
                "bootstrap 幂等：多次检测返回同一解析器单例");
    }

    @Test
    void strongMarkersEachTrigger() {
        for (String marker : new String[]{
                "hideVanillaList: [a]",
                "界面变量.x = 1",
                "用户变量.y = 2",
                "preRender: \"x\"",
                "\nFunctions:\n  open: \"\"",
        }) {
            assertNotNull(AdapterRegistry.detect(marker),
                    "强特征应命中: " + marker.replace("\n", "\\n"));
        }
    }
}
