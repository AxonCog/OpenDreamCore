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
    /** 贴图标签 → PNG 字节（内嵌解码产物或 assets/texture 文件）。 */
    private final Map<String, byte[]> textures = new HashMap<String, byte[]>();
    private TextureSink sink;
    private Path root;
    private java.util.List<Path> roots = new java.util.ArrayList<Path>();
    private int loadedModels;
    private int loadedTextures;

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
        missingIds.clear();
        loadedModels = 0;
        loadedTextures = 0;
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
        missingIds.clear();
        loadedModels = 0;
        loadedTextures = 0;
    }

    /**
     * 按名字直接取已烘焙模型：命中缓存就返回，没命中就按名字探路径、读盘、解码、烘焙。
     *
     * <p>写法随便：{@code 刺锤} / {@code 刺锤.bbmodel} / {@code models/刺锤.bbmodel} /
     * {@code opendreamcore:刺锤.bbmodel} / 反斜杠路径都认；扩展名缺省按 bbmodel→geo.json→obj 依次试。
     * 找不到返回 null（调用方跳过该实体，缺席即安全）。
     */
    public synchronized java.util.List<BakedModel> byModelId(String idRaw) {
        if (idRaw == null) {
            return null;
        }
        String id = idRaw.trim();
        if (id.isEmpty()) {
            return null;
        }
        java.util.List<BakedModel> hit = models.get(id);
        if (hit != null) {
            return hit;
        }
        if (missingIds.contains(id)) {
            return null;
        }
        byte[] raw = resolveBytes(id, true);
        if (raw == null) {
            missingIds.add(id);
            ModelLog.warn("模型没找到（按名字直接找，已缓存不再重复找）: " + idRaw + " 根=" + roots);
            return null;
        }
        try {
            String name = id.replace('\\', '/');
            int slash = name.lastIndexOf('/');
            if (slash >= 0) {
                name = name.substring(slash + 1);
            }
            DecodeResult r = FormatCodecs.decode(name, new String(raw, StandardCharsets.UTF_8));
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
            ModelLog.warn("模型解码失败 " + idRaw + ": " + e);
            return null;
        }
    }

    /** 已解析过的模型 id（命令列表用；没被规则引用过的不会出现——不扫描就是这样）。 */
    public synchronized java.util.Set<String> modelIds() {
        return models.keySet();
    }

    /** 贴图按名字直接取（同样是探路径，不扫描）。 */
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

    private static final String[] MODEL_EXT = {".bbmodel", ".geo.json", ".obj"};

    /** 常见子目录（限定集合，不是扫描）：名字写 models/x 或 x 都能落到同一个文件。 */
    private static final String[] SUB_DIRS = {
            "", "models/", "model/", "mobs/", "entity/", "blocks/", "items/",
            "assets/", "assets/model/", "assets/models/",
            "assets/opendreamcore/models/", "assets/opendreamcore/model/",
            "opendreamcore/models/", "opendreamcore/model/",
    };

    /** 探路径取字节：model=true 时缺省补扩展名。 */
    private byte[] resolveBytes(String ref, boolean model) {
        String norm = normalizeRef(ref);
        if (norm.isEmpty()) {
            return null;
        }
        java.util.List<String> rels = new java.util.ArrayList<String>();
        rels.add(norm);
        if (model && !hasKnownExt(norm)) {
            for (String ext : MODEL_EXT) {
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
            for (String sub : SUB_DIRS) {
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

    private static String normalizeRef(String ref) {
        String r = ref.trim().replace('\\', '/');
        while (r.startsWith("./")) {
            r = r.substring(2);
        }
        while (r.startsWith("/")) {
            r = r.substring(1);
        }
        return r;
    }

    private static boolean hasKnownExt(String ref) {
        String lower = ref.toLowerCase(java.util.Locale.ROOT);
        return lower.endsWith(".bbmodel") || lower.endsWith(".geo.json") || lower.endsWith(".obj")
                || lower.endsWith(".png");
    }

    /** 内嵌贴图解码：label = embedded:<名>（与材质引用一致），登记 PNG 字节。 */
    private void loadEmbedded(DecodeResult r, String modelId) {
        for (int i = 0; i < r.embeddedTextures.size(); i++) {
            DecodeResult.EmbeddedTexture t = r.embeddedTextures.get(i);
            textures.put("embedded:" + t.name, t.data);
            loadedTextures++;
        }
    }

    private void bakeAndPut(String id, DecodeResult r) {
        // 逐材质归一 UV：每条 BakedModel 用其贴图的像素尺寸
        List<BakedModel> baked = QuadBaker.bake(r.model);
        for (BakedModel b : baked) {
            int[] dim = pngSize(textures.get(b.texture));
            if (dim == null) {
                dim = new int[]{(int) r.model.meta.textureWidth, (int) r.model.meta.textureHeight};
            }
            if (dim[0] > 0 && dim[1] > 0) {
                normalize(b, dim[0], dim[1]);
            }
        }
        models.put(id, baked);
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
