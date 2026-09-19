package com.opendreamcore.client.visual;

import com.opendreamcore.visual.MatchSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 头顶名牌（HeadTag）的渲染数据。HeadTag 规则有俩消费面：一个转世界面板
 * （VisualWorldPages），另一个是这里——实体名字渲染时问一声"该不该盖
 * 自定义名牌、长啥样"，命中就盖，没规则就显示原版名字。
 *
 * 规则形态：
 *   entity: minecraft:zombie   # 匹配实体类型（去命名空间）
 *   name: 老王                 # 可选：名字前缀匹配
 *   distance: 16               # 可选：最大显示距离
 *   背景: { color: "#00000080", 圆角: 4, 边框色: "#FFFFFF", 边框宽: 1 }
 *   文字: { color: "#FFFFFF", 字号: 8 }
 */
public final class VisualNameTags {

    /** 名牌样式；没配的颜色字号就用这套默认。 */
    public record TagStyle(int bgColor, int bgRadius, int borderColor, int borderWidth,
                           int textColor, int fontSize, String texture) {

        public static TagStyle fallback() {
            return new TagStyle(0x80000000, 3, 0xFFFFFFFF, 1, 0xFFFFFFFF, 9, null);
        }
    }

    private static final class Entry {
        final MatchSpec spec;
        final String name;
        final double distance;
        final TagStyle style;

        Entry(MatchSpec spec, String name, double distance, TagStyle style) {
            this.spec = spec;
            this.name = name;
            this.distance = distance;
            this.style = style;
        }
    }

    private static volatile List<Entry> entries = List.of();
    private static volatile boolean loaded;

    private VisualNameTags() {
    }

    /** 规则集变化后重解析（handleVisualRules 入库时调用）。 */
    public static void refresh() {
        List<Entry> out = new ArrayList<>();
        for (String yaml : ClientVisualStore.get().rulesOf("HeadTag").values()) {
            try {
                Map<String, Object> ir = new com.opendreamcore.config.YamlParser().parse(yaml);
                if (ir == null) {
                    continue;
                }
                MatchSpec spec = MatchSpec.parse(ir);
                String name = str(ir.get("name"));
                double distance = num(ir.get("distance"), 0);
                TagStyle style = parseStyle(ir);
                out.add(new Entry(spec, name, distance, style));
            } catch (Exception ignored) {
                // 单文件坏了静默跳过——名牌丑总比炸渲染强
            }
        }
        entries = List.copyOf(out);
        loaded = true;
    }

    /** 实体名字渲染时查询：命中返回样式，未命中 null（原版名牌照旧）。 */
    public static TagStyle styleFor(String entityType, String entityName) {
        ensureLoaded();
        String t = stripNs(entityType);
        for (Entry e : entries) {
            if (e.distance > 0 && e.distance < 1) {
                continue; // 距离判定由渲染端传入，此处 distance<=0 视作不设限
            }
            if (e.name != null && !e.name.isEmpty() && (entityName == null || !entityName.contains(e.name))) {
                continue;
            }
            if (e.spec.matches(new com.opendreamcore.visual.ItemView(t, "", List.of(), Map.of()))) {
                return e.style;
            }
        }
        return null;
    }

    private static TagStyle parseStyle(Map<String, Object> rule) {
        Map<String, Object> bg = mapOf(rule.get("背景"));
        Map<String, Object> tx = mapOf(rule.get("文字"));
        return new TagStyle(
                argb(bg.get("color"), 0x80000000),
                (int) num(bg.get("圆角"), 3),
                argb(bg.get("边框色"), 0xFFFFFFFF),
                (int) num(bg.get("边框宽"), 1),
                argb(tx.get("color"), 0xFFFFFFFF),
                (int) num(tx.get("字号"), 9),
                str(firstOf(rule, "texture", "贴图")));
    }

    private static Map<String, Object> mapOf(Object o) {
        if (o instanceof Map<?, ?> m) {
            Map<String, Object> out = new java.util.LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                out.put(String.valueOf(e.getKey()), e.getValue());
            }
            return out;
        }
        return Map.of();
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

    private static int argb(Object o, int fallback) {
        if (o == null) {
            return fallback;
        }
        String s = String.valueOf(o).trim();
        try {
            String hex = s.startsWith("#") ? s.substring(1) : s.replace("0x", "");
            return (int) Long.parseLong(hex, 16);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static double num(Object o, double fallback) {
        return o instanceof Number n ? n.doubleValue() : fallback;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
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