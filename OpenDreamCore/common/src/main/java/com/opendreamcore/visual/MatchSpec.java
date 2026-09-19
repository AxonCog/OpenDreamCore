package com.opendreamcore.visual;

import com.opendreamcore.util.J8;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 统一匹配规范。
 *
 * 五个视觉系统（ItemIcon/ItemEffect/ArmorLayer/ItemTip/HeadTag 的名称过滤）
 * 共用这一套匹配语义，学一次全都会：
 *
 *   match: 屠龙            # 一行走天下：显示名或 lore 包含即命中
 *   name: "〖传说〗屠龙"     # 可选：仅显示名包含
 *   lore: 吸血              # 可选：任意一行 lore 包含
 *   id: diamond_sword       # 可选：物品类型（逗号分隔多个，任一命中）
 *   nbt: {CustomModelData: 7000}   # 可选：NBT 键值全等
 *   regex: "^神剑.*"         # 可选：显示名正则
 *
 * 多个键同时出现 = 全部满足才命中（AND）；单键内多值用逗号 = 任一命中（OR）。
 */
public record MatchSpec(String anyText, String name, String lore,
                        List<String> ids, Map<String, Object> nbt, String regex) {

    public MatchSpec {
        anyText = emptyToNull(anyText);
        name = emptyToNull(name);
        lore = emptyToNull(lore);
        regex = emptyToNull(regex);
        ids = ids == null || ids.isEmpty() ? J8.list() : J8.listCopy(ids);
        nbt = nbt == null || nbt.isEmpty() ? J8.map() : J8.mapCopy(nbt);
    }

    /** 是否完全没写匹配条件——空规范对任何物品都命中。 */
    public boolean isEmpty() {
        return anyText == null && name == null && lore == null
                && ids.isEmpty() && nbt.isEmpty() && regex == null;
    }

    private static final java.util.regex.Pattern COLOR_CODE =
            java.util.regex.Pattern.compile("[§&][0-9A-Fa-fk-orK-OR]");

    /** 去掉 MC 色码（§x / &x）——让服主写匹配词时不用抄色码。 */
    public static String stripColor(String s) {
        return s == null ? null : COLOR_CODE.matcher(s).replaceAll("");
    }

    /** 判断物品是否命中。 */
    public boolean matches(ItemView item) {
        if (isEmpty()) {
            return true;
        }
        // 包含匹配对色码不敏感：原文命中或去色码后命中都算
        if (anyText != null && !containsAny(item.name(), item.lore(), anyText)) {
            return false;
        }
        if (name != null && !containsLoose(item.name(), name)) {
            return false;
        }
        if (lore != null && item.lore().stream().noneMatch(l -> containsLoose(l, lore))) {
            return false;
        }
        if (!ids.isEmpty()) {
            boolean hit = false;
            for (String id : ids) {
                if (item.type().equalsIgnoreCase(id.trim())) {
                    hit = true;
                    break;
                }
            }
            if (!hit) {
                return false;
            }
        }
        for (Map.Entry<String, Object> e : nbt.entrySet()) {
            Object v = item.nbt().get(e.getKey());
            if (v == null || !String.valueOf(v).equals(String.valueOf(e.getValue()))) {
                return false;
            }
        }
        if (regex != null) {
            try {
                if (!item.name().matches(regex)) {
                    return false;
                }
            } catch (RuntimeException ex) {
                return false; // 非法正则按不命中处理，不炸调用方
            }
        }
        return true;
    }

    /**
     * 从规则 IR 解析 MatchSpec。
     * 约定键：match / name / lore / id / nbt / regex；
     * 另兼容 lore_contains 与 name_contains 作为显式长名写法。
     */
    public static MatchSpec parse(Map<String, Object> ir) {
        Object match = firstOf(ir, "match");
        Object name = firstOf(ir, "name", "name_contains");
        Object lore = firstOf(ir, "lore", "lore_contains");
        Object id = ir.get("id");
        Object nbtRaw = ir.get("nbt");
        Object regex = ir.get("regex");

        // id 支持逗号分隔多值
        List<String> ids = new ArrayList<>();
        if (id != null) {
            for (String part : String.valueOf(id).split(",")) {
                if (!J8.isBlank(part)) {
                    ids.add(part.trim());
                }
            }
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> nbt = nbtRaw instanceof Map<?, ?> m ? (Map<String, Object>) m : J8.map();

        return new MatchSpec(
                str(match), str(name), str(lore), ids, nbt, str(regex));
    }

    private static Object firstOf(Map<String, Object> ir, String... keys) {
        for (String k : keys) {
            Object v = ir.get(k);
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static String emptyToNull(String s) {
        return s == null || J8.isBlank(s) ? null : s;
    }

    /** 宽松包含：原文包含，或双方去色码后包含。 */
    private static boolean containsLoose(String haystack, String needle) {
        if (haystack == null || needle == null) {
            return false;
        }
        if (haystack.contains(needle)) {
            return true;
        }
        return stripColor(haystack).contains(stripColor(needle));
    }

    private static boolean containsAny(String name, List<String> lore, String needle) {
        if (name != null && name.contains(needle)) {
            return true;
        }
        return lore.stream().anyMatch(l -> l.contains(needle));
    }
}
