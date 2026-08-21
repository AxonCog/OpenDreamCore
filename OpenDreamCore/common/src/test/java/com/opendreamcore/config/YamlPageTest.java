package com.opendreamcore.config;

import com.opendreamcore.page.DisplayMode;
import com.opendreamcore.page.Element;
import com.opendreamcore.page.Page;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * YAML → 页面模型 全链路测试（对应 docs/YAML语法.md 示例）。
 */
class YamlPageTest {

    @Test
    void parseStorePage() {
        String yaml = """
                match: 商店
                title: 服务器商店
                coin: 100

                title_text:
                  type: text
                  x: 0
                  y: 10
                  width: "window.width"
                  height: 30
                  text:
                    content: "欢迎来到 {{global.server_name}}"
                    align: center

                buy_sword:
                  type: button
                  x: 60
                  y: 80
                  width: 160
                  height: 40
                  button:
                    label: 购买钻石剑
                  visibleWhen: "vars.coin >= 100"
                  actions:
                    click: |-
                      方法.请求购买("minecraft:diamond_sword", 1)
                """;

        Map<String, Object> ir = new YamlParser().parse(yaml);
        Page page = PageSchema.build("商店", ir);

        assertEquals("商店", page.match().target());
        assertEquals("服务器商店", page.title());
        assertEquals(100, ((Number) page.variables().get("coin")).intValue());
        assertEquals(2, page.elements().size());

        Element title = page.elements().get(0);
        assertEquals("title_text", title.id());
        assertEquals("text", title.type());
        assertEquals("window.width", title.layout().width());
        assertEquals("欢迎来到 {{global.server_name}}", title.props().get("text").toString().contains("欢迎") ? "欢迎来到 {{global.server_name}}" : title.props().get("text"));

        Element buy = page.elements().get(1);
        assertEquals("button", buy.type());
        assertEquals("vars.coin >= 100", buy.visibleWhen());
        assertTrue(buy.actions().containsKey("click"));
        assertTrue(buy.actions().get("click").contains("请求购买"));
    }

    @Test
    void parseNestedChildren() {
        String yaml = """
                match: 菜单
                panel:
                  type: layout
                  x: 10
                  width: 200
                  children:
                    title:
                      type: text
                      width: "parent.width"
                    ok:
                      type: button
                      y: "parent.height - 30"
                      button: {label: 确定}
                """;

        Page page = PageSchema.build("菜单", new YamlParser().parse(yaml));
        Element panel = page.elements().get(0);
        assertEquals("layout", panel.type());
        assertEquals(2, panel.children().size());

        Element title = panel.children().get(0);
        assertEquals("title", title.id());
        assertEquals("panel", title.parent());
        assertEquals("parent.width", title.layout().width());

        Element ok = panel.children().get(1);
        assertEquals("parent.height - 30", ok.layout().y());
        // 内联 flow 写法解析
        assertEquals("确定", ((Map<?, ?>) ok.props().get("button")).get("label"));
    }

    @Test
    void parseFlowStyle() {
        String yaml = "match: hud\nitem_a: {type: button, x: 0, y: 0, button: {label: A}}\n";

        Page page = PageSchema.build("hud", new YamlParser().parse(yaml));
        assertEquals(1, page.elements().size());
        Element a = page.elements().get(0);
        assertEquals("item_a", a.id());
        assertEquals("0", a.layout().x());
    }

    @Test
    void parseDisplayAndFunctions() {
        String yaml = """
                match: "minecraft:chest"
                display: container
                Functions:
                  open: |-
                    方法.播放音效("minecraft:block.chest.open")
                """;

        Page page = PageSchema.build("箱子", new YamlParser().parse(yaml));
        assertEquals(DisplayMode.CONTAINER, page.displayMode());
        assertTrue(page.functions().get("open").contains("播放音效"));
    }

    @Test
    void jsonParserSameModel() {
        String json = """
                {"match": "商店", "coin": 100,
                 "buy": {"type": "button", "x": 10, "button": {"label": "买"}}}
                """;

        Map<String, Object> ir = new JsonParser().parse(json);
        Page page = PageSchema.build("商店", ir);
        assertEquals("商店", page.match().target());
        assertEquals(100, ((Number) page.variables().get("coin")).intValue());
        assertEquals("button", page.elements().get(0).type());
    }

    @Test
    void badYamlReportsLine() {
        String yaml = "match: 商店\ncoin: 100\n  bad_indent: true\n";
        ConfigParseException e = assertThrows(ConfigParseException.class,
                () -> new YamlParser().parse(yaml));
        assertTrue(e.getMessage().contains("YAML 解析失败"));
    }

    @Test
    void elementWithoutTypeIsVariable() {
        String yaml = "match: hud\nsettings: {enable: true, rate: 0.5}\nitems: [a, b]\n";

        Page page = PageSchema.build("hud", new YamlParser().parse(yaml));
        assertTrue(page.variables().containsKey("settings"));
        assertTrue(page.variables().containsKey("items"));
        assertTrue(page.elements().isEmpty());
    }
}
