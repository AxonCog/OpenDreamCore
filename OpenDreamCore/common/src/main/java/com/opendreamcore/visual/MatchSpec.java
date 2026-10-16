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
                if (sameItemId(item.type(), id)) {
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
            if (v == null || !valueEquals(v, e.getValue())) {
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

    /** ID 比较保留完整 namespace，同时兼容既有的 minecraft:item 简写配置。 */
    private static boolean sameItemId(String actual, String expected) {
        String a = normalizeId(actual);
        String e = normalizeId(expected);
        if (a.equals(e)) {
            return true;
        }
        int ac = a.indexOf(':');
        int ec = e.indexOf(':');
        if (ac < 0 && ec >= 0) {
            return a.equals(e.substring(ec + 1)) && "minecraft".equals(e.substring(0, ec));
        }
        if (ec < 0 && ac >= 0) {
            return e.equals(a.substring(ac + 1)) && "minecraft".equals(a.substring(0, ac));
        }
        return false;
    }

    private static String normalizeId(String id) {
        return id == null ? "" : id.trim().toLowerCase(java.util.Locale.ROOT);
    }

    @SuppressWarnings("unchecked")
    private static boolean valueEquals(Object actual, Object expected) {
        if (actual instanceof Map<?, ?> am && expected instanceof Map<?, ?> em) {
            if (am.size() != em.size()) return false;
            for (Map.Entry<?, ?> e : em.entrySet()) {
                if (!am.containsKey(e.getKey()) || !valueEquals(am.get(e.getKey()), e.getValue())) return false;
            }
            return true;
        }
        if (actual instanceof List<?> al && expected instanceof List<?> el) {
            if (al.size() != el.size()) return false;
            for (int i = 0; i < al.size(); i++) if (!valueEquals(al.get(i), el.get(i))) return false;
            return true;
        }
        return String.valueOf(actual).equals(String.valueOf(expected));
    }

    /**
     * 从规则 IR 解析 MatchSpec。
     * 约定键：match / name / lore / id / nbt / regex；
     * 另兼容 lore_contains 与 name_contains 作为显式长名写法。
     */
    public static MatchSpec parse(Map<String, Object> ir) {
        if (ir == null) {
            return new MatchSpec(null, null, null, J8.list(), J8.map(), null);
        }
        Map<String, Object> source = ir;
        Object nested = ir.get("match");
        if (nested instanceof Map<?, ?> nm) {
            @SuppressWarnings("unchecked") Map<String, Object> merged = new java.util.LinkedHashMap<>((Map<String, Object>) nm);
            merged.putAll(ir);
            source = merged;
        }
        Object match = source.get("match") instanceof Map<?, ?> ? null : firstOf(source, "match");
        Object name = firstOf(source, "name", "name_contains");
        Object lore = firstOf(source, "lore", "lore_contains");
        Object id = source.get("id");
        Object nbtRaw = source.get("nbt");
        Object regex = source.get("regex");
        List<String> ids = new ArrayList<>();
        if (id != null) {
            for (String part : String.valueOf(id).split(",")) {
                if (!J8.isBlank(part)) ids.add(part.trim());
            }
        }
        @SuppressWarnings("unchecked") Map<String, Object> nbt = nbtRaw instanceof Map<?, ?> m
                ? (Map<String, Object>) m : J8.map();
        return new MatchSpec(str(match), str(name), str(lore), ids, nbt, str(regex));
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
