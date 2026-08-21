package com.opendreamcore.client;

import com.opendreamcore.script.NamespaceRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.biome.Biome;

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
        registerScreen();
        registerTime();
        registerUuid();
        registerDisplay();
        registerCamera();
        registerMessage();
        registerTip();
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
        NamespaceRegistry.register("Display", args -> (double) Minecraft.getInstance().getWindow().getScreenWidth(), "窗口宽", "width");
        NamespaceRegistry.register("Display", args -> (double) Minecraft.getInstance().getWindow().getScreenHeight(), "窗口高", "height");
        NamespaceRegistry.register("Display", args -> (double) Minecraft.getInstance().getWindow().getGuiScale(), "界面缩放", "getScale", "get_scale", "scale");
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
}
