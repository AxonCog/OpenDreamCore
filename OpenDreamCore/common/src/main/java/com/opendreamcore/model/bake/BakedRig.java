package com.opendreamcore.model.bake;

import java.util.ArrayList;
import java.util.List;

/**
 * 骨骼化烘焙产物：顶点按骨骼分桶、存 pivot 相对坐标（动画渲染栈用）。
 *
 * 与 BakedModel（拍平快路）并存：无动画模型走快路零变换成本；有动画/需平滑的
 * 模型走本产物，由代际胶水逐帧算骨骼世界变换后写顶点。
 * UV 归一策略与 BakedModel 一致（bake 后按贴图像素尺寸缩放）。
 */
public final class BakedRig {

    /** 前序扁平骨骼表（父索引指向表内下标，根为 -1）。 */
    public final Bone[] bones;

    public BakedRig(Bone[] bones) {
        this.bones = bones;
    }

    public static final class Bone {
        public final String name;
        public final int parent;
        /** 旋转/缩放中心（模型空间）。 */
        public final double[] pivot;
        /** 静态绑定姿态（角度制欧拉，可 null）。 */
        public final double[] bindRot;
        /** 挂在该骨骼上的部件（按贴图分组）。 */
        public final List<Part> parts = new ArrayList<Part>();

        public Bone(String name, int parent, double[] pivot, double[] bindRot) {
            this.name = name;
            this.parent = parent;
            this.pivot = pivot;
            this.bindRot = bindRot;
        }
    }

    /** 单部件：一张贴图标签 + 桶内顶点（8 float/顶点，pivot 相对坐标）。 */
    public static final class Part {
        public final String texture;
        public final float[] quads;
        public final float[] tris;

        public Part(String texture, float[] quads, float[] tris) {
            this.texture = texture;
            this.quads = quads;
            this.tris = tris;
        }
    }
}
