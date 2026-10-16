package com.opendreamcore.model.format.smd;

import com.opendreamcore.model.bake.SkinMesh;
import com.opendreamcore.model.ir.AnimationIR;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 自造 SMD 样例：骨骼表/蒙皮三角/权重顶点/动画帧全链。 */
class SmdParserTest {

    private static final String MESH_SMD = String.join("\n",
            "version 1",
            "nodes",
            "0 \"root\" -1",
            "1 \"arm\" 0",
            "end",
            "triangles",
            "t.png",
            "0  0 0 0  0 0 1  0 0",
            "0  1 0 0  0 0 1  1 0",
            "0  0 1 0  0 0 1  0 1",
            "1  2 0 0  0 0 1  0 0",
            "1  3 0 0  0 0 1  1 0",
            "1  2 1 0  0 0 1  0 1  2  1 0.5  1 0.5",
            "end",
            "");

    private static final String ANIM_SMD = String.join("\n",
            "version 1",
            "nodes",
            "0 \"root\" -1",
            "end",
            "time 0",
            "0  0 0 0  0 0 0",
            "time 500",
            "0  0 10 0  0 0 90",
            "end",
            "");

    @Test
    void 骨骼表与蒙皮三角入库() throws Exception {
        SmdParser.SmdDocument d = SmdParser.parse(
                new StringReader(MESH_SMD), "m");
        assertEquals(2, d.bones.length);
        assertEquals("arm", d.bones[1].name);
        assertEquals(0, d.bones[1].parent);
        assertEquals(1, d.meshes.size());
        assertEquals(2, d.meshes.get(0).triangles());
        assertEquals(6, d.meshes.get(0).vertices());
        // 带权顶点：双骨各 0.5，归一不变
        float w0 = d.meshes.get(0).weight[5 * 4];
        float w1 = d.meshes.get(0).weight[5 * 4 + 1];
        assertEquals(0.5f, w0, 1e-6);
        assertEquals(0.5f, w1, 1e-6);
        // V 翻转：v=1 → 存 0
        assertEquals(0.0f, d.meshes.get(0).uv[5 * 2 + 1], 1e-6);
        assertNull(d.animation);
    }

    @Test
    void 动画帧入IR按骨骼名挂轨道() throws Exception {
        SmdParser.SmdDocument d = SmdParser.parse(
                new StringReader(ANIM_SMD), "idle");
        assertNotNull(d.animation);
        AnimationIR ir = d.animation;
        assertEquals("idle", ir.name);
        assertEquals(500.0, ir.length, 1e-9);
        assertEquals(1, ir.bones.size());
        AnimationIR.Track pos = ir.bones.get("root").pos;
        assertEquals(2, pos.keys.size());
        assertEquals(10.0, pos.keys.get(1).value[1], 1e-9);
        assertEquals(90.0, ir.bones.get("root").rot.keys.get(1).value[2], 1e-9);
        assertTrue(d.meshes.isEmpty());
    }

    @Test
    void 半骨骼化两段都收() throws Exception {
        SmdParser.SmdDocument d = SmdParser.parse(
                new StringReader(MESH_SMD + "\n" + ANIM_SMD), "mix");
        assertEquals(1, d.meshes.size());
        assertNotNull(d.animation);
    }

    @Test
    void 骨骼id空洞补位不崩() throws Exception {
        String s = String.join("\n",
                "version 1",
                "nodes",
                "0 \"a\" -1",
                "5 \"b\" 0",
                "end",
                "time 0",
                "5  1 0 0  0 0 0",
                "end",
                "");
        SmdParser.SmdDocument d = SmdParser.parse(new StringReader(s), "g");
        assertEquals(6, d.bones.length);
        assertEquals("b", d.bones[5].name);
        assertNotNull(d.animation);
    }

    /** 真实导出文件不进仓库，本机有就验：网格要骨骼+蒙皮，动画要有轨道。 */
    @Test
    void 真实样本若在则全解() throws Exception {
        java.nio.file.Path dir = java.nio.file.Paths.get("D:/下载备份/害怕杰顿");
        if (!java.nio.file.Files.isDirectory(dir)) {
            return;
        }
        SmdParser.SmdDocument mesh = SmdParser.parse(java.nio.file.Files.newBufferedReader(
                dir.resolve("sj.smd"), java.nio.charset.StandardCharsets.UTF_8), "sj");
        assertTrue(mesh.bones.length > 0, "网格骨骼为空");
        assertFalse(mesh.meshes.isEmpty(), "网格三角为空");
        int verts = 0;
        for (SkinMesh m : mesh.meshes) {
            verts += m.vertices();
        }
        assertTrue(verts > 1000, "真实网格顶点异常少: " + verts);
        for (String anim : new String[]{"idle.smd", "walk.smd"}) {
            SmdParser.SmdDocument a = SmdParser.parse(java.nio.file.Files.newBufferedReader(
                    dir.resolve(anim), java.nio.charset.StandardCharsets.UTF_8), anim);
            assertNotNull(a.animation, anim + " 无动画");
            assertFalse(a.animation.bones.isEmpty(), anim + " 无轨道");
        }
    }
}
