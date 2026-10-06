package com.opendreamcore.model.ir;

import java.util.ArrayList;
import java.util.List;

/**
 * 模型中间表示（IR）：所有格式（bbmodel / geo.json / obj / 未来 smd 等）的统一落点。
 *
 * 语义约定（与 Bedrock/Blockbench 对齐，动画层直接消费）：
 *  - 坐标为模型空间（bedrock 约定），骨骼旋转为角度制（绕 X/Y/Z 的欧拉角，应用顺序 X→Y→Z）；
 *  - 骨骼 pivot 为「子骨骼与该骨骼网格的旋转中心」；网格顶点相对 pivot 旋转后按 pivot 平移；
 *  - obj 无骨骼 → 单根骨骼兜底（name="root"，pivot=[0,0,0]），o/g 分组映射为伪骨骼仅支持整体变换；
 *  - 渲染层只认本 IR，不认识任何具体格式（格式细节全部挡在解码器里）。
 *
 * 纯 Java 8，禁止 MC import（机检 R5/R6）。
 */
public final class ModelIR {

    /** 骨骼树根（任何模型至少一根：无骨骼格式由 codec 建单根兜底）。 */
    public final BoneIR root;
    /** 材质表（texture 为资源云相对路径或内嵌解码产物名；index 引用由 codec 填）。 */
    public final List<Material> materials = new ArrayList<Material>();
    public final Meta meta = new Meta();

    public ModelIR(BoneIR root) {
        this.root = root;
    }

    /** 骨骼节点：pivot + 子树 + 挂在其上的网格。 */
    public static final class BoneIR {
        public final String name;
        /** 旋转/缩放中心（模型空间，3 分量）。 */
        public final double[] pivot = new double[3];
        public final List<BoneIR> children = new ArrayList<BoneIR>();
        public final List<Mesh> meshes = new ArrayList<Mesh>();
        /** 静态绑定姿态（模型默认姿态），角度制欧拉；null=无旋转。 */
        public double[] bindRotation;

        public BoneIR(String name) {
            this.name = name;
        }
    }

    /** 网格抽象：BoxMesh（cube 族）与 TriMesh（obj/gltf 族）两个实现。 */
    public interface Mesh {
    }

    /** 立方体网格（bbmodel/geo 的 cube）。 */
    public static final class BoxMesh implements Mesh {
        /** from/to（模型空间，3 分量）。 */
        public final double[] from = new double[3];
        public final double[] to = new double[3];
        /** true=box UV（uv=[u1,v1,u2,v2]），false=逐面 UV（faces 北东西南上下的 [u1,v1,u2,v2]）。 */
        public boolean boxUv;
        /** boxUv 时 [u1,v1,u2,v2]；逐面时按 顺序 north,east,south,west,up,down 存 6 组。 */
        public final double[][] uv = new double[6][4];
        /** 材质索引（materials 下标）。 */
        public int material = 0;
        /** 可选整体膨胀（bbmodel inflate / geo cube grow）。 */
        public double inflate;
    }

    /** 任意拓扑三角网格（obj 等）。 */
    public static final class TriMesh implements Mesh {
        /** 展平 xyz。 */
        public float[] positions;
        /** 三角形索引。 */
        public int[] indices;
        /** 展平 xyz 法线（可空）。 */
        public float[] normals;
        /** 展平 uv。 */
        public float[] uvs;
        public int material = 0;
    }

    /** 材质：一张纹理 + 采样参数。 */
    public static final class Material {
        /** 资源云相对路径（如 texture/xxx.png）或内嵌解码产物名。 */
        public String texture;
        /** bbmodel 的纹理数组下标（-1=未绑定，渲染时用材质顺序）。 */
        public int sourceIndex = -1;
        public int frameCount = 1;
        public int fps = 12;
    }

    /** 解析元信息与告警（缺省即安全：告警不阻断加载）。 */
    public static final class Meta {
        public String sourceFormat = "unknown";
        /** 模型标称尺寸（bbmodel resolution / geo 说明宽高），缺省 0。 */
        public double textureWidth;
        public double textureHeight;
        public final List<String> warnings = new ArrayList<String>();

        public void warn(String msg) {
            warnings.add(msg);
        }
    }

    // ---------------------------------------------------------------- 遍历辅助

    /** 骨骼总数。 */
    public int boneCount() {
        return count(root);
    }

    private static int count(BoneIR b) {
        int n = 1;
        for (BoneIR c : b.children) {
            n += count(c);
        }
        return n;
    }

    /** 先序展平（父在子前，烘焙/动画求值按此顺序）。 */
    public void flatten(List<BoneIR> out) {
        flattenInto(root, out);
    }

    private static void flattenInto(BoneIR b, List<BoneIR> out) {
        out.add(b);
        for (BoneIR c : b.children) {
            flattenInto(c, out);
        }
    }

    /** 按名找骨骼（找不到返回 null）。 */
    public BoneIR bone(String name) {
        if (root.name.equals(name)) {
            return root;
        }
        return find(root, name);
    }

    private static BoneIR find(BoneIR b, String name) {
        for (BoneIR c : b.children) {
            if (c.name.equals(name)) {
                return c;
            }
            BoneIR r = find(c, name);
            if (r != null) {
                return r;
            }
        }
        return null;
    }
}
