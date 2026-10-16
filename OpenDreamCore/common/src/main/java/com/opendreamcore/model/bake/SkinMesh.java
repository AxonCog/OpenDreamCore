package com.opendreamcore.model.bake;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 蒙皮网格：顶点按权重绑定到多个骨骼（SMD/BMD/YSM 这类带权重的格式用）。
 *
 * <p>与 {@link BakedRig.Part} 的刚性绑定互补：刚性 = 每顶点恰好属于一根骨骼，
 * 蒙皮 = 每顶点最多 4 根骨骼加权混合。bbmodel 这类刚性格式可经
 * {@link #fromRigid} 统一进来（权重恒 1.0），渲染层就能只走一条采样路径。
 *
 * <p>顶点一律存模型空间坐标（不按骨骼 pivot 重定基）——骨骼矩阵直接套用。
 */
public final class SkinMesh {

    /** 顶点出口：变形后的一个顶点回调一次，由渲染层落进各自的顶点格式。 */
    public interface Sink {
        void vertex(float x, float y, float z, float u, float v,
                    float nx, float ny, float nz);
    }

    public final String texture;
    /** 全部三角形（SMD/BMD/YSM 本来就是三角网格，不做四边化）。 */
    public final float[] xyz;
    public final float[] uv;
    public final float[] nrm;
    /** 每顶点 4 个骨骼下标（不足补 -1）。 */
    public final short[] bones;
    /** 每顶点 4 个权重（已归一，与 bones 对齐；-1 槽位权重为 0）。 */
    public final float[] weight;

    SkinMesh(String texture, float[] xyz, float[] uv, float[] nrm,
             short[] bones, float[] weight) {
        this.texture = texture;
        this.xyz = xyz;
        this.uv = uv;
        this.nrm = nrm;
        this.bones = bones;
        this.weight = weight;
    }

    public int vertices() {
        return xyz.length / 3;
    }

    public int triangles() {
        return xyz.length / 9;
    }

    /** 流式构建：add 一个三角形 + 三个顶点的权重，build 时按贴图分块。 */
    public static final class Builder {
        private final Map<String, FloatBuf> byTexture = new LinkedHashMap<String, FloatBuf>();

        /** 加一个三角形；每顶点 bones/weights 等长且不超过 4，权重自动归一。 */
        public void triangle(String texture,
                             float[][] p, float[][] t, float[][] n,
                             int[][] bi, float[][] bw) {
            FloatBuf buf = byTexture.get(texture);
            if (buf == null) {
                buf = new FloatBuf();
                byTexture.put(texture, buf);
            }
            for (int v = 0; v < 3; v++) {
                buf.put(p[v][0], p[v][1], p[v][2], t[v][0], t[v][1],
                        n[v][0], n[v][1], n[v][2], bi[v], bw[v]);
            }
        }

        public List<SkinMesh> build() {
            List<SkinMesh> out = new ArrayList<SkinMesh>();
            for (Map.Entry<String, FloatBuf> e : byTexture.entrySet()) {
                out.add(e.getValue().toMesh(e.getKey()));
            }
            return out;
        }
    }

    private static final class FloatBuf {
        final List<float[]> pos = new ArrayList<float[]>();
        final List<float[]> tex = new ArrayList<float[]>();
        final List<float[]> nor = new ArrayList<float[]>();
        final List<int[]> b = new ArrayList<int[]>();
        final List<float[]> w = new ArrayList<float[]>();

        void put(float x, float y, float z, float u, float v,
                 float nx, float ny, float nz, int[] bi, float[] bw) {
            pos.add(new float[]{x, y, z});
            tex.add(new float[]{u, v});
            nor.add(new float[]{nx, ny, nz});
            int[] bi4 = new int[]{-1, -1, -1, -1};
            float[] bw4 = new float[4];
            float sum = 0;
            for (int i = 0; i < bi.length && i < 4; i++) {
                bi4[i] = bi[i];
                bw4[i] = bw[i];
                sum += bw[i];
            }
            if (sum > 0) {
                for (int i = 0; i < 4; i++) {
                    bw4[i] /= sum;
                }
            }
            b.add(bi4);
            w.add(bw4);
        }

        SkinMesh toMesh(String texture) {
            int n = pos.size();
            float[] xyz = new float[n * 3];
            float[] uv = new float[n * 2];
            float[] nrm = new float[n * 3];
            short[] bones = new short[n * 4];
            float[] weight = new float[n * 4];
            for (int i = 0; i < n; i++) {
                float[] p = pos.get(i);
                float[] t = tex.get(i);
                float[] nr = nor.get(i);
                int[] bi = b.get(i);
                float[] bw = w.get(i);
                System.arraycopy(p, 0, xyz, i * 3, 3);
                System.arraycopy(t, 0, uv, i * 2, 2);
                System.arraycopy(nr, 0, nrm, i * 3, 3);
                for (int k = 0; k < 4; k++) {
                    bones[i * 4 + k] = (short) bi[k];
                    weight[i * 4 + k] = bw[k];
                }
            }
            return new SkinMesh(texture, xyz, uv, nrm, bones, weight);
        }
    }
}
