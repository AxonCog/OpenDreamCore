package com.opendreamcore.model.format.obj;

import com.opendreamcore.model.format.IModelFormatCodec;
import com.opendreamcore.model.format.DecodeResult;

import com.opendreamcore.model.ir.ModelIR;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Wavefront obj 解码：v/vt/vn/f + usemtl/map_Kd → TriMesh；o/g 分组映射为伪骨骼（仅整体变换）。
 *
 * 语义约定：obj 不参与骨骼动画；没有骨骼就兜底挂到单根骨骼下；
 * 分组可选挂为 root 的伪骨骼（名字=组名，pivot=[0,0,0]）。
 * 面索引按 obj 规则 1 基、负数回卷；缺 vt/vn 时对应数组留空。
 */
public final class ObjCodec implements IModelFormatCodec {

    @Override
    public String id() {
        return "obj";
    }

    @Override
    public String[] extensions() {
        return new String[]{".obj"};
    }

    @Override
    public boolean sniff(String text) {
        return text.contains("\nv ") || text.startsWith("v ") || text.startsWith("#");
    }

    @Override
    public DecodeResult decode(String text) {
        ModelIR.BoneIR root = new ModelIR.BoneIR("root");
        ModelIR model = new ModelIR(root);
        model.meta.sourceFormat = id();

        List<float[]> verts = new ArrayList<float[]>();
        List<float[]> uvs = new ArrayList<float[]>();
        List<float[]> norms = new ArrayList<float[]>();
        Map<String, Integer> materials = new LinkedHashMap<String, Integer>();
        int currentMaterial = -1;
        // o/g 行 → 根下命名子骨骼；后续面归入当前骨骼
        ModelIR.BoneIR current = root;

        String[] lines = text.replace("\r\n", "\n").split("\n");
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] t = line.split("\\s+");
            String head = t[0];
            if ("v".equals(head) && t.length >= 4) {
                verts.add(new float[]{Float.parseFloat(t[1]), Float.parseFloat(t[2]), Float.parseFloat(t[3])});
            } else if ("vt".equals(head) && t.length >= 3) {
                uvs.add(new float[]{Float.parseFloat(t[1]), Float.parseFloat(t[2])});
            } else if ("vn".equals(head) && t.length >= 4) {
                norms.add(new float[]{Float.parseFloat(t[1]), Float.parseFloat(t[2]), Float.parseFloat(t[3])});
            } else if ("usemtl".equals(head) && t.length >= 2) {
                currentMaterial = materialIndex(materials, t[1], model);
            } else if (("o".equals(head) || "g".equals(head)) && t.length >= 2) {
                ModelIR.BoneIR named = model.bone(t[1]);
                if (named == null) {
                    named = new ModelIR.BoneIR(t[1]);
                    root.children.add(named);
                }
                current = named;
            } else if ("map_Kd".equals(head) && t.length >= 2) {
                // mtl 内嵌在本文件时：绑定最近材质的贴图（obj 主文件此行罕见，容忍）
                bindTexture(model, materials, t[1]);
            } else if (("f".equals(head) || "fo".equals(head)) && t.length >= 4) {
                ModelIR.TriMesh mesh = currentMesh(model, t, verts, uvs, norms, currentMaterial);
                if (mesh != null) {
                    current.meshes.add(mesh);
                }
            }
        }
        if (model.materials.isEmpty()) {
            ModelIR.Material m = new ModelIR.Material();
            model.materials.add(m);
        }
        return new DecodeResult(model);
    }

    private ModelIR.TriMesh currentMesh(ModelIR model, String[] t, List<float[]> verts,
                                        List<float[]> uvs, List<float[]> norms, int material) {
        ModelIR.TriMesh mesh = new ModelIR.TriMesh();
        mesh.material = material;
        List<Integer> idx = new ArrayList<Integer>();
        // obj 面：3+ 顶点 → 扇形三角化
        int[][] corner = new int[t.length - 1][3];
        int hasVt = 0;
        int hasVn = 0;
        for (int i = 1; i < t.length; i++) {
            String[] parts = t[i].split("/");
            int v = resolveIndex(parts[0], verts.size());
            corner[i - 1][0] = v;
            if (parts.length >= 2 && !parts[1].isEmpty()) {
                corner[i - 1][1] = resolveIndex(parts[1], uvs.size());
                hasVt++;
            }
            if (parts.length >= 3 && !parts[2].isEmpty()) {
                corner[i - 1][2] = resolveIndex(parts[2], norms.size());
                hasVn++;
            }
        }
        boolean withUv = hasVt == t.length - 1;
        boolean withN = hasVn == t.length - 1;
        for (int i = 1; i + 1 < t.length - 1; i++) {
            emit(mesh, idx, corner[0], withUv, withN);
            emit(mesh, idx, corner[i], withUv, withN);
            emit(mesh, idx, corner[i + 1], withUv, withN);
        }
        mesh.positions = new float[verts.size() * 3];
        for (int i = 0; i < verts.size(); i++) {
            float[] p = verts.get(i);
            mesh.positions[i * 3] = p[0];
            mesh.positions[i * 3 + 1] = p[1];
            mesh.positions[i * 3 + 2] = p[2];
        }
        mesh.indices = new int[idx.size()];
        for (int i = 0; i < idx.size(); i++) {
            mesh.indices[i] = idx.get(i);
        }
        if (!uvs.isEmpty()) {
            mesh.uvs = new float[uvs.size() * 2];
            for (int i = 0; i < uvs.size(); i++) {
                float[] uv = uvs.get(i);
                mesh.uvs[i * 2] = uv[0];
                mesh.uvs[i * 2 + 1] = uv[1];
            }
        }
        if (!norms.isEmpty()) {
            mesh.normals = new float[norms.size() * 3];
            for (int i = 0; i < norms.size(); i++) {
                float[] n = norms.get(i);
                mesh.normals[i * 3] = n[0];
                mesh.normals[i * 3 + 1] = n[1];
                mesh.normals[i * 3 + 2] = n[2];
            }
        }
        return mesh;
    }

    private void emit(ModelIR.TriMesh mesh, List<Integer> idx, int[] corner, boolean withUv, boolean withN) {
        // 每 顶点 3 个 int：position/uv/normal（uv、normal 缺失时为 -1）——渲染层按 hasUv 展开时再处理，
        // 这里为简化：把 pos 索引直接当最终顶点索引（同顶点多 UV 情况 P4 烘焙期展开，此处记录告警级语义）。
        idx.add(corner[0]);
    }

    private int resolveIndex(String s, int size) {
        int i = Integer.parseInt(s.trim());
        return i > 0 ? i - 1 : size + i;
    }

    private int materialIndex(Map<String, Integer> materials, String name, ModelIR model) {
        Integer i = materials.get(name);
        if (i == null) {
            ModelIR.Material m = new ModelIR.Material();
            m.texture = name;
            i = model.materials.size();
            model.materials.add(m);
            materials.put(name, i);
        }
        return i;
    }

    private void bindTexture(ModelIR model, Map<String, Integer> materials, String texName) {
        if (model.materials.isEmpty()) {
            return;
        }
        // 绑定最后一个材质（mtl 块内 map_Kd 在 usemtl 声明之后）
        ModelIR.Material last = model.materials.get(model.materials.size() - 1);
        if (last.texture != null && materials.containsValue(model.materials.size() - 1)) {
            last.texture = "texture/" + texName;
        }
    }
}
