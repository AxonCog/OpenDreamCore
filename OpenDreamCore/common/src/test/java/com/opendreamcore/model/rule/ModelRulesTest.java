package com.opendreamcore.model.rule;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** DreamCore 单行 match 语法与块式的归一化。 */
class ModelRulesTest {

    private static ModelRules.Lookup lookup(String... kv) {
        final Map<String, String> m = new HashMap<String, String>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m::get;
    }

    @Test
    void singleLine() {
        List<ModelRules.Cond> conds = ModelRules.normalize("Name:史莱姆");
        assertEquals(1, conds.size());
        assertEquals("name", conds.get(0).key);
        assertEquals("史莱姆", conds.get(0).value);
    }

    @Test
    void singleLineMulti() {
        List<ModelRules.Cond> conds = ModelRules.normalize("Type:minecraft:blaze;Name:炎魔");
        assertEquals(2, conds.size());
        assertEquals("type", conds.get(0).key);
        assertEquals("minecraft:blaze", conds.get(0).value);
        assertEquals("name", conds.get(1).key);
    }

    @Test
    void firstColonSplit() {
        // 值里带冒号（NBT JSON）：首个冒号后全为值
        List<ModelRules.Cond> conds = ModelRules.normalize("NBT:{display:{Name:\"x:y\"}}");
        assertEquals(1, conds.size());
        assertEquals("{display:{Name:\"x:y\"}}", conds.get(0).value);
    }

    @Test
    void noPrefixMeansName() {
        List<ModelRules.Cond> conds = ModelRules.normalize("史莱姆");
        assertEquals("name", conds.get(0).key);
        assertEquals("史莱姆", conds.get(0).value);
    }

    @Test
    void blockAliases() {
        Map<String, Object> block = new HashMap<String, Object>();
        block.put("custom_name", "炎魔");
        block.put("entity_type", "minecraft:blaze");
        List<ModelRules.Cond> conds = ModelRules.normalize(block);
        assertEquals(2, conds.size());
        assertTrue(conds.get(0).key.equals("name") || conds.get(1).key.equals("name"));
        assertTrue(conds.get(0).key.equals("type") || conds.get(1).key.equals("type"));
        // 物品侧别名
        Map<String, Object> item = new HashMap<String, Object>();
        item.put("item_id", "diamond_sword");
        item.put("item_name", "幽冥剑");
        List<ModelRules.Cond> itemConds = ModelRules.normalize(item);
        assertEquals("id", itemConds.get(0).key);
        assertEquals("name", itemConds.get(1).key);
    }

    @Test
    void unknownKeyDropped() {
        Map<String, Object> block = new HashMap<String, Object>();
        block.put("wat", "x");
        block.put("name", "y");
        assertEquals(1, ModelRules.normalize(block).size());
    }

    @Test
    void matchesAndNamespace() {
        List<ModelRules.Cond> conds = ModelRules.normalize("Type:minecraft:blaze;Name:炎魔");
        assertTrue(ModelRules.matches(conds, lookup("name", "§c炎魔", "type", "blaze")));
        assertTrue(ModelRules.matches(conds, lookup("name", "炎魔", "type", "minecraft:blaze")),
                "值带命名空间、实体不带也应匹配");
        assertFalse(ModelRules.matches(conds, lookup("name", "炎魔", "type", "zombie")));
        assertFalse(ModelRules.matches(conds, lookup("name", "史莱姆", "type", "blaze")));
        assertFalse(ModelRules.matches(ModelRules.normalize((Object) null), lookup("name", "x")));
    }

    @Test
    void listForm() {
        List<ModelRules.Cond> conds = ModelRules.normalize(java.util.Arrays.asList(
                (Object) "Name:a", (Object) "ID:diamond_sword"));
        assertEquals(2, conds.size());
        assertEquals("id", conds.get(1).key);
    }
}
