package com.opendreamcore.adapter.dreamcore.methods;

import com.opendreamcore.adapter.dreamcore.LegacyMethods;

public final class CoreMathLegacy {
    private CoreMathLegacy() { }

    private static double d(Object[] a, int i) {
        return a != null && i < a.length && a[i] instanceof Number n ? n.doubleValue() : 0;
    }

    private static int i(Object[] a, int i) {
        return (int) d(a, i);
    }

    public static void install() {
        LegacyMethods.register("abs", a -> Math.abs(d(a, 0)));
        LegacyMethods.register("sin", a -> Math.sin(Math.toRadians(d(a, 0))));
        LegacyMethods.register("cos", a -> Math.cos(Math.toRadians(d(a, 0))));
        LegacyMethods.register("tan", a -> Math.tan(Math.toRadians(d(a, 0))));
        LegacyMethods.register("asin", a -> Math.toDegrees(Math.asin(d(a, 0))));
        LegacyMethods.register("acos", a -> Math.toDegrees(Math.acos(d(a, 0))));
        LegacyMethods.register("atan", a -> Math.atan(d(a, 0)));
        LegacyMethods.register("atan2", a -> Math.toDegrees(Math.atan2(d(a, 0), d(a, 1))));
        LegacyMethods.register("sqrt", a -> Math.sqrt(d(a, 0)));
        LegacyMethods.register("pow", a -> Math.pow(d(a, 0), d(a, 1)));
        LegacyMethods.register("exp", a -> Math.exp(d(a, 0)));
        LegacyMethods.register("log", a -> Math.log(d(a, 0)));
        LegacyMethods.register("ln", a -> Math.log(d(a, 0)));
        LegacyMethods.register("log10", a -> Math.log10(d(a, 0)));
        LegacyMethods.register("floor", a -> Math.floor(d(a, 0)));
        LegacyMethods.register("ceil", a -> Math.ceil(d(a, 0)));
        LegacyMethods.register("round", a -> (double) Math.round(d(a, 0)));
        LegacyMethods.register("trunc", a -> Math.rint(d(a, 0)));
        LegacyMethods.register("fract", a -> d(a, 0) - Math.floor(d(a, 0)));
        LegacyMethods.register("sign", a -> Math.signum(d(a, 0)));
        LegacyMethods.register("mod", a -> {
            double x = d(a, 0), y = d(a, 1);
            return y == 0 ? 0 : ((x % y) + y) % y;
        });
        LegacyMethods.register("min", a -> {
            double m = Double.MAX_VALUE;
            for (Object x : a) if (x instanceof Number n) m = Math.min(m, n.doubleValue());
            return m == Double.MAX_VALUE ? 0 : m;
        });
        LegacyMethods.register("max", a -> {
            double m = -Double.MAX_VALUE;
            for (Object x : a) if (x instanceof Number n) m = Math.max(m, n.doubleValue());
            return m == -Double.MAX_VALUE ? 0 : m;
        });
        LegacyMethods.register("clamp", a -> Math.max(d(a, 1), Math.min(d(a, 2), d(a, 0))));
        LegacyMethods.register("lerp", a -> d(a, 0) + (d(a, 1) - d(a, 0)) * d(a, 2));
        LegacyMethods.register("twoLerp", a -> {
            double t = d(a, 4);
            double v1 = d(a, 0) + (d(a, 1) - d(a, 0)) * t;
            double v2 = d(a, 2) + (d(a, 3) - d(a, 2)) * t;
            return v1 + (v2 - v1) * t;
        });
        LegacyMethods.register("lerp_rotate", a -> {
            double from = d(a, 0), to = d(a, 1), t = d(a, 2);
            double diff = ((to - from) % 360 + 540) % 360 - 180;
            return from + diff * t;
        });
        LegacyMethods.register("hermite_blend", a -> {
            double t = d(a, 0);
            return t * t * (3 - 2 * t);
        });
        LegacyMethods.register("bezier", a -> d(a, 0));
        LegacyMethods.register("smooth", a -> {
            double t = d(a, 0);
            return t * t * t * (t * (t * 6 - 15) + 10);
        });
        LegacyMethods.register("elastic", a -> {
            double t = d(a, 0);
            if (t == 0 || t == 1) return t;
            return Math.pow(2, -10 * t) * Math.sin((t - 0.075) * (2 * Math.PI) / 0.3) + 1;
        });
        LegacyMethods.register("bounce", a -> {
            double t = d(a, 0);
            double n1 = 7.5625, d1 = 2.75;
            if (t < 1 / d1) return n1 * t * t;
            if (t < 2 / d1) return n1 * (t -= 1.5 / d1) * t + 0.75;
            if (t < 2.5 / d1) return n1 * (t -= 2.25 / d1) * t + 0.9375;
            return n1 * (t -= 2.625 / d1) * t + 0.984375;
        });
        LegacyMethods.register("expo", a -> {
            double t = d(a, 0);
            return t == 0 ? 0 : t == 1 ? 1 : Math.pow(2, 10 * t - 10);
        });
        LegacyMethods.register("sine", a -> {
            double t = d(a, 0);
            return -(Math.cos(Math.PI * t) - 1) / 2;
        });
        LegacyMethods.register("circX", a -> {
            double t = d(a, 0);
            return t < 0.5 ? (1 - Math.sqrt(1 - Math.pow(2 * t, 2))) / 2
                    : (Math.sqrt(1 - Math.pow(-2 * t + 2, 2)) + 1) / 2;
        });
        LegacyMethods.register("circY", a -> (double) 0);
        LegacyMethods.register("q2", a -> d(a, 0));
        LegacyMethods.register("q3", a -> d(a, 0));
        LegacyMethods.register("q4", a -> d(a, 0));
        LegacyMethods.register("q5", a -> d(a, 0));
        LegacyMethods.register("random", a -> Math.random());
        LegacyMethods.register("random_integer", a ->
                (double) (int) (Math.random() * (d(a, 1) - d(a, 0) + 1) + d(a, 0)));
        LegacyMethods.register("die_roll", a -> d(a, 0) + Math.random() * (d(a, 1) - d(a, 0)));
        LegacyMethods.register("die_roll_integer", a ->
                (double) (int) (Math.random() * (i(a, 1) - i(a, 0) + 1) + i(a, 0)));
        LegacyMethods.register("parseInt", a -> {
            try { return (double) Integer.parseInt(String.valueOf(arg(a, 0)).trim()); }
            catch (Exception e) { return 0.0; }
        });
        LegacyMethods.register("parseFloat", a -> {
            try { return Double.parseDouble(String.valueOf(arg(a, 0)).trim()); }
            catch (Exception e) { return 0.0; }
        });
        LegacyMethods.register("to_int", a -> (double) (int) d(a, 0));
        LegacyMethods.register("to_double", a -> d(a, 0));
        LegacyMethods.register("to_float", a -> (double) (float) d(a, 0));
        LegacyMethods.register("to_long", a -> (double) (long) d(a, 0));
        LegacyMethods.register("to_boolean", a -> Boolean.parseBoolean(String.valueOf(arg(a, 0))));
        LegacyMethods.register("to_string", a -> String.valueOf(arg(a, 0)));
        LegacyMethods.register("toString", a -> String.valueOf(arg(a, 0)));
        LegacyMethods.register("typeof", a -> {
            Object v = arg(a, 0);
            if (v == null) return "null";
            if (v instanceof Number) return "number";
            if (v instanceof Boolean) return "boolean";
            if (v instanceof String) return "string";
            return "object";
        });
        LegacyMethods.register("compare_to", a -> {
            Object l = arg(a, 0), r = arg(a, 1);
            if (l instanceof Number ln && r instanceof Number rn)
                return (double) Double.compare(ln.doubleValue(), rn.doubleValue());
            return (double) String.valueOf(l).compareTo(String.valueOf(r));
        });
        LegacyMethods.register("hash_code", a -> (double) String.valueOf(arg(a, 0)).hashCode());
        LegacyMethods.register("assert", a -> null);
    }

    private static Object arg(Object[] a, int i) {
        return a != null && i < a.length ? a[i] : null;
    }
}
