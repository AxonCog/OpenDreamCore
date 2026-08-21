package com.opendreamcore.api;

import com.opendreamcore.script.NamespaceRegistry;

/**
 * 脚本 API：给附属插件注册自定义 DreamLang 脚本方法。
 * 注册后页面 actions 和服务端脚本可以直接调用。
 *
 * 用法：
 * <pre>
 * ScriptAPI.register("Shop", "购买", args -> {
 *     // args 是脚本里传入的参数（已求值）
 *     String itemId = String.valueOf(args[0]);
 *     int count = ((Number) args[1]).intValue();
 *     // ... 执行购买逻辑
 *     return "购买成功";
 * });
 * </pre>
 * 页面 YAML 里：
 * <pre>
 * actions:
 *   click: |-
 *     Shop.购买("diamond", 1)
 * </pre>
 */
public final class ScriptAPI {

    static final ScriptAPI INSTANCE = new ScriptAPI();

    private ScriptAPI() {
    }

    /**
     * 注册脚本方法。
     * @param namespace 命名空间（如 "Shop"、"Teleport"）
     * @param method 方法名（如 "购买"、"buy"）
     * @param handler 处理器，args 为脚本传入的已求值参数
     */
    public static void register(String namespace, String method, NamespaceRegistry.Handler handler) {
        NamespaceRegistry.register(namespace, method, handler);
    }

    /** 一次注册多个别名（中英文共用同一实现）。 */
    public static void registerAlias(String namespace, NamespaceRegistry.Handler handler, String... names) {
        NamespaceRegistry.register(namespace, handler, names);
    }
}
