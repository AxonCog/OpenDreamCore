package com.opendreamcore.model.anim;

import com.opendreamcore.model.ir.AnimationIR;
import com.opendreamcore.model.molang.MolangEval;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 过渡系统：crossfade 快照混合、打断淡出、自动回落、speed 倍率、随机池。 */
class AnimPlayerTransitionTest {

    /** 常量姿态动画：唯一骨骼 bone 恒定在指定角度（绕 Y）。 */
    private static AnimationIR hold(double angle) {
        AnimationIR ir = new AnimationIR("hold" + angle, 10, AnimationIR.LOOP_LOOP);
        AnimationIR.BoneAnim ba = new AnimationIR.BoneAnim();
        ba.rot = new AnimationIR.Track();
        ba.rot.keys.add(new AnimationIR.Keyframe(0, new double[]{0, angle, 0}));
        ir.bones.put("bone", ba);
        return ir;
    }

    private static double boneY(Map<String, AnimEval.Pose> out) {
        AnimEval.Pose p = out.get("bone");
        return p == null ? Double.NaN : p.rot[1];
    }

    @Test
    void 切换时过渡从旧姿态滑向新目标() {
        AnimPlayer player = new AnimPlayer(0);
        Map<String, AnimEval.Pose> out = new HashMap<String, AnimEval.Pose>();
        player.play(hold(0));
        player.step(0.1, null, out);
        assertEquals(0, boneY(out), 1e-6);

        // 显式 0.4s 过渡切到 90°：中途应介于两者之间
        player.play(hold(90), 0.4);
        player.step(0.1, null, out);
        double mid = boneY(out);
        assertTrue(mid > 1 && mid < 89, "过渡中点应介于两姿态之间，实际 " + mid);
        // 过渡进行中
        assertTrue(player.fading());

        // 过渡结束后到位
        player.step(0.4, null, out);
        assertFalse(player.fading());
        assertEquals(90, boneY(out), 1e-4);
    }

    @Test
    void 无历史首切不淡入() {
        AnimPlayer player = new AnimPlayer(0);
        Map<String, AnimEval.Pose> out = new HashMap<String, AnimEval.Pose>();
        player.play(hold(45), 0.2);
        player.step(0.1, null, out);
        assertFalse(player.fading());           // 没有快照可混合
        assertEquals(45, boneY(out), 1e-4);     // 直接到位
    }

    @Test
    void 一次性动作打断也淡出() {
        AnimPlayer player = new AnimPlayer(0);
        Map<String, AnimEval.Pose> out = new HashMap<String, AnimEval.Pose>();
        player.play(hold(0));
        player.step(0.1, null, out);

        player.playOnce(hold(60), hold(0), 0.2);
        assertTrue(player.oneShotActive());
        player.step(0.1, null, out);
        double mid = boneY(out);
        assertTrue(mid > 0.5 && mid < 59.5, "打断淡出应平滑，实际 " + mid);

        // 播完（length=10 不可能等）→ 手动回落验证：直接再 play 回底座
        player.play(hold(0), 0);
        assertFalse(player.oneShotActive());
        player.step(0.1, null, out);
        assertEquals(0, boneY(out), 1e-4);
    }

    @Test
    void 播完自动回落底座() {
        AnimPlayer player = new AnimPlayer(0);
        Map<String, AnimEval.Pose> out = new HashMap<String, AnimEval.Pose>();
        AnimationIR shot = hold(60);
        AnimationIR timed = new AnimationIR(shot.name + "-t", 0.5, AnimationIR.LOOP_ONCE);
        timed.bones.putAll(shot.bones);
        shot = timed;
        player.play(hold(0));
        player.step(0.1, null, out);

        player.playOnce(shot, hold(0), 0);
        player.step(0.3, null, out);
        assertTrue(player.oneShotActive());
        assertEquals(60, boneY(out), 1e-4);

        player.step(0.3, null, out);   // 越过 0.5s → 自动回落
        assertFalse(player.oneShotActive());
        assertEquals(0, boneY(out), 1e-4);
    }

    @Test
    void 一次性动作无fallback结束后回零并停止() {
        AnimPlayer player = new AnimPlayer(0);
        Map<String, AnimEval.Pose> out = new HashMap<String, AnimEval.Pose>();
        AnimationIR shot = hold(90);
        AnimationIR timed = new AnimationIR("timed-null", 0.5, AnimationIR.LOOP_ONCE);
        timed.bones.putAll(shot.bones);
        assertTrue(player.playOnce(timed, null, 0));
        player.step(0.25, null, out);
        assertTrue(player.oneShotActive());
        assertEquals(90, boneY(out), 1e-9);
        player.step(0.25, null, out);
        assertFalse(player.oneShotActive());
        assertTrue(out.isEmpty() || Math.abs(boneY(out)) < 1e-9);
        player.step(0.25, null, out);
        assertFalse(player.oneShotActive());
        assertTrue(out.isEmpty() || Math.abs(boneY(out)) < 1e-9);
    }

    @Test
    void speed倍率缩放时钟() {
        AnimPlayer player = new AnimPlayer(0);
        Map<String, AnimEval.Pose> out = new HashMap<String, AnimEval.Pose>();
        player.play(hold(0));
        player.setSpeed(2);
        assertEquals(2, player.speed());
        // hold 常量姿态无法看时间差，直接验 clock 推进（tick 有 0.25s 限幅，用小步长）
        player.step(0.1, null, out);
        assertEquals(0.2, player.clock().time(), 1e-6);
        player.setSpeed(0);
        assertEquals(1, player.speed());   // 非法值回 1
    }

    @Test
    void 三级过渡配置链状态级压过规则级压过全局() {
        double backup = AnimPlayer.DEFAULT_TRANSITION;
        try {
            AnimPlayer.DEFAULT_TRANSITION = 0.2;
            java.util.Map<String, Double> stateT = new java.util.HashMap<String, Double>();
            stateT.put("attack", 0.05);

            // 状态级命中
            assertEquals(0.05, AnimPlayer.resolveTransition("attack", 0.3, stateT), 1e-9);
            // 状态级未配 → 规则级
            assertEquals(0.3, AnimPlayer.resolveTransition("hurt", 0.3, stateT), 1e-9);
            // 都未配 → 全局
            assertEquals(0.2, AnimPlayer.resolveTransition("hurt", null, stateT), 1e-9);
            // 状态键大小写归一
            assertEquals(0.05, AnimPlayer.resolveTransition("Attack", 0.3, stateT), 1e-9);
            // 状态表为 null 不炸
            assertEquals(0.3, AnimPlayer.resolveTransition("attack", 0.3, null), 1e-9);
            // state 为 null 落规则级
            assertEquals(0.3, AnimPlayer.resolveTransition(null, 0.3, stateT), 1e-9);
        } finally {
            AnimPlayer.DEFAULT_TRANSITION = backup;
        }
    }

    @Test
    void 中途打断一次性动作也走淡出() {
        AnimPlayer player = new AnimPlayer(0);
        Map<String, AnimEval.Pose> out = new HashMap<String, AnimEval.Pose>();
        player.play(hold(0));
        player.step(0.1, null, out);

        player.playOnce(hold(60), hold(0), 0);
        player.step(0.1, null, out);
        assertEquals(60, boneY(out), 1e-4);

        // 未播完就被另一个一次性动作打断：显式过渡生效，不跳变
        player.playOnce(hold(30), hold(0), 0.2);
        player.step(0.1, null, out);
        double mid = boneY(out);
        assertTrue(mid > 30.5 && mid < 59.5, "打断淡出应介于新旧之间，实际 " + mid);
    }

    @Test
    void 随机池命中且空池拒绝() {
        AnimPlayer player = new AnimPlayer(0);
        Map<String, AnimEval.Pose> out = new HashMap<String, AnimEval.Pose>();
        player.play(hold(0));
        player.step(0.1, null, out);

        assertFalse(player.playOnceFrom(Arrays.asList(
                (AnimationIR) null), hold(0), 0));
        assertTrue(player.playOnceFrom(Arrays.asList(
                hold(30), hold(30)), hold(0), 0));
        assertTrue(player.oneShotActive());
        player.play(hold(0), 0);
        assertFalse(player.playOnceFrom(null, hold(0), 0));
    }
}
