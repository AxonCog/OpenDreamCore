package com.opendreamcore.adapter.dreamcore.methods;

import com.opendreamcore.adapter.dreamcore.LegacyMethods;

public final class ChatSoundScheduleLegacy {
    private ChatSoundScheduleLegacy() { }

    private static Object ch(String m, Object... a) {
        return LegacyMethods.delegate("Chat", m, a);
    }

    private static Object so(String m, Object... a) {
        return LegacyMethods.delegate("Sound", m, a);
    }

    private static Object mu(String m, Object... a) {
        return LegacyMethods.delegate("Music", m, a);
    }

    public static void install() {
        installChat();
        installNotify();
        installSound();
        installSchedule();
    }

    private static void installChat() {
        LegacyMethods.register("send_message", a -> ch("发送消息", arg(a, 0)));
        LegacyMethods.register("send_chat", a -> ch("发送消息", arg(a, 0)));
        LegacyMethods.register("send_chat_message", a -> ch("发送消息", arg(a, 0)));
        LegacyMethods.register("chat_say", a -> ch("发送消息", arg(a, 0)));
        LegacyMethods.register("send_colored_message", a -> ch("发送消息", arg(a, 0)));
        LegacyMethods.register("send_rich_message", a -> ch("发送消息", arg(a, 0)));
        LegacyMethods.register("send_message_with_bg", a -> ch("发送消息", arg(a, 0)));
        LegacyMethods.register("send_action_bar", a -> null);
        LegacyMethods.register("send_actionbar", a -> null);
        LegacyMethods.register("simulate_message", a -> ch("添加消息", arg(a, 0)));
        LegacyMethods.register("get_last_message", a -> ch("获取最后消息"));
        LegacyMethods.register("get_current_message", a -> ch("获取最后消息"));
        LegacyMethods.register("is_chat_opened", a -> false);
        LegacyMethods.register("open_chat", a -> ch("打开聊天"));
        LegacyMethods.register("open_chat_gui", a -> ch("打开聊天"));
        LegacyMethods.register("get_chat_gui", a -> null);
        LegacyMethods.register("set_chat_gui", a -> null);
        LegacyMethods.register("send_title", a -> LegacyMethods.delegate("Title", "showTitle", args()));
        LegacyMethods.register("show_title", a -> LegacyMethods.delegate("Title", "showTitle", args()));
        LegacyMethods.register("send_subtitle", a -> null);
        LegacyMethods.register("send_packet", a -> null);
        LegacyMethods.register("send_packet_enhanced", a -> null);

        // 界面/系统打开
        LegacyMethods.register("open_advancements", a -> null);
        LegacyMethods.register("open_statistics", a -> null);
        LegacyMethods.register("open_game_options", a -> null);
        LegacyMethods.register("open_web_url", a -> {
            try {
                String url = s(a, 0);
                if (url != null && !url.isBlank()) {
                    java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
                }
            } catch (Exception ignored) { }
            return null;
        });
        LegacyMethods.register("game_advancements", a -> null);
        LegacyMethods.register("game_stats", a -> null);
        LegacyMethods.register("game_options", a -> null);
        LegacyMethods.register("minecraft_advancements", a -> null);
        LegacyMethods.register("minecraft_options", a -> null);
        LegacyMethods.register("minecraft_statistics", a -> null);
    }

    private static void installNotify() {
        LegacyMethods.register("notify", a -> LegacyMethods.delegate("Tip", "show", args()));
        LegacyMethods.register("notify_success", a -> LegacyMethods.delegate("Tip", "show", "§a" + s(a, 0)));
        LegacyMethods.register("notify_error", a -> LegacyMethods.delegate("Tip", "show", "§c" + s(a, 0)));
        LegacyMethods.register("notify_warning", a -> LegacyMethods.delegate("Tip", "show", "§e" + s(a, 0)));
    }

    private static void installSound() {
        LegacyMethods.register("play_sound", a -> so("play",
                a != null && a.length > 0 ? a[0] : null, 1.0));
        LegacyMethods.register("play_sound_full", a -> so("play",
                a != null && a.length > 0 ? a[0] : null,
                a != null && a.length > 1 ? num(a, 1) : 1.0));
        LegacyMethods.register("play_button_sound", a -> so("play", "ui.button.click", 1.0));
        LegacyMethods.register("play_click_sound", a -> so("play", "ui.button.click", 1.0));
        LegacyMethods.register("stop_sound", a -> so("stop"));
        LegacyMethods.register("play_music2", a -> mu("play",
                a != null && a.length > 0 ? a[0] : null, 0.8, true));
        LegacyMethods.register("play_music_full", a -> mu("play",
                a != null && a.length > 0 ? a[0] : null,
                a != null && a.length > 1 ? num(a, 1) : 1.0,
                a != null && a.length > 2 && Boolean.TRUE.equals(a[2])));
        LegacyMethods.register("play_music", a -> mu("play",
                a != null && a.length > 0 ? a[0] : null, 1.0, false));
        LegacyMethods.register("set_sound_volume", a -> mu("setVolume", arg(a, 0)));
        LegacyMethods.register("play_sound_timeline", a -> null);
        LegacyMethods.register("pause_sound_timeline", a -> null);
        LegacyMethods.register("resume_sound_timeline", a -> null);
        LegacyMethods.register("stop_sound_timeline", a -> null);
    }

    private static void installSchedule() {
        // 时间
        LegacyMethods.register("now", a -> (double) System.currentTimeMillis());
        LegacyMethods.register("current_time_millis", a -> (double) System.currentTimeMillis());
        LegacyMethods.register("current_time_formatted", a ->
                java.time.LocalTime.now().toString());
        LegacyMethods.register("get_current_time", a ->
                java.time.LocalTime.now().withNano(0).toString());
        LegacyMethods.register("get_current_time_format", a ->
                java.time.LocalTime.now().withNano(0).toString());
        LegacyMethods.register("get_tick_counter", a -> 0.0);
        LegacyMethods.register("reset_tick_counter", a -> null);
        LegacyMethods.register("thread_sleep", a -> {
            try { Thread.sleep((long) num(a, 0)); } catch (InterruptedException ignored) { }
            return null;
        });

        // 调度
        LegacyMethods.register("delay", a -> null);
        LegacyMethods.register("delay_execute", a -> {
            long ms = (long) num(a, 0);
            Object fn = arg(a, 1);
            if (ms >= 0) delayed(ms, fn);
            return null;
        });
        LegacyMethods.register("delay_execute_repeated", a -> {
            long interval = (long) num(a, 0);
            Object fn = arg(a, 1);
            long times = a.length > 2 ? (long) num(a, 2) : Long.MAX_VALUE;
            for (long k = 0; k < Math.min(times, 100); k++) {
                delayed(interval * (k + 1), fn);
            }
            return null;
        });
        LegacyMethods.register("schedule_function", a -> {
            delayed((long) num(a, 0), arg(a, 1));
            return null;
        });
        LegacyMethods.register("schedule_function_repeat", a -> {
            long interval = (long) num(a, 0);
            Object fn = arg(a, 1);
            for (long k = 1; k <= 10; k++) delayed(interval * k, fn);
            return null;
        });
        LegacyMethods.register("unschedule_function", a -> null);
        LegacyMethods.register("unschedule_all_functions", a -> null);
        LegacyMethods.register("cancel_all_delayed", a -> null);
        LegacyMethods.register("cancel_delayed_expr", a -> null);
        LegacyMethods.register("cancel_delayed_variable", a -> null);
        LegacyMethods.register("clear_delayed_variables", a -> null);
        LegacyMethods.register("clear_delayed_expr", a -> null);
        LegacyMethods.register("get_scheduled_task_ids", a -> new java.util.ArrayList<>());
        LegacyMethods.register("get_scheduled_task_info", a -> "");
        LegacyMethods.register("get_delayed_remaining_time", a -> 0.0);
        LegacyMethods.register("get_delayed_expr_remaining_time", a -> 0.0);
        LegacyMethods.register("get_delayed_variable", a -> null);
        LegacyMethods.register("get_delayed_expr_value", a -> null);
        LegacyMethods.register("await", a -> null);
        LegacyMethods.register("loop", a -> null);
        LegacyMethods.register("loop_until", a -> null);
        LegacyMethods.register("repeat", a -> null);

        // 执行
        LegacyMethods.register("run_on_main_thread", a -> {
            delayed(0, arg(a, 0));
            return null;
        });
        LegacyMethods.register("execute_async", a -> {
            new Thread(() -> invokeFn(arg(a, 0))).start();
            return null;
        });
        LegacyMethods.register("execute_sync", a -> {
            invokeFn(arg(a, 0));
            return null;
        });
        LegacyMethods.register("execute_method", a -> {
            invokeFn(arg(a, 0));
            return null;
        });

        // 函数注册
        LegacyMethods.register("register_function", a -> null);
        LegacyMethods.register("call_function", a -> null);
        LegacyMethods.register("execute_function", a -> null);
        LegacyMethods.register("has_function", a -> false);
        LegacyMethods.register("call_script_function", a -> null);
        LegacyMethods.register("import_script", a -> null);
        LegacyMethods.register("load_js", a -> null);
        LegacyMethods.register("reload_script", a -> null);
        LegacyMethods.register("is_script_loaded", a -> false);
        LegacyMethods.register("is_shader_active", a -> false);
        LegacyMethods.register("get_script_export", a -> "");

        // 高级
        LegacyMethods.register("advanced_execute", a -> null);
        LegacyMethods.register("if_statement", a -> {
            boolean cond = Boolean.TRUE.equals(arg(a, 0));
            Object branch = cond ? arg(a, 1) : (a.length > 2 ? arg(a, 2) : null);
            if (branch != null) invokeFn(branch);
            return null;
        });
        LegacyMethods.register("debug", a -> {
            System.out.println("[ODC-debug] " + (a.length > 0 ? String.valueOf(a[0]) : ""));
            return null;
        });
        LegacyMethods.register("println", a -> {
            System.out.println("[ODC] " + (a.length > 0 ? String.valueOf(a[0]) : ""));
            return null;
        });
        LegacyMethods.register("disconnect", a -> null);
        LegacyMethods.register("quit_game", a -> null);
        LegacyMethods.register("shutdown_game", a -> null);
        LegacyMethods.register("game_quit", a -> null);
        LegacyMethods.register("game_shutdown", a -> null);
        LegacyMethods.register("lock_features", a -> null);
        LegacyMethods.register("disable_pressure", a -> null);
        LegacyMethods.register("enable_pressure", a -> null);
        LegacyMethods.register("enable_ripple", a -> null);
    }

    private static void delayed(long ms, Object fn) {
        if (fn == null) return;
        java.util.concurrent.CompletableFuture.delayedExecutor(
                        Math.max(0, ms), java.util.concurrent.TimeUnit.MILLISECONDS)
                .execute(() -> invokeFn(fn));
    }

    private static void invokeFn(Object fn) {
        if (fn == null) return;
        String code = String.valueOf(fn).trim();
        if (code.isEmpty()) return;
        if (!code.endsWith(")") && !code.contains("(")) code = code + "()";
        try {
            com.opendreamcore.script.DreamLang.execute(code, null);
        } catch (Throwable t) {
            System.out.println("[ODC-fn-error] " + t.getMessage());
        }
    }

    private static Object[] args() {
        return new Object[0];
    }

    private static double num(Object[] a, int i) {
        return a != null && i < a.length && a[i] instanceof Number n ? n.doubleValue() : 0;
    }

    private static Object arg(Object[] a, int i) {
        return a != null && i < a.length ? a[i] : null;
    }

    private static String s(Object[] a, int i) {
        return a != null && i < a.length && a[i] != null ? String.valueOf(a[i]) : "";
    }
}
