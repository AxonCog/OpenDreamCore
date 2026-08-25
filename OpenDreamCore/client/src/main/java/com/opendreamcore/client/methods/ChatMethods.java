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
 * C1 拆分自 ClientMethods。// Chat 命名空间
 */
public final class ChatMethods {

    private ChatMethods() {
    }

    public static void register() {
        NamespaceRegistry.register("Chat", args -> {
            ClientMethodSupport.sendChat(args.length > 0 ? String.valueOf(args[0]) : "");
            return null;
        }, "发送消息", "sendMessage", "send_message", "say");
        NamespaceRegistry.register("Chat", args -> {
            // Chat.打开聊天() → 打开原版聊天输入（关闭/发送后回到原页面）
            ClientMethodSupport.openVanillaChat("");
            return true;
        }, "打开聊天", "openChat", "open_chat");
        NamespaceRegistry.register("Chat", args -> {
            // Chat.设置聊天内容("文本") → 打开聊天并预填内容
            String text = args.length > 0 && args[0] != null ? String.valueOf(args[0]) : "";
            ClientMethodSupport.openVanillaChat(text);
            return true;
        }, "设置聊天内容", "setChatMessage", "set_chat_message");
        NamespaceRegistry.register("Chat", args -> {
            // Chat.添加消息("文本") → 本地聊天栏显示 + 记录
            String text = args.length > 0 ? String.valueOf(args[0]) : "";
            ClientMethodSupport.sendChat(text);
            ClientController.get().addChatMessage(text);
            return null;
        }, "添加消息", "addChatMessage", "add_chat_message");
        NamespaceRegistry.register("Chat", args -> {
            // Chat.获取最后消息() → 最近一条聊天（无则空串）
            var list = ClientController.get().latestChat(1);
            return list.isEmpty() ? "" : list.get(0);
        }, "获取最后消息", "getLastMessage", "get_last_message");
    }
}