package com.opendreamcore.ui;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * entity / model 组件规格解析：字段默认值、表达式原样保留、交互标记。
 */
class EntityViewSpecTest {

    private static EntityViewSpec parse(String type, Map<String, Object> props) {
        return EntityViewSpec.parse(type, props);
    }

    @Test
    void entityDefaults() {
        EntityViewSpec s = parse("entity", Map.of());
        assertEquals(EntityViewSpec.Kind.ENTITY, s.kind);
        assertEquals(1.0, ((Number) s.scale).doubleValue());
        assertEquals(0.0, ((Number) s.rotateY).doubleValue());
        assertEquals(1.0, ((Number) s.alpha).doubleValue());
        assertFalse(s.followMouse);
        assertTrue(s.hideName);       // 默认藏名牌
        assertEquals(0.0, s.cooldown);
    }

    @Test
    void modelKindAndFields() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("entity", "owner");
        p.put("model", "zombie");
        p.put("followMouse", true);
        p.put("head", false);
        p.put("hideName", false);
        p.put("scale", "player.level * 1.5");
        p.put("rotateY", 45);
        p.put("tip", "看我的模型");
        p.put("cooldown", 1.5);
        EntityViewSpec s = parse("model", p);
        assertEquals(EntityViewSpec.Kind.MODEL, s.kind);
        assertEquals("zombie", s.model);
        assertTrue(s.followMouse);
        assertFalse(s.head);
        assertFalse(s.hideName);
        assertEquals("player.level * 1.5", s.scale); // 表达式原样保留，渲染端求值
        assertEquals(45, ((Number) s.rotateY).intValue());
        assertEquals("看我的模型", s.tip);
        assertEquals(1.5, s.cooldown);
    }

    @Test
    void onClickDetectedInFunctions() {
        Map<String, Object> p = new LinkedHashMap<>();
        Map<String, Object> fn = new LinkedHashMap<>();
        fn.put("onClick", "Title.显示(\"&6\", \"点到了\", 10, 20, 10)");
        p.put("Functions", fn);
        EntityViewSpec s = parse("entity", p);
        assertTrue(s.hasOnClick);
    }
}