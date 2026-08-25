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
 * C1 拆分自 ClientMethods。// Display 命名空间
 */
public final class DisplayMethods {

    private DisplayMethods() {
    }

    public static void register() {
        NamespaceRegistry.register("Display", args -> (double) Minecraft.getInstance().getWindow().getScreenWidth(),
                "窗口宽", "getWidth", "get_width", "width");
        NamespaceRegistry.register("Display", args -> (double) Minecraft.getInstance().getWindow().getScreenHeight(),
                "窗口高", "getHeight", "get_height", "height");
        NamespaceRegistry.register("Display", args -> (double) Minecraft.getInstance().getWindow().getGuiScale(),
                "界面缩放", "getScale", "get_scale", "scale");
        NamespaceRegistry.register("Display", args -> (double) Minecraft.getInstance().getFps(),
                "获取FPS", "getFPS", "get_fps", "fps");
        NamespaceRegistry.register("Display", args -> {
            // Display.设置界面缩放(2) → 立即改 GUI 缩放（0 = 自动）
            double scale = args.length > 0 && args[0] instanceof Number n ? n.doubleValue() : 0;
            var window = Minecraft.getInstance().getWindow();
            if (scale <= 0) {
                scale = window.calculateScale(Minecraft.getInstance().options.guiScale().get(), false);
            }
            window.setGuiScale((int) scale);
            return true;
        }, "设置界面缩放", "setGuiScale", "set_gui_scale");
        NamespaceRegistry.register("Display", args -> Minecraft.getInstance().getWindow().isFullscreen(), "是否全屏", "isFullscreen", "is_fullscreen");
        NamespaceRegistry.register("Display", args -> {
            Minecraft.getInstance().getWindow().toggleFullScreen();
            return true;
        }, "切换全屏", "toggleFullscreen", "toggle_fullscreen");
    }
}