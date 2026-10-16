package com.opendreamcore.model.bake;

/**
 * 烘焙产物：与 MC 版本无关的顶点数据。
 *
 * <p>布局：quad=4 顶点×8 float（x,y,z,u,v,nx,ny,nz），tri=3 顶点×8 float；
 * UV 已归一到 0..1。代际渲染器（gensrc/model-*）只负责把这两个数组写进各自的 VertexConsumer。
 */
public final class BakedModel {
    /** 贴图标签（内嵌纹理为 ModelAssetStore 分配的模型 namespace；代际层换算成各自的 RL/动态贴图）。 */
    public final String texture;
    /** 四边形顶点数据（4×8 float/quad）。 */
    public final float[] quads;
    /** 三角形顶点数据（3×8 float/tri，来自 TriMesh）。 */
    public final float[] tris;

    public BakedModel(String texture, float[] quads, float[] tris) {
        this.texture = texture;
        this.quads = quads;
        this.tris = tris;
    }

    public int quadCount() {
        return quads.length / 32;
    }

    public int triCount() {
        return tris.length / 24;
    }
}
