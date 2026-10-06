package com.opendreamcore.camera;

import com.opendreamcore.config.YamlParser;
import com.opendreamcore.visual.VisualYaml;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M1.9 关键帧缓动 + YAML 回环自测。
 *
 * ① ease/loop/duration/smooth 进出 YAML 不丢（面板导出=剪贴板、导入=回灌，走同一条路）；
 * ② held 段定格到下一帧前不动；
 * ③ 帧上的 linear 覆盖全局 smooth（样条只作用于没写 ease 的段）。
 */
class CameraKeyframeEaseTest {

    @SuppressWarnings("unchecked")
    private static CameraRule rule(String yaml, String id) throws Exception {
        Map<String, Object> root = new YamlParser().parse(yaml);
        return CameraRule.parse(id, (Map<String, Object>) root.get(id));
    }

    @Test
    void easeFieldRoundTripsThroughYamlDump() throws Exception {
        String yaml = """
                运镜_缓动:
                  type: path
                  duration: 6.0
                  loop: true
                  smooth: false
                  keyframes:
                    - t: 0.0
                      pos: [0, 64, 0]
                      rot: [0, 0]
                      ease: held
                    - t: 3.0
                      pos: [10, 64, 0]
                      rot: [90, 0]
                      ease: linear
                    - t: 6.0
                      pos: [20, 64, 0]
                      rot: [0, 0]
                """;
        CameraRule r = rule(yaml, "运镜_缓动");
        assertTrue(r.loop, "loop 应解析为 true");
        assertFalse(r.smooth, "smooth: false 应解析生效");
        assertEquals(6.0, r.duration, 1e-9);
        assertEquals("held", r.keyframes.get(0).ease);
        assertEquals("linear", r.keyframes.get(1).ease);
        assertNull(r.keyframes.get(2).ease, "没写 ease 的帧应为 null（跟随全局）");

        // dump → 再解析 → 关键字段不丢
        Map<String, Object> parsed = new YamlParser().parse(yaml);
        String dumped = VisualYaml.dump((Map<String, Object>) parsed.get("运镜_缓动"));
        CameraRule r2 = CameraRule.parse("运镜_缓动",
                (Map<String, Object>) new YamlParser().parse(dumped));
        assertEquals("held", r2.keyframes.get(0).ease);
        assertEquals("linear", r2.keyframes.get(1).ease);
        assertTrue(r2.loop);
        assertFalse(r2.smooth);
        assertEquals(6.0, r2.duration, 1e-9);
        assertEquals(10.0, r2.keyframes.get(1).pos[0], 1e-9);
    }

    @Test
    void heldKeyframeHoldsUntilNext() throws Exception {
        CameraRule r = rule("""
                运镜_定格:
                  type: path
                  keyframes:
                    - t: 0.0
                      pos: [0, 64, 0]
                      rot: [0, 0]
                      ease: held
                    - t: 4.0
                      pos: [10, 64, 0]
                      rot: [0, 0]
                """, "运镜_定格");
        CameraTimeline.Pose mid = CameraTimeline.sample(r, 2.0);
        assertEquals(0.0, mid.pos[0], 1e-9, "held 段中点应仍停在起始帧");
        CameraTimeline.Pose end = CameraTimeline.sample(r, 4.0);
        assertEquals(10.0, end.pos[0], 1e-9, "到下一帧应到位");
    }

    @Test
    void linearKeyframeOverridesGlobalSmooth() throws Exception {
        CameraRule r = rule("""
                运镜_直线段:
                  type: path
                  keyframes:
                    - t: 0.0
                      pos: [0, 64, 0]
                      rot: [0, 0]
                      ease: linear
                    - t: 2.0
                      pos: [4, 64, 0]
                      rot: [0, 0]
                    - t: 4.0
                      pos: [4, 64, 10]
                      rot: [0, 0]
                """, "运镜_直线段");
        CameraTimeline.Pose mid = CameraTimeline.sample(r, 1.0);
        assertEquals(2.0, mid.pos[0], 1e-9, "linear 段中点 = 纯 lerp（全局 smooth 不影响该段）");
    }
}
