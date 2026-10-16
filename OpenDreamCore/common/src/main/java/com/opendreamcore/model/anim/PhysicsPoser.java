package com.opendreamcore.model.anim;

/**
 * 物理姿态层：无动画文件也能让模型"活"——半速头跟随 + 走路摆臂。
 *
 *  - 优先级契约：一次性动作（attack/hurt）播放期间物理层让位；
 *    循环动画照播，物理层 additive 叠加；
 *  - 半速系数：头转向目标的速度是实体视角变化的一半，避免动画抖动突兀；
 *  - 纯 Java 8，无 MC 依赖（实体状态由调用方折算成角度/相位喂进来）。
 */
public final class PhysicsPoser {

    private final AnimEval.Pose scratch = new AnimEval.Pose();

    /** 头跟随平滑状态（跨帧累计，半速追赶目标）。 */
    private double headYaw;
    private double headPitch;

    /**
     * 半速头跟随：输出 head 骨骼姿态（写入 out["head"]，additive）。
     *
     * @param yawDelta   目标偏航（度，相对模型朝向）
     * @param pitchDelta 目标俯仰（度）
     * @param dt         帧间隔
     */
    public void followHead(java.util.Map<String, AnimEval.Pose> out,
                           double yawDelta, double pitchDelta, double dt) {
        double k = 1.0 - Math.exp(-Math.abs(dt) * 3.5); // 半速：帧响应率固定 3.5
        headYaw += (yawDelta - headYaw) * k;
        headPitch += (pitchDelta - headPitch) * k;
        if (Math.abs(headYaw) < 0.05 && Math.abs(headPitch) < 0.05) {
            return;
        }
        AnimEval.Pose p = pose(out);
        p.rot[1] += headYaw;
        p.rot[0] += headPitch;
    }

    /**
     * 走路摆臂：前后臂反相摆动（写入 armL/armR 骨骼，additive）。
     *
     * @param phase 步态相位（弧度，随移动距离累加）
     * @param amp   幅度（度；静止传 0）
     */
    public void armSwing(java.util.Map<String, AnimEval.Pose> out, double phase, double amp) {
        if (amp == 0) {
            return;
        }
        double s = Math.sin(phase) * amp;
        AnimEval.Pose l = pose(out, "armL");
        l.rot[0] += s;
        AnimEval.Pose r = pose(out, "armR");
        r.rot[0] -= s;
    }

    private AnimEval.Pose pose(java.util.Map<String, AnimEval.Pose> out) {
        return pose(out, "head");
    }

    private AnimEval.Pose pose(java.util.Map<String, AnimEval.Pose> out, String bone) {
        AnimEval.Pose p = out.get(bone);
        if (p == null) {
            scratch.reset();
            p = new AnimEval.Pose(); // hot-ok：每骨骼生命周期一次，进 map 复用
            out.put(bone, p);
        }
        return p;
    }
}
