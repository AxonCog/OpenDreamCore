package com.opendreamcore.client.render;

/**
 * 鼠标状态：共享层只负责存、边沿检测和命中测试。
 * 各 target 的壳在自家渲染钩子里喂 set()（每帧一次，开着界面时喂远处假坐标）；
 * 没喂的版本停在默认值——屏幕外角落，hover 永远不亮，行为安全。
 */
public final class MouseState {

    /** 逻辑像素坐标（与 screenWidth/Height 同一坐标系）。 */
    public static double mouseX = -9999;
    public static double mouseY = -9999;
    /** 左键按下中。 */
    public static boolean leftDown;

    /** 按下边沿捕获的点击位置（一帧一次，被 Interactions 消费掉才作废）。 */
    private static boolean clickQueued;
    private static double clickX;
    private static double clickY;
    /** 点击在队列里躺了几帧——太旧的作废，免得页面切换后误触发。 */
    private static int clickAge;
    /** 松开边沿（本帧刚抬起）。 */
    private static boolean releaseQueued;
    private static boolean lastDown;

    private MouseState() {
    }

    /** 每帧喂一次（来自各版壳的鼠标钩子）。边沿在这里检测。 */
    public static void set(double x, double y, boolean leftButtonDown) {
        if (leftButtonDown && !lastDown) {
            clickQueued = true;
            clickAge = 0;
            clickX = x;
            clickY = y;
        }
        if (!leftButtonDown && lastDown) {
            releaseQueued = true;
        }
        lastDown = leftButtonDown;
        mouseX = x;
        mouseY = y;
        leftDown = leftButtonDown;
        if (clickQueued && ++clickAge > 3) {
            clickQueued = false; // 三帧没人认领就作废
        }
    }

    /** 轴对齐命中：x/y/w/h 是绝对屏幕坐标。 */
    public static boolean hover(double x, double y, double w, double h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    /** 命中且正按着左键。 */
    public static boolean press(double x, double y, double w, double h) {
        return leftDown && hover(x, y, w, h);
    }

    /** 取走落在给定框里的待处理点击；取走即消费。 */
    public static boolean takeClick(double x, double y, double w, double h) {
        if (!clickQueued) {
            return false;
        }
        if (clickX >= x && clickX <= x + w && clickY >= y && clickY <= y + h) {
            clickQueued = false;
            return true;
        }
        return false;
    }

    /** 待处理点击的坐标（判断拖拽起点用）。 */
    public static double pendingClickX() {
        return clickX;
    }

    public static double pendingClickY() {
        return clickY;
    }

    /** 本帧是否刚抬起左键（取走即清）。 */
    public static boolean takeRelease() {
        boolean r = releaseQueued;
        releaseQueued = false;
        return r;
    }
}
