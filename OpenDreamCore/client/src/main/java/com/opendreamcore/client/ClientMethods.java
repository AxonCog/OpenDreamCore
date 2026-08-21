package com.opendreamcore.client;

import com.opendreamcore.script.NamespaceRegistry;
import com.opendreamcore.page.Page;
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
 * 客户端脚本命名空间（移植自 DreamCore 方法库，客户端侧实现）。
 * 服务端裁决类方法（商店/经济等）在插件侧注册同名命名空间。
 */
public final class ClientMethods {

    private ClientMethods() {
    }

    /** 注册全部客户端命名空间（FMLClientSetup 时调用一次）。 */
    public static void registerAll() {
        registerPlayer();
        registerChat();
        registerSound();
        registerMusic();
        registerScreen();
        registerScript();
        registerVar();
        registerNetwork();
        registerTime();
        registerUuid();
        registerDisplay();
        registerCamera();
        registerMessage();
        registerTip();
        registerKey();
        registerMouse();
        registerTitle();
    }

    // ========== Var（页面变量便捷操作；目标 = 屏幕 → HUD → 世界聚焦面板） ==========

    private static void registerVar() {
        NamespaceRegistry.register("Var", args -> {
            var page = ClientController.get().anyCurrentPage();
            return page != null && args.length > 0 && args[0] != null
                    && page.variables().containsKey(String.valueOf(args[0]));
        }, "是否存在", "hasVariable", "has_variable", "has");
        NamespaceRegistry.register("Var", args -> {
            var page = ClientController.get().anyCurrentPage();
            return page == null || args.length < 1 || args[0] == null
                    ? null : page.variables().get(String.valueOf(args[0]));
        }, "获取", "getVariable", "get_variable", "get", "读取变量");
        NamespaceRegistry.register("Var", args -> {
            if (args.length < 2 || args[0] == null) {
                return false;
            }
            return ClientController.get().setPageVarAny(String.valueOf(args[0]), args[1]);
        }, "设置", "setVariable", "set_variable", "set", "写入变量");
        NamespaceRegistry.register("Var", args -> {
            var page = ClientController.get().anyCurrentPage();
            return page == null ? 0.0 : (double) page.variables().size();
        }, "数量", "getVariableCount", "get_variable_count", "count");
        NamespaceRegistry.register("Var", args -> {
            var page = ClientController.get().anyCurrentPage();
            if (page == null) {
                return new java.util.ArrayList<>();
            }
            var out = new java.util.ArrayList<Object>();
            page.variables().keySet().forEach(out::add);
            return out;
        }, "名称列表", "getVariableNames", "get_variable_names", "names");
        NamespaceRegistry.register("Var", args -> {
            var page = ClientController.get().anyCurrentPage();
            if (page == null) {
                return 0.0;
            }
            int n = page.variables().size();
            page.variables().clear();
            return (double) n;
        }, "清空", "clearVariables", "clear_variables", "clear");
        NamespaceRegistry.register("Var", args -> {
            // Var.递增(变量名, 增量=1)：缺省从 0 起；非数值返回 -1
            if (args.length < 1 || args[0] == null) {
                return -1.0;
            }
            var page = ClientController.get().anyCurrentPage();
            if (page == null) {
                return -1.0;
            }
            String name = String.valueOf(args[0]);
            double step = args.length > 1 && args[1] instanceof Number s ? s.doubleValue() : 1.0;
            Object cur = page.variables().get(name);
            double base = cur instanceof Number n ? n.doubleValue() : 0.0;
            if (cur != null && !(cur instanceof Number)) {
                return -1.0;
            }
            double nv = base + step;
            page.variables().put(name, nv);
            return nv;
        }, "递增", "incrementVariable", "increment_variable", "increment", "加一");
        NamespaceRegistry.register("Var", args -> {
            if (args.length < 1 || args[0] == null) {
                return -1.0;
            }
            var page = ClientController.get().anyCurrentPage();
            if (page == null) {
                return -1.0;
            }
            String name = String.valueOf(args[0]);
            double step = args.length > 1 && args[1] instanceof Number s ? s.doubleValue() : 1.0;
            Object cur = page.variables().get(name);
            if (cur != null && !(cur instanceof Number)) {
                return -1.0;
            }
            double base = cur instanceof Number n ? n.doubleValue() : 0.0;
            double nv = base - step;
            page.variables().put(name, nv);
            return nv;
        }, "递减", "decrementVariable", "decrement_variable", "decrement", "减一");
        NamespaceRegistry.register("Var", args -> {
            // Var.增加(变量名, 数值)：数值加；非数值目标返回 -1
            if (args.length < 2 || args[0] == null || !(args[1] instanceof Number add)) {
                return -1.0;
            }
            var page = ClientController.get().anyCurrentPage();
            if (page == null) {
                return -1.0;
            }
            String name = String.valueOf(args[0]);
            Object cur = page.variables().get(name);
            if (cur != null && !(cur instanceof Number)) {
                return -1.0;
            }
            double base = cur instanceof Number n ? n.doubleValue() : 0.0;
            double nv = base + add.doubleValue();
            page.variables().put(name, nv);
            return nv;
        }, "增加", "addToVariable", "add_to_variable", "addTo", "加");
        NamespaceRegistry.register("Var", args -> {
            if (args.length < 2 || args[0] == null || !(args[1] instanceof Number sub)) {
                return -1.0;
            }
            var page = ClientController.get().anyCurrentPage();
            if (page == null) {
                return -1.0;
            }
            String name = String.valueOf(args[0]);
            Object cur = page.variables().get(name);
            if (cur != null && !(cur instanceof Number)) {
                return -1.0;
            }
            double base = cur instanceof Number n ? n.doubleValue() : 0.0;
            double nv = base - sub.doubleValue();
            page.variables().put(name, nv);
            return nv;
        }, "减少", "subtractFromVariable", "subtract_from_variable", "subtractFrom", "减");
        // 动画变量（数值补间：每帧写入页面变量，HUD/世界直接显示动画；屏幕刷新）
        NamespaceRegistry.register("Var", args -> {
            // Var.动画值(变量名) → 当前补间值（无补间读页面变量；不存在 0）
            if (args.length < 1 || args[0] == null) {
                return 0.0;
            }
            return ClientController.get().getAnimateValue(String.valueOf(args[0]));
        }, "动画值", "getAnimateValue", "get_animate_value", "animateValue");
        NamespaceRegistry.register("Var", args -> {
            // Var.设置动画值(变量名, 值) → 立即写入（清除补间）
            if (args.length < 2 || args[0] == null) {
                return false;
            }
            return ClientController.get().setAnimateValue(String.valueOf(args[0]), args[1]);
        }, "设置动画值", "setAnimationVariable", "set_animation_variable", "setAnimateValue");
        NamespaceRegistry.register("Var", args -> {
            // Var.动画到(变量名, 目标值, 毫秒, 缓动?) → 启动补间（缓动: linear/quad_out/cubic_in_out/
            // sine_out/elastic_out/bounce_out 等，缺省 linear）
            if (args.length < 3 || args[0] == null) {
                return false;
            }
            double to = num(args[1]);
            long ms = (long) num(args[2]);
            com.opendreamcore.script.Easing.Type easing = com.opendreamcore.script.Easing.Type.LINEAR;
            if (args.length > 3 && args[3] != null) {
                String e = String.valueOf(args[3]).toUpperCase(java.util.Locale.ROOT).replace('-', '_');
                try {
                    easing = com.opendreamcore.script.Easing.Type.valueOf(e);
                } catch (IllegalArgumentException ignored) {
                }
            }
            return ClientController.get().animateValueTo(String.valueOf(args[0]), to, ms, easing);
        }, "动画到", "animateValueTo", "animate_value_to", "tweenTo", "tween_to");
    }

    // ========== Player（玩家自身信息） ==========

    private static void registerPlayer() {
        NamespaceRegistry.register("Player", args -> name(), "获取名字", "getName", "get_name");
        NamespaceRegistry.register("Player", args -> health(), "获取血量", "getHealth", "get_health");
        NamespaceRegistry.register("Player", args -> maxHealth(), "获取最大血量", "getMaxHealth", "get_max_health");
        NamespaceRegistry.register("Player", args -> hunger(), "获取饥饿值", "getHunger", "get_hunger");
        NamespaceRegistry.register("Player", args -> exp(), "获取经验值", "getExp", "get_exp");
        NamespaceRegistry.register("Player", args -> level(), "获取等级", "getLevel", "get_level");
        NamespaceRegistry.register("Player", args -> x(), "获取坐标X", "getX", "get_x");
        NamespaceRegistry.register("Player", args -> y(), "获取坐标Y", "getY", "get_y");
        NamespaceRegistry.register("Player", args -> z(), "获取坐标Z", "getZ", "get_z");
        NamespaceRegistry.register("Player", args -> yaw(), "获取偏航角", "getYaw", "get_yaw");
        NamespaceRegistry.register("Player", args -> pitch(), "获取俯仰角", "getPitch", "get_pitch");
        NamespaceRegistry.register("Player", args -> gamemode(), "获取游戏模式", "getGameMode", "get_gamemode");
        NamespaceRegistry.register("Player", args -> biome(), "获取生物群系", "getBiome", "get_biome");
        NamespaceRegistry.register("Player", args -> language(), "获取语言", "getLanguage", "get_language");
        NamespaceRegistry.register("Player", args -> onlineTime(), "在线时长", "getOnlineTime", "get_online_time");
        // 玩家状态
        NamespaceRegistry.register("Player", args -> player() != null && player().isShiftKeyDown(),
                "是否潜行", "isSneaking", "is_sneaking", "sneaking");
        NamespaceRegistry.register("Player", args -> player() != null && player().isSprinting(),
                "是否疾跑", "isSprinting", "is_sprinting", "sprinting");
        NamespaceRegistry.register("Player", args -> player() != null && player().getAbilities().flying,
                "是否飞行", "isFlying", "is_flying", "flying");
        NamespaceRegistry.register("Player", args -> player() != null && player().isSwimming(),
                "是否游泳", "isSwimming", "is_swimming", "swimming");
        NamespaceRegistry.register("Player", args -> player() != null && player().isOnFire(),
                "是否着火", "isOnFire", "is_on_fire", "onFire");
        NamespaceRegistry.register("Player", args -> player() != null && player().isInWater(),
                "是否在水中", "isInWater", "is_in_water", "inWater");
        NamespaceRegistry.register("Player", args -> player() != null && player().onGround(),
                "是否在地面", "isOnGround", "is_on_ground", "onGround");
        NamespaceRegistry.register("Player", args -> player() != null && player().isInWaterRainOrBubble(),
                "是否淋雨", "isInRain", "is_in_rain", "inRain");
        NamespaceRegistry.register("Player", args -> player() != null && player().level() != null
                && player().level().isNight(), "是否夜间", "isNight", "is_night", "night");
        NamespaceRegistry.register("Player", args -> player() != null && player().level() != null
                && player().level().isDay(), "是否白天", "isDay", "is_day", "day");
        NamespaceRegistry.register("Player", args -> player() != null && player().isPassenger(),
                "是否乘骑", "isRiding", "is_riding", "riding");
        NamespaceRegistry.register("Player", args -> player() != null && player().isFallFlying(),
                "是否滑翔", "isGliding", "is_gliding", "gliding", "isFallFlying");
        NamespaceRegistry.register("Player", args -> {
            var p = player();
            if (p == null) return List.of(0.0, 0.0, 0.0);
            var v = p.getDeltaMovement();
            return List.of(v.x, v.y, v.z);
        }, "速度", "getVelocity", "get_velocity", "velocity");
        NamespaceRegistry.register("Player", args -> {
            var p = player();
            if (p == null) return 0.0;
            return (double) net.minecraft.client.Minecraft.getInstance().getConnection().getPlayerInfo(p.getUUID()).getLatency();
        }, "延迟", "getPing", "get_ping", "ping");
        NamespaceRegistry.register("Player", args -> {
            var p = player();
            if (p == null) return false;
            return p.level() != null && p.level().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, p.getBoundingBox().inflate(8)).size() > 1;
        }, "附近有实体", "hasNearbyEntities", "has_nearby_entities", "nearbyExists");
        NamespaceRegistry.register("Player", args -> {
            var p = player();
            if (p == null) return List.of();
            var list = p.level().getEntitiesOfClass(net.minecraft.world.entity.Entity.class, p.getBoundingBox().inflate(8));
            java.util.List<Object> out = new java.util.ArrayList<>();
            for (var e : list) if (e != p) out.add(e.getType().toString() + ":" + e.getId());
            return out;
        }, "附近实体", "getNearbyEntities", "get_nearby_entities", "nearbyEntities");
        // 手持物品
        NamespaceRegistry.register("Player", args -> {
            var stack = player() == null ? null : player().getMainHandItem();
            return stack == null || stack.isEmpty() ? "" : stack.getHoverName().getString();
        }, "手持物品", "getHeldItem", "get_held_item", "heldItem", "主手物品", "getMainHandItem", "get_main_hand_item");
        NamespaceRegistry.register("Player", args -> {
            var stack = player() == null ? null : player().getOffhandItem();
            return stack == null || stack.isEmpty() ? "" : stack.getHoverName().getString();
        }, "副手物品", "getOffhandItem", "get_offhand_item", "offhandItem");
        NamespaceRegistry.register("Player", args -> {
            var stack = player() == null ? null : player().getMainHandItem();
            if (stack == null || stack.isEmpty()) {
                return "";
            }
            return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        }, "手持物品ID", "getHeldItemId", "get_held_item_id", "heldItemId");
        NamespaceRegistry.register("Player", args -> {
            var stack = player() == null ? null : player().getMainHandItem();
            return stack == null ? 0.0 : (double) stack.getCount();
        }, "手持物品数量", "getHeldItemCount", "get_held_item_count", "heldItemCount");
        // 手持物品详情（tooltip 全量行：名称/lore/附魔/耐久/属性，Legacy 彩色）
        NamespaceRegistry.register("Player", args -> {
            var stack = player() == null ? null : player().getMainHandItem();
            if (stack == null || stack.isEmpty() || player().level() == null) {
                return new java.util.ArrayList<>();
            }
            var out = new java.util.ArrayList<Object>();
            for (var line : stack.getTooltipLines(
                    net.minecraft.world.item.Item.TooltipContext.of(player().level()),
                    player(), net.minecraft.world.item.TooltipFlag.ADVANCED)) {
                out.add(LegacyText.toLegacy(line));
            }
            return out;
        }, "手持物品详情", "getHeldItemDetail", "get_held_item_detail", "heldItemDetail",
                "手持物品提示", "getHeldItemTooltip", "get_held_item_tooltip");
        NamespaceRegistry.register("Player", args -> {
            var stack = player() == null ? null : player().getMainHandItem();
            if (stack == null || stack.isEmpty() || stack.getMaxDamage() <= 0) {
                return -1.0;
            }
            return (double) stack.getDamageValue();
        }, "手持物品耐久", "getHeldItemDurability", "get_held_item_durability", "heldItemDurability");
        NamespaceRegistry.register("Player", args -> {
            var stack = player() == null ? null : player().getMainHandItem();
            if (stack == null || stack.isEmpty()) {
                return 0.0;
            }
            return (double) stack.getMaxDamage();
        }, "手持物品最大耐久", "getHeldItemMaxDurability", "get_held_item_max_durability",
                "heldItemMaxDurability");
        NamespaceRegistry.register("Player", args -> {
            var stack = player() == null ? null : player().getMainHandItem();
            if (stack == null || stack.isEmpty() || stack.getEnchantments().isEmpty()) {
                return new java.util.ArrayList<>();
            }
            var out = new java.util.ArrayList<Object>();
            for (var entry : stack.getEnchantments().entrySet()) {
                out.add(net.minecraft.world.item.enchantment.Enchantment.getFullname(
                        entry.getKey(), entry.getValue()));
            }
            return out;
        }, "手持物品附魔", "getHeldItemEnchantments", "get_held_item_enchantments",
                "heldItemEnchantments", "手持附魔");
        NamespaceRegistry.register("Player", args -> {
            var stack = player() == null ? null : player().getMainHandItem();
            if (stack == null || stack.isEmpty()) {
                return new java.util.ArrayList<>();
            }
            var out = new java.util.ArrayList<Object>();
            var lore = stack.getComponents().get(net.minecraft.core.component.DataComponents.LORE);
            if (lore != null) {
                for (var line : lore.lines()) {
                    out.add(LegacyText.toLegacy(line));
                }
            }
            return out;
        }, "手持物品Lore", "getHeldItemLore", "get_held_item_lore", "heldItemLore");
        NamespaceRegistry.register("Player", args -> {
            if (player() == null) {
                return new java.util.ArrayList<>();
            }
            var out = new java.util.ArrayList<Object>();
            for (var stack : player().getInventory().armor) {
                out.add(stack == null || stack.isEmpty() ? "" : stack.getHoverName().getString());
            }
            return out;
        }, "盔甲栏", "getArmor", "get_armor", "armor");
        // 视线方向/视线方块（客户端拾取，最大 4.5 格）
        NamespaceRegistry.register("Player", args -> lookDirection()[0], "视线方向X", "getLookX", "get_look_x", "lookX");
        NamespaceRegistry.register("Player", args -> lookDirection()[1], "视线方向Y", "getLookY", "get_look_y", "lookY");
        NamespaceRegistry.register("Player", args -> lookDirection()[2], "视线方向Z", "getLookZ", "get_look_z", "lookZ");
        NamespaceRegistry.register("Player", args -> {
            var hit = lookAtBlock();
            return hit == null ? "" : hit.block();
        }, "视线方块", "getLookingBlock", "get_looking_block", "lookingBlock", "视线方块名");
        NamespaceRegistry.register("Player", args -> {
            var hit = lookAtBlock();
            return hit == null ? 0.0 : hit.x();
        }, "视线方块X", "getLookingBlockX", "get_looking_block_x", "lookingBlockX");
        NamespaceRegistry.register("Player", args -> {
            var hit = lookAtBlock();
            return hit == null ? 0.0 : hit.y();
        }, "视线方块Y", "getLookingBlockY", "get_looking_block_y", "lookingBlockY");
        NamespaceRegistry.register("Player", args -> {
            var hit = lookAtBlock();
            return hit == null ? 0.0 : hit.z();
        }, "视线方块Z", "getLookingBlockZ", "get_looking_block_z", "lookingBlockZ");
    }

    /** 视线单位向量（MC 坐标：x 东、y 上、z 南）。 */
    private static double[] lookDirection() {
        var p = player();
        if (p == null) {
            return new double[]{0, 0, 0};
        }
        double yaw = Math.toRadians(p.getYRot());
        double pitch = Math.toRadians(p.getXRot());
        double dx = -Math.sin(yaw) * Math.cos(pitch);
        double dy = -Math.sin(pitch);
        double dz = Math.cos(yaw) * Math.cos(pitch);
        return new double[]{dx, dy, dz};
    }

    /** 视线指向的方块（玩家拾取，4.5 格）：方块注册名 + 坐标。 */
    private static record LookBlock(String block, double x, double y, double z) {
    }

    private static LookBlock lookAtBlock() {
        var p = player();
        if (p == null || p.level() == null) {
            return null;
        }
        try {
            var hit = p.pick(4.5, 0.0F, false);
            if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
                var pos = ((net.minecraft.world.phys.BlockHitResult) hit).getBlockPos();
                var state = p.level().getBlockState(pos);
                String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                return new LookBlock(id, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static Player player() {
        return Minecraft.getInstance().player;
    }

    private static String name() {
        return player() != null ? player().getName().getString() : "";
    }

    private static double health() {
        return player() != null ? player().getHealth() : 0;
    }

    private static double maxHealth() {
        return player() != null ? player().getMaxHealth() : 0;
    }

    private static double hunger() {
        return player() != null ? player().getFoodData().getFoodLevel() : 0;
    }

    private static double exp() {
        return player() != null ? player().experienceProgress : 0;
    }

    private static double level() {
        return player() != null ? player().experienceLevel : 0;
    }

    private static double x() {
        return player() != null ? player().getX() : 0;
    }

    private static double y() {
        return player() != null ? player().getY() : 0;
    }

    private static double z() {
        return player() != null ? player().getZ() : 0;
    }

    private static double yaw() {
        return player() != null ? player().getYRot() : 0;
    }

    private static double pitch() {
        return player() != null ? player().getXRot() : 0;
    }

    private static String gamemode() {
        return Minecraft.getInstance().gameMode != null
                ? Minecraft.getInstance().gameMode.getPlayerMode().getName() : "";
    }

    private static String biome() {
        if (player() == null || player().level() == null) {
            return "";
        }
        return player().level().getBiome(player().blockPosition()).getRegisteredName();
    }

    private static String language() {
        return Minecraft.getInstance().getLanguageManager().getSelected();
    }

    private static double onlineTime() {
        return ClientController.get().onlineSeconds();
    }

    // ========== Chat / Message / Tip（消息显示） ==========

    private static void registerChat() {
        NamespaceRegistry.register("Chat", args -> {
            sendChat(args.length > 0 ? String.valueOf(args[0]) : "");
            return null;
        }, "发送消息", "sendMessage", "send_message", "say");
        NamespaceRegistry.register("Chat", args -> {
            // Chat.打开聊天() → 打开原版聊天输入（关闭/发送后回到原页面）
            openVanillaChat("");
            return true;
        }, "打开聊天", "openChat", "open_chat");
        NamespaceRegistry.register("Chat", args -> {
            // Chat.设置聊天内容("文本") → 打开聊天并预填内容
            String text = args.length > 0 && args[0] != null ? String.valueOf(args[0]) : "";
            openVanillaChat(text);
            return true;
        }, "设置聊天内容", "setChatMessage", "set_chat_message");
        NamespaceRegistry.register("Chat", args -> {
            // Chat.添加消息("文本") → 本地聊天栏显示 + 记录
            String text = args.length > 0 ? String.valueOf(args[0]) : "";
            sendChat(text);
            ClientController.get().addChatMessage(text);
            return null;
        }, "添加消息", "addChatMessage", "add_chat_message");
        NamespaceRegistry.register("Chat", args -> {
            // Chat.获取最后消息() → 最近一条聊天（无则空串）
            var list = ClientController.get().latestChat(1);
            return list.isEmpty() ? "" : list.get(0);
        }, "获取最后消息", "getLastMessage", "get_last_message");
    }

    // ========== Title（标题显示） ==========

    private static void registerTitle() {
        NamespaceRegistry.register("Title", args -> {
            // Title.显示标题(标题, 副标题?, 淡入?, 停留?, 淡出?)
            String title = args.length > 0 && args[0] != null ? String.valueOf(args[0]) : "";
            String subtitle = args.length > 1 && args[1] != null ? String.valueOf(args[1]) : "";
            int fadeIn = args.length > 2 && args[2] instanceof Number n ? n.intValue() : 10;
            int stay = args.length > 3 && args[3] instanceof Number n ? n.intValue() : 70;
            int fadeOut = args.length > 4 && args[4] instanceof Number n ? n.intValue() : 20;
            var gui = Minecraft.getInstance().gui;
            gui.setTimes(fadeIn, stay, fadeOut);
            gui.setSubtitle(Component.literal(subtitle));
            gui.setTitle(Component.literal(title));
            return true;
        }, "显示标题", "showTitle", "show_title");
    }

    private static void registerMessage() {
        NamespaceRegistry.register("Message", args -> {
            sendChat(args.length > 0 ? String.valueOf(args[0]) : "");
            return null;
        }, "发送", "send", "显示", "show");
    }

    private static void registerTip() {
        NamespaceRegistry.register("Tip", args -> {
            sendActionBar(args.length > 0 ? String.valueOf(args[0]) : "");
            return null;
        }, "发送", "send", "显示", "show");
    }

    private static void sendChat(String text) {
        if (player() != null) {
            player().displayClientMessage(Component.literal(text), false);
        }
    }

    /**
     * 打开原版聊天框；关闭或发送后恢复之前的屏幕（OpenDreamCore 页面保持打开）。
     * 匿名子类包一层 onClose/handleChatInput，避免 ChatScreen 直接退回游戏。
     */
    private static void openVanillaChat(String initialText) {
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.client.gui.screens.Screen previous = mc.screen;
        mc.setScreen(new net.minecraft.client.gui.screens.ChatScreen(initialText == null ? "" : initialText) {
            @Override
            public void onClose() {
                super.onClose();
                if (mc.screen == null && previous != null) {
                    mc.setScreen(previous);
                }
            }

            @Override
            public void handleChatInput(String input, boolean addToHistory) {
                super.handleChatInput(input, addToHistory);
                if (mc.screen == null && previous != null) {
                    mc.setScreen(previous);
                }
            }
        });
    }

    private static void sendActionBar(String text) {
        if (player() != null) {
            player().displayClientMessage(Component.literal(text), true);
        }
    }

    // ========== Sound（音效） ==========

    private static void registerSound() {
        NamespaceRegistry.register("Sound", args -> {
            if (args.length < 1) {
                return false;
            }
            playSound(String.valueOf(args[0]),
                    args.length > 1 ? num(args[1]) : 1.0,
                    args.length > 2 ? num(args[2]) : 1.0);
            return true;
        }, "播放音效", "playSound", "play_sound", "播放", "play");
        NamespaceRegistry.register("Sound", args -> {
            // Sound.循环播放(名字, 音效, 音量?, 音调?)
            if (args.length < 2 || args[0] == null || args[1] == null) {
                return false;
            }
            String name = String.valueOf(args[0]);
            SoundEvent event = soundEvent(String.valueOf(args[1]));
            SoundStore.get().playLoop(name, event,
                    args.length > 2 ? (float) num(args[2]) : 1.0F,
                    args.length > 3 ? (float) num(args[3]) : 1.0F);
            return true;
        }, "循环播放", "playLoop", "play_loop");
        NamespaceRegistry.register("Sound", args -> {
            // Sound.停止(名字?) — 停指定循环；不传停全部循环
            if (args.length > 0 && args[0] != null) {
                SoundStore.get().stopLoop(String.valueOf(args[0]));
            } else {
                SoundStore.get().stopAllLoops();
            }
            return true;
        }, "停止", "stop", "停止音效");
    }

    private static SoundEvent soundEvent(String soundName) {
        net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(soundName);
        return id == null ? null : BuiltInRegistries.SOUND_EVENT.get(id);
    }

    private static void playSound(String soundName, double volume, double pitch) {
        ResourceLocation id = ResourceLocation.tryParse(soundName);
        if (id == null || player() == null) {
            return;
        }
        SoundEvent event = BuiltInRegistries.SOUND_EVENT.get(id);
        if (event == null) {
            return;
        }
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(event, (float) pitch, (float) volume));
    }

    // ========== Music（背景音乐） ==========

    private static void registerMusic() {
        NamespaceRegistry.register("Music", args -> {
            // Music.播放(文件, 音量?, 循环?)
            if (args.length < 1 || args[0] == null) {
                return false;
            }
            MusicPlayer.get().play(String.valueOf(args[0]),
                    args.length > 1 ? num(args[1]) : 0.8,
                    args.length > 2 && args[2] instanceof Number n && n.intValue() != 0);
            return true;
        }, "播放", "play");
        NamespaceRegistry.register("Music", args -> {
            MusicPlayer.get().stop();
            return true;
        }, "停止", "stop");
        NamespaceRegistry.register("Music", args -> {
            // Music.音量(0-1)
            MusicPlayer.get().volume(args.length > 0 ? num(args[0]) : 0.8);
            return true;
        }, "音量", "volume", "setVolume");
        NamespaceRegistry.register("Music", args -> MusicPlayer.get().isPlaying(), "是否播放", "isPlaying", "is_playing");
        NamespaceRegistry.register("Music", args -> {
            String current = MusicPlayer.get().current();
            return current == null ? "" : current;
        }, "当前曲目", "current", "getCurrent");
    }

    // ========== Screen（页面控制） ==========

    private static void registerScreen() {
        NamespaceRegistry.register("Screen", args -> {
            if (args.length < 1 || args[0] == null) {
                return false;
            }
            ClientController.get().open(ClientController.get().localPages().get(String.valueOf(args[0])));
            return true;
        }, "打开页面", "openPage", "open_page", "打开", "open");
        NamespaceRegistry.register("Screen", args -> {
            ClientController.get().close();
            return null;
        }, "关闭页面", "closePage", "close_page", "关闭", "close");
        NamespaceRegistry.register("Screen", args -> {
            ClientController.get().autoMountHud();
            return null;
        }, "挂载HUD", "mountHud", "mount_hud");
        NamespaceRegistry.register("Screen", args -> {
            ClientController.get().closeHud();
            return null;
        }, "卸载HUD", "unmountHud", "unmount_hud");
        NamespaceRegistry.register("Screen", args -> ClientController.get().isOpen(), "是否打开", "isOpen", "is_open");
        NamespaceRegistry.register("Screen", args -> ClientController.get().isHudOpen(), "HUD是否打开", "isHudOpen", "is_hud_open");
        // 组件方法：动态改元素属性（文本/颜色/显隐/位置等）
        NamespaceRegistry.register("Screen", args -> {
            // Screen.设置元素(元素id, 路径, 值) → 如 ("title_text", "text.content", "新标题")
            if (args.length < 3 || args[0] == null || args[1] == null) {
                return false;
            }
            var controller = ClientController.get();
            Page page = controller.currentPage();
            if (page == null) {
                return false;
            }
            boolean ok = controller.setElementProp(page,
                    String.valueOf(args[0]), String.valueOf(args[1]), args[2]);
            if (ok) {
                controller.refreshCurrent();
            }
            return ok;
        }, "设置元素", "setElement", "set_element", "设置元素属性", "setElementProp", "set_element_prop");
        NamespaceRegistry.register("Screen", args -> {
            // Screen.获取元素(元素id, 路径)
            if (args.length < 2 || args[0] == null || args[1] == null) {
                return null;
            }
            var controller = ClientController.get();
            Page page = controller.currentPage();
            if (page == null) {
                return null;
            }
            return controller.getElementProp(page,
                    String.valueOf(args[0]), String.valueOf(args[1]));
        }, "获取元素", "getElement", "get_element", "获取元素属性", "getElementProp", "get_element_prop");
        // 组件动态操作：显隐/存在/悬停/创建
        NamespaceRegistry.register("Screen", args -> {
            if (args.length < 1 || args[0] == null) {
                return false;
            }
            return ClientController.get().hideElement(String.valueOf(args[0]));
        }, "隐藏元素", "hideElement", "hide_element", "隐藏");
        NamespaceRegistry.register("Screen", args -> {
            if (args.length < 1 || args[0] == null) {
                return false;
            }
            return ClientController.get().showElement(String.valueOf(args[0]));
        }, "显示元素", "showElement", "show_element", "显示");
        NamespaceRegistry.register("Screen", args -> {
            if (args.length < 1 || args[0] == null) {
                return false;
            }
            return ClientController.get().elementExists(String.valueOf(args[0]));
        }, "元素存在", "elementExists", "element_exists", "存在");
        NamespaceRegistry.register("Screen", args -> {
            String hovered = ClientController.get().hoveredElement();
            return hovered == null ? "" : hovered;
        }, "获取悬停元素", "getHoveredElement", "get_hovered_element", "悬停元素", "hovered");
        NamespaceRegistry.register("Screen", args -> {
            // Screen.创建元素(id, type, x, y, width?, height?)
            if (args.length < 4 || args[0] == null || args[1] == null) {
                return false;
            }
            double x = num(args.length > 2 ? args[2] : null);
            double y = num(args.length > 3 ? args[3] : null);
            double w = args.length > 4 && args[4] instanceof Number n ? n.doubleValue() : Double.NaN;
            double h = args.length > 5 && args[5] instanceof Number n2 ? n2.doubleValue() : Double.NaN;
            return ClientController.get().createElement(
                    String.valueOf(args[0]), String.valueOf(args[1]), x, y, w, h);
        }, "创建元素", "createElement", "create_element", "创建");
        NamespaceRegistry.register("Screen", args -> {
            if (args.length < 1 || args[0] == null) return false;
            String id = String.valueOf(args[0]);
            var c = ClientController.get();
            var page = c.currentPage();
            if (page == null) return false;
            if (c.findElement(page, id) == null && !c.elementExists(id)) return false;
            String pageId = page.id() == null ? "page" : page.id();
            c.elementEdits().markDeleted(pageId, id);
            c.refreshCurrent();
            return true;
        }, "删除元素", "removeElement", "remove_element", "移除元素", "deleteElement", "删除");
        NamespaceRegistry.register("Screen", args -> {
            if (args.length < 1 || args[0] == null) return null;
            var c = ClientController.get();
            var page = c.currentPage();
            if (page == null) return null;
            var el = c.findElement(page, String.valueOf(args[0]));
            if (el == null) return null;
            var layout = el.layout();
            return java.util.List.of(
                    layout == null || layout.x() == null ? 0 : layout.x(),
                    layout == null || layout.y() == null ? 0 : layout.y(),
                    layout == null || layout.width() == null ? 0 : layout.width(),
                    layout == null || layout.height() == null ? 0 : layout.height());
        }, "获取元素位置", "getElementPosition", "get_element_position", "元素位置");
        NamespaceRegistry.register("Screen", args -> {
            var c = ClientController.get();
            var page = c.currentPage();
            if (page == null) return List.of();
            List<String> out = new java.util.ArrayList<>();
            for (var e : page.elements()) collectElementIds(e, out);
            for (var e : c.elementEdits().copies(page.id() == null ? "page" : page.id())) collectElementIds(e, out);
            return out;
        }, "获取全部元素", "getAllElements", "get_all_elements", "全部元素");
        NamespaceRegistry.register("Screen", args -> {
            if (args.length < 1 || args[0] == null) return false;
            String id = String.valueOf(args[0]);
            var c = ClientController.get();
            var page = c.currentPage();
            if (page == null) return false;
            String pageId = page.id() == null ? "page" : page.id();
            boolean vis = c.elementExists(id) && !c.elementEdits().isHidden(pageId, id);
            if (vis) c.hideElement(id); else c.showElement(id);
            return !vis;
        }, "切换元素", "toggleElement", "toggle_element", "切换");
        NamespaceRegistry.register("Screen", args -> {
            if (args.length < 1 || args[0] == null) return List.of();
            String q = String.valueOf(args[0]).toLowerCase(java.util.Locale.ROOT);
            var c = ClientController.get();
            var page = c.currentPage();
            if (page == null) return List.of();
            List<String> all = new java.util.ArrayList<>();
            for (var e : page.elements()) collectElementIds(e, all);
            for (var e : c.elementEdits().copies(page.id() == null ? "page" : page.id())) collectElementIds(e, all);
            boolean byType = q.startsWith("type:");
            String filter = byType ? q.substring(5) : q;
            List<String> out = new java.util.ArrayList<>();
            for (String id : all) {
                if (byType) {
                    var el = c.findElement(page, id);
                    if (el != null && el.type().toLowerCase(java.util.Locale.ROOT).contains(filter)) out.add(id);
                } else {
                    String mode = args.length > 1 ? String.valueOf(args[1]).toLowerCase(java.util.Locale.ROOT) : "contains";
                    if ("prefix".equals(mode) ? id.toLowerCase(java.util.Locale.ROOT).startsWith(filter)
                            : "suffix".equals(mode) ? id.toLowerCase(java.util.Locale.ROOT).endsWith(filter)
                            : id.toLowerCase(java.util.Locale.ROOT).contains(filter)) out.add(id);
                }
            }
            return out;
        }, "获取元素按条件", "getElementsBy", "get_elements_by", "按条件获取元素");
        NamespaceRegistry.register("Screen", args -> {
            if (args.length < 2 || args[0] == null || args[1] == null) return false;
            var c = ClientController.get();
            var page = c.currentPage();
            if (page == null) return false;
            var el = c.findElement(page, String.valueOf(args[0]));
            if (el == null) return false;
            Map<String, Object> target = el.props();
            String path = String.valueOf(args[1]);
            String[] parts = path.split("\\.");
            for (int i = 0; i < parts.length - 1; i++) {
                Object next = target.get(parts[i]);
                if (!(next instanceof Map<?, ?> m)) { Map<String, Object> fresh = new java.util.LinkedHashMap<>(); target.put(parts[i], fresh); target = fresh; }
                else target = (Map<String, Object>) (Map<?, ?>) m;
            }
            target.put(parts[parts.length - 1], args[2]);
            c.refreshCurrent();
            return true;
        }, "批量设置属性", "batchSetProperty", "batch_set_property", "批量设置");
        NamespaceRegistry.register("Screen", args -> {
            if (args.length < 1 || args[0] == null) return Map.of();
            var c = ClientController.get();
            var page = c.currentPage();
            if (page == null) return Map.of();
            String id = String.valueOf(args[0]);
            var el = c.findElement(page, id);
            if (el == null) return Map.of();
            Map<String, Object> out = new java.util.LinkedHashMap<>();
            out.put("id", el.id());
            out.put("type", el.type());
            out.put("x", el.layout() == null ? 0 : el.layout().x());
            out.put("y", el.layout() == null ? 0 : el.layout().y());
            out.put("width", el.layout() == null ? 0 : el.layout().width());
            out.put("height", el.layout() == null ? 0 : el.layout().height());
            return out;
        }, "获取组件位置", "getComponentPosition", "get_component_position", "组件位置");
        // 延迟变量（基于脚本定时器体系：到期写页面变量并刷新对应展示形态）
        NamespaceRegistry.register("Screen", args -> {
            // Screen.延迟设置变量(变量名, 值, 毫秒) → 同名挂起先取消；返回任务 id（-1 失败）
            if (args.length < 3 || args[0] == null) {
                return -1.0;
            }
            return (double) ClientController.get().delaySetPageVar(
                    String.valueOf(args[0]), args[1], (long) num(args[2]));
        }, "延迟设置变量", "delaySetVar", "delay_set_var", "setDelayedVariable", "set_delayed_variable");
        NamespaceRegistry.register("Screen", args -> {
            // Screen.取消延迟变量(变量名)
            if (args.length < 1 || args[0] == null) {
                return false;
            }
            return ClientController.get().cancelDelayedVar(
                    ClientController.get().currentPageId(), String.valueOf(args[0]));
        }, "取消延迟变量", "cancelDelayedVar", "cancel_delayed_var", "clearDelayedVariable");
        NamespaceRegistry.register("Screen", args -> {
            // Screen.延迟剩余(变量名) → 剩余毫秒；无挂起任务 -1
            if (args.length < 1 || args[0] == null) {
                return -1.0;
            }
            return ClientController.get().delayedVarRemaining(
                    ClientController.get().currentPageId(), String.valueOf(args[0]));
        }, "延迟剩余", "delayedRemaining", "delayed_remaining", "getDelayedRemaining");
        // 视频控制（按元素 id，FfmpegVideoPlayer 注册表）
        NamespaceRegistry.register("Screen", args -> {
            // Screen.视频暂停(元素id)
            if (args.length < 1 || args[0] == null) {
                return false;
            }
            var video = FfmpegVideoPlayer.byElement(String.valueOf(args[0]));
            if (video == null) {
                return false;
            }
            video.pause();
            return true;
        }, "视频暂停", "pauseVideo", "pause_video", "暂停视频");
        NamespaceRegistry.register("Screen", args -> {
            // Screen.视频继续(元素id)
            if (args.length < 1 || args[0] == null) {
                return false;
            }
            var video = FfmpegVideoPlayer.byElement(String.valueOf(args[0]));
            if (video == null) {
                return false;
            }
            video.resume();
            return true;
        }, "视频继续", "resumeVideo", "resume_video", "继续视频");
        NamespaceRegistry.register("Screen", args -> {
            // Screen.视频停止(元素id) — 停止并清空画面
            if (args.length < 1 || args[0] == null) {
                return false;
            }
            var video = FfmpegVideoPlayer.byElement(String.valueOf(args[0]));
            if (video == null) {
                return false;
            }
            video.stop();
            return true;
        }, "视频停止", "stopVideo", "stop_video", "停止视频");
        NamespaceRegistry.register("Screen", args -> {
            // Screen.视频重播(元素id) — 从头播放
            if (args.length < 1 || args[0] == null) {
                return false;
            }
            var video = FfmpegVideoPlayer.byElement(String.valueOf(args[0]));
            if (video == null) {
                return false;
            }
            video.restart();
            return true;
        }, "视频重播", "restartVideo", "restart_video", "重播视频");
        NamespaceRegistry.register("Screen", args -> {
            // Screen.视频是否播放(元素id)
            if (args.length < 1 || args[0] == null) {
                return false;
            }
            var video = FfmpegVideoPlayer.byElement(String.valueOf(args[0]));
            return video != null && video.isPlaying();
        }, "视频是否播放", "isVideoPlaying", "is_video_playing", "视频播放中");
        NamespaceRegistry.register("Screen", args -> {
            // Screen.视频跳转(元素id, 秒) — seek 到指定时间
            if (args.length < 2 || args[0] == null) {
                return false;
            }
            var video = FfmpegVideoPlayer.byElement(String.valueOf(args[0]));
            if (video == null) {
                return false;
            }
            video.seek(num(args[1]));
            return true;
        }, "视频跳转", "seekVideo", "seek_video", "跳转视频");
        NamespaceRegistry.register("Screen", args -> {
            // Screen.隐藏原版界面(true/false) — 隐藏准星/物品栏/聊天等全部原版 HUD（F1 同款）
            boolean hide = args.length > 0 && Boolean.parseBoolean(String.valueOf(args[0]));
            Minecraft.getInstance().options.hideGui = hide;
            return true;
        }, "隐藏原版界面", "hideVanilla", "hide_vanilla", "隐藏原版HUD", "hideVanillaHud");
        // 世界元素位置（脚本化控制，配合拖拽）
        NamespaceRegistry.register("Screen", args -> {
            // Screen.设置世界元素位置(元素id, x, y, z)
            if (args.length < 4 || args[0] == null) {
                return false;
            }
            return ClientController.get().setWorldElementPos(String.valueOf(args[0]),
                    num(args[1]), num(args[2]), num(args[3]));
        }, "设置世界元素位置", "setWorldElementPos", "set_world_element_pos", "世界元素位置");
        NamespaceRegistry.register("Screen", args -> {
            // Screen.获取世界元素位置(元素id) → {x, y, z}
            if (args.length < 1 || args[0] == null) {
                return null;
            }
            return ClientController.get().getWorldElementPos(String.valueOf(args[0]));
        }, "获取世界元素位置", "getWorldElementPos", "get_world_element_pos", "世界元素坐标");
        // 动画方法
        NamespaceRegistry.register("Screen", args -> {
            if (args.length < 1 || args[0] == null) {
                return false;
            }
            AnimationEngine.get().play(String.valueOf(args[0]));
            return true;
        }, "播放动画", "playAnimation", "play_animation", "播放");
        NamespaceRegistry.register("Screen", args -> {
            if (args.length < 1 || args[0] == null) {
                return false;
            }
            AnimationEngine.get().stop(String.valueOf(args[0]));
            return true;
        }, "停止动画", "stopAnimation", "stop_animation", "停止");
        NamespaceRegistry.register("Screen", args -> {
            if (args.length < 1 || args[0] == null) {
                return false;
            }
            AnimationEngine.get().pause(String.valueOf(args[0]));
            return true;
        }, "暂停动画", "pauseAnimation", "pause_animation", "暂停");
        NamespaceRegistry.register("Screen", args -> {
            if (args.length < 1 || args[0] == null) {
                return false;
            }
            AnimationEngine.get().resume(String.valueOf(args[0]));
            return true;
        }, "恢复动画", "resumeAnimation", "resume_animation", "恢复");
        NamespaceRegistry.register("Screen", args -> {
            // Screen.播放动画序列("a", "b", "c")
            if (args.length < 1) {
                return false;
            }
            String[] names = new String[args.length];
            for (int i = 0; i < args.length; i++) {
                names[i] = args[i] == null ? "" : String.valueOf(args[i]);
            }
            AnimationEngine.get().playSequence(names);
            return true;
        }, "播放动画序列", "playSequence", "play_sequence", "播放序列");
        // 屏幕特效
        NamespaceRegistry.register("Screen", args -> {
            // Screen.屏幕震动(强度, 时长)
            double strength = args.length > 0 && args[0] instanceof Number n ? n.doubleValue() : 5;
            int duration = args.length > 1 && args[1] instanceof Number n2 ? n2.intValue() : 300;
            ClientController.get().shake(strength, duration);
            return true;
        }, "屏幕震动", "shakeScreen", "shake_screen", "震动");
        NamespaceRegistry.register("Screen", args -> {
            // Screen.闪屏("red" 或 "#FF0000", 时长)
            String color = args.length > 0 && args[0] != null ? String.valueOf(args[0]) : "#FFFFFF";
            int duration = args.length > 1 && args[1] instanceof Number n ? n.intValue() : 200;
            int argb = UiStyle.color(color, 0xFFFFFFFF);
            ClientController.get().flash(argb, duration);
            return true;
        }, "闪屏", "flashScreen", "flash_screen", "闪屏");
    }

    // ========== Script（运行控制：执行/延迟/计划/打印） ==========

    private static void registerScript() {
        NamespaceRegistry.register("Script", args -> {
            // Script.执行("Chat.发送消息(\"hi\")") — 立即在当前页作用域执行
            if (args.length < 1 || args[0] == null) {
                return false;
            }
            var controller = ClientController.get();
            Page page = controller.currentPage();
            if (page == null) {
                return false;
            }
            controller.runLocalAction(page, String.valueOf(args[0]));
            return true;
        }, "执行", "execute", "run");
        NamespaceRegistry.register("Script", args -> {
            // Script.延迟执行(毫秒, 脚本) → 返回任务 id
            if (args.length < 2 || args[1] == null) {
                return -1.0;
            }
            long delay = (long) num(args[0]);
            return (double) ClientController.get().scheduleScript(
                    String.valueOf(args[1]), delay, 0);
        }, "延迟执行", "delay", "delayExecute", "delay_execute");
        NamespaceRegistry.register("Script", args -> {
            // Script.计划执行(秒, 脚本) → 每 N 秒循环执行，返回任务 id
            if (args.length < 2 || args[1] == null) {
                return -1.0;
            }
            double seconds = num(args[0]);
            long interval = (long) (seconds * 1000);
            return (double) ClientController.get().scheduleScript(
                    String.valueOf(args[1]), interval, interval);
        }, "计划执行", "schedule", "scheduleRepeating", "schedule_repeating");
        NamespaceRegistry.register("Script", args -> {
            // Script.防抖(毫秒, 脚本, 键?) → 同名键重置计时，安静后执行一次；返回任务 id
            if (args.length < 2 || args[1] == null) {
                return -1.0;
            }
            String key = args.length >= 3 && args[2] != null ? String.valueOf(args[2]) : null;
            return (double) ClientController.get().debounceScript(
                    String.valueOf(args[1]), (long) num(args[0]), key);
        }, "防抖", "debounce", "debounceExecute", "debounce_execute");
        NamespaceRegistry.register("Script", args -> {
            // Script.节流(毫秒, 脚本, 键?) → 周期内最多执行一次（周期末补跑合并尾调用）；返回任务 id
            if (args.length < 2 || args[1] == null) {
                return -1.0;
            }
            String key = args.length >= 3 && args[2] != null ? String.valueOf(args[2]) : null;
            return (double) ClientController.get().throttleScript(
                    String.valueOf(args[1]), (long) num(args[0]), key);
        }, "节流", "throttle", "throttleExecute", "throttle_execute");
        NamespaceRegistry.register("Script", args -> {
            // Script.取消(任务id)
            if (args.length < 1) {
                return false;
            }
            return ClientController.get().cancelScript((long) num(args[0]));
        }, "取消", "cancel");
        NamespaceRegistry.register("Script", args -> {
            // Script.打印(消息) → 聊天栏 + 日志
            String msg = args.length > 0 && args[0] != null ? String.valueOf(args[0]) : "";
            var player = Minecraft.getInstance().player;
            if (player != null) {
                player.displayClientMessage(Component.literal(msg), false);
            }
            return msg;
        }, "打印", "print", "log");
        NamespaceRegistry.register("Script", args -> {
            // Script.调试(消息) → 仅日志
            String msg = args.length > 0 && args[0] != null ? String.valueOf(args[0]) : "";
            ClientController.LOGGER.info("[ODC-Script] {}", msg);
            return msg;
        }, "调试", "debug");
        NamespaceRegistry.register("Screen", args -> {
            if (args.length < 1 || args[0] == null) return false;
            String src = String.valueOf(args[0]);
            try {
                var win = Minecraft.getInstance().getWindow();
                ResourceLocation rl = ResourceLocation.tryParse(src.contains(":") ? src : "minecraft:" + src);
                if (rl == null) return false;
                var tex = net.minecraft.client.Minecraft.getInstance().getTextureManager().getTexture(rl);
                if (tex == null) return false;
                Minecraft.getInstance().execute(() -> {
                    try { Minecraft.getInstance().mouseHandler.setIgnoreFirstMove(); } catch (Exception ignored) {}
                });
                return true;
            } catch (Exception e) { return false; }
        }, "设置鼠标", "setMouse", "set_mouse", "setMouseTexture", "set_mouse_texture");
    }

    // ========== Network（自定义双向通道 custom_packet） ==========

    private static void registerNetwork() {
        NamespaceRegistry.register("Network", args -> {
            // Network.发送(通道, 内容) → 上行到服务端（无连接返回 false）
            if (args.length < 2 || args[0] == null) {
                return false;
            }
            String payload = args[1] == null ? "" : String.valueOf(args[1]);
            return ClientController.get().sendCustomPacket(String.valueOf(args[0]), payload);
        }, "发送", "send", "sendCustomPacket", "send_custom_packet");
        NamespaceRegistry.register("Network", args -> {
            // Network.订阅(通道, lambda) → 服务端下行分发（payload 作参数）；返回订阅 id
            if (args.length < 2 || args[0] == null
                    || !(args[1] instanceof com.opendreamcore.script.DreamLangExecutor.Callable c)) {
                return -1.0;
            }
            return (double) com.opendreamcore.script.EventBus.subscribe("custom:" + args[0], c);
        }, "订阅", "subscribe", "onCustomPacket", "on_custom_packet");
        NamespaceRegistry.register("Network", args -> {
            // Network.取消订阅(id)
            if (args.length < 1) {
                return false;
            }
            Object v = args[0];
            long id = v instanceof Number n ? n.longValue() : -1;
            return com.opendreamcore.script.EventBus.unsubscribe(id);
        }, "取消订阅", "unsubscribe", "off");
    }

    // ========== Time / UUID / Display / Camera（系统类） ==========

    private static void registerTime() {
        NamespaceRegistry.register("Time", args -> (double) (System.currentTimeMillis() / 1000), "当前时间戳", "now", "timestamp");
        NamespaceRegistry.register("Time", args -> (double) System.currentTimeMillis(), "当前毫秒", "millis");
        NamespaceRegistry.register("Time", args -> player() != null ? (double) player().level().getDayTime() : 0, "游戏时间", "gameTime", "game_time");
    }

    private static void registerUuid() {
        NamespaceRegistry.register("UUID", args -> UUID.randomUUID().toString(), "随机", "random");
    }

    private static void registerDisplay() {
        NamespaceRegistry.register("Display", args -> (double) Minecraft.getInstance().getWindow().getScreenWidth(),
                "窗口宽", "getWidth", "get_width", "width");
        NamespaceRegistry.register("Display", args -> (double) Minecraft.getInstance().getWindow().getScreenHeight(),
                "窗口高", "getHeight", "get_height", "height");
        NamespaceRegistry.register("Display", args -> (double) Minecraft.getInstance().getWindow().getGuiScale(),
                "界面缩放", "getScale", "get_scale", "scale");
        NamespaceRegistry.register("Display", args -> (double) Minecraft.getInstance().getFps(),
                "获取FPS", "getFPS", "get_fps", "fps");
        NamespaceRegistry.register("Display", args -> {
            // Display.设置界面缩放(2) → 立即改 GUI 缩放（0 = 自动）
            double scale = args.length > 0 && args[0] instanceof Number n ? n.doubleValue() : 0;
            var window = Minecraft.getInstance().getWindow();
            if (scale <= 0) {
                scale = window.calculateScale(Minecraft.getInstance().options.guiScale().get(), false);
            }
            window.setGuiScale(scale);
            return true;
        }, "设置界面缩放", "setGuiScale", "set_gui_scale");
        NamespaceRegistry.register("Display", args -> Minecraft.getInstance().getWindow().isFullscreen(), "是否全屏", "isFullscreen", "is_fullscreen");
        NamespaceRegistry.register("Display", args -> {
            Minecraft.getInstance().getWindow().toggleFullScreen();
            return true;
        }, "切换全屏", "toggleFullscreen", "toggle_fullscreen");
    }

    private static void registerCamera() {
        NamespaceRegistry.register("Camera", args -> x(), "获取X", "getX", "get_x");
        NamespaceRegistry.register("Camera", args -> y(), "获取Y", "getY", "get_y");
        NamespaceRegistry.register("Camera", args -> z(), "获取Z", "getZ", "get_z");
        NamespaceRegistry.register("Camera", args -> yaw(), "获取偏航", "getYaw", "get_yaw");
        NamespaceRegistry.register("Camera", args -> pitch(), "获取俯仰", "getPitch", "get_pitch");
    }

    private static double num(Object v) {
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ========== Key（键盘） ==========

    /** 常用键位名 → 游戏内 KeyMapping（键位绑定/查询共用）。 */
    public static net.minecraft.client.KeyMapping keyMapping(String name) {
        var options = Minecraft.getInstance().options;
        return switch (name) {
            case "key.keyboard.w", "key.up" -> options.keyUp;
            case "key.keyboard.a", "key.left" -> options.keyLeft;
            case "key.keyboard.s", "key.down" -> options.keyDown;
            case "key.keyboard.d", "key.right" -> options.keyRight;
            case "key.keyboard.space", "key.jump" -> options.keyJump;
            case "key.keyboard.left.shift", "key.sneak" -> options.keyShift;
            case "key.keyboard.left.control", "key.sprint" -> options.keySprint;
            case "key.keyboard.e", "key.inventory" -> options.keyInventory;
            case "key.keyboard.q", "key.drop" -> options.keyDrop;
            case "key.keyboard.f", "key.swapOffhand" -> options.keySwapOffhand;
            case "key.keyboard.attack", "key.attack" -> options.keyAttack;
            case "key.keyboard.use", "key.use" -> options.keyUse;
            case "key.keyboard.f5", "key.togglePerspective" -> options.keyTogglePerspective;
            default -> null;
        };
    }

    private static void registerKey() {
        NamespaceRegistry.register("Key", args -> {
            // Key.是否按下("key.keyboard.w")
            if (args.length < 1 || args[0] == null) {
                return false;
            }
            var mapping = keyMapping(String.valueOf(args[0]));
            return mapping != null && mapping.isDown();
        }, "是否按下", "isKeyDown", "is_down", "按下");
        NamespaceRegistry.register("Key", args -> {
            // Key.按键名("key.keyboard.w") → 当前绑定键的名字
            if (args.length < 1 || args[0] == null) {
                return "";
            }
            var mapping = keyMapping(String.valueOf(args[0]));
            return mapping == null ? "" : mapping.getName();
        }, "按键名", "getKeyName", "get_key_name");
    }

    // ========== Mouse（鼠标） ==========

    private static void registerMouse() {
        NamespaceRegistry.register("Mouse", args -> (double) Minecraft.getInstance().mouseHandler.xpos(),
                "获取X", "getX", "get_x", "X");
        NamespaceRegistry.register("Mouse", args -> (double) Minecraft.getInstance().mouseHandler.ypos(),
                "获取Y", "getY", "get_y", "Y");
        NamespaceRegistry.register("Mouse", args -> {
            double scale = Minecraft.getInstance().getWindow().getGuiScaledWidth()
                    / (double) Minecraft.getInstance().getWindow().getScreenWidth();
            return Minecraft.getInstance().mouseHandler.xpos() * scale;
        }, "获取缩放X", "getScaledX", "get_scaled_x");
        NamespaceRegistry.register("Mouse", args -> {
            double scale = Minecraft.getInstance().getWindow().getGuiScaledHeight()
                    / (double) Minecraft.getInstance().getWindow().getScreenHeight();
            return Minecraft.getInstance().mouseHandler.ypos() * scale;
        }, "获取缩放Y", "getScaledY", "get_scaled_y");
        NamespaceRegistry.register("Mouse", args -> {
            int button = args.length > 0 ? (int) num(args[0]) : 0;
            var handler = Minecraft.getInstance().mouseHandler;
            return switch (button) {
                case 0 -> handler.isLeftPressed();
                case 1 -> handler.isRightPressed();
                case 2 -> handler.isMiddlePressed();
                default -> false;
            };
        }, "是否按下", "isButtonDown", "is_down");
    }

    private static void collectElementIds(com.opendreamcore.page.Element el, List<String> out) {
        out.add(el.id());
        for (var child : el.children()) collectElementIds(child, out);
    }
}
