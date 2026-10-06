package com.opendreamcore.camera;

import com.opendreamcore.config.YamlParser;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 关键帧时间轴自检（M1.2）。
 *
 * 断言分三类，都是「宁可朴素也不能错」的那一面：
 * ① 两点一线确实线性、到点确实能算出总时长（自动停的依据）；
 * ② 配置写残（缺 pos/rot、只有一帧、speed 写 0/NaN）不能抛异常、不能给出乱轨迹；
 * ③ yaw 走最短角（350°→10° 是 +20°，不是绕 -340°）；
 * ④ M1.3 的样条：{@code smooth} 缺省为真 → 曲线过每一帧、帧间被拉圆，而两点路径在端点夹紧下
 *    恰好退化成直线（与 M1.2 行为完全一致，不会把老配置走形）。
 */
class CameraTimelineTest {

    @SuppressWarnings("unchecked")
    private static CameraRule rule(String yaml, String id) throws Exception {
        Map<String, Object> root = new YamlParser().parse(yaml);
        return CameraRule.parse(id, (Map<String, Object>) root.get(id));
    }

    private static CameraRule twoPointPath() throws Exception {
        return rule("""
                运镜_两点直线:
                  type: path
                  duration: 6.0
                  keyframes:
                    - t: 0.0
                      pos: [100, 72, 0]
                      rot: [0, 0]
                    - t: 6.0
                      pos: [120, 72, 20]
                      rot: [45, 0]
                """, "运镜_两点直线");
    }

    @Test
    void twoPointPathIsLinearAndAutoStops() throws Exception {
        CameraRule r = twoPointPath();
        assertTrue(CameraTimeline.autoStops(r), "两点路径未循环 → 应自动停");
        assertTrue(r.smooth, "smooth 缺省就是曲线档——两点路径必须仍走直线");
        assertEquals(6.0, CameraTimeline.totalSeconds(r), 1e-9);

        CameraTimeline.Pose at0 = CameraTimeline.sample(r, 0.0);
        assertNotNull(at0);
        assertEquals(100.0, at0.pos[0], 1e-9);
        assertEquals(0.0, at0.rot[0], 1e-9);

        CameraTimeline.Pose at3 = CameraTimeline.sample(r, 3.0);
        assertEquals(110.0, at3.pos[0], 1e-9, "线性中点 X（端点夹紧使样条在两点路径上退化成直线）");
        assertEquals(72.0, at3.pos[1], 1e-9);
        assertEquals(10.0, at3.pos[2], 1e-9, "线性中点 Z");
        assertEquals(22.5, at3.rot[0], 1e-9, "线性中点 yaw");

        CameraTimeline.Pose at6 = CameraTimeline.sample(r, 6.0);
        assertEquals(120.0, at6.pos[0], 1e-9);
        assertEquals(20.0, at6.pos[2], 1e-9);

        CameraTimeline.Pose beyond = CameraTimeline.sample(r, 99.0);
        assertEquals(120.0, beyond.pos[0], 1e-9, "超出末帧时间保持末帧姿态，不能外推");
    }

    @Test
    void speedScalesTimeline() throws Exception {
        CameraRule r = rule("""
                快进:
                  type: path
                  speed: 2.0
                  keyframes:
                    - t: 0.0
                      pos: [0, 64, 0]
                    - t: 6.0
                      pos: [12, 64, 0]
                """, "快进");
        assertEquals(3.0, CameraTimeline.totalSeconds(r), 1e-9, "speed=2 → 总时长减半");
        // 实际 1.5s（= 时间轴 3s）处应是中点
        CameraTimeline.Pose mid = CameraTimeline.sample(r, 1.5);
        assertEquals(6.0, mid.pos[0], 1e-9);
    }

    @Test
    void durationLongerThanLastFrameHoldsLastPose() throws Exception {
        CameraRule r = rule("""
                尾帧保持:
                  type: path
                  duration: 10.0
                  keyframes:
                    - t: 0.0
                      pos: [0, 64, 0]
                    - t: 4.0
                      pos: [8, 64, 0]
                """, "尾帧保持");
        assertEquals(10.0, CameraTimeline.totalSeconds(r), 1e-9, "duration 优先于末帧 t");
        CameraTimeline.Pose at9 = CameraTimeline.sample(r, 9.0);
        assertEquals(8.0, at9.pos[0], 1e-9, "末帧之后保持末帧姿态到 duration");
    }

    @Test
    void singleFramePathFallsBackToStaticPose() throws Exception {
        CameraRule r = rule("""
                单帧:
                  type: path
                  position: [5, 70, 5]
                  rotation: [90, 0]
                  keyframes:
                    - t: 0.0
                      pos: [999, 999, 999]
                """, "单帧");
        assertEquals(0.0, CameraTimeline.totalSeconds(r), 1e-9);
        assertFalse(CameraTimeline.autoStops(r), "帧数不足不能自动停（会瞬间闪一下）");
        CameraTimeline.Pose p = CameraTimeline.sample(r, 0.0);
        assertEquals(5.0, p.pos[0], 1e-9, "退化成静态姿态：用规则自身 position，不用那唯一一帧");
        assertEquals(90.0, p.rot[0], 1e-9);
    }

    @Test
    void missingPosInFrameInheritsPreviousFrame() throws Exception {
        CameraRule r = rule("""
                缺帧兜底:
                  type: path
                  keyframes:
                    - t: 0.0
                      pos: [0, 64, 0]
                    - t: 2.0
                      rot: [30, 0]
                    - t: 4.0
                      pos: [10, 64, 0]
                """, "缺帧兜底");
        // t=1 位于「有 pos 的 0s 帧」与「缺 pos 的 2s 帧」之间：中间帧沿用前一帧的 pos（原地不动，而非跳到原点）
        CameraTimeline.Pose at1 = CameraTimeline.sample(r, 1.0);
        assertEquals(0.0, at1.pos[0], 1e-9);
        assertEquals(0.0, at1.pos[2], 1e-9);
        // 朝向照常插值：0s 帧没有 rot → 往后取第一个有 rot 的帧（30），所以 1s 处仍是 30
        assertEquals(30.0, at1.rot[0], 1e-9);
        // t=3 位于 2s（缺 pos，沿用 0）与 4s（10）之间 → 中点 5
        CameraTimeline.Pose at3 = CameraTimeline.sample(r, 3.0);
        assertEquals(5.0, at3.pos[0], 1e-9);
    }

    @Test
    void missingRotationFallsBackToRuleRotation() throws Exception {
        CameraRule r = rule("""
                无帧朝向:
                  type: path
                  rotation: [180, 10]
                  keyframes:
                    - t: 0.0
                      pos: [0, 64, 0]
                    - t: 2.0
                      pos: [4, 64, 0]
                """, "无帧朝向");
        CameraTimeline.Pose p = CameraTimeline.sample(r, 1.0);
        assertEquals(180.0, p.rot[0], 1e-9, "关键帧没有 rot → 用规则自身 rotation");
        assertEquals(10.0, p.rot[1], 1e-9);
    }

    @Test
    void yawTakesShortestArc() throws Exception {
        CameraRule r = rule("""
                绕角:
                  type: path
                  keyframes:
                    - t: 0.0
                      pos: [0, 64, 0]
                      rot: [350, 0]
                    - t: 2.0
                      pos: [0, 64, 0]
                      rot: [10, 0]
                """, "绕角");
        CameraTimeline.Pose mid = CameraTimeline.sample(r, 1.0);
        // 最短弧：350 → 10 是 +20，所以中点是 360（等价 0），绝不是 180（那是绕远路）
        double yaw = mid.rot[0];
        assertTrue(Math.abs(yaw - 360.0) < 1e-6 || Math.abs(yaw) < 1e-6,
                "yaw 应走最短角（得到 0/360 附近），实际 " + yaw);
    }

    @Test
    void loopingPathDoesNotAutoStop() throws Exception {
        CameraRule r = rule("""
                循环:
                  type: path
                  loop: true
                  keyframes:
                    - t: 0.0
                      pos: [0, 64, 0]
                    - t: 3.0
                      pos: [6, 64, 0]
                """, "循环");
        assertFalse(CameraTimeline.autoStops(r), "循环路径不自动停（要停靠 /odc camera stop）");
        assertEquals(3.0, CameraTimeline.totalSeconds(r), 1e-9);
    }

    @Test
    void shotNeverAutoStops() throws Exception {
        CameraRule r = rule("""
                机位:
                  type: shot
                  position: [1, 2, 3]
                  rotation: [0, 0]
                """, "机位");
        assertFalse(CameraTimeline.autoStops(r), "固定机位保持到手动停止");
        assertEquals(0.0, CameraTimeline.totalSeconds(r), 1e-9);
        CameraTimeline.Pose p = CameraTimeline.sample(r, 123.0);
        assertEquals(1.0, p.pos[0], 1e-9, "固定机位与时间无关");
    }

    @Test
    void badSpeedIsTreatedAsOne() throws Exception {
        CameraRule zero = rule("""
                零速:
                  type: path
                  speed: 0
                  keyframes:
                    - t: 0.0
                      pos: [0, 64, 0]
                    - t: 6.0
                      pos: [6, 64, 0]
                """, "零速");
        assertEquals(6.0, CameraTimeline.totalSeconds(zero), 1e-9, "speed=0 当 1，不能除零/瞬间跑完");
        CameraRule nan = rule("""
                非数速:
                  type: path
                  speed: nan
                  keyframes:
                    - t: 0.0
                      pos: [0, 64, 0]
                    - t: 6.0
                      pos: [6, 64, 0]
                """, "非数速");
        assertEquals(6.0, CameraTimeline.totalSeconds(nan), 1e-9, "speed=NaN 同样当 1");
    }

    @Test
    void emptyRuleReturnsStaticPoseWithNullComponents() throws Exception {
        CameraRule r = rule("""
                空规则:
                  type: shot
                """, "空规则");
        CameraTimeline.Pose p = CameraTimeline.sample(r, 0.0);
        assertNotNull(p);
        assertNull(p.pos, "没有 position 就是 null 分量（客户端保持当前相机），不是原点");
        assertNull(p.rot);
        assertFalse(p.hasPos());
        assertFalse(p.hasRot());
    }

    // ─── M1.3：Catmull-Rom 样条（smooth 默认 true）────────────────────────────

    /** 四点方角路径：不走样条的话拐角是硬折，走样条则被拉圆。 */
    private static CameraRule fourPointSquare() throws Exception {
        return rule("""
                环绕:
                  type: path
                  duration: 9.0
                  keyframes:
                    - t: 0.0
                      pos: [0, 64, 0]
                    - t: 3.0
                      pos: [10, 64, 0]
                    - t: 6.0
                      pos: [10, 64, 10]
                    - t: 9.0
                      pos: [0, 64, 10]
                """, "环绕");
    }

    @Test
    void smoothPathPassesThroughEveryKeyframe() throws Exception {
        CameraRule r = fourPointSquare();
        assertTrue(r.smooth, "smooth 缺省应为 true");
        double[][] expect = {{0, 64, 0}, {10, 64, 0}, {10, 64, 10}, {0, 64, 10}};
        double[] times = {0.0, 3.0, 6.0, 9.0};
        for (int i = 0; i < times.length; i++) {
            CameraTimeline.Pose p = CameraTimeline.sample(r, times[i]);
            for (int k = 0; k < 3; k++) {
                assertEquals(expect[i][k], p.pos[k], 1e-9,
                        "曲线必须过关键帧，否则关键帧就白配了：t=" + times[i] + " 分量" + k);
            }
        }
    }

    @Test
    void smoothPathCurvesBetweenKeyframes() throws Exception {
        // 第 2 段 [10,64,0] → [10,64,10] 的中点：直线是 x=10；样条被前后两帧拉出去
        CameraTimeline.Pose mid = CameraTimeline.sample(fourPointSquare(), 4.5);
        assertEquals(11.25, mid.pos[0], 1e-9, "样条中点应偏离直线（拐角被拉圆）");
        assertEquals(5.0, mid.pos[2], 1e-9, "对称方向的中点上样条与直线重合");
        assertEquals(64.0, mid.pos[1], 1e-9, "高度没变就不该动");
    }

    @Test
    void smoothFalseKeepsStraightLines() throws Exception {
        CameraRule r = rule("""
                折线:
                  type: path
                  smooth: false
                  duration: 9.0
                  keyframes:
                    - t: 0.0
                      pos: [0, 64, 0]
                    - t: 3.0
                      pos: [10, 64, 0]
                    - t: 6.0
                      pos: [10, 64, 10]
                    - t: 9.0
                      pos: [0, 64, 10]
                """, "折线");
        assertFalse(r.smooth, "写了 false 就该是假");
        CameraTimeline.Pose mid = CameraTimeline.sample(r, 4.5);
        assertEquals(10.0, mid.pos[0], 1e-9, "smooth:false 必须回到直线");
        assertEquals(5.0, mid.pos[2], 1e-9);
    }
}
