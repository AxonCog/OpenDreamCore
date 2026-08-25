package com.opendreamcore.adapter.dreamcore.methods;

import com.opendreamcore.adapter.dreamcore.LegacyMethods;

import java.nio.file.Path;
import java.util.Map;

public final class ShaderYamlMiscLegacy {
    private ShaderYamlMiscLegacy() { }

    public static void install() {
        installShader();
        installYaml();
        installWaypoint();
        installCinematic();
        installPerspective();
        installGifTexture();
        installFurnace();
    }

    private static void installShader() {
        LegacyMethods.register("bind_shader", a -> null);
        LegacyMethods.register("unbind_shader", a -> null);
        LegacyMethods.register("compile_shader", a -> null);
        LegacyMethods.register("reload_shaders", a -> null);
        LegacyMethods.register("set_shader_parameter", a -> null);
        LegacyMethods.register("set_shader_parameter_vec2", a -> null);
        LegacyMethods.register("set_shader_parameter_vec3", a -> null);
        LegacyMethods.register("set_shader_parameter_vec4", a -> null);
        LegacyMethods.register("get_shader_time", a -> (double) (System.currentTimeMillis() % 100000));
        LegacyMethods.register("is_shader_active", a -> false);
        LegacyMethods.register("create_text_texture", a -> null);
        LegacyMethods.register("create_text_texture_with_bg", a -> null);
        LegacyMethods.register("create_gradient_text_texture", a -> null);
        LegacyMethods.register("get_text_texture_size", a -> new double[]{0, 0});
        LegacyMethods.register("clear_text_texture_cache", a -> null);
    }

    private static void installYaml() {
        LegacyMethods.register("yaml_file_exists", a -> {
            String f = str(a, 0);
            return f != null && java.nio.file.Files.isRegularFile(
                    GameDir.get().toPath().resolve("OpenDreamCore").resolve(f + ".yaml"));
        });
        LegacyMethods.register("get_yaml_value", a -> get(a));
        LegacyMethods.register("get_yaml_keys", a -> keys(a));
        LegacyMethods.register("get_yaml_all_keys", a -> keys(a));
    }

    private static void installWaypoint() {
        LegacyMethods.register("add_waypoint", a -> null);
        LegacyMethods.register("add_waypoint_here", a -> null);
        LegacyMethods.register("remove_waypoint", a -> null);
        LegacyMethods.register("clear_waypoints", a -> null);
        LegacyMethods.register("get_waypoints", a -> new java.util.ArrayList<>());
        LegacyMethods.register("get_waypoint_count", a -> 0.0);
        LegacyMethods.register("get_nearest_waypoint", a -> "");
        LegacyMethods.register("get_waypoint_distance", a -> 0.0);
        LegacyMethods.register("get_waypoint_angle", a -> 0.0);
        LegacyMethods.register("toggle_waypoint", a -> null);
        LegacyMethods.register("set_waypoint_on_compass", a -> null);
    }

    private static void installCinematic() {
        LegacyMethods.register("cinematic_start", a -> null);
        LegacyMethods.register("cinematic_stop", a -> null);
        LegacyMethods.register("cinematic_pause", a -> null);
        LegacyMethods.register("cinematic_resume", a -> null);
        LegacyMethods.register("cinematic_seek", a -> null);
        LegacyMethods.register("cinematic_is_playing", a -> false);
        LegacyMethods.register("cinematic_get_progress", a -> 0.0);
        LegacyMethods.register("cinematic_get_remaining_time", a -> 0.0);
        LegacyMethods.register("cinematic_face_player", a -> null);
    }

    private static void installPerspective() {
        LegacyMethods.register("set_perspective_transition", a -> null);
        LegacyMethods.register("set_perspective_transition_duration", a -> null);
        LegacyMethods.register("toggle_perspective_transition", a -> null);
        LegacyMethods.register("is_perspective_transitioning", a -> false);
        LegacyMethods.register("is_perspective_transition_enabled", a -> false);
        LegacyMethods.register("get_perspective_transition_progress", a -> 0.0);
        LegacyMethods.register("zoom_out", a -> null);
        LegacyMethods.register("zoom_out_with_pitch", a -> null);
        LegacyMethods.register("set_third_person", a ->
                LegacyMethods.delegate("Player", "切换视角"));
        LegacyMethods.register("set_fov", a -> null);
    }

    private static void installGifTexture() {
        LegacyMethods.register("get_gif_start_time", a -> 0.0);
        LegacyMethods.register("get_gif_time", a -> 0.0);
        LegacyMethods.register("set_gif_index", a -> null);
        LegacyMethods.register("set_gif_playing", a -> null);
    }

    private static void installFurnace() {
        LegacyMethods.register("is_furnace_burning", a -> false);
        LegacyMethods.register("get_furnace_progress", a -> 0.0);
        LegacyMethods.register("get_furnace_burn_time", a -> 0.0);
    }

    private static Object get(Object[] a) {
        String file = str(a, 0);
        String key = str(a, 1);
        if (file == null || key == null) return null;
        try {
            Path p = GameDir.get().toPath()
                    .resolve("OpenDreamCore").resolve(file + ".yaml");
            if (!java.nio.file.Files.isRegularFile(p)) return null;
            var data = new org.yaml.snakeyaml.Yaml().load(java.nio.file.Files.readString(p));
            if (!(data instanceof Map)) return null;
            Object cur = data;
            for (String part : key.split("\\.")) {
                if (!(cur instanceof Map)) return null;
                cur = ((Map<?, ?>) cur).get(part);
            }
            return cur;
        } catch (Exception e) { return null; }
    }

    @SuppressWarnings("unchecked")
    private static Object keys(Object[] a) {
        Object v = get(a);
        if (v instanceof Map) return new java.util.ArrayList<>(((Map<Object, Object>) v).keySet());
        return new java.util.ArrayList<>();
    }

    private static String str(Object[] a, int i) {
        return a != null && i < a.length && a[i] != null ? String.valueOf(a[i]) : null;
    }
}
