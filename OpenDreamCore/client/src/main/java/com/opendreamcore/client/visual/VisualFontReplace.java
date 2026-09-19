package com.opendreamcore.client.visual;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * FontConfig 字符替换（跟插件默认模板一一对应）。
 *
 * 服务端模板的四种形态在这全部认：
 *   肝:                         # 单字符精确替换
 *     texture: fonts/1.png
 *     height: 8
 *     ascent: 8
 *   数字组:                     # 区间批量：0-9 逐字符替换，贴图横向等分
 *     range: 0-9
 *     texture: fonts/digits.png
 *     height: 12
 *   金色汉字:                   # 正则选字：每个命中的字符都生成替换
 *     match: "[金银铜]"
 *     texture: fonts/metal.png
 *   全局字体:                   # TTF 模式：没写 font 的元素回退到这把字体
 *     ttf: fonts/font.ttf
 *
 * 跟字形叠加（VisualFontOverride，整段文字右侧贴装饰）分工不同：
 * 这个是逐字符换脸（碰见就贴图顶替），那个是整段加后缀——互不打扰。
 * 绘制端逐字符扫（TextElements.drawCharReplaced），普通段批画、命中字符贴图。
 *
 * 这文件最早是照着"整词换皮"写的，跟插件默认模板（单字符/区间/正则）没对上，
 * 用户配了半天没效果，后来按模板语义整个重写了。
 */
public final class VisualFontReplace {

    /** 单字符替换条目：贴图 + 帧信息（range/match 展开后的最终形态）。 */
    public record CharGlyph(String texture, int width, int height, int fontWidth,
                            int u, int v, int frameW, int frameH) {
    }

    /** range 区间条目：start 起 count 个连续字符共用一张横向等分贴图。 */
    public record RangeGlyph(char start, int count, String texture,
                             int width, int height, int fontWidth) {
    }

    /** match 正则条目：命中的字符共用一张整图。 */
    public record RegexGlyph(Pattern pattern, String texture,
                             int width, int height, int fontWidth) {
    }

    private static volatile Map<Character, CharGlyph> singles = Map.of();
    private static volatile List<RangeGlyph> ranges = List.of();
    private static volatile List<RegexGlyph> regexes = List.of();
    private static volatile String defaultTtf = null;

    private VisualFontReplace() {
    }

    /** 规则集变化后重解析（handleVisualRules 入库 / /codc reload 时调用）。 */
    public static void refresh() {
        Map<Character, CharGlyph> s = new ConcurrentHashMap<>();
        List<RangeGlyph> rs = new ArrayList<>();
        List<RegexGlyph> xs = new ArrayList<>();
        String ttf = null;
        // 服务端 VisualRuleManager 把 FontConfig.yml 每条规则拆成一个 entry（id=键名，value=规则字段体），
        // 这里必须按 id + 扁平 body 解析——把 body 当“整包多规则”解析会因全是标量而全空（历史大坑）。
        for (Map.Entry<String, String> ruleEntry : ClientVisualStore.get().rulesOf("FontConfig").entrySet()) {
            String ruleId = ruleEntry.getKey();
            String yaml = ruleEntry.getValue();
            try {
                Map<String, Object> rule = new com.opendreamcore.config.YamlParser().parse(yaml);
                if (rule == null) {
                    continue;
                }
                // 兼容“整包多规则”形态（值又是 Map 时回退旧逻辑）
                if (ruleId == null && !rule.isEmpty()) {
                    boolean any = false;
                    for (Map.Entry<String, Object> e : rule.entrySet()) {
                        if (e.getValue() instanceof Map<?, ?> mm) {
                            any |= parseRule((String) e.getKey(), (Map<String, Object>) mm,
                                    s, rs, xs);
                        }
                    }
                    if (any) {
                        continue;
                    }
                }
                parseRule(ruleId, rule, s, rs, xs);
            } catch (Exception ignored) {
                // 单文件坏了静默跳过，少个字形不影响大局
            }
        }
        singles = Map.copyOf(s);
        ranges = List.copyOf(rs);
        regexes = List.copyOf(xs);
        defaultTtf = ttf;
    }

    /** 解析单条规则：ruleId=单字符时即替换键；range/match/ttf 各就各位。 */
    private static boolean parseRule(String ruleId, Map<String, Object> rule,
                                     Map<Character, CharGlyph> s,
                                     List<RangeGlyph> rs, List<RegexGlyph> xs) {
        // ttf 全局字体：路径保持原样（用户自己指定，跟贴图同根；CustomFonts.getByPath 解析）
        Object ttfVal = rule.get("ttf");
        if (ttfVal != null && !String.valueOf(ttfVal).isBlank()) {
            defaultTtf = String.valueOf(ttfVal).trim().replace('\\', '/');
        }
        String texture = str(firstOf(rule, "texture", "path", "字形"));
        if (texture == null || texture.isBlank()) {
            return false; // 没贴图的规则（纯 ttf 之类）不进字符表
        }
        // 可选 fps：gif 播放帧率（0/缺省用 gif 自带帧间隔；>0 摁成 1000/fps 毫秒一帧）
        Object fpsRaw = rule.get("fps");
        if (fpsRaw != null && texture.endsWith(".gif")) {
            try {
                double fps = Double.parseDouble(String.valueOf(fpsRaw).trim());
                com.opendreamcore.client.resources.LooseResourceLoader.setGifFps(texture, fps);
            } catch (Exception ignored) {
                // 帧率写崩了这一条按默认帧间隔走，别连累别的规则
            }
        }
        int width = num(rule.get("width"), 9);
        int height = num(rule.get("height"), 9);
        int fontWidth = num(rule.get("fontWidth"), width);
        int u = num(rule.get("u"), 0);
        int v = num(rule.get("v"), 0);
        // range：区间批量，贴图横向等分（0-9 / a-f 都行）
        Object range = rule.get("range");
        if (range != null) {
            String rg = String.valueOf(range).trim();
            if (rg.length() == 3 && rg.charAt(1) == '-') {
                char a = rg.charAt(0), b = rg.charAt(2);
                if (a <= b) {
                    rs.add(new RangeGlyph(a, b - a + 1, texture, width, height, fontWidth));
                    return true;
                }
            }
        }
        // match：正则选字，每个命中的字符贴同一张整图
        Object match = rule.get("match");
        if (match != null) {
            try {
                xs.add(new RegexGlyph(Pattern.compile(String.valueOf(match).trim()),
                        texture, width, height, fontWidth));
                return true;
            } catch (Exception ignored) {
                // 正则写坏跳过这一条，别的规则照常
            }
        }
        // 单字符键：规则 id 即字符（服务端每条规则以键名当 id 下发）
        if (ruleId != null && ruleId.length() == 1) {
            s.put(ruleId.charAt(0), new CharGlyph(texture, width, height,
                    fontWidth, u, v, width, height));
            return true;
        }
        return false;
    }

    /** 按字符查替换条目；无命中 null。range 帧宽按贴图实际横向等分动态算。 */
    public static CharGlyph glyphFor(char c) {
        CharGlyph g = singles.get(c);
        if (g != null) {
            return g;
        }
        for (RangeGlyph r : ranges) {
            if (c >= r.start() && c < r.start() + r.count()) {
                int idx = c - r.start();
                int frameW = r.width();
                var sz = com.opendreamcore.client.resources.LooseResourceLoader.sizeOf(r.texture());
                if (sz != null && sz.width() > 0) {
                    frameW = sz.width() / r.count();
                }
                return new CharGlyph(r.texture(), frameW, r.height(), r.fontWidth(),
                        idx * frameW, 0, frameW, r.height());
            }
        }
        for (RegexGlyph x : regexes) {
            if (x.pattern().matcher(String.valueOf(c)).matches()) {
                return new CharGlyph(x.texture(), x.width(), x.height(), x.fontWidth(), 0, 0, x.width(), x.height());
            }
        }
        return null;
    }

    /** 是否有任何字符替换规则（快路径短路用）。 */
    public static boolean hasAny() {
        return !singles.isEmpty() || !ranges.isEmpty() || !regexes.isEmpty();
    }

    /** 全局字体（FontConfig 里 ttf 键）；没有返回 null。 */
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
        return o instanceof Number n ? n.intValue() : fallback;
    }
}