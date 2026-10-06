package com.opendreamcore.model.bake;

import com.opendreamcore.model.format.DecodeResult;
import com.opendreamcore.model.format.FormatCodecs;
import com.opendreamcore.model.ir.ModelIR;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P4 契约：obj(TriMesh) 样例单测 —— 解构 IR 之外再打通烘焙（TriMesh → 三角顶点表）。
 */
public class ObjBakeTest {

    private static String load(String name) throws Exception {
        InputStream in = ObjBakeTest.class.getResourceAsStream("/model/sample/" + name);
        try {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            in.close();
        }
    }

    /** 先序收集全部网格。 */
    private static void collect(ModelIR.BoneIR bone, java.util.List<ModelIR.Mesh> out) {
        out.addAll(bone.meshes);
        for (ModelIR.BoneIR c : bone.children) {
            collect(c, out);
        }
    }

    @Test
    public void objCubeBakesTris() throws Exception {
        DecodeResult r = FormatCodecs.decode("cube.obj", load("cube.obj"));
        assertTrue(r.model.meta.sourceFormat.equals("obj"));
        java.util.List<ModelIR.Mesh> meshes = new java.util.ArrayList<ModelIR.Mesh>();
        collect(r.model.root, meshes);
        boolean hasTri = false;
        for (ModelIR.Mesh m : meshes) {
            if (m instanceof ModelIR.TriMesh && ((ModelIR.TriMesh) m).indices.length >= 3) {
                hasTri = true;
            }
        }
        assertTrue(hasTri, "obj 样例解出 TriMesh（indices ≥ 3）");
        List<BakedModel> baked = QuadBaker.bake(r.model);
        assertTrue(!baked.isEmpty(), "烘焙产出非空");
        int triVerts = 0;
        for (BakedModel bm : baked) {
            triVerts += bm.tris.length;
        }
        assertTrue(triVerts >= 3 * 3, "三角顶点表非空（至少 1 个三角形 = 9 float）");
    }
}
