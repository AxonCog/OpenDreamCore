package com.opendreamcore.client.visual;

import com.opendreamcore.visual.ItemView;
import com.opendreamcore.visual.MatchSpec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 头顶名牌（远古版）：实体名字渲染时问一句"该不该盖自定义名牌"，命中
 * 返回样式（背景/边框/文字色/字号/贴图），没规则就显示原版名字。
 * Java8，跟现代端 VisualNameTags 一个意思。
 */
public final class LegacyVisualNameTags {

    /** 名牌样式，缺省有兜底，渲染端不用判空。 */
    public static final class TagStyle {
        public final int bgColor;
        public final int bgRadius;
        public final int borderColor;
        public final int borderWidth;
        public final int textColor;
        public final int fontSize;
        public final String texture;

        public TagStyle(int bgColor, int bgRadius, int borderColor, int borderWidth,
                        int textColor, int fontSize, String texture) {
            this.bgColor = bgColor;
            this.bgRadius = bgRadius;
            this.borderColor = borderColor;
            this.borderWidth = borderWidth;
            this.textColor = textColor;
            this.fontSize = fontSize;
            this.texture = texture;
        }

        public static TagStyle fallback() {
            return new TagStyle(0x80000000, 3, 0xFFFFFFFF, 1, 0xFFFFFFFF, 9, null);
        }
    }

    private static final class Entry {
        final MatchSpec spec;
        final String name;
        final TagStyle style;

        Entry(MatchSpec spec, String name, TagStyle style) {
            this.spec = spec;
            this.name = name;
            this.style = style;
        }
    }

    private static volatile List<Entry> entries = new ArrayList<Entry>();
    private static volatile boolean loaded;

    private LegacyVisualNameTags() {
    }

    /** 规则集变化后重解析（LegacyVisualSkins.apply 入库时调用）。 */
    public static void refresh() {
        List<Entry> out = new ArrayList<Entry>();
        for (String yaml : LegacyVisualSkins.rulesOf("HeadTag").values()) {
            try {
                Map<String, Object> ir = new com.opendreamcore.config.YamlParser().parse(yaml);
                if (ir == null) {
                    continue;
                }
                String name = ir.get("name") == null ? null : String.valueOf(ir.get("name"));
                out.add(new Entry(MatchSpec.parse(ir), name, parseStyle(ir)));
            } catch (Exception ignored) {
                // 单文件坏了跳过
            }
        }
        entries = out;
        loaded = true;
    }

    /** 实体名字渲染时查询：命中返回样式，未命中 null。 */
    public static TagStyle styleFor(String entityType, String entityName) {
        ensureLoaded();
        String t = stripNs(entityType);
        for (Entry e : entries) {
            if (e.name != null && !e.name.isEmpty() && (entityName == null || !entityName.contains(e.name))) {
                continue;
            }
            if (e.spec.matches(new ItemView(t, "", new java.util.ArrayList<String>(),
                    new java.util.LinkedHashMap<String, Object>()))) {
                return e.style;
            }
        }
        return null;
    }

    private static TagStyle parseStyle(Map<String, Object> rule) {
        Map<String, Object> bg = mapOf(rule.get("背景"));
        Map<String, Object> tx = mapOf(rule.get("文字"));
        Object texture = rule.get("texture");
        if (texture == null) {
            texture = rule.get("贴图");
        }
        return new TagStyle(
                argb(bg.get("color"), 0x80000000),
                (int) num(bg.get("圆角"), 3),
                argb(bg.get("边框色"), 0xFFFFFFFF),
                (int) num(bg.get("边框宽"), 1),
                argb(tx.get("color"), 0xFFFFFFFF),
                (int) num(tx.get("字号"), 9),
                texture == null ? null : String.valueOf(texture));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapOf(Object o) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        if (o instanceof Map) {
            for (Map.Entry<?, ?> e : ((Map<?, ?>) o).entrySet()) {
                out.put(String.valueOf(e.getKey()), e.getValue());
            }
        }
        return out;
    }

    private static int argb(Object o, int fallback) {
        if (o == null) {
            return fallback;
        }
        try {
            String s = String.valueOf(o).trim();
            if (s.startsWith("#")) {
                s = s.substring(1);
            } else {
                s = s.replace("0x", "");
            }
            return (int) Long.parseLong(s, 16);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static double num(Object o, double fallback) {
        return o instanceof Number ? ((Number) o).doubleValue() : fallback;
    }

    private static String stripNs(String type) {
        String s = type == null ? "" : type.trim().toLowerCase();
        int colon = s.indexOf(':');
        return colon >= 0 ? s.substring(colon + 1) : s.split(" ")[0];
    }

    private static void ensureLoaded() {
        if (!loaded) {
            refresh();
        }
    }

    /** 规则失效标记。 */
    public static void invalidate() {
        loaded = false;
    }
}
