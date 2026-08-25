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
 * C1 拆分自 ClientMethods。// Camera 命名空间
 */
public final class CameraMethods {

    private CameraMethods() {
    }

    public static void register() {
        NamespaceRegistry.register("Camera", args -> ClientMethodSupport.x(), "获取X", "getX", "get_x");
        NamespaceRegistry.register("Camera", args -> ClientMethodSupport.y(), "获取Y", "getY", "get_y");
        NamespaceRegistry.register("Camera", args -> ClientMethodSupport.z(), "获取Z", "getZ", "get_z");
        NamespaceRegistry.register("Camera", args -> ClientMethodSupport.yaw(), "获取偏航", "getYaw", "get_yaw");
        NamespaceRegistry.register("Camera", args -> ClientMethodSupport.pitch(), "获取俯仰", "getPitch", "get_pitch");
    }
}