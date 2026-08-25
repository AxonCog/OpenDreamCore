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
 * C1 拆分自 ClientMethods。// Sound 命名空间
 */
public final class SoundMethods {

    private SoundMethods() {
    }

    public static void register() {
        NamespaceRegistry.register("Sound", args -> {
            if (args.length < 1) {
                return false;
            }
            ClientMethodSupport.playSound(String.valueOf(args[0]),
                    args.length > 1 ? ClientMethodSupport.num(args[1]) : 1.0,
                    args.length > 2 ? ClientMethodSupport.num(args[2]) : 1.0);
            return true;
        }, "播放音效", "playSound", "play_sound", "播放", "play");
        NamespaceRegistry.register("Sound", args -> {
            // Sound.循环播放(名字, 音效, 音量?, 音调?)
            if (args.length < 2 || args[0] == null || args[1] == null) {
                return false;
            }
            String name = String.valueOf(args[0]);
            SoundEvent event = ClientMethodSupport.soundEvent(String.valueOf(args[1]));
            SoundStore.get().playLoop(name, event,
                    args.length > 2 ? (float) ClientMethodSupport.num(args[2]) : 1.0F,
                    args.length > 3 ? (float) ClientMethodSupport.num(args[3]) : 1.0F);
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
}