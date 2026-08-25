package com.opendreamcore.adapter.dreamcore.methods;

import com.opendreamcore.adapter.dreamcore.LegacyMethods;

public final class CoreVarLegacy {
    private CoreVarLegacy() { }

    private static Object v(String m, Object... a) {
        return LegacyMethods.delegate("Var", m, a);
    }

    private static double d(Object[] a, int i) {
        return a != null && i < a.length && a[i] instanceof Number n ? n.doubleValue() : 0;
    }

    public static void install() {
        LegacyMethods.register("get_variable", a -> v("获取变量", arg(a, 0)));
        LegacyMethods.register("set_variable", a -> v("设置变量", arg(a, 0), arg(a, 1)));
        LegacyMethods.register("variable_get", a -> v("获取变量", arg(a, 0)));
        LegacyMethods.register("variable_set", a -> v("设置变量", arg(a, 0), arg(a, 1)));
        LegacyMethods.register("has_variable", a -> v("是否有变量", arg(a, 0)));
        LegacyMethods.register("has_delayed_variable", a -> false);
        LegacyMethods.register("clear_variable", a -> v("设置变量", arg(a, 0), null));
        LegacyMethods.register("clear_variables", a -> null);
        LegacyMethods.register("clear_variable_all", a -> null);
        LegacyMethods.register("delete_variable", a -> v("设置变量", arg(a, 0), null));
        LegacyMethods.register("increment_variable", a -> {
            String name = s(a, 0);
            Object cur = v("获取变量", name);
            double base = cur instanceof Number n ? n.doubleValue() : 0;
            return v("设置变量", name, base + (a.length > 1 ? d(a, 1) : 1));
        });
        LegacyMethods.register("decrement_variable", a -> {
            String name = s(a, 0);
            Object cur = v("获取变量", name);
            double base = cur instanceof Number n ? n.doubleValue() : 0;
            return v("设置变量", name, base - (a.length > 1 ? d(a, 1) : 1));
        });
        LegacyMethods.register("add_to_variable", a -> {
            String name = s(a, 0);
            Object cur = v("获取变量", name);
            if (cur instanceof Number n && arg(a, 1) instanceof Number add)
                return v("设置变量", name, n.doubleValue() + add.doubleValue());
            if (cur instanceof String str)
                return v("设置变量", name, str + String.valueOf(arg(a, 1)));
            return v("设置变量", name, arg(a, 1));
        });
        LegacyMethods.register("subtract_from_variable", a -> {
            String name = s(a, 0);
            Object cur = v("获取变量", name);
            double base = cur instanceof Number n ? n.doubleValue() : 0;
            return v("设置变量", name, base - d(a, 1));
        });
        LegacyMethods.register("multiply_variable", a -> {
            String name = s(a, 0);
            Object cur = v("获取变量", name);
            double base = cur instanceof Number n ? n.doubleValue() : 0;
            return v("设置变量", name, base * d(a, 1));
        });
        LegacyMethods.register("divide_variable", a -> {
            String name = s(a, 0);
            Object cur = v("获取变量", name);
            double base = cur instanceof Number n ? n.doubleValue() : 0;
            double div = d(a, 1);
            return v("设置变量", name, div == 0 ? 0 : base / div);
        });
        LegacyMethods.register("replace_variable", a -> v("设置变量", arg(a, 0), arg(a, 1)));
        LegacyMethods.register("update_variable", a -> v("设置变量", arg(a, 0), arg(a, 1)));
        LegacyMethods.register("update_placeholder", a -> null);
        LegacyMethods.register("get_variable_names", a -> v("获取所有变量名"));
        LegacyMethods.register("get_variable_count", a -> v("获取变量数量"));
        LegacyMethods.register("variable_reload", a -> null);
        LegacyMethods.register("variable_clear_temp", a -> null);
        LegacyMethods.register("variable_get_temp", a -> v("获取变量", "temp_" + s(a, 0)));
        LegacyMethods.register("variable_set_temp", a -> v("设置变量", "temp_" + s(a, 0), arg(a, 1)));
        LegacyMethods.register("variable_remove_temp", a -> v("设置变量", "temp_" + s(a, 0), null));
        LegacyMethods.register("get_animate_value", a -> v("获取动画值", arg(a, 0)));
        LegacyMethods.register("get_animation_variable", a -> v("获取动画值", arg(a, 0)));
        LegacyMethods.register("set_animate_value", a -> v("设置动画值", arg(a, 0), arg(a, 1)));
        LegacyMethods.register("set_animation_variable", a -> v("设置动画值", arg(a, 0), arg(a, 1)));
        LegacyMethods.register("animate_value", a -> v("设置动画值", arg(a, 0), arg(a, 1)));
        LegacyMethods.register("animate_blink", a -> Math.sin(System.currentTimeMillis() / 500.0) * 0.5 + 0.5);
        LegacyMethods.register("animate_breathe", a -> Math.sin(System.currentTimeMillis() / 800.0) * 0.5 + 0.5);
        LegacyMethods.register("animate_pulse", a -> 1 + Math.sin(System.currentTimeMillis() / 300.0) * 0.1);
        LegacyMethods.register("animate_wave", a -> Math.sin(System.currentTimeMillis() / 400.0));
        LegacyMethods.register("animate_swing", a -> Math.sin(System.currentTimeMillis() / 600.0));
        LegacyMethods.register("animate_shake", a -> Math.sin(System.currentTimeMillis() / 50.0) * 2);
        LegacyMethods.register("animate_pop", a -> {
            long t = System.currentTimeMillis() % 1000;
            return t < 200 ? t / 200.0 * 1.2 : t < 400 ? 1.2 - (t - 200) / 200.0 * 0.2 : 1;
        });
        LegacyMethods.register("animate_fade_in", a -> Math.min(1, (System.currentTimeMillis() % 5000) / 500.0));
        LegacyMethods.register("animate_fade_out", a -> Math.max(0, 1 - (System.currentTimeMillis() % 5000) / 500.0));
        LegacyMethods.register("animate_slide_in_left", a -> System.currentTimeMillis() % 3000 < 500
                ? -(1 - (System.currentTimeMillis() % 3000) / 500.0) * 100 : 0);
        LegacyMethods.register("animate_slide_out_right", a -> System.currentTimeMillis() % 3000 < 500
                ? (System.currentTimeMillis() % 3000) / 500.0 * 100 : 0);
    }

    private static Object arg(Object[] a, int i) {
        return a != null && i < a.length ? a[i] : null;
    }

    private static String s(Object[] a, int i) {
        return a != null && i < a.length && a[i] != null ? String.valueOf(a[i]) : "";
    }
}
