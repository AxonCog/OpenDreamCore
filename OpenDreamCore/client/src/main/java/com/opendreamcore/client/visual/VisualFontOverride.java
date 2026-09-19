package com.opendreamcore.client.visual;

import com.opendreamcore.visual.ItemView;
import com.opendreamcore.visual.MatchSpec;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * FontConfig 系统客户端消费器（文字的「小跟班」）。
 *
 * 玩法：匹配到某段文字，就在它右边贴个小装饰字形——称号、角标、警号都行：
 *   血量称号:
 *     match:
 *       name: "^[区]服主$"        # 正则或包含匹配（MatchSpec 统一裁决）
 *     overlay: "◆"               # 叠加的字形（单字符也行短串也行）
 *     color: "&c"                # 可选，字形颜色（& 色码；不写就跟正文一个色）
 *     priority: 10               # 可选，撞车时谁说了算（大者赢）
 *
 * 挂点：TextElements.drawText 是全部页面文字的独门小道，俺就蹲这条路上——
 * 等文字画完，查一眼有没有命中的字形，有就贴右侧。规则表是空的？
 * 直接直通 return，连个 if 的开销都省给你。
 * 匹配拿 ItemView(name=文本) 喂 MatchSpec，跟物品图标同一套规矩
 * （色码先扒、正则/包含统一打架），零新匹配器。
 */
public final class VisualFontOverride {

    /** 一条已解析的叠加条目。 */
    public record GlyphEntry(MatchSpec spec, int priority, String overlay, int color) {
    }

    private static volatile List<GlyphEntry> entries = List.of();

    private VisualFontOverride() {
    }

    /** 规则集变化后重解析（handleVisualRules 入库时调用）。 */
    public static void refresh() {
        List<GlyphEntry> out = new ArrayList<>();
        int prio = 0;
        for (String yaml : ClientVisualStore.get().rulesOf("FontConfig").values()) {
            try {
                Map<String, Object> ir = new com.opendreamcore.config.YamlParser().parse(yaml);
                if (ir == null) {
                    continue;
                }
                for (Map.Entry<String, Object> e : ir.entrySet()) {
                    if (!(e.getValue() instanceof Map<?, ?> m)) {
                        continue;
                    }
                    @SuppressWarnings("unchecked")
                    Map<String, Object> rule = (Map<String, Object>) m;
                    String overlay = str(firstOf(rule, "overlay", "字形", "前缀"));
                    if (overlay == null || overlay.isBlank()) {
                        continue;
                    }
                    // match 块或平铺键两种形态都认（与 ItemIcon 的 firstOf 习惯一致）
                    Map<String, Object> specSrc = rule.get("match") instanceof Map<?, ?> mm
                            ? new java.util.LinkedHashMap<>((Map<String, Object>) mm)
                            : rule;
                    // 叠加字形不参与匹配，防止 overlay 键被当成 name 兜底
                    specSrc.remove("overlay");
                    specSrc.remove("字形");
                    specSrc.remove("前缀");
                    var spec = MatchSpec.parse(specSrc);
                    int priority = num(rule.get("priority"), prio);
                    out.add(new GlyphEntry(spec, priority, overlay, colorOf(rule.get("color"))));
                    prio = Math.max(prio, priority) + 1;
                }
            } catch (Exception ignored) {
                // 单文件坏了静默跳过——字形缺失只是少个装饰，不该炸渲染
            }
        }
        out.sort(Comparator.comparingInt(GlyphEntry::priority).reversed());
        entries = List.copyOf(out);
    }

    /**
     * 按文本找叠加字形；无命中 null。
     * 文本喂 ItemView(name=text)：MatchSpec 的 name 规则（含正则）统一裁决。
     */
    public static GlyphEntry overlayFor(String text) {
        List<GlyphEntry> list = entries;
        if (list.isEmpty() || text == null || text.isEmpty()) {
            return null;
        }
        ItemView view = new ItemView("text", text, List.of(), Map.of());
        for (GlyphEntry e : list) {
            if (e.spec().matches(view)) {
                return e;
            }
        }
        return null;
    }

    /** 是否有规则（快路径短路用）。 */
    public static boolean hasAny() {
        return !entries.isEmpty();
    }

    // 内部小工具

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

    /** &/§ 色码串转 ARGB（白底黑字兜底）。空返回 -1 = 跟随正文颜色。 */
    private static int colorOf(Object o) {
        if (o == null) {
            return -1;
        }
        String s = String.valueOf(o).replace("&", "§").replace("§", "");
        // 纯十六进制形态：#RRGGBB / RRGGBB
        String hex = s.startsWith("#") ? s.substring(1) : s;
        if (hex.matches("[0-9a-fA-F]{6}")) {
            try {
                return 0xFF000000 | Integer.parseInt(hex, 16);
            } catch (NumberFormatException ignored) {
            }
        }
        // mc 色码：0-9a-f 映射到标准调色板
        return switch (s.toLowerCase()) {
            case "0" -> 0xFF000000;
            case "1" -> 0xFF0000AA;
            case "2" -> 0xFF00AA00;
            case "3" -> 0xFF00AAAA;
            case "4" -> 0xFFAA0000;
            case "5" -> 0xFFAA00AA;
            case "6" -> 0xFFFFAA00;
            case "7" -> 0xFFAAAAAA;
            case "8" -> 0xFF555555;
            case "9" -> 0xFF5555FF;
            case "a" -> 0xFF55FF55;
            case "b" -> 0xFF55FFFF;
            case "c" -> 0xFFFF5555;
            case "d" -> 0xFFFF55FF;
            case "e" -> 0xFFFFFF55;
            case "f" -> 0xFFFFFFFF;
            default -> -1;
        };
    }
}