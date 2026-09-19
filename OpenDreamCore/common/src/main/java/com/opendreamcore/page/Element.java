package com.opendreamcore.page;

import com.opendreamcore.util.J8;

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

    // 下面两个字段走 setter 注入而不进构造器——构造器已有 9 处调用点，
    // 能不动就不动。class 由解析层写入，style 由主题管线写入。
    private List<String> classes = J8.list();
    private com.opendreamcore.ui.theme.ElementStyle style;

    public Element(String id, String type, Layout layout, Map<String, Object> props,
                   String visibleWhen, String enabledWhen, Map<String, String> actions,
                   List<Element> children, String parent) {
        if (id == null || J8.isBlank(id)) {
            throw new IllegalArgumentException("元素 id 不能为空");
        }
        if (type == null || J8.isBlank(type)) {
            throw new IllegalArgumentException("元素缺少 type: " + id);
        }
        this.id = id;
        this.type = type;
        this.layout = layout;
        this.props = props == null ? new LinkedHashMap<>() : props;
        this.visibleWhen = visibleWhen;
        this.enabledWhen = enabledWhen;
        this.actions = actions == null ? new LinkedHashMap<>() : actions;
        this.children = children == null ? new ArrayList<>() : J8.listCopy(children);
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

    /** 样式类列表（页面里 class: "a b" 空格分隔）；无则空列表。 */
    public List<String> classes() {
        return classes;
    }

    /** 仅由配置解析层写入。 */
    public void setClasses(List<String> classes) {
        this.classes = classes == null ? J8.list() : J8.listCopy(classes);
    }

    /** 主题管线产物（状态覆盖层 + 过渡声明）；无主题命中时为 null。 */
    public com.opendreamcore.ui.theme.ElementStyle style() {
        return style;
    }

    /** 仅由主题应用器写入。 */
    public void setStyle(com.opendreamcore.ui.theme.ElementStyle style) {
        this.style = style;
    }
}
