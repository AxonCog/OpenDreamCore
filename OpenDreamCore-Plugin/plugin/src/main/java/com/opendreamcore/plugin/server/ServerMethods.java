package com.opendreamcore.plugin.server;

import com.opendreamcore.script.NamespaceRegistry;
import com.opendreamcore.plugin.OpenDreamCorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * 服务端脚本命名空间（裁决脚本用）：与客户端命名空间同名，但实现走 Bukkit API。
 * 页面 actions 里的脚本默认在服务端执行（服务端裁决），方法从这里取。
 */
public final class ServerMethods {

    private ServerMethods() {
    }

    public static void registerAll() {
        com.opendreamcore.script.CommonMethods.registerAll();
        registerPlayer();
        registerChat();
        registerServer();
        registerItem();
        registerScreen();
        registerSound();
        registerEconomy();
        registerTeleport();
        registerParticle();
        registerGame();
        registerEntity();
        registerContainer();
        registerChatChannel();
        registerWorldUi();
        registerHud();
        registerMusic();
        registerScript();
        registerNetwork();
        registerTooltip();
        registerDreamCoreCompat();
    }

    // ========== Tooltip（动态 tooltip 注册/样式/移除） ==========

    private static String strArg(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static void registerTooltip() {
        NamespaceRegistry.register("Tooltip", args -> {
            // Tooltip.注册(元素id, 文本) → 注册纯文本 tooltip 并全量广播（按玩家权限过滤）
            if (args.length < 2 || args[0] == null || args[1] == null) {
                return false;
            }
            var plugin = OpenDreamCorePlugin.get();
            plugin.tooltipManager().register(String.valueOf(args[0]), String.valueOf(args[1]));
            plugin.networkLayer().broadcastTooltips();
            return true;
        }, "注册", "register", "set");
        NamespaceRegistry.register("Tooltip", args -> {
            // Tooltip.注册样式(元素id, 文本, 颜色?, 背景?, 边框?, 宽度?, 权限?)
            if (args.length < 2 || args[0] == null || args[1] == null) {
                return false;
            }
            var plugin = OpenDreamCorePlugin.get();
            plugin.tooltipManager().registerStyled(String.valueOf(args[0]), String.valueOf(args[1]),
                    strArg(args.length > 2 ? args[2] : null),
                    strArg(args.length > 3 ? args[3] : null),
                    strArg(args.length > 4 ? args[4] : null),
                    args.length > 5 && args[5] instanceof Number n ? n.doubleValue() : 0,
                    strArg(args.length > 6 ? args[6] : null));
            plugin.networkLayer().broadcastTooltips();
            return true;
        }, "注册样式", "registerStyled", "setStyled");
        NamespaceRegistry.register("Tooltip", args -> {
            // Tooltip.移除(元素id)
            if (args.length < 1 || args[0] == null) {
                return false;
            }
            var plugin = OpenDreamCorePlugin.get();
            plugin.tooltipManager().unregister(String.valueOf(args[0]));
            plugin.networkLayer().broadcastTooltips();
            return true;
        }, "移除", "unregister", "remove");
    }

    // ========== Network（自定义双向通道 custom_packet 下行） ==========

    private static void registerNetwork() {
        NamespaceRegistry.register("Network", args -> {
            // Network.发送(玩家名, 通道, 内容) → 服务端 → 指定客户端
            if (args.length < 3 || args[0] == null || args[1] == null) {
                return false;
            }
            Player p = Bukkit.getPlayerExact(String.valueOf(args[0]));
            if (p == null) {
                return false;
            }
            com.opendreamcore.plugin.network.CustomPacketRegistry.send(
                    OpenDreamCorePlugin.get(), p, String.valueOf(args[1]),
                    args[2] == null ? "" : String.valueOf(args[2]));
            return true;
        }, "发送", "send", "sendCustomPacket", "send_custom_packet");
        NamespaceRegistry.register("Network", args -> {
            // Network.广播(通道, 内容) → 全部在线玩家
            if (args.length < 2 || args[0] == null) {
                return false;
            }
            com.opendreamcore.plugin.network.CustomPacketRegistry.broadcast(
                    OpenDreamCorePlugin.get(), String.valueOf(args[0]),
                    args[1] == null ? "" : String.valueOf(args[1]));
            return true;
        }, "广播", "broadcast", "broadcastCustomPacket", "broadcast_custom_packet");
    }

    // ========== Script（运行控制：执行/延迟/计划/打印） ==========

    private static void registerScript() {
        NamespaceRegistry.register("Script", args -> {
            // Script.执行(脚本[, 玩家]) — 立即执行（主线程）
            if (args.length < 1 || args[0] == null) {
                return false;
            }
            Player p = player(args.length > 1 ? new Object[]{args[1]} : new Object[0]);
            if (p == null) {
                return false;
            }
            executeFor(p, String.valueOf(args[0]));
            return true;
        }, "执行", "execute", "run");
        NamespaceRegistry.register("Script", args -> {
            // Script.延迟执行(毫秒, 脚本[, 玩家]) → 任务 id
            if (args.length < 2 || args[1] == null) {
                return -1.0;
            }
            long delay = (long) num(args[0]);
            Player p = player(args.length > 2 ? new Object[]{args[2]} : new Object[0]);
            if (p == null) {
                return -1.0;
            }
            String script = String.valueOf(args[1]);
            OpenDreamCorePlugin plugin = OpenDreamCorePlugin.get();
            int id = plugin.getServer().getScheduler().runTaskLater(plugin, () -> executeFor(p, script), delay / 50).getTaskId();
            return (double) id;
        }, "延迟执行", "delay", "delayExecute", "delay_execute");
        NamespaceRegistry.register("Script", args -> {
            // Script.计划执行(秒, 脚本[, 玩家]) → 循环任务 id
            if (args.length < 2 || args[1] == null) {
                return -1.0;
            }
            long interval = Math.max(1, (long) (num(args[0]) * 20));
            Player p = player(args.length > 2 ? new Object[]{args[2]} : new Object[0]);
            if (p == null) {
                return -1.0;
            }
            String script = String.valueOf(args[1]);
            OpenDreamCorePlugin plugin = OpenDreamCorePlugin.get();
            int id = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> executeFor(p, script), interval, interval).getTaskId();
            return (double) id;
        }, "计划执行", "schedule", "scheduleRepeating", "schedule_repeating");
        NamespaceRegistry.register("Script", args -> {
            // Script.取消(任务id)
            if (args.length < 1) {
                return false;
            }
            Bukkit.getScheduler().cancelTask((int) num(args[0]));
            return true;
        }, "取消", "cancel");
        NamespaceRegistry.register("Script", args -> {
            // Script.打印(消息[, 玩家]) → 玩家聊天栏 + 控制台
            String msg = args.length > 0 && args[0] != null ? String.valueOf(args[0]) : "";
            Player p = player(args.length > 1 ? new Object[]{args[1]} : new Object[0]);
            if (p != null) {
                p.sendMessage(msg);
            }
            OpenDreamCorePlugin.get().getLogger().info("[ODC-Script] " + msg);
            return msg;
        }, "打印", "print", "log");
        NamespaceRegistry.register("Script", args -> {
            // Script.调试(消息) → 仅控制台
            String msg = args.length > 0 && args[0] != null ? String.valueOf(args[0]) : "";
            OpenDreamCorePlugin.get().getLogger().info("[ODC-Script] " + msg);
            return msg;
        }, "调试", "debug");
    }

    /** 以玩家作用域执行脚本（页面变量不存在的场景：仅 player 绑定）。 */
    private static void executeFor(Player player, String script) {
        try {
            com.opendreamcore.script.Scope scope = new com.opendreamcore.script.Scope();
            scope.assignPlayer("name", player.getName());
            scope.assignPlayer("uuid", player.getUniqueId().toString());
            com.opendreamcore.script.DreamLang.execute(script, scope);
        } catch (Exception e) {
            OpenDreamCorePlugin.get().getLogger().warning("Script 执行失败 (" + player.getName() + "): " + e);
        }
    }

    // ========== Player ==========

    private static void registerPlayer() {
        NamespaceRegistry.register("Player", args -> {
            Player p = player(args);
            return p != null ? p.getName() : "";
        }, "获取名字", "getName", "get_name");
        NamespaceRegistry.register("Player", args -> {
            Player p = player(args);
            return p != null ? p.getHealth() : 0.0;
        }, "获取血量", "getHealth", "get_health");
        NamespaceRegistry.register("Player", args -> {
            Player p = player(args);
            return p != null ? p.getHealthScale() : 0.0;
        }, "获取最大血量", "getMaxHealth", "get_max_health");
        NamespaceRegistry.register("Player", args -> {
            Player p = player(args);
            return p != null ? p.getFoodLevel() : 0.0;
        }, "获取饥饿值", "getHunger", "get_hunger");
        NamespaceRegistry.register("Player", args -> {
            Player p = player(args);
            return p != null ? p.getLevel() : 0.0;
        }, "获取等级", "getLevel", "get_level");
        NamespaceRegistry.register("Player", args -> {
            Player p = player(args);
            return p != null ? p.getExp() : 0.0;
        }, "获取经验值", "getExp", "get_exp");
        NamespaceRegistry.register("Player", args -> {
            Player p = player(args);
            return p != null ? p.getLocation().getX() : 0.0;
        }, "获取坐标X", "getX", "get_x");
        NamespaceRegistry.register("Player", args -> {
            Player p = player(args);
            return p != null ? p.getLocation().getY() : 0.0;
        }, "获取坐标Y", "getY", "get_y");
        NamespaceRegistry.register("Player", args -> {
            Player p = player(args);
            return p != null ? p.getLocation().getZ() : 0.0;
        }, "获取坐标Z", "getZ", "get_z");
        NamespaceRegistry.register("Player", args -> {
            Player p = player(args);
            return p != null ? p.getGameMode().name().toLowerCase() : "";
        }, "获取游戏模式", "getGameMode", "get_gamemode");
        NamespaceRegistry.register("Player", args -> {
            Player p = player(args);
            return p != null ? p.getUniqueId().toString() : "";
        }, "获取UUID", "getUUID", "get_uuid");
        // 玩家状态（Bukkit 权威数据）
        NamespaceRegistry.register("Player", args -> {
            Player p = player(args);
            return p != null && p.isSneaking();
        }, "是否潜行", "isSneaking", "is_sneaking", "sneaking");
        NamespaceRegistry.register("Player", args -> {
            Player p = player(args);
            return p != null && p.isSprinting();
        }, "是否疾跑", "isSprinting", "is_sprinting", "sprinting");
        NamespaceRegistry.register("Player", args -> {
            Player p = player(args);
            return p != null && p.isFlying();
        }, "是否飞行", "isFlying", "is_flying", "flying");
        NamespaceRegistry.register("Player", args -> {
            Player p = player(args);
            return p != null && p.isSwimming();
        }, "是否游泳", "isSwimming", "is_swimming", "swimming");
        NamespaceRegistry.register("Player", args -> {
            Player p = player(args);
            return p != null && p.getFireTicks() > 0;
        }, "是否着火", "isOnFire", "is_on_fire", "onFire");
        NamespaceRegistry.register("Player", args -> {
            Player p = player(args);
            return p != null && p.isInWater();
        }, "是否在水中", "isInWater", "is_in_water", "inWater");
        NamespaceRegistry.register("Player", args -> {
            Player p = player(args);
            return p != null && p.isOnGround();
        }, "是否在地面", "isOnGround", "is_on_ground", "onGround");
        // 手持物品（Bukkit 权威）
        NamespaceRegistry.register("Player", args -> {
            Player p = player(args);
            if (p == null || p.getInventory().getItemInMainHand().getType().isAir()) {
                return "";
            }
            ItemStack stack = p.getInventory().getItemInMainHand();
            return stack.hasItemMeta() && stack.getItemMeta().hasDisplayName()
                    ? stack.getItemMeta().getDisplayName() : itemName(stack);
        }, "手持物品", "getHeldItem", "get_held_item", "heldItem", "主手物品", "getMainHandItem", "get_main_hand_item");
        NamespaceRegistry.register("Player", args -> {
            Player p = player(args);
            if (p == null || p.getInventory().getItemInOffHand().getType().isAir()) {
                return "";
            }
            ItemStack stack = p.getInventory().getItemInOffHand();
            return stack.hasItemMeta() && stack.getItemMeta().hasDisplayName()
                    ? stack.getItemMeta().getDisplayName() : itemName(stack);
        }, "副手物品", "getOffhandItem", "get_offhand_item", "offhandItem");
        NamespaceRegistry.register("Player", args -> {
            Player p = player(args);
            if (p == null || p.getInventory().getItemInMainHand().getType().isAir()) {
                return "";
            }
            return p.getInventory().getItemInMainHand().getType().getKey().toString();
        }, "手持物品ID", "getHeldItemId", "get_held_item_id", "heldItemId");
        NamespaceRegistry.register("Player", args -> {
            Player p = player(args);
            if (p == null || p.getInventory().getItemInMainHand().getType().isAir()) {
                return 0.0;
            }
            return (double) p.getInventory().getItemInMainHand().getAmount();
        }, "手持物品数量", "getHeldItemCount", "get_held_item_count", "heldItemCount");
        NamespaceRegistry.register("Player", args -> {
            Player p = player(args);
            var out = new java.util.ArrayList<Object>();
            if (p == null) {
                return out;
            }
            for (ItemStack stack : p.getInventory().getArmorContents()) {
                out.add(stack == null || stack.getType().isAir() ? "" : itemName(stack));
            }
            return out;
        }, "盔甲栏", "getArmor", "get_armor", "armor");
        // 视线方块（Bukkit 拾取，默认 5 格）
        NamespaceRegistry.register("Player", args -> {
            Player p = player(args);
            int dist = args.length > 1 ? (int) num(args[1]) : 5;
            org.bukkit.block.Block b = p == null ? null : p.getTargetBlockExact(dist);
            return b == null ? "" : b.getType().getKey().toString();
        }, "视线方块", "getLookingBlock", "get_looking_block", "lookingBlock", "视线方块名");
        NamespaceRegistry.register("Player", args -> {
            Player p = player(args);
            int dist = args.length > 1 ? (int) num(args[1]) : 5;
            org.bukkit.block.Block b = p == null ? null : p.getTargetBlockExact(dist);
            return b == null ? 0.0 : b.getLocation().getX() + 0.5;
        }, "视线方块X", "getLookingBlockX", "get_looking_block_x", "lookingBlockX");
        NamespaceRegistry.register("Player", args -> {
            Player p = player(args);
            int dist = args.length > 1 ? (int) num(args[1]) : 5;
            org.bukkit.block.Block b = p == null ? null : p.getTargetBlockExact(dist);
            return b == null ? 0.0 : b.getLocation().getY() + 0.5;
        }, "视线方块Y", "getLookingBlockY", "get_looking_block_y", "lookingBlockY");
        NamespaceRegistry.register("Player", args -> {
            Player p = player(args);
            int dist = args.length > 1 ? (int) num(args[1]) : 5;
            org.bukkit.block.Block b = p == null ? null : p.getTargetBlockExact(dist);
            return b == null ? 0.0 : b.getLocation().getZ() + 0.5;
        }, "视线方块Z", "getLookingBlockZ", "get_looking_block_z", "lookingBlockZ");
    }

    /** 物品显示名（无自定义名时用注册键路径，如 diamond_sword）。 */
    private static String itemName(ItemStack stack) {
        String key = stack.getType().getKey().toString();
        int colon = key.indexOf(':');
        return colon >= 0 ? key.substring(colon + 1) : key;
    }

    /** 参数可以是玩家名/UUID，没传就用动作绑定玩家（暂无绑定则第一个在线玩家）。 */
    private static Player player(Object[] args) {
        if (args.length > 0 && args[0] != null) {
            String target = String.valueOf(args[0]);
            Player byName = Bukkit.getPlayerExact(target);
            if (byName != null) {
                return byName;
            }
            try {
                return Bukkit.getPlayer(UUID.fromString(target));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
    }

    // ========== Chat ==========

    private static void registerChat() {
        NamespaceRegistry.register("Chat", args -> {
            if (args.length > 0 && args[0] != null) {
                Player p = player(args.length > 1 ? new Object[]{args[1]} : new Object[0]);
                if (p != null) {
                    p.sendMessage(String.valueOf(args[0]));
                }
            }
            return null;
        }, "发送消息", "sendMessage", "send_message", "say");
    }

    // ========== Server ==========

    private static void registerServer() {
        NamespaceRegistry.register("Server", args -> Bukkit.getOnlinePlayers().size(), "在线人数", "onlinePlayers", "online_players");
        NamespaceRegistry.register("Server", args -> Bukkit.getMaxPlayers(), "最大人数", "maxPlayers", "max_players");
        NamespaceRegistry.register("Server", args -> {
            if (args.length > 0 && args[0] != null) {
                Bukkit.broadcastMessage(String.valueOf(args[0]));
            }
            return null;
        }, "广播", "broadcast");
    }

    // ========== Item（手持物品 + 真正给物品） ==========

    private static void registerItem() {
        NamespaceRegistry.register("Item", args -> {
            Player p = player(args);
            if (p == null) {
                return "";
            }
            ItemStack hand = p.getInventory().getItemInMainHand();
            return hand == null || hand.getType().isAir() ? "" : hand.getType().name().toLowerCase();
        }, "手持物品", "getHandItem", "get_hand_item");
        NamespaceRegistry.register("Item", args -> {
            // Item.给予物品(玩家, "minecraft:diamond", 数量)
            Player p = player(args.length > 0 ? new Object[]{args[0]} : new Object[0]);
            if (p == null || args.length < 2 || args[1] == null) {
                return false;
            }
            String id = String.valueOf(args[1]).replaceFirst("^minecraft:", "");
            int amount = args.length > 2 && args[2] instanceof Number n ? n.intValue() : 1;
            org.bukkit.Material material = org.bukkit.Material.matchMaterial(id);
            if (material == null) {
                return false;
            }
            ItemStack stack = new ItemStack(material, Math.max(1, amount));
            p.getInventory().addItem(stack);
            return true;
        }, "给予物品", "giveItem", "give_item", "给物品");
    }

    // ========== Screen（页面状态） ==========

    private static void registerScreen() {
        NamespaceRegistry.register("Screen", args -> {
            Player p = player(args.length > 2 ? new Object[]{args[2]} : new Object[0]);
            if (p == null || args.length < 2 || args[0] == null || args[1] == null) {
                return false;
            }
            var values = new java.util.LinkedHashMap<String, Object>();
            values.put(String.valueOf(args[0]), args[1]);
            OpenDreamCorePlugin.get().sendStatePatch(p, values);
            return true;
        }, "更新状态", "setState", "set_state", "更新变量", "setVar", "set_var");
        NamespaceRegistry.register("Screen", args -> {
            // Screen.设置变量(名, 值[, 玩家?])
            // - 写回服务端页面变量（下次触发器 vars.* 读到最新值，如 showreel 的 ready 自增）
            // - 第三参指定玩家 → 只给该玩家发状态补丁；
            //   无玩家上下文（tick 触发器）→ 广播给所有已打开界面的玩家（各自应用到当前页面）
            if (args.length < 2 || args[0] == null) {
                return false;
            }
            String key = String.valueOf(args[0]);
            Object value = args[1];
            Player target = player(args.length > 2 ? new Object[]{args[2]} : new Object[0]);
            String pageId = ScriptContext.currentPageId();
            if (pageId == null) {
                // 无页面上下文：回退找第一个声明了该变量的页面
                for (String id : OpenDreamCorePlugin.get().pageManager().ids()) {
                    com.opendreamcore.page.Page pg = OpenDreamCorePlugin.get().pageManager().get(id);
                    if (pg != null && pg.variables() != null && pg.variables().containsKey(key)) {
                        pageId = id;
                        break;
                    }
                }
            }
            if (pageId != null) {
                com.opendreamcore.page.Page page = OpenDreamCorePlugin.get().pageManager().get(pageId);
                if (page != null && page.variables() != null) {
                    page.variables().put(key, value);
                }
            }
            var values = new java.util.LinkedHashMap<String, Object>();
            values.put(key, value);
            if (target != null) {
                OpenDreamCorePlugin.get().sendStatePatch(target, values);
            } else {
                for (Player online : Bukkit.getOnlinePlayers()) {
                    OpenDreamCorePlugin.get().sendStatePatch(online, values);
                }
            }
            return true;
        }, "设置变量", "setVariable", "set_variable", "写入变量");
        NamespaceRegistry.register("Screen", args -> {
            Player p = player(args);
            if (p != null) {
                OpenDreamCorePlugin.get().closePage(p);
            }
            return null;
        }, "关闭页面", "closePage", "close_page");
        NamespaceRegistry.register("Screen", args -> {
            // Screen.打开页面(页面id, 玩家)
            Player p = player(args.length > 1 ? new Object[]{args[1]} : new Object[0]);
            if (p == null || args.length < 1 || args[0] == null) {
                return false;
            }
            OpenDreamCorePlugin.get().openPage(p, String.valueOf(args[0]));
            return true;
        }, "打开页面", "openPage", "open_page");
        NamespaceRegistry.register("Screen", args -> {
            // Screen.设置世界元素位置(页面id, 元素id, x, y, z[, 玩家?]) — 更新服务端页面 hologram 并同步给同页玩家
            if (args.length < 5 || args[0] == null || args[1] == null) {
                return false;
            }
            var page = OpenDreamCorePlugin.get().pageManager().get(String.valueOf(args[0]));
            if (page == null) {
                return false;
            }
            com.opendreamcore.page.Element element = findServerElement(page, String.valueOf(args[1]));
            if (element == null) {
                return false;
            }
            double x = num(args[2]);
            double y = num(args[3]);
            double z = num(args[4]);
            Object raw = element.props().get("hologram");
            java.util.Map<Object, Object> holo = new java.util.LinkedHashMap<>(
                    raw instanceof java.util.Map<?, ?> m ? (java.util.Map<?, ?>) m : java.util.Map.of());
            holo.put("x", x);
            holo.put("y", y);
            holo.put("z", z);
            element.props().put("hologram", holo);
            // 持久化（重启保留）
            OpenDreamCorePlugin.get().pageManager()
                    .saveWorldPosition(String.valueOf(args[0]), element.id(), x, y, z);
            // 重发给打开该页面的全部玩家（含指定玩家）
            for (Player online : Bukkit.getOnlinePlayers()) {
                OpenDreamCorePlugin.get().openPage(online, String.valueOf(args[0]));
            }
            return true;
        }, "设置世界元素位置", "setWorldElementPos", "set_world_element_pos", "世界元素位置");
        NamespaceRegistry.register("Screen", args -> {
            // Screen.设置世界页签(玩家, 页面, 页签) — 强制某玩家切换世界面板页签
            Player p = player(args.length > 0 ? new Object[]{args[0]} : new Object[0]);
            if (p == null || args.length < 3 || args[1] == null || args[2] == null) {
                return false;
            }
            OpenDreamCorePlugin.get().networkLayer().sendWorldTab(p, String.valueOf(args[1]), String.valueOf(args[2]));
            return true;
        }, "设置世界页签", "setWorldTab", "set_world_tab", "世界页签");
        NamespaceRegistry.register("Screen", args -> {
            // Screen.广播世界页签(页面, 页签) — 给所有正在看该页面的玩家切换页签
            if (args.length < 2 || args[0] == null || args[1] == null) {
                return false;
            }
            OpenDreamCorePlugin.get().networkLayer()
                    .broadcastWorldTab(String.valueOf(args[0]), String.valueOf(args[1]));
            return true;
        }, "广播世界页签", "broadcastWorldTab", "broadcast_world_tab", "全体世界页签");
        NamespaceRegistry.register("Screen", args -> {
            // Screen.设置元素可见(玩家, 页面, 元素, 布尔) — 强制某玩家隐藏/显示世界元素
            Player p = player(args.length > 0 ? new Object[]{args[0]} : new Object[0]);
            if (p == null || args.length < 4 || args[1] == null || args[2] == null) {
                return false;
            }
            OpenDreamCorePlugin.get().networkLayer().sendWorldElementState(p,
                    String.valueOf(args[1]), String.valueOf(args[2]),
                    com.opendreamcore.protocol.message.WorldElementState.MODE_VISIBLE,
                    Boolean.parseBoolean(String.valueOf(args[3])));
            return true;
        }, "设置元素可见", "setElementVisible", "set_element_visible", "元素可见");
        NamespaceRegistry.register("Screen", args -> {
            // Screen.设置元素可用(玩家, 页面, 元素, 布尔) — 强制某玩家禁用/启用世界元素
            Player p = player(args.length > 0 ? new Object[]{args[0]} : new Object[0]);
            if (p == null || args.length < 4 || args[1] == null || args[2] == null) {
                return false;
            }
            OpenDreamCorePlugin.get().networkLayer().sendWorldElementState(p,
                    String.valueOf(args[1]), String.valueOf(args[2]),
                    com.opendreamcore.protocol.message.WorldElementState.MODE_ENABLED,
                    Boolean.parseBoolean(String.valueOf(args[3])));
            return true;
        }, "设置元素可用", "setElementEnabled", "set_element_enabled", "元素可用");
        NamespaceRegistry.register("Screen", args -> {
            // Screen.广播元素可见(页面, 元素, 布尔) — 给所有正在看该页面的玩家
            if (args.length < 3 || args[0] == null || args[1] == null) {
                return false;
            }
            OpenDreamCorePlugin.get().networkLayer().broadcastWorldElementState(
                    String.valueOf(args[0]), String.valueOf(args[1]),
                    com.opendreamcore.protocol.message.WorldElementState.MODE_VISIBLE,
                    Boolean.parseBoolean(String.valueOf(args[2])));
            return true;
        }, "广播元素可见", "broadcastElementVisible", "broadcast_element_visible", "全体元素可见");
        NamespaceRegistry.register("Screen", args -> {
            // Screen.广播元素可用(页面, 元素, 布尔) — 给所有正在看该页面的玩家
            if (args.length < 3 || args[0] == null || args[1] == null) {
                return false;
            }
            OpenDreamCorePlugin.get().networkLayer().broadcastWorldElementState(
                    String.valueOf(args[0]), String.valueOf(args[1]),
                    com.opendreamcore.protocol.message.WorldElementState.MODE_ENABLED,
                    Boolean.parseBoolean(String.valueOf(args[2])));
            return true;
        }, "广播元素可用", "broadcastElementEnabled", "broadcast_element_enabled", "全体元素可用");
        NamespaceRegistry.register("Screen", args -> {
            // Screen.打开子页(页面id, 玩家)：在当前页之上叠子页
            Player p = player(args.length > 1 ? new Object[]{args[1]} : new Object[0]);
            if (p == null || args.length < 1 || args[0] == null) {
                return false;
            }
            OpenDreamCorePlugin.get().openSubPage(p, String.valueOf(args[0]));
            return true;
        }, "打开子页", "openSubPage", "open_sub_page");
        // 组件方法：服务端动态改元素属性（经 state_patch 的 @元素id.路径 约定）
        NamespaceRegistry.register("Screen", args -> {
            // Screen.设置元素(玩家, 元素id, 路径, 值)
            Player p = player(args.length > 0 ? new Object[]{args[0]} : new Object[0]);
            if (p == null || args.length < 4 || args[1] == null || args[2] == null) {
                return false;
            }
            var values = new java.util.LinkedHashMap<String, Object>();
            values.put("@" + args[1] + "." + args[2], args[3]);
            OpenDreamCorePlugin.get().sendStatePatch(p, values);
            return true;
        }, "设置元素", "setElement", "set_element", "设置元素属性", "setElementProp", "set_element_prop");
        // 屏幕特效下发（服务端远程触发客户端效果，经 ui_effect 通道）
        NamespaceRegistry.register("Screen", args -> {
            // Screen.屏幕震动(玩家, 强度, 时长ms)
            Player p = player(args.length > 0 ? new Object[]{args[0]} : new Object[0]);
            if (p == null) {
                return false;
            }
            double strength = num(args, 1);
            double duration = num(args, 2);
            if (duration <= 0) {
                duration = 300;
            }
            sendEffect(p, new com.opendreamcore.protocol.message.UiEffect(
                    com.opendreamcore.protocol.message.UiEffect.Kind.SHAKE, strength, duration, ""));
            return true;
        }, "屏幕震动", "shakeScreen", "shake_screen", "震动");
        NamespaceRegistry.register("Screen", args -> {
            // Screen.闪屏(玩家, 颜色, 时长ms)
            Player p = player(args.length > 0 ? new Object[]{args[0]} : new Object[0]);
            if (p == null) {
                return false;
            }
            String color = args.length > 1 && args[1] != null ? String.valueOf(args[1]) : "#FFFFFF";
            double duration = num(args, 2);
            if (duration <= 0) {
                duration = 200;
            }
            sendEffect(p, new com.opendreamcore.protocol.message.UiEffect(
                    com.opendreamcore.protocol.message.UiEffect.Kind.FLASH, duration, 0, color));
            return true;
        }, "闪屏", "flashScreen", "flash_screen");
        NamespaceRegistry.register("Screen", args -> {
            // Screen.过渡(玩家, 颜色?, 时长ms) — 全屏淡入淡出遮罩
            Player p = player(args.length > 0 ? new Object[]{args[0]} : new Object[0]);
            if (p == null) {
                return false;
            }
            String color = args.length > 1 && args[1] != null ? String.valueOf(args[1]) : "#000000";
            double duration = num(args, 2);
            if (duration <= 0) {
                duration = 400;
            }
            sendEffect(p, new com.opendreamcore.protocol.message.UiEffect(
                    com.opendreamcore.protocol.message.UiEffect.Kind.TRANSITION, duration, 0, color));
            return true;
        }, "过渡", "transition");
        // 动画远程触发（ui_animation 通道；名称 = 页面 animations 里的命名动画）
        NamespaceRegistry.register("Screen", args -> {
            // Screen.播放动画(玩家, 名称...) — 多名称 = 顺序播放序列
            Player p = player(args.length > 0 ? new Object[]{args[0]} : new Object[0]);
            if (p == null || args.length < 2) {
                return false;
            }
            String[] names = new String[args.length - 1];
            for (int i = 1; i < args.length; i++) {
                names[i - 1] = args[i] == null ? "" : String.valueOf(args[i]);
            }
            sendAnimation(p, com.opendreamcore.protocol.message.UiAnimation.Action.PLAY, names);
            return true;
        }, "播放动画", "playAnimation", "play_animation", "播放动画序列", "playSequence", "play_sequence");
        NamespaceRegistry.register("Screen", args -> {
            // Screen.停止动画(玩家, 名称)
            Player p = player(args.length > 0 ? new Object[]{args[0]} : new Object[0]);
            if (p == null || args.length < 2 || args[1] == null) {
                return false;
            }
            sendAnimation(p, com.opendreamcore.protocol.message.UiAnimation.Action.STOP,
                    new String[]{String.valueOf(args[1])});
            return true;
        }, "停止动画", "stopAnimation", "stop_animation", "停止");
        NamespaceRegistry.register("Screen", args -> {
            // Screen.暂停动画(玩家, 名称)
            Player p = player(args.length > 0 ? new Object[]{args[0]} : new Object[0]);
            if (p == null || args.length < 2 || args[1] == null) {
                return false;
            }
            sendAnimation(p, com.opendreamcore.protocol.message.UiAnimation.Action.PAUSE,
                    new String[]{String.valueOf(args[1])});
            return true;
        }, "暂停动画", "pauseAnimation", "pause_animation", "暂停");
        NamespaceRegistry.register("Screen", args -> {
            // Screen.恢复动画(玩家, 名称)
            Player p = player(args.length > 0 ? new Object[]{args[0]} : new Object[0]);
            if (p == null || args.length < 2 || args[1] == null) {
                return false;
            }
            sendAnimation(p, com.opendreamcore.protocol.message.UiAnimation.Action.RESUME,
                    new String[]{String.valueOf(args[1])});
            return true;
        }, "恢复动画", "resumeAnimation", "resume_animation", "恢复");
    }

    /** 服务端页面元素递归查找（Screen.设置世界元素位置 用）。 */
    private static com.opendreamcore.page.Element findServerElement(
            com.opendreamcore.page.Page page, String id) {
        for (com.opendreamcore.page.Element e : page.elements()) {
            com.opendreamcore.page.Element found = findServerElement(e, id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static com.opendreamcore.page.Element findServerElement(
            com.opendreamcore.page.Element element, String id) {
        if (element.id().equals(id)) {
            return element;
        }
        for (com.opendreamcore.page.Element child : element.children()) {
            com.opendreamcore.page.Element found = findServerElement(child, id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** 下发动画触发。 */
    private static void sendAnimation(Player p, com.opendreamcore.protocol.message.UiAnimation.Action action,
                                      String[] names) {
        OpenDreamCorePlugin plugin = OpenDreamCorePlugin.get();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            var network = plugin.networkLayer();
            if (network != null) {
                network.sendUiAnimation(p, new com.opendreamcore.protocol.message.UiAnimation(action, names));
            }
        });
    }

    /** 下发屏幕特效（ui_effect 通道）。 */
    private static void sendEffect(Player p, com.opendreamcore.protocol.message.UiEffect effect) {
        OpenDreamCorePlugin plugin = OpenDreamCorePlugin.get();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            var network = plugin.networkLayer();
            if (network != null) {
                network.sendUiEffect(p, effect);
            }
        });
    }

    // ========== Sound（给指定玩家播音效） ==========

    private static void registerSound() {
        NamespaceRegistry.register("Sound", args -> {
            Player p = player(args.length > 1 ? new Object[]{args[1]} : new Object[0]);
            if (p == null || args.length < 1 || args[0] == null) {
                return false;
            }
            playSound(p, String.valueOf(args[0]),
                    args.length > 2 ? num(args[2]) : 1.0,
                    args.length > 3 ? num(args[3]) : 1.0);
            return true;
        }, "播放音效", "playSound", "play_sound", "播放", "play");
    }

    // ========== Economy（记分板经济，脚本可读写） ==========

    /** 记分板目标：odc_coin（服务端重启保留）。 */
    public static final String COIN_OBJECTIVE = "odc_coin";

    private static void registerEconomy() {
        NamespaceRegistry.register("Economy", args -> {
            Player p = player(args);
            if (p == null) {
                return 0.0;
            }
            return (double) coins(p);
        }, "获取金币", "getCoins", "get_coins", "余额");
        NamespaceRegistry.register("Economy", args -> {
            // Economy.设置金币(玩家, 数量)
            Player p = player(args.length > 0 ? new Object[]{args[0]} : new Object[0]);
            if (p == null || args.length < 2) {
                return false;
            }
            setCoins(p, (long) num(args[1]));
            return true;
        }, "设置金币", "setCoins", "set_coins");
        NamespaceRegistry.register("Economy", args -> {
            // Economy.增加金币(玩家, 数量)
            Player p = player(args.length > 0 ? new Object[]{args[0]} : new Object[0]);
            if (p == null || args.length < 2) {
                return false;
            }
            addCoins(p, (long) num(args[1]));
            return true;
        }, "增加金币", "addCoins", "add_coins", "扣除金币", "removeCoins", "remove_coins");
    }

    /** 读取玩家金币（记分板，无则 0）。 */
    public static long coins(Player p) {
        var objective = p.getScoreboard().getObjective(COIN_OBJECTIVE);
        if (objective == null) {
            return 0;
        }
        return objective.getScore(p).getScore();
    }

    public static void setCoins(Player p, long value) {
        var scoreboard = p.getScoreboard();
        var objective = scoreboard.getObjective(COIN_OBJECTIVE);
        if (objective == null) {
            objective = scoreboard.registerNewObjective(COIN_OBJECTIVE, "dummy", "金币");
        }
        objective.getScore(p).setScore((int) Math.max(0, value));
    }

    public static void addCoins(Player p, long delta) {
        setCoins(p, coins(p) + delta);
    }

    // ========== Teleport ==========

    private static void registerTeleport() {
        NamespaceRegistry.register("Teleport", args -> {
            // Teleport.传送(玩家, x, y, z[, 世界])
            Player p = player(args.length > 0 ? new Object[]{args[0]} : new Object[0]);
            if (p == null || args.length < 4) {
                return false;
            }
            var loc = p.getLocation();
            loc.setX(num(args[1]));
            loc.setY(num(args[2]));
            loc.setZ(num(args[3]));
            p.teleport(loc);
            return true;
        }, "传送", "teleport", "tp");
        NamespaceRegistry.register("Teleport", args -> {
            // Teleport.传送到出生点(玩家)
            Player p = player(args);
            if (p != null) {
                p.teleport(p.getWorld().getSpawnLocation());
            }
            return p != null;
        }, "传送到出生点", "toSpawn", "to_spawn");
    }

    // ========== Particle（粒子特效） ==========

    private static void registerParticle() {
        NamespaceRegistry.register("Particle", args -> {
            // Particle.播放(玩家, "minecraft:flame", x, y, z, 数量[, 速度])
            Player p = player(args.length > 0 ? new Object[]{args[0]} : new Object[0]);
            if (p == null || args.length < 5 || args[1] == null) {
                return false;
            }
            String id = String.valueOf(args[1]).replaceFirst("^minecraft:", "");
            org.bukkit.Particle particle = null;
            try {
                particle = org.bukkit.Particle.valueOf(id.toUpperCase().replace('.', '_'));
            } catch (IllegalArgumentException ignored) {
                return false;
            }
            double x = num(args, 2);
            double y = num(args, 3);
            double z = num(args, 4);
            int count = args.length > 5 ? (int) num(args, 5) : 20;
            double speed = args.length > 6 ? num(args, 6) : 0;
            p.getWorld().spawnParticle(particle, x, y, z, count, 0.3, 0.3, 0.3, speed);
            return true;
        }, "播放", "spawn", "播放粒子", "spawnParticle", "spawn_particle");
    }

    // ========== Game（世界控制） ==========

    private static void registerGame() {
        NamespaceRegistry.register("Game", args -> {
            // Game.设置时间(玩家, 时间tick)
            Player p = player(args);
            if (p == null || args.length < 1) {
                return false;
            }
            p.getWorld().setTime((long) num(args, 0));
            return true;
        }, "设置时间", "setTime", "set_time", "时间");
        NamespaceRegistry.register("Game", args -> {
            // Game.设置天气(玩家, "clear"/"rain"/"thunder")
            Player p = player(args);
            if (p == null || args.length < 1 || args[0] == null) {
                return false;
            }
            String w = String.valueOf(args[0]);
            if ("clear".equalsIgnoreCase(w)) {
                p.getWorld().setStorm(false);
                p.getWorld().setThundering(false);
            } else if ("rain".equalsIgnoreCase(w)) {
                p.getWorld().setStorm(true);
            } else if ("thunder".equalsIgnoreCase(w)) {
                p.getWorld().setStorm(true);
                p.getWorld().setThundering(true);
            }
            return true;
        }, "设置天气", "setWeather", "set_weather", "天气");
        NamespaceRegistry.register("Game", args -> {
            // Game.设置难度(玩家, "peaceful"/"easy"/"normal"/"hard")
            Player p = player(args);
            if (p == null || args.length < 1 || args[0] == null) {
                return false;
            }
            try {
                p.getWorld().setDifficulty(org.bukkit.Difficulty.valueOf(
                        String.valueOf(args[0]).toUpperCase()));
            } catch (IllegalArgumentException ignored) {
                return false;
            }
            return true;
        }, "设置难度", "setDifficulty", "set_difficulty", "难度");
    }

    // ========== Entity（实体操作） ==========

    private static void registerEntity() {
        NamespaceRegistry.register("Entity", args -> {
            // Entity.生成(玩家, "minecraft:zombie", x, y, z)
            Player p = player(args.length > 0 ? new Object[]{args[0]} : new Object[0]);
            if (p == null || args.length < 4 || args[1] == null) {
                return false;
            }
            String id = String.valueOf(args[1]).replaceFirst("^minecraft:", "").toUpperCase();
            try {
                var type = org.bukkit.entity.EntityType.valueOf(id);
                var loc = p.getLocation();
                loc.setX(num(args, 2));
                loc.setY(num(args, 3));
                loc.setZ(num(args, 4));
                p.getWorld().spawnEntity(loc, type);
                return true;
            } catch (IllegalArgumentException e) {
                return false;
            }
        }, "生成", "spawn", "生成实体", "spawnEntity", "spawn_entity");
        NamespaceRegistry.register("Entity", args -> {
            // Entity.清除附近(玩家, 半径)
            Player p = player(args);
            if (p == null) {
                return 0.0;
            }
            double radius = args.length > 0 ? num(args, 0) : 10;
            int removed = 0;
            for (var entity : p.getNearbyEntities(radius, radius, radius)) {
                if (!(entity instanceof Player)) {
                    entity.remove();
                    removed++;
                }
            }
            return (double) removed;
        }, "清除附近", "clearNearby", "clear_nearby");
    }

    // ========== Container（容器操作） ==========

    private static void registerContainer() {
        NamespaceRegistry.register("Container", args -> {
            // Container.打开箱子(玩家, 标题, 槽位数)
            Player p = player(args);
            if (p == null) {
                return false;
            }
            String title = args.length > 0 && args[0] != null ? String.valueOf(args[0]) : "箱子";
            int size = args.length > 1 ? (int) num(args, 1) : 27;
            size = Math.max(9, Math.min(54, (size / 9) * 9));
            var inv = org.bukkit.Bukkit.createInventory(null, size, title);
            p.openInventory(inv);
            return true;
        }, "打开箱子", "openChest", "open_chest");
        NamespaceRegistry.register("Container", args -> {
            // Container.获取物品(会话id, 槽位) → 物品注册表 id（空槽返回空串）
            var binding = binding(args);
            if (binding == null || args.length < 2) {
                return "";
            }
            org.bukkit.inventory.ItemStack item = binding.inventory().getItem((int) num(args, 1));
            return item == null || item.getType().isAir() ? "" : item.getType().getKey().toString();
        }, "获取物品", "getItem", "get_item");
        NamespaceRegistry.register("Container", args -> {
            // Container.获取数量(会话id, 槽位) → 数量（空槽 0）
            var binding = binding(args);
            if (binding == null || args.length < 2) {
                return 0.0;
            }
            org.bukkit.inventory.ItemStack item = binding.inventory().getItem((int) num(args, 1));
            return item == null ? 0.0 : (double) item.getAmount();
        }, "获取数量", "getCount", "get_count");
        NamespaceRegistry.register("Container", args -> {
            // Container.设置物品(会话id, 槽位, "minecraft:diamond", 数量?)（数量省略 = 1；物品空 = 清空槽）
            var binding = binding(args);
            if (binding == null || args.length < 2) {
                return false;
            }
            int slot = (int) num(args, 1);
            String itemId = args.length > 2 && args[2] != null ? String.valueOf(args[2]) : null;
            if (itemId == null || itemId.isBlank()) {
                binding.inventory().setItem(slot, null);
            } else {
                int count = args.length > 3 ? (int) num(args, 3) : 1;
                org.bukkit.NamespacedKey key = org.bukkit.NamespacedKey.fromString(itemId);
                if (key == null) {
                    return false;
                }
                var material = org.bukkit.Registry.MATERIAL.get(key);
                if (material == null || !material.isItem()) {
                    return false;
                }
                org.bukkit.inventory.ItemStack item = new org.bukkit.inventory.ItemStack(material, Math.max(1, count));
                binding.inventory().setItem(slot, item);
            }
            resync(binding);
            return true;
        }, "设置物品", "setItem", "set_item");
        NamespaceRegistry.register("Container", args -> {
            // Container.取物品(会话id, 槽位, 数量?) → 容器槽 → 玩家背包（先堆叠同类栈、再放空槽，尽量多取）
            // 返回实际移动数量（0 = 未移动）
            var binding = binding(args);
            if (binding == null || args.length < 2 || binding.player() == null) {
                return 0.0;
            }
            int slot = (int) num(args, 1);
            int want = args.length > 2 ? (int) num(args, 2) : Integer.MAX_VALUE;
            org.bukkit.inventory.ItemStack from = binding.inventory().getItem(slot);
            if (from == null || from.getType().isAir() || want <= 0) {
                return 0.0;
            }
            int moved = 0;
            var leftover = from.clone();
            // 1) 堆叠到玩家背包同物品栈
            for (var stack : binding.player().getInventory().getStorageContents()) {
                if (want <= moved || leftover.getAmount() <= 0) {
                    break;
                }
                if (stack == null || stack.getType().isAir() || !stack.isSimilar(from)) {
                    continue;
                }
                int room = from.getMaxStackSize() - stack.getAmount();
                if (room <= 0) {
                    continue;
                }
                int take = Math.min(room, Math.min(want - moved, leftover.getAmount()));
                stack.setAmount(stack.getAmount() + take);
                leftover.setAmount(leftover.getAmount() - take);
                moved += take;
            }
            // 2) 剩余放空槽
            var contents = binding.player().getInventory().getStorageContents();
            for (int i = 0; i < contents.length && want > moved && leftover.getAmount() > 0; i++) {
                var stack = contents[i];
                if (stack != null && !stack.getType().isAir()) {
                    continue;
                }
                int take = Math.min(from.getMaxStackSize(), Math.min(want - moved, leftover.getAmount()));
                var put = leftover.clone();
                put.setAmount(take);
                binding.player().getInventory().setItem(i, put);
                leftover.setAmount(leftover.getAmount() - take);
                moved += take;
            }
            if (moved > 0) {
                if (leftover.getAmount() <= 0) {
                    binding.inventory().setItem(slot, null);
                } else {
                    from.setAmount(leftover.getAmount());
                }
                binding.player().updateInventory();
                resync(binding);
            }
            return (double) moved;
        }, "取物品", "takeItem", "take_item", "take", "取出");
        NamespaceRegistry.register("Container", args -> {
            // Container.放入物品(会话id, 槽位, 玩家栏位, 数量?) → 玩家背包栏位 → 容器槽（同类栈堆叠、空槽放入）
            // 返回实际移动数量（0 = 未移动）
            var binding = binding(args);
            if (binding == null || args.length < 3 || binding.player() == null) {
                return 0.0;
            }
            int slot = (int) num(args, 1);
            int playerSlot = (int) num(args, 2);
            int want = args.length > 3 ? (int) num(args, 3) : Integer.MAX_VALUE;
            var inv = binding.player().getInventory();
            var storage = inv.getStorageContents();
            if (playerSlot < 0 || playerSlot >= storage.length) {
                return 0.0;
            }
            org.bukkit.inventory.ItemStack from = inv.getItem(playerSlot);
            if (from == null || from.getType().isAir() || want <= 0) {
                return 0.0;
            }
            org.bukkit.inventory.ItemStack target = binding.inventory().getItem(slot);
            int moved = 0;
            if (target != null && !target.getType().isAir() && target.isSimilar(from)) {
                // 目标槽同类栈：堆叠（不足补满）
                int room = target.getMaxStackSize() - target.getAmount();
                int take = Math.min(room, Math.min(want, from.getAmount()));
                if (take > 0) {
                    target.setAmount(target.getAmount() + take);
                    from.setAmount(from.getAmount() - take);
                    moved = take;
                }
            } else if (target == null || target.getType().isAir()) {
                // 目标槽空：整栈或指定数量放入
                int take = Math.min(from.getMaxStackSize(), Math.min(want, from.getAmount()));
                var put = from.clone();
                put.setAmount(take);
                binding.inventory().setItem(slot, put);
                from.setAmount(from.getAmount() - take);
                moved = take;
            }
            if (moved > 0) {
                if (from.getAmount() <= 0) {
                    inv.setItem(playerSlot, null);
                }
                binding.player().updateInventory();
                resync(binding);
            }
            return (double) moved;
        }, "放入物品", "putItem", "put_item", "put", "放入");
        NamespaceRegistry.register("Container", args -> {
            // Container.刷新(会话id) → 重发全量快照
            var binding = binding(args);
            if (binding == null) {
                return false;
            }
            resync(binding);
            return true;
        }, "刷新", "refresh", "resync");
        NamespaceRegistry.register("Container", args -> {
            // Container.槽位数(会话id)
            var binding = binding(args);
            return binding == null ? 0.0 : (double) binding.inventory().getSize();
        }, "槽位数", "getSize", "get_size");
        NamespaceRegistry.register("Container", args -> {
            // Container.标题(会话id)
            var binding = binding(args);
            return binding == null ? "" : binding.title();
        }, "标题", "getTitle", "get_title");
        NamespaceRegistry.register("Container", args -> {
            // Container.类型(会话id) → "minecraft:chest"
            var binding = binding(args);
            return binding == null ? "" : binding.type();
        }, "类型", "getType", "get_type");
        NamespaceRegistry.register("Container", args -> {
            // Container.关闭(会话id) → 关闭页面并解绑
            var binding = binding(args);
            if (binding == null) {
                return false;
            }
            OpenDreamCorePlugin plugin = OpenDreamCorePlugin.get();
            plugin.containerRegistry().unbind(binding.sessionId());
            plugin.closePage(binding.player());
            return true;
        }, "关闭", "close");
    }

    /** 按第一个参数（会话 id）找容器绑定。 */
    private static com.opendreamcore.plugin.container.ContainerRegistry.Binding binding(Object[] args) {
        if (args.length < 1 || args[0] == null) {
            return null;
        }
        return OpenDreamCorePlugin.get().containerRegistry().get(String.valueOf(args[0]));
    }

    /** 重发容器快照给绑定玩家。 */
    private static void resync(com.opendreamcore.plugin.container.ContainerRegistry.Binding binding) {
        OpenDreamCorePlugin plugin = OpenDreamCorePlugin.get();
        plugin.getLogger().info("容器刷新 " + binding.sessionId());
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            var network = plugin.networkLayer();
            if (network != null) {
                network.sendContainerSync(binding.player(), plugin.containerRegistry().snapshot(binding));
            }
        });
    }

    // ========== ChatChannel（聊天通道：chat_display 富文本消息源） ==========

    private static final java.util.concurrent.atomic.AtomicLong CHAT_IDS = new java.util.concurrent.atomic.AtomicLong();

    private static void registerChatChannel() {
        NamespaceRegistry.register("ChatChannel", args -> {
            // ChatChannel.发送(通道, 消息, 玩家名?) — 消息支持 §/& 颜色码；玩家名省略 = 全体
            if (args.length < 2 || args[0] == null) {
                return false;
            }
            String channel = String.valueOf(args[0]);
            String text = args[1] == null ? "" : String.valueOf(args[1]);
            Player target = args.length > 2 ? player(new Object[]{args[2]}) : null;
            sendChannel(channel, com.opendreamcore.protocol.message.ChatMessage.Action.ADD,
                    CHAT_IDS.incrementAndGet(), text, target);
            return true;
        }, "发送", "send", "add", "发送消息");
        NamespaceRegistry.register("ChatChannel", args -> {
            // ChatChannel.清空(通道, 玩家名?)
            if (args.length < 1 || args[0] == null) {
                return false;
            }
            Player target = args.length > 1 ? player(new Object[]{args[1]}) : null;
            sendChannel(String.valueOf(args[0]), com.opendreamcore.protocol.message.ChatMessage.Action.CLEAR,
                    0, "", target);
            return true;
        }, "清空", "clear");
        NamespaceRegistry.register("ChatChannel", args -> {
            // ChatChannel.删除(通道, id, 玩家名?)
            if (args.length < 2 || args[0] == null) {
                return false;
            }
            Player target = args.length > 2 ? player(new Object[]{args[2]}) : null;
            sendChannel(String.valueOf(args[0]), com.opendreamcore.protocol.message.ChatMessage.Action.REMOVE,
                    (long) num(args, 1), "", target);
            return true;
        }, "删除", "remove", "delete");
        NamespaceRegistry.register("ChatChannel", args -> {
            // ChatChannel.编辑(通道, id, 新内容, 玩家名?)
            if (args.length < 3 || args[0] == null) {
                return false;
            }
            Player target = args.length > 3 ? player(new Object[]{args[3]}) : null;
            sendChannel(String.valueOf(args[0]), com.opendreamcore.protocol.message.ChatMessage.Action.EDIT,
                    (long) num(args, 1), args[2] == null ? "" : String.valueOf(args[2]), target);
            return true;
        }, "编辑", "edit");
    }

    /** 发送通道消息（按接收者逐个解析占位符；目标为空 = 广播全体）。 */
    private static void sendChannel(String channel, com.opendreamcore.protocol.message.ChatMessage.Action action,
                                    long id, String text, Player target) {
        OpenDreamCorePlugin plugin = OpenDreamCorePlugin.get();
        plugin.getLogger().info("聊天通道 " + channel + " " + action + " (id=" + id + ")");
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            var network = plugin.networkLayer();
            if (network == null) {
                return;
            }
            java.util.List<Player> recipients = target != null
                    ? java.util.List.of(target)
                    : new java.util.ArrayList<>(org.bukkit.Bukkit.getOnlinePlayers());
            for (Player recipient : recipients) {
                // 按接收者上下文解析 {player.name} / {system.online} 等占位符
                String resolved = ServerPlaceholders.resolveFor(recipient, text);
                network.sendChatMessage(recipient,
                        new com.opendreamcore.protocol.message.ChatMessage(channel, action, id, resolved));
            }
        });
    }

    // ========== WorldUi（世界 UI：Boss 条 / 名牌 / 物品提示） ==========

    private static void registerWorldUi() {
        NamespaceRegistry.register("BossBar", args -> {
            // BossBar.创建(id, 文本, 进度0-100, 颜色, 玩家?)
            if (args.length < 3 || args[0] == null || args[1] == null) {
                return false;
            }
            String color = args.length > 3 && args[3] != null ? String.valueOf(args[3]) : "#E53935";
            Player target = optionalPlayer(args, 4);
            sendBossBar(new com.opendreamcore.protocol.message.BossBarSync(String.valueOf(args[0]),
                    com.opendreamcore.protocol.message.BossBarSync.Action.ADD,
                    String.valueOf(args[1]), num(args, 2), color), target);
            return true;
        }, "创建", "create", "add");
        NamespaceRegistry.register("BossBar", args -> {
            // BossBar.更新(id, 文本, 进度, 颜色, 玩家?)
            if (args.length < 3 || args[0] == null || args[1] == null) {
                return false;
            }
            String color = args.length > 3 && args[3] != null ? String.valueOf(args[3]) : "#E53935";
            Player target = optionalPlayer(args, 4);
            sendBossBar(new com.opendreamcore.protocol.message.BossBarSync(String.valueOf(args[0]),
                    com.opendreamcore.protocol.message.BossBarSync.Action.UPDATE,
                    String.valueOf(args[1]), num(args, 2), color), target);
            return true;
        }, "更新", "update");
        NamespaceRegistry.register("BossBar", args -> {
            // BossBar.移除(id, 玩家?)
            if (args.length < 1 || args[0] == null) {
                return false;
            }
            Player target = optionalPlayer(args, 1);
            sendBossBar(new com.opendreamcore.protocol.message.BossBarSync(String.valueOf(args[0]),
                    com.opendreamcore.protocol.message.BossBarSync.Action.REMOVE, "", 0, ""), target);
            return true;
        }, "移除", "remove");
        NamespaceRegistry.register("NameTag", args -> {
            // NameTag.设置(目标玩家名, 文本, 颜色?) — 全体可见
            if (args.length < 2 || args[0] == null || args[1] == null) {
                return false;
            }
            org.bukkit.entity.Player target = Bukkit.getPlayerExact(String.valueOf(args[0]));
            if (target == null) {
                return false;
            }
            String color = args.length > 2 && args[2] != null ? String.valueOf(args[2]) : "#FFFFFF";
            sendNameTag(new com.opendreamcore.protocol.message.NameTagSync(
                    target.getEntityId(), String.valueOf(args[1]), color));
            return true;
        }, "设置", "set", "设置名牌");
        NamespaceRegistry.register("NameTag", args -> {
            // NameTag.移除(目标玩家名)
            if (args.length < 1 || args[0] == null) {
                return false;
            }
            org.bukkit.entity.Player target = Bukkit.getPlayerExact(String.valueOf(args[0]));
            if (target == null) {
                return false;
            }
            sendNameTag(new com.opendreamcore.protocol.message.NameTagSync(target.getEntityId(), "", ""));
            return true;
        }, "移除", "remove");
        NamespaceRegistry.register("ItemTip", args -> {
            // ItemTip.显示(玩家, 物品id, 数量?, 时长ms?)
            Player p = player(args.length > 0 ? new Object[]{args[0]} : new Object[0]);
            if (p == null || args.length < 2 || args[1] == null) {
                return false;
            }
            int count = args.length > 2 ? (int) num(args, 2) : 1;
            int duration = args.length > 3 ? (int) num(args, 3) : 2000;
            sendItemTip(p, new com.opendreamcore.protocol.message.ItemTipSync(
                    String.valueOf(args[1]), count, duration));
            return true;
        }, "显示", "show", "showTip", "显示提示");
    }

    /** 可选玩家参数（空/缺省 = 全体广播）。 */
    private static Player optionalPlayer(Object[] args, int index) {
        if (args.length <= index || args[index] == null) {
            return null;
        }
        String name = String.valueOf(args[index]);
        return name.isBlank() ? null : Bukkit.getPlayerExact(name);
    }

    private static void sendBossBar(com.opendreamcore.protocol.message.BossBarSync sync, Player target) {
        OpenDreamCorePlugin plugin = OpenDreamCorePlugin.get();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            var network = plugin.networkLayer();
            if (network != null) {
                network.sendBossBar(sync, target);
            }
        });
    }

    private static void sendNameTag(com.opendreamcore.protocol.message.NameTagSync sync) {
        OpenDreamCorePlugin plugin = OpenDreamCorePlugin.get();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            var network = plugin.networkLayer();
            if (network != null) {
                network.sendNameTag(sync);
            }
        });
    }

    private static void sendItemTip(Player p, com.opendreamcore.protocol.message.ItemTipSync sync) {
        OpenDreamCorePlugin plugin = OpenDreamCorePlugin.get();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            var network = plugin.networkLayer();
            if (network != null) {
                network.sendItemTip(p, sync);
            }
        });
    }

    // ========== Hud（HUD 三型：个人 / 全局常驻 GHUD / 静态广播 HUDStatic） ==========

    private static void registerHud() {
        NamespaceRegistry.register("Hud", args -> {
            // Hud.挂载个人(页面id, 玩家?) — 按玩家编译下发，进服自动重挂
            if (args.length < 1 || args[0] == null) {
                return false;
            }
            Player target = optionalPlayer(args, 1);
            if (target == null) {
                return false;
            }
            mountHud(target, String.valueOf(args[0]),
                    com.opendreamcore.protocol.message.HudSync.Mode.HUD, true);
            return true;
        }, "挂载个人", "mountPlayer", "mount_player", "个人");
        NamespaceRegistry.register("Hud", args -> {
            // Hud.挂载全局(页面id) — 全体广播 + 新进服自动挂（全局常驻）
            if (args.length < 1 || args[0] == null) {
                return false;
            }
            String pageId = String.valueOf(args[0]);
            OpenDreamCorePlugin plugin = OpenDreamCorePlugin.get();
            plugin.hudRegistry().setGlobal(com.opendreamcore.protocol.message.HudSync.Mode.GHUD, pageId);
            broadcastHud(pageId, com.opendreamcore.protocol.message.HudSync.Mode.GHUD);
            return true;
        }, "挂载全局", "mountGlobal", "mount_global", "全局", "GHUD");
        NamespaceRegistry.register("Hud", args -> {
            // Hud.挂载静态(页面id) — 全体广播（静态公告，页面不用变量/占位符）
            if (args.length < 1 || args[0] == null) {
                return false;
            }
            String pageId = String.valueOf(args[0]);
            OpenDreamCorePlugin plugin = OpenDreamCorePlugin.get();
            plugin.hudRegistry().setGlobal(com.opendreamcore.protocol.message.HudSync.Mode.STATIC, pageId);
            broadcastHud(pageId, com.opendreamcore.protocol.message.HudSync.Mode.STATIC);
            return true;
        }, "挂载静态", "mountStatic", "mount_static", "静态", "HUDStatic");
        NamespaceRegistry.register("Hud", args -> {
            // Hud.卸载个人(玩家?)
            Player target = optionalPlayer(args, 0);
            if (target == null) {
                return false;
            }
            OpenDreamCorePlugin plugin = OpenDreamCorePlugin.get();
            plugin.hudRegistry().unmountPlayer(target);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                var network = plugin.networkLayer();
                if (network != null) {
                    network.closeHud(target);
                }
            });
            return true;
        }, "卸载个人", "unmountPlayer", "unmount_player");
        NamespaceRegistry.register("Hud", args -> {
            // Hud.卸载全局() / Hud.卸载静态()
            OpenDreamCorePlugin plugin = OpenDreamCorePlugin.get();
            plugin.hudRegistry().clearGlobal(com.opendreamcore.protocol.message.HudSync.Mode.GHUD);
            plugin.hudRegistry().clearGlobal(com.opendreamcore.protocol.message.HudSync.Mode.STATIC);
            for (Player online : Bukkit.getOnlinePlayers()) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    var network = plugin.networkLayer();
                    if (network != null) {
                        network.closeHud(online);
                    }
                });
            }
            return true;
        }, "卸载全局", "unmountGlobal", "unmount_global", "卸载");
    }

    private static void mountHud(Player target, String pageId,
                                 com.opendreamcore.protocol.message.HudSync.Mode mode, boolean remember) {
        OpenDreamCorePlugin plugin = OpenDreamCorePlugin.get();
        if (remember) {
            plugin.hudRegistry().mountPlayer(target, pageId);
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            var network = plugin.networkLayer();
            String yaml = plugin.pageManager().compiledYaml(pageId, target);
            if (network != null && yaml != null) {
                network.openHud(target, pageId, yaml, mode);
            }
        });
    }

    private static void broadcastHud(String pageId, com.opendreamcore.protocol.message.HudSync.Mode mode) {
        OpenDreamCorePlugin plugin = OpenDreamCorePlugin.get();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            var network = plugin.networkLayer();
            if (network == null) {
                return;
            }
            for (Player online : Bukkit.getOnlinePlayers()) {
                String yaml = plugin.pageManager().compiledYaml(pageId, online);
                if (yaml != null) {
                    network.openHud(online, pageId, yaml, mode);
                }
            }
        });
    }

    // ========== Music（背景音乐：文件在客户端 OpenDreamCore/music 或云端 music/） ==========

    private static void registerMusic() {
        NamespaceRegistry.register("Music", args -> {
            // Music.播放(玩家, 文件, 音量?, 循环?)
            Player p = player(args.length > 0 ? new Object[]{args[0]} : new Object[0]);
            if (p == null || args.length < 2 || args[1] == null) {
                return false;
            }
            double vol = args.length > 2 ? num(args, 2) : 0.8;
            boolean loop = args.length <= 3 || num(args, 3) != 0;
            sendMusic(p, new com.opendreamcore.protocol.message.MusicSync(
                    com.opendreamcore.protocol.message.MusicSync.Action.PLAY,
                    String.valueOf(args[1]), vol, loop));
            return true;
        }, "播放", "play");
        NamespaceRegistry.register("Music", args -> {
            // Music.停止(玩家)
            Player p = player(args.length > 0 ? new Object[]{args[0]} : new Object[0]);
            if (p == null) {
                return false;
            }
            sendMusic(p, new com.opendreamcore.protocol.message.MusicSync(
                    com.opendreamcore.protocol.message.MusicSync.Action.STOP, "", 0, false));
            return true;
        }, "停止", "stop");
        NamespaceRegistry.register("Music", args -> {
            // Music.音量(玩家, 0-1)
            Player p = player(args.length > 0 ? new Object[]{args[0]} : new Object[0]);
            if (p == null || args.length < 2) {
                return false;
            }
            sendMusic(p, new com.opendreamcore.protocol.message.MusicSync(
                    com.opendreamcore.protocol.message.MusicSync.Action.VOLUME, "", num(args, 1), false));
            return true;
        }, "音量", "volume", "setVolume");
    }

    private static void sendMusic(Player p, com.opendreamcore.protocol.message.MusicSync sync) {
        OpenDreamCorePlugin plugin = OpenDreamCorePlugin.get();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            var network = plugin.networkLayer();
            if (network != null) {
                network.sendMusic(p, sync);
            }
        });
    }

    private static void playSound(Player p, String soundName, double volume, double pitch) {
        String key = soundName.replaceFirst("^minecraft:", "");
        org.bukkit.Sound sound = null;
        try {
            sound = org.bukkit.Sound.valueOf(key.toUpperCase().replace('.', '_'));
        } catch (IllegalArgumentException ignored) {
            // 枚举没有就试注册表
        }
        if (sound == null) {
            var reg = org.bukkit.Registry.SOUNDS.get(org.bukkit.NamespacedKey.fromString(soundName));
            if (reg == null) {
                return;
            }
            p.playSound(p.getLocation(), reg, (float) volume, (float) pitch);
            return;
        }
        p.playSound(p.getLocation(), sound, (float) volume, (float) pitch);
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

    private static double num(Object[] args, int index) {
        if (args.length <= index || args[index] == null) {
            return 0;
        }
        return num(args[index]);
    }

    // ========== DreamCore 兼容方法（菜单.yml 等旧页面直接运行） ==========

    /** 注册 DreamCore 兼容的"方法"命名空间。 */
    private static void registerDreamCoreCompat() {
        String ns = "方法";

        // 方法.聊天(玩家名, 消息) → 发送聊天消息
        NamespaceRegistry.register(ns, args -> {
            if (args.length >= 2 && args[0] instanceof Player p) {
                p.sendMessage(String.valueOf(args[1]));
            }
            return null;
        }, "聊天");

        // 方法.替换(文本, 旧值, 新值) → 字符串替换
        NamespaceRegistry.register(ns, "替换", args -> {
            if (args.length < 3 || args[0] == null) return args.length > 0 ? args[0] : "";
            return String.valueOf(args[0]).replace(
                    String.valueOf(args[1]), String.valueOf(args[2]));
        });

        // 方法.合并文本(a, b, ...) → 拼接所有参数
        NamespaceRegistry.register(ns, "合并文本", args -> {
            var sb = new StringBuilder();
            for (Object a : args) {
                if (a != null) sb.append(a);
            }
            return sb.toString();
        });

        // 方法.延时(ms) → 延迟执行后续脚本（服务端调度）
        NamespaceRegistry.register(ns, "延时", args -> {
            if (args.length > 0) {
                long delayMs = (long) num(args, 0);
                Bukkit.getScheduler().runTaskLater(OpenDreamCorePlugin.get(),
                        () -> { }, Math.max(1, delayMs / 50)); // tick = 50ms
            }
            return null;
        });

        // 方法.取当前时间() → 当前毫秒时间戳
        NamespaceRegistry.register(ns, "取当前时间", args -> System.currentTimeMillis());

        // 方法.输出(文本) → 控制台日志
        NamespaceRegistry.register(ns, "输出", args -> {
            if (args.length > 0 && args[0] != null) {
                OpenDreamCorePlugin.get().getLogger().info(String.valueOf(args[0]));
            }
            return null;
        });
    }
}
