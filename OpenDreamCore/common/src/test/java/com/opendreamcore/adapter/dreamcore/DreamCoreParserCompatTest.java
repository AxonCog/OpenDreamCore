package com.opendreamcore.adapter.dreamcore;

import org.junit.jupiter.api.Test;
import com.opendreamcore.script.engine.DreamLang;
import com.opendreamcore.script.engine.Scope;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DreamCoreParserCompatTest {
    @Test
    void nullInputsAreSafe() {
        assertFalse(DreamCoreParser.isDreamCoreFormat(null));
        assertEquals(Map.of(), DreamCoreParser.transform(null));
    }

    @Test
    void legacyFieldsMapToSchemaConsumers() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("field", new LinkedHashMap<>(Map.of(
                "type", "textbox", "texts", "旧文本", "enable", false, "visible", "vars.show")));
        Map<String, Object> field = cast(DreamCoreParser.transform(raw).get("field"));
        assertEquals("input", field.get("type"));
        assertEquals(false, field.get("enabledWhen"));
        assertEquals("vars.show", field.get("visibleWhen"));

        raw.clear();
        raw.put("caption", new LinkedHashMap<>(Map.of(
                "type", "label", "texts", "旧标题", "text", Map.of("content", "显式"))));
        Map<String, Object> caption = cast(DreamCoreParser.transform(raw).get("caption"));
        assertEquals("text", caption.get("type"));
        assertEquals("显式", ((Map<?, ?>) caption.get("text")).get("content"));
    }


    @Test
    void rewrittenScopedAssignmentsExecuteAcrossStatements() {
        Scope scope = new Scope();
        String rewritten = LegacyExpressionRewriter.rewrite(
                "界面变量.a = 1; 界面变量.b = 界面变量.a + 1;");
        DreamLang.execute(rewritten, scope);
        assertEquals(1.0, ((Number) scope.resolve("vars", "odc_ui_a")).doubleValue());
        assertEquals(2.0, ((Number) scope.resolve("vars", "odc_ui_b")).doubleValue());
    }

    @Test
    void rewrittenTernaryConcatAndColorCodeExecute() {
        Scope scope = new Scope();
        String rewritten = LegacyExpressionRewriter.rewrite(
                "界面变量.value = true ? return 0 : return 1; "
                        + "界面变量.text = 'x' & 'y'; 界面变量.color = '§c';");
        DreamLang.execute(rewritten, scope);
        assertEquals(0.0, ((Number) scope.resolve("vars", "odc_ui_value")).doubleValue());
        assertEquals("xy", scope.resolve("vars", "odc_ui_text"));
        assertEquals("§c", scope.resolve("vars", "odc_ui_color"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Object value) {
        assertTrue(value instanceof Map<?, ?>);
        return (Map<String, Object>) value;
    }
}
