package com.opendreamcore.visual;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 槽位规则测试：limit 条件平铺（无容器包裹）的校验语义。
 */
class SlotRuleTest {

    private static final com.opendreamcore.visual.ItemView POTION =
            new ItemView("potion", "小型治疗药水", List.of("§f[类型]§6药水", "恢复 4 点生命"), Map.of());

    @Test
    void flatConditionsAllMustPass() {
        SlotRule rule = new SlotRule("吊坠槽", Map.of(
                "lore", "吊坠",
                "permission", "essentials.use",
                "level", 10));

        var okPlayer = Map.<String, Object>of("permission:essentials.use", true, "level", 12);
        assertTrue(rule.allows(new ItemView("pendant", "吊坠·烈焰", List.of("吊坠"), Map.of()), okPlayer));
        // 物品不满足
        assertFalse(rule.allows(new ItemView("pendant", "别的", List.of("随便"), Map.of()), okPlayer));
        // 权限不足
        assertFalse(rule.allows(new ItemView("pendant", "吊坠", List.of("吊坠"), Map.of()),
                Map.of("level", 12)));
        // 等级不足
        assertFalse(rule.allows(new ItemView("pendant", "吊坠", List.of("吊坠"), Map.of()),
                Map.<String, Object>of("permission:essentials.use", true, "level", 3)));
    }

    @Test
    void loreContainsSingleLineForm() {
        SlotRule rule = new SlotRule("药水槽1", Map.of("lore_contains", "[类型]药水"));
        assertTrue(rule.allows(POTION, null));
        assertFalse(rule.allows(new ItemView("potion", "别的药水", List.of("无关"), Map.of()), null));
    }

    @Test
    void legacyNestedLimitStillAccepted() {
        // 兼容旧写法：limit 内嵌块的条件并入顶层
        SlotRule rule = new SlotRule("药水槽2", Map.of(
                "limit", Map.of("lore_contains", "[类型]药水")));
        assertTrue(rule.allows(POTION, null));
    }

    @Test
    void attributeAndSkinAreOptionalFlags() {
        SlotRule bare = new SlotRule("普通槽", Map.of());
        assertFalse(bare.attributeHook());
        SlotRule with = new SlotRule("时装槽", Map.of("attribute", true, "skin", true));
        assertTrue(with.attributeHook());
        assertTrue(with.skinHook());
    }
}
