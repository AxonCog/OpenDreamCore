package com.opendreamcore.client;

import com.opendreamcore.script.PlaceholderRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 客户端占位符实现：{player.*} / {entity.*} / {item.*} / {query.*} / {system.*} / {color.*}。
 * 与 {{vars.xxx}} 页面变量互补：{} 占位符在文本/标签/颜色属性里解析。
 * system 的未知名（online/max_players/server_name...）回落服务端 global 变量（5 秒推送）。
 */
public final class ClientPlaceholders {

    private ClientPlaceholders() {
    }

    /** 注册全部客户端占位符（客户端初始化时调用一次）。 */
    public static void registerAll() {
        registerColor();
        registerSystem();
        registerQuery();
        registerPlayer();
        registerEntity();
        registerItem();
    }

    private static Player player() {
        return Minecraft.getInstance().player;
    }

    // ========== color：命名颜色 → 色值 ==========

    private static void registerColor() {
        Map<String, String> colors = new java.util.LinkedHashMap<>();
        colors.put("black", "#000000");
        colors.put("dark_blue", "#0000AA");
        colors.put("dark_green", "#00AA00");
        colors.put("dark_aqua", "#00AAAA");
        colors.put("dark_red", "#AA0000");
        colors.put("dark_purple", "#AA00AA");
        colors.put("gold", "#FFAA00");
        colors.put("gray", "#AAAAAA");
        colors.put("dark_gray", "#555555");
        colors.put("blue", "#5555FF");
        colors.put("green", "#55FF55");
        colors.put("aqua", "#55FFFF");
        colors.put("red", "#FF5555");
        colors.put("light_purple", "#FF55FF");
        colors.put("yellow", "#FFFF55");
        colors.put("white", "#FFFFFF");
        colors.put("primary", "#7A8BFF");
        colors.put("secondary", "#9AA3B2");
        colors.put("success", "#66BB6A");
        colors.put("danger", "#E53935");
        colors.put("warning", "#FFD54F");
        colors.put("info", "#42A5F5");
        PlaceholderRegistry.register("color", key -> colors.get(key));
    }

    // ========== system：时间/日期/毫秒；未知回落 global ==========

    private static void registerSystem() {
        PlaceholderRegistry.register("system", key -> {
            switch (key) {
                case "time" -> {
                    return LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                }
                case "date" -> {
                    return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                }
                case "millis" -> {
                    return (double) System.currentTimeMillis();
                }
                case "uuid" -> {
                    return java.util.UUID.randomUUID().toString();
                }
                default -> {
                    // {system.online} 等服务端全局值（global_state 5 秒推送）
                    Object global = ClientController.get().globals().get(key);
                    return global;
                }
            }
        });
    }

    // ========== query：窗口/FPS ==========

    private static void registerQuery() {
        PlaceholderRegistry.register("query", key -> {
            var window = Minecraft.getInstance().getWindow();
            return switch (key) {
                case "width" -> (double) window.getGuiScaledWidth();
                case "height" -> (double) window.getGuiScaledHeight();
                case "fps" -> (double) Minecraft.getInstance().getFps();
                case "gui_scale" -> (double) window.getGuiScale();
                case "fullscreen" -> window.isFullscreen();
                case "tps" -> {
                    // 服务端通过 global_state 推送 TPS（Paper API / PAPI %server_tps%）
                    Object tps = ClientController.get().globals().get("tps");
                    if (tps instanceof Number n) {
                        yield n.doubleValue();
                    }
                    yield 20.0; // 单机/未连接时回退
                }
                case "ping" -> {
                    // 优先使用服务端推送的 ping（精确，global_state 含此字段）
                    Object ping = ClientController.get().globals().get("ping");
                    if (ping instanceof Number n) {
                        yield n.doubleValue();
                    }
                    // 回退：客户端自查延迟
                    var mc = Minecraft.getInstance();
                    var conn = mc.getConnection();
                    var player = mc.player;
                    yield conn != null && player != null && conn.getPlayerInfo(player.getUUID()) != null
                            ? (double) conn.getPlayerInfo(player.getUUID()).getLatency()
                            : 0.0;
                }
                default -> null;
            };
        });
    }

    // ========== player：当前玩家 ==========

    private static void registerPlayer() {
        PlaceholderRegistry.register("player", key -> {
            Player p = player();
            if (p == null) {
                return null;
            }
            return switch (key) {
                case "name", "display_name" -> p.getName().getString();
                case "health" -> (double) p.getHealth();
                case "max_health" -> (double) p.getMaxHealth();
                case "hunger" -> (double) p.getFoodData().getFoodLevel();
                case "level" -> (double) p.experienceLevel;
                case "exp" -> (double) p.experienceProgress;
                case "x" -> p.getX();
                case "y" -> p.getY();
                case "z" -> p.getZ();
                case "yaw" -> p.getYRot();
                case "pitch" -> p.getXRot();
                case "gamemode" -> Minecraft.getInstance().gameMode == null ? ""
                        : Minecraft.getInstance().gameMode.getPlayerMode().getName();
                case "biome" -> p.level() == null ? ""
                        : CompatRender.holderRegisteredName(p.level().getBiome(p.blockPosition()));
                case "dimension" -> p.level() == null ? "" : p.level().dimension().location().toString();
                case "online_time" -> (double) ClientController.get().onlineSeconds();
                case "held_item" -> p.getMainHandItem().isEmpty() ? "" : p.getMainHandItem().getHoverName().getString();
                case "held_item_id" -> p.getMainHandItem().isEmpty() ? ""
                        : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(p.getMainHandItem().getItem()).toString();
                case "held_item_count" -> (double) p.getMainHandItem().getCount();
                case "offhand" -> p.getOffhandItem().isEmpty() ? "" : p.getOffhandItem().getHoverName().getString();
                case "sneaking" -> p.isShiftKeyDown();
                case "sprinting" -> p.isSprinting();
                case "flying" -> p.getAbilities().flying;
                case "in_water" -> p.isInWater();
                case "on_ground" -> p.onGround();
                default -> null;
            };
        });
    }

    // ========== entity：准星指向实体 / 附近实体 ==========

    private static void registerEntity() {
        PlaceholderRegistry.register("entity", key -> {
            Player p = player();
            if (p == null || p.level() == null) {
                return null;
            }
            return switch (key) {
                case "target" -> {
                    Entity target = Minecraft.getInstance().crosshairPickEntity;
                    yield target == null ? "" : target.getName().getString();
                }
                case "target_type" -> {
                    Entity target = Minecraft.getInstance().crosshairPickEntity;
                    yield target == null ? "" : target.getType().toShortString();
                }
                case "count" -> {
                    // 16 格内非玩家实体数
                    double count = p.level().getEntitiesOfClass(Entity.class,
                            p.getBoundingBox().inflate(16.0), e -> e != p).size();
                    yield count;
                }
                default -> null;
            };
        });
    }

    // ========== item：手持物品 ==========

    private static void registerItem() {
        PlaceholderRegistry.register("item", key -> {
            Player p = player();
            if (p == null) {
                return null;
            }
            return switch (key) {
                case "hand" -> p.getMainHandItem().isEmpty() ? "" : p.getMainHandItem().getItem().toString();
                case "hand_name" -> p.getMainHandItem().isEmpty() ? "" : p.getMainHandItem().getHoverName().getString();
                case "count" -> (double) p.getMainHandItem().getCount();
                case "offhand" -> p.getOffhandItem().isEmpty() ? "" : p.getOffhandItem().getItem().toString();
                case "armor" -> (double) ((java.util.List<net.minecraft.world.item.ItemStack>) CompatRender.invArmor(p.getInventory()))
                        .stream().filter(s -> !s.isEmpty()).count();
                default -> null;
            };
        });
    }
}
