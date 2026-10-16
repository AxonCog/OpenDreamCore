package com.opendreamcore.model.format.ysm;

import com.opendreamcore.model.format.CodecUtil;
import com.opendreamcore.model.format.DecodeResult;
import com.opendreamcore.model.format.geo.GeoJsonCodec;
import com.opendreamcore.model.format.IModelFormatCodec;
import com.opendreamcore.model.format.anim.BedrockAnimCodec;
import com.opendreamcore.model.ir.AnimationIR;
import com.opendreamcore.model.ir.ModelIR;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * YSM 模型解析。两层结构：
 * 外层是 YSGP 容器（见 YsgpUnpacker，v1/v2 带加密），内层才是模型本体；
 * v3 起容器消失，文件直接是二进制流。
 *
 * 二进制本体按三块走：贴图（PNG 原样内嵌，回溯长度取名字）、
 * 骨骼（parent/立方体/名字/枢轴/旋转顺序读）、动画（与旧版同一套体格式，
 * 名字 + 时长 + 三通道关键帧，通道里混数值和 molang 表达式）。
 * 内层的段与段之间没有可靠的总长表，靠锚点扫描定位：
 * PNG 魔数、长度前缀的 hex 串 + 合理骨骼数、可读动画名开头。
 * 扫描失败就抛错，上层跳过该文件。
 */
public final class YsmCodec implements IModelFormatCodec {

    private static final int V3_MIN_FORMAT = 20;

    @Override
    public String id() {
        return "ysm";
    }

    @Override
    public String[] extensions() {
        return new String[]{".ysm"};
    }

    @Override
    public boolean sniff(String text) {
        return false;
    }

    @Override
    public DecodeResult decode(String text) {
        throw new IllegalArgumentException("YSM 是二进制格式，请走二进制入口");
    }

    @Override
    public boolean sniffBinary(byte[] data) {
        // 容器：YSGP 魔数；v3 裸流：首 int32 格式号落在已知区间，
        // 叠加长度下限避免把小文件误认
        if (data == null || data.length < 1024) {
            return false;
        }
        if (data[0] == 'Y' && data[1] == 'S' && data[2] == 'G' && data[3] == 'P') {
            return true;
        }
        int ver = (data[0] & 0xFF) | (data[1] & 0xFF) << 8 | (data[2] & 0xFF) << 16 | (data[3] & 0xFF) << 24;
        return ver >= 20 && ver <= 64;
    }

    @Override
    public DecodeResult decode(byte[] data) {
        int ver = YsgpUnpacker.version(data);
        if (ver > 0) {
            return decodeContainer(data);
        }
        return decodeRaw(data);
    }

    /** v1/v2：容器里是一组文件，几何 json 与动画 json 分开，贴图是散文件。 */
    private DecodeResult decodeContainer(byte[] data) {
        try {
            Map<String, byte[]> files = YsgpUnpacker.unzip(YsgpUnpacker.unpack(data));
            ModelIR model = null;
            List<AnimationIR> anims = new ArrayList<AnimationIR>();
            List<DecodeResult.EmbeddedTexture> textures = new ArrayList<DecodeResult.EmbeddedTexture>();
            for (Map.Entry<String, byte[]> e : files.entrySet()) {
                String name = e.getKey().toLowerCase(Locale.ROOT);
                byte[] content = e.getValue();
                if (name.endsWith(".json")) {
                    String text = new String(content, StandardCharsets.UTF_8);
                    if (text.contains("\"animations\"")) {
                        anims.addAll(BedrockAnimCodec.parse(text));
                    } else if (text.contains("\"geometry\"") || text.contains("\"bones\"")) {
                        if (model == null) {
                            model = new GeoJsonCodec().decode(text).model;
                        }
                    }
                } else if (name.endsWith(".png")) {
                    textures.add(new DecodeResult.EmbeddedTexture(e.getKey(), content));
                }
            }
            if (model == null) {
                throw new IllegalArgumentException("YSM 容器里没找到可用的模型文件");
            }
            DecodeResult out = new DecodeResult(model);
            out.animations.addAll(anims);
            out.embeddedTextures.addAll(textures);
            return out;
        } catch (IOException ex) {
            throw new IllegalArgumentException("YSM 容器解包失败: " + ex, ex);
        }
    }

    /** v3：无容器，直接解析二进制本体。 */
    private DecodeResult decodeRaw(byte[] data) {
        Bin bin = new Bin(data);
        int fmt = bin.i32();
        if (fmt < V3_MIN_FORMAT || fmt > 99) {
            throw new IllegalArgumentException("YSM 格式号 " + fmt + " 不在支持范围");
        }

        List<DecodeResult.EmbeddedTexture> textures = new ArrayList<DecodeResult.EmbeddedTexture>();
        collectTextures(data, textures);

        ModelAnchor model = locateModel(data);
        if (model == null) {
            throw new IllegalArgumentException("YSM 本体里没定位到骨骼段");
        }
        ModelIR root = readModel(model.bin, (int) model.count, fmt);
        DecodeResult out = new DecodeResult(root);
        out.embeddedTextures.addAll(textures);
        YsmAnimReader.readAnimations(data, fmt, out);
        return out;
    }

    // ---------- 贴图：PNG 魔数锚点，回溯长度取名字 ----------

    private void collectTextures(byte[] data, List<DecodeResult.EmbeddedTexture> textures) {
        for (int p = YsmScan.indexOf(data, PNG_MAGIC, 0); p >= 0; p = YsmScan.indexOf(data, PNG_MAGIC, p + 1)) {
            int end = YsmScan.indexOf(data, IEND_MAGIC, p);
            if (end < 0) {
                break;
            }
            end += IEND_MAGIC.length + 4;
            int size = end - p;
            int q = p;
            boolean ok = false;
            for (int n = 1; n <= 3 && q - n >= 0; n++) {
                int v = 0;
                int shift = 0;
                boolean valid = true;
                for (int i = 0; i < n; i++) {
                    int b = data[q - n + i] & 0xff;
                    if (i == n - 1) {
                        if ((b & 0x80) != 0) {
                            valid = false;
                            break;
                        }
                        v |= b << shift;
                    } else {
                        v |= (b & 0x7f) << shift;
                        shift += 7;
                    }
                }
                if (valid && v == size) {
                    q -= n;
                    ok = true;
                    break;
                }
            }
            if (!ok) {
                continue;
            }
            String hash = null;
            if (q >= 65 && data[q - 65] == 0x40 && YsmScan.isHex(data, q - 64, 64)) {
                hash = new String(data, q - 64, 64, StandardCharsets.US_ASCII);
                q -= 65;
            }
            String name = null;
            if (q >= 1) {
                int ln = data[q - 1] & 0xff;
                if (ln > 0 && ln < 64 && q - 1 - ln >= 0 && YsmScan.isPrintable(data, q - ln, ln)) {
                    name = new String(data, q - ln, ln, StandardCharsets.UTF_8);
                }
            }
            byte[] png = new byte[size];
            System.arraycopy(data, p, png, 0, size);
            textures.add(new DecodeResult.EmbeddedTexture(
                    name != null ? name : (hash != null ? hash.substring(0, 12) : "tex_" + p), png));
        }
    }

    // ---------- 骨骼段：hex 串锚点定位，逐骨验证 ----------

    /** 骨骼段锚点：数据读取器 + 骨数（指针已停在骨头起点）。 */
    private static final class ModelAnchor {
        final Bin bin;
        final long count;

        ModelAnchor(Bin bin, long count) {
            this.bin = bin;
            this.count = count;
        }
    }

    private ModelAnchor locateModel(byte[] data) {
        // 锚点：[0x40][64 位小写 hex（sha256 段标识）][骨数][首骨可解]。
        // 一个文件可能有多个子实体模型（本体/载具/投掷物），取骨数最多的当主模型
        ModelAnchor best = null;
        for (int p = 0; p < data.length - 70; p++) {
            if (data[p] != 0x40 || !YsmScan.isHex(data, p + 1, 64)) {
                continue;
            }
            int q = p + 65;
            long count = YsmScan.peekVarint(data, q);
            if (count < 1 || count > 1024) {
                continue;
            }
            if (best != null && count <= best.count) {
                continue;
            }
            Bin bin = new Bin(data);
            bin.p = (int) q + YsmScan.varintSize(count);
            try {
                long probe = Math.min(count, 2);
                for (long i = 0; i < probe; i++) {
                    readBone(bin);
                }
                if (bin.p > data.length) {
                    continue;
                }
                bin.p = (int) q + YsmScan.varintSize(count); // 探针会前移指针，读正式数据前拨回骨头起点
                best = new ModelAnchor(bin, count);
            } catch (RuntimeException ignore) {
                // 该锚点不是骨骼段，继续扫
            }
        }
        return best;
    }

    /** 读一整条骨骼（头 + 尾部 5 未知 varint + 枢轴 + 旋转），探针与正式解析共用。 */
    private BoneRec readBone(Bin bin) {
        String parent = bin.str();
        long nc = bin.varint();
        if (nc > 4096) {
            throw new IllegalStateException("立方体数异常 " + nc);
        }
        List<ModelIR.TriMesh> meshes = new ArrayList<ModelIR.TriMesh>();
        for (long i = 0; i < nc; i++) {
            meshes.add(readCube(bin));
        }
        String name = bin.str();
        if (name.isEmpty() || !YsmScan.isPrintable(name)) {
            throw new IllegalStateException("骨名不可读");
        }
        for (int u = 0; u < 5; u++) {
            bin.varint();
        }
        double[] pivot = new double[]{bin.f32(), bin.f32(), bin.f32()};
        double[] rot = new double[]{bin.f32(), bin.f32(), bin.f32()};
        BoneRec rec = new BoneRec(parent, name, pivot, rot);
        rec.meshes.addAll(meshes);
        return rec;
    }

    /** 骨骼的完整记录（读哪条算哪条，树在 readModel 里拼）。 */
    private static final class BoneRec {
        final String parent;
        final String name;
        final double[] pivot;
        final double[] rot;
        final List<ModelIR.TriMesh> meshes = new ArrayList<ModelIR.TriMesh>();

        BoneRec(String parent, String name, double[] pivot, double[] rot) {
            this.parent = parent;
            this.name = name;
            this.pivot = pivot;
            this.rot = rot;
        }
    }

    private ModelIR readModel(Bin bin, int count, int fmt) {
        List<ModelIR.BoneIR> flat = new ArrayList<ModelIR.BoneIR>();
        List<String> parents = new ArrayList<String>();
        for (int i = 0; i < count; i++) {
            BoneRec rec = readBone(bin);
            ModelIR.BoneIR bone = new ModelIR.BoneIR(rec.name);
            bone.pivot[0] = rec.pivot[0];
            bone.pivot[1] = rec.pivot[1];
            bone.pivot[2] = rec.pivot[2];
            bone.meshes.addAll(rec.meshes);
            if (Math.abs(rec.rot[0]) + Math.abs(rec.rot[1]) + Math.abs(rec.rot[2]) > 1e-9) {
                // 文件里存弧度，统一转成度（与 geo/bbmodel 同单位）
                bone.bindRotation = new double[]{
                        rec.rot[0] * 57.29577951308232,
                        rec.rot[1] * 57.29577951308232,
                        rec.rot[2] * 57.29577951308232};
            }
            flat.add(bone);
            parents.add(rec.parent);
        }
        // 按 parent 名挂树；parent 为空的都是根——多个根时包一层合成根
        List<ModelIR.BoneIR> roots = new ArrayList<ModelIR.BoneIR>();
        for (int i = 0; i < flat.size(); i++) {
            String parent = parents.get(i);
            ModelIR.BoneIR bone = flat.get(i);
            if (parent.isEmpty()) {
                roots.add(bone);
                continue;
            }
            ModelIR.BoneIR p = findByName(flat, parent);
            if (p != null) {
                p.children.add(bone);
            } else {
                roots.add(bone);
            }
        }
        if (roots.isEmpty()) {
            throw new IllegalStateException("骨骼树为空");
        }
        if (roots.size() == 1) {
            return new ModelIR(roots.get(0));
        }
        ModelIR.BoneIR top = new ModelIR.BoneIR("root");
        top.children.addAll(roots);
        return new ModelIR(top);
    }

    /** 一块立方体 = 六个四边形面（新些的版本面数写在前面）。 */
    private ModelIR.TriMesh readCube(Bin bin) {
        long nf = bin.varint();
        if (nf <= 0 || nf > 64) {
            throw new IllegalStateException("面数异常 " + nf);
        }
        float[] pos = new float[(int) (nf * 4 * 3)];
        float[] uv = new float[(int) (nf * 4 * 2)];
        float[] nrm = new float[(int) (nf * 4 * 3)];
        int vi = 0;
        int ui = 0;
        int ni = 0;
        for (long f = 0; f < nf; f++) {
            float nx = bin.f32();
            float ny = bin.f32();
            float nz = bin.f32();
            for (int v = 0; v < 4; v++) {
                pos[vi++] = bin.f32();
                pos[vi++] = bin.f32();
                pos[vi++] = bin.f32();
                uv[ui++] = bin.f32();
                uv[ui++] = bin.f32();
                nrm[ni++] = nx;
                nrm[ni++] = ny;
                nrm[ni++] = nz;
            }
        }
        for (int u = 0; u < 3; u++) {
            bin.varint();
        }
        int quads = (int) nf;
        int[] idx = new int[quads * 6];
        int ii = 0;
        for (int q = 0; q < quads; q++) {
            int b = q * 4;
            idx[ii++] = b;
            idx[ii++] = b + 1;
            idx[ii++] = b + 2;
            idx[ii++] = b;
            idx[ii++] = b + 2;
            idx[ii++] = b + 3;
        }
        ModelIR.TriMesh mesh = new ModelIR.TriMesh();
        mesh.positions = pos;
        mesh.uvs = uv;
        mesh.normals = nrm;
        mesh.indices = idx;
        return mesh;
    }

    // ---------- 动画：名字锚点 + 平铺流 ----------

    private static final byte[] PNG_MAGIC = new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
    private static final byte[] IEND_MAGIC = new byte[]{'I', 'E', 'N', 'D'};

    private ModelIR.BoneIR findByName(List<ModelIR.BoneIR> flat, String name) {
        for (ModelIR.BoneIR b : flat) {
            if (b.name.equals(name)) {
                return b;
            }
        }
        return null;
    }

    static final class Bin {
        final byte[] d;
        int p;

        Bin(byte[] d) {
            this.d = d;
        }

        int u8() {
            if (p >= d.length) {
                throw new IllegalStateException("数据提前结束 @" + p);
            }
            return d[p++] & 0xff;
        }

        long varint() {
            long v = 0;
            int shift = 0;
            while (true) {
                int b = u8();
                v |= (long) (b & 0x7f) << shift;
                if ((b & 0x80) == 0) {
                    return v;
                }
                shift += 7;
                if (shift > 35) {
                    throw new IllegalStateException("varint 过长 @" + p);
                }
            }
        }

        int i32() {
            if (p + 4 > d.length) {
                throw new IllegalStateException("数据提前结束 @" + p);
            }
            int v = (d[p] & 0xff) | (d[p + 1] & 0xff) << 8 | (d[p + 2] & 0xff) << 16 | (d[p + 3] & 0xff) << 24;
            p += 4;
            return v;
        }

        float f32() {
            if (p + 4 > d.length) {
                throw new IllegalStateException("数据提前结束 @" + p);
            }
            int v = i32();
            return Float.intBitsToFloat(v);
        }

        String str() {
            int len = (int) varint();
            if (len < 0 || len > 1 << 20 || p + len > d.length) {
                throw new IllegalStateException("字符串长度异常 " + len + " @" + p);
            }
            String s = new String(d, p, len, StandardCharsets.UTF_8);
            p += len;
            return s;
        }
    }
}
