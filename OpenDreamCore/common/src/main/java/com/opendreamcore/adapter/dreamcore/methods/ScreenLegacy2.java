package com.opendreamcore.adapter.dreamcore.methods;

import com.opendreamcore.adapter.dreamcore.LegacyMethods;

public final class ScreenLegacy2 {
    private ScreenLegacy2() { }

    private static Object s(String m, Object... a) {
        return LegacyMethods.delegate("Screen", m, a);
    }

    public static void install() {
        // 组件查询族
        LegacyMethods.register("取组件类型", a -> s("获取元素", LegacyMethods.arg(a, 0), "type"));
        LegacyMethods.register("取组件宽度", a -> s("获取元素", LegacyMethods.arg(a, 0), "width"));
        LegacyMethods.register("取组件高度", a -> s("获取元素", LegacyMethods.arg(a, 0), "height"));
        LegacyMethods.register("取组件位置", a -> s("获取元素", LegacyMethods.arg(a, 0), "x"));
        LegacyMethods.register("取组件中心", a -> s("获取元素", LegacyMethods.arg(a, 0), "x"));
        LegacyMethods.register("取组件距离", a -> 0);
        LegacyMethods.register("取悬浮组件", a -> s("获取元素", "_hovered", null));
        LegacyMethods.register("取悬浮组件名", a -> s("获取元素", "_hovered", null));
        LegacyMethods.register("取所有组件", a -> s("获取元素", "_all", null));
        LegacyMethods.register("取鼠标悬浮组件", a -> s("获取元素", "_hovered", null));
        LegacyMethods.register("取鼠标悬浮组件名", a -> s("获取元素", "_hovered", null));

        // 界面尺寸
        LegacyMethods.register("取界面尺寸", a -> s("获取变量", "_odc_screen_size"));
        LegacyMethods.register("取实际屏幕宽度", a -> LegacyMethods.delegate("Display", "getWidth"));
        LegacyMethods.register("取实际屏幕高度", a -> LegacyMethods.delegate("Display", "getHeight"));
        LegacyMethods.register("获取屏幕宽度V2", a -> LegacyMethods.delegate("Display", "getWidth"));
        LegacyMethods.register("获取屏幕高度V2", a -> LegacyMethods.delegate("Display", "getHeight"));
        LegacyMethods.register("获取窗口缩放", a -> LegacyMethods.delegate("Display", "getScale"));
        LegacyMethods.register("取缩放比例", a -> LegacyMethods.delegate("Display", "getScale"));
        LegacyMethods.register("获取缩放比例", a -> LegacyMethods.delegate("Display", "getScale"));
        LegacyMethods.register("缩放模式", a -> "auto");
        LegacyMethods.register("缩放模式名称", a -> "auto");

        // 界面打开时间
        LegacyMethods.register("取打开时间", a -> s("获取变量", "_odc_page_open_ms"));
        LegacyMethods.register("重置打开时间", a -> s("设置变量", "_odc_page_open_ms", System.currentTimeMillis()));

        // 基准尺寸
        LegacyMethods.register("取基准宽度", a -> 1920);
        LegacyMethods.register("取基准高度", a -> 1080);
        LegacyMethods.register("设置基准尺寸", a -> null);

        // 文本测量
        LegacyMethods.register("取文本宽度", a -> {
            String txt = a != null && a.length > 0 && a[0] != null ? String.valueOf(a[0]) : null;
            return txt != null ? txt.length() * 6 : 0;
        });
        LegacyMethods.register("取文本高度", a -> 9);
    }
}
