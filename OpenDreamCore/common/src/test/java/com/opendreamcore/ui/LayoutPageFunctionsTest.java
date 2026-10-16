package com.opendreamcore.ui;

import com.opendreamcore.page.Element;
import com.opendreamcore.page.Layout;
import com.opendreamcore.page.Page;
import com.opendreamcore.script.MethodRegistry;
import com.opendreamcore.script.engine.DreamLang;
import com.opendreamcore.script.engine.Scope;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LayoutPageFunctionsTest {
    private static Element element(String id, Layout layout, Map<String, Object> props) {
        return new Element(id, "rect", layout, new LinkedHashMap<>(props), null, null,
                Map.of(), List.of(), null);
    }

    private static Page page(List<Element> elements, Map<String, Object> variables,
                             Map<String, String> functions) {
        return new Page("test", null, null, null, new LinkedHashMap<>(variables), elements,
                new LinkedHashMap<>(functions), Map.of());
    }

    @Test
    void reinforcementFunctionsSeeSamePassBackgroundAndWindow() {
        Element background = element("背景", new Layout("(w-背景.width)*0.5", "(h-背景.height)*0.5",
                "h*方法.取yaml值('界面大小')", "方法.执行方法('设置宽度',方法.取yaml值('背景实际高度'))"), Map.of());
        Element slot = element("背包槽位_55", new Layout(
                "方法.执行方法('定位遍历X',8.5,18,9,局部变量.当前组件.序号)",
                "方法.执行方法('定位遍历Y',41,18,9,局部变量.当前组件.序号)", "16", "16"),
                Map.of("序号", 55));
        Page page = page(List.of(background, slot), Map.of("界面大小", 0.349,
                "背景实际宽度", 176, "背景实际高度", 231), Map.of(
                "设置宽度", "return 背景.width/方法.取yaml值('背景实际宽度')*局部变量.参数.0;",
                "定位遍历X", "return 背景.x+背景.width/方法.取yaml值('背景实际宽度')*(局部变量.参数.0+局部变量.参数.1*(局部变量.参数.3%局部变量.参数.2));",
                "定位遍历Y", "return 背景.y+背景.height/方法.取yaml值('背景实际高度')*(局部变量.参数.0+局部变量.参数.1*6);"));
        List<RenderNode> nodes = LayoutEngine.layout(page, 800, 600);
        double backgroundWidth = 600 * 0.349;
        double backgroundHeight = backgroundWidth / 176 * 231;
        assertEquals(backgroundWidth, nodes.get(0).width(), 1e-9);
        assertEquals(backgroundHeight, nodes.get(0).height(), 1e-9);
        assertEquals((800 - backgroundWidth) / 2, nodes.get(0).x(), 1e-9);
        assertEquals((600 - backgroundHeight) / 2, nodes.get(0).y(), 1e-9);
        assertEquals(nodes.get(0).x() + backgroundWidth / 176 * 26.5, nodes.get(1).x(), 1e-9);
        assertEquals(nodes.get(0).y() + backgroundHeight / 231 * 149, nodes.get(1).y(), 1e-9);
        assertFalse(page.variables().containsKey("参数"));
        assertFalse(page.variables().containsKey("当前组件"));
        assertFalse(page.variables().containsKey("背景"));
    }

    @Test
    void currentComponentFieldsDrivePropsVisibilityTextAndIdentifierWithoutModelMutation() {
        Map<String, Object> text = new LinkedHashMap<>(Map.of("content", "局部变量.当前组件.内容"));
        Element slot = new Element("商品_5", "chest_slot", new Layout(
                "方法.执行方法('位置',局部变量.当前组件.序号)", "0", "16", "16"),
                new LinkedHashMap<>(Map.of("序号", 5, "内容", "craftx_item_display", "text", text,
                        "identifier", "局部变量.当前组件.内容", "scale", "局部变量.当前组件.序号/5")),
                "局部变量.当前组件.序号 > 3", null, Map.of(), List.of(), null);
        Page page = page(List.of(slot), Map.of(), Map.of("位置",
                "return 局部变量.参数.0*10+局部变量.当前组件.序号;"));
        RenderNode node = LayoutEngine.layout(page, 800, 600).get(0);
        assertEquals(55, node.x());
        assertTrue(node.visible());
        assertEquals(1, node.scale());
        assertEquals("craftx_item_display", node.props().get("identifier"));
        assertEquals("craftx_item_display", ((Map<?, ?>) node.props().get("text")).get("content"));
        assertEquals("局部变量.当前组件.内容", text.get("content"));
        assertEquals("局部变量.当前组件.内容", slot.props().get("identifier"));
        slot.props().put("序号", 2);
        slot.props().put("内容", "craftx_next_display");
        RenderNode next = LayoutEngine.layout(page, 800, 600).get(0);
        assertEquals(22, next.x());
        assertFalse(next.visible());
        assertEquals("craftx_next_display", next.props().get("identifier"));
    }

    @Test
    void nestedFunctionsKeepCallerArgumentsLocalAndPreserveComponent() {
        Scope scope = new Scope();
        scope.declare("window", Map.of("width", 800, "height", 600));
        scope.assignVar("背景", Map.of("x", 100));
        scope.assignVar("bonus", 3);
        scope.declare("参数", new LinkedHashMap<>(Map.of("0", 99)));
        Page page = page(List.of(), Map.of(), Map.of(
                "outer", "局部变量.temporary=1; return 方法.执行方法('inner',8)+局部变量.参数.0;",
                "inner", "局部变量.temporary=9; return 背景.x+window.width+bonus+局部变量.参数.0+局部变量.当前组件.序号;"));
        Map<String, Object> component = new LinkedHashMap<>(Map.of("序号", 5));
        PageFunctions.bind(page, scope, component);
        assertEquals(920.0, DreamLang.evaluate("方法.执行方法('outer',4)", scope));
        assertEquals(99, scope.resolve("局部变量", "参数", "0"));
        assertNull(scope.resolve("局部变量", "temporary"));
        assertSame(component, scope.resolve("局部变量", "当前组件"));
        assertNull(DreamLang.currentScope());
    }

    @Test
    void nestedLayoutKeepsOuterFramesAndViewport() {
        Page inner = page(List.of(element("inner", new Layout("10px", "20px", "100px", "40px"), Map.of())),
                Map.of(), Map.of());
        MethodRegistry.registerOrReplace("layoutPageFunctionsNested", args -> {
            LayoutEngine.layout(inner, 400, 200, null, Viewport.IDENTITY);
            return 7;
        });
        try {
            Page outer = page(List.of(
                    element("背景", new Layout("30", "40", "200", "100"), Map.of()),
                    element("商品", new Layout("方法.执行方法('位置',2)", "背景.y", "20", "20"), Map.of())),
                    Map.of(), Map.of("位置", "return 方法.layoutPageFunctionsNested()+背景.x+局部变量.参数.0;"));
            List<RenderNode> nodes = LayoutEngine.layout(outer, 800, 600);
            assertEquals(39, nodes.get(1).x());
            assertEquals(40, nodes.get(1).y());
        } finally {
            MethodRegistry.unregister("layoutPageFunctionsNested");
        }
    }

    @Test
    void nativePageLiteralPropsAndVariableAliasesRemainUnchanged() {
        Element label = element("native", new Layout("window.width/2", "h-10", "50", "20"),
                Map.of("text", Map.of("content", "A literal label"), "identifier", "container_0"));
        RenderNode node = LayoutEngine.layout(page(List.of(label), Map.of("h", 100), Map.of()), 800, 600).get(0);
        assertEquals(400, node.x());
        assertEquals(90, node.y());
        assertEquals("A literal label", ((Map<?, ?>) node.props().get("text")).get("content"));
        assertEquals("container_0", node.props().get("identifier"));
    }
}
