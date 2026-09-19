package com.opendreamcore.visual;

import com.opendreamcore.config.PageSchema;
import com.opendreamcore.page.Page;
import com.opendreamcore.ui.LayoutEngine;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * HeadTag 实体上下文求值：
 * 规则里的元素表达式引用 entity.*（血量比例等），布局时按实体实况解析。
 * 实现路径：客户端把实体实况作为页面变量注入（entity 嵌套 map），
 * LayoutEngine 的表达式环境天然支持点路径取值。
 */
class HeadTagEntityContextTest {

    @Test
    void healthRatioDrivesBarWidth() {
        // 页面变量注入实体实况：entity.health_ratio = 0.25
        Map<String, Object> entityVars = Map.of(
                "entity", Map.of("health_ratio", 0.25, "name", "僵尸王"));

        Map<String, Object> bar = new LinkedHashMap<>();
        bar.put("type", "rect");
        bar.put("x", -0.88);
        bar.put("y", 0.11);
        bar.put("width", "2 * entity.health_ratio");
        bar.put("height", 0.28);

        Map<String, Object> ir = new LinkedHashMap<>();
        ir.put("entity", entityVars.get("entity"));   // 平铺进页面变量
        ir.put("血条", bar);

        Page page = PageSchema.build("headtag_test", ir);
        var nodes = LayoutEngine.layout(page, 800, 600);

        assertEquals(0.5, nodes.get(0).width(),
                "width = 2 × 0.25 → 血条宽度随血量比例变化");
    }

    @Test
    void entityNameResolvesInTextExpression() {
        Map<String, Object> ir = new LinkedHashMap<>();
        ir.put("entity", Map.of("name", "僵尸王"));
        Map<String, Object> label = new LinkedHashMap<>();
        label.put("type", "text");
        // 文本内容走插值体系：{entity.name} 由渲染层替换；这里验证表达式形态
        label.put("width", "entity.name == '僵尸王' ? 30 : 10");
        ir.put("名字宽", label);

        Page page = PageSchema.build("headtag_name", ir);
        var nodes = LayoutEngine.layout(page, 800, 600);
        assertEquals(30.0, nodes.get(0).width(), "实体名称参与条件表达式");
    }
}
