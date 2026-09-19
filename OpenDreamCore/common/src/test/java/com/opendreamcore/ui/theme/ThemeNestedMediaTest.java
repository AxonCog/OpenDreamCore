package com.opendreamcore.ui.theme;

import com.opendreamcore.config.PageSchema;
import com.opendreamcore.page.Element;
import com.opendreamcore.ui.LayoutEngine;
import com.opendreamcore.page.Page;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 嵌套规则与响应式查询测试：
 * CSS Nesting 式嵌套、& 父引用、> 子代、@媒体块（顶层与规则内），
 * 以及媒体覆盖在布局期的现判现用。
 */
class ThemeNestedMediaTest {

    private static Page page(Map<String, Object> ir) {
        return PageSchema.build("t", ir);
    }

    private static Map<String, Object> box(String type) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        m.put("rect", Map.of("color", "#333333"));
        return m;
    }

    // 嵌套规则

    @Test
    void nestedDefaultsToDescendantCombinator() {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("type", "layout");
        card.put("background", "#222222");
        card.put(".title", Map.of("fontSize", 12));   // 嵌套：默认后代

        Theme t = ThemeParser.parse("t", new LinkedHashMap<>(Map.of(".card", card)));
        assertEquals(2, t.rules().size());
        // 找到嵌套出来的那条
        StyleRule nested = t.rules().stream()
                .filter(r -> r.selector().toString().equals(".card .title"))
                .findFirst().orElse(null);
        assertNotNull(nested, "嵌套键应展开为 .card .title");
        assertEquals(12, nested.declarations().get("fontSize"));
    }

    @Test
    void ampersandRefersToParentAndGtMeansChild() {
        Map<String, Object> btn = new LinkedHashMap<>();
        btn.put("type", "button");
        btn.put("&:hover", Map.of("color", "#FF0000"));   // & 引用父选择器
        btn.put(">.icon", Map.of("width", 8));            // 显式子代
        Theme t = ThemeParser.parse("t", new LinkedHashMap<>(Map.of("button", btn)));

        assertTrue(t.rules().stream().anyMatch(r ->
                        r.selector().toString().equals("button:hover")),
                "& 应替换为父选择器原文");
        assertTrue(t.rules().stream().anyMatch(r ->
                        r.selector().toString().equals("button > .icon")),
                "> 应生成直接子代选择器");
    }

    @Test
    void nestedAndFlatCoexist() {
        // 用户要求的核心场景：嵌套写法与平写选择器共存于同一主题
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("type", "layout");
        card.put(".title", Map.of("fontSize", 12));              // 嵌套写法
        card.put("@max-width 500", Map.of("paddingAll", 2));     // 规则内媒体块
        Map<String, Object> ir = new LinkedHashMap<>();
        ir.put(".card", card);
        ir.put(".modal .title", Map.of("fontSize", 20));         // 平写后代选择器
        Theme t = ThemeParser.parse("t", ir);

        // .card 底色 / .card .title(嵌套) / .card@media(paddingAll) / .modal .title(平写)
        assertEquals(4, t.rules().size());
        assertTrue(t.rules().stream().anyMatch(r -> r.selector().toString().equals(".modal .title")));
    }

    // 响应式查询

    @Test
    void topLevelMediaBlockParsesCondition() {
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("button", Map.of("height", 14));
        Map<String, Object> ir = new LinkedHashMap<>();
        ir.put("@max-width 854", inner);
        Theme t = ThemeParser.parse("t", ir);

        assertEquals(1, t.rules().size());
        StyleRule r = t.rules().get(0);
        assertNotNull(r.media(), "顶层 @块应给规则挂上条件");
        assertTrue(r.media().matches(800, 600));
        assertFalse(r.media().matches(1200, 800));
        assertEquals(14, r.declarations().get("height"));
    }

    @Test
    void cssFlavoredMediaConditionAlsoAccepted() {
        MediaQuery q = MediaQuery.parse("@media (min-width: 400) and (max-height: 600)");
        assertNotNull(q);
        assertTrue(q.matches(500, 550));
        assertFalse(q.matches(300, 550));   // 宽度不足
        assertFalse(q.matches(500, 700));   // 高度超限
    }

    @Test
    void mediaInsideRuleTargetsParentSelector() {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("@max-width 500", Map.of("paddingAll", 2));   // 直接声明，目标=父选择器
        // （主题文件里不需要 type——那是页面元素的事）
        Theme t = ThemeParser.parse("t", new LinkedHashMap<>(Map.of(".card", card)));

        assertEquals(1, t.rules().size());
        StyleRule r = t.rules().get(0);
        assertEquals(".card", r.selector().toString());
        assertNotNull(r.media());
        assertEquals(2, r.declarations().get("paddingAll"));
    }

    // 媒体覆盖在布局期现判现用

    @Test
    void mediaOverlayAppliesAtLayoutTimeByWindowSize() {
        // 元素带一条 "max-width 854 时 opacity=0.5" 的媒体覆盖
        Map<String, Object> ir = new LinkedHashMap<>();
        ir.put("panel", box("rect"));
        Page p = page(ir);
        Element el = p.elements().get(0);

        MediaQuery cond = MediaQuery.parse("@max-width 854");
        el.setStyle(new ElementStyle(Map.of(),
                java.util.List.of(),
                java.util.List.of(new ElementStyle.MediaOverride(
                        cond, Map.of("opacity", 0.5), 10, 0))));

        // 窗口 800 宽：命中 → opacity 被覆盖
        var small = LayoutEngine.layout(p, 800, 600);
        assertEquals(0.5, small.get(0).opacity());

        // 同一个页面模型，窗口 1200 宽：不命中 → 保持默认
        var big = LayoutEngine.layout(p, 1200, 800);
        assertEquals(1.0, big.get(0).opacity(), "超窗宽后覆盖必须失效");
    }

    @Test
    void mediaOverlayDoesNotTouchOtherElements() {
        Map<String, Object> ir = new LinkedHashMap<>();
        Map<String, Object> a = box("rect");
        ir.put("a", a);
        ir.put("b", box("rect"));
        Page p = page(ir);
        p.elements().get(0).setStyle(new ElementStyle(Map.of(),
                java.util.List.of(),
                java.util.List.of(new ElementStyle.MediaOverride(
                        MediaQuery.ALWAYS, Map.of("opacity", 0.25), 10, 0))));

        var nodes = LayoutEngine.layout(p, 800, 600);
        assertEquals(0.25, nodes.get(0).opacity());
        assertEquals(1.0, nodes.get(1).opacity(), "没有覆盖层的元素不受影响");
    }

    // 解析容错

    @Test
    void badMediaKeyIsSkippedGracefully() {
        Map<String, Object> ir = new LinkedHashMap<>();
        ir.put("@什么玩意", Map.of("text", Map.of("color", "#FFF")));
        assertDoesNotThrow(() -> ThemeParser.parse("t", ir));
    }
}
