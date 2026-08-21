package com.opendreamcore.script;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 缓动函数测试：起点/终点/单调性。
 */
class EasingTest {

    @Test
    void endpoints() {
        for (Easing.Type type : Easing.Type.values()) {
            assertEquals(0.0, Easing.apply(type, 0), 1e-6, type + " 起点");
            assertEquals(1.0, Easing.apply(type, 1), 1e-6, type + " 终点");
        }
    }

    @Test
    void monotonicQuad() {
        assertTrue(Easing.apply(Easing.Type.QUAD_OUT, 0.5) > Easing.apply(Easing.Type.QUAD_OUT, 0.25));
        assertTrue(Easing.apply(Easing.Type.CUBIC_IN_OUT, 0.75) > Easing.apply(Easing.Type.CUBIC_IN_OUT, 0.5));
    }

    @Test
    void bounceStaysInRange() {
        for (double p = 0; p <= 1; p += 0.01) {
            double v = Easing.apply(Easing.Type.BOUNCE_OUT, p);
            assertTrue(v >= 0 && v <= 1.01, "bounce 越界 p=" + p + " v=" + v);
        }
    }

    @Test
    void linearIsIdentity() {
        assertEquals(0.3, Easing.apply(Easing.Type.LINEAR, 0.3), 1e-9);
    }
}
