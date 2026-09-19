package com.opendreamcore.visual;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 双形态加载器测试：根文件 + 同名文件夹共存、同 id 文件夹覆盖根文件、
 * 默认示例自动生成、坏文件 warn 跳过不炸、九系统模板全部可解析。
 */
class VisualRulesTest {

    @TempDir
    Path dataRoot;

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
        // 九个默认模板一个序列都不能有，且全部可被加载器吃回去。
        // 唯一例外：KeyConfig 的命令序列（运行时指令天然有序，实测列表写法最顺手），
        // 但序列项必须是命令字符串（以引号开头），不允许结构嵌套。
        for (var entry : VisualTemplates.all().entrySet()) {
            String system = entry.getKey();
            String yaml = entry.getValue();
            boolean keyConfig = system.equals("KeyConfig");
            for (String line : yaml.split("\n")) {
                String stripped = line.strip();
                if (!stripped.startsWith("- ")) {
                    continue;
                }
                assertTrue(keyConfig && stripped.startsWith("- \""),
                        system + " 默认模板出现非法序列行: " + line);
            }
            Path file = dataRoot.resolve(system + ".yml");
            Files.writeString(file, yaml);
            var rules = VisualRules.load(dataRoot, system);
            assertFalse(rules.isEmpty(), system + " 模板应至少解析出一条规则");
        }
    }
}
