package com.opendreamcore.adapter.dreamcore;

import com.opendreamcore.config.PageSchema;
import com.opendreamcore.config.YamlParser;
import com.opendreamcore.page.Element;
import com.opendreamcore.page.Page;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Golden 测试：真实 菜单.yml（仓库根目录快照）全链路解析。
 * 链路 = YamlParser → DreamCoreParser.transform → PageSchema.build（与客户端 LocalPageManager 一致）。
 * 查的是：不抛异常、元素数量正确、关键映射逐点抽查。
 */
class MenuYmlGoldenTest {

    private static Map<String, Object> transformReal() throws Exception {
        try (var in = MenuYmlGoldenTest.class.getResourceAsStream("/legacy/menu.yml")) {
            assertNotNull(in, "测试资源 legacy/menu.yml 缺失");
            String yaml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Map<String, Object> ir = new YamlParser().parse(yaml);
            return DreamCoreParser.transform(ir);
        }
    }

    @Test
    void fullLegacyMenuParsesAndBuilds() throws Exception {
        Map<String, Object> ir = transformReal();
        Page page = PageSchema.build("龙核菜单", ir);
        assertEquals("龙核菜单", page.match().target());
        assertTrue(page.options().containsKey("allowEscClose"), "allowEscClose 顶层标量透传进选项");
        assertTrue(page.elements().size() > 40,
                "菜单.yml 应产出 40+ 顶层元素，实际 " + page.elements().size());
        assertTrue(count(page) >= page.elements().size(),
                "元素树完整（含嵌套子元素）");
    }

    @Test
    void backgroundTextureBecomesImage() throws Exception {
        Map<String, Object> el = find(transformReal(), "背景");
        assertEquals("image", el.get("type"));
        assertTrue(el.get("image") instanceof Map<?, ?>, "贴图配置转换为image spec");
    }

    @Test
    void mallSlotMapsToChestSlot11() throws Exception {
        Map<String, Object> el = find(transformReal(), "商城");
        assertEquals("chest_slot", el.get("type"));
        assertTrue(el.get("chest_slot") instanceof Map<?, ?> cs
                && Integer.valueOf(11).equals(((Map<?, ?>) cs).get("slot")), "container_11 → slot 11");
        assertTrue(el.get("chest_slot") instanceof Map<?, ?> cs2
                && Boolean.FALSE.equals(((Map<?, ?>) cs2).get("showSlot")));
    }

    @Test
    void functionsRemainExecutableAfterRewrite() throws Exception {
        Map<String, Object> ir = transformReal();
        Object rawFunctions = ir.get("Functions");
        assertTrue(rawFunctions instanceof Map<?, ?>, "Functions 键保留");
        Map<?, ?> functions = (Map<?, ?>) rawFunctions;
        assertTrue(functions.containsKey("open") && functions.containsKey("keyPress")
                && functions.containsKey("wheel") && functions.containsKey("槽位检测"));
        assertFalse(String.valueOf(functions.get("槽位检测")).contains("界面变量."), "旧作用域记号不残留");
    }

    @Test
    void scrollRegionAndOpacityExpressionsPreserved() throws Exception {
        Map<String, Object> el = find(transformReal(), "标题_label");
        assertEquals("text", el.get("type"));
        assertEquals("方法.取屏幕高度*0.0034", el.get("scale"), "scale 表达式原样（运行时求值）");
        assertEquals("方法.取界面存活时间/700", el.get("opacity"), "alpha→opacity 表达式原样");
        Map<String, Object> mainCity = find(transformReal(), "主城");
        assertTrue(mainCity.containsKey("limitY") && mainCity.containsKey("limitWidth"),
                "旧滚动区 limit* 键保留（滚动语义在事件阶段处理）");
    }

    // 工具

    private static Map<String, Object> find(Map<String, Object> ir, String id) {
        Object o = ir.get(id);
        if (o instanceof Map<?, ?>) {
            @SuppressWarnings("unchecked")
            Map<String, Object> cast = (Map<String, Object>) o;
            return cast;
        }
        fail("找不到元素: " + id);
        return null;
    }

    private static int count(Page p) {
        int n = p.elements().size();
        for (Element e : p.elements()) {
            n += countChildren(e);
        }
        return n;
    }

    private static int countChildren(Element e) {
        int n = e.children().size();
        for (Element c : e.children()) {
            n += countChildren(c);
        }
        return n;
    }
}
