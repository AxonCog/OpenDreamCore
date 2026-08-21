package com.opendreamcore.client;

import com.opendreamcore.protocol.message.ChatMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 聊天通道存储：服务端 chat_message 写入的通道消息（按通道隔离）。
 * chat_display 指定 channel 时从这里取数渲染；"all" 或未指定走全局聊天缓存。
 */
public final class ChatStore {

    /** 通道内一条消息。 */
    public record Entry(long id, String text) {
    }

    private final Map<String, List<Entry>> channels = new ConcurrentHashMap<>();
    private static final int MAX_PER_CHANNEL = 200;

    /** 处理服务端通道消息（ADD/EDIT/REMOVE/CLEAR）。 */
    public void handle(ChatMessage message) {
        List<Entry> list = channels.computeIfAbsent(message.channel(), k -> new ArrayList<>());
        synchronized (list) {
            switch (message.action()) {
                case ADD -> {
                    list.add(new Entry(message.id(), message.text()));
                    while (list.size() > MAX_PER_CHANNEL) {
                        list.remove(0);
                    }
                }
                case EDIT -> {
                    for (int i = 0; i < list.size(); i++) {
                        if (list.get(i).id() == message.id()) {
                            list.set(i, new Entry(message.id(), message.text()));
                            return;
                        }
                    }
                }
                case REMOVE -> list.removeIf(e -> e.id() == message.id());
                case CLEAR -> list.clear();
            }
        }
    }

    /** 通道消息（新 → 旧）。 */
    public List<String> messages(String channel) {
        List<Entry> list = channels.get(channel);
        if (list == null) {
            return List.of();
        }
        synchronized (list) {
            List<String> out = new ArrayList<>(list.size());
            for (int i = list.size() - 1; i >= 0; i--) {
                out.add(list.get(i).text());
            }
            return out;
        }
    }

    public void clear() {
        channels.clear();
    }
}
