package com.opendreamcore.adapter.dreamcore.methods;

import com.opendreamcore.adapter.dreamcore.LegacyMethods;

public final class ChatLegacy {
    private ChatLegacy() { }

    private static Object c(String m, Object... a) {
        return LegacyMethods.delegate("Chat", m, a);
    }

    public static void install() {
        LegacyMethods.register("取聊天栏内容", a -> c("getLastMessage"));
        LegacyMethods.register("取最后一条消息", a -> c("getLastMessage"));
        LegacyMethods.register("是否打开聊天栏", a -> false);
        LegacyMethods.register("设置聊天栏内容", a -> c("setChatMessage", a.length > 0 ? a[0] : ""));
    }
}
