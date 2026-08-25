package com.opendreamcore.adapter.dreamcore.methods;

import com.opendreamcore.adapter.dreamcore.LegacyMethods;

public final class PlayerLegacy2 {
    private PlayerLegacy2() { }

    private static Object p(String m, Object... a) {
        return LegacyMethods.delegate("Player", m, a);
    }

    public static void install() {
        LegacyMethods.register("取玩家头像", a -> p("获取头像"));
        LegacyMethods.register("获取FOV", a -> p("获取FOV"));
        LegacyMethods.register("获取在线玩家字典", a -> p("获取在线玩家字典"));
        LegacyMethods.register("获取方块名称", a -> "");
        LegacyMethods.register("设置第三人称", a -> p("切换视角"));
        LegacyMethods.register("设置全屏", a -> LegacyMethods.delegate("Display", "toggleFullscreen"));
    }
}
