package com.opendreamcore.ui;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * item_model 组件规格：字段默认值、表达式原样保留、交互标记。
 */
class ItemModelSpecTest {

    @Test
    void defaults() {
        ItemModelSpec s = ItemModelSpec.parse(Map.of());
        assertEquals(1.0, ((Number) s.scale).doubleValue());
        assertEquals(0.0, ((Number) s.rotateY).doubleValue());
        assertEquals(1.0, ((Number) s.alpha).doubleValue());
        assertFalse(s.followMouse);
        assertEquals(0.0, s.cooldown);
    }

    @Test
    void fieldsAndExpression() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("item", "minecraft:diamond_sword");
        p.put("followMouse", true);
        p.put("scale", "player.level * 2");
        p.put("rotateY", 45);
        p.put("tip", "看剑");
        p.put("cooldown", 2);
        ItemModelSpec s = ItemModelSpec.parse(p);
        assertEquals("minecraft:diamond_sword", s.item);
        assertTrue(s.followMouse);
        assertEquals("player.level * 2", s.scale);
        assertEquals(45, ((Number) s.rotateY).intValue());
        assertEquals("看剑", s.tip);
        assertEquals(2.0, s.cooldown);
    }

    @Test
    void onClickInFunctions() {
        Map<String, Object> fn = new LinkedHashMap<>();
        fn.put("onClick", "Title.显示(\"&6\", \"买它\", 10, 20, 10)");
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("Functions", fn);
        assertTrue(ItemModelSpec.parse(p).hasOnClick);
    }
}