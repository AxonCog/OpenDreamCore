package com.opendreamcore.client.api;

import java.util.Map;
import java.util.function.Consumer;

/**
 * 桥事件总线：客户端内部各层之间传话的口子，也是附属模组挂钩子的地方。
 *
 * 底下复用脚本那套 EventBus（名字 + 载荷），通道名统一加 "bridge:" 前缀，
 * 跟服务端自定义通道（"custom:"）分开：
 *   bridge:custom:<通道>   服务端经 custom_packet 下来的消息（Java 侧订阅）
 *   bridge:page:<动作>     页面开关、HUD 挂载之类的客户端事件
 *
 * 附属模组不用碰 DreamLang 也能收消息：
 * <pre>{@code
 * long id = BridgeEvents.on("custom:dreamcore:edit", payload -> { ... });
 * BridgeEvents.off(id);
 * }</pre>
 */
public final class BridgeEvents {

    private BridgeEvents() { }

    /** 订阅一个通道。回调可能在客户端主线程执行，别在这儿干重活。 */
    public static long on(String channel, Consumer<String> handler) {
        if (channel == null || channel.isEmpty() || handler == null) {
            return -1;
        }
        return com.opendreamcore.script.EventBus.subscribe(topic(channel),
                args -> {
                    handler.accept(args.length > 0 && args[0] != null ? String.valueOf(args[0]) : "");
                    return null;
                });
    }

    /** 取消订阅（用 on 返回的 id）。 */
    public static boolean off(long subscriptionId) {
        return subscriptionId >= 0 && com.opendreamcore.script.EventBus.unsubscribe(subscriptionId);
    }

    /** 发一条内部事件（附属模组自造事件也走这里）。 */
    public static void post(String channel, String payload) {
        if (channel == null || channel.isEmpty()) {
            return;
        }
        try {
            com.opendreamcore.script.EventBus.publish(topic(channel), payload);
        } catch (Exception ignored) {
            // 总线上的异常不外溢：一个订阅者挂了不该连累发送方
        }
    }

    /** 带结构化载荷的发布（值必须是脚本层认得的类型：字符串/数字/布尔/Map/List）。 */
    public static void post(String channel, Map<String, Object> payload) {
        if (channel == null || channel.isEmpty() || payload == null) {
            return;
        }
        try {
            com.opendreamcore.script.EventBus.publish(topic(channel), payload);
        } catch (Exception ignored) {
        }
    }

    /** 某个通道当前挂了几条订阅，排查用。 */
    public static int listenerCount(String channel) {
        return channel == null ? 0 : com.opendreamcore.script.EventBus.handlerCount(topic(channel));
    }

    /** 通道名 → 总线话题名。 */
    public static String topic(String channel) {
        return channel.startsWith("custom:") || channel.startsWith("bridge:")
                ? channel : "bridge:" + channel;
    }
}
