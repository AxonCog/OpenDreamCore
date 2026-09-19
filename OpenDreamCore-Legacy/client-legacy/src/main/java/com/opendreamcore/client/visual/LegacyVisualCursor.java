package com.opendreamcore.client.visual;

/**
 * 光标形态（远古版）：页面元素 cursor 属性在 hover 时设置，
 * 屏幕渲染时按它画光标。Java8。
 */
public final class LegacyVisualCursor {

    private static volatile String current = "crosshair";

    private LegacyVisualCursor() {
    }

    /** hover 命中时设置。 */
    public static void set(String cursor) {
        current = cursor == null || cursor.trim().isEmpty() ? "crosshair" : cursor.trim();
    }

    /** 当前形态。 */
    public static String current() {
        return current;
    }

    /** crosshair 用原版，不用画。 */
    public static boolean custom() {
        return !"crosshair".equals(current);
    }

    /** 页面关了，光标本该恢复原样。 */
    public static void clear() {
        current = "crosshair";
    }
}
