package com.opendreamcore.adapter.dreamcore;

import com.opendreamcore.config.PageSchema;
import com.opendreamcore.config.YamlParser;
import com.opendreamcore.page.Page;
import com.opendreamcore.visual.VisualYaml;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 名牌端到端回归：用用户真实服上的龙核 Blood.yml 走「适配器 IR → 下发 YAML 往返 → 页面构建」。
 *
 * <p>规则侧必须出节点；贴图解析是客户端资源加载的事，所以只把贴图名打出来供人工对照。
 */
public class DragonCoreNametagEndToEndTest {

    private static Map<String, Object> loadFixture() throws Exception {
        // 走 classpath：测试宿主的工作目录在 Legacy 模块侧，相对路径取不到 common 的样例
        try (java.io.InputStream in = DragonCoreNametagEndToEndTest.class
                .getResourceAsStream("/nametag/dragoncore-blood-user.yml")) {
            assertNotNull(in, "样例 Blood.yml 应在测试资源里（/nametag/dragoncore-blood-user.yml）");
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Map<String, Object> root = YamlParser.lenient().parse(text);
            assertNotNull(root, "样例 Blood.yml 应能解析");
            assertFalse(root.isEmpty(), "样例 Blood.yml 顶层应有规则");
            return root;
        }
    }

    @Test
    public void userBloodConfigProducesBloodPageWithNodes() throws Exception {
        Map<String, Object> root = loadFixture();
        Map<String, Map<String, Object>> rules = DreamCoreSystems.blood(root);
        assertFalse(rules.isEmpty(), "龙核 Blood.yml 应翻出 Blood 规则");

        List<String> srcs = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> e : rules.entrySet()) {
            String id = e.getKey();
            Map<String, Object> ir = e.getValue();
            // 下发往返：服务端 dump → 客户端 parse，语义必须一致
            String dumped = VisualYaml.dump(ir);
            assertNotNull(dumped);
            Map<String, Object> back = YamlParser.lenient().parse(dumped);
            assertNotNull(back, "往返后应能重新解析: " + id);
            Page page = PageSchema.build("blood_" + id, back);
            assertNotNull(page);
            assertFalse(page.elements().isEmpty(),
                    "Blood 规则 " + id + " 往返后应仍有元素（元素为空 = 名牌画不出来）");
            collectSrcs(back, srcs);
        }
        System.out.println("[nametag-e2e] Blood 节点正常，用到贴图: " + srcs);
        assertFalse(srcs.isEmpty(), "龙核血条配置里应有贴图引用");
    }

    @Test
    public void userBloodConfigNamePartsProduceHeadTagPage() throws Exception {
        Map<String, Object> root = loadFixture();
        Map<String, Map<String, Object>> rules = DreamCoreSystems.headTag(root);
        // 用户的样例没有名字件，允许为空，但不能抛
        for (Map.Entry<String, Map<String, Object>> e : rules.entrySet()) {
            Map<String, Object> back = YamlParser.lenient().parse(VisualYaml.dump(e.getValue()));
            Page page = PageSchema.build("headtag_" + e.getKey(), back);
            assertNotNull(page);
            assertFalse(page.elements().isEmpty(), "HeadTag 规则 " + e.getKey() + " 应有元素");
        }
        System.out.println("[nametag-e2e] HeadTag 规则数: " + rules.size());
    }

    /** 收集 IR 里所有 image.src（贴图链的输入）。 */
    @SuppressWarnings("unchecked")
    private static void collectSrcs(Object node, List<String> out) {
        if (node instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if ("src".equals(String.valueOf(e.getKey())) && e.getValue() != null) {
                    out.add(String.valueOf(e.getValue()));
                } else {
                    collectSrcs(e.getValue(), out);
                }
            }
        } else if (node instanceof List<?> l) {
            for (Object o : l) {
                collectSrcs(o, out);
            }
        }
    }
}