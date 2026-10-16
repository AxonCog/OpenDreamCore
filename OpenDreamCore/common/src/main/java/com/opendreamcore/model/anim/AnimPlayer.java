package com.opendreamcore.model.anim;

import com.opendreamcore.model.ir.AnimationIR;
import com.opendreamcore.model.molang.MolangEval;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 实体动画播放器：时钟推进 → 表达式/插值采样 → 弹簧平滑 → 骨骼姿态输出。
 *
 *  - 每实体一个实例（渲染线程独占）；
 *  - 无动画（anim=null）输出全零位姿态（rest pose），平滑器落定后零积分成本；
 *  - 切换动画时平滑器天然提供过渡（骨骼从旧姿态追到新姿态），无需显式 crossfade；
 *  - 纯 Java 8，无 MC 依赖。
 */
public final class AnimPlayer {

    /** 全局默认过渡时长（秒）：可被 play/playOnce 的显式参数覆盖。 */
    public static double DEFAULT_TRANSITION = 0.2;

    /**
     * 三级过渡配置链：状态级 > 规则级 > 全局默认。
     *
     * @param state   当前状态（attack/hurt/...；null=未知，落到规则级）
     * @param ruleT   规则级 transition（null=未配）
     * @param stateT  规则级 transition_<状态> 表（可 null）
     */
    public static double resolveTransition(String state, Double ruleT, Map<String, Double> stateT) {
        if (state != null && stateT != null) {
            Double s = stateT.get(state.toLowerCase(java.util.Locale.ROOT));
            if (s != null) {
                return s;
            }
        }
        if (ruleT != null) {
            return ruleT;
        }
        return DEFAULT_TRANSITION;
    }

    private final AnimationClock clock = new AnimationClock();
    private final BoneSmoother smoother;
    private final Random random = new Random();
    private AnimationIR current;
    /** 一次性动作底座：允许 null（动作结束后回零姿态）。 */
    private AnimationIR base;
    private boolean onceActive;
    private final Map<String, AnimEval.Pose> raw = new HashMap<String, AnimEval.Pose>();

    /** 过渡快照：切换瞬间的实际姿态，向新目标线性收拢。 */
    private final Map<String, AnimEval.Pose> fadeFrom = new HashMap<String, AnimEval.Pose>();
    private double fadeT;
    private double fadeDur;
    /** 播放速率倍率（1 = 原速；攻击/慢动作等场景用）。 */
    private double speed = 1;

    public AnimPlayer(double smooth) {
        this.smoother = new BoneSmoother(smooth);
        this.baseSmooth = smooth;
    }

    /** 切换/设置当前动画（null=回 rest pose；打断一次性动作并清底座）。 */
    public void play(AnimationIR anim) {
        play(anim, DEFAULT_TRANSITION);
    }

    /** 显式过渡版本：transition<=0 直接切（无淡入）。 */
    public void play(AnimationIR anim, double transition) {
        if (anim != current) {
            beginFade(anim != null && anim.overridePrevious ? 0 : transition);
            clock.reset();
        }
        if (anim != null) {
            speed = anim.speed;
        }
        current = anim;
        base = null;
        onceActive = false;
    }

    /**
     * 播一次性动作（attack/hurt 等）：播完自动回到 base（通常是循环行走/待机）。
     * 返回值=false 表示动作无效（null），当前播放不变。
     */
    public boolean playOnce(AnimationIR oneShot, AnimationIR fallback) {
        return playOnce(oneShot, fallback, DEFAULT_TRANSITION);
    }

    public boolean playOnce(AnimationIR oneShot, AnimationIR fallback, double transition) {
        return playOnce(oneShot, fallback, transition, false);
    }

    /**
     * raw 直出变体：短动作跳过二阶弹簧（smooth=0 直通）。
     * 弹簧是给循环动画的自然感调的，套在 0.5s 短动作上会把曲线拖成
     * 慢动作（离线复现实锤：head 目标 -68°，0.75s 才爬到 -46°），
     * 播完回落再慢慢飘回 0 = 观感「歪头一下又慢慢回正」。
     * 播完回落时恢复原平滑。 */
    public boolean playOnce(AnimationIR oneShot, AnimationIR fallback, double transition, boolean raw) {
        if (oneShot == null) {
            return false;
        }
        rawOnce = raw;
        smoother.setSmooth(raw ? 0 : baseSmooth);
        beginFade(oneShot.overridePrevious ? 0 : transition);
        base = fallback;
        onceActive = true;
        current = oneShot;
        clock.reset();
        speed = oneShot.speed;
        return true;
    }

    /** playOnce(raw=true) 期间为真；回落时恢复平滑用。 */
    private boolean rawOnce;
    private final double baseSmooth;

    /** 攻击随机池：池里随机挑一个播；空池/全 null 视为无效不切。 */
    public boolean playOnceFrom(List<AnimationIR> pool, AnimationIR fallback, double transition) {
        if (pool == null || pool.isEmpty()) {
            return false;
        }
        AnimationIR pick = pool.get(random.nextInt(pool.size()));
        return playOnce(pick, fallback, transition);
    }

    /** 播放速率倍率。 */
    public void setSpeed(double s) {
        speed = s <= 0 ? 1 : s;
    }

    public double speed() {
        return speed;
    }

    /** 过渡是否还在进行（诊断/测试用）。 */
    public boolean fading() {
        return fadeT < fadeDur;
    }

    /** 记录切换瞬间的实际姿态（从 smoother 上帧输出抄），开启淡入窗口。 */
    private void beginFade(double transition) {
        if (transition <= 0) {
            fadeDur = 0;
            fadeT = 0;
            return;
        }
        // 首次切换无历史姿态：不淡入，直接播
        if (!smoother.hasOutput()) {
            fadeDur = 0;
            fadeT = 0;
            return;
        }
        smoother.snapshot(fadeFrom);
        fadeDur = transition;
        fadeT = 0;
    }

    /** 一次性动作是否还在播（即使回落底座为 null 也为真）。 */
    public boolean oneShotActive() {
        return onceActive;
    }

    public AnimationIR current() {
        return current;
    }

    public BoneSmoother smoother() {
        return smoother;
    }

    public AnimationClock clock() {
        return clock;
    }

    private double lastDt;

    /**
     * 推进一帧并输出骨骼姿态（骨骼名 → Pose；out 复用调用方对象）。
     *
     * @param dt    真实帧间隔（秒，未限幅；内部统一限幅）
     * @param vars  molang 变量源（可 null）
     * @param out   输出姿态表
     */
    public void step(double dt, MolangEval.Variables vars, Map<String, AnimEval.Pose> out) {
        clock.noteGap(dt);
        lastDt = dt;
        if (current == null) {
            out.clear();
            return;
        }
        double t = clock.tick(dt * speed);
        // 一次性动作播完：自动回落底座；没有底座则回零姿态并停止。
        if (onceActive && current != null && current.length > 0 && t >= current.length) {
            beginFade(DEFAULT_TRANSITION);
            current = base;
            onceActive = false;
            speed = current != null ? current.speed : 1;
            base = null;
            clock.reset();
            t = 0; // 底座首帧必须使用重置后的时钟，而不是旧动作末尾时间。
            if (rawOnce) {
                rawOnce = false;
                smoother.setSmooth(baseSmooth);
            }
        }
        if (current == null) {
            raw.clear();
            // 无底座即释放姿态；不能把最后一帧留在平滑器中供下一动作继承。
            smoother.setSmooth(0);
            smoother.step(raw, dt, out);
            smoother.setSmooth(baseSmooth);
            fadeDur = fadeT = 0;
            return;
        }
        AnimEval.sample(current, t, vars, raw);
        smoother.step(raw, dt, out);
        applyFade(out);
    }

    /** 过渡窗口内：out = 快照 → 新目标 线性收拢。 */
    private void applyFade(Map<String, AnimEval.Pose> out) {
        if (fadeT >= fadeDur) {
            return;
        }
        fadeT = Math.min(fadeDur, fadeT + lastDt);
        if (fadeT >= fadeDur) {
            return;
        }
        double k = ease(fadeT / fadeDur);
        for (Map.Entry<String, AnimEval.Pose> e : out.entrySet()) {
            AnimEval.Pose from = fadeFrom.get(e.getKey());
            if (from != null) {
                lerpPose(from, e.getValue(), k);
            }
        }
    }

    /** 平滑曲线：先快后慢（sin 缓出），比纯线性更接近观感上的自然。 */
    private static double ease(double k) {
        return Math.sin(k * Math.PI / 2);
    }

    private static void lerpPose(AnimEval.Pose from, AnimEval.Pose to, double k) {
        for (int i = 0; i < 3; i++) {
            to.rot[i] = from.rot[i] + (to.rot[i] - from.rot[i]) * k;
            to.pos[i] = from.pos[i] + (to.pos[i] - from.pos[i]) * k;
            to.scale[i] = from.scale[i] + (to.scale[i] - from.scale[i]) * k;
        }
    }
}
