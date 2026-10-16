package com.opendreamcore.model.anim;

import com.opendreamcore.model.ir.AnimationIR;
import com.opendreamcore.model.molang.MolangEval;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 状态判定引擎：吃 {@link ControllerIR}，管「此刻该播什么」。
 *
 * <p>判定节奏学实战做法：姿态每帧推进，条件求值每 3 tick 一次——
 * 移动/挥手这类判定不值得每帧做，省下的算力留给渲染。切状态带过渡；
 * 短动作（once）在 95% 进度前不接受低优先级切换——挥手挥到一半被打断很难看；
 * once 播完自动回落 default；variants 每次进入随机抽一条。
 *
 * <p>触发（fire）是另一条面：服务端按名点火，队列里排着播，
 * 挂在指定层上；层是 ADDING 就叠加在 base 之上，OVERRIDE 就盖住。
 */
public final class ControllerEngine {

    /** 动画库（按名取；缺动画 → 该通道跳过，不炸）。 */
    public interface Library {
        AnimationIR get(String name);
    }

    /** 环境条件：引擎按这些旗子挑状态。 */
    public interface Conditions {
        boolean moving();

        boolean swinging();

        boolean sneak();

        boolean swimming();

        boolean dead();

        boolean riding();
    }

    /** 环境条件的平铺快照：抓一次实体状态填入，避免匿名内部类的每帧分配。 */
    public static final class EngineConditions implements Conditions {
        public final boolean moving;
        public final boolean swinging;
        public final boolean sneak;
        public final boolean swimming;
        public final boolean dead;
        public final boolean riding;

        public EngineConditions(boolean moving, boolean swinging, boolean sneak,
                                boolean swimming, boolean dead, boolean riding) {
            this.moving = moving;
            this.swinging = swinging;
            this.sneak = sneak;
            this.swimming = swimming;
            this.dead = dead;
            this.riding = riding;
        }

        @Override
        public boolean moving() {
            return moving;
        }

        @Override
        public boolean swinging() {
            return swinging;
        }

        @Override
        public boolean sneak() {
            return sneak;
        }

        @Override
        public boolean swimming() {
            return swimming;
        }

        @Override
        public boolean dead() {
            return dead;
        }

        @Override
        public boolean riding() {
            return riding;
        }
    }

    private static final class Once {
        String anim;
        double startClock;
        double priority;
    }

    private final Library library;
    private final ControllerIR ir;
    private final Random rng;
    private final double defaultTransition;

    private double clock;
    private int evalCounter;
    private String currentState = "";
    private String currentAnim = "";
    private double currentTransition;
    private double baseStartClock;
    private boolean directActive;
    private String directLayer = "";
    private String ambientAnim = "";
    private boolean ambientOnce;
    private boolean ambientCompleted;
    private boolean ambientOverride;
    private String completedState = "";
    private final Once once = new Once();
    /** 进触发面后的回落目标（通常=default）。 */
    private String fallbackAnim = "";

    /** 触发队列：每个触发一串动画，播完一条下一条，最后回落。 */
    private static final class TriggerRun {
        ControllerIR.Trigger trigger;
        List<AnimationIR> anims = new ArrayList<AnimationIR>();
        int index;
        double startClock;
        boolean active;
    }

    private final List<TriggerRun> runs = new ArrayList<TriggerRun>();

    /** 自动意图桥接；同一 once 条件保持期间只进入一次。 */
    public void updateAmbient(String animName, boolean onceMode, String fallbackName, boolean explicitIntent) {
        if (ir != null && !ir.states.isEmpty() && !explicitIntent) {
            if (ambientOverride) {
                ambientOverride = false;
                ambientAnim = "";
                if (!directActive) clearDirect();
            }
            return;
        }
        ambientOverride = true;
        fallbackAnim = fallbackName == null ? "" : fallbackName;
        if (directActive || triggerActive()) return;
        if (animName == null || animName.isEmpty()) {
            ambientAnim = "";
            ambientOnce = false;
            if (!currentAnim.isEmpty()) clearDirect();
            return;
        }
        boolean changed = !animName.equals(ambientAnim) || onceMode != ambientOnce;
        ambientAnim = animName;
        ambientOnce = onceMode;
        if (!changed) return;
        AnimationIR a = library.get(animName);
        if (a == null) return;
        currentAnim = animName;
        currentState = "";
        baseStartClock = clock;
        currentTransition = defaultTransition;
        if (onceMode) {
            once.anim = animName;
            once.startClock = clock;
            once.priority = Double.NEGATIVE_INFINITY;
        } else {
            once.anim = null;
        }
    }

    public ControllerEngine(Library library, ControllerIR ir) {
        this(library, ir, new Random(), AnimPlayer.DEFAULT_TRANSITION);
    }

    public ControllerEngine(Library library, ControllerIR ir, Random rng, double defaultTransition) {
        this.library = library;
        this.ir = ir;
        this.rng = rng;
        this.defaultTransition = defaultTransition;
        // ir 允许为 null（无控制器的直控模式）：/odc anim play 走 playDirect
        // 只需要 library+currentAnim，不该因为 Animation/ 里没有可用控制器就整个丢帧
        String def = ir == null ? "" : ir.defaultAnim;
        this.currentState = def;
        this.currentAnim = def;
        this.fallbackAnim = def;
    }

    /**
     * 推帧。姿态时钟、触发队列、短动作回落每帧都走；条件求值每 3 次调用做一次。
     */
    public void step(double dt, Conditions c) {
        clock += dt;
        advanceRuns();
        if (directActive) {
            AnimationIR direct = library.get(currentAnim);
            if (direct != null && AnimationIR.LOOP_ONCE.equals(direct.loopMode)
                    && direct.length > 0 && (clock - baseStartClock) * direct.speed >= direct.length) {
                clearDirect();
            }
        }
        advanceOnceFallback();
        // 求值节奏 3 tick 一次；短动作进行中照样求值——高优先级（如死亡）必须能打断锁
        if (++evalCounter % 3 == 1) {
            evaluate(c);
        }
    }

    private void evaluate(Conditions c) {
        if (ir == null || c == null || directActive || ambientOverride) {
            return;
        }
        String best = "";
        double bestPriority = Double.NEGATIVE_INFINITY;
        for (ControllerIR.State s : ir.states) {
            if (s.when.isEmpty() || !matches(s.when, c)) {
                continue;
            }
            if (s.priority > bestPriority) {
                bestPriority = s.priority;
                best = s.name;
            }
        }
        if (best.isEmpty()) {
            // 全都不匹配 → 家（default）；已经在家里就别折腾
            if (!currentState.isEmpty() && !currentState.equals(ir.defaultAnim)
                    && (!onceActive() || onceProgress() >= 0.95)) {
                enterState(ir.state(ir.defaultAnim));
                if (currentAnim.isEmpty() && !ir.defaultAnim.isEmpty()) {
                    currentAnim = ir.defaultAnim;
                }
            }
            return;
        }
        if (best.equals(completedState)) {
            return;
        }
        if (!best.equals(completedState)) {
            completedState = "";
        }
        if (best.equals(currentState)) {
            return;
        }
        // once 锁：上一次短动作没播到 95%，低优先级切换靠边
        if (onceActive() && once.priority > bestPriority && onceProgress() < 0.95) {
            return;
        }
        enterState(ir.state(best));
    }

    private void enterState(ControllerIR.State s) {
        if (s == null) {
            return;
        }
        currentState = s.name;
        String pick = pickAnim(s);
        AnimationIR a = library.get(pick);
        if (a == null) {
            currentAnim = "";
            return;
        }
        currentAnim = pick;
        baseStartClock = clock;
        currentTransition = s.transitionTime > 0 ? s.transitionTime : defaultTransition;
        if (s.once) {
            once.anim = pick;
            once.startClock = clock;
            once.priority = s.priority;
        } else {
            once.anim = null;   // 进了非短动作，旧的 once 记录作废（否则回头乱回落）
        }
    }

    /** variants 非空随机抽，否则用 anim。 */
    private String pickAnim(ControllerIR.State s) {
        if (!s.variants.isEmpty()) {
            return s.variants.get(rng.nextInt(s.variants.size()));
        }
        return s.anim;
    }

    private boolean matches(String when, Conditions c) {
        if ("moving".equals(when)) {
            return c.moving();
        }
        if ("swinging".equals(when)) {
            return c.swinging();
        }
        if ("sneak".equals(when)) {
            return c.sneak();
        }
        if ("swimming".equals(when)) {
            return c.swimming();
        }
        if ("dead".equals(when)) {
            return c.dead();
        }
        if ("riding".equals(when)) {
            return c.riding();
        }
        return false;
    }

    private boolean onceActive() {
        if (once.anim == null) {
            return false;
        }
        AnimationIR a = library.get(once.anim);
        return a != null && onceProgress(a) < 1.0;
    }

    private double onceProgress() {
        AnimationIR a = library.get(once.anim);
        return a == null ? 1.0 : onceProgress(a);
    }

    private double onceProgress(AnimationIR a) {
        if (a.length <= 0) return 1.0;
        return (clock - once.startClock) * a.speed / a.length;
    }

    /** 服务端触发：入队触发。同一触发已在播时忽略重复点火，避免队列增长和重播。 */
    public void fire(String triggerName) {
        if (ir == null) {
            return; // 无控制器：没有触发表可点
        }
        ControllerIR.Trigger t = ir.trigger(triggerName);
        if (t == null) {
            return;
        }
        for (TriggerRun existing : runs) {
            if (existing.active && existing.trigger == t) {
                return;
            }
        }
        TriggerRun run = new TriggerRun();
        run.trigger = t;
        run.active = true;
        run.startClock = clock;
        for (String name : t.anims) {
            AnimationIR a = library.get(name);
            if (a != null) {
                run.anims.add(a);
            }
        }
        if (!run.anims.isEmpty()) {
            runs.add(run);
        }
    }

    /** 触发队列推进：本条播完切下一条，整串播完收摊；一步跨多条时循环追平。 */
    private void advanceRuns() {
        Iterator<TriggerRun> it = runs.iterator();
        while (it.hasNext()) {
            TriggerRun r = it.next();
            if (!r.active || r.anims.isEmpty()) {
                it.remove();
                continue;
            }
            while (r.active) {
                AnimationIR a = r.anims.get(r.index);
                // speed=2 时同一段真实时间算双倍动画时间，wall-clock 段长为 length/speed。
                double localT = (clock - r.startClock) * r.trigger.speed;
                if (a.length <= 0 || localT >= a.length) {
                    r.index++;
                    r.startClock += a.length <= 0 ? 0 : a.length / r.trigger.speed;
                    if (r.index >= r.anims.size()) {
                        r.active = false;
                    }
                } else {
                    break;
                }
            }
            if (!r.active) {
                it.remove();
            }
        }
    }

    /** once 回落：完成后保持底座，不因条件持续而重播。 */
    private void advanceOnceFallback() {
        if (once.anim != null && once.anim.equals(currentAnim) && !onceActive()
                && !currentAnim.equals(fallbackAnim)) {
            String finished = once.anim;
            ambientCompleted = true;
            completedState = currentState;
            currentAnim = fallbackAnim;
            currentState = ir == null ? "" : ir.defaultAnim;
            baseStartClock = clock;
            once.anim = null;
        }
    }

    /** 当前 base 面该播的动画名（空=没有）。 */
    public String currentAnim() {
        return currentAnim;
    }

    /**
     * 当前应播的动画 IR（供玩家渲染胶水消费；空=引擎无话事权）。
     * playDirect 直控或触发面活着时返回对应段；都没有回 null，
     * 让调用方走自己的 fallback（全局意图/待机）。
     */
    public AnimationIR activeAnimation() {
        // 触发面优先（一次性动作正在播；队列顺序 index 起头）
        for (TriggerRun r : runs) {
            if (r.active && !r.anims.isEmpty()) {
                return r.anims.get(Math.min(r.index, r.anims.size() - 1));
            }
        }
        if (currentAnim != null && !currentAnim.isEmpty()) {
            return library.get(currentAnim);
        }
        return null;
    }

    public void stopDirect() {
        stopTriggers();
        directActive = false;
        directLayer = "";
        currentAnim = "";
        currentState = "";
        once.anim = null;
    }

    /** 只清 playDirect 直控段（不动触发面）：指令动画播完回待机用。
     * 不清的话 activeAnimation() 永远返回它，胶水层播完一重新 pick 就
     * 会重播成死循环（实机：右闪避每 0.5s 重播一遍）。 */
    public void clearDirect() {
        directActive = false;
        directLayer = "";
        currentAnim = fallbackAnim;
        currentState = ir == null ? "" : ir.defaultAnim;
        baseStartClock = clock;
        once.anim = null;
    }

    /** 当前 base 面切换过渡时长。 */
    public double currentTransition() {
        return currentTransition;
    }

    public String currentState() {
        return currentState;
    }

    /** 触发面是否还有活着的队列。 */
    public boolean triggerActive() {
        for (TriggerRun r : runs) {
            if (r.active && !r.anims.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** 插件直接播放：不走控制器状态，直接把这段动画放上 base 层。 */
    public void playDirect(String animName, String layer) {
        AnimationIR a = animName == null ? null : library.get(animName);
        if (a == null) {
            // 名字对不上必须留痕（直出，不进 debug 开关）：文件没下发到/名字打错
            com.opendreamcore.model.bake.ModelLog.info("[ODC-anim] playDirect 未命中「"
                    + animName + "」（文件未下发或名字不符）");
            return;
        }
        currentAnim = animName;
        currentState = "";
        currentTransition = defaultTransition;
        once.anim = null;
        stopTriggers();
        directActive = true;
        directLayer = layer == null ? "" : layer;
        baseStartClock = clock;
    }

    /** 插件喊停：清触发面；base 交回状态判定自己挑。 */
    public void stopTriggers() {
        for (TriggerRun r : runs) {
            r.active = false;
        }
    }

    /**
     * 采样：base 面写入 out；触发面按层的 blend 合入 out。
     * scratchA/scratchB 复用临时表，全程零分配新增（表是调用方的）。
     */
    public void sample(MolangEval.Variables vars,
                       Map<String, AnimEval.Pose> scratchA,
                       Map<String, AnimEval.Pose> scratchB,
                       Map<String, AnimEval.Pose> out) {
        out.clear();
        // base 面（含过渡混合：切换后 currentTransition 秒内新旧插值）
        AnimationIR cur = currentAnim.isEmpty() ? null : library.get(currentAnim);
        if (cur != null) {
            AnimEval.sample(cur, (clock - baseStartClock) * cur.speed, vars, out);
        }
        // 触发面：逐队列采样，叠/盖到 out（推进在 step 里做）
        for (TriggerRun r : runs) {
            if (!r.active || r.anims.isEmpty()) {
                continue;
            }
            AnimationIR a = r.anims.get(r.index);
            // 与推进共用同一时间轴：speed=2 时采样时间也为真实时间的两倍。
            double localT = (clock - r.startClock) * r.trigger.speed;
            ControllerIR.Layer layer = ir.layer(r.trigger.layer);
            double weight = layer == null ? 1.0 : layer.weight;
            boolean adding = layer != null && "ADDING".equals(layer.blend);
            if (a == null) {
                continue;
            }
            AnimEval.sample(a, localT, vars, scratchA);
            for (Map.Entry<String, AnimEval.Pose> e : scratchA.entrySet()) {
                AnimEval.Pose base = out.get(e.getKey());
                if (base == null) {
                    if (adding) {
                        continue; // 叠加层对没有 base 的骨不凭空造姿态
                    }
                    out.put(e.getKey(), e.getValue());
                    continue;
                }
                AnimEval.Pose merged = AnimBlender.fresh();
                if (adding) {
                    AnimBlender.addInto(base, e.getValue(), weight, merged);
                } else {
                    AnimBlender.lerpInto(base, e.getValue(), weight, merged);
                }
                out.put(e.getKey(), merged);
            }
        }
    }

    /** 调试/单测用：时钟。 */
    public double clock() {
        return clock;
    }
}
