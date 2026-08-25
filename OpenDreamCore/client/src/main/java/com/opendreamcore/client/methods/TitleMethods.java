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
 * C1 拆分自 ClientMethods。// Title 命名空间
 */
public final class TitleMethods {

    private TitleMethods() {
    }

    public static void register() {
        NamespaceRegistry.register("Title", args -> {
            // Title.显示标题(标题, 副标题?, 淡入?, 停留?, 淡出?)
            String title = args.length > 0 && args[0] != null ? String.valueOf(args[0]) : "";
            String subtitle = args.length > 1 && args[1] != null ? String.valueOf(args[1]) : "";
            int fadeIn = args.length > 2 && args[2] instanceof Number n ? n.intValue() : 10;
            int stay = args.length > 3 && args[3] instanceof Number n ? n.intValue() : 70;
            int fadeOut = args.length > 4 && args[4] instanceof Number n ? n.intValue() : 20;
            var gui = Minecraft.getInstance().gui;
            gui.setTimes(fadeIn, stay, fadeOut);
            gui.setSubtitle(Component.literal(subtitle));
            gui.setTitle(Component.literal(title));
            return true;
        }, "显示标题", "showTitle", "show_title");
    }
}