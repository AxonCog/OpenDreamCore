package com.opendreamcore.client.visual;

import com.opendreamcore.client.CloudCache;
import com.opendreamcore.client.LooseTextureLoader;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 全局 TTF 字形源（远古版）：FontConfig 的「全局字体」规则命中后，这里负责把
 * 一个字符光栅成可绘制的字形位图与度量。远古三版（1.6.4 / 1.7.10 / 1.12.2）的
 * 字体层没有可挂靠的字形接口，绘制与量宽都得自己算，所以这个类把「字形位图」
 * 和「推进宽度 / 基线上偏」一起给出来，绘制端与量宽端取同一份结果，口径不会分叉。
 *
 * 缩放基准跟现代端逐字一致：以全角汉字（『中』）的推进宽度为基准，把它压到 9 像素，
 * 其余字符按同一比例缩放。这样同一个字体文件在 1.12.2 / 1.16.5 / 1.21.8 上画出来
 * 的字号相同，同一份 FontConfig 跨版本看起来才是一个样。
 *
 * 字体文件的寻找顺序：先看客户端 OpenDreamCore 目录下的本地文件（玩家自己丢进去的），
 * 再退到服务端下发的云缓存（服务器统一配的字体）。云缓存里取到的字节要落成一个临时
 * 文件才能交给字体解析器——字体解析只认文件，不认字节流。
 *
 * 缓存按「字体路径 + 码位」建键，字体路径变了旧键自然失效，不用额外清理；
 * 出任何意外都返回 null，调用方回退原版字体，绝不把异常带进渲染线程。
 */
public final class LegacyTtfSource {

    /** 一个字符的光栅结果：已按目标字号缩放的白字位图 + 度量。 */
    public static final class Glyph {
        /** 该字形的码位（纹理缓存按它建键）。 */
        public final char codePoint;
        /** 推进宽度（缩放后，像素，至少 1）。 */
        public final int advance;
        /** 字形框顶相对基线的偏移（缩放后，向上为负）。 */
        public final float top;
        /** 白字位图（已缩放，ARGB）。 */
        public final BufferedImage image;

        Glyph(char codePoint, int advance, float top, BufferedImage image) {
            this.codePoint = codePoint;
            this.advance = advance;
            this.top = top;
            this.image = image;
        }
    }

    /** 尺度过大时重建缓存，防止长会话里码位累积把内存撑爆。 */
    private static final int CACHE_MAX = 4096;

    /** 基准字号：全角汉字的推进宽压到这个像素数，与现代端同一口径。 */
    private static final float BASE_PX = 9.0F;

    private static final Map<String, Glyph> CACHE = new ConcurrentHashMap<String, Glyph>();
    /** 字形位图 → 已注册的纹理位置（按码位缓存，一个字符只上传一次显存）。 */
    private static final Map<Integer, String> TEXTURES = new ConcurrentHashMap<Integer, String>();
    /** 字体解析结果缓存：路径 → 解析器（解析很贵，一个字体只做一次）。 */
    private static final Map<String, Object> FONT_CACHE = new ConcurrentHashMap<String, Object>();
    /** 解析失败的路径也记一笔，避免每个字符都去撞一次磁盘。 */
    private static final Map<String, Boolean> FONT_FAILED = new ConcurrentHashMap<String, Boolean>();

    private static volatile Path gameDir;

    private LegacyTtfSource() {
    }

    /** target 初始化时告知客户端游戏目录（找本地字体用）。 */
    public static void setGameDir(Path dir) {
        gameDir = dir;
        invalidate();
    }

    /** 清缓存：字体路径换了、或重进世界重扫资源时调用。 */
    public static void invalidate() {
        CACHE.clear();
        TEXTURES.clear();
        FONT_CACHE.clear();
        FONT_FAILED.clear();
    }

    /**
     * 字形位图 → 可直接绘制的纹理位置（按码位缓存）。
     *
     * 全局字体字形是渲染期一个一个光栅出来的，不是扫盘扫到的，所以走「临时注册」这条路，
     * 不进散装贴图那两张按路径建的表；缓存与复用在这里管——同一个字符只往显存传一次，
     * 之后每帧绘制都复用。各版纹理系统怎么注册的差异全压在注册 SPI 后面，这里只跟字符串打交道。
     * 失败返回 null，调用方跳过这个字、保持原样。
     */
    public static String textureOf(Glyph g) {
        if (g == null) {
            return null;
        }
        try {
            Integer key = Integer.valueOf(g.codePoint);
            String hit = TEXTURES.get(key);
            if (hit != null) {
                return hit;
            }
            String rel = "ttf-glyph-" + Integer.toHexString(g.codePoint) + ".png";
            String rl = LooseTextureLoader.registerAdhoc(rel, g.image);
            if (rl == null) {
                return null;
            }
            if (TEXTURES.size() >= CACHE_MAX) {
                TEXTURES.clear();
            }
            TEXTURES.put(key, rl);
            return rl;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 是否配了全局字体（快路径短路：没配就零开销）。 */
    public static boolean enabled() {
        String path = LegacyFontReplace.defaultTtf();
        return path != null && !path.trim().isEmpty();
    }

    /** 取字形；没配字体、字体解析不了、字符无字形、任何异常，都返回 null。 */
    public static Glyph get(char c) {
        try {
            String path = LegacyFontReplace.defaultTtf();
            if (path == null || path.trim().isEmpty()) {
                return null;
            }
            String key = path + "#" + (int) c;
            Glyph hit = CACHE.get(key);
            if (hit != null) {
                return hit;
            }
            com.opendreamcore.ui.TtfFont font = font(path);
            if (font == null) {
                return null;
            }
            Glyph made = render(c, font);
            if (made == null) {
                return null;
            }
            if (CACHE.size() >= CACHE_MAX) {
                CACHE.clear();
            }
            CACHE.put(key, made);
            return made;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 按 9 像素全角基准缩放一个字形。
     *
     * 原版渲染源是 64 号字（共享层字体封装定的），直接画会大得离谱，所以先取
     * 全角汉字的推进宽当基准，算出缩放比，再把位图与度量一起缩到目标尺寸。
     * 基线对齐沿用原版口径：上偏 = 减去 ascent，绘制时从「基线 + 上偏」起画。
     */
    private static Glyph render(char c, com.opendreamcore.ui.TtfFont font) {
        BufferedImage raw = font.renderGlyph(c);
        if (raw == null) {
            return null; // 空白/无字形：交给调用方回退
        }
        int base = font.advance('中');
        if (base <= 0) {
            base = Math.max(1, font.lineHeight());
        }
        float scale = BASE_PX / (float) base;
        int w = Math.max(1, Math.round(raw.getWidth() * scale));
        int h = Math.max(1, Math.round(raw.getHeight() * scale));
        BufferedImage scaled = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scaled.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(raw, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }
        float top = -Math.round(font.ascent() * scale);
        int advance = Math.max(1, Math.round(font.advance(c) * scale));
        return new Glyph(c, advance, top, scaled);
    }

    /** 解析字体（带缓存）：本地文件优先，退云缓存字节落临时文件。失败返回 null。 */
    private static com.opendreamcore.ui.TtfFont font(String path) {
        String key = path.trim().replace('\\', '/');
        Object cached = FONT_CACHE.get(key);
        if (cached != null) {
            return (com.opendreamcore.ui.TtfFont) cached;
        }
        if (FONT_FAILED.containsKey(key)) {
            return null;
        }
        try {
            File file = resolveFile(key);
            if (file == null || !file.isFile()) {
                FONT_FAILED.put(key, Boolean.TRUE);
                return null;
            }
            // 字体名用文件基名，日志与诊断里好认
            String name = file.getName();
            com.opendreamcore.ui.TtfFont font =
                    com.opendreamcore.ui.TtfFont.fromFile(name, file);
            FONT_CACHE.put(key, font);
            return font;
        } catch (Throwable t) {
            FONT_FAILED.put(key, Boolean.TRUE);
            return null;
        }
    }

    /**
     * 定位字体文件：先本地 OpenDreamCore 目录，再云缓存（写临时文件后交给解析器）。
     * 云缓存里取到的字节是以路径为键的，所以拿配置里那个相对路径去问就行。
     */
    private static File resolveFile(String key) throws IOException {
        Path dir = gameDir;
        if (dir != null) {
            Path local = dir.resolve("OpenDreamCore").resolve(key).normalize();
            if (Files.isRegularFile(local)) {
                return local.toFile();
            }
        }
        byte[] bytes = CloudCache.loadCached(key);
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        // 云缓存字节落临时文件：字体解析只认文件。名字按路径算，同一个字体只落一次
        Path tmpDir = dir != null ? dir.resolve("OpenDreamCore").resolve("cache").resolve("fonts-tmp")
                : Files.createTempDirectory("odc-fonts");
        Files.createDirectories(tmpDir);
        String base = key.substring(key.lastIndexOf('/') + 1);
        Path target = tmpDir.resolve(base);
        if (!Files.isRegularFile(target) || Files.size(target) != bytes.length) {
            Files.write(target, bytes);
        }
        return target.toFile();
    }
}
