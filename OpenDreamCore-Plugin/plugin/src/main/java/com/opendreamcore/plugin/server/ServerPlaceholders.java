package com.opendreamcore.plugin.server;

import com.opendreamcore.plugin.util.LegacyItemCompat;
import com.opendreamcore.script.PlaceholderRegistry;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 服务端占位符实现（Bukkit）：{player.*} / {system.*} / {query.*} / {color.*}。
 * 解析目标玩家由 ThreadLocal 指定（ChatChannel 发送时按接收者逐人解析）。
 */
public final class ServerPlaceholders {

    private static final ThreadLocal<Player> CURRENT = new ThreadLocal<>();

    private ServerPlaceholders() {
    }

    /** %xxx% 令牌：PAPI 风格的写法，页面里最常见的那批。 */
    private static final Pattern PAPI_TOKEN = Pattern.compile("%([a-zA-Z0-9_]+)%");

    /** 在指定玩家上下文里解析占位符（消息按接收者个性化）。 */
    public static String resolveFor(Player player, String text) {
        CURRENT.set(player);
        try {
            String out = PlaceholderRegistry.resolve(text);
            out = papiSetPlaceholders(player, out);
            return papiFallback(player, out);
        } finally {
            CURRENT.remove();
        }
    }

    /** 装了 PlaceholderAPI 就先走它，没装返回原文。 */
    private static String papiSetPlaceholders(Player player, String text) {
        if (text == null || text.indexOf('%') < 0) {
            return text;
        }
        try {
            Class<?> api = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            return (String) api.getMethod("setPlaceholders", Player.class, String.class)
                    .invoke(null, player, text);
        } catch (ClassNotFoundException notInstalled) {
            return text;
        } catch (Throwable t) {
            return text;
        }
    }

    /**
     * PAPI 没装时，常见令牌用自家占位符顶上，页面里不再裸奂一串百分号。
     * 不认识的令牌原样保留，留给装了 PAPI 的服。
     */
    private static String papiFallback(Player player, String text) {
        if (text == null || text.indexOf('%') < 0) {
            return text;
        }
        Matcher m = PAPI_TOKEN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String nativeValue = nativeForPapi(m.group(1), player);
            m.appendReplacement(sb, Matcher.quoteReplacement(
                    nativeValue != null ? nativeValue : m.group()));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** PAPI 常见令牌 → 自家取值。返回 null 表示不认识。 */
    private static String nativeForPapi(String token, Player player) {
        if (player == null) {
            return null;
        }
        switch (token) {
            case "player": case "player_name":
                return player.getName();
            case "player_displayname":
                return player.getDisplayName();
            case "player_health":
                return trimNum(player.getHealth());
            case "player_max_health":
                return trimNum(player.getMaxHealth());
            case "player_hunger":
                return String.valueOf(player.getFoodLevel());
            case "player_level":
                return String.valueOf(player.getLevel());
            case "player_world":
                return player.getWorld().getName();
            case "player_gamemode":
                return player.getGameMode().name().toLowerCase();
            case "player_x":
                return trimNum(player.getLocation().getX());
            case "player_y":
                return trimNum(player.getLocation().getY());
            case "player_z":
                return trimNum(player.getLocation().getZ());
            case "player_ping":
                return String.valueOf(LegacyItemCompat.ping(player));
            case "server_online":
                return String.valueOf(Bukkit.getOnlinePlayers().size());
            case "server_max_players":
                return String.valueOf(Bukkit.getMaxPlayers());
            default:
                return null;
        }
    }

    /** 整数不带小数点，小数留一位，坐标别掉一地 9。 */
    private static String trimNum(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v)
                : String.valueOf(Math.round(v * 10.0) / 10.0);
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
                case "ping" -> p == null ? 0.0 : (double) LegacyItemCompat.ping(p);
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
                case "held_item_id" -> com.opendreamcore.plugin.util.LegacyItemCompat.isAir(p.getInventory().getItemInMainHand()) ? ""
                        : com.opendreamcore.plugin.util.LegacyItemCompat.key(p.getInventory().getItemInMainHand());
                case "held_item_count" -> com.opendreamcore.plugin.util.LegacyItemCompat.isAir(p.getInventory().getItemInMainHand()) ? 0.0
                        : (double) p.getInventory().getItemInMainHand().getAmount();
                case "offhand" -> heldName(p.getInventory().getItemInOffHand());
                case "offhand_id" -> com.opendreamcore.plugin.util.LegacyItemCompat.isAir(p.getInventory().getItemInOffHand()) ? ""
                        : com.opendreamcore.plugin.util.LegacyItemCompat.key(p.getInventory().getItemInOffHand());
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
        if (stack == null || com.opendreamcore.plugin.util.LegacyItemCompat.isAir(stack)) {
            return "";
        }
        if (stack.hasItemMeta() && stack.getItemMeta().hasDisplayName()) {
            return stack.getItemMeta().getDisplayName();
        }
        String key = com.opendreamcore.plugin.util.LegacyItemCompat.key(stack);
        int colon = key.indexOf(':');
        return colon >= 0 ? key.substring(colon + 1) : key;
    }

    /** 最近 TPS：Paper API getTPS → PAPI %server_tps% → 默认 20.0。 */
    private static double tickRate() {
        try {
            double[] tps;
        try {
            tps = (double[]) Bukkit.getServer().getClass().getMethod("getTPS").invoke(Bukkit.getServer());
        } catch (ReflectiveOperationException e) {
            tps = null;
        }
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
