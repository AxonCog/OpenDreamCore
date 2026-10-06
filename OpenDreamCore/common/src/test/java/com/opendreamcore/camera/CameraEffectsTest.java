package com.opendreamcore.camera;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M1.5 效果层自检：震屏衰减 + FOV 平滑包络。全是纯函数，断言只盯契约句：
 * 「shake 从 0.3 衰减到 0」「FOV 平滑变化」「不演出时必须完全直通」。
 */
class CameraEffectsTest {

    @Test
    void shakeOffIsExactlyZero() {
        double[] z = new double[]{0, 0, 0, 0, 0};
        // 独立开关：strength≤0 / shakeTicks≤0 / 已播完 → 全零（混入侧拿到也不能动姿态）
        assertEquals(0.0, CameraEffects.shake(0.0, 20, 1, 1)[0], 0.0);
        assertEquals(0.0, CameraEffects.shake(-0.5, 20, 1, 1)[1], 0.0);
        assertEquals(0.0, CameraEffects.shake(0.3, 0, 1, 1)[2], 0.0);
        assertEquals(0.0, CameraEffects.shake(0.3, 20, 20, 1)[0], 0.0);
        assertEquals(0.0, CameraEffects.shake(0.3, 20, 25, 1)[4], 0.0);
        assertEquals(0.0, CameraEffects.shake(0.3, 20, -1, 1)[3], 0.0);
        assertEquals(0.0, z[0], 0.0);
    }

    @Test
    void shakeDecaysToZero() {
        // 起点：位置分量被包络限在 strength 内、旋转分量限在 2×strength 内
        double[] s = CameraEffects.shake(0.3, 20, 0, 1);
        for (int i = 0; i < 3; i++) {
            assertTrue(Math.abs(s[i]) <= 0.3 + 1e-12, "pos 分量越界: " + s[i]);
        }
        for (int i = 3; i < 5; i++) {
            assertTrue(Math.abs(s[i]) <= 0.6 + 1e-12, "rot 分量越界: " + s[i]);
        }
        // 中点：幅度包络减半
        double[] mid = CameraEffects.shake(0.3, 20, 10, 1);
        for (int i = 0; i < 3; i++) {
            assertTrue(Math.abs(mid[i]) <= 0.15 + 1e-12, "中点 pos 包络应减半: " + mid[i]);
        }
        // 末端：只剩 0.025 tick 的余量，必须接近全零
        double[] end = CameraEffects.shake(0.3, 20, 19.5, 1);
        assertEquals(0.0, end[0], 0.008);
        assertEquals(0.0, end[3], 0.016);
        // 确定性：同参数同结果（可复现，回放/测试都依赖这个）
        double[] again = CameraEffects.shake(0.3, 20, 5, 1);
        double[] twice = CameraEffects.shake(0.3, 20, 5, 1);
        for (int i = 0; i < 5; i++) {
            assertEquals(again[i], twice[i], 0.0);
        }
    }

    @Test
    void fovNoShowPassesThrough() {
        // 没有关键帧 fov 且 stretch=1 → 无论过渡进度如何都恒为 1（完全直通）
        assertEquals(1.0, CameraEffects.fovFactor(null, 1.0, 70.0, 0.0, 1.0), 1e-12);
        assertEquals(1.0, CameraEffects.fovFactor(null, 1.0, 70.0, 1.0, 1.0), 1e-12);
        assertEquals(1.0, CameraEffects.fovFactor(null, 1.0, 70.0, 1.0, 0.3), 1e-12);
        // 非法 baseFov 也必须直通
        assertEquals(1.0, CameraEffects.fovFactor(null, 2.0, 0.0, 1.0, 1.0), 1e-12);
    }

    @Test
    void fovSmoothEnvelope() {
        // 进过渡开始 = 1（无跳变），进完 = 目标倍率，出过渡中点 = 目标与 1 的中点
        assertEquals(1.0, CameraEffects.fovFactor(null, 2.0, 70.0, 0.0, 1.0), 1e-12);
        assertEquals(2.0, CameraEffects.fovFactor(null, 2.0, 70.0, 1.0, 1.0), 1e-12);
        assertEquals(1.5, CameraEffects.fovFactor(null, 2.0, 70.0, 1.0, 0.5), 1e-12);
        // 关键帧 fov 是绝对度数，优先于 stretch
        assertEquals(100.0 / 70.0, CameraEffects.fovFactor(100.0, 2.0, 70.0, 1.0, 1.0), 1e-9);
        assertEquals(100.0 / 70.0, CameraEffects.fovFactor(100.0, 1.0, 70.0, 1.0, 1.0), 1e-9);
        // 包络对 kf 同样生效：进过渡从 1 平滑滑到 100/70
        assertEquals(1.0, CameraEffects.fovFactor(100.0, 1.0, 70.0, 0.0, 1.0), 1e-12);
    }

    @Test
    void smoothstepEndsFlat() {
        assertEquals(0.0, CameraEffects.smoothstep(0.0), 1e-12);
        assertEquals(1.0, CameraEffects.smoothstep(1.0), 1e-12);
        assertEquals(0.5, CameraEffects.smoothstep(0.5), 1e-12);
        // 对称性：s(1-x) = 1 - s(x)（中点两侧互补，端点导数为 0）
        assertEquals(1.0 - CameraEffects.smoothstep(0.3), CameraEffects.smoothstep(0.7), 1e-12);
    }
}
