package com.opendreamcore.model.bake;

import com.opendreamcore.model.anim.BoneXform;

import java.util.List;

/**
 * 蒙皮采样：顶点按权重混合骨骼世界矩阵后回调。
 *
 * <p>位置 = Σ w·(Mᵢ·v)，法线 = Σ w·(Mᵢ 方向) 后归一。
 * 刚性（单骨骼权重 1.0）自然退化成普通骨骼变换，不必特判。
 *
 * <p>矩阵数组与 {@link BakedRig#bones} 一一对应（渲染层按现代
 * GenModelRenderer 或 Legacy 两套各自烘焙，本类只做混合，不关心矩阵怎么来）。
 */
public final class SkinSampler {

    private SkinSampler() {
    }

    private static final double[] PX = new double[3];
    private static final double[] NX = new double[3];

    /**
     * 采样一块蒙皮网格。outXyz/outNrm 由调用方提供复用缓冲（长度 ≥ 顶点数×3），
     * 便于热路径零分配。
     */
    public static void sample(SkinMesh mesh, BoneXform[] world,
                              double[] outXyz, double[] outNrm,
                              SkinMesh.Sink sink) {
        int n = mesh.vertices();
        for (int i = 0; i < n; i++) {
            double px = 0, py = 0, pz = 0;
            double nx = 0, ny = 0, nz = 0;
            float x = mesh.xyz[i * 3], y = mesh.xyz[i * 3 + 1], z = mesh.xyz[i * 3 + 2];
            for (int k = 0; k < 4; k++) {
                short bi = mesh.bones[i * 4 + k];
                if (bi < 0) {
                    break;
                }
                float w = mesh.weight[i * 4 + k];
                if (w == 0) {
                    continue;
                }
                BoneXform m = world[bi];
                m.apply(x, y, z, PX);
                m.applyDir(mesh.nrm[i * 3], mesh.nrm[i * 3 + 1], mesh.nrm[i * 3 + 2], NX);
                px += PX[0] * w;
                py += PX[1] * w;
                pz += PX[2] * w;
                nx += NX[0] * w;
                ny += NX[1] * w;
                nz += NX[2] * w;
            }
            double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len > 1e-9) {
                nx /= len;
                ny /= len;
                nz /= len;
            }
            outXyz[i * 3] = px;
            outXyz[i * 3 + 1] = py;
            outXyz[i * 3 + 2] = pz;
            outNrm[i * 3] = nx;
            outNrm[i * 3 + 1] = ny;
            outNrm[i * 3 + 2] = nz;
        }
        // 三角形逐顶点回调（蒙皮网格全三角，三顶点一组）
        for (int t = 0; t + 2 < n; t += 3) {
            for (int v = 0; v < 3; v++) {
                int i = t + v;
                sink.vertex((float) outXyz[i * 3], (float) outXyz[i * 3 + 1],
                        (float) outXyz[i * 3 + 2],
                        mesh.uv[i * 2], mesh.uv[i * 2 + 1],
                        (float) outNrm[i * 3], (float) outNrm[i * 3 + 1],
                        (float) outNrm[i * 3 + 2]);
            }
        }
    }

    /**
     * 刚性骨架统一成蒙皮网格：每顶点权重 1.0 绑回自己的骨骼。
     * Part 顶点是 pivot 局部坐标，这里加回 pivot 还原模型空间。
     */
    public static List<SkinMesh> unify(BakedRig rig) {
        SkinMesh.Builder builder = new SkinMesh.Builder();
        for (int b = 0; b < rig.bones.length; b++) {
            BakedRig.Bone bone = rig.bones[b];
            double[] pv = bone.pivot;
            for (BakedRig.Part part : bone.parts) {
                emit(builder, part.texture, part.tris, 3, b, pv);
                emit(builder, part.texture, part.quads, 4, b, pv);
            }
        }
        return builder.build();
    }

    private static void emit(SkinMesh.Builder builder, String texture,
                             float[] data, int vpp, int bone, double[] pivot) {
        if (data == null) {
            return;
        }
        for (int off = 0; off + vpp * 8 <= data.length; off += vpp * 8) {
            if (vpp == 4) {
                // 四边形拆两三角（对角分割，与烘焙顺序一致）
                tri(builder, texture, data, off, 0, 1, 2, bone, pivot);
                tri(builder, texture, data, off, 0, 2, 3, bone, pivot);
            } else {
                tri(builder, texture, data, off, 0, 1, 2, bone, pivot);
            }
        }
    }

    private static void tri(SkinMesh.Builder builder, String texture,
                            float[] data, int off, int a, int b, int c,
                            int bone, double[] pivot) {
        float[][] p = new float[3][3];
        float[][] t = new float[3][2];
        float[][] n = new float[3][3];
        int[] idx = {a, b, c};
        int[][] bi = new int[3][];
        float[][] bw = new float[3][];
        for (int v = 0; v < 3; v++) {
            int o = off + idx[v] * 8;
            p[v][0] = data[o] + (float) pivot[0];
            p[v][1] = data[o + 1] + (float) pivot[1];
            p[v][2] = data[o + 2] + (float) pivot[2];
            t[v][0] = data[o + 3];
            t[v][1] = data[o + 4];
            n[v][0] = data[o + 5];
            n[v][1] = data[o + 6];
            n[v][2] = data[o + 7];
            // 刚性绑定：单骨骼满权重，其余槽位留空
            bi[v] = new int[]{bone, -1, -1, -1};
            bw[v] = new float[]{1, 0, 0, 0};
        }
        builder.triangle(texture, p, t, n, bi, bw);
    }
}
