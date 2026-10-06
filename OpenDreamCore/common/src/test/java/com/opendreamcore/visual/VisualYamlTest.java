package com.opendreamcore.visual;

import com.opendreamcore.config.YamlParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 规则序列化自检：服务端下发的是文本，客户端只解析——所以 dump → parse 必须回到同一个 IR。
 * 重点覆盖曾经压烂的「表列表」（相机的 keyframes）。
 */
class VisualYamlTest {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> roundTrip(Map<String, Object> ir) throws Exception {
        String text = VisualYaml.dump(ir);
        return new YamlParser().parse(text);
    }

    @Test
    void keyframesListOfMapsSurvivesRoundTrip() throws Exception {
        Map<String, Object> kf0 = new java.util.LinkedHashMap<>();
        kf0.put("t", 0.0);
        kf0.put("pos", List.of(100, 72, 0));
        kf0.put("fov", 70);
        Map<String, Object> kf1 = new java.util.LinkedHashMap<>();
        kf1.put("t", 8.0);
        kf1.put("pos", List.of(120, 68, 20));
        kf1.put("look", "实体:国王");

        Map<String, Object> ir = new java.util.LinkedHashMap<>();
        ir.put("type", "path");
        ir.put("duration", 8.0);
        ir.put("keyframes", List.of(kf0, kf1));

        String text = VisualYaml.dump(ir);
        assertTrue(text.contains("- t:"), "表列表必须落成真块序列，实际:\n" + text);
        assertTrue(!text.contains("{t="), "不能出现 String.valueOf(map) 那种烂行，实际:\n" + text);

        Map<String, Object> back = roundTrip(ir);
        assertEquals("path", back.get("type"));
        assertEquals(8.0, ((Number) back.get("duration")).doubleValue(), 1e-9);
        assertInstanceOf(List.class, back.get("keyframes"));
        List<?> frames = (List<?>) back.get("keyframes");
        assertEquals(2, frames.size(), "实际:\n" + text);
        Map<?, ?> f0 = (Map<?, ?>) frames.get(0);
        assertEquals(0.0, ((Number) f0.get("t")).doubleValue(), 1e-9);
        assertEquals(3, ((List<?>) f0.get("pos")).size());
        assertEquals(70.0, ((Number) f0.get("fov")).doubleValue(), 1e-9);
        Map<?, ?> f1 = (Map<?, ?>) frames.get(1);
        assertEquals("实体:国王", f1.get("look"), "含冒号的值必须引起来才能原样回来");
    }

    @Test
    void scalarListsBecomeFlowSequencesAndSurvive() throws Exception {
        // 曾经把标量列表写成「每行重复同一个 key」——那是重复键，客户端解析器直接报错。
        // 两元素以上在旧实现下必然中招（transition/interrupt/shake 都是两元素）。
        Map<String, Object> ir = new java.util.LinkedHashMap<>();
        ir.put("interrupt", List.of("damage", "move"));
        ir.put("shake", List.of(0.0, 20));
        ir.put("single", List.of("only"));
        ir.put("withComma", List.of("a,b", "c"));
        ir.put("position", List.of(1, 2, 3));
        String text = VisualYaml.dump(ir);
        assertTrue(text.contains("interrupt: [damage, move]"), text);
        assertTrue(text.contains("shake: [0.0, 20]"), text);
        assertTrue(text.contains("position: [1, 2, 3]"), text);
        assertTrue(text.contains("single: [only]"), "单元素也统一 flow: " + text);
        assertTrue(text.contains("withComma: [\"a,b\", c]"), "flow 里带逗号的值要引起来: " + text);

        Map<String, Object> back = roundTrip(ir);
        assertEquals(List.of("damage", "move"), back.get("interrupt"));
        assertEquals(2, ((List<?>) back.get("shake")).size());
        assertEquals(0.0, ((Number) ((List<?>) back.get("shake")).get(0)).doubleValue(), 1e-9);
        assertEquals(List.of("only"), back.get("single"));
        assertEquals(List.of("a,b", "c"), back.get("withComma"));
        assertEquals(List.of(1, 2, 3), back.get("position"));
    }

    @Test
    void stringsWithYamlMetacharactersStayStrings() throws Exception {
        Map<String, Object> ir = new java.util.LinkedHashMap<>();
        ir.put("players", "@a[distance=..32]");
        ir.put("look", "实体:剧情NPC_01");
        ir.put("note", "带 # 号的注释样文字");
        ir.put("empty", "  ");
        Map<String, Object> back = roundTrip(ir);
        assertEquals("@a[distance=..32]", back.get("players"));
        assertEquals("实体:剧情NPC_01", back.get("look"));
        assertEquals("带 # 号的注释样文字", back.get("note"));
        assertEquals("  ", back.get("empty"), "空白串原样回来（不 strip）");
    }

    @Test
    void numbersAndBooleansAreNotQuoted() {
        String text = VisualYaml.dump(new java.util.LinkedHashMap<>(Map.of(
                "duration", 8.0, "loop", false, "zero", 0, "pad", 7)));
        assertTrue(text.contains("duration: 8.0"), text);
        assertTrue(text.contains("loop: false"), text);
        assertTrue(text.contains("zero: 0"), text);
        assertTrue(!text.contains("\"8.0\"") && !text.contains("\"false\""), "数值/布尔不该被引起来: " + text);
    }

    @Test
    void emptyListAndNestedMapSurvive() throws Exception {
        Map<String, Object> nested = new java.util.LinkedHashMap<>();
        nested.put("inner", List.of("x"));
        Map<String, Object> ir = new java.util.LinkedHashMap<>();
        ir.put("emptyList", List.of());
        ir.put("child", nested);
        Map<String, Object> back = roundTrip(ir);
        assertEquals(List.of(), back.get("emptyList"));
        assertEquals(List.of("x"), ((Map<?, ?>) back.get("child")).get("inner"),
                "嵌套表里的标量列表也要读回列表（flow）");
    }
}
