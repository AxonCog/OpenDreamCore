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
 * C1 拆分自 ClientMethods。// Time 命名空间
 */
public final class TimeMethods {

    private TimeMethods() {
    }

    public static void register() {
        NamespaceRegistry.register("Time", args -> (double) (System.currentTimeMillis() / 1000), "当前时间戳", "now", "timestamp");
        NamespaceRegistry.register("Time", args -> (double) System.currentTimeMillis(), "当前毫秒", "millis");
        NamespaceRegistry.register("Time", args -> ClientMethodSupport.player() != null ? (double) ClientMethodSupport.player().level().getDayTime() : 0, "游戏时间", "gameTime", "game_time");
    }
}