package com.opendreamcore.page;

import com.opendreamcore.config.ConfigParseException;
import com.opendreamcore.config.YamlParser;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * import 模板复用测试：元素级/页面级/children 内联、变量合并、循环防护。
 */
class PageImporterTest {

    private static Map<String, Object> parse(String yaml) {
        return new YamlParser().parse(yaml);
    }

    /** 简易页面源：id → YAML。 */
    private static PageImporter.PageSource source(Map<String, String> pages) {
        return pageId -> {
            String yaml = pages.get(pageId);
            return yaml == null ? null : parse(yaml);
        };
    }

    @Test
    void elementLevelImportInlinesWithPrefixAndOffset() {
        Map<String, String> pages = new LinkedHashMap<>();
        pages.put("card_tpl", """
                title: 模板标题
                card:
                  type: layout
                  x: 0
                  y: 0
                  width: 200
                  height: 60
                  children:
                    label:
                      type: text
                      text: {content: "{{vars.name}}"}
                """);
        Map<String, Object> out = PageImporter.expand(parse("""
                coin: 100
                my_card:
                  type: import
                  page: card_tpl
                  prefix: mc_
                  x: 30
                  y: 40
                  vars: {name: 张三}
                """), source(pages));

        // 变量：目标页缺省并入，import 上的 vars 覆盖
        assertEquals("模板标题", out.get("title"));
        assertEquals("张三", out.get("name"));
        assertEquals(100, out.get("coin"));
        // 元素：id 加前缀、数字坐标加偏移
        assertTrue(out.containsKey("mc_card"), "导入元素应带前缀 id");
        @SuppressWarnings("unchecked")
        Map<String, Object> card = (Map<String, Object>) out.get("mc_card");
        assertEquals(30.0, card.get("x"));
        assertEquals(40.0, card.get("y"));
        assertTrue(card.containsKey("children"));
        @SuppressWarnings("unchecked")
        Map<String, Object> children = (Map<String, Object>) card.get("children");
        assertTrue(children.containsKey("mc_label"), "子元素 id 也应加前缀");
        // 原始元素不应泄漏
        assertFalse(out.containsKey("card"));
    }

    @Test
    void pageLevelImportsList() {
        Map<String, String> pages = new LinkedHashMap<>();
        pages.put("header_tpl", """
                logo:
                  type: text
                  text: {content: "LOGO"}
                """);
        Map<String, Object> out = PageImporter.expand(parse("""
                imports:
                  - page: header_tpl
                    prefix: hdr_
                """), source(pages));
        assertTrue(out.containsKey("hdr_logo"));
    }

    @Test
    void importInsideChildrenMergesSiblings() {
        Map<String, String> pages = new LinkedHashMap<>();
        pages.put("slot_tpl", """
                slot:
                  type: layout
                  width: 40
                  height: 40
                """);
        Map<String, Object> out = PageImporter.expand(parse("""
                row:
                  type: layout
                  children:
                    a:
                      type: layout
                    import_slots:
                      type: import
                      page: slot_tpl
                """), source(pages));
        @SuppressWarnings("unchecked")
        Map<String, Object> children = (Map<String, Object>) out.get("row");
        assertNotNull(children);
        @SuppressWarnings("unchecked")
        Map<String, Object> kids = (Map<String, Object>) children.get("children");
        assertTrue(kids.containsKey("a"), "原有子元素保留");
        assertTrue(kids.containsKey("slot_tpl_slot"), "导入元素并成兄弟");
        assertFalse(kids.containsKey("import_slots"), "import 占位不应残留");
    }

    @Test
    void nestedImportResolvesRecursively() {
        Map<String, String> pages = new LinkedHashMap<>();
        pages.put("base_tpl", """
                base_el:
                  type: layout
                """);
        pages.put("mid_tpl", """
                mid_el:
                  type: import
                  page: base_tpl
                """);
        Map<String, Object> out = PageImporter.expand(parse("""
                top:
                  type: import
                  page: mid_tpl
                """), source(pages));
        // 直接目标页 mid_tpl 的前缀套在最外层，内部 base_tpl 的元素也带自身前缀
        assertTrue(out.containsKey("mid_tpl_base_tpl_base_el"), "嵌套 import 应递归展开");
    }

    @Test
    void cycleDetectionThrows() {
        Map<String, String> pages = new LinkedHashMap<>();
        pages.put("a", """
                b_ref:
                  type: import
                  page: b
                """);
        pages.put("b", """
                a_ref:
                  type: import
                  page: a
                """);
        ConfigParseException ex = assertThrows(ConfigParseException.class,
                () -> PageImporter.expand(parse("""
                        a_ref:
                          type: import
                          page: a
                        """), source(pages)));
        assertTrue(ex.getMessage().contains("循环引用"));
    }

    @Test
    void missingTargetThrows() {
        assertThrows(ConfigParseException.class, () -> PageImporter.expand(parse("""
                x:
                  type: import
                  page: not_exist
                """), source(Map.of())));
    }

    @Test
    void localVariablesWinOverImported() {
        Map<String, String> pages = new LinkedHashMap<>();
        pages.put("tpl", """
                name: 模板名
                el:
                  type: layout
                """);
        Map<String, Object> out = PageImporter.expand(parse("""
                name: 本页名
                tpl_ref:
                  type: import
                  page: tpl
                """), source(pages));
        assertEquals("本页名", out.get("name"), "本页已有变量优先");
    }
}
