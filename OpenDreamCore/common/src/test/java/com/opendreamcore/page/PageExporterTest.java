package com.opendreamcore.page;

import com.opendreamcore.config.PageSchema;
import com.opendreamcore.config.YamlParser;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 页面导出测试：Page → YAML → 重新解析 → 模型一致（round-trip）。
 */
class PageExporterTest {

    private static Page page(String yaml) {
        Map<String, Object> ir = new YamlParser().parse(yaml);
        return PageSchema.build("测试页", ir);
    }

    @Test
    void roundTripPreservesStructure() {
        Page original = page("""
                match: 菜单
                title: 商店
                display: screen
                coin: 100
                allowEscClose: true
                Functions:
                  open: |-
                    Sound.播放音效("minecraft:block.chest.open")
                header:
                  type: text
                  x: 10
                  y: "parent.width / 2"
                  width: 200
                  text: {content: "标题", color: "#FFD54F", align: center}
                  visibleWhen: "vars.coin >= 100"
                  actions:
                    click: |-
                      Chat.发送消息("点了")
                panel:
                  type: layout
                  x: 0
                  y: 40
                  width: 300
                  children:
                    ok:
                      type: button
                      y: 0
                      button: {label: "确定"}
                """);
        String yaml = PageExporter.toYaml(original);
        Page reparsed = page(yaml);

        assertEquals(original.title(), reparsed.title());
        assertEquals(original.match().target(), reparsed.match().target());
        assertEquals(original.displayMode(), reparsed.displayMode());
        assertEquals(100, reparsed.variables().get("coin"));
        assertEquals(true, reparsed.options().get("allowEscClose"));
        assertEquals(original.functions().get("open"), reparsed.functions().get("open"));
        assertEquals(original.elements().size(), reparsed.elements().size());

        Element header = reparsed.elements().get(0);
        assertEquals("text", header.type());
        assertEquals("10", header.layout().x());
        assertEquals("parent.width / 2", header.layout().y());
        assertEquals("标题", ((Map<?, ?>) header.props().get("text")).get("content"));
        assertEquals("#FFD54F", ((Map<?, ?>) header.props().get("text")).get("color"));
        assertEquals("vars.coin >= 100", header.visibleWhen());
        assertEquals("Chat.发送消息(\"点了\")", header.actions().get("click"));

        Element panel = reparsed.elements().get(1);
        assertEquals(1, panel.children().size());
        assertEquals("ok", panel.children().get(0).id());
        assertEquals("确定", ((Map<?, ?>) panel.children().get(0).props().get("button")).get("label"));
    }

    @Test
    void numericStringsStayStrings() {
        // "true"/数字字符串在序列化后不能被重解析成布尔/数字
        Page original = page("""
                a:
                  type: text
                  text: {content: "true", color: "123"}
                """);
        String yaml = PageExporter.toYaml(original);
        Page reparsed = page(yaml);
        Map<?, ?> text = (Map<?, ?>) reparsed.elements().get(0).props().get("text");
        assertEquals("true", text.get("content"), "字符串 true 保持字符串");
        assertEquals("123", text.get("color"), "数字串保持字符串");
    }
}
