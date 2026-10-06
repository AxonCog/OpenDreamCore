package com.opendreamcore.client.render;

/**
 * 批次的固定 UV：纯色几何（血条、描边、分隔线、相机路径线、画布笔刷）开了之后，
 * 写入入口自动给每个顶点补同一个 UV。
 *
 * <p>为什么需要：世界里的纯色几何必须走带 UV 的顶点格式（见 WorldSolidRect），
 * 而写顶点的 helper 有一堆（ribbon / billboard / canvasRect / 模型骨骼线 …），
 * 逐个补 setUv 总会漏，漏掉的顶点要么在 build 时炸、要么整批画不出来。
 * 开关放在写入入口统一处理，漏写与写错都不可能再发生。
 *
 * <p>白纹理任意 UV 都是白，所以固定成 (0,0) 就行，不用管批次在贴图上的位置。
 * 实际写属性走 {@link CompatVertexAttributes}（跨版本方法名的收口在那儿）。
 */
/** 批次固定 UV 的状态持有者；由 {@link com.opendreamcore.client.CompatBuffer} 驱动。 */
public final class FixedVertexUv {

    private float u;
    private float v;
    private boolean on;

    /** 开固定 UV；纯色几何配白纹理时用。 */
    public void fix(float u, float v) {
        this.u = u;
        this.v = v;
        this.on = true;
    }

    public boolean isOn() {
        return on;
    }

    public float u() {
        return u;
    }

    public float v() {
        return v;
    }
}