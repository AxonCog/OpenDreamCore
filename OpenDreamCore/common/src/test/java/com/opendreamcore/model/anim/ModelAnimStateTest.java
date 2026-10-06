package com.opendreamcore.model.anim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P6：动画意图状态（Model.播放动画 / 相机 model_anim 事件点的公共底座）。
 */
class ModelAnimStateTest {

    @Test
    void setAndClear() {
        ModelAnimState s = ModelAnimState.get();
        long v0 = s.version();
        long v1 = s.set("walk");
        assertEquals("walk", s.current());
        assertTrue(s.version() > v0, "版本单调递增");
        long v2 = s.clear();
        assertNull(s.current());
        assertTrue(v2 > v1, "clear 也推进版本（胶水可见变化）");
        // 恢复干净状态，避免影响其他用例/运行期
        s.clear();
    }

    @Test
    void setTrimsAndNullClears() {
        ModelAnimState s = ModelAnimState.get();
        s.set("  attack  ");
        assertEquals("attack", s.current(), "首尾空白修剪");
        s.set(null);
        assertNull(s.current(), "null 等价 clear");
        s.set("   ");
        assertNull(s.current(), "空串等价 clear");
        s.clear();
    }

    @Test
    void versionNeverGoesBackwards() {
        ModelAnimState s = ModelAnimState.get();
        long v = s.version();
        s.set("a");
        s.set("b");
        s.clear();
        s.set("c");
        assertTrue(s.version() >= v + 4, "每次操作都推进版本");
        s.clear();
    }
}
