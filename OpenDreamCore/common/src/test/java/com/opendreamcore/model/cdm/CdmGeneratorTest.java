package com.opendreamcore.model.cdm;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P4 契约：CDM json 断言测试（gen1 谓词形态 / gen3 select 形态 / 聚合与清洗 / 坏条目跳过）。
 */
public class CdmGeneratorTest {

    private static CdmGenerator.Entry e(String item, String key, int v) {
        return new CdmGenerator.Entry(item, key, v);
    }

    @Test
    public void gen1OverridesPredicate() {
        List<CdmGenerator.Entry> in = new ArrayList<CdmGenerator.Entry>();
        in.add(e("paper", "rpg_skeleton", 7001));
        Map<String, String> out = CdmGenerator.generate(in, CdmGenerator.Gen.V1201);
        assertEquals(1, out.size());
        String path = "assets/minecraft/models/item/paper.json";
        assertTrue(out.containsKey(path));
        String json = out.get(path);
        assertTrue(json.contains("\"predicate\":{\"custom_model_data\":7001}"), json);
        assertTrue(json.contains("\"model\":\"odc:model/rpg_skeleton\""), json);
        assertTrue(json.contains("\"layer0\":\"minecraft:item/paper\""), "保留原版贴图回落");
    }

    @Test
    public void gen3SelectWithStrings() {
        List<CdmGenerator.Entry> in = new ArrayList<CdmGenerator.Entry>();
        in.add(e("paper", "rpg_skeleton", 7001));
        Map<String, String> out = CdmGenerator.generate(in, CdmGenerator.Gen.V1214UP);
        String path = "assets/minecraft/items/paper.json";
        assertTrue(out.containsKey(path));
        String json = out.get(path);
        assertTrue(json.contains("\"property\":\"minecraft:custom_model_data\""), json);
        assertTrue(json.contains("\"when\":[\"7001\"]"), json);
        assertTrue(json.contains("\"fallback\":{\"type\":\"minecraft:model\",\"model\":\"minecraft:item/paper\"}"), json);
        assertFalse(out.containsKey("assets/minecraft/models/item/paper.json"), "gen3 不再写 models/item 形态");
    }

    @Test
    public void sameItemMergesInOrder() {
        List<CdmGenerator.Entry> in = new ArrayList<CdmGenerator.Entry>();
        in.add(e("paper", "model_a", 1));
        in.add(e("paper", "model_b", 2));
        in.add(e("blaze_rod", "model_c", 3));
        Map<String, String> out = CdmGenerator.generate(in, CdmGenerator.Gen.V1201);
        assertEquals(2, out.size());
        String json = out.get("assets/minecraft/models/item/paper.json");
        int a = json.indexOf("model_a");
        int b = json.indexOf("model_b");
        assertTrue(a >= 0 && b > a, "同物品两条 override 按输入顺序合并");
    }

    @Test
    public void namespaceAndKeyCleaned() {
        List<CdmGenerator.Entry> in = new ArrayList<CdmGenerator.Entry>();
        in.add(e("minecraft:paper", "RPG Skeleton!", 7));
        Map<String, String> out = CdmGenerator.generate(in, CdmGenerator.Gen.V1201);
        String json = out.get("assets/minecraft/models/item/paper.json");
        assertTrue(json.contains("odc:model/rpg_skeleton_"), json);
    }

    @Test
    public void badEntriesSkipped() {
        List<CdmGenerator.Entry> in = new ArrayList<CdmGenerator.Entry>();
        in.add(e("", "x", 1));
        in.add(e("paper", "", 1));
        in.add(null);
        in.add(e("paper", "ok", 9));
        Map<String, String> out = CdmGenerator.generate(in, CdmGenerator.Gen.V1201);
        assertEquals(1, out.size(), "坏条目跳过，好条目保留");
        assertTrue(out.get("assets/minecraft/models/item/paper.json").contains("odc:model/ok"));
    }

    @Test
    public void nullInputSafe() {
        assertTrue(CdmGenerator.generate(null, CdmGenerator.Gen.V1201).isEmpty());
    }
}
