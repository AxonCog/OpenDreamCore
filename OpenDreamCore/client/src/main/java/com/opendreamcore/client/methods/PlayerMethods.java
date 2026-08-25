package com.opendreamcore.client.methods;

import com.opendreamcore.client.AnimationEngine;
import com.opendreamcore.client.ClientController;
import com.opendreamcore.client.CompatRender;
import com.opendreamcore.client.FfmpegVideoPlayer;
import com.opendreamcore.client.LegacyText;
import com.opendreamcore.client.MusicPlayer;
import com.opendreamcore.client.SoundStore;
import com.opendreamcore.client.UiRenderer;
import com.opendreamcore.client.UiStyle;
import com.opendreamcore.page.Page;
import com.opendreamcore.script.Easing;
import com.opendreamcore.script.NamespaceRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.biome.Biome;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * C1 拆分自 ClientMethods。// Player 命名空间
 */
public final class PlayerMethods {

    private PlayerMethods() {
    }

    public static void register() {
        NamespaceRegistry.register("Player", args -> ClientMethodSupport.name(), "获取名字", "getName", "get_name");
        NamespaceRegistry.register("Player", args -> ClientMethodSupport.health(), "获取血量", "getHealth", "get_health");
        NamespaceRegistry.register("Player", args -> ClientMethodSupport.maxHealth(), "获取最大血量", "getMaxHealth", "get_max_health");
        NamespaceRegistry.register("Player", args -> ClientMethodSupport.hunger(), "获取饥饿值", "getHunger", "get_hunger");
        NamespaceRegistry.register("Player", args -> ClientMethodSupport.exp(), "获取经验值", "getExp", "get_exp");
        NamespaceRegistry.register("Player", args -> ClientMethodSupport.level(), "获取等级", "getLevel", "get_level");
        NamespaceRegistry.register("Player", args -> ClientMethodSupport.x(), "获取坐标X", "getX", "get_x");
        NamespaceRegistry.register("Player", args -> ClientMethodSupport.y(), "获取坐标Y", "getY", "get_y");
        NamespaceRegistry.register("Player", args -> ClientMethodSupport.z(), "获取坐标Z", "getZ", "get_z");
        NamespaceRegistry.register("Player", args -> ClientMethodSupport.yaw(), "获取偏航角", "getYaw", "get_yaw");
        NamespaceRegistry.register("Player", args -> ClientMethodSupport.pitch(), "获取俯仰角", "getPitch", "get_pitch");
        NamespaceRegistry.register("Player", args -> ClientMethodSupport.gamemode(), "获取游戏模式", "getGameMode", "get_gamemode");
        NamespaceRegistry.register("Player", args -> ClientMethodSupport.biome(), "获取生物群系", "getBiome", "get_biome");
        NamespaceRegistry.register("Player", args -> ClientMethodSupport.language(), "获取语言", "getLanguage", "get_language");
        NamespaceRegistry.register("Player", args -> ClientMethodSupport.onlineTime(), "在线时长", "getOnlineTime", "get_online_time");
        // 玩家状态
        NamespaceRegistry.register("Player", args -> ClientMethodSupport.player() != null && ClientMethodSupport.player().isShiftKeyDown(),
                "是否潜行", "isSneaking", "is_sneaking", "sneaking");
        NamespaceRegistry.register("Player", args -> ClientMethodSupport.player() != null && ClientMethodSupport.player().isSprinting(),
                "是否疾跑", "isSprinting", "is_sprinting", "sprinting");
        NamespaceRegistry.register("Player", args -> ClientMethodSupport.player() != null && ClientMethodSupport.player().getAbilities().flying,
                "是否飞行", "isFlying", "is_flying", "flying");
        NamespaceRegistry.register("Player", args -> ClientMethodSupport.player() != null && ClientMethodSupport.player().isSwimming(),
                "是否游泳", "isSwimming", "is_swimming", "swimming");
        NamespaceRegistry.register("Player", args -> ClientMethodSupport.player() != null && ClientMethodSupport.player().isOnFire(),
                "是否着火", "isOnFire", "is_on_fire", "onFire");
        NamespaceRegistry.register("Player", args -> ClientMethodSupport.player() != null && ClientMethodSupport.player().isInWater(),
                "是否在水中", "isInWater", "is_in_water", "inWater");
        NamespaceRegistry.register("Player", args -> ClientMethodSupport.player() != null && ClientMethodSupport.player().onGround(),
                "是否在地面", "isOnGround", "is_on_ground", "onGround");
        NamespaceRegistry.register("Player", args -> ClientMethodSupport.player() != null
                        && CompatRender.boolQuery(ClientMethodSupport.player(), new String[]{"isInWaterRainOrBubble", "isInWaterRainOrBubble"}, false),
                "是否淋雨", "isInRain", "is_in_rain", "inRain");
        NamespaceRegistry.register("Player", args -> ClientMethodSupport.player() != null && ClientMethodSupport.player().level() != null
                        && CompatRender.boolQuery(ClientMethodSupport.player().level(), new String[]{"isNight", "isNighttime"}, false),
                "是否夜间", "isNight", "is_night", "night");
        NamespaceRegistry.register("Player", args -> ClientMethodSupport.player() != null && ClientMethodSupport.player().level() != null
                        && CompatRender.boolQuery(ClientMethodSupport.player().level(), new String[]{"isDay", "isDaytime"}, true),
                "是否白天", "isDay", "is_day", "day");
        NamespaceRegistry.register("Player", args -> ClientMethodSupport.player() != null && ClientMethodSupport.player().isPassenger(),
                "是否乘骑", "isRiding", "is_riding", "riding");
        NamespaceRegistry.register("Player", args -> ClientMethodSupport.player() != null && ClientMethodSupport.player().isFallFlying(),
                "是否滑翔", "isGliding", "is_gliding", "gliding", "isFallFlying");
        NamespaceRegistry.register("Player", args -> {
            var p = ClientMethodSupport.player();
            if (p == null) return List.of(0.0, 0.0, 0.0);
            var v = p.getDeltaMovement();
            return List.of(v.x, v.y, v.z);
        }, "速度", "getVelocity", "get_velocity", "velocity");
        NamespaceRegistry.register("Player", args -> {
            var p = ClientMethodSupport.player();
            if (p == null) return 0.0;
            return (double) net.minecraft.client.Minecraft.getInstance().getConnection().getPlayerInfo(p.getUUID()).getLatency();
        }, "延迟", "getPing", "get_ping", "ping");
        NamespaceRegistry.register("Player", args -> {
            var p = ClientMethodSupport.player();
            if (p == null) return false;
            return p.level() != null && p.level().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, p.getBoundingBox().inflate(8)).size() > 1;
        }, "附近有实体", "hasNearbyEntities", "has_nearby_entities", "nearbyExists");
        NamespaceRegistry.register("Player", args -> {
            var p = ClientMethodSupport.player();
            if (p == null) return List.of();
            var list = p.level().getEntitiesOfClass(net.minecraft.world.entity.Entity.class, p.getBoundingBox().inflate(8));
            java.util.List<Object> out = new java.util.ArrayList<>();
            for (var e : list) if (e != p) out.add(e.getType().toString() + ":" + e.getId());
            return out;
        }, "附近实体", "getNearbyEntities", "get_nearby_entities", "nearbyEntities");
        // 手持物品
        NamespaceRegistry.register("Player", args -> {
            var stack = ClientMethodSupport.player() == null ? null : ClientMethodSupport.player().getMainHandItem();
            return stack == null || stack.isEmpty() ? "" : stack.getHoverName().getString();
        }, "手持物品", "getHeldItem", "get_held_item", "heldItem", "主手物品", "getMainHandItem", "get_main_hand_item");
        NamespaceRegistry.register("Player", args -> {
            var stack = ClientMethodSupport.player() == null ? null : ClientMethodSupport.player().getOffhandItem();
            return stack == null || stack.isEmpty() ? "" : stack.getHoverName().getString();
        }, "副手物品", "getOffhandItem", "get_offhand_item", "offhandItem");
        NamespaceRegistry.register("Player", args -> {
            var stack = ClientMethodSupport.player() == null ? null : ClientMethodSupport.player().getMainHandItem();
            if (stack == null || stack.isEmpty()) {
                return "";
            }
            return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        }, "手持物品ID", "getHeldItemId", "get_held_item_id", "heldItemId");
        NamespaceRegistry.register("Player", args -> {
            var stack = ClientMethodSupport.player() == null ? null : ClientMethodSupport.player().getMainHandItem();
            return stack == null ? 0.0 : (double) stack.getCount();
        }, "手持物品数量", "getHeldItemCount", "get_held_item_count", "heldItemCount");
        // 手持物品详情（tooltip 全量行：名称/lore/附魔/耐久/属性，Legacy 彩色）
        NamespaceRegistry.register("Player", args -> {
            var stack = ClientMethodSupport.player() == null ? null : ClientMethodSupport.player().getMainHandItem();
            if (stack == null || stack.isEmpty() || ClientMethodSupport.player().level() == null) {
                return new java.util.ArrayList<>();
            }
            var out = new java.util.ArrayList<Object>();
            for (var line : CompatRender.tooltipLines(stack, ClientMethodSupport.player().level(), ClientMethodSupport.player(),
                    net.minecraft.world.item.TooltipFlag.ADVANCED)) {
                out.add(LegacyText.toLegacy((net.minecraft.network.chat.Component) line));
            }
            return out;
        }, "手持物品详情", "getHeldItemDetail", "get_held_item_detail", "heldItemDetail",
                "手持物品提示", "getHeldItemTooltip", "get_held_item_tooltip");
        NamespaceRegistry.register("Player", args -> {
            var stack = ClientMethodSupport.player() == null ? null : ClientMethodSupport.player().getMainHandItem();
            if (stack == null || stack.isEmpty() || stack.getMaxDamage() <= 0) {
                return -1.0;
            }
            return (double) stack.getDamageValue();
        }, "手持物品耐久", "getHeldItemDurability", "get_held_item_durability", "heldItemDurability");
        NamespaceRegistry.register("Player", args -> {
            var stack = ClientMethodSupport.player() == null ? null : ClientMethodSupport.player().getMainHandItem();
            if (stack == null || stack.isEmpty()) {
                return 0.0;
            }
            return (double) stack.getMaxDamage();
        }, "手持物品最大耐久", "getHeldItemMaxDurability", "get_held_item_max_durability",
                "heldItemMaxDurability");
        NamespaceRegistry.register("Player", args -> {
            var stack = ClientMethodSupport.player() == null ? null : ClientMethodSupport.player().getMainHandItem();
            if (stack == null || stack.isEmpty()) {
                return new java.util.ArrayList<>();
            }
            var out = new java.util.ArrayList<Object>();
            for (var line : CompatRender.enchantmentLines(stack)) {
                out.add(line);
            }
            return out;
        }, "手持物品附魔", "getHeldItemEnchantments", "get_held_item_enchantments",
                "heldItemEnchantments", "手持附魔");
        NamespaceRegistry.register("Player", args -> {
            var stack = ClientMethodSupport.player() == null ? null : ClientMethodSupport.player().getMainHandItem();
            if (stack == null || stack.isEmpty()) {
                return new java.util.ArrayList<>();
            }
            var out = new java.util.ArrayList<Object>();
            var loreLines = CompatRender.loreLines(stack);
            for (var line : loreLines) {
                out.add(LegacyText.toLegacy((net.minecraft.network.chat.Component) line));
            }
            return out;
        }, "手持物品Lore", "getHeldItemLore", "get_held_item_lore", "heldItemLore");
        NamespaceRegistry.register("Player", args -> {
            if (ClientMethodSupport.player() == null) {
                return new java.util.ArrayList<>();
            }
            var out = new java.util.ArrayList<Object>();
            for (var stack : (java.util.List<net.minecraft.world.item.ItemStack>) CompatRender.invArmor(ClientMethodSupport.player().getInventory())) {
                out.add(stack == null || stack.isEmpty() ? "" : stack.getHoverName().getString());
            }
            return out;
        }, "盔甲栏", "getArmor", "get_armor", "armor");
        // 视线方向/视线方块（客户端拾取，最大 4.5 格）
        NamespaceRegistry.register("Player", args -> ClientMethodSupport.lookDirection()[0], "视线方向X", "getLookX", "get_look_x", "lookX");
        NamespaceRegistry.register("Player", args -> ClientMethodSupport.lookDirection()[1], "视线方向Y", "getLookY", "get_look_y", "lookY");
        NamespaceRegistry.register("Player", args -> ClientMethodSupport.lookDirection()[2], "视线方向Z", "getLookZ", "get_look_z", "lookZ");
        NamespaceRegistry.register("Player", args -> {
            var hit = ClientMethodSupport.lookAtBlock();
            return hit == null ? "" : hit.block();
        }, "视线方块", "getLookingBlock", "get_looking_block", "lookingBlock", "视线方块名");
        NamespaceRegistry.register("Player", args -> {
            var hit = ClientMethodSupport.lookAtBlock();
            return hit == null ? 0.0 : hit.x();
        }, "视线方块X", "getLookingBlockX", "get_looking_block_x", "lookingBlockX");
        NamespaceRegistry.register("Player", args -> {
            var hit = ClientMethodSupport.lookAtBlock();
            return hit == null ? 0.0 : hit.y();
        }, "视线方块Y", "getLookingBlockY", "get_looking_block_y", "lookingBlockY");
        NamespaceRegistry.register("Player", args -> {
            var hit = ClientMethodSupport.lookAtBlock();
            return hit == null ? 0.0 : hit.z();
        }, "视线方块Z", "getLookingBlockZ", "get_looking_block_z", "lookingBlockZ");
    }
}