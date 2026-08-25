package com.opendreamcore.adapter.dreamcore.methods;

import com.opendreamcore.adapter.dreamcore.LegacyMethods;

/**
 * 玩家/视角类旧方法：坐标、朝向、生命、经验等。
 * 委派到客户端 Player 命名空间。
 */
public final class PlayerLegacy {

    private PlayerLegacy() {
    }

    private static Object p(String method, Object... args) {
        return LegacyMethods.delegate("Player", method, args);
    }

    public static void install() {
        LegacyMethods.register("取玩家坐标x", a -> p("获取X"));
        LegacyMethods.register("取玩家坐标y", a -> p("获取Y"));
        LegacyMethods.register("取玩家坐标z", a -> p("获取Z"));
        LegacyMethods.register("取玩家位置", a -> p("获取X") + "," + p("获取Y") + "," + p("获取Z"));
        LegacyMethods.register("取玩家yaw", a -> p("获取偏航"));
        LegacyMethods.register("取玩家pitch", a -> p("获取俯仰"));
        LegacyMethods.register("取玩家名", a -> p("获取名"));
        LegacyMethods.register("获取玩家名", a -> p("获取名"));
        LegacyMethods.register("获取玩家UUID", a -> p("获取UUID"));
        LegacyMethods.register("获取玩家X", a -> p("获取X"));
        LegacyMethods.register("获取玩家Y", a -> p("获取Y"));
        LegacyMethods.register("获取玩家Z", a -> p("获取Z"));
        LegacyMethods.register("获取玩家俯仰", a -> p("获取俯仰"));
        LegacyMethods.register("获取玩家偏航", a -> p("获取偏航"));
        LegacyMethods.register("获取玩家主手物品", a -> p("获取主手物品"));
        LegacyMethods.register("获取玩家当前槽位", a -> p("获取当前槽位"));
        LegacyMethods.register("获取玩家生命", a -> p("获取生命"));
        LegacyMethods.register("获取玩家最大生命", a -> p("获取最大生命"));
        LegacyMethods.register("获取玩家饥饿", a -> p("获取饥饿"));
        LegacyMethods.register("获取玩家经验", a -> p("获取经验"));
        LegacyMethods.register("获取玩家等级", a -> p("获取等级"));
        LegacyMethods.register("获取玩家护甲", a -> p("获取护甲"));
        LegacyMethods.register("获取玩家氧气", a -> p("获取氧气"));
        LegacyMethods.register("玩家移动速度", a -> p("移动速度"));
    }
}
