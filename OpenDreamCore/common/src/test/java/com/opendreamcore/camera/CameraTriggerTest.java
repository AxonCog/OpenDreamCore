package com.opendreamcore.camera;

import com.opendreamcore.config.YamlParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M1.6 触发器/事件点自检：纯解析与纯几何，全部可离线断言。
 */
class CameraTriggerTest {

    @SuppressWarnings("unchecked")
    private static CameraRule rule(String yaml, String id) throws Exception {
        Map<String, Object> root = new YamlParser().parse(yaml);
        return CameraRule.parse(id, (Map<String, Object>) root.get(id));
    }

    @Test
    void keyTriggerParsed() throws Exception {
        CameraRule r = rule("开场:\n  type: path\n  keyframes: [{pos: [1,2,3], t: 0}]\n"
                + "  触发:\n    按键: R\n", "开场");
        assertNotNull(r.trigger);
        assertEquals(1, r.trigger.keys.size());
        assertEquals("r", r.trigger.keys.get(0));
        assertNull(r.trigger.pos1);
        assertNull(r.trigger.event);
        assertTrue(r.trigger.present());
    }

    @Test
    void areaTriggerParsedAndOnce() throws Exception {
        CameraRule r = rule("开场:\n  type: shot\n"
                + "  触发:\n    区域: {pos1: [0, 64, 0], pos2: [10, 70, 10], once: false}\n", "开场");
        assertNotNull(r.trigger);
        assertNotNull(r.trigger.pos1);
        assertNotNull(r.trigger.pos2);
        assertFalse(r.trigger.once, "once: false 必须原样保留");
    }

    @Test
    void eventTriggerParsed() throws Exception {
        CameraRule r = rule("开场:\n  type: shot\n  触发: {事件: 进世界}\n", "开场");
        assertNotNull(r.trigger);
        assertEquals("进世界", r.trigger.event);
    }

    @Test
    void noTriggerIsNull() throws Exception {
        CameraRule r = rule("开场:\n  type: shot\n", "开场");
        assertNull(r.trigger);
    }

    @Test
    void boxContainsInclusiveBothOrders() {
        double[] p1 = {0, 64, 0};
        double[] p2 = {10, 70, 10};
        assertTrue(CameraTrigger.contains(new double[]{5, 67, 5}, p1, p2));
        assertTrue(CameraTrigger.contains(new double[]{0, 64, 0}, p1, p2), "边界含");
        assertTrue(CameraTrigger.contains(new double[]{10, 70, 10}, p1, p2), "对角角点含");
        assertFalse(CameraTrigger.contains(new double[]{11, 67, 5}, p1, p2));
        assertFalse(CameraTrigger.contains(null, p1, p2));
        // 两角任意顺序
        assertTrue(CameraTrigger.contains(new double[]{5, 67, 5}, p2, p1));
    }

    @Test
    void eventsParseAndDueWindow() throws Exception {
        CameraRule r = rule("开场:\n  type: path\n  keyframes: [{pos: [0,0,0], t: 0}]\n"
                + "  事件:\n"
                + "    - {时刻: 2.5, 类型: sound, 值: 'minecraft:entity.experience_orb.pickup', 音量: 0.5}\n"
                + "    - {时刻: 4, 类型: particle, 值: 'minecraft:heart', 偏移: [0, 1.5, 0]}\n"
                + "    - {时刻: 6, 类型: script, 值: 'UI.提示(\"中段\")'}\n"
                + "    - {时刻: 8, 类型: animation, 值: xxx}\n", "开场");
        assertEquals(4, r.events.size());
        assertEquals(CameraEvent.Kind.SOUND, r.events.get(0).kind);
        assertEquals(0.5, r.events.get(0).volume, 1e-9);
        assertEquals(CameraEvent.Kind.PARTICLE, r.events.get(1).kind);
        assertEquals(1.5, r.events.get(1).offset[1], 1e-9);
        assertEquals(CameraEvent.Kind.SCRIPT, r.events.get(2).kind);
        assertEquals(CameraEvent.Kind.ANIMATION, r.events.get(3).kind, "animation 解析入库（接口预留）");

        // due 窗口：(prev, now]
        assertTrue(CameraEvent.due(r.events, 0.0, 0.1).isEmpty());
        assertEquals(1, CameraEvent.due(r.events, 2.0, 2.5).size());
        assertTrue(CameraEvent.due(r.events, 2.51, 3.0).isEmpty());
        assertEquals(2, CameraEvent.due(r.events, 3.9, 6.0).size());
        // 开场即触发（t<=0）
        List<CameraEvent> now0 = CameraEvent.startup(CameraEvent.parseList(
                java.util.Arrays.asList((Object) new java.util.LinkedHashMap<String, Object>())));
        assertEquals(0, now0.size());
    }

    @Test
    void lookAnglesMcSemantics() {
        // +X 方向：yaw = -90；正上方：pitch = -90（向上看为负）
        double[] a = CameraEffects.lookAngles(new double[]{0, 0, 0}, new double[]{10, 0, 0});
        assertEquals(-90.0, a[0], 1e-6);
        assertEquals(0.0, a[1], 1e-6);
        double[] up = CameraEffects.lookAngles(new double[]{0, 0, 0}, new double[]{0, 5, 0});
        assertEquals(-90.0, up[1], 1e-6);
        double[] down = CameraEffects.lookAngles(new double[]{0, 5, 0}, new double[]{0, 0, 0});
        assertEquals(90.0, down[1], 1e-6);
        // +Z 方向：yaw = 0
        double[] z = CameraEffects.lookAngles(new double[]{0, 0, 0}, new double[]{0, 0, 10});
        assertEquals(0.0, z[0], 1e-6);
        // 同点：安全零
        double[] same = CameraEffects.lookAngles(new double[]{1, 1, 1}, new double[]{1, 1, 1});
        assertEquals(0.0, same[0], 0.0);
        assertEquals(0.0, same[1], 0.0);
    }
}
