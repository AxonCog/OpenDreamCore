package com.opendreamcore.ui;

import com.opendreamcore.page.Element;
import com.opendreamcore.page.Layout;
import com.opendreamcore.page.Page;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 旧版（DreamCore/龙核）表达式简写兼容：w = 窗口宽、h = 窗口高。
 * 菜单.yml 全篇使用 (w-背景.width)/1.8、h*1.4 等写法。
 */
class LayoutEngineWAliasTest {

    private static Element el(String id, String widthExpr, String heightExpr) {
        return new Element(id, "rect", new Layout(null, null, widthExpr, heightExpr),
                new java.util.LinkedHashMap<>(), null, null, Map.of(), List.of(), null);
    }

    private static Page page(List<Element> elements, Map<String, Object> variables) {
        return new Page("t", null, null, null,
                new java.util.LinkedHashMap<>(variables), elements,
                Map.of(), Map.of());
    }

    @Test
    void wAndHResolveToWindowDimensions() {
        Page page = page(List.of(el("a", "w * 0.5", "h - 100")), Map.of());
        List<RenderNode> nodes = LayoutEngine.layout(page, 1920, 1080);
        assertEquals(960.0, nodes.get(0).width(), 0.001, "w = 窗口宽");
        assertEquals(980.0, nodes.get(0).height(), 0.001, "h = 窗口高");
    }

    @Test
    void legacyMenuStyleExpression() {
        // 菜单.yml 原句风格：(w-背景.width)/1.8 —— 先布局背景元素，再引用其计算宽度
        Element bg = el("背景", "w * 0.7", "h * 0.8");
        Element panel = new Element("面板", "rect", new Layout("(w-背景.width)/1.8", "h * 0.2", null, null),
                new java.util.LinkedHashMap<>(), null, null, Map.of(), List.of(), null);
        Page page = page(List.of(bg, panel), Map.of());
        List<RenderNode> nodes = LayoutEngine.layout(page, 2000, 1000);
        assertEquals(1400.0, nodes.get(0).width(), 0.001, "背景宽 = w*0.7");
        assertEquals((2000.0 - 1400.0) / 1.8, nodes.get(1).x(), 0.001, "交叉引用 + w 别名混用");
    }

    @Test
    void pageVariableOverridesAlias() {
        // 页面显式定义了同名变量时，变量优先于别名（不破坏既有配置）
        Page page = page(List.of(el("a", "w", "h")), Map.of("h", 50));
        List<RenderNode> nodes = LayoutEngine.layout(page, 1920, 1080);
        assertEquals(1920.0, nodes.get(0).width(), 0.001);
        assertEquals(50.0, nodes.get(0).height(), 0.001, "页面变量 h 覆盖别名");
    }
}
