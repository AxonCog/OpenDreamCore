package com.opendreamcore.client.visual;

import com.opendreamcore.visual.ItemView;
import com.opendreamcore.visual.MatchSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 物品图标覆写（远古版）：ItemIcon 规则按物品类型匹配出自定义贴图路径，
 * RenderItem 的 mixin 画图标前问一句，命中就盖自定义图。
 * Java8，跟现代端 VisualItemSkins 一个意思。
 */
public final class LegacyVisualItemIcons {

    private static final class Entry {
        final MatchSpec spec;
        final String texture;
        final double scale;

        Entry(MatchSpec spec, String texture, double scale) {
            this.spec = spec;
            this.texture = texture;
            this.scale = scale;
        }
    }

    private static volatile List<Entry> entries = new ArrayList<Entry>();
    private static volatile boolean loaded;

    private LegacyVisualItemIcons() {
    }

    /** 规则集变化后重解析（LegacyVisualSkins.apply 入库时调用）。 */
    public static void refresh() {
        List<Entry> out = new ArrayList<Entry>();
        for (String yaml : LegacyVisualSkins.rulesOf("ItemIcon").values()) {
            try {
                Map<String, Object> ir = new com.opendreamcore.config.YamlParser().parse(yaml);
                if (ir == null) {
                    continue;
                }
                for (Map.Entry<String, Object> e : ir.entrySet()) {
                    if (!(e.getValue() instanceof Map)) {
                        continue;
                    }
                    @SuppressWarnings("unchecked")
                    Map<String, Object> rule = (Map<String, Object>) e.getValue();
                    MatchSpec spec = MatchSpec.parse(rule);
                    Object tex = rule.get("texture");
                    if (tex == null) {
                        tex = rule.get("icon");
                    }
                    if (tex == null) {
                        continue;
                    }
                    out.add(new Entry(spec, String.valueOf(tex), num(rule.get("scale"), 1)));
                }
            } catch (Exception ignored) {
                // 单文件坏了跳过，少个图标不影响大局
            }
        }
        entries = out;
        loaded = true;
    }

    /** 按物品类型找覆写贴图；无命中 null。类型去命名空间匹配。 */
    public static String textureFor(String itemType) {
        ensureLoaded();
        String t = stripNs(itemType);
        for (Entry e : entries) {
            if (e.spec.matches(new ItemView(t, "", new java.util.ArrayList<String>(),
                    new java.util.LinkedHashMap<String, Object>()))) {
                return e.texture;
            }
        }
        return null;
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

    private static double num(Object o, double fallback) {
        return o instanceof Number ? ((Number) o).doubleValue() : fallback;
    }

    private static String stripNs(String itemType) {
        String s = itemType == null ? "" : itemType.trim().toLowerCase();
        int colon = s.indexOf(':');
        return colon >= 0 ? s.substring(colon + 1) : s.split(" ")[0];
    }
}
