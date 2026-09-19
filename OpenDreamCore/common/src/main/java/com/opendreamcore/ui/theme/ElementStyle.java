package com.opendreamcore.ui.theme;

import com.opendreamcore.util.J8;

import java.util.List;
import java.util.Map;

/**
 * 元素的最终样式产物，编译期由主题管线产出，随页面一起下发。
 *   base 声明已合并进元素 props（内联永远优先）；
 *   这里只携带客户端运行时才需要的东西——状态覆盖层与过渡声明。
 * 平台版本渲染层只读本结构与 props，不需要知道主题存在。
 */
public final class ElementStyle {

    /** 状态键（框架自动维护）：悬停/按下/禁用/聚焦。 */
    public static final String STATE_HOVER = "hover";
    public static final String STATE_PRESSED = "pressed";
    public static final String STATE_DISABLED = "disabled";
    public static final String STATE_FOCUS = "focus";

    /** 一条响应式覆盖层：条件 + 属性表 + 级联权重。 */
    public record MediaOverride(MediaQuery media, Map<String, Object> props,
                                int specificity, int order) {
    }

    private final Map<String, Map<String, Object>> stateOverlays; // 状态名 -> 属性覆盖
    private final List<TransitionSpec> transitions;
    private final List<MediaOverride> mediaOverrides;             // 响应式覆盖层（按级联序）

    public ElementStyle(Map<String, Map<String, Object>> stateOverlays,
                        List<TransitionSpec> transitions) {
        this(stateOverlays, transitions, J8.list());
    }

    public ElementStyle(Map<String, Map<String, Object>> stateOverlays,
                        List<TransitionSpec> transitions,
                        List<MediaOverride> mediaOverrides) {
        this.stateOverlays = J8.mapCopy(stateOverlays);
        this.transitions = J8.listCopy(transitions);
        this.mediaOverrides = J8.listCopy(mediaOverrides);
    }

    /** 某状态的属性覆盖；无则返回 null。 */
    public Map<String, Object> overlay(String state) {
        return stateOverlays.get(state);
    }

    /** 全部状态覆盖（只读）。 */
    public Map<String, Map<String, Object>> stateOverlays() {
        return stateOverlays;
    }

    /** 属性过渡声明列表。 */
    public List<TransitionSpec> transitions() {
        return transitions;
    }

    /** 响应式覆盖层（按级联序存放）。 */
    public List<MediaOverride> mediaOverrides() {
        return mediaOverrides;
    }

    /** 某属性的过渡声明；无则 null。 */
    public TransitionSpec transitionFor(String property) {
        return TransitionSpec.find(transitions, property);
    }

    public boolean isEmpty() {
        return stateOverlays.isEmpty() && transitions.isEmpty() && mediaOverrides.isEmpty();
    }
}
