package com.opendreamcore.config;

import com.opendreamcore.page.Page;
import com.opendreamcore.script.DreamLang;
import com.opendreamcore.script.Scope;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 4.5 ConfigIR 声明式变换管线：transforms 列表 → DreamLang 表达式改写 IR 路径，
 * extras 透传，内置进 PageSchema.build。
 */
class IrTransformsTest {

    private static Map<String, Object> ir(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    private static Map<String, Object> transform(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    @Test
    void rewriteByExpression() {
        List<Object> ts = new ArrayList<>();
        ts.add(transform("field", "gold", "set", "value * 2"));
        Map<String, Object> ir = ir("gold", 50, "transforms", ts);
        IrTransforms.apply(ir);
        assertEquals(100.0, ((Number) ir.get("gold")).doubleValue(), "value=50 乘 2 = 100");
        assertFalse(ir.containsKey("transforms"), "变换跑完自身摘掉");
    }

    @Test
    void whenGateSkips() {
        List<Object> ts = new ArrayList<>();
        ts.add(transform("field", "gold", "when", "1 == 2", "set", "value - 1"));
        Map<String, Object> ir = ir("gold", 50, "transforms", ts);
        IrTransforms.apply(ir);
        assertEquals(50.0, ((Number) ir.get("gold")).doubleValue(), "when false 不动原值");
    }

    @Test
    void extrasPassthroughToExpression() {
        List<Object> ts = new ArrayList<>();
        ts.add(transform("field", "gold", "set", "value + bonus"));
        Map<String, Object> ir = ir("gold", 50, "bonus", 7, "transforms", ts);
        IrTransforms.apply(ir);
        assertEquals(57.0, ((Number) ir.get("gold")).doubleValue(), "非标准顶层键 bonus 透传可读");
        assertEquals(7, ir.get("bonus"), "extras 键本身保留");
    }

    @Test
    void nestedPathAutoCreates() {
        List<Object> ts = new ArrayList<>();
        ts.add(transform("field", "options.hud.baseline", "set", "'1920x1080'"));
        Map<String, Object> ir = ir("transforms", ts);
        IrTransforms.apply(ir);
        @SuppressWarnings("unchecked")
        Map<String, Object> options = (Map<String, Object>) ir.get("options");
        assertNotNull(options, "中间层自动补 map");
        assertEquals("1920x1080", ((Map<?, ?>) options.get("hud")).get("baseline"),
                "点路径写入生效");
    }

    @Test
    void singleMapTransformAccepted() {
        Map<String, Object> ir = ir("gold", 10,
                "transforms", transform("field", "gold", "set", "value + 5"));
        IrTransforms.apply(ir);
        assertEquals(15.0, ((Number) ir.get("gold")).doubleValue(), "单个 Map 也当列表跑");
    }

    @Test
    void dreamLangStringLiteralDebug() {
        Object v = DreamLang.evaluate("'1920x1080'", new Scope());
        assertNotNull(v, "字符串字面量求值不应为 null");
        assertEquals("1920x1080", String.valueOf(v));
        Object doubleV = DreamLang.evaluate("\"1920x1080\"", new Scope());
        assertEquals("1920x1080", String.valueOf(doubleV), "双引号也支持");
    }

    @Test
    void pageSchemaIntegration() {
        List<Object> ts = new ArrayList<>();
        ts.add(transform("field", "gold", "set", "value * 3"));
        Map<String, Object> ir = ir("gold", 20, "title", "shop", "transforms", ts);
        Page page = PageSchema.build("shop_x", ir);
        assertEquals(60.0, ((Number) page.variables().get("gold")).doubleValue(),
                "PageSchema.build 内部先跑变换");
        assertFalse(page.variables().containsKey("transforms"), "transforms 不进变量表");
        assertEquals("shop", page.title());
    }
}