package com.opendreamcore.script;

import com.opendreamcore.plugin.Registry;

/**
 * 脚本方法注册表：`方法.发送消息("x")` 里的方法名 → 处理器。
 * 万物皆插件：插件注册新方法即扩展脚本能力。
 */
public final class MethodRegistry {

    /** 方法处理器：参数为求值后的值。 */
    @FunctionalInterface
    public interface Handler {
        Object invoke(Object[] args);
    }

    private static final Registry<Handler> REGISTRY = new Registry<>();

    private MethodRegistry() {
    }

    public static void register(String name, Handler handler) {
        REGISTRY.register(name, handler);
    }

    public static void registerOrReplace(String name, Handler handler) {
        REGISTRY.registerOrReplace(name, handler);
    }

    public static Handler require(String name) {
        return REGISTRY.require(name);
    }

    public static boolean contains(String name) {
        return REGISTRY.contains(name);
    }

    public static void unregister(String name) {
        REGISTRY.unregister(name);
    }
}
