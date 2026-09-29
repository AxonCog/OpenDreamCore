package com.opendreamcore.visual;

import com.opendreamcore.config.YamlParser;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 头顶两系统模板自检：Blood 模板可解析且含背景/动态前景/数值文本三要素；
 * HeadTag 模板保留线A新键示例（contains/offsetX/offsetY/health_ratio）；
 * 全部注册模板必须能被 YamlParser 解析（新系统落模板时跑一遍防手误）。
 */
class VisualTemplatesTest {

    @Test
    void bloodRegisteredAndParses() {
        var all = VisualTemplates.all();
        assertTrue(all.containsKey("Blood"), "Blood 模板必须注册进 all()");
        Map<String, Object> ir = new YamlParser().parse(all.get("Blood"));
        assertNotNull(ir, "Blood 模板必须可解析");
        assertEquals("rect", ((Map<?, ?>) ir.get("血条底")).get("type"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void bloodTemplateHasDynamicBarAndNumericText() {
        Map<String, Object> ir = new YamlParser().parse(VisualTemplates.all().get("Blood"));
        Map<String, Object> fg = (Map<String, Object>) ir.get("血条前景");
        assertEquals("rect", fg.get("type"));
        assertEquals("2 * health_ratio", String.valueOf(fg.get("width")),
                "前景宽度必须随 health_ratio 动态伸缩");
        Map<String, Object> txt = (Map<String, Object>) ir.get("血条数值");
        assertEquals("text", txt.get("type"));
        Map<String, Object> spec = (Map<String, Object>) txt.get("text");
        assertEquals("{health}/{health_max}", String.valueOf(spec.get("content")));
    }

    @Test
    void headTagTemplateKeepsLineAKeys() {
        String t = VisualTemplates.all().get("HeadTag");
        assertNotNull(t);
        assertTrue(t.contains("contains:"), "模板应示例 contains 键");
        assertTrue(t.contains("offsetX:"), "模板应示例 offsetX 键");
        assertTrue(t.contains("offsetY:"), "模板应示例 offsetY 键");
        assertTrue(t.contains("health_ratio"), "模板应示例 health_ratio 变量");
    }

    @Test
    void allRegisteredTemplatesParse() {
        for (var e : VisualTemplates.all().entrySet()) {
            Map<String, Object> ir = new YamlParser().parse(e.getValue());
            assertNotNull(ir, e.getKey() + " 模板必须可解析");
        }
    }
}
