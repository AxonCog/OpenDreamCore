package com.opendreamcore.client.visual;

import com.opendreamcore.config.YamlParser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * FontConfig 字符替换（远古线 Java8 版，语义跟现代端 VisualFontReplace 对齐）�? *
 * 四种形态全认：
 *   �?                     # 单字符精确替�? *     texture: testfonts/1.png
 *     height: 8
 *   数字�?                 # 区间批量�?-9 逐字符，贴图横向等分
 *     range: 0-9
 *     texture: testfonts/digits.png
 *   金色汉字:               # 正则选字
 *     match: "[金银铜]"
 *     texture: testfonts/metal.png
 *   全局字体:               # TTF（远古线 Java8 无合成字库，仅作规则登记，绘制回退默认字体�? *     ttf: fonts/font.ttf
 *
 * texture 路径�?image 元素 src 同一套语义：不带命名空间时按
 * �?target �?resolve 规则映射（assets/opendreamcore/textures/ 下找）�? * 用户�?testfonts/1.png，文件放材质�?assets/opendreamcore/textures/testfonts/1.png 就命中�? *
 * 消费面有三处，互不打架：
 *   1. PageRenderer.drawTextShape —�?OpenDreamCore 自己 UI 内逐字符替换；
 *   2. GlobalFontRenderer（各 target）—�?换掉 Minecraft.fontRenderer 全局实例�? *      聊天/输入�?�?原版界面全被接管，这是真正的全局替换�? *   3. glyphFor 直接调用 —�?谁想查就查，规则表是公开的�? */
public final class LegacyFontReplace {

    /** 单字符替换条目：贴图 + 帧信息（range/match 展开后的最终形态）�?*/
    public static final class Glyph {
        public final String texture;
        public final int width;
        public final int height;
        public final int fontWidth;
        public final int u;
        public final int v;
        public final int frameW;
        public final int frameH;
        /** 贴图横向总帧数（range 横向等分用；单字�?正则=1）�?*/
        public final int totalFrames;

        /**
         * 定帧：贴图确实在动、且这条字形是“整图当作一张帧表”的用法（单字/正则），
         * 才把 u 换成当前时间帧号。range 的 u 是字符在区间里的序号，语义不同，
         * 整张图就是该字符的帧表，不能拿去当帧号，否则会把相邻字符的帧切过来。
         *
         * 换过去的帧表由 LooseTextureLoader 提供（帧横向排开）；它自己会兜底——
         * 贴图不是动图、或帧表没注册上，给回来的就是静态整图（frames=1）。
         */
        public Glyph framed() {
            if (totalFrames == 1 && u == 0 && com.opendreamcore.client.LooseTextureLoader.isAnimated(texture)) {
                com.opendreamcore.client.LooseTextureLoader.Sheet sh =
                        com.opendreamcore.client.LooseTextureLoader.sheetOf(texture);
                if (sh != null && sh.frames > 1) {
                    return new Glyph(sh.rl, frameW, frameH, fontWidth,
                            sh.frame, 0, sh.frameW, sh.frameH, sh.frames);
                }
            }
            return this;
        }

        public Glyph(String texture, int width, int height, int fontWidth,
                     int u, int v, int frameW, int frameH) {
            this(texture, width, height, fontWidth, u, v, frameW, frameH, 1);
        }

        public Glyph(String texture, int width, int height, int fontWidth,
                     int u, int v, int frameW, int frameH, int totalFrames) {
            this.texture = texture;
            this.width = width;
            this.height = height;
            this.fontWidth = fontWidth;
            this.u = u;
            this.v = v;
            this.frameW = frameW;
            this.frameH = frameH;
            this.totalFrames = totalFrames;
        }
    }

    /** range 区间条目：start �?count 个连续字符共用一张横向等分贴图�?*/
    private static final class RangeGlyph {
        final char start;
        final int count;
        final String texture;
        final int width;
        final int height;
        final int fontWidth;

        RangeGlyph(char start, int count, String texture, int width, int height, int fontWidth) {
            this.start = start;
            this.count = count;
            this.texture = texture;
            this.width = width;
            this.height = height;
            this.fontWidth = fontWidth;
        }
    }

    /** match 正则条目：命中的字符共用一张整图�?*/
    private static final class RegexGlyph {
        final Pattern pattern;
        final String texture;
        final int width;
        final int height;
        final int fontWidth;

        RegexGlyph(Pattern pattern, String texture, int width, int height, int fontWidth) {
            this.pattern = pattern;
            this.texture = texture;
            this.width = width;
            this.height = height;
            this.fontWidth = fontWidth;
        }
    }

    private static volatile Map<Character, Glyph> singles = java.util.Collections.emptyMap();
    private static volatile List<RangeGlyph> ranges = java.util.Collections.emptyList();
    private static volatile List<RegexGlyph> regexes = java.util.Collections.emptyList();
    private static volatile String defaultTtf = null;

    /** gif 帧率覆盖：贴图路径 → fps（0=遵循 gif 自带帧间隔，跟现代端同一套语义）。 */
    private static final Map<String, Double> GIF_FPS = new java.util.concurrent.ConcurrentHashMap<String, Double>();

    /**
     * 尺寸协商：w/h 有正数就照用；为 0（远端图未写尺寸、或本地图没写）则查实际
     * 像素。查不到就退回缺省边长，宁可画小一点也不能出 0 宽度的不可见字形。
     */
    private static Glyph effective(Glyph g) {
        if (g.width > 0 && g.height > 0) {
            return g;
        }
        int ew = g.width > 0 ? g.width : 0;
        int eh = g.height > 0 ? g.height : 0;
        int[] sz = com.opendreamcore.client.LooseTextureLoader.sizeOf(g.texture);
        if (sz != null && sz.length >= 2) {
            if (ew <= 0 && sz[0] > 0) {
                ew = sz[0];
            }
            if (eh <= 0 && sz[1] > 0) {
                eh = sz[1];
            }
        }
        if (ew <= 0) {
            ew = 9;
        }
        if (eh <= 0) {
            eh = 9;
        }
        return new Glyph(g.texture, ew, eh, g.fontWidth > 0 ? g.fontWidth : ew,
                g.u, g.v, g.frameW > 0 ? g.frameW : ew, g.frameH > 0 ? g.frameH : eh, g.totalFrames);
    }

    /** 帧率登记（FontConfig 的 fps 键落地）；fps<=0 清除覆盖，回 gif 自带节奏。 */
    public static void setGifFps(String texture, double fps) {
        if (texture == null) {
            return;
        }
        String want = texture.replace('\\', '/');
        if (fps > 0.0D) {
            GIF_FPS.put(want, Double.valueOf(fps));
        } else {
            GIF_FPS.remove(want);
        }
    }

    /** 取帧率覆盖：先原名、再尾名兜底；没配返回 0（用 gif 自带节奏）。 */
    public static double gifFpsOf(String texture) {
        if (texture == null) {
            return 0.0D;
        }
        String want = texture.replace('\\', '/');
        Double hit = GIF_FPS.get(want);
        if (hit != null) {
            return hit.doubleValue();
        }
        String tail = want.substring(want.lastIndexOf('/') + 1);
        for (Map.Entry<String, Double> e : GIF_FPS.entrySet()) {
            String k = e.getKey();
            String kTail = k.substring(k.lastIndexOf('/') + 1);
            if (kTail.equalsIgnoreCase(tail)) {
                return e.getValue().doubleValue();
            }
        }
        return 0.0D;
    }

    private LegacyFontReplace() {
    }

    /** 规则集变化后重解析（LegacyVisualSkins.apply 入库时调用）�?*/
    public static void refresh() {
        Map<Character, Glyph> s = new LinkedHashMap<>();
        List<RangeGlyph> rs = new ArrayList<>();
        List<RegexGlyph> xs = new ArrayList<>();
        String ttf = null;
        for (Map.Entry<String, String> ruleEntry : LegacyVisualSkins.rulesOf("FontConfig").entrySet()) {
            String ruleId = ruleEntry.getKey();
            try {
                Map<String, Object> rule = new YamlParser().parse(ruleEntry.getValue());
                if (rule == null) {
                    continue;
                }
                    Object ttfVal = rule.get("ttf");
                    if (ttfVal != null && !String.valueOf(ttfVal).trim().isEmpty()) {
                        ttf = String.valueOf(ttfVal).trim().replace('\\', '/');
                    }
                    String texture = str(firstOf(rule, "texture", "path", "字形"));
                    if (texture == null || texture.trim().isEmpty()) {
                        continue;
                    }
                    // 可选 fps：gif 播放帧率（缺省/0 用 gif 自带帧间隔，>0 摁成 1000/fps）
                    Object fpsRaw = rule.get("fps");
                    if (fpsRaw != null && texture.endsWith(".gif")) {
                        try {
                            double fps = Double.parseDouble(String.valueOf(fpsRaw).trim());
                            setGifFps(texture, fps);
                        } catch (Exception ignored) {
                            // 帧率写崩了这一条按默认帧间隔走，别连累别的规则
                        }
                    }
                    // URL 贴图（http/https）未写尺寸 = 原生帧尺寸（0 交给 effective 协商）；
                    // 本地贴图维持 9×9 缺省（老配置全是本地小帧图，不能突然放大炸屏）
                    boolean remoteTex = texture.startsWith("http://") || texture.startsWith("https://");
                    int defSize = remoteTex ? 0 : 9;
                    int width = num(rule.get("width"), defSize);
                    int height = num(rule.get("height"), defSize);
                    int fontWidth = num(rule.get("fontWidth"), width);
                    int u = num(rule.get("u"), 0);
                    int v = num(rule.get("v"), 0);
                    Object range = rule.get("range");
                    if (range != null) {
                        String rg = String.valueOf(range).trim();
                        if (rg.length() == 3 && rg.charAt(1) == '-') {
                            char a = rg.charAt(0);
                            char b = rg.charAt(2);
                            if (a <= b) {
                                rs.add(new RangeGlyph(a, b - a + 1, texture, width, height, fontWidth));
                                continue;
                            }
                        }
                    }
                    Object match = rule.get("match");
                    if (match != null) {
                        try {
                            xs.add(new RegexGlyph(Pattern.compile(String.valueOf(match).trim()),
                                    texture, width, height, fontWidth));
                        } catch (Exception ignored) {
                            // 正则写坏跳过这一条，别的规则照常
                        }
                        continue;
                    }
                    String key = ruleId;
                    if (key != null && key.length() == 1) {
                        s.put(key.charAt(0), new Glyph(texture, width, height, fontWidth, u, v, width, height));
                    }
            } catch (Exception ignored) {
                // 单文件坏了静默跳过，少个字形不影响大局
            }
        }
        singles = s.isEmpty() ? java.util.Collections.<Character, Glyph>emptyMap()
                : java.util.Collections.unmodifiableMap(s);
        ranges = rs.isEmpty() ? java.util.Collections.<RangeGlyph>emptyList()
                : java.util.Collections.unmodifiableList(rs);
        regexes = xs.isEmpty() ? java.util.Collections.<RegexGlyph>emptyList()
                : java.util.Collections.unmodifiableList(xs);
        defaultTtf = ttf;
        // 远端贴图（http/https）提前取回来：取回后按原 URL 注册成普通散装贴图，
        // 于是 lookup / sheetOf / sizeOf 照旧按配置里写的字符串查就能命中，
        // 规则层与绘制层一个字不用改。取图全在后台线程，这里不阻塞。
        List<String> remote = new ArrayList<String>();
        for (Glyph g : s.values()) {
            if (LegacyUrlTextures.isRemote(g.texture) && !remote.contains(g.texture)) {
                remote.add(g.texture);
            }
        }
        for (RangeGlyph rg : rs) {
            if (LegacyUrlTextures.isRemote(rg.texture) && !remote.contains(rg.texture)) {
                remote.add(rg.texture);
            }
        }
        for (RegexGlyph xg : xs) {
            if (LegacyUrlTextures.isRemote(xg.texture) && !remote.contains(xg.texture)) {
                remote.add(xg.texture);
            }
        }
        LegacyUrlTextures.prefetch(remote);
    }
    /** 按字符查替换条目；无命中 null。range �?u=帧号，绘制端�?totalFrames 等分�?*/
    public static Glyph glyphFor(char c) {
        Glyph g = singles.get(c);
        if (g != null) {
            return g.framed();
        }
        for (RangeGlyph r : ranges) {
            if (c >= r.start && c < r.start + r.count) {
                int idx = c - r.start;
                // 帧宽按贴图实际横向等分动态算：配置写的 width 往往只是估数，
                // 真实帧宽 = 总宽 / 帧数，切偏了就会看到邻帧的边
                int frameW = r.width;
                int[] sz = com.opendreamcore.client.LooseTextureLoader.sizeOf(r.texture);
                if (sz != null && sz.length >= 1 && sz[0] > 0 && r.count > 0) {
                    int fw = sz[0] / r.count;
                    if (fw > 0) {
                        frameW = fw;
                    }
                }
                return effective(new Glyph(r.texture, frameW, r.height, r.fontWidth,
                        idx, 0, frameW, r.height, r.count));
            }
        }
        for (RegexGlyph x : regexes) {
            if (x.pattern.matcher(String.valueOf(c)).matches()) {
                return effective(new Glyph(x.texture, x.width, x.height, x.fontWidth, 0, 0, x.width, x.height))
                        .framed();
            }
        }
        return null;
    }

    /** 是否有任何字符替换规则（快路径短路用）�?*/
    public static boolean hasAny() {
        return !singles.isEmpty() || !ranges.isEmpty() || !regexes.isEmpty()
                || (defaultTtf != null && !defaultTtf.trim().isEmpty());
    }

    /** 全局字体路径（FontConfig �?ttf 键；远古线仅登记，绘制回退默认字体）�?*/
    public static String defaultTtf() {
        return defaultTtf;
    }

    private static Object firstOf(Map<String, Object> m, String... keys) {
        for (String k : keys) {
            Object v = m.get(k);
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static int num(Object o, int fallback) {
        return o instanceof Number ? ((Number) o).intValue() : fallback;
    }
}
