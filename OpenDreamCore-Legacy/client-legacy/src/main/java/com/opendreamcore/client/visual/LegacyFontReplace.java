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
                    int width = num(rule.get("width"), 9);
                    int height = num(rule.get("height"), 9);
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
    }
    /** 按字符查替换条目；无命中 null。range �?u=帧号，绘制端�?totalFrames 等分�?*/
    public static Glyph glyphFor(char c) {
        Glyph g = singles.get(c);
        if (g != null) {
            return g;
        }
        for (RangeGlyph r : ranges) {
            if (c >= r.start && c < r.start + r.count) {
                int idx = c - r.start;
                return new Glyph(r.texture, r.width, r.height, r.fontWidth,
                        idx, 0, r.width, r.height, r.count);
            }
        }
        for (RegexGlyph x : regexes) {
            if (x.pattern.matcher(String.valueOf(c)).matches()) {
                return new Glyph(x.texture, x.width, x.height, x.fontWidth, 0, 0, x.width, x.height);
            }
        }
        return null;
    }

    /** 是否有任何字符替换规则（快路径短路用）�?*/
    public static boolean hasAny() {
        return !singles.isEmpty() || !ranges.isEmpty() || !regexes.isEmpty();
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
