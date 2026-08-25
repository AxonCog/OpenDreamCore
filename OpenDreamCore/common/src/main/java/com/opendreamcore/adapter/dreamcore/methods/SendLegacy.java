package com.opendreamcore.adapter.dreamcore.methods;

import com.opendreamcore.adapter.dreamcore.LegacyMethods;

/** 发送/网络/执行类。 */
public final class SendLegacy {
    private SendLegacy() { }

    private static Object n(String m, Object... a) {
        return LegacyMethods.delegate("Network", m, a);
    }

    public static void install() {
        LegacyMethods.register("发送数据包", a -> n("sendCustomPacket",
                LegacyMethods.arg(a, 0), LegacyMethods.arg(a, 1)));
        LegacyMethods.register("发送命令", a -> n("发送命令", LegacyMethods.arg(a, 0)));
        LegacyMethods.register("执行命令", a -> n("发送命令", LegacyMethods.arg(a, 0)));
        LegacyMethods.register("打开网页", a -> n("打开网页", LegacyMethods.arg(a, 0)));
        LegacyMethods.register("同步执行方法", a -> {
            String name = LegacyMethods.str(a, 0);
            if (name != null && !name.isBlank()) {
                com.opendreamcore.script.NamespaceRegistry.require(
                        name.contains(".") ? name.substring(0, name.indexOf('.')) : "Script",
                        name.contains(".") ? name.substring(name.indexOf('.') + 1) : name)
                        .invoke(java.util.Arrays.copyOfRange(a, 1, a.length));
            }
            return null;
        });
        LegacyMethods.register("执行", a -> {
            String script = LegacyMethods.str(a, 0);
            if (script != null && !script.isBlank()) {
                com.opendreamcore.script.DreamLang.execute(script, null);
            }
            return null;
        });
        LegacyMethods.register("异步执行", a -> {
            String script = LegacyMethods.str(a, 0);
            if (script != null && !script.isBlank()) {
                java.util.concurrent.CompletableFuture.runAsync(() ->
                        com.opendreamcore.script.DreamLang.execute(script, null));
            }
            return null;
        });
    }
}
