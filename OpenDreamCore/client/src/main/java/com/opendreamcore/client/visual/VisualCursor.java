package com.opendreamcore.client.visual;

/**
 * 光标形态：页面元素 cursor 属性在 hover 时设置，屏幕渲染时按它画。
 *
 * 形态：
 *   crosshair      原版十字准心（默认，不画额外东西）
 *   point          手型指针
 *   其它字符串     当自定义贴图路径（resourcepacks/OpenDreamCore 下）
 */
public final class VisualCursor {

    private static volatile String current = "crosshair";
    private static volatile long untilMs;

    private VisualCursor() {
    }

    /** hover 命中时设置（传入当前系统时间毫秒 + 保持时长）。 */
    public static void set(String cursor, long holdMs) {
        current = cursor == null || cursor.isBlank() ? "crosshair" : cursor.trim();
        untilMs = holdMs <= 0 ? Long.MAX_VALUE : System.currentTimeMillis() + holdMs;
    }

    /** 当前形态；超过保持时长自动回 crosshair。 */
    public static String current() {
        if (untilMs != Long.MAX_VALUE && System.currentTimeMillis() > untilMs) {
            current = "crosshair";
        }
        return current;
    }

    /** 是否需要画自定义光标（crosshair 走原版，不需要碰）。 */
    public static boolean custom() {
        return !"crosshair".equals(current());
    }

    /** 清回原版（页面关闭/失焦时调用）。 */
    public static void clear() {
        current = "crosshair";
        untilMs = Long.MAX_VALUE;
    }
}