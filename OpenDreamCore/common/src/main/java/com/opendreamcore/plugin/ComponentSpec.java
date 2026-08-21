package com.opendreamcore.plugin;

import com.opendreamcore.page.Element;

import java.util.Map;

/**
 * 组件规格：一个 UI 组件类型 = 解析器 + 行为 + 渲染（渲染在平台侧）。
 * 组件三件套通过 ComponentRegistry 注册，插件可新增任意组件类型。
 * 支持热拔插：onRegister/onUnregister 生命周期钩子 + 依赖声明。
 */
public interface ComponentSpec {

    /** 组件类型 id（YAML 里 type 字段的值）。 */
    String type();

    /**
     * 配置解析器：把元素 props（组件专属字段）校验/规范化为运行数据。
     * 返回 null 表示无专属数据。
     *
     * @throws IllegalArgumentException 配置非法（带元素 id）
     */
    Object parseProps(Element element, Map<String, Object> props);

    /**
     * 运行时行为：事件处理等（服务端裁决/客户端交互）。
     * 返回 null 表示无行为。
     */
    default ComponentBehavior behavior() {
        return null;
    }

    // ---- 热拔插生命周期 ----

    /** 组件注册时调用（可做初始化、注册子组件等）。 */
    default void onRegister() {}

    /** 组件注销时调用（可做清理、释放资源等）。 */
    default void onUnregister() {}

    /** 声明依赖的其他组件类型 id（无依赖返回空列表）。 */
    default java.util.List<String> dependencies() {
        return java.util.List.of();
    }

    /** 组件分类（用于调色板/文档分组；缺省 "通用"）。 */
    default String category() {
        return "通用";
    }

    /** 组件描述（文档/调色板提示用）。 */
    default String description() {
        return "";
    }
}
