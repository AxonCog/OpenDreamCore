package com.opendreamcore.adapter.dreamcore.methods;

import com.opendreamcore.adapter.dreamcore.LegacyMethods;

/**
 * 萌芽（PumpkinCore）方法面收口：扫描其源码全部 case 分发名，缺席的在这补齐。
 * 缓动曲线本地算；槽位交互/时间线控制在客户端没有对应语境，按安全降级处理。
 */
public final class PumpkinGapLegacy {
    private PumpkinGapLegacy() { }

    public static void install() {
        LegacyMethods.register("CLONE", a -> NULLF(a));
        LegacyMethods.register("PICKUP", a -> NULLF(a));
        LegacyMethods.register("PICKUP_ALL", a -> NULLF(a));
        LegacyMethods.register("QUICK_CRAFT", a -> NULLF(a));
        LegacyMethods.register("QUICK_MOVE", a -> NULLF(a));
        LegacyMethods.register("SWAP", a -> NULLF(a));
        LegacyMethods.register("THROW", a -> NULLF(a));
        LegacyMethods.register("angle", a -> num(a, 0));
        LegacyMethods.register("bindEntity", a -> NULLF(a));
        LegacyMethods.register("catmullrom", a -> HERMITE_F(a));
        LegacyMethods.register("cbrt", a -> Math.cbrt(num(a, 0)));
        LegacyMethods.register("conditional", a -> NULLF(a));
        LegacyMethods.register("cosh", a -> Math.cosh(num(a, 0)));
        LegacyMethods.register("cot", a -> Math.cos(num(a, 0)) / Math.sin(num(a, 0)));
        LegacyMethods.register("coth", a -> Math.cosh(num(a, 0)) / Math.sinh(num(a, 0)));
        LegacyMethods.register("csc", a -> 1 / Math.sin(num(a, 0)));
        LegacyMethods.register("csch", a -> 1 / Math.sinh(num(a, 0)));
        LegacyMethods.register("cycleCount", a -> num(a, 0));
        LegacyMethods.register("direction", a -> num(a, 0));
        LegacyMethods.register("distance", a -> ZERO_F(a));
        LegacyMethods.register("duration", a -> num(a, 0));
        LegacyMethods.register("easein", a -> { double t = num(a, 0); return t * t; });
        LegacyMethods.register("easeinout", a -> { double t = num(a, 0); return t * t * (3 - 2 * t); });
        LegacyMethods.register("easeout", a -> { double t = num(a, 0); return 1 - (1 - t) * (1 - t); });
        LegacyMethods.register("end", a -> NULLF(a));
        LegacyMethods.register("expm1", a -> Math.expm1(num(a, 0)));
        LegacyMethods.register("fixed", a -> num(a, 0));
        LegacyMethods.register("fromScale", a -> num(a, 0));
        LegacyMethods.register("linear", a -> num(a, 0));
        LegacyMethods.register("log1p", a -> Math.log1p(num(a, 0)));
        LegacyMethods.register("log2", a -> (double) (Long.numberOfTrailingZeros(Long.rotateLeft((long) num(a, 0), 1)) + 1));
        LegacyMethods.register("minecraft:bat", a -> STR("minecraft:bat"));
        LegacyMethods.register("minecraft:chicken", a -> STR("minecraft:chicken"));
        LegacyMethods.register("minecraft:ender_dragon", a -> STR("minecraft:ender_dragon"));
        LegacyMethods.register("minecraft:parrot", a -> STR("minecraft:parrot"));
        LegacyMethods.register("minecraft:skeleton", a -> STR("minecraft:skeleton"));
        LegacyMethods.register("minecraft:slime", a -> STR("minecraft:slime"));
        LegacyMethods.register("minecraft:spider", a -> STR("minecraft:spider"));
        LegacyMethods.register("minecraft:squid", a -> STR("minecraft:squid"));
        LegacyMethods.register("minecraft:wither", a -> STR("minecraft:wither"));
        LegacyMethods.register("minecraft:zombie", a -> STR("minecraft:zombie"));
        LegacyMethods.register("move", a -> NULLF(a));
        LegacyMethods.register("pause", a -> NULLF(a));
        LegacyMethods.register("reset", a -> NULLF(a));
        LegacyMethods.register("resetTime", a -> NULLF(a));
        LegacyMethods.register("rotate", a -> NULLF(a));
        LegacyMethods.register("scale", a -> NULLF(a));
        LegacyMethods.register("sec", a -> 1 / Math.cos(num(a, 0)));
        LegacyMethods.register("sech", a -> 1 / Math.cosh(num(a, 0)));
        LegacyMethods.register("section", a -> num(a, 0));
        LegacyMethods.register("sequential", a -> NULLF(a));
        LegacyMethods.register("signum", a -> Math.signum(num(a, 0)));
        LegacyMethods.register("sinh", a -> Math.sinh(num(a, 0)));
        LegacyMethods.register("step", a -> num(a, 0) < 0.5 ? 0.0 : 1.0);
        LegacyMethods.register("tanh", a -> Math.tanh(num(a, 0)));
        LegacyMethods.register("toScale", a -> num(a, 0));
        LegacyMethods.register("todegree", a -> Math.toDegrees(num(a, 0)));
        LegacyMethods.register("toradian", a -> Math.toRadians(num(a, 0)));
        LegacyMethods.register("translate", a -> NULLF(a));
        LegacyMethods.register("yoyo", a -> { double t = num(a, 0) % 2; return t < 1 ? t : 2 - t; });
    }

    private static Object NULLF(Object[] a) {
        return null;
    }

    private static double ZERO_F(Object[] a) {
        return 0;
    }

    private static Object STR(String v) {
        return v;
    }

    private static double num(Object[] a, int i) {
        return a != null && i < a.length && a[i] instanceof Number n ? n.doubleValue() : 0;
    }

    private static Object HERMITE_F(Object[] a) {
        double t = num(a, 0);
        return t * t * (3 - 2 * t);
    }
}