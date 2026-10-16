package com.opendreamcore.model.format.smd;

import com.opendreamcore.model.bake.SkinMesh;
import com.opendreamcore.model.ir.AnimationIR;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * SMD（Valve Studio Model Data，ASCII 文本）流式解析。
 *
 * <p>公开格式三段式：version 1 / nodes 骨骼表 / triangles 蒙皮三角或
 * time 动画帧块。一个文件可同时含 triangles 与 time 段（半骨骼化），
 * 都有就都解析。顶点行绑定权重最多取 4 根（超出按权重排序截断的义务
 * 在导出端，这里只保留前 4，和蒙皮 IR 的通道数一致）。
 *
 * <p>数值约定：SMD 角度为度，动画 IR 的旋转轨道同样存度（采样层统一转弧度）。
 */
public final class SmdParser {

    /** 解析结果：骨骼表 + 蒙皮网格 + 可选动画（triangles/time 段至少其一）。 */
    public static final class SmdDocument {
        public final Bone[] bones;
        public final List<SkinMesh> meshes;
        public final AnimationIR animation;

        SmdDocument(Bone[] bones, List<SkinMesh> meshes, AnimationIR animation) {
            this.bones = bones;
            this.meshes = meshes;
            this.animation = animation;
        }
    }

    public static final class Bone {
        public final int id;
        public final String name;
        public final int parent;

        Bone(int id, String name, int parent) {
            this.id = id;
            this.name = name;
            this.parent = parent;
        }
    }

    private SmdParser() {
    }

    public static SmdDocument parse(Reader reader, String name) throws IOException {
        BufferedReader in = reader instanceof BufferedReader
                ? (BufferedReader) reader : new BufferedReader(reader);

        List<Bone> boneList = new ArrayList<Bone>();
        // 按节点 id 直接落位（id 不连续时中间留 null，末尾压缩）
        Bone[] slots = new Bone[16];
        int version = 0;
        MeshSection mesh = new MeshSection();
        AnimSection anim = new AnimSection();

        String line;
        while ((line = in.readLine()) != null) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("//")) {
                continue;
            }
            if (t.startsWith("version")) {
                version = (int) num(t.substring(7));
                continue;
            }
            if (t.startsWith("nodes")) {
                readNodes(in, slots);
            } else if (t.startsWith("triangles")) {
                readTriangles(in, mesh);
            } else if (t.startsWith("time")) {
                anim.openFrame((int) num(t.substring(4)));
            } else if (t.startsWith("end")) {
                anim.closeFrame();
            } else if (anim.open) {
                readBonePose(t, anim);
            }
        }

        boneList.clear();
        int maxId = -1;
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] != null) {
                maxId = i;
            }
        }
        for (int i = 0; i <= maxId; i++) {
            boneList.add(slots[i] != null ? slots[i]
                    : new Bone(i, "bone_" + i, i == 0 ? -1 : 0));
        }
        Bone[] bones = boneList.toArray(new Bone[0]);
        return new SmdDocument(bones, mesh.build(), anim.toIR(name, bones));
    }

    // ---- nodes：id "名字" 父id ----

    private static void readNodes(BufferedReader in, Bone[] slots) throws IOException {
        String line;
        while ((line = in.readLine()) != null) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("end")) {
                return;
            }
            int q1 = t.indexOf('"');
            int q2 = t.indexOf('"', q1 + 1);
            if (q1 < 0 || q2 < 0) {
                continue;
            }
            int id = (int) num(t.substring(0, q1));
            String nm = t.substring(q1 + 1, q2);
            int parent = (int) num(t.substring(q2 + 1).trim());
            Bone b = new Bone(id, nm, parent);
            if (id >= slots.length) {
                Bone[] grown = new Bone[Math.max(id + 1, slots.length * 2)];
                System.arraycopy(slots, 0, grown, 0, slots.length);
                slots = grown;
            }
            slots[id] = b;
        }
    }

    // ---- triangles：材质名 + 每三角 3 顶点 ----

    private static void readTriangles(BufferedReader in, MeshSection mesh) throws IOException {
        String line;
        String material = null;
        float[][] p = new float[3][3];
        float[][] t = new float[3][2];
        float[][] n = new float[3][3];
        int[][] bi = new int[3][];
        float[][] bw = new float[3][];
        int v = 0;
        while ((line = in.readLine()) != null) {
            String s = line.trim();
            if (s.isEmpty() || s.startsWith("end")) {
                if (v == 4) {
                    mesh.builder.triangle(material, p, t, n, bi, bw);
                }
                return;
            }
            if (v == 4) {
                mesh.builder.triangle(material, p, t, n, bi, bw);
                v = 0;
            }
            if (v == 0) {
                if (looksNumeric(s)) {
                    // 规范要求每三角前都有材质行，但有些导出器只写一次材质、
                    // 后续三角直接连排顶点：同材质续三角，材质沿用上一块
                    readVertex(s, 0, p, t, n, bi, bw);
                    v = 2;
                } else {
                    material = s;
                    v++;
                }
                continue;
            }
            readVertex(s, v - 1, p, t, n, bi, bw);
            v++;  // v: 1→2→3→4（收满三顶点后下一行触发 flush）
        }
    }

    /** 顶点行：bone px py pz nx ny nz u v [links [bone weight]...]（V 坐标翻转让 UV 原点落左上）。 */
    private static void readVertex(String s, int slot,
                                   float[][] p, float[][] t, float[][] n,
                                   int[][] bi, float[][] bw) {
        String[] tok = splitWs(s);
        int o = 1; // tok[0] = 骨骼 id
        p[slot][0] = (float) num(tok[o++]);
        p[slot][1] = (float) num(tok[o++]);
        p[slot][2] = (float) num(tok[o++]);
        n[slot][0] = (float) num(tok[o++]);
        n[slot][1] = (float) num(tok[o++]);
        n[slot][2] = (float) num(tok[o++]);
        t[slot][0] = (float) num(tok[o++]);
        t[slot][1] = 1.0f - (float) num(tok[o++]);
        int links = tok.length > o ? (int) num(tok[o++]) : 0;
        int[] ids = new int[4];
        float[] ws = new float[4];
        for (int k = 0; k < links && o + 1 < tok.length && k < 4; k++) {
            ids[k] = (int) num(tok[o++]);
            ws[k] = (float) num(tok[o++]);
        }
        if (links == 0) {
            ids[0] = (int) num(tok[0]);
            ws[0] = 1.0f;
        }
        bi[slot] = ids;
        bw[slot] = ws;
    }

    /** 首个非空 token 是否纯数字（含小数/负号/科学计数）：区分材质行与顶点行。 */
    private static boolean looksNumeric(String s) {
        int i = 0;
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
        int start = i;
        while (i < s.length() && !Character.isWhitespace(s.charAt(i))) {
            i++;
        }
        if (i == start) {
            return false;
        }
        try {
            Double.parseDouble(s.substring(start, i));
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // ---- time 帧：bone px py pz rx ry rz（度） ----

    private static void readBonePose(String s, AnimSection anim) {
        String[] tok = splitWs(s);
        if (tok.length < 7) {
            return;
        }
        int id = (int) num(tok[0]);
        if (id < 0 || id > 4096) {
            return;
        }
        anim.ensureBone(id);
        for (int k = 0; k < 3; k++) {
            anim.pos[id][k] = num(tok[1 + k]);
            anim.rot[id][k] = num(tok[4 + k]);
        }
    }

    // ---- 小工具 ----

    private static double num(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 手写空白切分（连续空格/制表符都算一个分隔），避免 split 正则在热路径分配。 */
    private static String[] splitWs(String s) {
        List<String> out = new ArrayList<String>(10);
        int i = 0;
        while (i < s.length()) {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
            int start = i;
            while (i < s.length() && !Character.isWhitespace(s.charAt(i))) {
                i++;
            }
            if (i > start) {
                out.add(s.substring(start, i));
            }
        }
        return out.toArray(new String[0]);
    }

    // ---- 内部累积器 ----

    private static final class MeshSection {
        final SkinMesh.Builder builder = new SkinMesh.Builder();

        List<SkinMesh> build() {
            return builder.build();
        }
    }

    private static final class AnimSection {
        boolean open = false;
        double time = 0;
        double[][] pos = new double[0][];
        double[][] rot = new double[0][];
        double[] frameTime = new double[8];
        double[][][] framePos = new double[8][][];
        double[][][] frameRot = new double[8][][];
        int frameCount = 0;
        int maxBoneSeen = 0;

        void openFrame(int t) {
            closeFrame();
            open = true;
            time = t;
        }

        void closeFrame() {
            if (!open) {
                return;
            }
            open = false;
            if (frameCount == frameTime.length) {
                frameTime = grow(frameTime);
                framePos = grow2(framePos);
                frameRot = grow2(frameRot);
            }
            frameTime[frameCount] = time;
            framePos[frameCount] = pos;
            frameRot[frameCount] = rot;
            frameCount++;
            pos = new double[0][];
            rot = new double[0][];
        }

        private static double[] grow(double[] src) {
            double[] out = new double[src.length * 2];
            System.arraycopy(src, 0, out, 0, src.length);
            return out;
        }

        private static double[][][] grow2(double[][][] src) {
            double[][][] out = new double[src.length * 2][][];
            System.arraycopy(src, 0, out, 0, src.length);
            return out;
        }

        void ensureBone(int id) {
            if (id >= pos.length) {
                int n = Math.max(id + 1, 8);
                double[][] np = new double[n][];
                double[][] nr = new double[n][];
                System.arraycopy(pos, 0, np, 0, pos.length);
                System.arraycopy(rot, 0, nr, 0, rot.length);
                pos = np;
                rot = nr;
            }
            if (pos[id] == null) {
                pos[id] = new double[3];
                rot[id] = new double[3];
                maxBoneSeen = Math.max(maxBoneSeen, id);
            }
        }

        AnimationIR toIR(String name, Bone[] bones) {
            closeFrame();
            if (frameCount == 0) {
                return null;
            }
            double end = frameTime[frameCount - 1];
            AnimationIR ir = new AnimationIR(name, end, AnimationIR.LOOP_LOOP);
            // 帧里可能出现 nodes 表外的骨 id（真实导出常见），表外 id 没名字
            // 也绑不上骨架，直接不遍历——否则 bones[b] 越界
            int named = Math.min(bones.length, maxBoneSeen + 1);
            for (int b = 0; b < named; b++) {
                AnimationIR.BoneAnim ba = new AnimationIR.BoneAnim();
                ba.pos = new AnimationIR.Track();
                ba.rot = new AnimationIR.Track();
                for (int f = 0; f < frameCount; f++) {
                    double[][] fp = framePos[f];
                    double[][] fr = frameRot[f];
                    // 帧里缺席的骨骼（空洞位/未驱动）跳过，不造零位关键帧
                    if (b >= fp.length || fp[b] == null || fr[b] == null) {
                        continue;
                    }
                    ba.pos.keys.add(new AnimationIR.Keyframe(frameTime[f],
                            new double[]{fp[b][0], fp[b][1], fp[b][2]}));
                    ba.rot.keys.add(new AnimationIR.Keyframe(frameTime[f],
                            new double[]{fr[b][0], fr[b][1], fr[b][2]}));
                }
                ba.pos.sort();
                ba.rot.sort();
                ir.bones.put(bones[b].name, ba);
            }
            return ir;
        }
    }
}
