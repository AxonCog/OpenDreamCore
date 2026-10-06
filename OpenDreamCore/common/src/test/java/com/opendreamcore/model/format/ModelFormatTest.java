package com.opendreamcore.model.format;

import org.junit.jupiter.api.Test;

import com.opendreamcore.model.ir.AnimationIR;
import com.opendreamcore.model.ir.ModelIR;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** P1 契约：真实样例（bbmodel×3 / geo×2 / obj×1）全部解构成 IR。 */
class ModelFormatTest {

    private static String load(String name) throws Exception {
        InputStream in = ModelFormatTest.class.getResourceAsStream("/model/sample/" + name);
        assertNotNull(in, "样例缺失: " + name);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        in.close();
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private static int countBones(ModelIR.BoneIR b) {
        int n = 1;
        for (ModelIR.BoneIR c : b.children) {
            n += countBones(c);
        }
        return n;
    }

    @Test
    void bbmodelRpgSkeleton() throws Exception {
        DecodeResult r = FormatCodecs.decode("rpg_skeleton.bbmodel", load("rpg_skeleton.bbmodel"));
        assertEquals("bbmodel", r.model.meta.sourceFormat);
        assertTrue(countBones(r.model.root) > 3, "骨骼树应 >3");
        assertTrue(r.model.root.children.size() > 0, "根下应有骨骼组");
        int meshes = 0;
        java.util.List<ModelIR.BoneIR> flat = new java.util.ArrayList<ModelIR.BoneIR>();
        r.model.flatten(flat);
        for (ModelIR.BoneIR c : flat) {
            meshes += c.meshes.size();
        }
        assertTrue(meshes > 40, "47 个元素应基本全部挂上（实挂 " + meshes + "）");
        // 内嵌贴图（base64）：实样含两张（主+发光层）
        assertEquals(2, r.model.materials.size());
        assertTrue(r.model.materials.get(0).texture.startsWith("embedded:"), "内嵌贴图应解码为 embedded:");
        assertEquals(2, r.embeddedTextures.size());
        assertTrue(r.embeddedTextures.get(0).data.length > 100, "PNG 字节应已解码");
        // 动画：idle/walk/death/attack1/attack2；loop 实样为 "loop"/"once" 字符串
        assertEquals(5, r.animations.size());
        AnimationIR idle = null;
        for (AnimationIR a : r.animations) {
            if ("idle".equals(a.name)) {
                idle = a;
            }
        }
        assertNotNull(idle);
        assertEquals(2.0, idle.length, 1e-9);
        assertTrue(idle.loop, "idle 应为循环动画");
        assertTrue(idle.bones.size() > 5, "idle 应驱动多骨骼");
        AnimationIR.BoneAnim jaw = idle.bones.get("h_jaw");
        assertNotNull(jaw, "应有 h_jaw 骨骼轨道");
        assertNotNull(jaw.rot);
        assertTrue(jaw.rot.keys.size() >= 2);
        assertEquals("catmullrom", jaw.rot.keys.get(0).interp);
        // 逐面 UV（rpg_skeleton box_uv=false）
        for (ModelIR.BoneIR c : r.model.root.children) {
            for (ModelIR.Mesh m : c.meshes) {
                ModelIR.BoxMesh box = (ModelIR.BoxMesh) m;
                assertTrue(box.uv[0][2] > box.uv[0][0], "逐面 UV 应有跨度");
                return;
            }
        }
    }

    @Test
    void bbmodelBoxUv() throws Exception {
        DecodeResult r = FormatCodecs.decode("hz1.bbmodel", load("hz1.bbmodel"));
        // hz1 是 box_uv=true：盒式展开后各面 UV 应非零跨度
        assertTrue(r.model.root.children.size() > 0);
        boolean checked = false;
        for (ModelIR.BoneIR c : r.model.root.children) {
            for (ModelIR.Mesh m : c.meshes) {
                ModelIR.BoxMesh box = (ModelIR.BoxMesh) m;
                assertTrue(box.boxUv, "hz1 元素应为盒式 UV");
                assertTrue(box.uv[0][2] > box.uv[0][0], "north 面 UV 应展开");
                assertTrue(box.uv[4][3] > box.uv[4][1], "up 面 UV 应展开");
                checked = true;
            }
        }
        assertTrue(checked, "至少应有一个 cube");
    }

    @Test
    void bbmodelZombie() throws Exception {
        DecodeResult r = FormatCodecs.decode("rpg_zombie1.bbmodel", load("rpg_zombie1.bbmodel"));
        assertTrue(countBones(r.model.root) > 3);
        assertTrue(r.animations.size() > 0);
    }

    @Test
    void geoJsonBat() throws Exception {
        DecodeResult r = FormatCodecs.decode("bat.geo.json", load("bat.geo.json"));
        assertEquals("geo.json", r.model.meta.sourceFormat);
        assertTrue(countBones(r.model.root) > 3, "bat 应有多骨骼");
        assertTrue(r.model.meta.textureWidth > 0, "应有 texture_width");
        // cubes 解析成 BoxMesh
        int cubes = 0;
        java.util.List<ModelIR.BoneIR> flat = new java.util.ArrayList<ModelIR.BoneIR>();
        r.model.flatten(flat);
        for (ModelIR.BoneIR b : flat) {
            cubes += b.meshes.size();
        }
        assertTrue(cubes > 5, "bat 应有多个 cube");
    }

    @Test
    void geoJsonCreeper() throws Exception {
        DecodeResult r = FormatCodecs.decode("creeper.geo.json", load("creeper.geo.json"));
        assertTrue(countBones(r.model.root) > 2);
        assertNotNull(r.model.bone("head"), "creeper 应有 head 骨骼");
    }

    @Test
    void objCube() throws Exception {
        DecodeResult r = FormatCodecs.decode("cube.obj", load("cube.obj"));
        assertEquals("obj", r.model.meta.sourceFormat);
        assertEquals(1, r.model.root.children.size(), "命名对象 'o Cube' 应成为根下子骨骼");
        assertEquals(1, r.model.root.children.size(), "命名对象 'o Cube' 应成为根下子骨骼");
        ModelIR.BoneIR owner = r.model.root.children.get(0);
        assertEquals(2, owner.meshes.size(), "两个 f 面 → 两个 TriMesh（每 f 行独立）");
        int totalIdx = 0;
        for (ModelIR.Mesh mm : owner.meshes) {
            totalIdx += ((ModelIR.TriMesh) mm).indices.length;
        }
        assertEquals(12, totalIdx, "两个四边形各扇形化为 2 三角 → 共 12 个索引");
        ModelIR.TriMesh mesh = (ModelIR.TriMesh) owner.meshes.get(0);
        assertEquals(8 * 3, mesh.positions.length);
        // 两个四边形 → 4 个三角索引
        assertEquals(6, mesh.indices.length);
        assertEquals(4 * 2, mesh.uvs.length, "样例 4 条 vt");
        assertEquals(2 * 3, mesh.normals.length);
        assertEquals(1, r.model.materials.size());
        assertEquals("main", r.model.materials.get(0).texture);
    }
    @Test
    void unknownFormatRejected() {
        boolean thrown = false;
        try {
            FormatCodecs.decode("x.unknown", "hello");
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        assertTrue(thrown, "未知格式应报错");
    }
}
