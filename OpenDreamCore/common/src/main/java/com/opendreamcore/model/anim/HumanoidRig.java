package com.opendreamcore.model.anim;

import com.opendreamcore.model.bake.BakedRig;

/**
 * 内置标准人形骨架：十一根骨、二段式四肢，层级和枢轴对齐原版玩家模型。
 *
 * <p>存在的意义：实体没绑自定义模型时，动画也得有骨架可落。名字按最常见的
 * 人形叫法（body/head、leftarm/leftforearm……），配合 {@link BoneAliases}
 * 各家写法都能对上。骨架本身不带任何方块面（parts 空）——它只负责提供
 * 层级和枢轴，让姿态有地方挂；前臂/小腿作为手臂/大腿的子骨，父骨动了
 * 子骨跟着走，和二段式模型动起来是一个感觉。
 */
public final class HumanoidRig {

    private HumanoidRig() {
    }

    /** 单位：模型像素（1/16 格），与 bbmodel 编解码一致。 */
    public static BakedRig build() {
        BakedRig.Bone body = new BakedRig.Bone("body", -1, pv(0, 24, 0), rot());
        BakedRig.Bone head = new BakedRig.Bone("head", 0, pv(0, 24, 0), rot());
        BakedRig.Bone leftArm = new BakedRig.Bone("leftarm", 0, pv(5, 22, 0), rot());
        BakedRig.Bone leftForeArm = new BakedRig.Bone("leftforearm", 2, pv(5, 12, 0), rot());
        BakedRig.Bone rightArm = new BakedRig.Bone("rightarm", 0, pv(-5, 22, 0), rot());
        BakedRig.Bone rightForeArm = new BakedRig.Bone("rightforearm", 4, pv(-5, 12, 0), rot());
        BakedRig.Bone leftLeg = new BakedRig.Bone("leftleg", 0, pv(1.9, 12, 0), rot());
        BakedRig.Bone leftForeLeg = new BakedRig.Bone("leftforeleg", 6, pv(1.9, 6, 0), rot());
        BakedRig.Bone rightLeg = new BakedRig.Bone("rightleg", 0, pv(-1.9, 12, 0), rot());
        BakedRig.Bone rightForeLeg = new BakedRig.Bone("rightforeleg", 8, pv(-1.9, 6, 0), rot());
        BakedRig.Bone rightItem = new BakedRig.Bone("rightitem", 5, pv(-5, 10, 0), rot());
        return new BakedRig(new BakedRig.Bone[]{
                body, head, leftArm, leftForeArm, rightArm, rightForeArm,
                leftLeg, leftForeLeg, rightLeg, rightForeLeg, rightItem});
    }

    private static double[] pv(double x, double y, double z) {
        return new double[]{x, y, z};
    }

    private static double[] rot() {
        return new double[]{0, 0, 0};
    }
}
