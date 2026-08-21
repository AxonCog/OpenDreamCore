package com.opendreamcore.script;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 脚本事件总线：跨脚本/跨页面发布订阅（进程内单例，客户端/服务端各自 JVM 各自总线）。
 * 用法（脚本里）：
 *   Event.订阅("boss_killed", (name) => { Chat.发送消息(name + " 被击杀") })
 *   Event.发布("boss_killed", "凋零")
 * 发布时参数透传给订阅的 Lambda；单个订阅出错不影响其他订阅。
 * 纯 JDK 实现，可独立单测。
 */
public final class EventBus {

    /** 订阅条目：id + 处理器（脚本 Lambda）。 */
    public record Entry(long id, DreamLangExecutor.Callable handler) {
    }

    private static final Map<String, List<Entry>> HANDLERS = new ConcurrentHashMap<>();
    private static final AtomicLong IDS = new AtomicLong(1);

    private EventBus() {
    }

    /** 订阅事件；返回订阅 id（取消订阅用）。 */
    public static long subscribe(String name, DreamLangExecutor.Callable handler) {
        if (name == null || handler == null) {
            return -1;
        }
        long id = IDS.getAndIncrement();
        HANDLERS.computeIfAbsent(name, k -> new CopyOnWriteArrayList<>()).add(new Entry(id, handler));
        return id;
    }

    /** 按 id 取消订阅。 */
    public static boolean unsubscribe(long id) {
        for (List<Entry> list : HANDLERS.values()) {
            for (Entry entry : list) {
                if (entry.id() == id) {
                    list.remove(entry);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 发布事件：全部订阅按注册顺序触发（参数透传），返回最后一个处理器结果。
     * 单个订阅抛错不影响其他订阅。
     */
    public static Object publish(String name, Object... args) {
        List<Entry> list = HANDLERS.get(name);
        if (list == null || list.isEmpty()) {
            return null;
        }
        Object last = null;
        for (Entry entry : list) {
            try {
                last = entry.handler().call(args);
            } catch (Exception ignored) {
                // 单个订阅出错不影响其他订阅
            }
        }
        return last;
    }

    /** 清空单个事件的全部订阅。 */
    public static boolean clear(String name) {
        return HANDLERS.remove(name) != null;
    }

    /** 清空全部订阅。 */
    public static void clearAll() {
        HANDLERS.clear();
    }

    /** 某事件的订阅数（测试/调试用）。 */
    public static int handlerCount(String name) {
        List<Entry> list = HANDLERS.get(name);
        return list == null ? 0 : list.size();
    }
}
