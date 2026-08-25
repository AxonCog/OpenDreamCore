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
 * C1 拆分自 ClientMethods。// Script 命名空间
 */
public final class ScriptMethods {

    private ScriptMethods() {
    }

    public static void register() {
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
            long delay = (long) ClientMethodSupport.num(args[0]);
            return (double) ClientController.get().scheduleScript(
                    String.valueOf(args[1]), delay, 0);
        }, "延迟执行", "delay", "delayExecute", "delay_execute");
        NamespaceRegistry.register("Script", args -> {
            // Script.计划执行(秒, 脚本) → 每 N 秒循环执行，返回任务 id
            if (args.length < 2 || args[1] == null) {
                return -1.0;
            }
            double seconds = ClientMethodSupport.num(args[0]);
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
                    String.valueOf(args[1]), (long) ClientMethodSupport.num(args[0]), key);
        }, "防抖", "debounce", "debounceExecute", "debounce_execute");
        NamespaceRegistry.register("Script", args -> {
            // Script.节流(毫秒, 脚本, 键?) → 周期内最多执行一次（周期末补跑合并尾调用）；返回任务 id
            if (args.length < 2 || args[1] == null) {
                return -1.0;
            }
            String key = args.length >= 3 && args[2] != null ? String.valueOf(args[2]) : null;
            return (double) ClientController.get().throttleScript(
                    String.valueOf(args[1]), (long) ClientMethodSupport.num(args[0]), key);
        }, "节流", "throttle", "throttleExecute", "throttle_execute");
        NamespaceRegistry.register("Script", args -> {
            // Script.取消(任务id)
            if (args.length < 1) {
                return false;
            }
            return ClientController.get().cancelScript((long) ClientMethodSupport.num(args[0]));
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
}