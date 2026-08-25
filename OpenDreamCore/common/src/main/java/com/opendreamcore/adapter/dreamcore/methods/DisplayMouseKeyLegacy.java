package com.opendreamcore.adapter.dreamcore.methods;

import com.opendreamcore.adapter.dreamcore.LegacyMethods;

public final class DisplayMouseKeyLegacy {
    private DisplayMouseKeyLegacy() { }

    private static Object dp(String m, Object... a) {
        return LegacyMethods.delegate("Display", m, a);
    }

    private static Object mo(String m, Object... a) {
        return LegacyMethods.delegate("Mouse", m, a);
    }

    private static Object ky(String m, Object... a) {
        return LegacyMethods.delegate("Key", m, a);
    }

    public static void install() {
        installDisplay();
        installMouse();
        installKey();
    }

    private static void installDisplay() {
        LegacyMethods.register("get_screen_width", a -> dp("getWidth"));
        LegacyMethods.register("get_screen_height", a -> dp("getHeight"));
        LegacyMethods.register("get_screen_width_v2", a -> dp("getWidth"));
        LegacyMethods.register("get_screen_height_v2", a -> dp("getHeight"));
        LegacyMethods.register("get_gui_scale", a -> dp("getScale"));
        LegacyMethods.register("get_gui_scale_factor", a -> dp("getScale"));
        LegacyMethods.register("set_gui_scale", a -> dp("setGuiScale", arg(a, 0)));
        LegacyMethods.register("get_window_scale", a -> dp("getScale"));
        LegacyMethods.register("get_scale", a -> dp("getScale"));
        LegacyMethods.register("window_width", a -> dp("getWidth"));
        LegacyMethods.register("window_height", a -> dp("getHeight"));
        LegacyMethods.register("display_window_width", a -> dp("getWidth"));
        LegacyMethods.register("display_window_height", a -> dp("getHeight"));
        LegacyMethods.register("desktop_width", a -> 1920.0);
        LegacyMethods.register("desktop_height", a -> 1080.0);
        LegacyMethods.register("display_desktop_width", a -> 1920.0);
        LegacyMethods.register("display_desktop_height", a -> 1080.0);
        LegacyMethods.register("is_fullscreen", a -> dp("isFullscreen"));
        LegacyMethods.register("display_is_fullscreen", a -> dp("isFullscreen"));
        LegacyMethods.register("set_fullscreen", a -> dp("toggleFullscreen"));
        LegacyMethods.register("display_set_fullscreen", a -> dp("toggleFullscreen"));
        LegacyMethods.register("is_resizable", a -> true);
        LegacyMethods.register("display_is_resizable", a -> true);
        LegacyMethods.register("set_resizable", a -> null);
        LegacyMethods.register("display_set_resizable", a -> null);
        LegacyMethods.register("resize_window", a -> null);
        LegacyMethods.register("display_resize", a -> null);
        LegacyMethods.register("window_x", a -> 0.0);
        LegacyMethods.register("window_y", a -> 0.0);
        LegacyMethods.register("display_window_x", a -> 0.0);
        LegacyMethods.register("display_window_y", a -> 0.0);
        LegacyMethods.register("display_location", a -> "0,0");
        LegacyMethods.register("set_window_location", a -> null);
        LegacyMethods.register("logical_to_px", a -> num(a, 0));
        LegacyMethods.register("px_to_logical", a -> num(a, 0));
        LegacyMethods.register("px_to_absolute", a -> num(a, 0));
        LegacyMethods.register("set_px_scale_factor", a -> null);
    }

    private static void installMouse() {
        LegacyMethods.register("get_mouse_x", a -> mo("getX"));
        LegacyMethods.register("get_mouse_y", a -> mo("getY"));
        LegacyMethods.register("get_mouse_x_v2", a -> mo("getX"));
        LegacyMethods.register("get_mouse_y_v2", a -> mo("getY"));
        LegacyMethods.register("get_mouse_delta_x", a -> mo("getScaledX"));
        LegacyMethods.register("get_mouse_delta_y", a -> mo("getScaledY"));
        LegacyMethods.register("get_mouse_wheel", a -> 0.0);
        LegacyMethods.register("move_mouse", a -> null);
        LegacyMethods.register("reset_mouse", a -> null);
        LegacyMethods.register("has_custom_mouse", a -> false);
        LegacyMethods.register("set_mouse_texture", a -> mo("setTexture", arg(a, 0)));
    }

    private static void installKey() {
        LegacyMethods.register("key_is_pressed", a -> ky("isKeyDown", arg(a, 0)));
        LegacyMethods.register("control_key_is_pressed", a -> ky("isKeyDown", arg(a, 0)));
        LegacyMethods.register("get_key_name", a -> ky("getKeyName", arg(a, 0)));
        LegacyMethods.register("get_control_key_name", a -> ky("getKeyName", arg(a, 0)));
        LegacyMethods.register("get_control_key_extra", a -> "");
        LegacyMethods.register("get_current_pressed_key", a -> "");
        LegacyMethods.register("simulate_key_press", a -> null);
        LegacyMethods.register("execute_key_command", a -> null);
        LegacyMethods.register("set_control_key", a -> null);
    }

    private static double num(Object[] a, int i) {
        return a != null && i < a.length && a[i] instanceof Number n ? n.doubleValue() : 0;
    }

    private static Object arg(Object[] a, int i) {
        return a != null && i < a.length ? a[i] : null;
    }
}
