package com.opendreamcore.adapter.dreamcore.methods;

import com.opendreamcore.adapter.dreamcore.LegacyMethods;

public final class TimeVarLegacy {
    private TimeVarLegacy() { }

    public static void install() {
        LegacyMethods.register("取当前时间戳", a -> LegacyMethods.delegate("Time", "当前毫秒"));
        LegacyMethods.register("延时替换变量", a -> {
            long ms = (long) LegacyMethods.num2(a, 0);
            String name = LegacyMethods.str(a, 1);
            Object val = a != null && a.length > 2 ? a[2] : null;
            if (name != null && ms > 0) {
                java.util.concurrent.CompletableFuture.delayedExecutor(ms,
                        java.util.concurrent.TimeUnit.MILLISECONDS)
                        .execute(() -> LegacyMethods.delegate("Var", "设置", name, val));
            }
            return null;
        });
        LegacyMethods.register("延迟替换", a -> LegacyMethods.str(a, 0));
        LegacyMethods.register("延迟设置变量", a -> LegacyMethods.str(a, 0));
        LegacyMethods.register("设置临时变量", a -> LegacyMethods.delegate("Var",
                "设置", a != null && a.length > 0 ? a[0] : null,
                a != null && a.length > 1 ? a[1] : null));
        LegacyMethods.register("获取临时变量", a -> LegacyMethods.delegate("Var",
                "获取", a != null && a.length > 0 ? a[0] : null));
        LegacyMethods.register("合并加入文本", a -> {
            StringBuilder sb = new StringBuilder();
            for (Object x : a) { if (x != null) sb.append(x); }
            return sb.toString();
        });
        LegacyMethods.register("替换占位符", a -> {
            String text = LegacyMethods.str(a, 0);
            if (text == null) return "";
            for (int i = 1; i + 1 < a.length; i += 2) {
                String k = LegacyMethods.str(a, i);
                String v = LegacyMethods.str(a, i + 1);
                if (k != null) text = text.replace("%" + k + "%", v == null ? "" : v);
            }
            return text;
        });
        LegacyMethods.register("替换正则", a -> {
            String s = LegacyMethods.str(a, 0);
            String pat = LegacyMethods.str(a, 1);
            String rep = LegacyMethods.str(a, 2);
            if (s == null || pat == null) return s;
            try { return s.replaceAll(pat, rep == null ? "" : rep); } catch (Exception e) { return s; }
        });
        LegacyMethods.register("替换首次", a -> {
            String s = LegacyMethods.str(a, 0);
            if (s == null || a.length < 3) return s;
            return s.replaceFirst(LegacyMethods.str(a, 1),
                    LegacyMethods.str(a, 2) == null ? "" : LegacyMethods.str(a, 2));
        });
    }
}
