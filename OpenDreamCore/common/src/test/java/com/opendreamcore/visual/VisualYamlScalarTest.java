package com.opendreamcore.visual;

import com.opendreamcore.config.YamlParser;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * VisualYaml 标量转义回归：服务端 dump 的文本客户端要能原样读回。
 *
 * <p>{@code entity: *} 不加引号时客户端直接解析失败、整条规则作废（2026-10 名牌不显示）。
 */
public class VisualYamlScalarTest {

    private static void roundTrip(String value) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("k", value);
        String dumped = VisualYaml.dump(root);
        Map<String, Object> back = YamlParser.lenient().parse(dumped);
        assertNotNull(back, "dump 后应能解析: " + value + " -> " + dumped);
        assertEquals(value, back.get("k"), "往返值必须一致: " + dumped);
    }

    @Test
    public void yamlIndicatorLeadingCharsRoundTrip() {
        // 指示符开头的值：YAML 会读成别名/锚点/标签/块标量/流集合
        String[] risky = {"*", "&anchor", "!tag", "|pipe", ">gt", "%percent", "@at", "`tick",
                "{brace", "[bracket", ",comma", "#hash", "?question", ":colon", "-dash",
                "'quote", "\"dquote"};
        for (String s : risky) {
            roundTrip(s);
        }
    }

    @Test
    public void wildcardValueRoundTripsAsStringNotAlias() {
        // 名牌/血条「匹配全体」就是靠它，单测钉死不再裸奔
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("entity", "*");
        String dumped = VisualYaml.dump(root);
        assertTrue(dumped.contains("\"*\""), "* 必须加引号: " + dumped);
        Map<String, Object> back = YamlParser.lenient().parse(dumped);
        assertEquals("*", back.get("entity"));
    }

    @Test
    public void typeLookingStringsStayStrings() {
        for (String s : Arrays.asList("true", "false", "yes", "no", "on", "off", "null", "~",
                "123", "1.5", "-3", "0x1F", "007")) {
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("v", s);
            Map<String, Object> back = YamlParser.lenient().parse(VisualYaml.dump(root));
            assertNotNull(back, s + " 应能解析");
            assertEquals(s, back.get("v"), "必须读回字符串而不是布尔/数字: " + s);
        }
    }

    @Test
    public void embeddedPunctuationAndSpacesRoundTrip() {
        roundTrip("Screen.设置变量(\"odc_blood_alive\", entity.health > 0)");
        roundTrip("a: b");
        roundTrip("trailing: ");
        roundTrip("has # hash");
        roundTrip("  前后空格  ");
        roundTrip("");
        roundTrip("反斜杠 \\ 与引号 \" 混排");
    }

    @Test
    public void multiLineStringRoundTrips() {
        roundTrip("第一行\n第二行\n第三行");
        roundTrip("带引号的多行\n\"q\" 与 \\ 反斜杠\n结尾");
    }

    @Test
    public void nestedStructuresRoundTrip() {
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("type", "image");
        inner.put("src", "xx.png");
        inner.put("size", 12.5);
        inner.put("flag", Boolean.TRUE);

        Map<String, Object> el = new LinkedHashMap<>();
        el.put("image", inner);
        el.put("x", 0.0);

        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("entity", "*");
        rule.put("血条", el);
        List<Object> list = new ArrayList<>();
        list.add("*");
        list.add("a, b");
        list.add(3.5);
        rule.put("关键帧", list);

        // dump 的契约是「序列化一条规则」：顶层就是规则字段表
        String dumped = VisualYaml.dump(rule);
        Map<String, Object> backRule = YamlParser.lenient().parse(dumped);
        assertNotNull(backRule);
        @SuppressWarnings("unchecked")
        Map<String, Object> backEl = (Map<String, Object>) backRule.get("血条");
        @SuppressWarnings("unchecked")
        Map<String, Object> backImage = (Map<String, Object>) backEl.get("image");
        assertEquals("xx.png", backImage.get("src"));
        assertEquals(12.5, ((Number) backImage.get("size")).doubleValue(), 1e-9);
        assertEquals(Boolean.TRUE, backImage.get("flag"));
        @SuppressWarnings("unchecked")
        List<Object> backList = (List<Object>) backRule.get("关键帧");
        assertEquals("*", backList.get(0));
        assertEquals("a, b", backList.get(1));
        assertEquals(3.5, ((Number) backList.get(2)).doubleValue(), 1e-9);
    }
}