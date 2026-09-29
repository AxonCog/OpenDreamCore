package com.opendreamcore.visual;

import com.opendreamcore.config.YamlParser;
import com.opendreamcore.util.J8;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * FontConfig 规则模型（共享层，零 MC 依赖）。
 *
 * 服务端把 FontConfig.yml 每条规则拆成「规则 id + 字段体 YAML」下发，各平台端在
 * 这里解析成同一份模型，再由各版本自己的渲染层消费。解析与渲染分家的理由很实在：
 * 十六个目标里真正跟着版本走的只有「怎么把贴图画到字形上」；「配置怎么写、哪个键
 * 是什么含义」跨版本没有任何差别——写两份迟早写歪，历史上确实歪过（同一个 range
 * 在两条线里一个按帧号一个按像素算，配置一样效果不一样）。
 *
 * 键名取值是一份超集，历史写法全部保留：
 * <pre>
 *   texture / path / 字形    贴图路径（gif 直接写 .gif）
 *   gif                      贴图路径别名；写成布尔值时表示「这是动图」的显式声明
 *   width / height           绘制尺寸；不写时远程贴图取原生帧尺寸、本地贴图取 9
 *   ascent                   字形顶相对基线的距离（不写沿用历史几何）
 *   offsetX / xOffset        贴图横向偏移
 *   offsetY / yOffset        贴图纵向偏移
 *   fontWidth                排版推进宽度（不写等于 width）
 *   u / v                    整图取景起点
 *   range                    a-b 连续字符共用一张横向等分贴图
 *   match                    正则选字，命中字符共用一张整图
 *   fps                      gif 播放帧率（不写用 gif 自带帧间隔）
 *   ttf                      全局字体路径
 *   permissions              权限清单（谁可见，交给消费方裁决）
 *   permissionsContains      权限匹配语义：true 任一命中 / false 全部命中
 *   alias / single / color / style / alignment
 *                            兼容保留字段，解析时原样带出，不替消费方做决定
 * </pre>
 *
 * 解析失败一律「丢这一条、留其余」，绝不抛给调用方：配置文件里一个错字不该让整
 * 套字形失效。查询不到替换时返回 null，由调用方回退原版字形——本层不产出任何
 * 占位几何，避免把「没贴图」画成看不见的空顶点。
 */
public final class FontRules {

    /** 单条规则（不可变）。id 为单字符时即该字符的精确替换。 */
    public static final class Rule {
        public final String id;
        public final String texture;
        public final boolean gif;
        public final double fps;
        public final int width;
        public final int height;
        public final int ascent;
        public final float offsetX;
        public final float offsetY;
        public final int fontWidth;
        public final int u;
        public final int v;
        /** range 区间（无则 start>end）。 */
        public final char rangeStart;
        public final char rangeEnd;
        /** match 正则（无则 null）。 */
        public final Pattern match;
        /** 全局字体路径（只有 ttf 键的规则也会带出来）。 */
        public final String ttf;
        public final List<String> permissions;
        public final boolean permissionsContains;
        public final String alias;
        public final boolean single;
        public final String color;
        public final String style;
        public final String alignment;

        private Rule(String id, String texture, boolean gif, double fps,
                     int width, int height, int ascent, float offsetX, float offsetY,
                     int fontWidth, int u, int v, char rangeStart, char rangeEnd,
                     Pattern match, String ttf, List<String> permissions,
                     boolean permissionsContains, String alias, boolean single,
                     String color, String style, String alignment) {
            this.id = id;
            this.texture = texture;
            this.gif = gif;
            this.fps = fps;
            this.width = width;
            this.height = height;
            this.ascent = ascent;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.fontWidth = fontWidth;
            this.u = u;
            this.v = v;
            this.rangeStart = rangeStart;
            this.rangeEnd = rangeEnd;
            this.match = match;
            this.ttf = ttf;
            this.permissions = permissions;
            this.permissionsContains = permissionsContains;
            this.alias = alias;
            this.single = single;
            this.color = color;
            this.style = style;
            this.alignment = alignment;
        }

        /** 是否带可绘制的贴图（纯 ttf / 纯权限规则为 false）。 */
        public boolean hasBitmap() {
            return texture != null && !texture.isEmpty();
        }

        /** 是否区间规则。 */
        public boolean isRange() {
            return rangeStart <= rangeEnd;
        }

        /** 区间字符数量。 */
        public int rangeCount() {
            return isRange() ? (rangeEnd - rangeStart + 1) : 0;
        }
    }

    /**
     * 查询结果：已就绪的绘制描述（不可变）。
     *
     * 贴图尺寸与帧切分在这里只给「声明」，具体 UV 由渲染层结合真实贴图尺寸算：
     * 本层不依赖任何客户端资源系统，拿不到像素宽高。
     * totalFrames &gt; 1 时按 frameIndex/totalFrames 横向切片（range 等分图）；
     * gif 为 true 时帧序号由贴图自身的播放时钟驱动，frameIndex 仅作起始值。
     */
    public static final class Glyph {
        public final String texture;
        public final int width;
        public final int height;
        public final int ascent;
        public final float offsetX;
        public final float offsetY;
        public final int fontWidth;
        public final int u;
        public final int v;
        public final int frameIndex;
        public final int totalFrames;
        public final boolean gif;

        private Glyph(String texture, int width, int height, int ascent,
                      float offsetX, float offsetY, int fontWidth,
                      int u, int v, int frameIndex, int totalFrames, boolean gif) {
            this.texture = texture;
            this.width = width;
            this.height = height;
            this.ascent = ascent;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.fontWidth = fontWidth;
            this.u = u;
            this.v = v;
            this.frameIndex = frameIndex;
            this.totalFrames = totalFrames;
            this.gif = gif;
        }
    }

    /** 本地/远程尺寸缺省：远程贴图不写尺寸时取原生帧尺寸，本地贴图维持 9。 */
    private static final int DEFAULT_SIZE = 9;

    private final List<Rule> rules;
    private final Map<Integer, Rule> singles;
    private final List<Rule> ranges;
    private final List<Rule> regexes;
    private final String defaultTtf;

    private FontRules(List<Rule> rules, Map<Integer, Rule> singles,
                      List<Rule> ranges, List<Rule> regexes, String defaultTtf) {
        this.rules = rules;
        this.singles = singles;
        this.ranges = ranges;
        this.regexes = regexes;
        this.defaultTtf = defaultTtf;
    }

    /**
     * 解析服务端下发的规则表（规则 id → 字段体 YAML）。
     *
     * 同时兼容「整包多规则」形态（值本身又是一层 map，本地自定义文件就是这个样子），
     * 判定与历史实现一致：只有在 id 为空或本地覆盖标记、且确实出现嵌套 map 时才当整包，
     * 免得把普通规则体里的嵌套字段误展开。
     */
    public static FontRules parse(Map<String, String> ruleBodies) {
        List<Rule> all = new ArrayList<Rule>();
        Map<Integer, Rule> singles = new LinkedHashMap<Integer, Rule>();
        List<Rule> ranges = new ArrayList<Rule>();
        List<Rule> regexes = new ArrayList<Rule>();
        String ttf = null;
        if (ruleBodies != null) {
            for (Map.Entry<String, String> entry : ruleBodies.entrySet()) {
                String id = entry.getKey();
                String body = entry.getValue();
                Map<String, Object> parsed;
                try {
                    parsed = new YamlParser().parse(body);
                } catch (Exception broken) {
                    continue; // 单条写坏就丢这一条，别连累整份配置
                }
                if (parsed == null || parsed.isEmpty()) {
                    continue;
                }
                boolean local = id == null || "_local".equals(id) || "_local_override".equals(id);
                if (local && hasNestedMap(parsed)) {
                    for (Map.Entry<String, Object> nested : parsed.entrySet()) {
                        if (!(nested.getValue() instanceof Map)) {
                            continue;
                        }
                        Rule r = readRule(String.valueOf(nested.getKey()), castMap(nested.getValue()));
                        if (r != null) {
                            all.add(r);
                            if (r.ttf != null) {
                                ttf = r.ttf;
                            }
                            index(r, singles, ranges, regexes);
                        }
                    }
                    continue;
                }
                Rule r = readRule(id, parsed);
                if (r != null) {
                    all.add(r);
                    if (r.ttf != null) {
                        ttf = r.ttf;
                    }
                    index(r, singles, ranges, regexes);
                }
            }
        }
        return new FontRules(Collections.unmodifiableList(all),
                Collections.unmodifiableMap(singles),
                Collections.unmodifiableList(ranges),
                Collections.unmodifiableList(regexes), ttf);
    }

    /** 规则体里出现嵌套 map：整包形态的特征。 */
    private static boolean hasNestedMap(Map<String, Object> parsed) {
        for (Object v : parsed.values()) {
            if (v instanceof Map) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object o) {
        return (Map<String, Object>) o;
    }

    /** 规则入索引：单字符进精确表，range/match 各进各的。 */
    private static void index(Rule r, Map<Integer, Rule> singles,
                              List<Rule> ranges, List<Rule> regexes) {
        if (!r.hasBitmap()) {
            return;
        }
        if (r.isRange()) {
            ranges.add(r);
            return;
        }
        if (r.match != null) {
            regexes.add(r);
            return;
        }
        if (r.id != null && r.id.length() == 1) {
            singles.put((int) r.id.charAt(0), r);
        }
    }

    /** 单个规则体的字段读取；没贴图也没 ttf 时返回 null（不占索引）。 */
    private static Rule readRule(String id, Map<String, Object> body) {
        if (body == null) {
            return null;
        }
        String ttf = text(body.get("ttf"));
        String texture = firstText(body, "texture", "path", "字形");
        Object gifRaw = body.get("gif");
        boolean gifFlag = false;
        if (texture == null && gifRaw != null && !(gifRaw instanceof Boolean)) {
            // gif 写成路径时等同 texture
            String g = text(gifRaw);
            if (g != null && !isBooleanWord(g)) {
                texture = g;
            }
        }
        if (gifRaw instanceof Boolean) {
            gifFlag = (Boolean) gifRaw;
        }
        if (texture != null) {
            texture = texture.replace('\\', '/').trim();
            if (texture.isEmpty()) {
                texture = null;
            }
        }
        if (ttf != null) {
            ttf = ttf.replace('\\', '/').trim();
            if (ttf.isEmpty()) {
                ttf = null;
            }
        }
        if (texture == null && ttf == null) {
            return null;
        }
        boolean gif = gifFlag || (texture != null && texture.toLowerCase().endsWith(".gif"));
        double fps = number(body.get("fps"), 0.0);
        // 远程贴图不写尺寸取原生帧尺寸（0 表示「交给贴图自己说」），本地维持 9
        boolean remote = texture != null
                && (texture.startsWith("http://") || texture.startsWith("https://"));
        int defSize = remote ? 0 : DEFAULT_SIZE;
        int width = (int) number(body.get("width"), defSize);
        int height = (int) number(body.get("height"), defSize);
        int ascent = (int) number(body.get("ascent"), 0);
        float offsetX = (float) number(first(body, "offsetX", "xOffset"), 0.0);
        float offsetY = (float) number(first(body, "offsetY", "yOffset"), 0.0);
        int fontWidth = (int) number(body.get("fontWidth"), width);
        int u = (int) number(body.get("u"), 0);
        int v = (int) number(body.get("v"), 0);

        char rangeStart = 1;
        char rangeEnd = 0;
        String range = text(body.get("range"));
        if (range != null) {
            String rg = range.trim();
            if (rg.length() == 3 && rg.charAt(1) == '-' && rg.charAt(0) <= rg.charAt(2)) {
                rangeStart = rg.charAt(0);
                rangeEnd = rg.charAt(2);
            }
        }
        Pattern match = null;
        String matchText = text(body.get("match"));
        if (matchText != null && !matchText.trim().isEmpty() && rangeStart > rangeEnd) {
            try {
                match = Pattern.compile(matchText.trim());
            } catch (PatternSyntaxException broken) {
                match = null; // 正则写坏跳过这一条，别的规则照常
            }
        }
        List<String> permissions = textList(body.get("permissions"));
        boolean permissionsContains = bool(body.get("permissionsContains"), true);
        return new Rule(id, texture, gif, fps, width, height, ascent,
                offsetX, offsetY, fontWidth, u, v, rangeStart, rangeEnd, match, ttf,
                permissions, permissionsContains, text(body.get("alias")),
                bool(body.get("single"), false), text(body.get("color")),
                text(body.get("style")), text(body.get("alignment")));
    }

    /**
     * 按字符查询替换字形；无命中返回 null（调用方回退原版字形）。
     * 顺序与历史行为一致：精确单字 → 区间 → 正则。
     */
    public Glyph glyphFor(int codePoint) {
        Rule r = singles.get(codePoint);
        if (r != null) {
            return toGlyph(r, 0, 1);
        }
        for (Rule range : ranges) {
            if (codePoint >= range.rangeStart && codePoint <= range.rangeEnd) {
                return toGlyph(range, codePoint - range.rangeStart, range.rangeCount());
            }
        }
        for (Rule regex : regexes) {
            if (regex.match.matcher(String.valueOf((char) codePoint)).matches()) {
                return toGlyph(regex, 0, 1);
            }
        }
        return null;
    }

    private static Glyph toGlyph(Rule r, int frameIndex, int totalFrames) {
        return new Glyph(r.texture, r.width, r.height, r.ascent, r.offsetX, r.offsetY,
                r.fontWidth, r.u, r.v, frameIndex, totalFrames, r.gif);
    }

    /** 全部规则（含纯 ttf / 权限规则），按声明顺序。 */
    public List<Rule> rules() {
        return rules;
    }

    /** 规则条数。 */
    public int ruleCount() {
        return rules.size();
    }

    /** 是否有任何替换能力（含只配了全局字体的情形）。 */
    public boolean hasAny() {
        return !rules.isEmpty();
    }

    /** 全局字体路径（FontConfig 的 ttf 键）；没有返回 null。 */
    public String defaultTtf() {
        return defaultTtf;
    }

    /** 精确替换的字符集合（字形层注册用；区间与正则不在此列）。 */
    public Set<Integer> explicitCodePoints() {
        return Collections.unmodifiableSet(new LinkedHashSet<Integer>(singles.keySet()));
    }

    /** 出现过的贴图路径，按声明顺序去重（跨版本一致性自检用）。 */
    public List<String> textures() {
        Set<String> seen = new LinkedHashSet<String>();
        for (Rule r : rules) {
            if (r.hasBitmap()) {
                seen.add(r.texture);
            }
        }
        return Collections.unmodifiableList(new ArrayList<String>(seen));
    }

    /** 区间规则展开出的字符数量总和（自检用）。 */
    public int rangeCodePoints() {
        int n = 0;
        for (Rule r : ranges) {
            n += r.rangeCount();
        }
        return n;
    }

    /** 正则规则条数（自检用）。 */
    public int regexCount() {
        return regexes.size();
    }

    private static boolean isBooleanWord(String s) {
        return "true".equalsIgnoreCase(s) || "false".equalsIgnoreCase(s);
    }

    private static Object first(Map<String, Object> m, String... keys) {
        for (String k : keys) {
            Object v = m.get(k);
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    private static String firstText(Map<String, Object> m, String... keys) {
        return text(first(m, keys));
    }

    private static String text(Object o) {
        if (o == null) {
            return null;
        }
        String s = String.valueOf(o);
        return s.isEmpty() ? null : s;
    }

    /** 数值：YAML 里可能是数字，也可能是带引号的字符串；都认，认不出用 fallback。 */
    private static double number(Object o, double fallback) {
        if (o instanceof Number) {
            return ((Number) o).doubleValue();
        }
        if (o instanceof String) {
            try {
                return Double.parseDouble(((String) o).trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static boolean bool(Object o, boolean fallback) {
        if (o instanceof Boolean) {
            return (Boolean) o;
        }
        if (o instanceof String) {
            if ("true".equalsIgnoreCase((String) o)) {
                return true;
            }
            if ("false".equalsIgnoreCase((String) o)) {
                return false;
            }
        }
        return fallback;
    }

    private static List<String> textList(Object o) {
        if (o == null) {
            return Collections.emptyList();
        }
        if (o instanceof List) {
            List<String> out = new ArrayList<String>();
            for (Object item : (List<?>) o) {
                if (item != null) {
                    out.add(String.valueOf(item));
                }
            }
            return Collections.unmodifiableList(out);
        }
        String single = String.valueOf(o).trim();
        if (single.isEmpty()) {
            return Collections.emptyList();
        }
        if (J8.isBlank(single)) {
            return Collections.emptyList();
        }
        return Collections.singletonList(single);
    }
}
