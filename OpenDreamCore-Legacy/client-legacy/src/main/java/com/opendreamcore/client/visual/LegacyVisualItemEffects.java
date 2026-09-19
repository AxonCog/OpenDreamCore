package com.opendreamcore.client.visual;

import com.opendreamcore.visual.ItemView;
import com.opendreamcore.visual.MatchSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 物品特效（远古版）：ItemEffect 规则按物品类型匹配特效，画完图标
 * 再叠发光/描边。Java8，跟现代端 VisualItemEffects 一个意思。
 */
public final class LegacyVisualItemEffects {

    /** 特效条目：类型匹配 + 特效名 + 颜色。 */
    public static final class Effect {
        public final MatchSpec spec;
        public final String effect;
        public final int color;

        public Effect(MatchSpec spec, String effect, int color) {
            this.spec = spec;
            this.effect = effect;
            this.color = color;
        }
    }

    private static volatile List<Effect> entries = new ArrayList<Effect>();
    private static volatile boolean loaded;

    private LegacyVisualItemEffects() {
    }

    /** 规则集变化后重解析（LegacyVisualSkins.apply 入库时调用）。 */
    public static void refresh() {
        List<Effect> out = new ArrayList<Effect>();
        for (String yaml : LegacyVisualSkins.rulesOf("ItemEffect").values()) {
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
                    Object fx = rule.get("effect");
                    if (fx == null) {
                        continue;
                    }
                    out.add(new Effect(MatchSpec.parse(rule), String.valueOf(fx).trim().toLowerCase(),
                            argb(rule.get("color"), 0xFFD54F)));
                }
            } catch (Exception ignored) {
                // 单文件坏了跳过，少个特效不影响大局
            }
        }
        entries = out;
        loaded = true;
    }

    /** 按物品类型找特效；无命中 null。 */
    public static Effect effectFor(String itemType) {
        ensureLoaded();
        String t = stripNs(itemType);
        for (Effect e : entries) {
            if (e.spec.matches(new ItemView(t, "", new java.util.ArrayList<String>(),
                    new java.util.LinkedHashMap<String, Object>()))) {
                return e;
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

    private static String stripNs(String itemType) {
        String s = itemType == null ? "" : itemType.trim().toLowerCase();
        int colon = s.indexOf(':');
        return colon >= 0 ? s.substring(colon + 1) : s.split(" ")[0];
    }
}
