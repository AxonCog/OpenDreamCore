package com.opendreamcore.client.render;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 渲染侧运行时数据角：每帧由渲染入口写入窗口逻辑尺寸，
 * 占位符 {query.width}/{query.height}/{query.fps} 从这取。
 * 全 volatile——渲染线程写、替换线程读，字面可见就够。
 */
public final class RenderSupport {

    public static volatile double winW;
    public static volatile double winH;

    private static final Map<String, Object> EXTRAS = new ConcurrentHashMap<>();

    private RenderSupport() {
    }

    /** 渲染入口每帧调（分辨率注入处顺手带上）。 */
    public static void frame(double width, double height) {
        winW = width;
        winH = height;
    }

    /** 各版塞杂项（fps 之类）。 */
    public static void put(String key, Object value) {
        EXTRAS.put(key, value);
    }

    /**
     * 老版 Minecraft.debugFPS 是私有的，生产环境字段还被改回 SRG 名：
     * 两个名字挨个试，都拿不到就不塞——占位符保持原样总比崩好。
     */
    public static void probeFps(Class<?> mcClass) {
        String[] names = {"debugFPS", "field_71470_ab"};
        for (String name : names) {
            try {
                java.lang.reflect.Field f = mcClass.getDeclaredField(name);
                f.setAccessible(true);
                put("fps", f.getInt(null));
                return;
            } catch (Throwable ignore) {
                // 下一个名字接着试
            }
        }
    }

    public static Object extra(String key) {
        return EXTRAS.get(key);
    }

    /** 进服时刻（毫秒）：握手 ready 时记，{player.online_time} 的起点。 */
    private static volatile long joinedAt;

    public static void markJoined() {
        joinedAt = System.currentTimeMillis();
    }

    public static long joinedAt() {
        return joinedAt;
    }
}
