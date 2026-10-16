package com.opendreamcore.model.bake;

import com.opendreamcore.model.format.DecodeResult;
import com.opendreamcore.model.format.FormatCodecs;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模型资产库：扫描 assets/model 目录（.bbmodel/.geo.json/.obj）→ 解码 → 烘焙。
 *
 * <p>贴图注册是版本相关操作（DynamicTexture 构造跨代不同），由代际 glue 实现
 * {@link TextureSink} 注入；本类只持有 PNG 字节。
 */
public final class ModelAssetStore {

    /** 贴图注册回调（代际 glue：把 PNG 字节注册成可采样贴图）。 */
    public interface TextureSink {
        void register(String label, byte[] png);
    }

    private static final ModelAssetStore INSTANCE = new ModelAssetStore();

    /**
     * 找不到的模型 id 负缓存：实体渲染每帧都会按名查找，找不到时若无缓存，
     * 每帧都会对所有根目录做磁盘探测（每实体×每帧×每规则）——既是刷屏也是卡顿源。
     * setRoots/reload 时清空，允许用户放好文件后重载生效。
     */
    private final java.util.Set<String> missingIds = new java.util.HashSet<String>();

    public static ModelAssetStore get() {
        return INSTANCE;
    }

    /** 模型 id → 烘焙产物（一个模型可能按材质拆多条）。 */
    private final Map<String, List<BakedModel>> models = new LinkedHashMap<String, List<BakedModel>>();
    /** 模型 id → 骨骼化产物（与 models 同键；无骨骼/拍平无差异时也生成）。 */
    private final Map<String, BakedRig> rigs = new LinkedHashMap<String, BakedRig>();
    /** 模型 id → 内嵌动画（bbmodel 内嵌；bedrock json 动画另入）。 */
    private final Map<String, List<com.opendreamcore.model.ir.AnimationIR>> anims =
            new LinkedHashMap<String, List<com.opendreamcore.model.ir.AnimationIR>>();
    /** 贴图标签 → PNG 字节（内嵌解码产物或 assets/texture 文件）。 */
    final Map<String, byte[]> textures = new HashMap<String, byte[]>();
    private TextureSink sink;
    private Path root;
    private java.util.List<Path> roots = new java.util.ArrayList<Path>();
    private int loadedModels;
    private int loadedTextures;
    private volatile long revision;

    /** 资源重载版本；渲染器据此释放旧 GPU 贴图，下一次使用时重新注册。 */
    public long revision() {
        return revision;
    }

    private ModelAssetStore() {
    }

    public void bind(TextureSink textureSink) {
        this.sink = textureSink;
        // 换绑定后需重注册全部贴图
        if (textureSink != null) {
            for (Map.Entry<String, byte[]> e : textures.entrySet()) {
                textureSink.register(e.getKey(), e.getValue());
            }
        }
    }

    /** 直接按名字解析（不扫描）。设置可解析的根目录。 */
    public synchronized void setRoots(java.util.List<Path> newRoots) {
        roots = new java.util.ArrayList<Path>();
        if (newRoots != null) {
            for (Path p : newRoots) {
                if (p != null) {
                    roots.add(p);
                }
            }
        }
        root = roots.isEmpty() ? null : roots.get(0);
        models.clear();
        textures.clear();
        rigs.clear();
        anims.clear();
        formats.clear();
        animPool.clear();
        animPoolLoaded = false;
        missingIds.clear();
        loadedModels = 0;
        loadedTextures = 0;
        revision++;
    }

    public synchronized void load(Path assetsRoot) {
        setRoots(java.util.Collections.singletonList(assetsRoot));
    }

    /** 当前解析根（诊断用）。 */
    public synchronized java.util.List<Path> roots() {
        return new java.util.ArrayList<Path>(roots);
    }

    /** 清缓存（根不变）；下次用到同名模型会重新读盘。 */
    public synchronized void reload() {
        models.clear();
        textures.clear();
        rigs.clear();
        anims.clear();
        formats.clear();
        animPool.clear();
        animPoolLoaded = false;
        missingIds.clear();
        loadedModels = 0;
        loadedTextures = 0;
        revision++;
    }

    /**
     * 按名字直接取已烘焙模型：命中缓存就返回，没命中就按名字探路径、读盘、解码、烘焙。
     *
     * <p>写法随便：{@code 刺锤} / {@code 刺锤.bbmodel} / {@code models/刺锤.bbmodel} /
     * {@code opendreamcore:刺锤.bbmodel} / 反斜杠路径都认；扩展名缺省按 bbmodel→geo.json→obj 依次试。
     * 找不到返回 null（调用方跳过该实体，缺席即安全）。
     */
    public synchronized java.util.List<BakedModel> byModelId(String idRaw) {
        if (idRaw == null || idRaw.trim().isEmpty()) {
            return null;
        }
        String id = idRaw.trim();
        java.util.List<BakedModel> hit = models.get(id);
        if (hit != null) {
            return hit;
        }
        if (missingIds.contains(id)) {
            return null;
        }
        byte[] raw = resolveRaw(id, idRaw);
        if (raw == null) {
            missingIds.add(id);
            ModelLog.warn("模型没找到（按名字直接找，已缓存不再重复找）: " + idRaw + " 根=" + roots);
            return null;
        }
        return decodeAndBake(id, idRaw, raw);
    }

    /** 三级找字节：本地路径 → zip 引用 → 资源云兜底。 */
    private byte[] resolveRaw(String id, String idRaw) {
        byte[] raw = resolveBytes(id, true);
        if (raw == null) {
            raw = resolveZipRef(id);
        }
        if (raw == null && cloudResolver != null) {
            // 资源云兜底：服务端同步下来的非图文件（模型/动画）不落盘，
            // client 侧启动时把读取器挂进来（common 不能反向依赖 client）
            try {
                raw = cloudResolver.apply(idRaw);
            } catch (Exception ignored) {
            }
        }
        return raw;
    }

    /** 解码 → 内嵌资源登记 → 烘焙入库。 */
    private java.util.List<BakedModel> decodeAndBake(String id, String idRaw, byte[] raw) {
        try {
            String name = id.replace('\\', '/');
            int slash = name.lastIndexOf('/');
            if (slash >= 0) {
                name = name.substring(slash + 1);
            }
            DecodeResult r;
            if (raw.length > 4 && raw[0] == 'P' && raw[1] == 'K') {
                r = ZipBundleReader.load(this, name, raw, id);
            } else {
                // 统一入口：文本格式（bbmodel/geo/obj/smd）按扩展名走文本路，
                // 二进制（bmd/ysm）直接吃字节
                r = FormatCodecs.decodeBytes(name, raw);
            }
            formats.put(id, r.model.meta.sourceFormat == null ? "unknown" : r.model.meta.sourceFormat);
            namespaceEmbeddedMaterials(r, id);
            loadEmbedded(r, id);
            bakeAndPut(id, r);
            java.util.List<BakedModel> baked = models.get(id);
            if (baked != null) {
                models.put(idRaw, baked);
                loadedModels++;
                ModelLog.info("模型按名解析成功: " + idRaw + "（材质 " + baked.size() + " 个）");
            }
            return baked;
        } catch (Exception e) {
            missingIds.add(id);
            ModelLog.warn("模型解码失败 " + idRaw + ": " + e);
            return null;
        }
    }

    /** 已解析过的模型 id（命令列表用；没被规则引用过的不会出现——不扫描就是这样）。 */
    public synchronized java.util.Set<String> modelIds() {
        return models.keySet();
    }

    /** 模型 id → 来源格式（meta.sourceFormat；列表列展示用）。 */
    public synchronized String formatOf(String id) {
        return formats.get(id);
    }

    private final Map<String, String> formats = new LinkedHashMap<String, String>();

    /** 资源云兜底读取器（client 启动时注入；common 不反向依赖 client）。 */
    public static volatile java.util.function.Function<String, byte[]> cloudResolver;

    /**
     * zip 混合包：引用可以是 {@code 包.zip}（取包内首个模型条目）
     * 或 {@code 包.zip/内部路径/模型.smd}。返回包字节并把内部路径拼到 id 后。
     */
    private byte[] resolveZipRef(String ref) {
        String norm = AssetRefs.normalizeRef(ref);
        int cut = norm.toLowerCase(java.util.Locale.ROOT).indexOf(".zip/");
        String zipRef;
        String inner;
        if (cut >= 0) {
            zipRef = norm.substring(0, cut + 4);
            inner = norm.substring(cut + 5).replace('\\', '/');
        } else if (norm.toLowerCase(java.util.Locale.ROOT).endsWith(".zip")) {
            zipRef = norm;
            inner = null;
        } else {
            return null;
        }
        byte[] zip = resolveBytes(zipRef, false);
        if (zip == null || zip.length < 4 || zip[0] != 'P' || zip[1] != 'K') {
            return null;
        }
        pendingZipInner.set(inner);
        return zip;
    }

    private static final ThreadLocal<String> pendingZipInner = ZipBundleReader.PENDING_INNER;


    public synchronized byte[] texture(String label) {
        if (label == null) {
            return null;
        }
        String key = label.trim();
        byte[] hit = textures.get(key);
        if (hit != null) {
            return hit;
        }
        if (key.startsWith("embedded:")) {
            return null;
        }
        byte[] png = resolveBytes(key, false);
        if (png != null) {
            textures.put(key, png);
            loadedTextures++;
            if (sink != null) {
                sink.register(key, png);
            }
        }
        return png;
    }

    // ---------------------------------------------------------------- 直接解析（按名字探路径，不做目录扫描）

    /** 探路径取字节：model=true 时缺省补扩展名。 */
    private byte[] resolveBytes(String ref, boolean model) {
        String norm = AssetRefs.normalizeRef(ref);
        if (norm.isEmpty()) {
            return null;
        }
        java.util.List<String> rels = new java.util.ArrayList<String>();
        rels.add(norm);
        if (model && !AssetRefs.hasKnownExt(norm)) {
            for (String ext : AssetRefs.MODEL_EXT) {
                rels.add(norm + ext);
            }
        } else if (!model && !norm.toLowerCase(java.util.Locale.ROOT).endsWith(".png")) {
            rels.add(norm + ".png");
        }
        // 带命名空间原样也试一次（opendreamcore:刺锤.bbmodel → opendreamcore/刺锤.bbmodel）
        int colon = norm.indexOf(':');
        if (colon >= 0) {
            rels.add(norm.substring(0, colon) + "/" + norm.substring(colon + 1));
        }
        for (Path base : roots) {
            if (base == null || !Files.isDirectory(base)) {
                continue;
            }
            for (String sub : AssetRefs.SUB_DIRS) {
                for (String rel : rels) {
                    Path f = base.resolve(sub + rel);
                    if (Files.isRegularFile(f)) {
                        try {
                            return Files.readAllBytes(f);
                        } catch (Exception e) {
                            ModelLog.warn("读模型文件失败 " + f + ": " + e);
                        }
                    }
                }
            }
        }
        return null;
    }

    /** 内嵌贴图解码：每个模型使用独立 namespace，登记 PNG 字节及已绑定的纹理 sink。 */
    private void loadEmbedded(DecodeResult r, String modelId) {
        for (int i = 0; i < r.embeddedTextures.size(); i++) {
            DecodeResult.EmbeddedTexture t = r.embeddedTextures.get(i);
            String key = embeddedKey(modelId, t.name);
            textures.put(key, t.data);
            loadedTextures++;
            if (sink != null) {
                sink.register(key, t.data);
            }
        }
    }

    /** 内嵌贴图引用不能以裸文件名跨模型共享；只迁移本 DecodeResult 的材质引用。 */
    private static void namespaceEmbeddedMaterials(DecodeResult r, String modelId) {
        for (int i = 0; i < r.model.materials.size(); i++) {
            com.opendreamcore.model.ir.ModelIR.Material material = r.model.materials.get(i);
            String texture = material.texture;
            if (texture != null && texture.startsWith("embedded:")) {
                material.texture = embeddedKey(modelId, texture.substring("embedded:".length()));
            }
        }
    }

    private static String embeddedKey(String modelId, String name) {
        // 长度分隔避免带 namespace 的模型 id 与贴图名中的 ':' 形成同一个键。
        return "embedded:" + modelId.length() + ":" + modelId + ":" + name;
    }

    private void bakeAndPut(String id, DecodeResult r) {
        // 逐材质归一 UV：每条 BakedModel 用其贴图的像素尺寸
        List<BakedModel> baked = QuadBaker.bake(r.model);
        for (BakedModel b : baked) {
            int[] dim = pngSize(texture(b.texture));
            if (dim == null) {
                dim = new int[]{(int) r.model.meta.textureWidth, (int) r.model.meta.textureHeight};
            }
            if (dim[0] > 0 && dim[1] > 0) {
                normalize(b, dim[0], dim[1]);
            }
        }
        models.put(id, baked);
        // 骨骼化产物：每个 part 使用自身贴图像素尺寸，不能由首张贴图污染
        BakedRig rig = RigBaker.rig(r.model);
        for (BakedRig.Bone bone : rig.bones) {
            for (BakedRig.Part p : bone.parts) {
                int[] dim = pngSize(texture(p.texture));
                if (dim == null) {
                    dim = new int[]{(int) r.model.meta.textureWidth, (int) r.model.meta.textureHeight};
                }
                if (dim[0] > 0 && dim[1] > 0) {
                    scaleUv(p.quads, dim[0], dim[1]);
                    scaleUv(p.tris, dim[0], dim[1]);
                }
            }
        }
        rigs.put(id, rig);
        anims.put(id, new java.util.ArrayList<com.opendreamcore.model.ir.AnimationIR>(r.animations));
    }

    /** 全局动画池（名字 → IR）：bbmodel 内嵌 + animations/*.json 独立文件。 */
    final Map<String, com.opendreamcore.model.ir.AnimationIR> animPool =
            new LinkedHashMap<String, com.opendreamcore.model.ir.AnimationIR>();
    private boolean animPoolLoaded;

    /** 按动画名取 IR（懒建池；找不到回 null）。 */
    public synchronized com.opendreamcore.model.ir.AnimationIR animByName(String name) {
        ensureAnimPool();
        return name == null ? null : animPool.get(name);
    }

    public synchronized boolean animPoolEmpty() {
        ensureAnimPool();
        return animPool.isEmpty();
    }

    /** Animation 库并入：同名以 Animation 库为准（后并入者覆盖）。 */
    public synchronized void importAnims(Map<String, com.opendreamcore.model.ir.AnimationIR> extra) {
        if (extra == null || extra.isEmpty()) {
            return;
        }
        ensureAnimPool();
        animPool.putAll(extra);
    }

    void ensureAnimPool() {
        if (animPoolLoaded) {
            return;
        }
        animPoolLoaded = true;
        for (java.util.List<com.opendreamcore.model.ir.AnimationIR> list : anims.values()) {
            for (com.opendreamcore.model.ir.AnimationIR a : list) {
                animPool.put(a.name, a);
            }
        }
        // 独立动画文件：animations/*.json（geo.json 是模型不碰）+ *.bmd（多骨架组即帧动画）
        for (Path root : roots) {
            if (root == null || !java.nio.file.Files.isDirectory(root)) {
                continue;
            }
            try (java.util.stream.Stream<Path> walk = java.nio.file.Files.walk(root)) {
                walk.filter(p -> {
                    String n = p.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                    return (n.endsWith(".json") && !n.endsWith(".geo.json")) || n.endsWith(".bmd");
                }).forEach(this::loadAnimFile);
            } catch (Exception e) {
                ModelLog.warn("动画文件扫描失败 " + root + ": " + e);
            }
        }
    }

    private void loadAnimFile(Path p) {
        try {
            String fn = p.getFileName().toString();
            if (fn.toLowerCase(java.util.Locale.ROOT).endsWith(".bmd")) {
                // 独立 BMD 动画：多骨架组才产 IR；单组的模型文件在这不重复入库
                byte[] data = java.nio.file.Files.readAllBytes(p);
                com.opendreamcore.model.format.bmd.BmdCodec codec =
                        new com.opendreamcore.model.format.bmd.BmdCodec();
                codec.setAnimName(fn.substring(0, fn.length() - 4));
                com.opendreamcore.model.format.DecodeResult r =
                        codec.decode(data);
                for (com.opendreamcore.model.ir.AnimationIR a : r.animations) {
                    animPool.put(a.name, a);
                }
                return;
            }
            String text = new String(java.nio.file.Files.readAllBytes(p),
                    java.nio.charset.StandardCharsets.UTF_8);
            for (com.opendreamcore.model.ir.AnimationIR a
                    : com.opendreamcore.model.format.anim.BedrockAnimCodec.parse(text)) {
                animPool.put(a.name, a);
            }
        } catch (Exception e) {
            ModelLog.warn("动画文件解析失败 " + p.getFileName() + " -> " + e);
        }
    }

    /** 骨骼化产物（与 byModelId 同键；无则 null）。 */
    public synchronized BakedRig rigOf(String idRaw) {
        if (idRaw == null) {
            return null;
        }
        return rigs.get(resolvedId(idRaw));
    }

    /** 内嵌动画（与 byModelId 同键；无则空表）。 */
    public synchronized java.util.List<com.opendreamcore.model.ir.AnimationIR> animsOf(String idRaw) {
        java.util.List<com.opendreamcore.model.ir.AnimationIR> out =
                new java.util.ArrayList<com.opendreamcore.model.ir.AnimationIR>();
        if (idRaw != null) {
            java.util.List<com.opendreamcore.model.ir.AnimationIR> hit = anims.get(resolvedId(idRaw));
            if (hit != null) {
                out.addAll(hit);
            }
        }
        return out;
    }

    private synchronized String resolvedId(String idRaw) {
        String id = idRaw.trim();
        if (rigs.containsKey(id)) {
            return id;
        }
        for (Map.Entry<String, BakedRig> e : rigs.entrySet()) {
            if (e.getKey().endsWith(id)) {
                return e.getKey();
            }
        }
        return id;
    }

    private void normalize(BakedModel b, int w, int h) {
        scaleUv(b.quads, w, h);
        scaleUv(b.tris, w, h);
    }

    private static void scaleUv(float[] data, int w, int h) {
        for (int i = 0; i + 4 < data.length; i += 8) {
            data[i + 3] /= w;
            data[i + 4] /= h;
        }
    }

    private static int[] pngSize(byte[] png) {
        if (png == null || png.length < 24 || png[0] != (byte) 0x89 || png[1] != 'P') {
            return null;
        }
        int w = ((png[16] & 0xFF) << 24) | ((png[17] & 0xFF) << 16) | ((png[18] & 0xFF) << 8) | (png[19] & 0xFF);
        int h = ((png[20] & 0xFF) << 24) | ((png[21] & 0xFF) << 16) | ((png[22] & 0xFF) << 8) | (png[23] & 0xFF);
        return new int[]{w, h};
    }

    private Path assetsRootOf(Path file) {
        return root;
    }

    /** 相对根的 id（去扩展名；geo.json 去双层后缀）。 */
    static String relKey(Path rootDir, Path file, String stripExt) {
        String s = rootDir == null ? file.toString() : rootDir.relativize(file).toString();
        s = s.replace('\\', '/');
        if (stripExt != null && s.endsWith(stripExt)) {
            s = s.substring(0, s.length() - stripExt.length());
        } else if (s.endsWith(".geo.json")) {
            s = s.substring(0, s.length() - ".geo.json".length());
        } else {
            int dot = s.lastIndexOf('.');
            if (dot > 0) {
                s = s.substring(0, dot);
            }
        }
        return s;
    }
}
