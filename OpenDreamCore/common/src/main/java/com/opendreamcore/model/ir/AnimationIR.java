package com.opendreamcore.model.ir;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 动画中间表示：轨道 + 关键帧 + 插值语义（与 Bedrock/Blockbench 对齐）。
 *
 *  - 旋转轨道值为角度制欧拉（应用顺序 X→Y→Z，与模型语义一致）；
 *  - time 单位秒；length 为动画时长（秒）；loop 语义 = 是否循环；
 *  - molang 表达式（bedrock 动画支持表达式关键帧）以字符串保存，
 *    求值时经 MolangEval（SafeMath 子集），失败回退 0（molang_strict=false）。
 */
public final class AnimationIR {

    /** 循环语义四值（bedrock/geckolib 对齐）：once 播一次夹尾；loop 回绕；hold 播完停在末帧；default 同 once。 */
    public static final String LOOP_ONCE = "once";
    public static final String LOOP_LOOP = "loop";
    public static final String LOOP_HOLD = "hold";
    public static final String LOOP_DEFAULT = "default";

    public final String name;
    public final double length;
    /** 循环语义（LOOP_* 之一；loop=true 的旧构造映射为 LOOP_LOOP）。 */
    public final String loopMode;
    /** 时间反向播放（void = length-void，萌芽等价）。 */
    public boolean reverse;

    /** 播放速率倍率（倍速包用；1 = 原速，AnimPlayer 按 clock.tick(dt*speed) 生效）。 */
    public double speed = 1.0;

    /** 打断语义：true = 播放本动画时立即打断当前动作（无过渡，不接管底座）。 */
    public boolean overridePrevious;
    /** 骨骼名 → 轨道组。 */
    public final Map<String, BoneAnim> bones = new LinkedHashMap<String, BoneAnim>();

    public AnimationIR(String name, double length, boolean loop) {
        this(name, length, loop ? LOOP_LOOP : LOOP_ONCE);
    }

    public AnimationIR(String name, double length, String loopMode) {
        this.name = name;
        this.length = length;
        this.loopMode = normalizeLoop(loopMode);
    }

    private static String normalizeLoop(String m) {
        if (m == null) {
            return LOOP_ONCE;
        }
        switch (m.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "loop":
                return LOOP_LOOP;
            case "hold":
            case "hold_on_last_frame":
                return LOOP_HOLD;
            case "default":
                return LOOP_DEFAULT;
            default:
                return LOOP_ONCE;
        }
    }

    /** 单骨骼的三通道轨道（缺通道 = 不动）。 */
    public static final class BoneAnim {
        public Track pos;
        public Track rot;
        public Track scale;
    }

    /** 单通道：关键帧按 time 升序。 */
    public static final class Track {
        public final List<Keyframe> keys = new ArrayList<Keyframe>();

        public void sort() {
            java.util.Collections.sort(keys);
        }
    }

    /** 关键帧：value 长度 3（pos/scale）或 3（rot 度）；表达式非空时优先表达式。 */
    public static final class Keyframe implements Comparable<Keyframe> {
        public final double time;
        /** xyz。 */
        public final double[] value;
        /** linear / step / bezier（数据点在 dataPoints）。 */
        public String interp = "linear";
        /** 段缓动（bedrock easing 名称，作用于本帧到达插值的 u；空=线性）。 */
        public String easing;
        /** easing 幅度参数（back/elastic 用；可空）。 */
        public double[] easingArgs;
        /** bezier 控制点（前/后，各 xyz，可空）。 */
        public double[] bezierIn;
        public double[] bezierOut;
        /** Bedrock 进入关键帧前的 pre 值；该时刻及之后使用 value（post）。 */
        public double[] preValue;
        public String[] preExpr;
        public String preEasing;
        public double[] preEasingArgs;
        /** Bedrock lerp_mode；空时保留通用 interp 的到达插值约定。 */
        public String lerpMode;
        /** molang 表达式（每通道一个，可空串表示无）。 */
        public final String[] expr = new String[3];

        public Keyframe(double time, double[] value) {
            this.time = time;
            this.value = value;
        }

        @Override
        public int compareTo(Keyframe o) {
            return Double.compare(time, o.time);
        }
    }

    /** 按骨骼名取轨道组（无则 null，调用方决定是否新建）。 */
    public BoneAnim bone(String name) {
        return bones.get(name);
    }

    public BoneAnim ensureBone(String name) {
        BoneAnim b = bones.get(name);
        if (b == null) {
            b = new BoneAnim();
            bones.put(name, b);
        }
        return b;
    }

    /** 是否有实际轨道内容。 */
    public boolean isEmpty() {
        for (BoneAnim b : bones.values()) {
            if (b.pos != null || b.rot != null || b.scale != null) {
                return false;
            }
        }
        return true;
    }
}
