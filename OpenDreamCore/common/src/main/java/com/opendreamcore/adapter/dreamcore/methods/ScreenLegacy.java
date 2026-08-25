package com.opendreamcore.adapter.dreamcore.methods;

import com.opendreamcore.adapter.dreamcore.LegacyMethods;

/** 界面/组件操作类。 */
public final class ScreenLegacy {
    private ScreenLegacy() { }

    private static Object s(String m, Object... a) {
        return LegacyMethods.delegate("Screen", m, a);
    }

    public static void install() {
        // 组件操作
        LegacyMethods.register("取组件", a -> s("获取元素", LegacyMethods.arg(a, 0), null));
        LegacyMethods.register("取组件属性", a -> s("获取元素", LegacyMethods.arg(a, 0), LegacyMethods.str(a, 1)));
        LegacyMethods.register("设置组件位置", a -> s("设置元素", LegacyMethods.arg(a, 0), "x", LegacyMethods.num(a, 1)));
        LegacyMethods.register("设置组件属性", a -> s("设置元素", LegacyMethods.arg(a, 0), LegacyMethods.str(a, 1), LegacyMethods.arg(a, 2)));
        LegacyMethods.register("批量设置属性", a -> {
            for (int i = 0; i + 2 < a.length; i += 3) {
                s("设置元素", a[i], a[i + 1], a[i + 2]);
            }
            return null;
        });
        LegacyMethods.register("移动组件", a -> s("设置元素", LegacyMethods.arg(a, 0), "x", LegacyMethods.num(a, 1)));

        // 界面变量
        LegacyMethods.register("设置界面变量", a -> s("设置变量", LegacyMethods.arg(a, 0), LegacyMethods.arg(a, 1)));
        LegacyMethods.register("取界面变量", a -> s("获取变量", LegacyMethods.arg(a, 0)));
        LegacyMethods.register("设置变量", a -> s("设置变量", LegacyMethods.arg(a, 0), LegacyMethods.arg(a, 1)));
        LegacyMethods.register("设置变量值", a -> s("设置变量", LegacyMethods.arg(a, 0), LegacyMethods.arg(a, 1)));
        LegacyMethods.register("取变量", a -> s("获取变量", LegacyMethods.arg(a, 0)));
        LegacyMethods.register("取变量值", a -> s("获取变量", LegacyMethods.arg(a, 0)));
        LegacyMethods.register("替换变量", a -> s("设置变量", LegacyMethods.arg(a, 0), LegacyMethods.arg(a, 1)));
        LegacyMethods.register("替换变量值", a -> s("设置变量", LegacyMethods.arg(a, 0), LegacyMethods.arg(a, 1)));

        // 界面状态
        LegacyMethods.register("取当前界面名", a -> s("获取元素", "_page_id", null));
        LegacyMethods.register("界面是否已关闭", a -> !(Boolean) s("isOpen"));

        // 显示/隐藏
        LegacyMethods.register("设置显示", a -> s("设置元素", LegacyMethods.arg(a, 0), "visible", LegacyMethods.arg(a, 1)));
        LegacyMethods.register("设置隐藏", a -> s("设置元素", LegacyMethods.arg(a, 0), "visible", false));
    }
}
