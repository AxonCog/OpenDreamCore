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

    public final String name;
    public final double length;
    public final boolean loop;
    /** 骨骼名 → 轨道组。 */
    public final Map<String, BoneAnim> bones = new LinkedHashMap<String, BoneAnim>();

    public AnimationIR(String name, double length, boolean loop) {
        this.name = name;
        this.length = length;
        this.loop = loop;
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
        /** bezier 控制点（前/后，各 xyz，可空）。 */
        public double[] bezierIn;
        public double[] bezierOut;
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
