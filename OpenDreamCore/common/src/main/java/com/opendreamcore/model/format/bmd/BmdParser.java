package com.opendreamcore.model.format.bmd;

import com.opendreamcore.model.bake.SkinMesh;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * BMD（二进制模型数据）解析。
 *
 * <p>文件布局（小节顺序固定，全小端由 DataInputStream 按 Java 标准大端读——
 * 该格式实际是大端）：1 字节版本号（1 = 裸二进制流；2 = 后续载荷为 XZ 流，
 * 解压后同 v1 布局），随后：
 * <pre>
 *   short 节点数；每节点：short 自身 id、short 父 id（-1 = 根）、零终止名
 *   short 骨架数；每骨架：short 条目数；每条目：short 骨骼 id +
 *       6 float 静止位姿（位移 xyz + 欧拉角 xyz，度）
 *   short 材质数；每材质：零终止名
 *   short 三角数；每三角：byte 材质索引；3 个顶点：
 *       short 保留、float x/y/z、float 法线 xyz、float u/v、
 *       byte 权重链长度、链 ×（short 骨骼 id、float 权重）
 * </pre>
 *
 * <p>坐标约定：文件里 y/z（含法线）为 Z-up 系，入库统一取反换到 Y-up；
 * v 坐标入库翻转为左上原点（1-v）。权重链最多收 4 根，超出部分按顺序丢弃
 * （导出端义务保序）。
 */
public final class BmdParser {

    public static final class BmdDocument {
        public final Bone[] bones;
        /** 静止位姿，与 bones 对齐：{x,y,z, rotX,rotY,rotZ}（度）。 */
        public final double[][] restPose;
        /** 全部骨架组（帧）：get(g) = 每骨 6 值；第 0 组即静止位姿，多组 = 帧动画。 */
        public final java.util.List<double[][]> frameGroups;
        public final List<SkinMesh> meshes;

        BmdDocument(Bone[] bones, double[][] restPose, List<SkinMesh> meshes) {
            this(bones, restPose, meshes,
                    restPose == null ? java.util.Collections.<double[][]>emptyList()
                            : java.util.Collections.singletonList(restPose));
        }

        BmdDocument(Bone[] bones, double[][] restPose, List<SkinMesh> meshes,
                    java.util.List<double[][]> frameGroups) {
            this.bones = bones;
            this.restPose = restPose;
            this.meshes = meshes;
            this.frameGroups = frameGroups;
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

    private BmdParser() {
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(1 << 16);
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    /** 完整 XZ 容器（带 index/footer）。不是完整容器返回 null，由宽容路径接管。 */
    private static byte[] fullXz(byte[] all) {
        try {
            org.tukaani.xz.XZInputStream xz = new org.tukaani.xz.XZInputStream(
                    new ByteArrayInputStream(all));
            byte[] out = readAll(xz);
            xz.close();
            return out;
        } catch (IOException eofOrBad) {
            return null;
        }
    }

    /**
     * 宽容路径：逐块直解。块头 = [size 字节][(size+1)*4-1 字节余下]
     * + 过滤器列表（只认 LZMA2），随后紧贴 LZMA2 流（终止标记 0x00 收尾）。
     * 导出器砍掉了 index/footer，tukaani 容器流解不了；
     * LZMA2 块自身自描述，直解不受影响。
     */
    private static byte[] lenientXz(byte[] all) throws IOException {
        ByteArrayInputStream in = new ByteArrayInputStream(all);
        // 容器前置：magic(6)+flags(2)+crc32(4)。截尾流也有这 12 字节；
        // 万一是裸 LZMA2（无前置）就原样开跑
        if (all.length >= 12 && (all[0] & 0xFF) == 0xFD && all[1] == 0x37) {
            in.skip(12);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(1 << 16);
        while (in.available() > 0) {
            int sizeByte = in.read();
            if (sizeByte == 0) {
                break;   // index indicator：后面是元数据，不是块，到此为止
            }
            int headerLen = (sizeByte + 1) * 4;
            byte[] header = new byte[headerLen - 1];
            if (in.read(header) != header.length) {
                throw new IOException("块头被截断");
            }
            int flags = header[0] & 0xFF;
            int p = 1;
            if ((flags & 0x40) != 0) {
                p += 8;   // 压缩/未压缩长度字段（本包没有，防御性跳过）
            }
            int filterCount = (flags & 0x03) + 1;
            int dictSize = -1;
            for (int f = 0; f < filterCount && p < header.length; f++) {
                int id = header[p++] & 0xFF;
                int propsLen = header[p++] & 0xFF;
                byte[] props = new byte[propsLen];
                System.arraycopy(header, p, props, 0, Math.min(propsLen, header.length - p));
                p += propsLen;
                if (id == 0x21 && propsLen == 1) {
                    dictSize = lzf2DictSize(props[0] & 0xFF);
                }
            }
            if (dictSize < 0) {
                StringBuilder hd = new StringBuilder();
                for (int i = 0; i < header.length && i < 16; i++) {
                    hd.append(String.format("%02x ", header[i]));
                }
                throw new IOException("块头里找不到 LZMA2 过滤器 flags=" + flags + " 头=" + hd);
            }
            org.tukaani.xz.LZMA2InputStream lz = new org.tukaani.xz.LZMA2InputStream(in, dictSize);
            byte[] buf = new byte[8192];
            int n;
            try {
                while ((n = lz.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
            } catch (java.io.EOFException truncated) {
                // 导出器把流砍在半途（实测宝可梦包 15/15，尾部连内容一起缺，
                // 不止缺 index/footer）：能解多少算多少，上层按截断数据容错
            }
            lz.close();
        }
        if (out.size() == 0) {
            throw new IOException("XZ 载荷一比特都没解出来");
        }
        return out.toByteArray();
    }

    /** LZMA2 字典大小编码：code 0..39，(2|(code&1)) << (code/2+11)。 */
    private static int lzf2DictSize(int code) {
        if (code > 40) {
            throw new IllegalArgumentException("LZMA2 字典码非法: " + code);
        }
        return (2 | (code & 1)) << (code / 2 + 11);
    }

    public static BmdDocument parse(InputStream raw) throws IOException {
        DataInputStream in = new DataInputStream(raw);
        int version = in.read();
        if (version != 1 && version != 2) {
            throw new IOException("BMD 版本不认识: " + version);
        }
        if (version == 2) {
            // 载荷是 XZ 系压缩流，解压后是一份完整 v1 布局（含自己的版本字节）——
            // 递归按 v1 解（不能直接往下读：内层版本字节会被当成节点数）
            byte[] all = readAll(raw);
            byte[] payload = fullXz(all);
            if (payload == null) {
                // 部分导出器（实测宝可梦包）只写了 块头+LZMA2+终止标记，
                // 没有 index/footer——完整容器解不动，改按块直解
                payload = lenientXz(all);
            }
            if (payload.length > 0 && payload[0] == 1) {
                // 内层自带版本字节（自造往返样本）——递归按 v1 解
                return parse(new ByteArrayInputStream(payload));
            }
            // 实测导出器：内层不带版本字节，直接是 v1 正文（首两字节=节点数）
            return parseV1(new ByteArrayInputStream(payload));
        }
        return parseV1(raw);
    }

    private static BmdDocument parseV1(InputStream raw) throws IOException {
        DataInputStream in = new DataInputStream(raw);

        Bone[] bones = readNodes(in);
        java.util.List<double[][]> groups = readFrameGroups(in, bones.length);
        double[][] rest = groups.isEmpty() ? new double[bones.length][] : groups.get(0);
        List<SkinMesh> meshes = readTriangles(in);

        return new BmdDocument(bones, rest, meshes, groups);
    }

    // ---- nodes ----

    /** UTF-16BE 骨名，U+0000 结尾（实测 blender_implicit/Body/UpperBody 全大端）。 */
    private static String readUtf16BeNullTerm(DataInputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        while (true) {
            int c = in.readChar();
            if (c == 0) {
                break;
            }
            sb.append((char) c);
        }
        return sb.toString();
    }

    private static Bone[] readNodes(DataInputStream in) throws IOException {
        int count = in.readShort() & 0xFFFF;
        Bone[] out = new Bone[count];
        for (int i = 0; i < count; i++) {
            try {
                int id = in.readShort();
                int parent = in.readShort();
                String name = readUtf16BeNullTerm(in);
                if (id >= 0 && id < count) {
                    out[id] = new Bone(id, name, parent);
                }
            } catch (EOFException truncated) {
                break;   // 截断文件（实测宝可梦包）：有几根算几根
            }
        }
        // id 空洞补占位，保证下标=骨骼 id 的约定成立
        for (int i = 0; i < count; i++) {
            if (out[i] == null) {
                out[i] = new Bone(i, "bone_" + i, i == 0 ? -1 : 0);
            }
        }
        return out;
    }

    // ---- skeletons（组 0 = 静止位姿，多组 = 帧动画）----

    private static java.util.List<double[][]> readFrameGroups(DataInputStream in, int boneCount) throws IOException {
        java.util.List<double[][]> groups = new java.util.ArrayList<double[][]>();
        int groupCount = in.readShort() & 0xFFFF;
        for (int g = 0; g < groupCount; g++) {
            double[][] frame = new double[boneCount][];
            int entries;
            try {
                entries = in.readShort() & 0xFFFF;
            } catch (EOFException truncated) {
                break;
            }
            for (int e = 0; e < entries; e++) {
                int id;
                double x, y, z, rx, ry, rz;
                try {
                    id = in.readShort();
                    x = in.readFloat();
                    y = in.readFloat();
                    z = in.readFloat();
                    rx = in.readFloat();
                    ry = in.readFloat();
                    rz = in.readFloat();
                } catch (EOFException truncated) {
                    break;   // 截断文件：有几根算几根
                }
                if (id >= 0 && id < boneCount) {
                    // 文件 y/z 为 Z-up：入库取反换 Y-up（角度同理）
                    frame[id] = new double[]{x, -y, -z, rx, -ry, -rz};
                }
            }
            groups.add(frame);
        }
        return groups;
    }

    // ---- triangles ----

    private static List<SkinMesh> readTriangles(DataInputStream in) throws IOException {
        SkinMesh.Builder builder = new SkinMesh.Builder();
        int materialCount;
        try {
            materialCount = in.readShort() & 0xFFFF;
        } catch (EOFException noSection) {
            // 动画 bmd 到骨架段就没了，没有材质/三角区
            return builder.build();
        }
        String[] materials = new String[materialCount];
        for (int i = 0; i < materialCount; i++) {
            try {
                materials[i] = readUtf16BeNullTerm(in);   // 材质名与骨名同为 UTF-16BE
            } catch (EOFException truncated) {
                materialCount = i;
                break;
            }
        }
        int triCount;
        try {
            triCount = in.readShort() & 0xFFFF;
        } catch (EOFException noTris) {
            return builder.build();
        }

        for (int tri = 0; tri < triCount; tri++) {
            // 导出端把 XZ 流砍在半途是这类文件的常态（实测宝可梦包 15/15 全缺尾部，
            // 差 24 个三角）：读到一半就断不算坏文件，已解出的几何照常入库
            try {
                readTriangle(in, materials, materialCount, builder);
            } catch (EOFException truncated) {
                System.out.println("[ODC-bmd] 载荷在三角区 " + tri + "/" + triCount
                        + " 处截断，已解 " + tri + " 条照常使用");
                break;
            }
        }
        return builder.build();
    }

    private static void readTriangle(DataInputStream in, String[] materials, int materialCount,
                                     SkinMesh.Builder builder) throws IOException {
            int matIdx = in.readByte() & 0xFF;
            String mat = matIdx < materialCount ? materials[matIdx] : "missing_" + matIdx;
            float[][] p = new float[3][3];
            float[][] t = new float[3][2];
            float[][] n = new float[3][3];
            int[][] bi = new int[3][4];
            float[][] bw = new float[3][4];
            for (int v = 0; v < 3; v++) {
                in.readShort(); // 保留字段
                p[v][0] = in.readFloat();
                p[v][1] = -in.readFloat();
                p[v][2] = -in.readFloat();
                n[v][0] = in.readFloat();
                n[v][1] = -in.readFloat();
                n[v][2] = -in.readFloat();
                t[v][0] = in.readFloat();
                t[v][1] = 1.0f - in.readFloat();
                int links = in.readByte() & 0xFF;
                for (int k = 0; k < links && k < 4; k++) {
                    bi[v][k] = in.readShort();
                    bw[v][k] = in.readFloat();
                }
                // 超 4 根的链尾读掉不收（保持流对齐）
                for (int k = 4; k < links; k++) {
                    in.readShort();
                    in.readFloat();
                }
            }
            builder.triangle(mat, p, t, n, bi, bw);
    }

    // ---- 小工具 ----

    private static String readNullTerm(DataInputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream(24);
        for (;;) {
            int b = in.read();
            if (b < 0) {
                throw new EOFException("名字段截断");
            }
            if (b == 0) {
                break;
            }
            buf.write(b);
        }
        return new String(buf.toByteArray(), StandardCharsets.UTF_8);
    }
}
