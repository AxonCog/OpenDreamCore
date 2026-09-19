package com.opendreamcore.client.visual;

import com.opendreamcore.visual.ItemView;
import com.opendreamcore.visual.MatchSpec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 盔甲层贴图覆写。干法很直白：不碰盔甲渲染那坨，只盯着"找贴图路径"
 * 那一步，人家要去加载原版盔甲皮之前，把路径换成咱自定义的贴图。
 * 渲染怎么画是原版的事，咱只管把路径塞对，冲突最小、好维护。
 *
 * 规则形态：
 *   minecraft:zombie:          # 实体类型（去命名空间匹配）
 *     chest: custom/chest.png  # 按槽位给贴图
 *     helmet: custom/helm.png
 *   minecraft:player:
 *     layer: custom/player.png # 或者一个 layer 全槽共用
 */
public final class VisualArmorLayer {

    /** 一条盔甲覆写条目：实体匹配 + 槽位→贴图。 */
    public record ArmorEntry(MatchSpec spec, int priority, Map<String, String> layers) {
    }

    private static volatile List<ArmorEntry> entries = List.of();
    private static volatile boolean loaded;

    private VisualArmorLayer() {
    }

    /** 规则集变化后重解析（handleVisualRules 入库时调用）。 */
    public static void refresh() {
        List<ArmorEntry> out = new ArrayList<>();
        for (String yaml : ClientVisualStore.get().rulesOf("ArmorLayer").values()) {
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
                    Map<String, String> layers = new LinkedHashMap<>();
                    for (String slot : new String[]{"helmet", "chest", "legs", "feet", "layer"}) {
                        Object v = rule.get(slot);
                        if (v != null) {
                            layers.put(slot, String.valueOf(v));
                        }
                    }
                    if (layers.isEmpty()) {
                        continue;
                    }
                    int priority = rule.get("priority") instanceof Number n ? n.intValue() : prio;
                    out.add(new ArmorEntry(spec, priority, layers));
                    prio = Math.max(prio, priority) + 1;
                }
            } catch (Exception ignored) {
                // 单文件坏了静默跳过
            }
        }
        out.sort((a, b) -> Integer.compare(b.priority(), a.priority()));
        entries = List.copyOf(out);
        loaded = true;
    }

    /** 按实体类型+槽位取覆写贴图；无命中 null。 */
    public static String textureFor(String entityType, String slot) {
        ensureLoaded();
        String t = stripNs(entityType);
        for (ArmorEntry e : entries) {
            if (e.spec().matches(new ItemView(t, "", List.of(), Map.of()))) {
                String direct = e.layers().get(slot);
                if (direct != null) {
                    return direct;
                }
                // layer 键作为全槽回退
                String shared = e.layers().get("layer");
                if (shared != null) {
                    return shared;
                }
            }
        }
        return null;
    }

    /** 全局模式（无实体上下文的版本用）：不挑实体，按槽位直接取首条命中
     * 的 layer 键（或全槽回退）。layer 键不匹配具体实体时可用。 */
    public static String textureForGlobal(String slot) {
        ensureLoaded();
        for (ArmorEntry e : entries) {
            String direct = e.layers().get(slot);
            if (direct != null) {
                return direct;
            }
            String shared = e.layers().get("layer");
            if (shared != null) {
                return shared;
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