package com.opendreamcore.adapter.dreamcore.methods;

import com.opendreamcore.adapter.dreamcore.LegacyMethods;

public final class MouseKeyLegacy {
    private MouseKeyLegacy() { }

    private static Object m(String mth, Object... a) {
        return LegacyMethods.delegate("Mouse", mth, a);
    }

    private static Object k(String mth, Object... a) {
        return LegacyMethods.delegate("Key", mth, a);
    }

    public static void install() {
        LegacyMethods.register("取鼠标X", a -> m("getX"));
        LegacyMethods.register("取鼠标Y", a -> m("getY"));
        LegacyMethods.register("取鼠标deltax", a -> m("getScaledX"));
        LegacyMethods.register("取鼠标deltay", a -> m("getScaledY"));
        LegacyMethods.register("获取鼠标XV2", a -> m("getX"));
        LegacyMethods.register("获取鼠标YV2", a -> m("getY"));
        LegacyMethods.register("移动鼠标", a -> null);
        LegacyMethods.register("设置鼠标", a -> null);
        LegacyMethods.register("取按键", a -> k("isKeyDown", a.length > 0 ? a[0] : ""));
        LegacyMethods.register("取键", a -> k("isKeyDown", a.length > 0 ? a[0] : ""));
        LegacyMethods.register("发送按键", a -> k("isKeyDown", a.length > 0 ? a[0] : ""));
        LegacyMethods.register("执行按键指令", a -> k("isKeyDown", a.length > 0 ? a[0] : ""));
        LegacyMethods.register("获取按键名", a -> k("getKeyName", a.length > 0 ? a[0] : ""));
        LegacyMethods.register("获取控制按键名", a -> k("getKeyName", a.length > 0 ? a[0] : ""));
        LegacyMethods.register("设置控制按键", a -> null);
    }
}
