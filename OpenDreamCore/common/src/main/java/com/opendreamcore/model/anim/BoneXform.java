package com.opendreamcore.model.anim;

/**
 * 骨骼变换（渲染栈数学核心）：欧拉 XYZ（度）→ 旋转矩阵 + 平移，父子链合成。
 *
 * 约定与 ModelIR 一致：模型空间、角度制、应用顺序 X→Y→Z；
 * 骨骼局部变换 = T(pivot) ∘ R(bind+pose) ∘ S(scale) ∘ T(-pivot)（顶点已存 pivot 相对坐标）。
 * 纯 double，无 MC 依赖；double[9] 行主序 3x3，平移独立 double[3]。
 */
public final class BoneXform {

    public final double[] m = new double[9];   // 行主序 3x3
    public final double[] t = new double[3];   // 平移

    public BoneXform() {
        identity();
    }

    public void identity() {
        m[0] = m[4] = m[8] = 1;
        m[1] = m[2] = m[3] = m[5] = m[6] = m[7] = 0;
        t[0] = t[1] = t[2] = 0;
    }

    /** this = parent ∘ local（parent=this 自身也可，就地更新前先备份）。 */
    public void compose(BoneXform parent, BoneXform local) {
        // 备份 parent 值（允许 parent==this）
        double m0 = parent.m[0], m1 = parent.m[1], m2 = parent.m[2];
        double m3 = parent.m[3], m4 = parent.m[4], m5 = parent.m[5];
        double m6 = parent.m[6], m7 = parent.m[7], m8 = parent.m[8];
        double tx = parent.t[0], ty = parent.t[1], tz = parent.t[2];
        double[] l = local.m;
        // R = Mp * Ml
        m[0] = m0 * l[0] + m1 * l[3] + m2 * l[6];
        m[1] = m0 * l[1] + m1 * l[4] + m2 * l[7];
        m[2] = m0 * l[2] + m1 * l[5] + m2 * l[8];
        m[3] = m3 * l[0] + m4 * l[3] + m5 * l[6];
        m[4] = m3 * l[1] + m4 * l[4] + m5 * l[7];
        m[5] = m3 * l[2] + m4 * l[5] + m5 * l[8];
        m[6] = m6 * l[0] + m7 * l[3] + m8 * l[6];
        m[7] = m6 * l[1] + m7 * l[4] + m8 * l[7];
        m[8] = m6 * l[2] + m7 * l[5] + m8 * l[8];
        // t = Mp·tl + tp（world = parent∘local：先 local 后 parent）
        t[0] = m0 * local.t[0] + m1 * local.t[1] + m2 * local.t[2] + tx;
        t[1] = m3 * local.t[0] + m4 * local.t[1] + m5 * local.t[2] + ty;
        t[2] = m6 * local.t[0] + m7 * local.t[1] + m8 * local.t[2] + tz;
    }

    /** 变换一个点（就地写出 out[3]）。 */
    public void apply(double x, double y, double z, double[] out) {
        out[0] = m[0] * x + m[1] * y + m[2] * z + t[0];
        out[1] = m[3] * x + m[4] * y + m[5] * z + t[1];
        out[2] = m[6] * x + m[7] * y + m[8] * z + t[2];
    }

    /** 变换方向（不含平移，法线用；不 renormalize，调用方负责）。 */
    public void applyDir(double x, double y, double z, double[] out) {
        out[0] = m[0] * x + m[1] * y + m[2] * z;
        out[1] = m[3] * x + m[4] * y + m[5] * z;
        out[2] = m[6] * x + m[7] * y + m[8] * z;
    }

    /**
     * 由欧拉角（度，XYZ 顺序）与缩放构造旋转缩放矩阵（不含平移）。
     * Mc = Rz(c)·Ry(b)·Rx(a) 的行主序展开，缩放右乘（先缩放后旋转）。
     */
    public static BoneXform of(double[] rotDeg, double[] scale) {
        BoneXform x = new BoneXform();
        setRotScale(x, rotDeg, scale);
        return x;
    }

    /** 把旋转+缩放矩阵直写进 dst（热路径免分配版，配合 dst.identity() 用）。 */
    public static void setRotScale(BoneXform dst, double[] rotDeg, double[] scale) {
        double ax = Math.toRadians(rotDeg[0]);
        double ay = Math.toRadians(rotDeg[1]);
        double az = Math.toRadians(rotDeg[2]);
        double cx = Math.cos(ax), sx = Math.sin(ax);
        double cy = Math.cos(ay), sy = Math.sin(ay);
        double cz = Math.cos(az), sz = Math.sin(az);
        // Rx·Ry·Rz 展开（先 Z 后 Y 后 X 应用到列向量 → 组合矩阵 R = Rx*Ry*Rz）
        double r00 = cy * cz, r01 = -cy * sz, r02 = sy;
        double r10 = sx * sy * cz + cx * sz, r11 = -sx * sy * sz + cx * cz, r12 = -sx * cy;
        double r20 = -cx * sy * cz + sx * sz, r21 = cx * sy * sz + sx * cz, r22 = cx * cy;
        dst.m[0] = r00 * scale[0];
        dst.m[1] = r01 * scale[1];
        dst.m[2] = r02 * scale[2];
        dst.m[3] = r10 * scale[0];
        dst.m[4] = r11 * scale[1];
        dst.m[5] = r12 * scale[2];
        dst.m[6] = r20 * scale[0];
        dst.m[7] = r21 * scale[1];
        dst.m[8] = r22 * scale[2];
    }
}
