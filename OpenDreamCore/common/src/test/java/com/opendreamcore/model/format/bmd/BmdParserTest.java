package com.opendreamcore.model.format.bmd;

import com.opendreamcore.model.bake.SkinMesh;
import com.opendreamcore.model.format.DecodeResult;
import com.opendreamcore.model.ir.ModelIR;
import org.junit.jupiter.api.Test;
import org.tukaani.xz.XZOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 自造 BMD 样例：v1 裸流 + v2 XZ 载荷全链（节点/静止位姿/材质/权重三角）。 */
class BmdParserTest {

    /** 按布局手写一个两骨骼、两三角（其一带双骨权重）的 v1 裸流。 */
    private static byte[] v1Sample() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream d = new DataOutputStream(bos);
        d.write(1);
        writeV1Nodes(d);
        writeV1Skeletons(d);
        // materials
        d.writeShort(1);
        writeUtf16Name(d, "t.png");
        // triangles
        d.writeShort(2);
        for (int tri = 0; tri < 2; tri++) {
            d.writeByte(0); // 材质索引
            for (int v = 0; v < 3; v++) {
                d.writeShort(0); // 保留
                d.writeFloat(tri);            // x
                d.writeFloat(1.0f + tri);     // y（入库取反）
                d.writeFloat(2.0f);           // z（入库取反）
                d.writeFloat(0f);
                d.writeFloat(0f);
                d.writeFloat(1.0f);           // 法线 +z（y/z 取反后仍是 +z）
                d.writeFloat(v * 0.5f);
                d.writeFloat(tri == 0 ? 0.25f : 0.75f);
                if (tri == 1 && v == 0) {
                    // 双骨权重链
                    d.writeByte(2);
                    d.writeShort(0);
                    d.writeFloat(0.25f);
                    d.writeShort(1);
                    d.writeFloat(0.75f);
                } else {
                    d.writeByte(1);
                    d.writeShort(0);
                    d.writeFloat(1.0f);
                }
            }
        }
        return bos.toByteArray();
    }

    /** nodes：名字与真实文件同（UTF-16BE，U+0000 结尾）。 */
    private static void writeV1Nodes(DataOutputStream d) throws IOException {
        d.writeShort(2);
        d.writeShort(0);
        d.writeShort(-1);
        writeUtf16Name(d, "root");
        d.writeShort(1);
        d.writeShort(0);
        writeUtf16Name(d, "arm");
    }

    /** skeletons：3 组（静止位姿 + 2 帧），验证多组 → AnimationIR。 */
    private static void writeV1Skeletons(DataOutputStream d) throws IOException {
        d.writeShort(3);
        d.writeShort(1);
        d.writeShort(1);
        d.writeFloat(1.0f);   // x
        d.writeFloat(2.0f);   // y（入库 -2）
        d.writeFloat(3.0f);   // z（入库 -3）
        d.writeFloat(0f);
        d.writeFloat(90f);
        d.writeFloat(0f);
        for (int g = 1; g < 3; g++) {
            d.writeShort(1);      // 帧内条目数
            d.writeShort(1);      // arm
            d.writeFloat(g);
            d.writeFloat(0f);
            d.writeFloat(0f);
            d.writeFloat(0f);
            d.writeFloat(g * 10f);
            d.writeFloat(0f);
        }
    }

    private static void writeUtf16Name(DataOutputStream d, String name) throws IOException {
        for (int i = 0; i < name.length(); i++) {
            d.writeChar(name.charAt(i));
        }
        d.writeChar(0);
    }

    @Test
    void v1裸流全链解析() throws Exception {
        BmdParser.BmdDocument doc = BmdParser.parse(new ByteArrayInputStream(v1Sample()));
        assertEquals(2, doc.bones.length);
        assertEquals("arm", doc.bones[1].name);
        assertEquals(0, doc.bones[1].parent);
        // 静止位姿 y/z 取反，角度同理（(x,-y,-z) 是绕 X 转 180° 的镜像，
        // 旋转向量同变换：rx,-ry,-rz）
        assertEquals(-2.0, doc.restPose[1][1], 1e-6);
        assertEquals(-3.0, doc.restPose[1][2], 1e-6);
        assertEquals(-90.0, doc.restPose[1][4], 1e-6);
        // 三角入库：6 顶点 2 三角
        assertEquals(1, doc.meshes.size());
        assertEquals(2, doc.meshes.get(0).triangles());
        // 顶点 y/z 取反：顶点 (tri, 1+tri, 2) → (tri, -(1+tri), -2)
        assertEquals(-1.0f, doc.meshes.get(0).xyz[3 + 1], 1e-6);
        assertEquals(-2.0f, doc.meshes.get(0).xyz[3 + 2], 1e-6);
        // v 翻转：0.25 → 0.75
        assertEquals(0.75f, doc.meshes.get(0).uv[1], 1e-6);
        // 权重链：第 4 个顶点（下标 3，即 tri1 首顶点）双骨 0.25/0.75
        assertEquals(0, doc.meshes.get(0).bones[3 * 4]);
        assertEquals(0.25f, doc.meshes.get(0).weight[3 * 4], 1e-6);
        assertEquals(1, doc.meshes.get(0).bones[3 * 4 + 1]);
        assertEquals(0.75f, doc.meshes.get(0).weight[3 * 4 + 1], 1e-6);
    }

    @Test
    void v2xz载荷解压后同布局() throws Exception {
        ByteArrayOutputStream xz = new ByteArrayOutputStream();
        XZOutputStream out = new XZOutputStream(xz, new org.tukaani.xz.LZMA2Options());
        out.write(v1Sample());
        out.finish();
        byte[] wrapped = new byte[xz.size() + 1];
        wrapped[0] = 2;
        System.arraycopy(xz.toByteArray(), 0, wrapped, 1, xz.size());

        BmdParser.BmdDocument doc = BmdParser.parse(new ByteArrayInputStream(wrapped));
        assertEquals(2, doc.bones.length);
        assertEquals(2, doc.meshes.get(0).triangles());
        assertEquals(6, doc.meshes.get(0).vertices());
    }

    @Test
    void 版本字节不对拒绝() {
        assertThrows(IOException.class,
                () -> BmdParser.parse(new ByteArrayInputStream(new byte[]{7, 0, 0})));
    }

    /** 真实导出文件不进仓库，本机有就验：网格骨骼+蒙皮全链 decode。 */
    @Test
    void 真实样本若在则全解() throws Exception {
        java.nio.file.Path dir = java.nio.file.Paths.get(
                "D:/下载备份/害怕杰顿/pokemon/005_charmeleon/all/base/none");
        if (!java.nio.file.Files.isDirectory(dir)) {
            return;
        }
        BmdCodec codec = new BmdCodec();
        byte[] meshBytes = java.nio.file.Files.readAllBytes(dir.resolve("charmeleon.bmd"));
        BmdParser.BmdDocument mesh = BmdParser.parse(new ByteArrayInputStream(meshBytes));
        assertTrue(mesh.bones.length > 0, "网格骨骼为空");
        assertFalse(mesh.meshes.isEmpty(), "网格三角为空");
        int verts = 0;
        for (SkinMesh m : mesh.meshes) {
            verts += m.vertices();
        }
        assertTrue(verts > 100, "真实网格顶点异常少: " + verts);
        DecodeResult r = codec.decode(meshBytes);
        java.util.List<ModelIR.BoneIR> flat = new java.util.ArrayList<ModelIR.BoneIR>();
        r.model.flatten(flat);
        assertFalse(flat.isEmpty(), "decode 骨架为空");
        for (String anim : new String[]{"idle.bmd", "walk.bmd"}) {
            byte[] ab = java.nio.file.Files.readAllBytes(dir.resolve(anim));
            BmdParser.BmdDocument a = BmdParser.parse(new java.io.ByteArrayInputStream(ab));
            assertTrue(a.bones.length > 0, anim + " 无骨骼");
            DecodeResult ar = codec.decode(ab);
            assertFalse(ar.animations.isEmpty(), anim + " 未产帧动画（骨架组应 >1）");
            com.opendreamcore.model.ir.AnimationIR ir = ar.animations.get(0);
            assertTrue(ir.length > 0, anim + " 动画时长为 0");
            assertFalse(ir.bones.isEmpty(), anim + " 无骨骼轨道");
        }
    }
}
