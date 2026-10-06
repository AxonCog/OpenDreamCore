package com.opendreamcore.visual;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 双形态加载器测试：根文件 + 同名文件夹共存、同 id 文件夹覆盖根文件、
 * 默认示例自动生成、坏文件 warn 跳过不炸、各系统模板全部可解析。
 */
class VisualRulesTest {

    /**
     * 刻意留空（内容全注释）的模板：本地 visual/&lt;系统&gt;.yml 是 _local_override，
     * 解析时最后合并、覆盖服务端同名规则，所以默认模板一旦带生效示例，就会把服务器上
     * 真在用的规则盖掉。FontConfig 于 2026-09-21 因此改成全注释。
     *
     * <p>EntityModel/ItemModel 不在此列：模型资产与规则都在客户端本地（服务器不下发模型
     * 规则时走本地直读），且用户明确要求“真示例而不是只有注释”——两条示例指向 assets/model
     * 里已有的样例模型，删掉整块即可停用。
     */
    private static final java.util.Set<String> INERT_TEMPLATES = java.util.Set.of("FontConfig");

    @TempDir
    Path dataRoot;

    /** 各测试自己的临时根目录（整文件语义用例要在 dataRoot 之外另开，避免互相污染）。 */
    @TempDir
    Path temp;

    @Test
    void rootFileAndFolderCoexistWithFolderOverride() throws Exception {
        // 根文件写两条规则
        Files.writeString(dataRoot.resolve("ItemIcon.yml"), """
                贴图1:
                  match: 测试材质1
                  texture: icons/a.png
                共享:
                  match: 来自根文件
                  texture: icons/from_root.png
                """);

        // 文件夹里放"共享"（同 id → 整体覆盖根文件条目）和一条新规则
        Path dir = dataRoot.resolve("ItemIcon");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("共享.yml"), "match: 来自文件夹\n");
        Files.writeString(dir.resolve("新规则.yml"), "id: diamond_sword\n");

        var rules = VisualRules.load(dataRoot, "ItemIcon");

        assertEquals(3, rules.size());
        assertEquals("来自文件夹", rules.get("共享").get("match"),
                "同 id 时文件夹条目整体覆盖根文件条目");
        assertNull(rules.get("共享").get("texture"),
                "覆盖是整体的：文件夹版本没写的键不会被根文件残留");
        assertEquals("diamond_sword", rules.get("新规则").get("id"));
    }

    @Test
    void folderOnlyFormWorks() throws Exception {
        Path dir = dataRoot.resolve("HeadTag");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("僵尸王血条.yml"), """
                entity: zombie
                distance: 64
                血条底:
                  type: rect
                """);

        var rules = VisualRules.load(dataRoot, "HeadTag");
        assertEquals(1, rules.size());
        var rule = rules.get("僵尸王血条");
        assertEquals("zombie", rule.get("entity"));
        assertEquals(64L, ((Number) rule.get("distance")).longValue());
        assertNotNull(rule.get("血条底"), "嵌套元素树原样保留在 IR 里");
    }

    @Test
    void defaultTemplateGeneratedWhenBothMissing() throws Exception {
        assertFalse(Files.exists(dataRoot.resolve("ItemIcon.yml")));
        assertTrue(VisualRules.ensureDefault(dataRoot, "ItemIcon", VisualTemplates.ITEM_ICON));
        assertTrue(Files.isRegularFile(dataRoot.resolve("ItemIcon.yml")));

        // 已存在时不重复生成（用户改过的内容不被冲掉）
        assertFalse(VisualRules.ensureDefault(dataRoot, "ItemIcon", VisualTemplates.ITEM_ICON));

        // 生成的默认模板必须能被加载器吃回去（自洽）
        var rules = VisualRules.load(dataRoot, "ItemIcon");
        assertTrue(rules.containsKey("贴图1"), "默认模板应解析出示例规则");
    }

    @Test
    void brokenRuleFileIsSkippedNotFatal() throws Exception {
        Path dir = dataRoot.resolve("Sounds");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("坏的.yml"), "这不是合法的 YAML 内容");
        Files.writeString(dir.resolve("好的.yml"), "file: sounds/a.ogg\n");

        var rules = VisualRules.load(dataRoot, "Sounds");
        assertNull(rules.get("坏的"));
        assertEquals("sounds/a.ogg", rules.get("好的").get("file"));
    }

    @Test
    void allNineTemplatesAreFlatAndParseable() throws Exception {
        // 全部默认模板一个根级序列都不能有，且全部可被加载器吃回去。
        // 唯一例外：KeyConfig 的命令序列（运行时指令天然有序，实测列表写法最顺手），
        // 但序列项必须是命令字符串（以引号开头），不允许结构嵌套。
        // 注意只看「零缩进」的行：嵌套结构里的列表（Camera 的 keyframes）合法，
        // 本断言要拦的是「根级写成了序列」——那种文件 VisualRules.load 直接吃不下。
        for (var entry : VisualTemplates.all().entrySet()) {
            String system = entry.getKey();
            String yaml = entry.getValue();
            boolean keyConfig = system.equals("KeyConfig");
            for (String line : yaml.split("\n")) {
                if (!line.startsWith("- ")) {
                    continue;
                }
                assertTrue(keyConfig && line.strip().startsWith("- \""),
                        system + " 默认模板出现根级序列行: " + line);
            }
            Path file = dataRoot.resolve(system + ".yml");
            Files.writeString(file, yaml);
            var rules = VisualRules.load(dataRoot, system);
            if (INERT_TEMPLATES.contains(system)) {
                // 刻意全注释的模板：本地模板是 _local_override（最后合并、覆盖服务端），
                // 示例一旦生效就会盖掉服务器上真在用的规则（详见 FONT_CONFIG 模板注释）
                assertTrue(rules.isEmpty(),
                        system + " 默认模板刻意留空（示例必须注释掉），却有规则: " + rules.keySet());
            } else {
                assertFalse(rules.isEmpty(), system + " 模板应至少解析出一条规则");
            }
        }
    }

    // ---------------------------------------------------------------- HeadTag/Blood 整文件语义

    /** 整文件即一条规则：老壳/龙核写法（entity/distance/Functions + 页面元素在顶层）。 */
    @Test
    void headTagWholeFileRootCountsAsOneRule() throws Exception {
        Path root = temp.resolve("whole");
        Files.createDirectories(root);
        Files.writeString(root.resolve("HeadTag.yml"), String.join("\n",
                "entity: zombie",
                "distance: 64",
                "y: 1.5",
                "Functions:",
                "  tick: |-",
                "    Screen.设置元素(\"名字\", \"text.content\", 方法.取实体名)",
                "血条底:",
                "  type: rect",
                "  x: -1",
                "  y: 0",
                "  width: 2",
                "  height: 0.5",
                "color: \"#AA000000\""));
        var rules = VisualRules.load(root, "HeadTag");
        assertEquals(1, rules.size(), "整文件必须只解析成一条规则（key 取系统名）: " + rules.keySet());
        Map<String, Object> ir = rules.get("HeadTag");
        assertNotNull(ir);
        assertTrue(ir.containsKey("entity"), "页面级标量必须在规则里");
        assertTrue(ir.containsKey("血条底"), "页面元素必须在规则里");
        assertTrue(ir.containsKey("Functions"));
    }

    /** 多规则单文件写法（每个顶层键一条规则）保持可用，不能被整文件语义吃掉。 */
    @Test
    void headTagMultiRuleRootStillSplitsPerKey() throws Exception {
        Path root = temp.resolve("multi");
        Files.createDirectories(root);
        Files.writeString(root.resolve("HeadTag.yml"), String.join("\n",
                "僵尸王:",
                "  entity: zombie",
                "  血条:",
                "    type: rect",
                "骷髅王:",
                "  entity: skeleton",
                "  名字:",
                "    type: text"));
        var rules = VisualRules.load(root, "HeadTag");
        assertEquals(2, rules.size(), "多规则写法应逐键拆分: " + rules.keySet());
        assertTrue(rules.containsKey("僵尸王") && rules.containsKey("骷髅王"));
    }

    /** Blood 与 HeadTag 同语义；ItemIcon 等其余系统不受影响（永远逐键拆分）。 */
    @Test
    void bloodSharesWholeFileSemanticsButItemIconDoesNot() throws Exception {
        Path root = temp.resolve("blood");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Blood.yml"), String.join("\n",
                "entity: \"*\"",
                "distance: 32",
                "血条:",
                "  type: rect"));
        var blood = VisualRules.load(root, "Blood");
        assertEquals(1, blood.size(), "Blood 同样是整文件一条: " + blood.keySet());

        Files.writeString(root.resolve("ItemIcon.yml"), String.join("\n",
                "PAPER:",
                "  item: PAPER",
                "  texture: item/paper.png",
                "BOOK:",
                "  item: BOOK",
                "  texture: item/book.png"));
        var icon = VisualRules.load(root, "ItemIcon");
        assertEquals(2, icon.size(), "ItemIcon 仍逐键拆分: " + icon.keySet());
    }
}
