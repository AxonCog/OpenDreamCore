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

        /** 尺寸协商入口。
         *  width/height = **显示尺寸**（GUI 像素/世界单位）；frameW/frameH = **贴图内取图尺寸**。
         *  两者解耦：以前 frameW 直接镜像 width，配置里一写 width 就会把大图裁成左上角一块。
         *  规则：宽高都给 = 自定义原样；只给一个 = 按原生帧等比补另一个；
         *  都不给 = 原生帧尺寸（贴图未就绪回退 9×9，就绪后自动拿到真实值并等比）。 */
        public CharGlyph effective() {
            int nw = 0;
            int nh = 0;
            if (frameW <= 0 || frameH <= 0) {
                // 取图尺寸没写死才需要查原生帧；全写死的配置（含本地 png 老配置）零查询开销
                var si = com.opendreamcore.client.resources.LooseResourceLoader.sheetOf(texture);
                nw = si != null ? si.frameW() : 0;
                nh = si != null ? si.frameH() : 0;
            }
            return negotiated(nw, nh);
        }

        /** 按原生帧尺寸（nativeW/nativeH，0=未知）协商最终显示尺寸与取图尺寸。 */
        public CharGlyph negotiated(int nativeW, int nativeH) {
            int w = width;
            int h = height;
            if (w <= 0 && h <= 0) {
                w = nativeW > 0 ? nativeW : 9;
                h = nativeH > 0 ? nativeH : 9;
            } else if (w > 0 && h <= 0) {
                // 只给宽：高等比
                h = (nativeW > 0 && nativeH > 0)
                        ? Math.max(1, (int) Math.round((double) w * nativeH / nativeW))
                        : (nativeH > 0 ? nativeH : w);
            } else if (h > 0 && w <= 0) {
                // 只给高：宽等比
                w = (nativeW > 0 && nativeH > 0)
                        ? Math.max(1, (int) Math.round((double) h * nativeW / nativeH))
                        : (nativeW > 0 ? nativeW : h);
            }
            int fw = fontWidth > 0 ? fontWidth : w;
            int srcW = frameW > 0 ? frameW : (nativeW > 0 ? nativeW : Math.max(w, 1));
            int srcH = frameH > 0 ? frameH : (nativeH > 0 ? nativeH : Math.max(h, 1));
            return new CharGlyph(texture, Math.max(w, 1), Math.max(h, 1), Math.max(fw, 1),
                    u, v, srcW, srcH);
        }
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
        // 规则源：服务端下发 + 本地自定义（本地 visual/FontConfig.yml 作为 _local_override 最后合并，
        // 解析时覆盖服务端同名键——连服时用户本地 url 字形仍生效）。
        Map<String, String> fontRules = new java.util.LinkedHashMap<>(
                ClientVisualStore.get().rulesOf("FontConfig"));
        try {
            java.nio.file.Path localFc = net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath()
                    .resolve("OpenDreamCore").resolve("visual").resolve("FontConfig.yml");
            if (java.nio.file.Files.isRegularFile(localFc)) {
                fontRules.put("_local_override",
                        java.nio.file.Files.readString(localFc, java.nio.charset.StandardCharsets.UTF_8));
            }
        } catch (Throwable ignored) {
        }
        // 服务端 VisualRuleManager 把 FontConfig.yml 每条规则拆成一个 entry（id=键名，value=规则字段体），
        // 这里必须按 id + 扁平 body 解析——把 body 当“整包多规则”解析会因全是标量而全空（历史大坑）。
        for (Map.Entry<String, String> ruleEntry : fontRules.entrySet()) {
            String ruleId = ruleEntry.getKey();
            String yaml = ruleEntry.getValue();
            try {
                Map<String, Object> rule = new com.opendreamcore.config.YamlParser().parse(yaml);
                if (rule == null) {
                    continue;
                }
                // 兼容“整包多规则”形态（值又是 Map 时回退旧逻辑）
                if ((ruleId == null || "_local".equals(ruleId) || "_local_override".equals(ruleId))
                        && !rule.isEmpty()) {
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
        syncReplaceFonts(s, rs);
        // 远程 url 纹理预取：规则一下发就后台下载，渲染时 lookup 直接命中（替换即时生效）
        for (CharGlyph g : s.values()) {
            com.opendreamcore.client.remote.RemotePrefetch.prefetch(g.texture());
        }
        for (RangeGlyph g : rs) {
            com.opendreamcore.client.remote.RemotePrefetch.prefetch(g.texture());
        }
        for (RegexGlyph g : xs) {
            com.opendreamcore.client.remote.RemotePrefetch.prefetch(g.texture());
        }
    }

    /** 字形层同步：单字符 + 区间展开注册（正则类无法枚举命中字符，drawInBatch 路径仍处理）。 */
    private static void syncReplaceFonts(Map<Character, CharGlyph> s, List<RangeGlyph> rs) {
        com.opendreamcore.client.visual.ReplaceFontProvider.clear();
        try {
            for (Map.Entry<Character, CharGlyph> e : s.entrySet()) {
                com.opendreamcore.client.visual.ReplaceFontProvider.register(
                        e.getKey(), e.getValue().texture(), e.getValue().fontWidth(), e.getValue().height());
            }
            for (RangeGlyph r : rs) {
                for (int i = 0; i < r.count(); i++) {
                    com.opendreamcore.client.visual.ReplaceFontProvider.register(
                            r.start() + i, r.texture(), r.fontWidth(), r.height());
                }
            }
        } catch (Exception ignored) {
            // 字形层同步失败不影响 GlyphRenderer 路径
        }
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
        // 不限定 .gif 后缀：远程 url 图床常带查询串/无后缀（内容仍是 gif），
        // 非 gif 时 setGifFps 查不到播放器只会把值存起来，无副作用
        if (fpsRaw != null) {
            try {
                double fps = Double.parseDouble(String.valueOf(fpsRaw).trim());
                com.opendreamcore.client.resources.LooseResourceLoader.setGifFps(texture, fps);
            } catch (Exception ignored) {
                // 帧率写崩了这一条按默认帧间隔走，别连累别的规则
            }
        }
        // URL 贴图（http/https）未写尺寸 = 原生帧尺寸（0 交给 effective 协商）；
        // 本地贴图维持 9×9 缺省（老配置全是本地小帧图，不能突然放大炸屏）
        // 尺寸缺省一律 0（未指定）：交给 effective()/negotiated() 用贴图原生帧尺寸补齐（等比）。
        // 以前本地贴图硬缺省 9×9 —— 只写 height 时宽度恒为 9（不等比）；
        // 贴图未就绪时 negotiated 仍回退 9×9，行为不会突然炸屏。
        int defSize = 0;
        int width = num(rule.get("width"), defSize);
        int height = num(rule.get("height"), defSize);
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
            // frameW/frameH 传 0：取图尺寸交 effective() 用原生帧解析（width/height 只当显示尺寸）
            s.put(ruleId.charAt(0), new CharGlyph(texture, width, height,
                    fontWidth, u, v, 0, 0));
            return true;
        }
        return false;
    }

    /** 按字符查替换条目（已做尺寸协商）；无命中 null。range 帧宽按贴图实际横向等分动态算。 */
    public static CharGlyph glyphFor(char c) {
        CharGlyph g = singles.get(c);
        if (g != null) {
            return g.effective();
        }
        for (RangeGlyph r : ranges) {
            if (c >= r.start() && c < r.start() + r.count()) {
                int idx = c - r.start();
                var sz = com.opendreamcore.client.resources.LooseResourceLoader.sizeOf(r.texture());
                int sheetW = sz != null ? sz.width() : 0;
                int sheetH = sz != null ? sz.height() : 0;
                // 区间贴图横向等分：单帧原生宽 = 整图宽 / 字符数；原生高 = 整图高
                int nativeW = (sheetW > 0 && r.count() > 0) ? sheetW / r.count() : r.width();
                int nativeH = sheetH > 0 ? sheetH : r.height();
                int frameW = nativeW > 0 ? nativeW : r.width();
                return new CharGlyph(r.texture(), r.width(), r.height(), r.fontWidth(),
                        idx * frameW, 0, frameW, nativeH)
                        .negotiated(nativeW, nativeH);
            }
        }
        for (RegexGlyph x : regexes) {
            if (x.pattern().matcher(String.valueOf(c)).matches()) {
                // frameW/frameH 传 0：取图尺寸交 effective() 用原生帧解析
                return new CharGlyph(x.texture(), x.width(), x.height(), x.fontWidth(), 0, 0, 0, 0)
                        .effective();
            }
        }
        return null;
    }

    /** 是否有任何替换能力（快路径短路用；含全局 TTF 字体）。 */
    public static boolean hasAny() {
        return !singles.isEmpty() || !ranges.isEmpty() || !regexes.isEmpty()
                || (defaultTtf != null && !defaultTtf.isBlank());
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