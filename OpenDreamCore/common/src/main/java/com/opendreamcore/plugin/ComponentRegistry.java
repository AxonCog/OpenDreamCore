package com.opendreamcore.plugin;

/**
 * UI 组件注册表（万物皆插件核心之一）。
 * 内置组件与第三方插件组件同一注册表，无特权差异。
 * 支持热拔插：注册时调用 onRegister，注销时调用 onUnregister。
 */
public final class ComponentRegistry {

    private static final Registry<ComponentSpec> REGISTRY = new Registry<>();

    public static void register(ComponentSpec spec) {
        // 依赖检查
        for (String dep : spec.dependencies()) {
            if (!REGISTRY.contains(dep)) {
                throw new IllegalStateException("组件 " + spec.type() + " 依赖未注册的组件: " + dep);
            }
        }
        REGISTRY.register(spec.type(), spec);
        spec.onRegister();
    }

    public static void registerOrReplace(ComponentSpec spec) {
        // 依赖检查
        for (String dep : spec.dependencies()) {
            if (!REGISTRY.contains(dep)) {
                throw new IllegalStateException("组件 " + spec.type() + " 依赖未注册的组件: " + dep);
            }
        }
        // 注销旧组件
        ComponentSpec old = REGISTRY.get(spec.type()).orElse(null);
        if (old != null) {
            old.onUnregister();
        }
        REGISTRY.registerOrReplace(spec.type(), spec);
        spec.onRegister();
    }

    public static ComponentSpec require(String type) {
        return REGISTRY.require(type);
    }

    public static boolean contains(String type) {
        return REGISTRY.contains(type);
    }

    public static void unregister(String type) {
        ComponentSpec spec = REGISTRY.get(type).orElse(null);
        if (spec != null) {
            spec.onUnregister();
        }
        REGISTRY.unregister(type);
    }

    /** 校验 YAML 里的 type 是否存在（schema 报错用）。 */
    public static void checkType(String type, String elementId) {
        if (!REGISTRY.contains(type)) {
            throw new IllegalArgumentException("未知元素类型 \"" + type + "\"（元素 " + elementId + "）");
        }
    }

    /** 所有已注册组件快照（调色板/文档用）。 */
    public static java.util.Map<String, ComponentSpec> snapshot() {
        return REGISTRY.snapshot();
    }
}
