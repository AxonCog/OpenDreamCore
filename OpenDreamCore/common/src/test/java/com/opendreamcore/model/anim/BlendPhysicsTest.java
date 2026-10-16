package com.opendreamcore.model.anim;

import com.opendreamcore.model.ir.AnimationIR;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 混合栈契约：additive 叠加、一次性动作播完回落、物理层优先级。 */
class BlendPhysicsTest {

    private static AnimEval.Pose rotPose(Map<String, AnimEval.Pose> m, String bone, double x) {
        AnimEval.Pose p = new AnimEval.Pose();
        p.rot[0] = x;
        m.put(bone, p);
        return p;
    }

    @Test
    void additive权重与中性跳过() {
        Map<String, AnimEval.Pose> base = new HashMap<String, AnimEval.Pose>();
        rotPose(base, "head", 10);
        Map<String, AnimEval.Pose> layer = new HashMap<String, AnimEval.Pose>();
        rotPose(layer, "head", 30);
        rotPose(layer, "tail", 0); // 中性姿态不新增骨骼

        AnimBlender.add(base, layer, 0.5);
        assertEquals(25.0, base.get("head").rot[0], 1e-9);
        assertFalse(base.containsKey("tail")); // 中性层不写入

        // weight=0 恒等
        AnimBlender.add(base, layer, 0);
        assertEquals(25.0, base.get("head").rot[0], 1e-9);

        // weight=1 全量：缩放相乘语义
        Map<String, AnimEval.Pose> base2 = new HashMap<String, AnimEval.Pose>();
        AnimEval.Pose bp = new AnimEval.Pose();
        bp.rot[1] = 5;
        bp.scale[0] = 2;
        base2.put("head", bp);
        AnimEval.Pose lp = new AnimEval.Pose();
        lp.rot[1] = 15;
        lp.scale[0] = 1.5;
        Map<String, AnimEval.Pose> layer2 = new HashMap<String, AnimEval.Pose>();
        layer2.put("head", lp);
        AnimBlender.add(base2, layer2, 1.0);
        assertEquals(20.0, bp.rot[1], 1e-9);
        assertEquals(3.0, bp.scale[0], 1e-9);
    }

    @Test
    void 一次性动作播完自动回落底座() {
        AnimationIR walk = new AnimationIR("walk", 1.0, AnimationIR.LOOP_LOOP);
        AnimationIR attack = new AnimationIR("attack", 0.3, AnimationIR.LOOP_ONCE);
        AnimPlayer p = new AnimPlayer(0.0); // 关平滑，断言干净
        p.play(walk);
        Map<String, AnimEval.Pose> out = new HashMap<String, AnimEval.Pose>();

        assertTrue(p.playOnce(attack, walk));
        assertTrue(p.oneShotActive());
        p.step(0.1, null, out);
        assertSame(attack, p.current());

        // 播过时长后自动回底座（crossfade 由平滑器承担，此处断言状态机）
        p.step(0.3, null, out);
        assertFalse(p.oneShotActive());
        assertSame(walk, p.current());
        assertEquals(0.0, p.clock().time(), 1e-9); // 回落后时钟重置

        // 无底座（play 直接切）不触发回落
        p.play(attack);
        p.step(1.0, null, out);
        assertSame(attack, p.current());
        // 空动作 playOnce 返回 false 且不打断当前
        assertFalse(p.playOnce(null, walk));
        assertSame(attack, p.current());
    }

    @Test
    void 物理层优先级与半速跟随() {
        PhysicsPoser ph = new PhysicsPoser();
        Map<String, AnimEval.Pose> out = new HashMap<String, AnimEval.Pose>();

        // 半速跟随：头姿态渐进逼近目标（每帧喂新底姿表，物理层 additive）
        double first = Double.NaN;
        for (int i = 0; i < 200; i++) {
            Map<String, AnimEval.Pose> frame = new HashMap<String, AnimEval.Pose>();
            ph.followHead(frame, 30, 10, 0.05);
            if (i == 0) {
                first = frame.get("head").rot[1];
                assertTrue(first > 0 && first < 30, "首帧应部分逼近: " + first);
            }
            out.putAll(frame);
        }
        assertEquals(30.0, out.get("head").rot[1], 0.5);
        assertEquals(10.0, out.get("head").rot[0], 0.5);

        // 摆臂：反相对称
        Map<String, AnimEval.Pose> arms = new HashMap<String, AnimEval.Pose>();
        ph.armSwing(arms, Math.PI / 2, 20);
        assertEquals(20.0, arms.get("armL").rot[0], 1e-9);
        assertEquals(-20.0, arms.get("armR").rot[0], 1e-9);
        // 静止幅度 0 不写骨骼
        Map<String, AnimEval.Pose> idle = new HashMap<String, AnimEval.Pose>();
        ph.armSwing(idle, 1.0, 0);
        assertFalse(idle.containsKey("armL"));

        // 优先级：一次性动作播放中物理层让位（调用方契约）；
        // 这里验证 AnimPlayer.oneShotActive() 作为让位开关可用
        AnimPlayer player = new AnimPlayer(0.0);
        AnimationIR walk = new AnimationIR("walk", 1.0, AnimationIR.LOOP_LOOP);
        AnimationIR hurt = new AnimationIR("hurt", 0.2, AnimationIR.LOOP_ONCE);
        player.play(walk);
        assertTrue(player.playOnce(hurt, walk));
        Map<String, AnimEval.Pose> physOut = new HashMap<String, AnimEval.Pose>();
        if (!player.oneShotActive()) { // 让位开关：动作中跳过物理层
            ph.followHead(physOut, 30, 0, 0.05);
        }
        assertFalse(physOut.containsKey("head"));
        player.step(0.3, null, physOut); // 播完
        if (!player.oneShotActive()) {   // 回落后物理层恢复
            ph.followHead(physOut, 30, 0, 0.05);
        }
        assertTrue(physOut.containsKey("head"));
    }
}
