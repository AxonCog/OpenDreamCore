package com.opendreamcore.model.format.ysm;

import com.opendreamcore.model.format.DecodeResult;
import com.opendreamcore.model.ir.AnimationIR;
import com.opendreamcore.model.ir.ModelIR;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/** v3 裸格式：自造最小字节流往返 + 真实样本（存在才跑）。 */
class YsmCodecTest {

    private static void varint(ByteArrayOutputStream out, int v) {
        while ((v & ~0x7f) != 0) {
            out.write((v & 0x7f) | 0x80);
            v >>>= 7;
        }
        out.write(v);
    }

    private static void f32(ByteArrayOutputStream out, float f) {
        int b = Float.floatToIntBits(f);
        out.write(b);
        out.write(b >>> 8);
        out.write(b >>> 16);
        out.write(b >>> 24);
    }

    private static void str(ByteArrayOutputStream out, String s) {
        byte[] raw = s.getBytes(StandardCharsets.UTF_8);
        varint(out, raw.length);
        for (byte b : raw) {
            out.write(b);
        }
    }

    @Test
    void v3MinimalRoundTrip() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(31); // 格式号 int32 LE（后三字节为 0）
        out.write(0);
        out.write(0);
        out.write(0);
        writeBoneSegment(out);
        writeAnimSegment(out);
        DecodeResult r = new YsmCodec().decode(out.toByteArray());
        ModelIR.BoneIR body = r.model.root.name.equals("body")
                ? r.model.root : r.model.root.children.get(0);
        assertEquals("body", body.name);
        assertEquals(1, body.meshes.size());
        ModelIR.TriMesh mesh = (ModelIR.TriMesh) body.meshes.get(0);
        assertEquals(4, mesh.positions.length / 3); // 1 面 4 顶点
        assertEquals(1, r.animations.size());
        AnimationIR anim = r.animations.get(0);
        assertEquals("idle", anim.name);
        assertEquals(AnimationIR.LOOP_LOOP, anim.loopMode);
        assertEquals(45.0, anim.bones.get("body").rot.keys.get(0).value[2], 1e-6);
    }

    /** 骨骼段锚点：0x40 + 64 hex + 1 骨 1 面 4 顶点（带 UV/法线）。 */
    private static void writeBoneSegment(ByteArrayOutputStream out) {
        // 骨骼段锚点：0x40 + 64 个 hex + 骨骼数
        out.write(0x40);
        for (int i = 0; i < 64; i++) {
            out.write('a' + (i % 6));
        }
        varint(out, 1); // 1 根骨骼
        str(out, ""); // parent
        varint(out, 1); // 1 块立方体
        varint(out, 1); // 1 个面
        float[] nrm = {0, 0, 1};
        for (float f : nrm) {
            f32(out, f);
        }
        for (int v = 0; v < 4; v++) {
            f32(out, v - 1.5f);
            f32(out, v - 1.5f);
            f32(out, 0.5f);
            f32(out, v == 0 || v == 3 ? 0 : 1);
            f32(out, v < 2 ? 0 : 1);
        }
        varint(out, 0);
        varint(out, 0);
        varint(out, 0);
        str(out, "body");
        for (int i = 0; i < 5; i++) {
            varint(out, 0);
        }
        f32(out, 0);
        f32(out, 24);
        f32(out, 0);
        for (int i = 0; i < 3; i++) {
            f32(out, 0);
        }
    }

    /** 动画段：idle 1 秒循环，body 骨一帧旋转 45°。 */
    private static void writeAnimSegment(ByteArrayOutputStream out) {
        // 动画：idle，1 秒循环，root 骨骼一帧旋转
        str(out, "idle");
        f32(out, 20); // 20 tick = 1 秒
        varint(out, 1); // loop
        varint(out, 0);
        varint(out, 0);
        varint(out, 0); // 无 blend weight
        varint(out, 0); // assert
        varint(out, 1); // 1 骨
        str(out, "body");
        varint(out, 1); // rotation 一帧
        f32(out, 0);
        varint(out, 0);
        for (int c = 0; c < 3; c++) {
            out.write(0);
            f32(out, c == 2 ? 45 : 0);
        }
        out.write(0);
        varint(out, 0); // position
        varint(out, 0); // scale
        varint(out, 0); // 无 timeline
        varint(out, 0); // 无 sound effects
    }

/** 真实样本不进仓库，本机有就验（骨骼/贴图/动画三者齐全才叫解出来）。 */
    @Test
    void realSampleIfPresent() throws Exception {
        java.nio.file.Path sample = Paths.get("D:/下载备份/小小酒狐2.1.ysm.dec");
        Assumptions.assumeTrue(Files.exists(sample), "本机无样本，跳过");
        byte[] data = Files.readAllBytes(sample);
        DecodeResult r = new YsmCodec().decode(data);
        assertTrue(countBones(r.model.root) >= 2, "骨骼数 " + countBones(r.model.root));
        assertFalse(r.embeddedTextures.isEmpty(), "贴图数 0");
        assertFalse(r.animations.isEmpty(), "动画数 0");
        assertTrue(r.animations.stream().anyMatch(a -> !a.bones.isEmpty()), "动画都没绑到骨骼");
    }

    private static int countBones(ModelIR.BoneIR b) {
        int n = 1;
        for (ModelIR.BoneIR c : b.children) {
            n += countBones(c);
        }
        return n;
    }
}
