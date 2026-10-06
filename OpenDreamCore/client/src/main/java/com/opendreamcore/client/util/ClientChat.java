package com.opendreamcore.client.util;

import net.minecraft.client.Minecraft;

/**
 * 聊天栏输出工具（从 ClientController 迁出）：普通输出、带去重的失败提示、异常缩略。
 */
public final class ClientChat {

    private ClientChat() {
    }

    public static void chat(String text) {
        try {
            var mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal(text), false);
            }
        } catch (Throwable ignored) {
            // 没玩家/没启动完：静默，别拖垮调用方
        }
    }

    /** 失败提示去重表：同一 key 只说一次，防每帧/每包刷屏。 */
    private static final java.util.Set<String> CHAT_WARNED = java.util.Collections.newSetFromMap(
            new java.util.concurrent.ConcurrentHashMap<>());

    /** 带去重的失败提示（key 相同只发一次）。 */
    public static void chatWarnOnce(String key, String text) {
        if (CHAT_WARNED.size() > 256) {
            CHAT_WARNED.clear(); // 防膨胀：刷满就重置，宁可多提醒也别永久哑巴
        }
        if (CHAT_WARNED.add(key)) {
            chat(text);
        }
    }

    /** 异常原因缩略：聊天栏一句话说清，别贴全文堆栈。 */
    public static String shortReason(Throwable t) {
        if (t == null) {
            return "未知原因";
        }
        String s = String.valueOf(t);
        return s.length() > 120 ? s.substring(0, 120) + "…" : s;
    }
}
