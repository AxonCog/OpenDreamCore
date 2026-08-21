package com.opendreamcore.plugin.server;

import com.opendreamcore.script.PlaceholderRegistry;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * 服务端占位符实现（Bukkit）：{player.*} / {system.*} / {query.*} / {color.*}。
 * 解析目标玩家由 ThreadLocal 指定（ChatChannel 发送时按接收者逐人解析）。
 */
public final class ServerPlaceholders {

    private static final ThreadLocal<Player> CURRENT = new ThreadLocal<>();

    private ServerPlaceholders() {
    }

    /** 在指定玩家上下文里解析占位符（消息按接收者个性化）。 */
    public static String resolveFor(Player player, String text) {
        CURRENT.set(player);
        try {
            return PlaceholderRegistry.resolve(text);
        } finally {
            CURRENT.remove();
        }
    }

    /** 注册全部服务端占位符（插件启用时调用一次）。 */
    public static void registerAll() {
        PlaceholderRegistry.register("color", key -> switch (key) {
            case "primary" -> "#7A8BFF";
            case "secondary" -> "#9AA3B2";
            case "success" -> "#66BB6A";
            case "danger" -> "#E53935";
            case "warning" -> "#FFD54F";
            case "info" -> "#42A5F5";
            case "black" -> "#000000";
            case "dark_blue" -> "#0000AA";
            case "dark_green" -> "#00AA00";
            case "dark_aqua" -> "#00AAAA";
            case "dark_red" -> "#AA0000";
            case "dark_purple" -> "#AA00AA";
            case "gold" -> "#FFAA00";
            case "gray" -> "#AAAAAA";
            case "dark_gray" -> "#555555";
            case "blue" -> "#5555FF";
            case "green" -> "#55FF55";
            case "aqua" -> "#55FFFF";
            case "red" -> "#FF5555";
            case "light_purple" -> "#FF55FF";
            case "yellow" -> "#FFFF55";
            case "white" -> "#FFFFFF";
            default -> null;
        });
        PlaceholderRegistry.register("system", key -> switch (key) {
            case "time" -> LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            case "date" -> LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            case "millis" -> (double) System.currentTimeMillis();
            case "online" -> (double) Bukkit.getOnlinePlayers().size();
            case "max_players" -> (double) Bukkit.getMaxPlayers();
            case "server_name" -> Bukkit.getName();
            case "uuid" -> java.util.UUID.randomUUID().toString();
            default -> null;
        });
        PlaceholderRegistry.register("query", key -> {
            Player p = CURRENT.get();
            return switch (key) {
                case "ping" -> p == null ? 0.0 : (double) p.getPing();
                case "tps" -> tickRate();
                default -> null;
            };
        });
        PlaceholderRegistry.register("player", key -> {
            Player p = CURRENT.get();
            if (p == null) {
                return null;
            }
            return switch (key) {
                case "name" -> p.getName();
                case "display_name" -> p.getDisplayName();
                case "health" -> (double) p.getHealth();
                case "max_health" -> (double) p.getHealthScale();
                case "hunger" -> (double) p.getFoodLevel();
                case "level" -> (double) p.getLevel();
                case "exp" -> (double) p.getExp();
                case "x" -> p.getLocation().getX();
                case "y" -> p.getLocation().getY();
                case "z" -> p.getLocation().getZ();
                case "yaw" -> (double) p.getLocation().getYaw();
                case "pitch" -> (double) p.getLocation().getPitch();
                case "gamemode" -> p.getGameMode().name().toLowerCase();
                case "world" -> p.getWorld().getName();
                case "uuid" -> p.getUniqueId().toString();
                case "online_time" -> (double) (System.currentTimeMillis() - p.getFirstPlayed());
                case "held_item" -> heldName(p.getInventory().getItemInMainHand());
                case "held_item_id" -> p.getInventory().getItemInMainHand().getType().isAir() ? ""
                        : p.getInventory().getItemInMainHand().getType().getKey().toString();
                case "held_item_count" -> p.getInventory().getItemInMainHand().getType().isAir() ? 0.0
                        : (double) p.getInventory().getItemInMainHand().getAmount();
                case "offhand" -> heldName(p.getInventory().getItemInOffHand());
                case "offhand_id" -> p.getInventory().getItemInOffHand().getType().isAir() ? ""
                        : p.getInventory().getItemInOffHand().getType().getKey().toString();
                case "sneaking" -> p.isSneaking();
                case "sprinting" -> p.isSprinting();
                case "flying" -> p.isFlying();
                case "in_water" -> p.isInWater();
                case "on_ground" -> p.isOnGround();
                default -> null;
            };
        });
    }

    /** 手持物品显示名（自定义名优先，否则注册键路径）。 */
    private static String heldName(org.bukkit.inventory.ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return "";
        }
        if (stack.hasItemMeta() && stack.getItemMeta().hasDisplayName()) {
            return stack.getItemMeta().getDisplayName();
        }
        String key = stack.getType().getKey().toString();
        int colon = key.indexOf(':');
        return colon >= 0 ? key.substring(colon + 1) : key;
    }

    /** 最近 TPS：Paper API getTPS → PAPI %server_tps% → 默认 20.0。 */
    private static double tickRate() {
        try {
            double[] tps = Bukkit.getTPS();
            if (tps != null && tps.length > 0 && tps[0] > 0) {
                return Math.min(20.0, tps[0]);
            }
        } catch (Throwable ignored) {
            // 非 Paper 服务端没有 getTPS
        }
        // 尝试 PAPI（如 servertools 等插件提供 %server_tps%）
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                Class<?> api = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                Object result = api.getMethod("setPlaceholders", org.bukkit.entity.Player.class, String.class)
                        .invoke(null, null, "%server_tps%");
                if (result != null && !String.valueOf(result).contains("%")) {
                    return Double.parseDouble(String.valueOf(result).trim());
                }
            }
        } catch (Throwable ignored) {
        }
        return 20.0;
    }
}
