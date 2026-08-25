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
 * C1 拆分自 ClientMethods。// Music 命名空间
 */
public final class MusicMethods {

    private MusicMethods() {
    }

    public static void register() {
        NamespaceRegistry.register("Music", args -> {
            // Music.播放(文件, 音量?, 循环?)
            if (args.length < 1 || args[0] == null) {
                return false;
            }
            MusicPlayer.get().play(String.valueOf(args[0]),
                    args.length > 1 ? ClientMethodSupport.num(args[1]) : 0.8,
                    args.length > 2 && args[2] instanceof Number n && n.intValue() != 0);
            return true;
        }, "播放", "play");
        NamespaceRegistry.register("Music", args -> {
            MusicPlayer.get().stop();
            return true;
        }, "停止", "stop");
        NamespaceRegistry.register("Music", args -> {
            // Music.音量(0-1)
            MusicPlayer.get().volume(args.length > 0 ? ClientMethodSupport.num(args[0]) : 0.8);
            return true;
        }, "音量", "volume", "setVolume");
        NamespaceRegistry.register("Music", args -> MusicPlayer.get().isPlaying(), "是否播放", "isPlaying", "is_playing");
        NamespaceRegistry.register("Music", args -> {
            String current = MusicPlayer.get().current();
            return current == null ? "" : current;
        }, "当前曲目", "current", "getCurrent");
    }
}