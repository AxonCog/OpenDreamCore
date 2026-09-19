package com.opendreamcore.client.visual;

import com.opendreamcore.visual.ItemView;
import com.opendreamcore.visual.MatchSpec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 盔甲层贴图覆写（远古版）：不碰盔甲渲染，在"找盔甲皮路径"那步把结果
 * 换成自定义贴图。规则按实体类型匹配，按槽位给路径（layer 全槽回退）。
 */
public final class LegacyVisualArmorLayer {

    private static final class Entry {
        final MatchSpec spec;
        final Map<String, String> layers;

        Entry(MatchSpec spec, Map<String, String> layers) {
            this.spec = spec;
            this.layers = layers;
        }
    }

    private static volatile List<Entry> entries = new ArrayList<Entry>();
    private static volatile boolean loaded;

    private LegacyVisualArmorLayer() {
    }

    /** 规则集变化后重解析（LegacyVisualSkins.apply 入库时调用）。 */
    public static void refresh() {
        List<Entry> out = new ArrayList<Entry>();
        for (String yaml : LegacyVisualSkins.rulesOf("ArmorLayer").values()) {
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
                    Map<String, String> layers = new LinkedHashMap<String, String>();
                    for (String slot : new String[]{"helmet", "chest", "legs", "feet", "layer"}) {
                        Object v = rule.get(slot);
                        if (v != null) {
                            layers.put(slot, String.valueOf(v));
                        }
                    }
                    if (!layers.isEmpty()) {
                        out.add(new Entry(MatchSpec.parse(rule), layers));
                    }
                }
            } catch (Exception ignored) {
                // 单文件坏了跳过
            }
        }
        entries = out;
        loaded = true;
    }

    /** 按实体类型+槽位取覆写贴图；无命中 null。 */
    public static String textureFor(String entityType, String slot) {
        ensureLoaded();
        String t = stripNs(entityType);
        for (Entry e : entries) {
            if (e.spec.matches(new ItemView(t, "", new java.util.ArrayList<String>(),
                    new java.util.LinkedHashMap<String, Object>()))) {
                String direct = e.layers.get(slot);
                if (direct != null) {
                    return direct;
                }
                return e.layers.get("layer");
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

    private static String stripNs(String entityType) {
        String s = entityType == null ? "" : entityType.trim().toLowerCase();
        int colon = s.indexOf(':');
        return colon >= 0 ? s.substring(colon + 1) : s.split(" ")[0];
    }
}
