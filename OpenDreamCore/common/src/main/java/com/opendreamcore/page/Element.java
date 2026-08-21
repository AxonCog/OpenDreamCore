package com.opendreamcore.page;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 页面元素（组件）。有 type 即元素；属性进 props（组件 schema 解释）。
 * 支持多级嵌套（children）与显式挂父（parent）。
 */
public final class Element {

    private final String id;
    private final String type;
    private final Layout layout;
    private final Map<String, Object> props;
    private final String visibleWhen;
    private final String enabledWhen;
    private final Map<String, String> actions;
    private final List<Element> children;
    private final String parent;

    public Element(String id, String type, Layout layout, Map<String, Object> props,
                   String visibleWhen, String enabledWhen, Map<String, String> actions,
                   List<Element> children, String parent) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("元素 id 不能为空");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("元素缺少 type: " + id);
        }
        this.id = id;
        this.type = type;
        this.layout = layout;
        this.props = props == null ? new LinkedHashMap<>() : props;
        this.visibleWhen = visibleWhen;
        this.enabledWhen = enabledWhen;
        this.actions = actions == null ? new LinkedHashMap<>() : actions;
        this.children = children == null ? new ArrayList<>() : List.copyOf(children);
        this.parent = parent;
    }

    public String id() {
        return id;
    }

    public String type() {
        return type;
    }

    public Layout layout() {
        return layout;
    }

    public Map<String, Object> props() {
        return props;
    }

    public String visibleWhen() {
        return visibleWhen;
    }

    public String enabledWhen() {
        return enabledWhen;
    }

    public Map<String, String> actions() {
        return actions;
    }

    public List<Element> children() {
        return children;
    }

    /** 显式挂父（children 嵌套时自动设置）。 */
    public String parent() {
        return parent;
    }
}
