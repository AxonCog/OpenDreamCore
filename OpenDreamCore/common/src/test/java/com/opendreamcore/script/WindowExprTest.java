package com.opendreamcore.script;

import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** 表达式布局环境回归：window.width 成员链必须能解析（远古端对齐的高版本语义）。 */
class WindowExprTest {
    @Test
    void windowWidthResolves() {
        Scope s = new Scope();
        Map<String, Object> window = new LinkedHashMap<>();
        window.put("width", 480.0);
        window.put("height", 270.0);
        s.assign("window", window);
        Object v = DreamLang.evaluate("window.width / 2 - 170", s);
        assertEquals(70.0, ((Number) v).doubleValue(), 0.001);
    }
}
