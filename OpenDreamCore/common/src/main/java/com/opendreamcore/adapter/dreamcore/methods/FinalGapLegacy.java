package com.opendreamcore.adapter.dreamcore.methods;

import com.opendreamcore.adapter.dreamcore.LegacyMethods;

public final class FinalGapLegacy {
    private FinalGapLegacy() { }

    public static void install() {
        LegacyMethods.register("add_component", a ->
                LegacyMethods.delegate("Screen", "创建元素", arg(a, 0), arg(a, 1)));
        LegacyMethods.register("animate_effect", a -> 0.0);
        LegacyMethods.register("animate_two_stage", a -> 0.0);
        LegacyMethods.register("back", a -> null);
        LegacyMethods.register("clear_transitions", a -> null);
        LegacyMethods.register("get_animate_effect", a -> "");
        LegacyMethods.register("get_animation_layer", a -> "");
        LegacyMethods.register("get_block_name", a -> "");
        LegacyMethods.register("get_clipboard", a -> clipboardGet());
        LegacyMethods.register("set_clipboard", a -> {
            try {
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new java.awt.datatransfer.StringSelection(str(a, 0)), null);
            } catch (Exception ignored) { }
            return null;
        });
        LegacyMethods.register("get_container_item_id", a -> LegacyMethods.slotItem(a, 0));
        LegacyMethods.register("get_fps", a -> LegacyMethods.delegate("Display", "getFPS"));
        LegacyMethods.register("get_online_players_dict", a ->
                LegacyMethods.delegate("Player", "获取在线玩家字典"));
        LegacyMethods.register("get_opengl_time", a ->
                (double) (System.currentTimeMillis() % 100000));
        LegacyMethods.register("get_player_pos", a -> new double[]{
                dbl(LegacyMethods.delegate("Player", "获取X")),
                dbl(LegacyMethods.delegate("Player", "获取Y")),
                dbl(LegacyMethods.delegate("Player", "获取Z"))});
        LegacyMethods.register("get_px_description", a -> "");
        LegacyMethods.register("insert", a -> {
            String src = str(a, 0);
            int idx = (int) num(a, 1);
            String piece = str(a, 2);
            if (src == null) return piece;
            idx = Math.max(0, Math.min(src.length(), idx));
            return src.substring(0, idx) + (piece == null ? "" : piece) + src.substring(idx);
        });
        LegacyMethods.register("is_on_screen", a -> false);
        LegacyMethods.register("particle_burst", a -> null);
        LegacyMethods.register("remove", a -> {
            String s = str(a, 0);
            String target = str(a, 1);
            return s != null && target != null ? s.replace(target, "") : s;
        });
        LegacyMethods.register("replace_placeholder", a -> {
            String text = str(a, 0);
            if (text == null) return "";
            for (int i = 1; i + 1 < len(a); i += 2) {
                String k = str(a, i);
                String v = str(a, i + 1);
                if (k != null) text = text.replace("%" + k + "%", v == null ? "" : v);
            }
            return text;
        });
        LegacyMethods.register("send_command", a ->
                LegacyMethods.delegate("Player", "发送命令", arg(a, 0)));
        LegacyMethods.register("set_close_wait_time", a -> null);
        LegacyMethods.register("set_delayed_variable", a -> {
            long ms = (long) num(a, 0);
            String name = str(a, 1);
            Object val = arg(a, 2);
            if (name != null && ms >= 0) {
                java.util.concurrent.CompletableFuture.delayedExecutor(ms,
                                java.util.concurrent.TimeUnit.MILLISECONDS)
                        .execute(() -> LegacyMethods.delegate("Var", "设置变量", name, val));
            }
            return null;
        });
        LegacyMethods.register("set_mouse", a -> LegacyMethods.delegate("Mouse", "setMouse", args()));
        LegacyMethods.register("set_shake_animation", a -> null);
        LegacyMethods.register("spring", a -> num(a, 0));
        LegacyMethods.register("sub_array", a -> {
            Object listObj = arg(a, 0);
            if (!(listObj instanceof java.util.List)) return new java.util.ArrayList<>();
            java.util.List<?> l = (java.util.List<?>) listObj;
            int st = Math.max(0, (int) num(a, 1));
            int en = Math.min(l.size(), (int) num(a, 2));
            return st <= en ? new java.util.ArrayList<>(l.subList(st, en)) : new java.util.ArrayList<>();
        });
        // 中文别名
        LegacyMethods.register("清除界面变量", a -> null);
        LegacyMethods.register("清除组件属性", a -> null);
    }

    private static String clipboardGet() {
        try {
            var cb = java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();
            return (String) cb.getContents(null).getTransferData(
                    java.awt.datatransfer.DataFlavor.stringFlavor);
        } catch (Exception e) { return ""; }
    }

    private static double dbl(Object o) {
        return o instanceof Number n ? n.doubleValue() : 0;
    }

    private static int len(Object[] a) {
        return a == null ? 0 : a.length;
    }

    private static double num(Object[] a, int i) {
        return a != null && i < a.length && a[i] instanceof Number n ? n.doubleValue() : 0;
    }

    private static Object arg(Object[] a, int i) {
        return a != null && i < a.length ? a[i] : null;
    }

    private static Object[] args() {
        return new Object[0];
    }

    private static String str(Object[] a, int i) {
        return a != null && i < a.length && a[i] != null ? String.valueOf(a[i]) : null;
    }
}
