package com.opendreamcore.adapter.dreamcore;

import com.opendreamcore.config.YamlParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 龙核原生系统配置翻译金测试——样本就是服务器 plugins/DragonCore/ 下
 * 真实跑着的文件快照（resources/dragoncore/systems/，KeyConfig.yml 带
 * BOM 也是故意的，Windows 编辑器写出来就这样）。
 * 逐系统断言字段映射，近似点与丢弃项见 DreamCoreSystems 注释。
 */
class DreamCoreSystemsTest {

    private static Map<String, Object> load(String name) throws Exception {
        try (var in = DreamCoreSystemsTest.class.getResourceAsStream("/dragoncore/systems/" + name)) {
            assertNotNull(in, "样本缺失: " + name);
            // 桥接层同样用宽松解析：龙核配置有重复键
            return YamlParser.lenient().parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void fileNameRouting() {
        assertEquals("KeyConfig", DreamCoreSystems.systemFor("KeyConfig.yml"));
        assertEquals("HeadTag", DreamCoreSystems.systemFor("Blood.yml"));
        assertEquals("SlotConfig", DreamCoreSystems.systemFor("slotconfig.YML"));
        assertEquals("", DreamCoreSystems.systemFor("ItemTip.yml"), "ItemTip 是界面页，走 GUI 解析器");
        assertEquals("", DreamCoreSystems.systemFor("ItemModel.yml"), "模型替换规划外");
    }

    @Test
    void keyConfigKeepsCooldownAndCommands() throws Exception {
        Map<String, Map<String, Object>> rules = DreamCoreSystems.keyConfig(load("KeyConfig.yml"));
        assertTrue(rules.containsKey("T"), "BOM 剥掉后首个键名是干净的");
        Map<String, Object> t = rules.get("T");
        assertEquals(1, ((Number) t.get("cooldown")).intValue());
        assertTrue(t.get("commands") instanceof java.util.List<?> l && !l.isEmpty(),
                "命令规整成列表");
        assertTrue(String.valueOf(((java.util.List<?>) t.get("commands")).get(0))
                .startsWith("[console]dragoncore opengui %player%"), "命令原文保留（前缀大小写与占位符由执行器兜）");
        assertTrue(rules.containsKey("地区传送"), "中文键名直通");
    }

    @Test
    void itemIconMapsTypeToIdAndDropsUnknowns() throws Exception {
        Map<String, Map<String, Object>> rules = DreamCoreSystems.itemIcon(load("ItemIcon.yml"));
        Map<String, Object> mantis = rules.get("巨钳螳螂");
        assertEquals("可靠的伙伴", mantis.get("match"));
        assertEquals("212.png", mantis.get("texture"));
        assertEquals("5856", mantis.get("id"), "数字旧 id → 字符串 id");
        assertFalse(mantis.containsKey("mode"), "mode 无对应，丢弃");
        Map<String, Object> q1 = rules.get("品质1");
        assertEquals("https://s3.bmp.ovh/imgs/2024/09/28/27aa68f4be6e8dfc.gif", q1.get("texture"),
                "http 贴图直通");
        assertFalse(q1.containsKey("width"), "width 丢弃");
    }

    @Test
    void worldTextureFlattensAndRenames() throws Exception {
        Map<String, Map<String, Object>> rules = DreamCoreSystems.worldTexture(load("WorldTexture.yml"));
        Map<String, Object> flat = rules.get("随便一个名字");
        assertEquals("backpack.png", flat.get("texture"), "path→texture");
        assertFalse(flat.containsKey("rotateX"), "rotateX 丢弃（我们只有 rotate_y）");
        assertEquals(50, ((Number) flat.get("rotate_y")).intValue(), "rotateY→rotate_y");
        assertFalse(flat.containsKey("follow"), "follow:false 不生成 options");
        Map<String, Object> nested = rules.get("_DragonCore_bs_png");
        assertNotNull(nested, "嵌套写法 /DragonCore/bs→png 摊平");
        assertEquals("zuanshi", nested.get("world"));
        Map<String, Object> followOne = rules.get("_DragonCore_bs_png");
        assertTrue(followOne.get("options") instanceof Map<?, ?> o
                && ((Map<?, ?>) o.get("world")) != null
                && Boolean.TRUE.equals(((Map<?, ?>) ((Map<?, ?>) o.get("world"))).get("follow")),
                "follow:true → options.world.follow");
        assertFalse(rules.get("bs_png").containsKey("options"), "follow:false 不生成 options");
    }

    @Test
    void armorLayerSplitsTextureIntoLayers() throws Exception {
        Map<String, Map<String, Object>> rules = DreamCoreSystems.armorLayer(load("ArmorLayer.yml"));
        Map<String, Object> one = rules.get("随便一个名称啦");
        assertEquals("阿巴阿巴", one.get("match"));
        assertEquals("armor/diamond_layer_1.png", one.get("layer1"));
        assertEquals("armor/diamond_layer_2.png", one.get("layer2"));
    }

    @Test
    void fontConfigMapsPathToTexture() throws Exception {
        Map<String, Map<String, Object>> rules = DreamCoreSystems.fontConfig(load("FontConfig.yml"));
        Map<String, Object> first = rules.get("闇");
        assertEquals("chenghao/1.gif", first.get("texture"));
        assertEquals(8.5, ((Number) first.get("height")).doubleValue(), 1e-9);
    }

    @Test
    void slotConfigUnwrapsLimitList() throws Exception {
        Map<String, Map<String, Object>> rules = DreamCoreSystems.slotConfig(load("SlotConfig.yml"));
        Map<String, Object> extra = rules.get("额外槽位1");
        assertEquals(Boolean.TRUE, extra.get("attribute"), "兼容开关直通");
        Map<String, Object> pendant = rules.get("吊坠槽位");
        assertEquals("吊坠", pendant.get("lore"), "lore|吊坠 → lore");
        assertEquals("essentials.use", pendant.get("permission"), "permission|x → permission");
        Map<String, Object> necklace = rules.get("项链槽位");
        assertEquals("项链", necklace.get("lore_contains"), "裸值按 lore 包含处理");
        assertFalse(rules.containsKey("Script"), "龙核服务端脚本块不进规则库");
    }

    @Test
    void bloodBecomesHeadTagPage() throws Exception {
        Map<String, Map<String, Object>> rules = DreamCoreSystems.blood(load("Blood.yml"));
        Map<String, Object> baka = rules.get("baka");
        assertEquals("*", baka.get("entity"), "龙核血条对全体生物，通配");
        assertEquals(0.3, ((Number) baka.get("y")).doubleValue(), 1e-9, "offsetY 3 → y 0.3（脚底→头顶近似折算）");
        Map<?, ?> bg = (Map<?, ?>) baka.get("血条底");
        assertEquals("image", bg.get("type"));
        assertEquals("xx.png", ((Map<?, ?>) bg.get("image")).get("src"));
        assertEquals(4.0, ((Number) ((Map<?, ?>) bg.get("hologram")).get("width")).doubleValue(), 1e-9,
                "200px → 4 格（50px=1格）");
        Map<?, ?> bar = (Map<?, ?>) baka.get("血条");
        assertEquals("(5+entity.health*190) / 50", bar.get("width"), "%health%→实体上下文，套像素折算");
        Map<?, ?> txt = (Map<?, ?>) baka.get("血字");
        assertEquals("text", txt.get("type"));
        assertEquals("卧槽无情", ((Map<?, ?>) txt.get("text")).get("content"));
    }

    @Test
    void bloodVarsRewrite() {
        assertEquals("5+entity.health*190", DreamCoreSystems.rewriteBloodVars("5+%health%*190"));
        assertEquals("entity.name", DreamCoreSystems.rewriteBloodVars("%name%"));
        assertEquals("entity.health_ratio", DreamCoreSystems.rewriteBloodVars("%per%"));
        assertEquals("原样", DreamCoreSystems.rewriteBloodVars("原样"));
    }
}
