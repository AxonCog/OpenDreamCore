package com.opendreamcore.client;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * 散装贴图加载器（远古版）：扫 resourcepacks/OpenDreamCore 全深度 png/jpg/gif，
 * 运行时注册进各版 TextureManager——完全绕开资源包系统，所以中文文件名
 * 天然支持（键=原始文件名，ResourceLocation 用净化名）。跟现代端
 * LooseResourceLoader 一个意思，Java8 语法。
 *
 * gif 分两路注册：
 *   1. 首帧照旧注册成普通纹理（lookup 给谁都用它）——页面图片、光标、物品图标
 *      这些消费方行为一个字不变，不会因为改字体体系而回归；
 *   2. 另注册一张横向帧表，专供字符替换取帧（sheetOf），动图就是切 uv 换帧。
 * 帧表只在真动图上多占一张纹理，静态图不产生任何额外开销。
 *
 * 注册动作各版 TextureManager API 不一样，收敛进 TextureRegistrar SPI，
 * 四个远古 target 各实现一个。
 */
public final class LooseTextureLoader {

    /** 注册 SPI：target 实现，把一张 BufferedImage 注册成可渲染纹理。 */
    public interface TextureRegistrar {
        /** rel 是相对 resourcepacks/OpenDreamCore 的路径（含文件名）。返回可渲染的 RL 字符串；失败 null。 */
        String register(String rel, java.awt.image.BufferedImage img);
    }

    /** 字符替换取帧结果：帧表纹理 + 当前帧 + 单帧尺寸。静态图 frames=1、frame=0。 */
    public static final class Sheet {
        public final String rl;
        public final int frame;
        public final int frames;
        public final int frameW;
        public final int frameH;

        Sheet(String rl, int frame, int frames, int frameW, int frameH) {
            this.rl = rl;
            this.frame = frame;
            this.frames = frames;
            this.frameW = frameW;
            this.frameH = frameH;
        }
    }

    private static volatile TextureRegistrar registrar;
    private static final Map<String, String> RL_BY_FILE = new ConcurrentHashMap<String, String>();
    /** 帧表：rel → RL（只有真动图才有，静态图不给这张，避免白占纹理）。 */
    private static final Map<String, String> SHEET_BY_FILE = new ConcurrentHashMap<String, String>();
    /** 动图播放器：rel → 播放器（帧表 RL 与尺寸都从它取）。 */
    private static final Map<String, LegacyGifPlayer> GIF_BY_FILE = new ConcurrentHashMap<String, LegacyGifPlayer>();
    /** 原图尺寸：rel → {宽, 高}（配置没写 width/height 时按实际尺寸协商用）。 */
    private static final Map<String, int[]> SIZE_BY_FILE = new ConcurrentHashMap<String, int[]>();
    private static volatile boolean scanned;

    /** 帧表宽度上限：超了就只当静态图，别硬塞一张 GL 上不去的巨图。 */
    private static final int MAX_SHEET_WIDTH = 16384;

    private LooseTextureLoader() {
    }

    /** target 初始化时注册一次。 */
    public static void setRegistrar(TextureRegistrar r) {
        registrar = r;
    }

    /**
     * 远端贴图注册（URL 直连取回来的图）：键就是那条 URL 本身，所以配置里写 URL 的
     * 规则照旧按原字符串就能查到，规则层一个字不用改。另存一份尾名（URL 最后一段），
     * 跟本地散装贴图一个规矩，方便只写文件名的配置也能命中。
     *
     * gif 不为 null 说明解出了多帧：首帧当普通纹理，另注册一张帧表专供取帧，
     * 跟扫盘那一路完全同一个流程。帧表过宽（GL 上不去）时退回只注册首帧。
     */
    public static void registerRemote(String url, java.awt.image.BufferedImage img, LegacyGifPlayer gif) {
        TextureRegistrar reg = registrar;
        if (reg == null || url == null || img == null) {
            return;
        }
        String key = url.trim();
        String rl = reg.register(key, img);
        if (rl == null) {
            return;
        }
        RL_BY_FILE.put(key, rl);
        String tail = tailKey(key);
        if (tail != null && !RL_BY_FILE.containsKey(tail)) {
            RL_BY_FILE.put(tail, rl);
        }
        int sw = gif != null ? gif.frameW() : img.getWidth();
        int sh = gif != null ? gif.frameH() : img.getHeight();
        SIZE_BY_FILE.put(key, new int[]{sw, sh});
        if (tail != null && !SIZE_BY_FILE.containsKey(tail)) {
            SIZE_BY_FILE.put(tail, new int[]{sw, sh});
        }
        if (gif != null) {
            String sheetRl = reg.register(key + "#sheet", gif.sheet());
            if (sheetRl != null) {
                SHEET_BY_FILE.put(key, sheetRl);
                GIF_BY_FILE.put(key, gif);
                if (tail != null) {
                    if (!SHEET_BY_FILE.containsKey(tail)) {
                        SHEET_BY_FILE.put(tail, sheetRl);
                    }
                    if (!GIF_BY_FILE.containsKey(tail)) {
                        GIF_BY_FILE.put(tail, gif);
                    }
                }
            }
        }
    }

    /** URL 尾名：最后一段路径（去掉查询串、# 片段），拿不到给 null。 */
    private static String tailKey(String url) {
        try {
            String s = url;
            int hash = s.indexOf('#');
            if (hash >= 0) {
                s = s.substring(0, hash);
            }
            int q = s.indexOf('?');
            if (q >= 0) {
                s = s.substring(0, q);
            }
            int slash = s.lastIndexOf('/');
            String tail = slash >= 0 ? s.substring(slash + 1) : s;
            return tail.isEmpty() ? null : tail;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 扫描注册：全深度递归，改完 Ctrl+R / 重进会重扫（scanned 标记在 reload 时清）。 */
    public static void scan(Path gameDir) {
        RL_BY_FILE.clear();
        SHEET_BY_FILE.clear();
        GIF_BY_FILE.clear();
        SIZE_BY_FILE.clear();
        if (gameDir == null) {
            scanned = true;
            return;
        }
        Path root = gameDir.resolve("resourcepacks").resolve("OpenDreamCore");
        if (!Files.isDirectory(root)) {
            scanned = true;
            return;
        }
        TextureRegistrar reg = registrar;
        if (reg == null) {
            scanned = true;
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path p : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                // 不按扩展名过滤：是不是图看解码结果，非图静默跳过
                String rel = root.relativize(p).toString().replace('\\', '/');
                try {
                    // 动图先解帧表：解出来才说明真是多帧 gif，再去注册；
                    // 静态图这步直接返回 null，后面按整张图注册，跟以前完全一样
                    LegacyGifPlayer gif = gifOf(p.toFile(), rel);
                    java.awt.image.BufferedImage img =
                            gif != null ? reader0(p) : javax.imageio.ImageIO.read(p.toFile());
                    if (img == null) {
                        continue;
                    }
                    String rl = reg.register(rel, img);
                    if (rl != null) {
                        RL_BY_FILE.put(rel, rl);
                        // 顺带存尾名，方便只写文件名不写目录的配置
                        String tail = rel.substring(rel.lastIndexOf('/') + 1);
                        if (!RL_BY_FILE.containsKey(tail)) {
                            RL_BY_FILE.put(tail, rl);
                        }
                        // 原图尺寸：动图记单帧尺寸（绘制端按帧切 uv），静态图记整图
                        int sw = gif != null ? gif.frameW() : img.getWidth();
                        int sh = gif != null ? gif.frameH() : img.getHeight();
                        SIZE_BY_FILE.put(rel, new int[]{sw, sh});
                        if (!SIZE_BY_FILE.containsKey(tail)) {
                            SIZE_BY_FILE.put(tail, new int[]{sw, sh});
                        }
                        // 帧表另注册一张（RL 加 _sheet 后缀，不跟首帧纹理撞名）
                        if (gif != null) {
                            String sheetRl = reg.register(sheetKey(rel), gif.sheet());
                            if (sheetRl != null) {
                                SHEET_BY_FILE.put(rel, sheetRl);
                                SHEET_BY_FILE.put(tail, sheetRl);
                                GIF_BY_FILE.put(rel, gif);
                                GIF_BY_FILE.put(tail, gif);
                            }
                        }
                    }
                } catch (Throwable ignored) {
                    // 单张坏了跳过，别让整批加载崩
                }
            }
        } catch (Throwable ignored) {
        }
        scanned = true;
    }

    /** 按配置里写的路径（可中文、可带目录）查可渲染 RL；未命中 null。 */
    public static String lookup(String file) {
        if (file == null) {
            return null;
        }
        String want = file.replace('\\', '/');
        String hit = RL_BY_FILE.get(want);
        if (hit != null) {
            return hit;
        }
        // 尾名匹配兜底：配置写 肝.png，注册键可能是 testfonts/肝.png
        String tail = want.substring(want.lastIndexOf('/') + 1);
        for (Map.Entry<String, String> e : RL_BY_FILE.entrySet()) {
            String k = e.getKey();
            String kTail = k.substring(k.lastIndexOf('/') + 1);
            if (kTail.equalsIgnoreCase(tail)) {
                return e.getValue();
            }
        }
        return null;
    }

    /** 取帧信息：动图给帧表 + 当前帧；静态图给整图 + 单帧。未注册返回 null。 */
    public static Sheet sheetOf(String file) {
        String rel = matchKey(file);
        if (rel == null) {
            return null;
        }
        LegacyGifPlayer gif = GIF_BY_FILE.get(rel);
        if (gif != null) {
            String sheetRl = SHEET_BY_FILE.get(rel);
            if (sheetRl != null) {
                return new Sheet(sheetRl, gif.currentFrame(), gif.frames(), gif.frameW(), gif.frameH());
            }
        }
        // 静态图（或帧表没注册上）：整张当一帧，uv 自然是 0..1
        String rl = RL_BY_FILE.get(rel);
        return rl == null ? null : new Sheet(rl, 0, 1, 0, 0);
    }

    /** 原图尺寸 {宽, 高}；未注册返回 null。静态图是整图尺寸，动图是单帧尺寸。 */
    public static int[] sizeOf(String file) {
        String rel = matchKey(file);
        return rel == null ? null : SIZE_BY_FILE.get(rel);
    }

    /** 是不是动图（决定绘制端要不要每帧重取 uv）。 */
    public static boolean isAnimated(String file) {
        String rel = matchKey(file);
        return rel != null && GIF_BY_FILE.containsKey(rel);
    }

    /** 按配置里写的路径找注册键：先原名、再尾名兜底（跟 lookup 一套匹配规矩）。 */
    private static String matchKey(String file) {
        if (file == null) {
            return null;
        }
        String want = file.replace('\\', '/');
        if (RL_BY_FILE.containsKey(want)) {
            return want;
        }
        String tail = want.substring(want.lastIndexOf('/') + 1);
        for (Map.Entry<String, String> e : RL_BY_FILE.entrySet()) {
            String k = e.getKey();
            String kTail = k.substring(k.lastIndexOf('/') + 1);
            if (kTail.equalsIgnoreCase(tail)) {
                return k;
            }
        }
        return null;
    }

    /** 解当前配置帧率下的动图：不是动图、读了 fps=0 之类一律返回 null。 */
    private static LegacyGifPlayer gifOf(File f, String rel) {
        double fps = com.opendreamcore.client.visual.LegacyFontReplace.gifFpsOf(rel);
        LegacyGifPlayer p = LegacyGifPlayer.decode(f, fps);
        // 帧表太宽（长动图 × 大帧）就别硬上，退回静态首帧
        if (p != null && (long) p.frameW() * (long) p.frames() > MAX_SHEET_WIDTH) {
            return null;
        }
        return p;
    }

    /** 首帧解码：gif 已解过就复用第一帧，省一次读盘。 */
    private static java.awt.image.BufferedImage reader0(Path p) throws java.io.IOException {
        try (javax.imageio.stream.ImageInputStream in = javax.imageio.ImageIO.createImageInputStream(p.toFile())) {
            java.util.Iterator<javax.imageio.ImageReader> it =
                    javax.imageio.ImageIO.getImageReadersByFormatName("gif");
            if (!it.hasNext()) {
                return javax.imageio.ImageIO.read(p.toFile());
            }
            javax.imageio.ImageReader r = it.next();
            try {
                r.setInput(in, false, false);
                return r.read(0);
            } finally {
                r.dispose();
            }
        }
    }

    /** 帧表注册键：rel 上挂 _sheet 后缀（sanitize 会把下划线原样保留）。 */
    private static String sheetKey(String rel) {
        int dot = rel.lastIndexOf('.');
        return dot > 0 ? rel.substring(0, dot) + "_sheet" + rel.substring(dot) : rel + "_sheet";
    }

    /**
     * 临时注册一张图：不进两张查询表，只把图交给各版纹理系统拿到一个可渲染位置。
     * 全局字体的字形是渲染期一个一个光栅出来的，不是扫盘扫到的，没法按路径预先建表，
     * 所以走这条——缓存与复用交给调用方自己管。
     */
    public static String registerAdhoc(String rel, java.awt.image.BufferedImage img) {
        TextureRegistrar reg = registrar;
        if (reg == null || rel == null || img == null) {
            return null;
        }
        try {
            return reg.register(rel, img);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 强制重扫（Ctrl+R / 视觉规则重载时清标记）。 */
    public static void invalidate() {
        scanned = false;
    }

    /** RL 净化：非 [a-z0-9_.-/] 替换成 _+短哈希（跟现代端一套，保证合法且不撞名）。 */
    public static String sanitize(String rel) {
        StringBuilder sb = new StringBuilder();
        for (char c : rel.toLowerCase().toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '.' || c == '/' || c == '-') {
                sb.append(c);
            } else {
                sb.append('_').append(Integer.toHexString(c));
            }
        }
        return sb.toString();
    }
}