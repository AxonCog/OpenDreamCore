package com.opendreamcore.model.bake;

import com.opendreamcore.model.ir.ModelIR;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 骨骼化烘焙：ModelIR → BakedRig（前序扁平骨骼表 + pivot 相对顶点桶）。
 * 复用 QuadBaker 的盒/三角烘焙，烘焙后把顶点坐标平移到 pivot 相对。
 */
public final class RigBaker {

    private RigBaker() {
    }

    public static BakedRig rig(ModelIR model) {
        List<ModelIR.BoneIR> flat = new ArrayList<ModelIR.BoneIR>();
        model.flatten(flat);
        BakedRig.Bone[] bones = new BakedRig.Bone[flat.size()];
        for (int i = 0; i < flat.size(); i++) {
            ModelIR.BoneIR src = flat.get(i);
            // 父索引：flatten 顺序保证父先于子；按名字回查
            int parent = -1;
            ModelIR.BoneIR walk = src;
            // flatten 无父指针，向上找：这里用简单法——遍历 flat 找包含 src 的父（模型规模小，可接受）
            for (int j = i - 1; j >= 0; j--) {
                if (contains(flat.get(j), src)) {
                    parent = j;
                    break;
                }
            }
            BakedRig.Bone bone = new BakedRig.Bone(
                    src.name, parent,
                    src.pivot != null && src.pivot.length == 3 ? src.pivot.clone() : new double[3],
                    // bindRotation 归一：长度非 3（解析缺省时空表）直接视为无绑旋转，
                    // 渲染端 rot[0] 取值才不会炸（实机 crash-23.44 根因）
                    src.bindRotation != null && src.bindRotation.length == 3
                            ? src.bindRotation.clone() : null);
            bones[i] = bone;
            bakeBone(model, src, bone);
        }
        return new BakedRig(bones);
    }

    private static boolean contains(ModelIR.BoneIR ancestor, ModelIR.BoneIR target) {
        if (ancestor == target) {
            return false;
        }
        for (ModelIR.BoneIR c : ancestor.children) {
            if (c == target || contains(c, target)) {
                return true;
            }
        }
        return false;
    }

    private static void bakeBone(ModelIR model, ModelIR.BoneIR src, BakedRig.Bone bone) {
        if (src.meshes.isEmpty()) {
            return;
        }
        Map<String, QuadBaker.Buf> byTex = new LinkedHashMap<String, QuadBaker.Buf>();
        for (ModelIR.Mesh mesh : src.meshes) {
            QuadBaker.bakeMesh(model, mesh, byTex);
        }
        for (Map.Entry<String, QuadBaker.Buf> e : byTex.entrySet()) {
            float[] quads = e.getValue().quadArray();
            float[] tris = e.getValue().triArray();
            rebase(quads, bone.pivot);
            rebase(tris, bone.pivot);
            if (quads.length > 0 || tris.length > 0) {
                bone.parts.add(new BakedRig.Part(e.getKey(), quads, tris));
            }
        }
    }

    /** 顶点坐标平移到 pivot 相对（每顶点前 3 分量）。 */
    private static void rebase(float[] data, double[] pivot) {
        for (int i = 0; i + 2 < data.length; i += 8) {
            data[i] -= (float) pivot[0];
            data[i + 1] -= (float) pivot[1];
            data[i + 2] -= (float) pivot[2];
        }
    }

}
