package com.opendreamcore.client.visual;

import com.opendreamcore.visual.KeyCombo;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * KeyConfig 客户端触发器。
 *
 * 客户端负责把"修饰键状态 + 主键名"解析成组合串并上报；
 * 服务端按规则执行命令/脚本。只对被规则引用的组合上报，无关按键零流量。
 */
public final class ClientKeyConfigTrigger {

    /** 上次解析的规则集指纹（内容哈希）→ 解析结果。规则集没变就不再逐行重解析。 */
    private static volatile Map<String, Set<String>> cachedPrimaries = Map.of();
    private static volatile int cachedFingerprint = 0;

    private ClientKeyConfigTrigger() {
    }

    /**
     * 从 KeyConfig 规则集提取"主键名 → 组合串集合"。
     * 两种形态都认：
     *   新形态：ID 作键，值里 keys 列表（元素可为组合键 Ctrl+左键 这种）；
     *   老形态：键名即组合串（如 C+LEFT_SHIFT+左键）。
     * 组合串一律经 KeyCombo.canonical 归一（修饰键排序 + 别名收敛），上报就报规范串。
     * 结果按规则集内容指纹缓存——按键事件每次都来查，不能每次都扫 YAML。
     */
    public static Map<String, Set<String>> collectPrimaries(Map<String, String> rulesYaml) {
        int fp = rulesYaml.hashCode();
        if (fp == cachedFingerprint) {
            return cachedPrimaries;
        }
        Map<String, Set<String>> out = new LinkedHashMap<>();
        for (String yaml : rulesYaml.values()) {
            try {
                Map<String, Object> ir = new com.opendreamcore.config.YamlParser().parse(yaml);
                if (ir == null) {
                    continue;
                }
                for (Map.Entry<String, Object> e : ir.entrySet()) {
                    if (e.getValue() instanceof Map<?, ?> m && m.containsKey("keys")) {
                        // 新形态：ID + keys 列表，组合键写在元素里
                        for (String keyStr : asStringList(m.get("keys"))) {
                            addPrimary(out, KeyCombo.canonical(keyStr));
                        }
                    } else {
                        // 老形态：键名即组合串
                        addPrimary(out, KeyCombo.canonical(e.getKey()));
                    }
                }
            } catch (Exception ignored) {
            }
        }
        cachedPrimaries = java.util.Collections.unmodifiableMap(out);
        cachedFingerprint = fp;
        return cachedPrimaries;
    }

    /** 单条组合入表：主键 = 组合串末段。 */
    private static void addPrimary(Map<String, Set<String>> out, String combo) {
        KeyCombo kc = new KeyCombo(combo);
        if (kc.isEmpty()) {
            return;
        }
        String primary = null;
        for (String k : kc.keys()) {
            primary = k; // 末段即主键
        }
        if (primary != null) {
            out.computeIfAbsent(primary, k -> new HashSet<>()).add(combo);
        }
    }

    private static java.util.List<String> asStringList(Object o) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (o instanceof java.util.List<?> l) {
            for (Object x : l) {
                out.add(String.valueOf(x));
            }
        } else if (o != null) {
            out.add(String.valueOf(o));
        }
        return out;
    }

    /**
     * 匹配：主键相等且规则声明的修饰键全部处于激活态。
     *
     * 返回：命中的组合串；无命中 null
     */
    public static String match(Map<String, Set<String>> primaries, String keyName,
                               Set<String> activeModifiers) {
        Set<String> combos = primaries.get(keyName);
        if (combos == null) {
            return null;
        }
        for (String combo : combos) {
            KeyCombo kc = new KeyCombo(combo);
            if (modsSatisfied(kc.keys(), activeModifiers)) {
                return combo;
            }
        }
        return null;
    }

    /**
     * 修饰键全等匹配：规则声明的修饰键组 == 当前激活的修饰键组。
     * 同一物理修饰键有多种别名（C/CTRL/CONTROL/LEFT_CTRL/LEFT_CONTROL…），按组归一后比较，
     * 数数量会误判——"C+左键"（1 组）和"C+SHIFT+左键"（2 组）必须分得清。
     * 规则没声明修饰键时，要求当前没有任何修饰键激活（plain 左键 不吃 SHIFT+左键）。
     */
    public static boolean modsSatisfied(Set<String> ruleKeys, Set<String> active) {
        return modifierGroups(ruleKeys).equals(modifierGroups(active));
    }

    /** 键名集合 → 修饰键规范组集合（CTRL/SHIFT/ALT；非修饰键忽略）。归一表在 KeyCombo，别处不重复维护。 */
    private static java.util.Set<String> modifierGroups(Set<String> names) {
        java.util.Set<String> out = new HashSet<>();
        for (String k : names) {
            String g = KeyCombo.modifierGroup(k);
            if (g != null) {
                out.add(g);
            }
        }
        return out;
    }
}
