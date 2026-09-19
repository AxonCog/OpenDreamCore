package com.opendreamcore.visual;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 统一匹配规范测试：单行 match / 精确键 / 正则 / 组合 AND 语义。
 */
class MatchSpecTest {

    private static ItemView item(String type, String name, String... lore) {
        return new ItemView(type, name, List.of(lore), Map.of());
    }

    @Test
    void singleLineMatchHitsNameOrLore() {
        MatchSpec spec = MatchSpec.parse(Map.of("match", "屠龙"));
        assertTrue(spec.matches(new ItemView("diamond_sword", "〖传说〗屠龙刀", List.of("吸血"), Map.of())));
        assertTrue(spec.matches(new ItemView("book", "普通书", List.of("屠龙者称号"), Map.of())),
                "lore 包含同样命中");
        assertFalse(spec.matches(item("book", "无关", "没词")));
    }

    @Test
    void explicitKeysAreAndSemantics() {
        MatchSpec spec = MatchSpec.parse(Map.of(
                "id", "diamond_sword",
                "name", "神剑",
                "nbt", Map.of("CustomModelData", 7000)));
        assertTrue(spec.matches(new ItemView("diamond_sword", "神剑", List.of(),
                Map.of("CustomModelData", 7000))));
        assertFalse(spec.matches(new ItemView("diamond_sword", "神剑", List.of(),
                Map.of())), "缺 NBT 不命中");
        assertFalse(spec.matches(new ItemView("iron_sword", "神剑", List.of(),
                Map.of("CustomModelData", 7000))), "类型不符不命中");
    }

    @Test
    void commaSeparatedIdIsOrSemantics() {
        MatchSpec spec = MatchSpec.parse(Map.of("id", "diamond_sword, gold_sword"));
        assertTrue(spec.matches(item("gold_sword", "x")));
        assertTrue(spec.matches(item("DIAMOND_SWORD", "x")), "大小写不敏感");
        assertFalse(spec.matches(item("iron_sword", "x")));
    }

    @Test
    void regexAppliesToDisplayName() {
        MatchSpec spec = MatchSpec.parse(Map.of("regex", "^神剑.*"));
        assertTrue(spec.matches(item("sword", "神剑·烈焰版")));
        assertFalse(spec.matches(item("sword", "凡剑")));
    }

    @Test
    void emptySpecMatchesEverything() {
        assertTrue(MatchSpec.parse(Map.of()).matches(item("anything", "随便")));
        assertTrue(MatchSpec.parse(Map.of("match", "")).isEmpty());
    }

    @Test
    void longAliasKeysAccepted() {
        // name_contains / lore_contains 长名写法与短名等价
        MatchSpec spec = MatchSpec.parse(Map.of(
                "name_contains", "神",
                "lore_contains", "吸血"));
        assertTrue(spec.matches(new ItemView("sword", "神剑", List.of("附魔: 吸血"), Map.of())));
        assertFalse(spec.matches(item("sword", "凡剑", "空空如也")));
    }

    @Test
    void colorCodesDoNotBreakContainsMatching() {
        MatchSpec spec = MatchSpec.parse(Map.of("lore_contains", "[类型]药水"));
        assertTrue(spec.matches(new ItemView("potion", "治疗药水",
                List.of("§f[类型]§6药水"), Map.of())),
                "中间夹着色码也应命中（去色码归一化）");
    }

    @Test
    void invalidRegexNeverThrows() {
        MatchSpec bad = MatchSpec.parse(Map.of("regex", "["));
        assertDoesNotThrow(() -> bad.matches(item("x", "任意名称")));
        // 非法正则按不命中处理
        assertFalse(bad.matches(item("x", "任意名称")));
    }
}
