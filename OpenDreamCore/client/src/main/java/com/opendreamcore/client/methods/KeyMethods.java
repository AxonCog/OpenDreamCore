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
 * C1 拆分自 ClientMethods。// Key 命名空间
 */
public final class KeyMethods {

    private KeyMethods() {
    }

    public static void register() {
        NamespaceRegistry.register("Key", args -> {
            // Key.是否按下("key.keyboard.w")
            if (args.length < 1 || args[0] == null) {
                return false;
            }
            var mapping = ClientMethodSupport.keyMapping(String.valueOf(args[0]));
            return mapping != null && mapping.isDown();
        }, "是否按下", "isKeyDown", "is_down", "按下");
        NamespaceRegistry.register("Key", args -> {
            // Key.按键名("key.keyboard.w") → 当前绑定键的名字
            if (args.length < 1 || args[0] == null) {
                return "";
            }
            var mapping = ClientMethodSupport.keyMapping(String.valueOf(args[0]));
            return mapping == null ? "" : mapping.getName();
        }, "按键名", "getKeyName", "get_key_name");
    }
}