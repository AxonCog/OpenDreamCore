package com.opendreamcore.client.visual;

import com.opendreamcore.visual.ItemView;
import com.opendreamcore.visual.MatchSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 物品特效：ItemEffect 规则给指定物品挂个发光/描边。渲染口画完图标
 * 再叠一层效果；不认识的 effect 值就啥也不画，别把渲染炸了。
 *
 * 特效形态（渲染口按类型画）：
 *   glow    发光——多层半透明扩散层（物品图标后置画）
 *   outline 描边——轮廓高亮
 */
public final class VisualItemEffects {

    /** 一条已解析的特效条目。 */
    public record EffectEntry(MatchSpec spec, int priority, String effect, int color) {
    }

    private static volatile List<EffectEntry> entries = List.of();
    private static volatile boolean loaded;

    private VisualItemEffects() {
    }

    /** 规则集变化后重解析（handleVisualRules 入库时调用）。 */
    public static void refresh() {
        List<EffectEntry> out = new ArrayList<>();
        for (String yaml : ClientVisualStore.get().rulesOf("ItemEffect").values()) {
            try {
                Map<String, Object> ir = new com.opendreamcore.config.YamlParser().parse(yaml);
                if (ir == null) {
                    continue;
                }
                int prio = 0;
                for (Map.Entry<String, Object> e : ir.entrySet()) {
                    if (!(e.getValue() instanceof Map<?, ?> m)) {
                        continue;
                    }
                    @SuppressWarnings("unchecked")
                    Map<String, Object> rule = (Map<String, Object>) m;
                    var spec = MatchSpec.parse(rule);
                    String effect = str(rule.get("effect"));
                    if (effect == null || effect.isBlank()) {
                        continue;
                    }
                    int priority = rule.get("priority") instanceof Number n ? n.intValue() : prio;
                    int color = 0xFFFFD54F;
                    if (rule.get("color") instanceof String cs) {
                        try {
                            color = (int) Long.parseLong(cs.replace("#", "").replace("0x", ""), 16) | 0xFF000000;
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    out.add(new EffectEntry(spec, priority, effect.trim().toLowerCase(), color));
                    prio = Math.max(prio, priority) + 1;
                }
            } catch (Exception ignored) {
                // 单文件坏了静默跳过——特效缺失只是不闪，不该炸渲染
            }
        }
        out.sort((a, b) -> Integer.compare(b.priority(), a.priority()));
        entries = List.copyOf(out);
        loaded = true;
    }

    /** 按物品类型找特效；无命中 null。类型名去命名空间前缀匹配。 */
    public static EffectEntry effectFor(String itemType) {
        ensureLoaded();
        String t = stripNs(itemType);
        for (EffectEntry e : entries) {
            if (e.spec().matches(new ItemView(t, "", List.of(), Map.of()))) {
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

    /** 规则失效标记（规则重新入库时置位）。 */
    public static void invalidate() {
        loaded = false;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static String stripNs(String itemType) {
        String s = itemType == null ? "" : itemType.trim().toLowerCase();
        int colon = s.indexOf(':');
        return colon >= 0 ? s.substring(colon + 1) : s.split(" ")[0];
    }
}