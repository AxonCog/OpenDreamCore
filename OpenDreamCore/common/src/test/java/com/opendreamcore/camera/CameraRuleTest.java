package com.opendreamcore.camera;

import com.opendreamcore.config.YamlParser;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 相机规则 IR 解析自检。
 * 重点断言「缺省即安全」：不写 lock_input 绝不锁死、不写 player 必是 keep——
 * 这是规范 §4.1 的硬要求（一个字段漏写不能把玩家锁死在运镜里）。
 */
class CameraRuleTest {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> ruleOf(String yaml, String id) throws Exception {
        Map<String, Object> root = new YamlParser().parse(yaml);
        return (Map<String, Object>) root.get(id);
    }

    @Test
    void shotDefaultsAreSafe() throws Exception {
        CameraRule r = CameraRule.parse("固定机位_城门", ruleOf("""
                固定机位_城门:
                  type: shot
                  position: [128.5, 70.0, -32.5]
                  rotation: [90.0, 15.0]
                """, "固定机位_城门"));
        assertEquals(CameraRule.Type.SHOT, r.type);
        assertArrayEquals(new double[]{128.5, 70.0, -32.5}, r.position, 1e-9);
        assertArrayEquals(new double[]{90.0, 15.0}, r.rotation, 1e-9);
        assertFalse(r.lockInput, "不写 lock_input 必须是不锁（缺省即安全）");
        assertFalse(r.hideHud);
        assertNull(r.player, "不写 player 必须是 keep（null）");
        assertNull(r.perspective);
        assertEquals(1.0, r.stretch, 1e-9);
        assertEquals(1.0, r.speed, 1e-9);
        assertEquals(0, r.transitionIn);
        assertEquals(0, r.transitionOut);
        assertTrue(r.keyframes.isEmpty());
        assertTrue(r.interrupt.isEmpty());
        assertTrue(r.summaryLine().contains("固定机位"));
    }

    @Test
    void pathWithoutTypeKeyBecomesPathAndKeyframesSortByT() throws Exception {
        CameraRule r = CameraRule.parse("运镜_开场", ruleOf("""
                运镜_开场:
                  duration: 8.0
                  keyframes:
                    - t: 8.0
                      pos: [120, 68, 20]
                      look: 实体:国王
                    - t: 0.0
                      pos: [100, 72, 0]
                      fov: 70
                """, "运镜_开场"));
        assertEquals(CameraRule.Type.PATH, r.type, "没写 type 但有关键帧 → 当路径");
        assertEquals(2, r.keyframes.size());
        assertEquals(0.0, r.keyframes.get(0).t, 1e-9, "关键帧按 t 升序");
        assertEquals(70.0, r.keyframes.get(0).fov, 1e-9);
        assertArrayEquals(new double[]{100.0, 72.0, 0.0}, r.keyframes.get(0).pos, 1e-9);
        assertEquals("实体:国王", r.keyframes.get(1).look);
        assertEquals(8.0, r.duration, 1e-9);
        assertTrue(r.summaryLine().contains("2 个关键帧"));
    }

    @Test
    void expressionFormIsPreservedNotParsed() throws Exception {
        CameraRule r = CameraRule.parse("表达式机位", ruleOf("""
                表达式机位:
                  type: shot
                  position: height * 2
                  rotation: yaw + 90
                """, "表达式机位"));
        assertNull(r.position, "表达式值不能被硬解析成坐标");
        assertNull(r.rotation);
        assertEquals("height * 2", r.positionExpr, "表达式原样保留，M1.3 再求值");
        assertEquals("yaw + 90", r.rotationExpr);
    }

    @Test
    void storeAppliesMultiRuleFile() {
        CameraStore store = new CameraStore();
        String summary = store.apply(Map.of("_local", """
                固定机位_a:
                  type: shot
                  position: [1, 2, 3]

                运镜_b:
                  type: path
                  duration: 5
                  keyframes:
                    - t: 0.0
                      pos: [0, 0, 0]
                """));
        assertNotNull(summary);
        assertTrue(summary.contains("机位 1"), summary);
        assertTrue(summary.contains("路径 1"), summary);
        assertEquals(2, store.rules().size());
        assertNotNull(store.byId("固定机位_a"));
        assertEquals(CameraRule.Type.PATH, store.byId("运镜_b").type);
    }

    @Test
    void storeAppliesServerBundleSingleRuleForm() {
        // 服务端 bundle 形态：字段平铺、顶层含 type（单条规则）
        CameraStore store = new CameraStore();
        String summary = store.apply(Map.of("Camera",
                "type: shot\nposition: [5, 6, 7]\nlock_input: true\nhide_hud: true\n"));
        assertNotNull(summary);
        assertEquals(1, store.rules().size());
        assertTrue(store.byId("Camera").lockInput);
        assertTrue(store.byId("Camera").hideHud);
    }

    @Test
    void storeCountsUnparseableFileAsBadWithoutKillingOthers() {
        CameraStore store = new CameraStore();
        Map<String, String> bundles = new java.util.LinkedHashMap<>();
        bundles.put("坏文件", "key: [unclosed");          // YAML 语法错误 → bad 计数
        bundles.put("好规则", "type: shot\nposition: [1, 2, 3]\n");
        String summary = store.apply(bundles);
        assertNotNull(summary);
        assertTrue(summary.contains("1 条解析失败"), summary);
        assertEquals(1, store.rules().size());
        assertNotNull(store.byId("好规则"), "坏文件不能拖垮其它规则");
    }

    @Test
    void emptyApplyIsNoop() {
        assertNull(CameraStore.get().apply(null));
        assertNull(CameraStore.get().apply(Map.of()));
    }

    @Test
    void defaultTemplateSeedsParseableRules() throws Exception {
        // 服务端播种路径：visual/Camera.yml 缺失 → ensureDefault 写模板 → 加载器读回规则。
        // 这条是 task-3 契约“模板种子”那半的机器侧证据（真实播种由插件 reload 触发）；
        // M1.2 起模板多了一条最简两点路径（契约里的「两点直线」验收样例）；
        // M1.3 起再多一条四点环绕（契约：4 点环绕路径播放无抖动无拉丝）。
        java.nio.file.Path root = java.nio.file.Files.createTempDirectory("odc-camera-seed");
        java.nio.file.Files.createDirectories(root);
        String template = com.opendreamcore.visual.VisualTemplates.all().get("Camera");
        assertNotNull(template, "all() 必须含 Camera 模板");
        // 走真实键路径（VisualRuleManager.reload 用的就是这个）
        com.opendreamcore.visual.VisualRules.ensureDefault(root, "Camera", template);
        java.nio.file.Path file = root.resolve("Camera.yml");
        assertTrue(java.nio.file.Files.exists(file), "缺省应写出 Camera.yml");
        var rules = com.opendreamcore.visual.VisualRules.load(root, "Camera");
        assertEquals(4, rules.size(),
                "模板应含 固定机位_城门 + 运镜_两点直线 + 运镜_开场 + 运镜_四点环绕，实际: " + rules.keySet());
        assertTrue(rules.containsKey("固定机位_城门"));
        assertTrue(rules.containsKey("运镜_两点直线"), "M1.2 契约要的两点直线样例必须在模板里");
        assertTrue(rules.containsKey("运镜_开场"));
        assertTrue(rules.containsKey("运镜_四点环绕"), "M1.3 契约要的四点环绕样例必须在模板里");
        // 服务端 bundle 形态（字段平铺含 type）逐条入库，与 ClientController 收到后的形状一致
        CameraStore store = new CameraStore();
        java.util.Map<String, String> bundle = new java.util.LinkedHashMap<>();
        rules.forEach((id, ir) -> bundle.put(id, com.opendreamcore.visual.VisualYaml.dump(ir)));
        String summary = store.apply(bundle);
        assertNotNull(summary);
        assertTrue(summary.contains("机位 1"), summary);
        // 模板里路径 3 条：两点直线 + 开场 + M1.3 的四点环绕；机位仍只有 1 条
        assertTrue(summary.contains("路径 3"), summary);
        assertEquals(4, store.rules().size(), "四条模板规则应全部入库");
    }
}
