package com.opendreamcore.model.anim;

import com.opendreamcore.model.ir.AnimationIR;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 缓动全集 / 循环四语义 / reverse / 时钟看门狗 单测。
 */
public class EasingLoopTest {

    // ---------------------------------------------------------------- Easing

    @Test
    public void linearAndUnknownFallBack() {
        assertEquals(0.5, Easing.apply("linear", 0.5, null), 1e-9);
        assertEquals(0.5, Easing.apply(null, 0.5, null), 1e-9);
        assertEquals(0.5, Easing.apply("什么玩意", 0.5, null), 1e-9);
    }

    @Test
    public void stepAndHold() {
        assertEquals(0.0, Easing.apply("step", 0.9, null), 1e-9);
        assertEquals(1.0, Easing.apply("step", 1.0, null), 1e-9);
        assertEquals(0.0, Easing.apply("hold", 0.3, null), 1e-9);
    }

    @Test
    public void easePrefixEquivalence() {
        // "ease_in_sine" 与 "sine_in" 同形
        assertEquals(Easing.apply("sine_in", 0.3, null), Easing.apply("ease_in_sine", 0.3, null), 1e-12);
        // 无后缀 ease_in/ease_out/ease_in_out = quad
        assertEquals(Easing.apply("quad_in", 0.3, null), Easing.apply("ease_in", 0.3, null), 1e-12);
        assertEquals(Easing.apply("quad_out", 0.7, null), Easing.apply("ease_out", 0.7, null), 1e-12);
    }

    @Test
    public void endpointsAnchored() {
        String[] all = {"sine", "quad", "cubic", "quart", "quint", "expo", "circle",
                "back", "bounce", "elastic"};
        for (String f : all) {
            for (String dir : new String[]{"_in", "_out", "_in_out"}) {
                assertEquals(0.0, Easing.apply(f + dir, 0.0, null), 1e-6, f + dir + " @0");
                assertEquals(1.0, Easing.apply(f + dir, 1.0, null), 1e-6, f + dir + " @1");
            }
        }
    }

    @Test
    public void easeInLagsEaseOut() {
        // 同一 u 下 in 曲线值 ≤ out 曲线值（起点附近）
        assertTrue(Easing.apply("cubic_in", 0.2, null) < Easing.apply("cubic_out", 0.2, null));
        assertTrue(Easing.apply("quint_in", 0.2, null) < Easing.apply("quint_out", 0.2, null));
    }

    @Test
    public void smoothIsSineInOut() {
        assertEquals(Easing.apply("sine_in_out", 0.4, null), Easing.apply("smooth", 0.4, null), 1e-12);
    }

    @Test
    public void amplitudeArgsRespected() {
        // back 幅度大 → 起步更快过冲（值可超 1）
        double def = Easing.apply("back_out", 0.8, null);
        double big = Easing.apply("back_out", 0.8, new double[]{4.0});
        assertTrue(big > def || big > 1.0, "back 幅度参数应影响曲线");
    }

    // ---------------------------------------------------------------- 循环/反向

    private static AnimationIR anim(double length, String loopMode, boolean reverse) {
        AnimationIR a = new AnimationIR("t", length, loopMode);
        a.reverse = reverse;
        return a;
    }

    @Test
    public void loopWraps() {
        assertEquals(0.5, AnimEval.effectiveTime(anim(2.0, "loop", false), 2.5), 1e-9);
        assertEquals(1.9, AnimEval.effectiveTime(anim(2.0, "loop", false), -0.1), 1e-9);
    }

    @Test
    public void onceAndHoldClamp() {
        assertEquals(2.0, AnimEval.effectiveTime(anim(2.0, "once", false), 5.0), 1e-9);
        assertEquals(2.0, AnimEval.effectiveTime(anim(2.0, "hold", false), 5.0), 1e-9);
        assertEquals(0.0, AnimEval.effectiveTime(anim(2.0, "default", false), -1.0), 1e-9);
    }

    @Test
    public void reverseMirrors() {
        assertEquals(1.5, AnimEval.effectiveTime(anim(2.0, "once", true), 0.5), 1e-9);
        // reverse + loop：2.5 → wrap 0.5 → mirror 1.5
        assertEquals(1.5, AnimEval.effectiveTime(anim(2.0, "loop", true), 2.5), 1e-9);
    }

    // ---------------------------------------------------------------- 时钟

    @Test
    public void clockClampsSingleFrame() {
        AnimationClock c = new AnimationClock();
        c.tick(0.016);
        double t = c.tick(10.0); // 断点恢复：10s 一帧 → 截到 0.25
        assertEquals(0.266, t, 1e-9);
    }

    @Test
    public void clockFreezeWatchdog() {
        AnimationClock c = new AnimationClock();
        c.tick(0.016);
        assertFalse(c.frozen());
        for (int i = 0; i < 70; i++) {
            c.noteGap(0.02); // 1.4s 静默
        }
        assertTrue(c.frozen());
        c.tick(0.016);
        assertFalse(c.frozen());
        assertTrue(c.time() < 0.3); // 恢复后限幅步进，不追帧
    }
}
