package com.opendreamcore.model.format;

import com.opendreamcore.model.bake.SkinMesh;
import com.opendreamcore.model.ir.ModelIR;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 蒙皮网格 → 统一骨架 IR。
 *
 * <p>SMD/BMD 这类权重蒙皮格式进来后，三角形按「首顶点主骨骼」归到骨，
 * 变成骨挂 TriMesh（与 bbmodel/geo/YSM 同一条渲染管线）。
 * 每顶点 4 权重里最大的说了算——刚性归骨对大多数模型观感无差，
 * 权重过渡留给后续需要时的骨骼级混合。
 */
public final class SkinToRig {

    private SkinToRig() {
    }

    /**
     * 拼装 ModelIR。
     *
     * @param boneNames 骨名（输入顺序即节点顺序）
     * @param parents   父骨下标（-1=根），与 boneNames 对齐
     * @param pivots    每骨枢轴（模型空间 3 分量）
     * @param restRot   每骨绑定旋转（角度制欧拉，可 null 或元素 null）
     * @param meshes    蒙皮网格（可空）
     */
    public static ModelIR toModelIR(String[] boneNames, int[] parents,
                                    double[][] pivots, double[][] restRot,
                                    List<SkinMesh> meshes) {
        int n = boneNames.length;
        ModelIR.BoneIR[] nodes = new ModelIR.BoneIR[n];
        for (int i = 0; i < n; i++) {
            nodes[i] = new ModelIR.BoneIR(boneNames[i]);
            if (pivots != null && i < pivots.length && pivots[i] != null && pivots[i].length >= 3) {
                nodes[i].pivot[0] = pivots[i][0];
                nodes[i].pivot[1] = pivots[i][1];
                nodes[i].pivot[2] = pivots[i][2];
            }
            if (restRot != null && i < restRot.length && restRot[i] != null
                    && restRot[i].length >= 3
                    && Math.abs(restRot[i][0]) + Math.abs(restRot[i][1]) + Math.abs(restRot[i][2]) > 1e-9) {
                nodes[i].bindRotation = restRot[i].clone();
            }
        }
        // 挂树：父下标有效就挂父，否则等当根；根有多个时收拢到第一个
        ModelIR.BoneIR root = null;
        List<ModelIR.BoneIR> looseRoots = new ArrayList<ModelIR.BoneIR>();
        for (int i = 0; i < n; i++) {
            int p = parents != null && i < parents.length ? parents[i] : -1;
            if (p >= 0 && p < n && p != i) {
                nodes[p].children.add(nodes[i]);
            } else {
                looseRoots.add(nodes[i]);
            }
        }
        for (ModelIR.BoneIR r : looseRoots) {
            if (root == null) {
                root = r;
            } else {
                root.children.add(r);
            }
        }
        if (root == null) {
            root = new ModelIR.BoneIR("root");
        }
        ModelIR ir = new ModelIR(root);

        Map<String, Integer> matIdx = new LinkedHashMap<String, Integer>();
        // (骨下标, 材质下标) → 顶点收集
        Map<Long, List<float[]>> pos = new LinkedHashMap<Long, List<float[]>>();
        Map<Long, List<float[]>> uv = new LinkedHashMap<Long, List<float[]>>();
        Map<Long, List<float[]>> nor = new LinkedHashMap<Long, List<float[]>>();
        for (SkinMesh m : meshes == null ? new ArrayList<SkinMesh>() : meshes) {
            int mat = matIdx.get(m.texture) != null ? matIdx.get(m.texture) : -1;
            if (mat < 0) {
                ModelIR.Material material = new ModelIR.Material();
                material.texture = m.texture;
                mat = ir.materials.size();
                ir.materials.add(material);
                matIdx.put(m.texture, mat);
            }
            int tris = m.triangles();
            for (int t = 0; t < tris; t++) {
                int base = t * 3;
                // 主骨骼 = 首顶点权重最大的骨
                int bone = dominantBone(m, base);
                if (bone < 0 || bone >= n) {
                    continue;
                }
                long key = ((long) bone << 20) | mat;
                List<float[]> pp = list(pos, key);
                List<float[]> tt = list(uv, key);
                List<float[]> nn = list(nor, key);
                for (int v = 0; v < 3; v++) {
                    int i = base + v;
                    pp.add(new float[]{m.xyz[i * 3], m.xyz[i * 3 + 1], m.xyz[i * 3 + 2]});
                    tt.add(new float[]{m.uv[i * 2], m.uv[i * 2 + 1]});
                    nn.add(m.nrm != null
                            ? new float[]{m.nrm[i * 3], m.nrm[i * 3 + 1], m.nrm[i * 3 + 2]}
                            : new float[]{0, 1, 0});
                }
            }
        }
        // 落成 TriMesh：索引按收集顺序隐式展开
        for (Map.Entry<Long, List<float[]>> e : pos.entrySet()) {
            long key = e.getKey();
            int bone = (int) (key >> 20);
            int mat = (int) (key & 0xFFFFF);
            List<float[]> pp = e.getValue();
            ModelIR.TriMesh tm = new ModelIR.TriMesh();
            float[] P = new float[pp.size() * 3];
            float[] T = new float[pp.size() * 2];
            float[] N = new float[pp.size() * 3];
            int[] idx = new int[pp.size()];
            for (int i = 0; i < pp.size(); i++) {
                System.arraycopy(pp.get(i), 0, P, i * 3, 3);
                float[] t = uv.get(key).get(i);
                T[i * 2] = t[0];
                T[i * 2 + 1] = t[1];
                System.arraycopy(nor.get(key).get(i), 0, N, i * 3, 3);
                idx[i] = i;
            }
            tm.positions = P;
            tm.uvs = T;
            tm.normals = N;
            tm.indices = idx;
            tm.material = mat;
            nodes[bone].meshes.add(tm);
        }
        return ir;
    }

    /** 首顶点权重最大的骨；全零权重回 -1（三角丢弃）。 */
    private static int dominantBone(SkinMesh m, int vertexBase) {
        int best = -1;
        float bestW = -1;
        for (int k = 0; k < 4; k++) {
            short b = m.bones[vertexBase * 4 + k];
            float w = m.weight[vertexBase * 4 + k];
            if (w > bestW) {
                bestW = w;
                best = b;
            }
        }
        return best;
    }

    private static <V> List<V> list(Map<Long, List<V>> map, long key) {
        List<V> l = map.get(key);
        if (l == null) {
            l = new ArrayList<V>();
            map.put(key, l);
        }
        return l;
    }
}
