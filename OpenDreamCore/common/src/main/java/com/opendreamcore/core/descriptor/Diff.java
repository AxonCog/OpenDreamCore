package com.opendreamcore.core.descriptor;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** 规则集合的差异：消失的 → revoke，新增或内容变了的 → apply。 */
public final class Diff {

    private final Map<String, Map<String, Object>> removed;
    private final Map<String, Map<String, Object>> addedOrChanged;

    private Diff(Map<String, Map<String, Object>> removed,
                 Map<String, Map<String, Object>> addedOrChanged) {
        this.removed = removed;
        this.addedOrChanged = addedOrChanged;
    }

    /** 消失的规则（key 为规则 id）。 */
    public Map<String, Map<String, Object>> removed() {
        return removed;
    }

    /** 新增或内容变化的规则（key 为规则 id）。 */
    public Map<String, Map<String, Object>> addedOrChanged() {
        return addedOrChanged;
    }

    /** 没有任何变化（可以做「无变化就不重挂」的短路）。 */
    public boolean isEmpty() {
        return removed.isEmpty() && addedOrChanged.isEmpty();
    }

    /** 计算差异；null 一律按空集合处理。 */
    public static Diff of(Map<String, Map<String, Object>> oldRules,
                          Map<String, Map<String, Object>> newRules) {
        Map<String, Map<String, Object>> a = oldRules == null
                ? Collections.<String, Map<String, Object>>emptyMap() : oldRules;
        Map<String, Map<String, Object>> b = newRules == null
                ? Collections.<String, Map<String, Object>>emptyMap() : newRules;
        Map<String, Map<String, Object>> gone = new LinkedHashMap<String, Map<String, Object>>();
        Map<String, Map<String, Object>> changed = new LinkedHashMap<String, Map<String, Object>>();
        for (Map.Entry<String, Map<String, Object>> e : a.entrySet()) {
            if (!b.containsKey(e.getKey())) {
                gone.put(e.getKey(), e.getValue());
            }
        }
        for (Map.Entry<String, Map<String, Object>> e : b.entrySet()) {
            Map<String, Object> prev = a.get(e.getKey());
            if (prev == null || !Objects.equals(prev, e.getValue())) {
                changed.put(e.getKey(), e.getValue());
            }
        }
        return new Diff(gone, changed);
    }
}
