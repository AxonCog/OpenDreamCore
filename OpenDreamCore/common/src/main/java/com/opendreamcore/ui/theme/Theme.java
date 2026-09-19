package com.opendreamcore.ui.theme;

import com.opendreamcore.util.J8;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 一个解析完成的主题。
 * 内部按最右段建立桶索引（tag/class/id/通配），匹配时先圈候选再全量校验，
 * 避免规则数 × 元素数的全乘扫描。
 */
public final class Theme {

    public static final Theme EMPTY = new Theme("empty", J8.map(), J8.list());

    private final String name;
    private final Map<String, Object> vars;
    private final List<StyleRule> rules;

    // 桶索引：最右段 kind:value -> 规则
    private final Map<String, List<StyleRule>> byBucket = new LinkedHashMap<>();
    private final List<StyleRule> universal = new ArrayList<>();

    public Theme(String name, Map<String, Object> vars, List<StyleRule> rules) {
        this.name = name == null ? "unnamed" : name;
        this.vars = vars == null ? J8.map() : Collections.unmodifiableMap(new LinkedHashMap<>(vars));
        this.rules = rules == null ? J8.list() : J8.listCopy(rules);
        index();
    }

    private void index() {
        for (StyleRule rule : rules) {
            String key = rule.selector().bucketKey();
            if ("*".equals(key)) {
                universal.add(rule);
            } else {
                byBucket.computeIfAbsent(key, k -> new ArrayList<>()).add(rule);
            }
        }
    }

    public String name() {
        return name;
    }

    public Map<String, Object> vars() {
        return vars;
    }

    public List<StyleRule> rules() {
        return rules;
    }

    /**
     * 圈定可能与目标节点相关的候选规则（含通配与全部桶回退）。
     * 候选仍需 StyleMatcher 全链校验。
     */
    public Collection<StyleRule> candidates(StyleNode node) {
        Set<StyleRule> out = new LinkedHashSet<>();
        addBucket(out, "tag:" + node.type().toLowerCase());
        for (String cls : node.classes()) {
            addBucket(out, "class:" + cls.toLowerCase());
        }
        if (node.id() != null && !J8.isBlank(node.id())) {
            addBucket(out, "id:" + node.id().toLowerCase());
        }
        out.addAll(universal);
        return out;
    }

    private void addBucket(Set<StyleRule> out, String key) {
        List<StyleRule> bucket = byBucket.get(key);
        if (bucket != null) {
            out.addAll(bucket);
        }
    }
}
