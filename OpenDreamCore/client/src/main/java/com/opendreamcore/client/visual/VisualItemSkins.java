package com.opendreamcore.client.visual;

import com.opendreamcore.visual.ItemView;
import com.opendreamcore.visual.MatchSpec;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 物品皮肤渲染器。
 *
 * 把 ItemIcon 规则解析成有序条目（priority 降序），按物品类型匹配出
 * 应显示的自定义贴图路径——绘制口用它覆盖原版物品图标。
 * 贴图本体经 LooseResourceLoader（resourcepacks/OpenDreamCore/）或
 * GifPlayer（OpenDreamCore/ 下，.gif 自动播帧）加载，两条路都已存在。
 */
public final class VisualItemSkins {

    /** 一条已解析的皮肤条目。 */
    public record SkinEntry(MatchSpec spec, int priority,
                            String texture, Double frameMs,
                            double scale, boolean held, boolean center) {
    }

    private static volatile List<SkinEntry> entries = List.of();
    private static volatile boolean loaded;

    private VisualItemSkins() {
    }

    /** 规则集变化后重解析（handleVisualRules 入库时调用）。 */
    public static void refresh() {
        List<SkinEntry> out = new ArrayList<>();
        for (String yaml : com.opendreamcore.client.visual.ClientVisualStore.get()
                .rulesOf("ItemIcon").values()) {
            out.addAll(parseYaml(yaml));
        }
        out.sort((a, b) -> Integer.compare(b.priority(), a.priority()));
        entries = List.copyOf(out);
        loaded = true;
    }

    /**
     * 按物品类型找皮肤贴图路径；无命中 null。
     * 类型名会去掉命名空间前缀（minecraft:diamond_sword → diamond_sword）再匹配。
     */
    public static String textureFor(String itemType) {
        ensureLoaded();
        String t = stripNs(itemType);
        for (SkinEntry e : entries) {
            if (e.spec().matches(new ItemView(t, "", List.of(), Map.of()))) {
                return e.texture();
            }
        }
        return null;
    }

    /** 命中条目的换帧间隔（gif 用）；无声明 null。单次遍历（渲染热路径，逐帧都会查）。 */
    public static Double frameMsFor(String itemType) {
        String t = stripNs(itemType);
        for (SkinEntry e : entries) {
            if (e.spec().matches(new ItemView(t, "", List.of(), Map.of()))) {
                return e.frameMs();
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

    private static List<SkinEntry> parseYaml(String yamlText) {
        List<SkinEntry> out = new ArrayList<>();
        try {
            Map<String, Object> ir = new com.opendreamcore.config.YamlParser().parse(yamlText);
            if (ir == null) {
                return out;
            }
            int prio = 0;
            for (Map.Entry<String, Object> e : ir.entrySet()) {
                if (!(e.getValue() instanceof Map<?, ?> m)) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> rule = (Map<String, Object>) m;
                var spec = MatchSpec.parse(rule);
                String texture = str(firstOf(rule, "texture"));
                if (texture == null || texture.isBlank()) {
                    continue;
                }
                int priority = num(rule.get("priority"), prio);
                Double frameMs = rule.get("frame_ms") instanceof Number n ? n.doubleValue() : null;
                out.add(new SkinEntry(spec, priority, texture, frameMs,
                        num(rule.get("scale"), 1), bool(rule.get("held")),
                        bool(rule.get("center"))));
                prio = Math.max(prio, priority) + 1;
            }
        } catch (Exception ignored) {
            // 单文件坏了静默跳过——皮肤缺失只是难看，不该炸渲染
        }
        return out;
    }

    private static Object firstOf(Map<String, Object> m, String key) {
        Object direct = m.get(key);
        if (direct != null) {
            return direct;
        }
        // match 内嵌块形态兼容
        Object nested = m.get("match");
        return nested instanceof Map<?, ?> nm ? nm.get(key) : null;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static int num(Object o, int fallback) {
        return o instanceof Number n ? n.intValue() : fallback;
    }

    private static double num(Object o, double fallback) {
        return o instanceof Number n ? n.doubleValue() : fallback;
    }

    private static boolean bool(Object o) {
        return Boolean.parseBoolean(String.valueOf(o));
    }

    private static String stripNs(String itemType) {
        String s = itemType == null ? "" : itemType.trim().toLowerCase();
        int colon = s.indexOf(':');
        return colon >= 0 ? s.substring(colon + 1) : s.split(" ")[0];
    }
}
