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
 * C1 拆分自 ClientMethods。// Network 命名空间
 */
public final class NetworkMethods {

    private NetworkMethods() {
    }

    public static void register() {
        NamespaceRegistry.register("Network", args -> {
            // Network.发送(通道, 内容) → 上行到服务端（无连接返回 false）
            if (args.length < 2 || args[0] == null) {
                return false;
            }
            String payload = args[1] == null ? "" : String.valueOf(args[1]);
            return ClientController.get().sendCustomPacket(String.valueOf(args[0]), payload);
        }, "发送", "send", "sendCustomPacket", "send_custom_packet");
        NamespaceRegistry.register("Network", args -> {
            // Network.订阅(通道, lambda) → 服务端下行分发（payload 作参数）；返回订阅 id
            if (args.length < 2 || args[0] == null
                    || !(args[1] instanceof com.opendreamcore.script.DreamLangExecutor.Callable c)) {
                return -1.0;
            }
            return (double) com.opendreamcore.script.EventBus.subscribe("custom:" + args[0], c);
        }, "订阅", "subscribe", "onCustomPacket", "on_custom_packet");
        NamespaceRegistry.register("Network", args -> {
            // Network.取消订阅(id)
            if (args.length < 1) {
                return false;
            }
            Object v = args[0];
            long id = v instanceof Number n ? n.longValue() : -1;
            return com.opendreamcore.script.EventBus.unsubscribe(id);
        }, "取消订阅", "unsubscribe", "off");
    }
}