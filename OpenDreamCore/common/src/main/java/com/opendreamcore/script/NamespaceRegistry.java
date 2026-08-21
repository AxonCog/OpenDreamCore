package com.opendreamcore.script;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 脚本命名空间注册表：`Player.获取血量()` / `Chat.发送消息("x")`。
 * 一个命名空间 = 一组方法；方法处理器同 MethodRegistry（参数为求值后的值）。
 * 万物皆插件：插件注册命名空间即扩展脚本能力（客户端/服务端各自注册各自的实现）。
 */
public final class NamespaceRegistry {

    /** 方法处理器：参数为求值后的值。 */
    @FunctionalInterface
    public interface Handler {
        Object invoke(Object[] args);
    }

    private static final Map<String, Map<String, Handler>> NAMESPACES = new ConcurrentHashMap<>();

    private NamespaceRegistry() {
    }

    public static void register(String namespace, String method, Handler handler) {
        NAMESPACES.computeIfAbsent(namespace, k -> new ConcurrentHashMap<>()).put(method, handler);
    }

    public static void registerOrReplace(String namespace, String method, Handler handler) {
        NAMESPACES.computeIfAbsent(namespace, k -> new ConcurrentHashMap<>()).put(method, handler);
    }

    /** 一次注册多个名字（中英文/别名共用同一实现）。 */
    public static void register(String namespace, Handler handler, String... names) {
        for (String name : names) {
            register(namespace, name, handler);
        }
    }

    public static boolean containsNamespace(String namespace) {
        return NAMESPACES.containsKey(namespace);
    }

    public static Handler require(String namespace, String method) {
        Map<String, Handler> methods = NAMESPACES.get(namespace);
        if (methods == null) {
            throw new DreamLangExecutor.ScriptException("未知命名空间: " + namespace);
        }
        Handler handler = methods.get(method);
        if (handler == null) {
            throw new DreamLangExecutor.ScriptException("命名空间 " + namespace + " 没有方法: " + method);
        }
        return handler;
    }

    public static void unregister(String namespace) {
        NAMESPACES.remove(namespace);
    }
}
