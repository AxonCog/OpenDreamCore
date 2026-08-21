package com.opendreamcore.page;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 页面模型（YAML → ConfigIR → 本模型）。
 * 顶层：match 触发 + 变量（无 type 即变量）+ 元素（有 type）。
 */
public final class Page {

    private final String id;
    private final String title;
    private final Match match;
    private final DisplayMode displayMode;
    private final Map<String, Object> variables;
    private final List<Element> elements;
    private final Map<String, String> functions;
    private final Map<String, Object> options;

    public Page(String id, String title, Match match, DisplayMode displayMode,
                Map<String, Object> variables, List<Element> elements,
                Map<String, String> functions, Map<String, Object> options) {
        this.id = id;
        this.title = title;
        this.match = match;
        this.displayMode = displayMode;
        this.variables = variables == null ? new LinkedHashMap<>() : variables;
        this.elements = elements == null ? new ArrayList<>() : new ArrayList<>(elements);
        this.functions = functions == null ? new LinkedHashMap<>() : functions;
        this.options = options == null ? new LinkedHashMap<>() : options;
    }

    /** 可空（不写默认取文件名）。 */
    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    /** 可空（页面也可由命令/插件主动打开）。 */
    public Match match() {
        return match;
    }

    public DisplayMode displayMode() {
        return displayMode;
    }

    public Map<String, Object> variables() {
        return variables;
    }

    /** 编辑模式直接替换元素列表（树排序/重挂载用）。 */
    public void replaceElements(List<Element> newElements) {
        elements.clear();
        elements.addAll(newElements);
    }

    public List<Element> elements() {
        return elements;
    }

    /** 页面生命周期脚本（open/close...）。 */
    public Map<String, String> functions() {
        return functions;
    }

    /** 页面级选项（allowEscClose/background/through/hideVanilla 等，schema 解释）。 */
    public Map<String, Object> options() {
        return options;
    }
}
