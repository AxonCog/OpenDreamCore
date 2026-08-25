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
 * C1 拆分自 ClientMethods。// Mouse 命名空间
 */
public final class MouseMethods {

    private MouseMethods() {
    }

    public static void register() {
        NamespaceRegistry.register("Mouse", args -> (double) Minecraft.getInstance().mouseHandler.xpos(),
                "获取X", "getX", "get_x", "X");
        NamespaceRegistry.register("Mouse", args -> (double) Minecraft.getInstance().mouseHandler.ypos(),
                "获取Y", "getY", "get_y", "Y");
        NamespaceRegistry.register("Mouse", args -> {
            double scale = Minecraft.getInstance().getWindow().getGuiScaledWidth()
                    / (double) Minecraft.getInstance().getWindow().getScreenWidth();
            return Minecraft.getInstance().mouseHandler.xpos() * scale;
        }, "获取缩放X", "getScaledX", "get_scaled_x");
        NamespaceRegistry.register("Mouse", args -> {
            double scale = Minecraft.getInstance().getWindow().getGuiScaledHeight()
                    / (double) Minecraft.getInstance().getWindow().getScreenHeight();
            return Minecraft.getInstance().mouseHandler.ypos() * scale;
        }, "获取缩放Y", "getScaledY", "get_scaled_y");
        NamespaceRegistry.register("Mouse", args -> {
            int button = args.length > 0 ? (int) ClientMethodSupport.num(args[0]) : 0;
            var handler = Minecraft.getInstance().mouseHandler;
            return switch (button) {
                case 0 -> handler.isLeftPressed();
                case 1 -> handler.isRightPressed();
                case 2 -> handler.isMiddlePressed();
                default -> false;
            };
        }, "是否按下", "isButtonDown", "is_down");
    }
}