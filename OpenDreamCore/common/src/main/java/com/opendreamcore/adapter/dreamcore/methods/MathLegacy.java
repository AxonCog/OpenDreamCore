package com.opendreamcore.adapter.dreamcore.methods;

import com.opendreamcore.adapter.dreamcore.LegacyMethods;

/** 数学计算类。 */
public final class MathLegacy {
    private MathLegacy() { }

    private static double n(Object[] a, int i) {
        return a != null && i < a.length && a[i] instanceof Number num ? num.doubleValue() : 0;
    }

    public static void install() {
        LegacyMethods.register("abs", a -> Math.abs(LegacyMethods.num(a, 0)));
        LegacyMethods.register("acos", a -> Math.acos(LegacyMethods.num(a, 0)));
        LegacyMethods.register("asin", a -> Math.asin(LegacyMethods.num(a, 0)));
        LegacyMethods.register("pi", a -> Math.PI);
        LegacyMethods.register("缩放模式名称", a -> "auto");
        LegacyMethods.register("旋转插值", a -> {
            double from = LegacyMethods.num(a, 0);
            double to = n2(a, 1);
            double t = n2(a, 2);
            return from + (to - from) * t;
        });
    }

    private static double n2(Object[] a, int i) {
        return a != null && i < a.length && a[i] instanceof Number num ? num.doubleValue() : 0;
    }
}
