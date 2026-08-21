package com.opendreamcore.plugin;

/**
 * 组件运行时行为：事件处理入口（点击/悬停/输入等）。
 * 平台侧注册实际处理器；common 提供事件名常量。
 */
public interface ComponentBehavior {

    /** 元素事件（与 YAML actions 键一致）。 */
    interface Event {
        String CLICK = "click";
        String HOVER = "hover";
        String PRESS = "press";
        String INPUT = "input";
        String SCROLL = "scroll";
        String KEY = "key";
    }
}
