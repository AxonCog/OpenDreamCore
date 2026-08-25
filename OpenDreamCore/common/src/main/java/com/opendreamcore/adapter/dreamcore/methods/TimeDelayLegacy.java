package com.opendreamcore.adapter.dreamcore.methods;

import com.opendreamcore.adapter.dreamcore.LegacyMethods;

public final class TimeDelayLegacy {
    private TimeDelayLegacy() { }

    public static void install() {
        LegacyMethods.register("延迟执行", a -> {
            String script = LegacyMethods.str(a, 1);
            long ms = (long) LegacyMethods.num2(a, 0);
            if (script != null && ms > 0) {
                java.util.concurrent.CompletableFuture.delayedExecutor(ms, java.util.concurrent.TimeUnit.MILLISECONDS)
                        .execute(() -> com.opendreamcore.script.DreamLang.execute(script, null));
            }
            return null;
        });
        LegacyMethods.register("定时执行", a -> {
            String fn = LegacyMethods.str(a, 0);
            long ms = (long) LegacyMethods.num2(a, 1);
            if (fn != null && ms > 0) {
                java.util.concurrent.CompletableFuture.delayedExecutor(ms, java.util.concurrent.TimeUnit.MILLISECONDS)
                        .execute(() -> com.opendreamcore.script.DreamLang.execute(fn + "()", null));
            }
            return null;
        });
        LegacyMethods.register("重复执行", a -> {
            // 简版：只执行一次（完整循环调度需 Script 调度器配合）
            String fn = LegacyMethods.str(a, 0);
            long interval = (long) LegacyMethods.num2(a, 1);
            if (fn != null) {
                com.opendreamcore.script.DreamLang.execute(fn + "()", null);
            }
            return null;
        });
        LegacyMethods.register("取消所有定时任务", a -> null);
        LegacyMethods.register("取消定时任务", a -> null);
    }
}
