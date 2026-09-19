package com.opendreamcore.ui.theme;

import com.opendreamcore.util.J8;

import java.util.Map;

/**
 * 一条样式规则 = 选择器 + 声明表 + 级联权重 + 声明序（同权重后者胜），
 * 可选携带响应式条件——带条件的规则不参与编译期合并，
 * 由布局引擎在每次重算时按当前窗口尺寸现判现用。
 */
public final class StyleRule {

    private final StyleSelector selector;
    private final Map<String, Object> declarations;
    private final int specificity;
    private final int order;          // 主题内声明顺序（稳定排序依据）
    private final String sourceName;  // 所属主题名（诊断用）
    private final MediaQuery media;   // null = 无条件，永远参与

    public StyleRule(StyleSelector selector, Map<String, Object> declarations,
                     int order, String sourceName) {
        this(selector, declarations, order, sourceName, null);
    }

    public StyleRule(StyleSelector selector, Map<String, Object> declarations,
                     int order, String sourceName, MediaQuery media) {
        this.selector = selector;
        this.declarations = J8.mapCopy(declarations);
        this.specificity = selector.specificity();
        this.order = order;
        this.sourceName = sourceName;
        this.media = media;
    }

    public StyleSelector selector() {
        return selector;
    }

    public Map<String, Object> declarations() {
        return declarations;
    }

    public int specificity() {
        return specificity;
    }

    public int order() {
        return order;
    }

    public String sourceName() {
        return sourceName;
    }

    /** 响应式条件；null 表示无条件。 */
    public MediaQuery media() {
        return media;
    }

    /** 规则是否带状态伪类（带状态的进状态覆盖层，不参与基础合并）。 */
    public boolean hasState() {
        return selector.hasState();
    }

    @Override
    public String toString() {
        return sourceName + "::" + (media == null ? "" : media + " :: ") + selector;
    }
}
