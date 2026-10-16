package com.opendreamcore.model.anim;

import com.opendreamcore.model.ir.AnimationIR;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P3 动画运行时三段单测：表达式/插值采样、混合、自动绑定状态机。
 * 样式对齐 CameraRuleTest / VisualRulesTest（JDK8 直断言，无 mock）。
 */
public class AnimRuntimeTest {

    // ---------------------------------------------------------------- 造数据

    private static AnimationIR.Keyframe kf(double time, double x, double y, double z) {
        return new AnimationIR.Keyframe(time, new double[]{x, y, z});
    }

    private static AnimationIR.Track track(AnimationIR.Keyframe... keys) {
        AnimationIR.Track t = new AnimationIR.Track();
        for (AnimationIR.Keyframe k : keys) {
            t.keys.add(k);
        }
        t.sort();
        return t;
    }

    private static AnimationIR anim(String name, double length, boolean loop,
                                    AnimationIR.Track rot, AnimationIR.Track pos, AnimationIR.Track scale) {
        AnimationIR a = new AnimationIR(name, length, loop);
        AnimationIR.BoneAnim ba = a.ensureBone("body");
        ba.rot = rot;
        ba.pos = pos;
        ba.scale = scale;
        return a;
    }

    private static double rotAt(Map<String, AnimEval.Pose> poses, int ch) {
        AnimEval.Pose p = poses.get("body");
        return p == null ? 0 : p.rot[ch];
    }

    private static double scaleAt(Map<String, AnimEval.Pose> poses, int ch) {
        AnimEval.Pose p = poses.get("body");
        return p == null ? 1 : p.scale[ch];
    }

    // ---------------------------------------------------------------- 采样/插值

    @Test
    public void stepKeepsPreviousValue() {
        AnimationIR a = anim("step", 2, false,
                track(kf(0, 0, 0, 0), kf(1, 10, 0, 0)), null, null);
        a.bone("body").rot.keys.get(1).interp = "step";
        Map<String, AnimEval.Pose> out = new LinkedHashMap<String, AnimEval.Pose>();
        AnimEval.sample(a, 0.5, null, out);
        assertEquals(0.0, rotAt(out, 0), 1e-9, "step 在到达前保持前值");
        AnimEval.sample(a, 1.0, null, out);
        assertEquals(10.0, rotAt(out, 0), 1e-9);
    }

    @Test
    public void linearInterpolates() {
        AnimationIR a = anim("lin", 2, false,
                track(kf(0, 0, 0, 0), kf(1, 10, -20, 30)), null, null);
        Map<String, AnimEval.Pose> out = new LinkedHashMap<String, AnimEval.Pose>();
        AnimEval.sample(a, 0.25, null, out);
        assertEquals(2.5, rotAt(out, 0), 1e-9);
        assertEquals(-5.0, rotAt(out, 1), 1e-9);
        assertEquals(7.5, rotAt(out, 2), 1e-9);
    }

    @Test
    public void clampsBeforeFirstAndAfterLast() {
        AnimationIR a = anim("clamp", 5, false,
                track(kf(1, 7, 0, 0), kf(3, 9, 0, 0)), null, null);
        Map<String, AnimEval.Pose> out = new LinkedHashMap<String, AnimEval.Pose>();
        AnimEval.sample(a, 0, null, out);
        assertEquals(7.0, rotAt(out, 0), 1e-9, "首帧前取第一关键帧");
        AnimEval.sample(a, 4, null, out);
        assertEquals(9.0, rotAt(out, 0), 1e-9, "末帧后取最后关键帧");
    }

    @Test
    public void loopWrapsByLength() {
        AnimationIR a = anim("loop", 2, true,
                track(kf(0, 0, 0, 0), kf(1, 10, 0, 0)), null, null);
        Map<String, AnimEval.Pose> out = new LinkedHashMap<String, AnimEval.Pose>();
        AnimEval.sample(a, 2.5, null, out);
        assertEquals(5.0, rotAt(out, 0), 1e-9, "t=2.5 回绕到 0.5");
        assertTrue(AnimEval.effectiveTime(a, 4.75) - 0.75 < 1e-9);
    }

    @Test
    public void expressionKeyframeEvaluatesWithAnimTime() {
        AnimationIR a = anim("expr", 2, false,
                track(kf(0, 0, 0, 0), kf(1, 0, 0, 0)), null, null);
        AnimationIR.Keyframe last = a.bone("body").rot.keys.get(1);
        last.expr[0] = "query.anim_time * 90";
        last.value[0] = 999; // 表达式优先于数值
        Map<String, AnimEval.Pose> out = new LinkedHashMap<String, AnimEval.Pose>();
        AnimEval.sample(a, 0.5, null, out);
        assertEquals(45 * 0.5, rotAt(out, 0), 1e-9,
                "表达式在目标关键帧求值（=45），再与前一帧线性插值半程 = 22.5");
        AnimEval.sample(a, 1.0, null, out);
        assertEquals(90.0, rotAt(out, 0), 1e-9, "到达表达式关键帧：anim_time=1.0 → 90");
    }

    @Test
    public void expressionFailureFallsBackToZero() {
        AnimationIR a = anim("badexpr", 1, false, track(kf(0, 3, 0, 0)), null, null);
        a.bone("body").rot.keys.get(0).expr[0] = "math.nonexistent_fn(1)";
        Map<String, AnimEval.Pose> out = new LinkedHashMap<String, AnimEval.Pose>();
        AnimEval.sample(a, 0, null, out);
        assertEquals(0.0, rotAt(out, 0), 1e-9, "molang 宽容语义：坏表达式回 0 不上抛");
    }

    @Test
    public void bezierMidpointMatchesCubic() {
        AnimationIR a = anim("bez", 1, false,
                track(kf(0, 0, 0, 0), kf(1, 90, 0, 0)), null, null);
        AnimationIR.Keyframe b = a.bone("body").rot.keys.get(1);
        b.interp = "bezier";
        b.bezierIn = new double[]{90, 0, 0}; // P2 = 90 → u=0.5 = 0.5*90
        Map<String, AnimEval.Pose> out = new LinkedHashMap<String, AnimEval.Pose>();
        AnimEval.sample(a, 0.5, null, out);
        assertEquals(45.0, rotAt(out, 0), 1e-9,
                "B(0.5)=0.125*0+0.375*0+0.375*90+0.125*90=45");
    }

    @Test
    public void scaleChannelDefaultsToOneAndSamples() {
        AnimationIR a = anim("scale", 1, false, null, null,
                track(kf(0, 1, 1, 1), kf(1, 2, 1, 1)));
        Map<String, AnimEval.Pose> out = new LinkedHashMap<String, AnimEval.Pose>();
        AnimEval.sample(a, 0.5, null, out);
        assertEquals(1.5, scaleAt(out, 0), 1e-9);
        assertEquals(1.0, scaleAt(out, 1), 1e-9);
    }

    @Test
    public void catmullromIsCurvedAndFullRotationIsNotCollapsed() {
        AnimationIR a = anim("curve", 3, false,
                track(kf(0, 0, 0, 0), kf(1, 10, 0, 0), kf(2, 0, 0, 0), kf(3, 10, 0, 0)), null, null);
        for (AnimationIR.Keyframe k : a.bone("body").rot.keys) {
            k.interp = "catmullrom";
        }
        Map<String, AnimEval.Pose> out = new LinkedHashMap<String, AnimEval.Pose>();
        AnimEval.sample(a, 0.5, null, out);
        assertEquals(5.625, rotAt(out, 0), 1e-9, "Catmull-Rom 中点不应退化成线性 5");

        AnimationIR roll = anim("roll", 1, false,
                track(kf(0, 0, 0, 0), kf(1, 360, 0, 0)), null, null);
        AnimEval.sample(roll, 0.5, null, out);
        assertEquals(180, rotAt(out, 0), 1e-9, "欧拉角线性插值必须保留 360 度翻滚");
    }

    @Test
    public void emptyTrackClearsToZeroAndPrePostUsesBoundaryValues() {
        AnimationIR a = anim("boundary", 2, false,
                track(kf(0, 1, 0, 0), kf(2, 3, 0, 0)), null, null);
        AnimationIR.Keyframe b = a.bone("body").rot.keys.get(1);
        b.preValue = new double[]{9, 0, 0};
        Map<String, AnimEval.Pose> out = new LinkedHashMap<String, AnimEval.Pose>();
        AnimEval.sample(a, 1.999, null, out);
        assertEquals(8.996, rotAt(out, 0), 1e-9);
        AnimEval.sample(a, 2, null, out);
        assertEquals(3, rotAt(out, 0), 1e-9);

        AnimationIR.Track empty = new AnimationIR.Track();
        AnimationIR blank = anim("blank", 1, false, empty, null, null);
        AnimEval.sample(blank, 0.5, null, out);
        assertEquals(0, rotAt(out, 0), 1e-9, "空轨道不能残留上一动画姿态");
    }

    @Test
    public void nestedSamplingKeepsOuterTimeAndEndpointBuffers() {
        AnimationIR outer = anim("outer", 1, false,
                track(kf(0, 1, 0, 0), kf(1, 3, 0, 0)), null, null);
        outer.bone("body").rot.keys.get(1).expr[0] = "variable.nested + query.anim_time";
        outer.bone("body").rot.keys.get(1).expr[1] = "query.anim_time";
        AnimationIR inner = anim("inner", 1, false, track(kf(0, 100, 0, 0)), null, null);
        Map<String, AnimEval.Pose> out = new LinkedHashMap<String, AnimEval.Pose>();
        Map<String, AnimEval.Pose> nestedOut = new LinkedHashMap<String, AnimEval.Pose>();
        AnimEval.sample(outer, .5, name -> {
            AnimEval.sample(inner, .9, null, nestedOut);
            return 3;
        }, out);
        assertEquals(2.25, rotAt(out, 0), 1e-9);
        assertEquals(.25, rotAt(out, 1), 1e-9);
        assertEquals(100, rotAt(nestedOut, 0), 1e-9);
    }

    @Test
    public void parallelSamplingKeepsSeparateTimeVariablesAndBuffers() throws Exception {
        final AnimationIR a = anim("a", 1, false, track(kf(0, 1, 0, 0), kf(1, 3, 0, 0)), null, null);
        a.bone("body").rot.keys.get(1).expr[0] = "variable.barrier + query.anim_time";
        final java.util.concurrent.CyclicBarrier barrier = new java.util.concurrent.CyclicBarrier(2);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Future<Double> f1 = pool.submit(() -> parallelValue(a, .25, 10, barrier));
            java.util.concurrent.Future<Double> f2 = pool.submit(() -> parallelValue(a, .75, 20, barrier));
            assertEquals(3.3125, f1.get(), 1e-9);
            assertEquals(15.8125, f2.get(), 1e-9);
        } finally {
            pool.shutdownNow();
        }
    }

    private static double parallelValue(AnimationIR anim, double time, double value,
                                         java.util.concurrent.CyclicBarrier barrier) {
        Map<String, AnimEval.Pose> out = new LinkedHashMap<String, AnimEval.Pose>();
        AnimEval.sample(anim, time, name -> {
            try {
                barrier.await(5, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new AssertionError(e);
            }
            return value;
        }, out);
        return rotAt(out, 0);
    }

    // ---------------------------------------------------------------- 混合

    @Test
    public void lerpBlendAtHalfway() {
        AnimEval.Pose a = AnimBlender.fresh();
        AnimEval.Pose b = AnimBlender.fresh();
        b.rot[0] = 90;
        b.scale[0] = 2;
        AnimEval.Pose out = AnimBlender.fresh();
        AnimBlender.lerpInto(a, b, 0.5, out);
        assertEquals(45.0, out.rot[0], 1e-9);
        assertEquals(1.5, out.scale[0], 1e-9);
        AnimBlender.lerpInto(a, b, 0, out);
        assertEquals(0.0, out.rot[0], 1e-9, "u=0 = 全 a");
        AnimBlender.lerpInto(a, b, 2, out);
        assertEquals(90.0, out.rot[0], 1e-9, "u 越界夹取到 1 = 全 b");
    }

    @Test
    public void additiveBlendScalesMultiplicatively() {
        AnimEval.Pose base = AnimBlender.fresh();
        base.pos[1] = 10;
        base.scale[0] = 1;
        AnimEval.Pose add = AnimBlender.fresh();
        add.pos[1] = 4;
        add.scale[0] = 2;
        AnimEval.Pose out = AnimBlender.fresh();
        AnimBlender.addInto(base, add, 0.5, out);
        assertEquals(12.0, out.pos[1], 1e-9, "pos 加权累加 10+4*0.5");
        assertEquals(1.5, out.scale[0], 1e-9, "scale 乘法混合 1*(1+(2-1)*0.5)");
    }

    // ---------------------------------------------------------------- 自动绑定

    @Test
    public void controllerSwitchesIdleWalkWithFade() {
        Map<String, AnimationIR> lib = new HashMap<String, AnimationIR>();
        lib.put("idle", anim("idle", 1, true, track(kf(0, 0, 0, 0)), null, null));
        lib.put("walk", anim("walk", 1, true, track(kf(0, 40, 0, 0)), null, null)); // 单关键帧 = 常量 40
        AnimController c = new AnimController(AnimController.libraryOf(lib),
                "idle", "walk", 1.0, 0.01);
        Map<String, AnimEval.Pose> scratch = new HashMap<String, AnimEval.Pose>();
        AnimEval.Pose out = AnimBlender.fresh();

        c.update(0.5, 0.0);
        assertEquals("idle", c.state());
        assertEquals(-1, c.blendProgress(), 1e-9, "未切换无过渡");

        c.update(0.5, 0.5); // 切到 walk，blendT=0
        assertEquals("walk", c.state());
        assertEquals(0.0, c.blendProgress(), 1e-9, "过渡从 0 开始");

        c.update(0.5, 0.5); // 过渡半程
        assertEquals(0.5, c.blendProgress(), 1e-9);
        Map<String, AnimEval.Pose> sa = new HashMap<String, AnimEval.Pose>();
        Map<String, AnimEval.Pose> sb = new HashMap<String, AnimEval.Pose>();
        Map<String, AnimEval.Pose> res = new HashMap<String, AnimEval.Pose>();
        c.sample(null, sa, sb, res);
        assertEquals(20.0, rotAt(res, 0), 1e-9, "idle(0) 与 walk(常量40) 半程混合 = 20");

        c.update(0.5, 0.5); // 过渡结束
        assertEquals(-1, c.blendProgress(), 1e-9, "fade 完成后回到单姿态");
        c.sample(null, sa, sb, res);
        assertEquals(40.0, rotAt(res, 0), 1e-9, "稳定走 walk（loop 回绕到 t=0）");
    }

    @Test
    public void controllerReturnsToIdleWhenStill() {
        Map<String, AnimationIR> lib = new HashMap<String, AnimationIR>();
        lib.put("idle", anim("idle", 1, true, track(kf(0, 1, 0, 0)), null, null));
        lib.put("walk", anim("walk", 1, true, track(kf(0, 3, 0, 0)), null, null));
        AnimController c = new AnimController(AnimController.libraryOf(lib),
                "idle", "walk", 0, 0.01);
        c.update(0.1, 1.0);
        assertEquals("walk", c.state());
        c.update(0.1, 0.0);
        assertEquals("idle", c.state(), "速度归零回 idle（零过渡时长直接切）");
    }

    @Test
    public void missingAnimIsSafeDefault() {
        AnimController c = new AnimController(AnimController.emptyLibrary(),
                "idle", "walk", 0.25, 0.01);
        c.update(0.1, 0.5);
        Map<String, AnimEval.Pose> sa = new HashMap<String, AnimEval.Pose>();
        Map<String, AnimEval.Pose> sb = new HashMap<String, AnimEval.Pose>();
        Map<String, AnimEval.Pose> res = new HashMap<String, AnimEval.Pose>();
        c.sample(null, sa, sb, res);
        assertEquals(0, res.size(), "缺动画回空姿态表不炸");
    }
}
