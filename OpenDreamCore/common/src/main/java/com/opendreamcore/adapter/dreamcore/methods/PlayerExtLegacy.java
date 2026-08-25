package com.opendreamcore.adapter.dreamcore.methods;

import com.opendreamcore.adapter.dreamcore.LegacyMethods;

public final class PlayerExtLegacy {
    private PlayerExtLegacy() { }

    private static Object p(String m, Object... a) {
        return LegacyMethods.delegate("Player", m, a);
    }

    private static Object e(String m, Object... a) {
        return LegacyMethods.delegate("Entity", m, a);
    }

    public static void install() {
        installPlayer();
        installEntity();
        installItem();
    }

    private static void installPlayer() {
        LegacyMethods.register("get_player_name", a -> p("获取名字"));
        LegacyMethods.register("get_player_uuid", a -> p("获取UUID"));
        LegacyMethods.register("get_player_health", a -> p("获取血量"));
        LegacyMethods.register("get_player_max_health", a -> p("获取最大血量"));
        LegacyMethods.register("get_player_food", a -> p("获取饥饿"));
        LegacyMethods.register("get_player_saturation", a -> 20.0);
        LegacyMethods.register("get_player_air", a -> 300.0);
        LegacyMethods.register("get_player_max_air", a -> 300.0);
        LegacyMethods.register("get_player_exp", a -> p("获取经验"));
        LegacyMethods.register("get_player_experience", a -> p("获取经验"));
        LegacyMethods.register("get_player_level", a -> p("获取等级"));
        LegacyMethods.register("get_player_total_experience", a -> p("获取经验"));
        LegacyMethods.register("get_player_gamemode", a -> p("获取游戏模式"));
        LegacyMethods.register("get_player_ping", a -> p("获取延迟"));
        LegacyMethods.register("get_player_x", a -> p("获取X"));
        LegacyMethods.register("get_player_y", a -> p("获取Y"));
        LegacyMethods.register("get_player_z", a -> p("获取Z"));
        LegacyMethods.register("get_player_pos_x", a -> p("获取X"));
        LegacyMethods.register("get_player_pos_y", a -> p("获取Y"));
        LegacyMethods.register("get_player_pos_z", a -> p("获取Z"));
        LegacyMethods.register("get_player_yaw", a -> p("获取视角"));
        LegacyMethods.register("get_player_pitch", a -> p("获取俯仰"));
        LegacyMethods.register("get_player_rotation_yaw", a -> p("获取视角"));
        LegacyMethods.register("get_player_rotation_pitch", a -> p("获取俯仰"));
        LegacyMethods.register("get_player_dimension", a -> "");
        LegacyMethods.register("get_player_biome", a -> p("获取生物群系"));
        LegacyMethods.register("get_player_armor", a -> p("获取护甲"));
        LegacyMethods.register("get_player_armor_helmet", a -> null);
        LegacyMethods.register("get_player_armor_chestplate", a -> null);
        LegacyMethods.register("get_player_armor_leggings", a -> null);
        LegacyMethods.register("get_player_armor_boots", a -> null);
        LegacyMethods.register("get_player_head_texture", a -> p("获取头像"));
        LegacyMethods.register("get_player_score", a -> 0.0);
        LegacyMethods.register("get_player_selected_slot", a -> 0.0);
        LegacyMethods.register("get_player_current_slot", a -> 0.0);
        LegacyMethods.register("get_player_inventory_size", a -> 36.0);
        LegacyMethods.register("get_player_perspective", a -> 0.0);
        LegacyMethods.register("get_player_move_speed", a -> 0.0);
        LegacyMethods.register("get_player_fly_speed", a -> 0.05);
        LegacyMethods.register("get_player_motion_x", a -> 0.0);
        LegacyMethods.register("get_player_motion_y", a -> 0.0);
        LegacyMethods.register("get_player_motion_z", a -> 0.0);
        LegacyMethods.register("get_player_velocity_x", a -> 0.0);
        LegacyMethods.register("get_player_velocity_y", a -> 0.0);
        LegacyMethods.register("get_player_velocity_z", a -> 0.0);
        LegacyMethods.register("is_player_burning", a -> false);
        LegacyMethods.register("is_player_dead", a -> false);
        LegacyMethods.register("is_player_flying", a -> false);
        LegacyMethods.register("is_player_on_ground", a -> true);
        LegacyMethods.register("is_player_sneaking", a -> false);
        LegacyMethods.register("is_player_sprinting", a -> false);
        LegacyMethods.register("is_player_swimming", a -> false);
        LegacyMethods.register("is_player_in_water", a -> false);
        LegacyMethods.register("is_player_in_lava", a -> false);
        LegacyMethods.register("is_player_sleeping", a -> false);
        LegacyMethods.register("is_player_fall_flying", a -> false);
        LegacyMethods.register("respawn", a -> null);
        LegacyMethods.register("player_respawn", a -> null);
        LegacyMethods.register("drop_item", a -> null);
        LegacyMethods.register("item_use_stop", a -> null);

        // 主手/副手物品
        LegacyMethods.register("get_main_hand_item", a -> p("获取主手物品"));
        LegacyMethods.register("get_off_hand_item", a -> p("获取副手物品"));
        LegacyMethods.register("get_player_main_hand_item", a -> p("获取主手物品"));
        LegacyMethods.register("is_holding_item", a -> {
            Object item = p("获取主手物品");
            return item != null && !String.valueOf(item).isEmpty()
                    && !"air".equalsIgnoreCase(String.valueOf(item));
        });
        LegacyMethods.register("get_player_item_id", a -> LegacyMethods.slotItem(a, 0));
        LegacyMethods.register("get_player_item_name", a -> LegacyMethods.slotItem(a, 0));
        LegacyMethods.register("get_player_item_count", a -> 1.0);
        LegacyMethods.register("get_player_item_damage", a -> 0.0);
        LegacyMethods.register("get_player_item_max_damage", a -> 0.0);
        LegacyMethods.register("get_player_item_max_count", a -> 64.0);

        // 冷却
        LegacyMethods.register("set_cooldown", a -> null);
        LegacyMethods.register("reset_cooldown", a -> null);
        LegacyMethods.register("is_in_cooldown", a -> false);
        LegacyMethods.register("get_cooldown_progress", a -> 0.0);
        LegacyMethods.register("get_item_use", a -> false);
        LegacyMethods.register("get_item_use_time", a -> 0.0);
        LegacyMethods.register("get_item_use_time_max", a -> 0.0);
    }

    private static void installEntity() {
        LegacyMethods.register("entity_exists", a -> e("存在", arg(a, 0)));
        LegacyMethods.register("entity_is_rendering", a -> true);
        LegacyMethods.register("get_entity", a -> e("获取指向实体"));
        LegacyMethods.register("get_entity_name", a -> e("获取名字", arg(a, 0)));
        LegacyMethods.register("get_entity_uuid", a -> e("获取UUID", arg(a, 0)));
        LegacyMethods.register("get_entity_health", a -> e("获取血量", arg(a, 0)));
        LegacyMethods.register("get_entity_max_health", a -> e("获取最大血量", arg(a, 0)));
        LegacyMethods.register("get_entity_health_ratio", a -> {
            Object h = e("获取血量", arg(a, 0));
            Object mh = e("获取最大血量", arg(a, 0));
            double hh = h instanceof Number n ? n.doubleValue() : 0;
            double mm = mh instanceof Number n ? n.doubleValue() : 1;
            return mm == 0 ? 0 : hh / mm;
        });
        LegacyMethods.register("get_entity_x", a -> e("获取X", arg(a, 0)));
        LegacyMethods.register("get_entity_y", a -> e("获取Y", arg(a, 0)));
        LegacyMethods.register("get_entity_z", a -> e("获取Z", arg(a, 0)));
        LegacyMethods.register("get_entity_pos_x", a -> e("获取X", arg(a, 0)));
        LegacyMethods.register("get_entity_pos_y", a -> e("获取Y", arg(a, 0)));
        LegacyMethods.register("get_entity_pos_z", a -> e("获取Z", arg(a, 0)));
        LegacyMethods.register("get_entity_yaw", a -> e("获取视角", arg(a, 0)));
        LegacyMethods.register("get_entity_pitch", a -> e("获取俯仰", arg(a, 0)));
        LegacyMethods.register("get_entity_height", a -> 1.8);
        LegacyMethods.register("get_entity_velocity_x", a -> 0.0);
        LegacyMethods.register("get_entity_velocity_y", a -> 0.0);
        LegacyMethods.register("get_entity_velocity_z", a -> 0.0);
        LegacyMethods.register("get_entity_distance", a -> 0.0);
        LegacyMethods.register("get_distance_between", a -> 0.0);
        LegacyMethods.register("distance_to_camera", a -> 0.0);
        LegacyMethods.register("get_nearby_entities", a -> e("获取附近实体", args()));
        LegacyMethods.register("is_entity_burning", a -> false);
        LegacyMethods.register("is_entity_flying", a -> false);
        LegacyMethods.register("is_entity_in_water", a -> false);
        LegacyMethods.register("is_entity_on_ground", a -> true);
        LegacyMethods.register("is_entity_sneaking", a -> false);
        LegacyMethods.register("is_entity_sprinting", a -> false);
        LegacyMethods.register("is_entity_swimming", a -> false);
        LegacyMethods.register("get_entity_name_by_uuid", a -> "");

        // 指向的实体
        LegacyMethods.register("get_pointed_entity", a -> e("获取指向实体"));
        LegacyMethods.register("get_pointed_entity_health", a -> e("获取血量", "pointed"));
        LegacyMethods.register("get_pointed_entity_max_health", a -> e("获取最大血量", "pointed"));
        LegacyMethods.register("get_pointed_entity_uuid", a -> e("获取UUID", "pointed"));

        // 鼠标悬浮实体
        LegacyMethods.register("get_mouse_entity_distance", a -> 0.0);
        LegacyMethods.register("get_mouse_entity_health", a -> 0.0);
        LegacyMethods.register("get_mouse_entity_max_health", a -> 0.0);
        LegacyMethods.register("get_mouse_entity_name", a -> "");
        LegacyMethods.register("get_mouse_entity_uuid", a -> "");
        LegacyMethods.register("is_mouse_entity_adyeshach", a -> false);
    }

    private static void installItem() {
        // 物品槽位
        LegacyMethods.register("get_slot_item", a -> LegacyMethods.slotItem(a, 0));
        LegacyMethods.register("get_slot_lore", a -> LegacyMethods.slotLore(a, 0));
        LegacyMethods.register("get_slot_property", a -> null);
        LegacyMethods.register("click_slot", a -> null);
        LegacyMethods.register("serialize_item", a -> "");
        LegacyMethods.register("deserialize_item", a -> null);

        // 容器物品
        LegacyMethods.register("get_container_item", a -> LegacyMethods.slotItem(a, 0));
        LegacyMethods.register("get_container_items", a -> new java.util.ArrayList<>());
        LegacyMethods.register("get_container_item_count", a -> 0.0);
        LegacyMethods.register("get_container_max_item_count", a -> 64.0);
        LegacyMethods.register("delete_container_item", a -> null);

        // 悬浮物品
        LegacyMethods.register("get_hovered_item_id", a -> "");
        LegacyMethods.register("get_hovered_item_name", a -> "");
        LegacyMethods.register("get_hovered_item_count", a -> 0.0);
        LegacyMethods.register("get_hovered_item_damage", a -> 0.0);
        LegacyMethods.register("get_hovered_item_max_damage", a -> 0.0);
        LegacyMethods.register("get_hovered_item_lore", a -> "");
        LegacyMethods.register("get_hovered_item_lore_text", a -> "");
        LegacyMethods.register("is_hovered_item", a -> false);

        // 物品通用
        LegacyMethods.register("get_item_id", a -> LegacyMethods.slotItem(a, 0));
        LegacyMethods.register("get_item_name", a -> LegacyMethods.slotItem(a, 0));
        LegacyMethods.register("get_item_lore", a -> LegacyMethods.slotLore(a, 0));
        LegacyMethods.register("get_item_all_lore", a -> LegacyMethods.slotLore(a, 0));
        LegacyMethods.register("get_item_lore_count", a -> 0.0);
        LegacyMethods.register("get_item_count", a -> 1.0);
        LegacyMethods.register("get_item_damage", a -> 0.0);
        LegacyMethods.register("get_item_max_damage", a -> 0.0);
        LegacyMethods.register("get_item_max_count", a -> 64.0);
        LegacyMethods.register("get_item_nbt", a -> "");
        LegacyMethods.register("get_item_armor", a -> 0.0);
        LegacyMethods.register("get_all_items", a -> new java.util.ArrayList<>());
    }

    private static Object[] args() {
        return new Object[0];
    }

    private static Object arg(Object[] a, int i) {
        return a != null && i < a.length ? a[i] : null;
    }
}
