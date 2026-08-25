package com.opendreamcore.adapter.dreamcore.methods;

import com.opendreamcore.adapter.dreamcore.LegacyMethods;

public final class ScreenOpsLegacy {
    private ScreenOpsLegacy() { }

    private static Object sc(String m, Object... a) {
        return LegacyMethods.delegate("Screen", m, a);
    }

    public static void install() {
        // 组件创建/删除/查询
        LegacyMethods.register("create_component", a -> sc("创建元素", arg(a, 0), arg(a, 1)));
        LegacyMethods.register("create_element", a -> sc("创建元素", arg(a, 0), arg(a, 1)));
        LegacyMethods.register("delete_component", a -> sc("删除元素", arg(a, 0)));
        LegacyMethods.register("remove_component", a -> sc("删除元素", arg(a, 0)));
        LegacyMethods.register("component_exists", a -> sc("元素存在", arg(a, 0)));
        LegacyMethods.register("get_component", a -> sc("获取元素", arg(a, 0)));
        LegacyMethods.register("get_all_components", a -> sc("获取所有元素"));
        LegacyMethods.register("get_components_by_prefix", a -> sc("按前缀取元素", arg(a, 0)));
        LegacyMethods.register("get_components_by_suffix", a -> sc("按后缀取元素", arg(a, 0)));
        LegacyMethods.register("get_components_by_type", a -> sc("按类型取元素", arg(a, 0)));
        LegacyMethods.register("import_components", a -> null);
        LegacyMethods.register("export_gui", a -> null);
        LegacyMethods.register("import_gui", a -> null);
        LegacyMethods.register("reload_imported_components", a -> null);
        LegacyMethods.register("copy_component_properties", a -> {
            String from = s(a, 0), to = s(a, 1);
            if (from != null && to != null) {
                sc("设置元素", to, "opacity", sc("获取元素", from, "opacity"));
                sc("设置元素", to, "visible", sc("获取元素", from, "visible"));
            }
            return null;
        });

        // 组件属性读写
        LegacyMethods.register("set_component", a -> sc("设置元素", arg(a, 0), arg(a, 1), arg(a, 2)));
        LegacyMethods.register("set_component_position", a -> {
            sc("设置元素", arg(a, 0), "x", arg(a, 1));
            sc("设置元素", arg(a, 0), "y", arg(a, 2));
            return null;
        });
        LegacyMethods.register("move_component", a -> {
            Object cx = sc("获取元素", arg(a, 0), "x");
            Object cy = sc("获取元素", arg(a, 0), "y");
            double dx = num(a, 1), dy = num(a, 2);
            if (cx instanceof Number nx) sc("设置元素", arg(a, 0), "x", nx.doubleValue() + dx);
            if (cy instanceof Number ny) sc("设置元素", arg(a, 0), "y", ny.doubleValue() + dy);
            return null;
        });
        LegacyMethods.register("align_component", a -> sc("设置元素", arg(a, 0), "align", arg(a, 1)));
        LegacyMethods.register("batch_set_property", a -> sc("批量设置", args()));
        LegacyMethods.register("clear_component_properties", a -> null);
        LegacyMethods.register("get_component_value", a -> sc("获取元素", arg(a, 0)));
        LegacyMethods.register("get_component_type", a -> sc("获取元素", arg(a, 0), "type"));
        LegacyMethods.register("get_component_width", a -> sc("获取元素", arg(a, 0), "width"));
        LegacyMethods.register("get_component_height", a -> sc("获取元素", arg(a, 0), "height"));
        LegacyMethods.register("get_component_position", a -> sc("获取元素位置", arg(a, 0)));
        LegacyMethods.register("get_component_center", a -> sc("获取元素位置", arg(a, 0)));

        // 显示/隐藏/切换
        LegacyMethods.register("show_component", a -> sc("显示元素", arg(a, 0)));
        LegacyMethods.register("hide_component", a -> sc("隐藏元素", arg(a, 0)));
        LegacyMethods.register("toggle_component", a -> sc("切换元素", arg(a, 0)));

        // 悬浮
        LegacyMethods.register("get_hovered_component", a -> sc("获取悬浮元素"));
        LegacyMethods.register("get_hovered_component_name", a -> sc("获取悬浮元素名"));
        LegacyMethods.register("get_all_hovered_components", a -> sc("获取所有悬浮元素"));

        // 动画控制
        LegacyMethods.register("play_animation", a -> sc("播放动画", args()));
        LegacyMethods.register("pause_animation", a -> sc("暂停动画", arg(a, 0)));
        LegacyMethods.register("resume_animation", a -> sc("恢复动画", arg(a, 0)));
        LegacyMethods.register("stop_animation", a -> sc("停止动画", arg(a, 0)));
        LegacyMethods.register("remove_animation", a -> sc("停止动画", arg(a, 0)));
        LegacyMethods.register("clear_animations", a -> null);
        LegacyMethods.register("has_animation", a -> false);
        LegacyMethods.register("is_animating", a -> false);
        LegacyMethods.register("is_animation_playing", a -> false);
        LegacyMethods.register("orbit_animation", a -> null);
        LegacyMethods.register("path_animation", a -> null);
        LegacyMethods.register("sequence_animation", a -> null);
        LegacyMethods.register("parallel_animation", a -> null);

        // 界面打开/关闭/重载
        LegacyMethods.register("open_gui", a -> sc("打开页面", arg(a, 0)));
        LegacyMethods.register("close_gui", a -> sc("关闭页面"));
        LegacyMethods.register("open_sub_gui", a -> sc("打开页面", arg(a, 0)));
        LegacyMethods.register("close_main_gui", a -> sc("关闭页面"));
        LegacyMethods.register("open_hud", a -> sc("挂载HUD", arg(a, 0)));
        LegacyMethods.register("close_hud", a -> sc("卸载HUD", arg(a, 0)));
        LegacyMethods.register("reload_gui", a -> sc("设置变量", "_odc_refresh", System.currentTimeMillis()));
        LegacyMethods.register("execute_screen", a -> null);
        LegacyMethods.register("is_screen_closed", a -> false);
        LegacyMethods.register("screen_active_time", a ->
                (double) (System.currentTimeMillis() - openTime));
        LegacyMethods.register("screen_open_time_get", a ->
                (double) (System.currentTimeMillis() - openTime));
        LegacyMethods.register("screen_open_time_reset", a -> {
            openTime = System.currentTimeMillis();
            return null;
        });
        LegacyMethods.register("get_screen_alive_time", a ->
                (double) (System.currentTimeMillis() - openTime));

        // 界面名称
        LegacyMethods.register("get_current_screen_name", a -> sc("当前页面名"));
        LegacyMethods.register("screen_get_name", a -> sc("当前页面名"));
        LegacyMethods.register("screen_original_name", a -> sc("当前页面名"));
        LegacyMethods.register("minecraft_get_screen_name", a -> sc("当前页面名"));

        // 界面特效
        LegacyMethods.register("screen_shake", a -> sc("震动屏幕", arg(a, 0)));
        LegacyMethods.register("screen_earthquake", a -> sc("震动屏幕", arg(a, 0)));
        LegacyMethods.register("shake", a -> sc("震动屏幕", arg(a, 0)));
        LegacyMethods.register("flash_screen", a -> null);
        LegacyMethods.register("screen_set_hide", a -> null);
        LegacyMethods.register("screen_set_show", a -> null);

        // 基准尺寸/缩放模式
        LegacyMethods.register("get_base_width", a -> 1920.0);
        LegacyMethods.register("get_base_height", a -> 1080.0);
        LegacyMethods.register("set_base_size", a -> null);
        LegacyMethods.register("reset_scale_config", a -> null);
        LegacyMethods.register("get_scale_mode", a -> "adaptive");
        LegacyMethods.register("get_scale_mode_name", a -> "自适应");
        LegacyMethods.register("set_scale_mode", a -> null);
        LegacyMethods.register("set_scale_range", a -> null);
        LegacyMethods.register("set_keep_aspect_ratio", a -> null);
        LegacyMethods.register("view_stretch", a -> null);
        LegacyMethods.register("reset_adaptive", a -> null);
        LegacyMethods.register("adapt", a -> null);
        LegacyMethods.register("adaptX", a -> null);
        LegacyMethods.register("adaptY", a -> null);
        LegacyMethods.register("adapt_width", a -> null);
        LegacyMethods.register("adapt_height", a -> null);
        LegacyMethods.register("adapt_font", a -> null);
        LegacyMethods.register("center_x", a -> 0.5);
        LegacyMethods.register("center_y", a -> 0.5);
        LegacyMethods.register("get_center_x", a -> 0.5);
        LegacyMethods.register("get_center_y", a -> 0.5);

        // 屏幕坐标比例
        LegacyMethods.register("get_screen_width_ratio", a -> 1.0);
        LegacyMethods.register("get_screen_height_ratio", a -> 1.0);
        LegacyMethods.register("get_screen_width_adaptive", a -> LegacyMethods.delegate("Display", "getWidth"));
        LegacyMethods.register("get_screen_height_adaptive", a -> LegacyMethods.delegate("Display", "getHeight"));

        // 世界坐标
        LegacyMethods.register("get_world_screen_pos", a -> sc("获取世界元素位置", args()));
        LegacyMethods.register("get_screen_world_pos", a -> sc("获取世界元素位置", args()));

        // 过渡动画
        LegacyMethods.register("start_transition", a -> null);
        LegacyMethods.register("cancel_transition", a -> null);
        LegacyMethods.register("get_transition_progress", a -> 0.0);
        LegacyMethods.register("get_transition_value", a -> 0.0);
        LegacyMethods.register("is_transition_finished", a -> true);
        LegacyMethods.register("set_transition_animation", a -> null);
        LegacyMethods.register("fade", a -> null);
        LegacyMethods.register("slide", a -> null);
        LegacyMethods.register("swing", a -> null);
        LegacyMethods.register("pulse", a -> null);
        LegacyMethods.register("blink", a -> null);
        LegacyMethods.register("breathe", a -> null);
        LegacyMethods.register("wave", a -> null);

        // 卡片（简版占位）
        LegacyMethods.register("add_card", a -> null);
        LegacyMethods.register("add_card_action", a -> null);
        LegacyMethods.register("clear_cards", a -> null);
        LegacyMethods.register("flip_card", a -> null);
        LegacyMethods.register("get_card_state", a -> false);

        // 视频控制
        LegacyMethods.register("play_video", a -> null);
        LegacyMethods.register("pause_video", a -> null);
        LegacyMethods.register("resume_video", a -> null);
        LegacyMethods.register("stop_video", a -> null);
        LegacyMethods.register("restart_video", a -> null);
        LegacyMethods.register("seek_video", a -> null);
        LegacyMethods.register("is_video_playing", a -> false);

        // 其他组件操作
        LegacyMethods.register("process_foreach", a -> null);
        LegacyMethods.register("create_foreach_components", a -> null);
        LegacyMethods.register("execute_component_function", a -> null);
        LegacyMethods.register("cache_update", a -> null);
        LegacyMethods.register("clear_imported_cache", a -> null);
        LegacyMethods.register("loading_complete_step", a -> null);
        LegacyMethods.register("loading_get_percentage", a -> 100.0);
        LegacyMethods.register("loading_get_progress", a -> 1.0);
        LegacyMethods.register("loading_is_completed", a -> true);
        LegacyMethods.register("loading_reset", a -> null);
        LegacyMethods.register("loading_set_total_steps", a -> null);
    }

    private static long openTime = System.currentTimeMillis();

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
