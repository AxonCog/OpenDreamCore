package com.opendreamcore.client;

import java.lang.reflect.Method;

/**
 * 键盘修饰键状态查询的版本自适应垫片。
 *
 * <p>mojmap 漂移：1.21.8 及以前是 {@code Screen.hasShiftDown()/hasControlDown()} 静态方法；
 * 1.21.9+ 移到 {@code Minecraft} 实例方法（Screen 上的静态形态被移除）。
 * 共享客户端树必须同时编译两代 API，故经反射择路：优先实例形态，回退静态形态。</p>
 */
public final class CompatInput {

    private CompatInput() {}

    public static boolean hasShiftDown() {
        return query("hasShiftDown");
    }

    public static boolean hasControlDown() {
        return query("hasControlDown");
    }

    public static boolean hasAltDown() {
        return query("hasAltDown");
    }

    private static boolean query(String name) {
        try {
            Object mc = net.minecraft.client.Minecraft.getInstance();
            Method instance = mc.getClass().getMethod(name);
            return Boolean.TRUE.equals(instance.invoke(mc));
        } catch (NoSuchMethodException noInstance) {
            try {
                Method legacy = net.minecraft.client.gui.screens.Screen.class.getMethod(name);
                return Boolean.TRUE.equals(legacy.invoke(null));
            } catch (Throwable t) {
                return false;
            }
        } catch (Throwable t) {
            return false;
        }
    }
}
