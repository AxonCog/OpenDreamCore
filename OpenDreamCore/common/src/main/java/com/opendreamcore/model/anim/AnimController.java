package com.opendreamcore.model.anim;

import com.opendreamcore.model.ir.AnimationIR;
import com.opendreamcore.model.molang.MolangEval;

import java.util.HashMap;
import java.util.Map;

/**
 * 动画控制器（三段分离之三：自动绑定状态机）。
 *
 *  - 双态自动绑定：speed ≤ 0 → idle，speed > 阈值 → walk（阈值缺省 0.01 格/帧）；
 *  - 状态切换走 crossfade（缺省 0.25s）：过渡期旧姿态冻结在切换时刻、新姿态继续走时钟，按进度插值；
 *  - 时钟按 deltaTime 累加（与相机 deltaTime 同口径，timeScale 由调用方先乘）。
 */
public final class AnimController {

    /** 动画库（按名取；缺动画 → 空姿态，不炸）。 */
    public interface Library {
        AnimationIR get(String name);
    }

    private final Library library;
    private final String idleName;
    private final String walkName;
    private final double fadeSeconds;
    private final double speedThreshold;

    private String state = "idle";
    private double clock;
    /** 过渡：from 状态名 + 冻结时钟 + 已过渡秒数。 */
    private String fromState;
    private double fromClock;
    private double blendT = -1;

    public AnimController(Library library, String idleName, String walkName,
                          double fadeSeconds, double speedThreshold) {
        this.library = library;
        this.idleName = idleName;
        this.walkName = walkName;
        this.fadeSeconds = Math.max(0, fadeSeconds);
        this.speedThreshold = speedThreshold;
    }

    /** 推帧：dt 秒，speed 当前速度（格/帧 或 格/秒，与阈值同单位即可）。 */
    public void update(double dt, double speed) {
        clock += dt;
        String target = speed > speedThreshold ? walkName : idleName;
        if (!target.equals(state) && blendT < 0) {
            fromState = state;
            fromClock = clock;
            state = target;
            // 过渡从切换帧后开始（本帧进度 0，下一帧才推进）
            blendT = fadeSeconds <= 0 ? -1 : 0;
        } else if (blendT >= 0) {
            blendT += dt;
            if (blendT >= fadeSeconds) {
                blendT = -1;
            }
        }
    }

    /** 采样当前（可能处于过渡混合）姿态：结果按骨骼名写入 out。
     *  scratchA/scratchB 是调用方复用的临时表（过渡期分别装旧/新姿态；非过渡只用 out）。 */
    public void sample(MolangEval.Variables vars,
                       Map<String, AnimEval.Pose> scratchA,
                       Map<String, AnimEval.Pose> scratchB,
                       Map<String, AnimEval.Pose> out) {
        AnimationIR cur = library.get(state);
        if (blendT >= 0 && fromState != null) {
            double progress = fadeSeconds <= 0 ? 1 : Math.min(1, blendT / fadeSeconds);
            AnimationIR prev = library.get(fromState);
            if (prev != null) {
                AnimEval.sample(prev, fromClock, vars, scratchA);
            } else {
                scratchA.clear();
            }
            if (cur != null) {
                AnimEval.sample(cur, clock, vars, scratchB);
            } else {
                scratchB.clear();
            }
            java.util.TreeSet<String> names = new java.util.TreeSet<String>(scratchA.keySet());
            names.addAll(scratchB.keySet());
            out.clear();
            for (String name : names) {
                AnimEval.Pose a = scratchA.get(name);
                AnimEval.Pose b = scratchB.get(name);
                AnimEval.Pose o = new AnimEval.Pose();
                AnimBlender.lerpInto(a == null ? AnimBlender.fresh() : a,
                        b == null ? AnimBlender.fresh() : b, progress, o);
                out.put(name, o);
            }
            return;
        }
        if (cur != null) {
            AnimEval.sample(cur, clock, vars, out);
        } else {
            out.clear();
        }
    }

    /** 调试用：当前状态名。 */
    public String state() {
        return state;
    }

    /** 调试用：过渡进度（不在过渡 = -1）。 */
    public double blendProgress() {
        if (blendT < 0) {
            return -1;
        }
        return fadeSeconds <= 0 ? 1 : Math.min(1, blendT / fadeSeconds);
    }

    /** 便捷：把一组动画按名转 Library。 */
    public static Library libraryOf(final Map<String, AnimationIR> anims) {
        return new Library() {
            @Override
            public AnimationIR get(String name) {
                return anims.get(name);
            }
        };
    }

    /** 便捷：空库（测试/无动画模型）。 */
    public static Library emptyLibrary() {
        return libraryOf(new HashMap<String, AnimationIR>());
    }
}
