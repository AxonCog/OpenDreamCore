package com.opendreamcore.client.api;

import java.util.Map;

/**
 * 桥事件总线（老壳四版共用）：客户端内部各层传话的口子，也是附属模组挂钩子的地方。
 *
 * 底下复用脚本那套 EventBus（话题名 + 载荷），跟现代端 BridgeEvents 一个口径：
 *   custom:<通道>    服务端经 custom_packet 下来的消息（Java 侧也在这儿收）
 *   bridge:<名字>    客户端内部事件（页面开关、HUD 挂载之类）
 *
 * 附属模组不用碰 DreamLang 也能收发：
 * <pre>
 * long id = BridgeEvents.on("custom:dreamcore:shop", payload -> { ... });
 * BridgeEvents.off(id);
 * </pre>
 */
public final class BridgeEvents {

    private BridgeEvents() { }

    /** 收到消息时的回调。 */
    public interface Listener {
        void accept(String payload);
    }

    /** 订阅一个通道，返回订阅 id（off 用）。回调在服务端包到达的那一帧执行。 */
    public static long on(String channel, Listener listener) {
        if (channel == null || channel.isEmpty() || listener == null) {
            return -1;
        }
        final Listener sink = listener;
        return com.opendreamcore.script.EventBus.subscribe(topic(channel),
                new com.opendreamcore.script.DreamLangExecutor.Callable() {
                    @Override
                    public Object call(Object[] args) {
                        sink.accept(args != null && args.length > 0 && args[0] != null
                                ? String.valueOf(args[0]) : "");
                        return null;
                    }
                });
    }

    /** 取消订阅（用 on 返回的 id）。 */
    public static boolean off(long subscriptionId) {
        return subscriptionId >= 0 && com.opendreamcore.script.EventBus.unsubscribe(subscriptionId);
    }

    /** 发一条事件（载荷是字符串）。 */
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

    /** 带结构化载荷的发布（值限脚本层认得的类型）。 */
    public static void post(String channel, Map<String, Object> payload) {
        if (channel == null || channel.isEmpty() || payload == null) {
            return;
        }
        try {
            com.opendreamcore.script.EventBus.publish(topic(channel), payload);
        } catch (Exception ignored) {
        }
    }

    /** 某通道挂了几条订阅，排查用。 */
    public static int listenerCount(String channel) {
        return channel == null ? 0 : com.opendreamcore.script.EventBus.handlerCount(topic(channel));
    }

    /** 通道名 → 总线话题名：custom: 开头的照原样，其余补 bridge: 前缀。 */
    public static String topic(String channel) {
        return channel.startsWith("custom:") || channel.startsWith("bridge:")
                ? channel : "bridge:" + channel;
    }
}
