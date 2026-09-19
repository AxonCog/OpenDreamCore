package com.opendreamcore.ui.theme;

import com.opendreamcore.config.PageSchema;
import com.opendreamcore.page.Element;
import com.opendreamcore.page.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 主题系统单元测试：选择器解析、级联优先级、变量解析，
 * 以及"嵌套 children 与扁平 parent 两种页面语法全兼容"的硬保证。
 */
class ThemeTest {

    @BeforeEach
    void resetLibrary() {
        ThemeLibrary.get().clear();
    }

    // 构造工具：统一走 PageSchema 真实解析链路

    /** 单元素页面：ir 形如 {元素id: {type..}, ...}，支持 class/parent/children 全部键。 */
    private static Page buildPage(Map<String, Object> ir) {
        return PageSchema.build("t", ir);
    }

    private static Element el(Page page, String id) {
        return findDeep(page, id);
    }

    private static Element findDeep(Page page, String id) {
        for (Element root : page.elements()) {
            Element r = findIn(root, id);
            if (r != null) {
                return r;
            }
        }
        return null;
    }

    private static Element findIn(Element root, String id) {
        if (root.id().equals(id)) {
            return root;
        }
        for (Element c : root.children()) {
            Element r = findIn(c, id);
            if (r != null) {
                return r;
            }
        }
        return null;
    }

    private static Map<String, Object> textEl(String type, String cls) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        if (cls != null) {
            m.put("class", cls);
        }
        return m;
    }

    // 选择器解析

    @Test
    void selectorParsesTagClassIdAndStates() {
        StyleSelector s = StyleSelector.parse("button:hover");
        assertNotNull(s);
        assertEquals(1, s.compoundCount());
        assertTrue(s.hasState());
        assertEquals("hover", s.stateKey().iterator().next());

        StyleSelector c = StyleSelector.parse(".card > .title");
        assertNotNull(c);
        assertEquals(2, c.compoundCount());
        assertTrue(c.isChildLink(0));
        assertFalse(c.hasState());

        StyleSelector d = StyleSelector.parse(".modal .footer button");
        assertNotNull(d);
        assertEquals(3, d.compoundCount());
        assertFalse(d.isChildLink(0));
        assertFalse(d.isChildLink(1));

        assertNull(StyleSelector.parse(">"));
        assertNull(StyleSelector.parse(""));
        assertNull(StyleSelector.parse("button:"));

        // specificity：id=100、class/伪类=10、tag=1
        assertEquals(100, StyleSelector.parse("#x").specificity());
        assertEquals(10, StyleSelector.parse(".x").specificity());
        assertEquals(1, StyleSelector.parse("text").specificity());
        assertEquals(11, StyleSelector.parse("button:hover").specificity());
    }

    @Test
    void pseudoAttachesWithinCompound() {
        // 复合段级语义："button.card:hover" = 同一元素同时满足 type=button、
        // class 含 card、处于 hover 态；伪类挂在段内任一 simple 上均可。
        StyleSelector s = StyleSelector.parse("button.card:hover");
        assertNotNull(s);
        var simples = s.compounds().get(0).simples();
        assertEquals(2, simples.size());
        boolean hasHover = false;
        for (var simple : simples) {
            if (simple.pseudos().contains("hover")) {
                hasHover = true;
            }
        }
        assertTrue(hasHover, "伪类必须保留在复合段内，不得丢失或自成一段");
        assertTrue(s.stateKey().contains("hover"));
    }

    // 过渡声明

    @Test
    void transitionParsesAllForms() {
        var list = TransitionSpec.parseAll("background 0.25 QUAD_OUT 0.1, opacity .3");
        assertEquals(2, list.size());
        assertEquals("background", list.get(0).property());
        assertEquals(0.25f, list.get(0).duration());
        assertEquals(Ease.QUAD_OUT, list.get(0).ease());
        assertEquals(0.1f, list.get(0).delay());
        assertEquals("opacity", list.get(1).property());
        assertEquals(Ease.LINEAR, list.get(1).ease());
        assertNull(TransitionSpec.parseOne("opacity"));
        assertNull(TransitionSpec.parseOne("opacity abc"));
    }

    // 主题表与级联

    private static Theme sampleTheme() {
        Map<String, Object> ir = new LinkedHashMap<>();
        ir.put("schema", 1);
        ir.put("vars", Map.of("accent", "#71A4F4"));

        Map<String, Object> textRule = new LinkedHashMap<>();
        textRule.put("color", "{accent}");   // 变量引用
        textRule.put("shadow", false);
        ir.put("text", textRule);

        Map<String, Object> cardRule = new LinkedHashMap<>();
        cardRule.put("color", "#FFFFFF");    // class 权重高于 tag，应覆盖同属性
        cardRule.put("paddingAll", 10);
        ir.put(".card", cardRule);

        ir.put(".card > .title", Map.of("fontSize", 12));
        ir.put("button:hover", Map.of("color", "{accent}"));
        return ThemeParser.parse("t", ir);
    }

    @Test
    void cascadeClassBeatsTagInlineBeatsAll() {
        Theme theme = sampleTheme();

        // 无类无内联：tag 规则生效 + 变量已解析
        Page p1 = buildPage(Map.of("lbl", textEl("text", null)));
        ThemeApplier.apply(p1, theme);
        Element plain = el(p1, "lbl");
        assertEquals("#71A4F4", plain.props().get("color"));
        assertEquals(false, plain.props().get("shadow"));

        // 类命中：class 的 color 覆盖 tag（10 > 1），paddingAll 补入
        Page p2 = buildPage(Map.of("panel", textEl("rect", "card")));
        ThemeApplier.apply(p2, theme);
        Element card = el(p2, "panel");
        assertEquals("#FFFFFF", card.props().get("color"));
        assertEquals(10, card.props().get("paddingAll"));

        // 内联永远赢
        Map<String, Object> m = textEl("text", null);
        m.put("color", "#FF0000");
        Page p3 = buildPage(Map.of("lbl", m));
        ThemeApplier.apply(p3, theme);
        assertEquals("#FF0000", el(p3, "lbl").props().get("color"));
    }

    @Test
    void descendantChildSelectorsRespectAncestry() {
        Map<String, Object> ir = new LinkedHashMap<>();
        ir.put(".modal .title", Map.of("fontSize", 9));   // 后代（任意层级）
        ir.put(".card > .title", Map.of("fontSize", 20)); // 直接子代

        // 结构 A：card > wrap(modal) > title —— title 不是 card 直接子代
        Map<String, Object> titleA = textEl("text", "title");
        Map<String, Object> wrap = new LinkedHashMap<>();
        wrap.put("type", "layout");
        wrap.put("class", "modal");
        wrap.put("children", new LinkedHashMap<>(Map.of("title_a", titleA)));
        Map<String, Object> cardA = new LinkedHashMap<>();
        cardA.put("type", "layout");
        cardA.put("class", "card");
        cardA.put("children", new LinkedHashMap<>(Map.of("wrap", wrap)));
        Page pA = buildPage(new LinkedHashMap<>(Map.of("card_a", cardA)));
        ThemeApplier.apply(pA, ThemeParser.parse("t", ir));
        assertEquals(9, el(pA, "title_a").props().get("fontSize"),
                "祖先链上有 modal → 后代规则命中；不是直接子代 → 子代规则不命中");

        // 结构 B：card > title —— 直接子代命中
        Map<String, Object> titleB = textEl("text", "title");
        Map<String, Object> cardB = new LinkedHashMap<>();
        cardB.put("type", "layout");
        cardB.put("class", "card");
        cardB.put("children", new LinkedHashMap<>(Map.of("title_b", titleB)));
        Page pB = buildPage(new LinkedHashMap<>(Map.of("card_b", cardB)));
        ThemeApplier.apply(pB, ThemeParser.parse("t", ir));
        assertEquals(20, el(pB, "title_b").props().get("fontSize"));
    }

    @Test
    void flatParentSyntaxGetsSameThemingAsNested() {
        // 双语法兼容硬保证：同一结构两种写法，主题结果必须一致
        Map<String, Object> rules = new LinkedHashMap<>();
        rules.put(".card", Map.of("background", "#2C2C34"));
        rules.put(".card > .title", Map.of("fontSize", 12));
        Theme theme = ThemeParser.parse("t", rules);

        // 嵌套写法
        Map<String, Object> nTitle = textEl("text", "title");
        Map<String, Object> nCard = new LinkedHashMap<>();
        nCard.put("type", "layout");
        nCard.put("class", "card");
        nCard.put("children", new LinkedHashMap<>(Map.of("n_title", nTitle)));
        Page pNested = buildPage(new LinkedHashMap<>(Map.of("n_card", nCard)));

        // 扁平写法：两个顶层元素，靠 parent 挂父
        Map<String, Object> fCard = textEl("layout", "card");
        Map<String, Object> fTitle = textEl("text", "title");
        fTitle.put("parent", "f_card");
        Map<String, Object> flatIr = new LinkedHashMap<>();
        flatIr.put("f_card", fCard);
        flatIr.put("f_title", fTitle);
        Page pFlat = buildPage(flatIr);

        ThemeApplier.apply(pNested, theme);
        ThemeApplier.apply(pFlat, theme);

        Element nC = el(pNested, "n_card");
        Element nT = el(pNested, "n_title");
        Element fC = el(pFlat, "f_card");
        Element fT = el(pFlat, "f_title");

        assertEquals("#2C2C34", nC.props().get("background"));
        assertEquals("#2C2C34", fC.props().get("background"));
        assertEquals(12, nT.props().get("fontSize"));
        assertEquals(12, fT.props().get("fontSize"),
                "扁平语法经 parent 链解析祖先，子代选择器必须同样命中");
    }

    @Test
    void stateRulesGoToOverlayNotBase() {
        Element btn = el(buildPage(Map.of("btn", textEl("button", null))), "btn");
        ThemeApplier.apply(buildPage(Map.of()), Theme.EMPTY); // 冒烟：空页不炸
        ThemeApplier.apply(pageOf(btn), sampleTheme());

        assertNull(btn.props().get("color"), "带状态规则不允许污染基础属性");
        assertNotNull(btn.style());
        assertNotNull(btn.style().overlay("hover"));
        assertEquals("#71A4F4", btn.style().overlay("hover").get("color"));
    }

    @Test
    void transitionDeclarationParsedFromProps() {
        Map<String, Object> m = textEl("rect", null);
        m.put("transition", "opacity 0.3 CUBIC_OUT");
        Element rect = el(buildPage(Map.of("r", m)), "r");
        ThemeApplier.apply(pageOf(rect), Theme.EMPTY);
        assertNotNull(rect.style());
        assertEquals(1, rect.style().transitions().size());
        assertEquals(Ease.CUBIC_OUT, rect.style().transitions().get(0).ease());
    }

    // 全链路接线

    @Test
    void pageSchemaWiresThemeFromClassAndLibrary() {
        ThemeLibrary.get().register(sampleTheme());
        // default 名字的主题：另建一份同名规则集
        Map<String, Object> ir = new LinkedHashMap<>();
        ir.put("match", "菜单");
        ir.put("lbl", textEl("text", "card"));
        ir.put("theme", "t");

        Page page = PageSchema.build("测试", ir);
        Element e = page.elements().get(0);
        assertEquals(List.of("card"), e.classes());
        assertEquals("#FFFFFF", e.props().get("color"), "显式命名主题经全链路生效");
        assertEquals(10, e.props().get("paddingAll"));
    }

    @Test
    void explicitThemeNameSelectsNonDefaultTheme() {
        ThemeLibrary.get().register(sampleTheme()); // 名字 t，不是 default
        Map<String, Object> ir = new LinkedHashMap<>();
        ir.put("theme", "t");
        ir.put("x", textEl("text", null));
        Page page = PageSchema.build("选主题", ir);
        assertEquals("#71A4F4", page.elements().get(0).props().get("color"));
    }

    @Test
    void oldPagesWithoutThemeFieldsAreUntouched() {
        ThemeLibrary.get().register(sampleTheme());
        Map<String, Object> old = textEl("text", null);
        old.put("color", "#123456");
        Map<String, Object> ir = new LinkedHashMap<>();
        ir.put("theme", "t");
        ir.put("old_lbl", old);
        Page page = PageSchema.build("老页面", ir);
        Element e = page.elements().get(0);
        assertEquals("#123456", e.props().get("color"), "内联值不被主题覆盖");
        assertFalse(e.props().containsKey("paddingAll"), "无 class 不命中任何规则");
    }

    // 辅助

    private static Page pageOf(Element root) {
        List<Element> roots = new ArrayList<>();
        roots.add(root);
        return new Page("single", null, null, null, Map.of(), roots, Map.of(), Map.of());
    }
}
