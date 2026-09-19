package com.opendreamcore.visual;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 按键组合纯匹配测试：按下集合 ⊇ 组合全集；冷却由执行器计时，不在本类。
 */
class KeyComboTest {

    @Test
    void plusSeparatedKeysParsed() {
        KeyCombo combo = new KeyCombo("C+LEFT_SHIFT+左键");
        assertEquals(Set.of("C", "LEFT_SHIFT", "左键"), combo.keys());
        assertFalse(combo.isEmpty());
    }

    @Test
    void matchesRequiresFullCoverage() {
        KeyCombo combo = new KeyCombo("C+LEFT_SHIFT+左键");
        assertTrue(combo.matches(Set.of("C", "LEFT_SHIFT", "左键")));
        assertTrue(combo.matches(Set.of("C", "LEFT_SHIFT", "左键", "多余的键")),
                "按下集合可以比组合更大");
        assertFalse(combo.matches(Set.of("C", "LEFT_SHIFT")));
        assertFalse(combo.matches(Set.of()));
        assertFalse(combo.matches(null));
    }

    @Test
    void emptyComboNeverMeaningful() {
        assertTrue(new KeyCombo("").isEmpty());
        assertTrue(new KeyCombo("+").isEmpty());
    }

    @Test
    void canonicalNormalizesModifierAliasesAndOrder() {
        // 驼峰/全大写/左右之分/加号或空格，全归一成规范串：修饰键固定排序 + '+' 连接
        assertEquals("CTRL+SHIFT+左键", KeyCombo.canonical("Ctrl+Shift+左键"));
        assertEquals("CTRL+SHIFT+左键", KeyCombo.canonical("C+LEFT_SHIFT+左键"));
        assertEquals("CTRL+SHIFT+左键", KeyCombo.canonical("shift 左键 + ctrl"));
        assertEquals("CTRL+R", KeyCombo.canonical("Ctrl+R"));
        assertEquals("R", KeyCombo.canonical("R"));
        assertEquals("CTRL", KeyCombo.canonical("Ctrl")); // 只有修饰键：不炸
        assertEquals("", KeyCombo.canonical(""));
    }

    @Test
    void modifierGroupCoversControlAliases() {
        assertEquals("CTRL", KeyCombo.modifierGroup("C"));
        assertEquals("CTRL", KeyCombo.modifierGroup("CTRL"));
        assertEquals("CTRL", KeyCombo.modifierGroup("LEFT_CONTROL"));
        assertEquals("CTRL", KeyCombo.modifierGroup("RIGHT_CTRL"));
        assertEquals("SHIFT", KeyCombo.modifierGroup("shift"));
        assertEquals("ALT", KeyCombo.modifierGroup("ALT"));
        assertNull(KeyCombo.modifierGroup("左键"));
        assertNull(KeyCombo.modifierGroup("R"));
    }
}
