package com.opendreamcore.client.camera;

import com.opendreamcore.camera.CameraEffects;
import com.opendreamcore.camera.CameraRule;
import net.minecraft.client.Minecraft;

/**
 * M1.5 效果胶水（震屏 / FOV）：把「算效果」留在共享层纯数学（{@link CameraEffects}，可测），
 * 把「摸 MC」留在这里。单独成类是反臃肿 R1 的要求——别把这些塞回 CameraPlayback。
 */
final class CameraFx {

    private CameraFx() {
    }

    /**
     * 把震屏噪声直接加在姿态数组上（pos 单位米、rot 单位度）。
     * strength≤0 或 shakeTicks≤0 时是空操作——这就是震屏的独立开关。
     */
    static void applyShake(CameraRule rule, double elapsed, double[] pos, double[] rot) {
        // M1.6 脚本震屏：与规则震屏相互独立，必须赶在规则早退前生效
        CameraLive.applyBurst(pos, rot);
        if (rule.shakeStrength <= 0.0 || rule.shakeTicks <= 0) {
            return;
        }
        double[] sh = CameraEffects.shake(rule.shakeStrength, rule.shakeTicks,
                elapsed * 20.0, rule.id.hashCode() * 0.173);
        for (int i = 0; i < 3; i++) {
            pos[i] += sh[i];
        }
        rot[0] += sh[3];
        rot[1] += sh[4];
    }

    /**
     * 计算并发布本 tick 的 FOV 因子。无 fov 演出（无关键帧 fov 且 stretch=1）时发布 1，
     * 混入侧直通。FOV 的独立开关 = 关键帧 fov 与 stretch 两个字段：都不给就没有 fov 演出。
     */
    static void publishFov(Minecraft mc, CameraActive a) {
        double baseFov = mc.options.fov().get();
        double inFrac = a.rule.transitionIn > 0
                ? a.elapsed * 20.0 / a.rule.transitionIn
                : (a.elapsed > 0.0 ? 1.0 : 0.0);
        double outFrac = (a.outro && a.outSeconds > 0.0)
                ? 1.0 - a.outroElapsed / a.outSeconds
                : 1.0;
        float f = (float) CameraEffects.fovFactor(a.curFov, a.rule.stretch, baseFov, inFrac, outFrac);
        // M1.6 脚本 FOV 覆盖：优先于规则 FOV 演出（脚本要的是绝对掌控）
        f = CameraLive.applyFov(f, baseFov);
        CameraFovHook.publish(f);
    }

    /** 恢复/中断/结束必须归一（restoreNow/dropState 调用），见 {@link CameraFovHook#reset}。 */
    static void reset() {
        CameraFovHook.reset();
        CameraLive.reset();
    }
}
