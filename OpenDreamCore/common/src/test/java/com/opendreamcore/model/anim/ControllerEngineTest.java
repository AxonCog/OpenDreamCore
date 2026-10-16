package com.opendreamcore.model.anim;

import com.opendreamcore.model.ir.AnimationIR;
import com.opendreamcore.model.molang.MolangEval;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 引擎三件事：状态切换、短动作锁与回落、变体随机。 */
class ControllerEngineTest {

    private static final String YML = "default: idle\n"
            + "states:\n"
            + "  idle:\n"
            + "    anim: idle\n"
            + "  walk:\n"
            + "    anim: walk\n"
            + "    when: moving\n"
            + "  attack:\n"
            + "    variants: [a1, a2]\n"
            + "    when: swinging\n"
            + "    once: true\n"
            + "    priority: 10\n"
            + "  death:\n"
            + "    anim: death\n"
            + "    when: dead\n"
            + "    priority: 100\n"
            + "layers:\n"
            + "  特效:\n"
            + "    blend_type: ADDING\n"
            + "    weight: 0.5\n"
            + "triggers:\n"
            + "  cast:\n"
            + "    layer: 特效\n"
            + "    anim: \"起手 > 循环\"\n";

    private static AnimationIR anim(String name, double length) {
        return new AnimationIR(name, length, AnimationIR.LOOP_ONCE);
    }

    /** 带 body 骨位移通道的动画（触发采样断言用）。 */
    private static AnimationIR animWithBody(String name, double length) {
        AnimationIR a = anim(name, length);
        AnimationIR.Track t = new AnimationIR.Track();
        t.keys.add(new AnimationIR.Keyframe(0.0, new double[]{0.0, 0.0, 0.0}));
        t.keys.add(new AnimationIR.Keyframe(length, new double[]{0.0, 0.5, 0.0}));
        a.bones.put("body", new AnimationIR.BoneAnim());
        a.bones.get("body").pos = t;
        return a;
    }

    private static Map<String, AnimationIR> lib() {
        Map<String, AnimationIR> m = new LinkedHashMap<String, AnimationIR>();
        m.put("idle", animWithBody("idle", 2.0));
        m.put("walk", animWithBody("walk", 1.0));
        m.put("a1", anim("a1", 0.5));
        m.put("a2", anim("a2", 0.5));
        m.put("death", anim("death", 3.0));
        m.put("起手", animWithBody("起手", 0.3));
        m.put("循环", animWithBody("循环", 1.0));
        return m;
    }

    private static ControllerEngine.EngineConditions cond(final boolean moving, final boolean swinging,
                                                          final boolean dead) {
        return new ControllerEngine.EngineConditions(moving, swinging, false, false, dead, false);
    }

    /** 求值每 3 步一次：用小步跨过节拍点，让每个断言点前都恰好求值过。 */
    private static void steps(ControllerEngine e, int n, double dt, ControllerEngine.EngineConditions c) {
        for (int i = 0; i < n; i++) {
            e.step(dt, c);
        }
    }

    @Test
    void 移动切换与回落() throws Exception {
        ControllerEngine e = new ControllerEngine(lib(lib()), ControllerCodec.parse(YML));
        steps(e, 1, 0.1, cond(true, false, false));   // 第 1 步就求值
        assertEquals("walk", e.currentAnim());

        steps(e, 3, 0.1, cond(false, false, false));  // 第 4 步再次求值 → 回家
        assertEquals("idle", e.currentAnim());
    }

    @Test
    void 优先级定胜者() throws Exception {
        ControllerEngine e = new ControllerEngine(lib(lib()), ControllerCodec.parse(YML));
        steps(e, 1, 0.1, cond(true, true, false));
        assertTrue(e.currentAnim().startsWith("a")); // attack(10) > walk(0)，变体随机

        // 短动作进行中（<95%），walk 抢不动
        steps(e, 2, 0.1, cond(true, false, false));   // 第 2、3 步不求值，天然不动
        assertTrue(e.currentAnim().startsWith("a"));
        // 播完后回落 idle，moving=false 不会拉回 walk
        steps(e, 3, 0.2, cond(false, false, false));  // 第 4~6 步，第 4 步求值
        assertEquals("idle", e.currentAnim());
    }

    @Test
    void 短动作九成五前不换() throws Exception {
        ControllerEngine e = new ControllerEngine(lib(lib()), ControllerCodec.parse(YML));
        steps(e, 1, 0.1, cond(false, true, false));
        String playing = e.currentAnim();
        assertTrue(playing.equals("a1") || playing.equals("a2"));
        // 0.4s：进度 0.8 <0.95，死亡(100) 也能进——优先级高于锁
        steps(e, 3, 0.1, cond(false, false, true));
        assertEquals("death", e.currentAnim());
    }

    @Test
    void 锁挡得住同优先级切换() throws Exception {
        // walk=0 priority，attack once 进行中 walk/moving 不得打断
        ControllerEngine e = new ControllerEngine(lib(lib()), ControllerCodec.parse(YML));
        steps(e, 1, 0.1, cond(false, true, false));
        String playing = e.currentAnim();
        steps(e, 2, 0.1, cond(true, false, false));   // 第 2、3 步不求值，锁都没机会被挑战
        assertEquals(playing, e.currentAnim());
        steps(e, 4, 0.2, cond(true, false, false));   // 播完回落 + 第 7 步求值 → walk
        assertEquals("walk", e.currentAnim());
    }

    @Test
    void 变体随机有种子() throws Exception {
        ControllerIR ir = ControllerCodec.parse(YML);
        // 固定种子：a 序列可复现
        ControllerEngine e1 = new ControllerEngine(lib(lib()), ir, new Random(7), 0.2);
        e1.step(0.1, cond(false, true, false));
        String first1 = e1.currentAnim();
        ControllerEngine e2 = new ControllerEngine(lib(lib()), ir, new Random(7), 0.2);
        e2.step(0.1, cond(false, true, false));
        assertEquals(first1, e2.currentAnim());
        assertTrue(first1.equals("a1") || first1.equals("a2"));
    }

    @Test
    void 触发队列播完回落() throws Exception {
        ControllerEngine e = new ControllerEngine(lib(lib()), ControllerCodec.parse(YML));
        e.fire("cast");
        assertTrue(e.triggerActive());
        e.step(0.1, cond(false, false, false));
        assertEquals("idle", e.currentAnim()); // base 面不动

        Map<String, AnimEval.Pose> out = new HashMap<String, AnimEval.Pose>();
        Map<String, AnimEval.Pose> s1 = new HashMap<String, AnimEval.Pose>();
        Map<String, AnimEval.Pose> s2 = new HashMap<String, AnimEval.Pose>();
        e.sample(VARS, s1, s2, out);
        // ADDING 层只碰有 base 姿态的骨；触发动画打 body，base idle 也有 body
        assertTrue(out.containsKey("body"));

        e.step(1.5, cond(false, false, false)); // 起手0.3+循环1.0=1.3，一步跨完（循环追平）
        assertFalse(e.triggerActive());
    }

    @Test
    void triggerKeySelectsDeclaredAnimationName() {
        AnimationIR animation = anim("roll_animation", 1.0);
        Map<String, AnimationIR> animations = new LinkedHashMap<String, AnimationIR>();
        animations.put("roll_animation", animation);
        ControllerIR.Trigger trigger = new ControllerIR.Trigger("前翻滚", "base",
                Collections.singletonList("roll_animation"), 0.1);
        ControllerIR ir = new ControllerIR("", Collections.<ControllerIR.State>emptyList(),
                Collections.singletonList(new ControllerIR.Layer("base", "OVERRIDE", 1.0,
                        false, null, "", 0)), Collections.singletonList(trigger));
        ControllerEngine e = new ControllerEngine(lib(animations), ir);
        e.fire("前翻滚");
        assertEquals("roll_animation", e.activeAnimation().name);
    }

    @Test
    void triggerSpeedScalesSamplingAndCompletion() {
        AnimationIR roll = animWithBody("roll_animation", 1.0);
        Map<String, AnimationIR> animations = new LinkedHashMap<String, AnimationIR>();
        animations.put("roll_animation", roll);
        ControllerIR.Trigger trigger = new ControllerIR.Trigger("roll", "base",
                Collections.singletonList("roll_animation"), 0.1, 2.0, 800L);
        ControllerIR ir = new ControllerIR("", Collections.<ControllerIR.State>emptyList(),
                Collections.singletonList(new ControllerIR.Layer("base", "OVERRIDE", 1.0,
                        false, null, "", 0)), Collections.singletonList(trigger));
        ControllerEngine e = new ControllerEngine(lib(animations), ir);
        e.fire("roll");
        e.fire("roll"); // duplicate while active must not enqueue or restart
        Map<String, AnimEval.Pose> out = new HashMap<String, AnimEval.Pose>();
        Map<String, AnimEval.Pose> scratchA = new HashMap<String, AnimEval.Pose>();
        Map<String, AnimEval.Pose> scratchB = new HashMap<String, AnimEval.Pose>();
        e.step(0.25, cond(false, false, false));
        e.sample(VARS, scratchA, scratchB, out);
        assertEquals(0.25, out.get("body").pos[1], 1e-9);
        assertTrue(e.triggerActive());
        e.step(0.25, cond(false, false, false)); // local animation time reaches 1.0
        assertFalse(e.triggerActive());
        assertEquals("", e.activeAnimation() == null ? "" : e.activeAnimation().name);
    }

    @Test
    void completedTriggerCanBeFiredAgain() {
        AnimationIR roll = anim("roll_animation", 0.1);
        Map<String, AnimationIR> animations = new LinkedHashMap<String, AnimationIR>();
        animations.put("roll_animation", roll);
        ControllerIR.Trigger trigger = new ControllerIR.Trigger("roll", "base",
                Collections.singletonList("roll_animation"), 0.1, 1.0, 800L);
        ControllerIR ir = new ControllerIR("", Collections.<ControllerIR.State>emptyList(),
                Collections.singletonList(new ControllerIR.Layer("base", "OVERRIDE", 1.0,
                        false, null, "", 0)), Collections.singletonList(trigger));
        ControllerEngine e = new ControllerEngine(lib(animations), ir);
        e.fire("roll");
        e.step(0.2, cond(false, false, false));
        assertFalse(e.triggerActive());
        assertEquals(null, e.activeAnimation());
        e.fire("roll");
        assertTrue(e.triggerActive());
        assertEquals("roll_animation", e.activeAnimation().name);
    }

    private static final MolangEval.Variables VARS = new MolangEval.Variables() {
        @Override
        public double get(String name) {
            return 0;
        }
    };

    private static ControllerEngine.Library lib(final Map<String, AnimationIR> m) {
        return new ControllerEngine.Library() {
            @Override
            public AnimationIR get(String name) {
                return m.get(name);
            }
        };
    }
}
