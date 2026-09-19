package com.opendreamcore.client.spi;

/**
 * 远古键盘键入桥：四个老壳的屏幕收到按键事件后翻译成这几个动作，
 * 键入链表全部收在共享层（Interactions 持有焦点元素、改 props、回传
 * UiEvent.INPUT），版本壳只负责把自家按键键位码映射过来。
 */
public final class KeyboardBridge {

    private KeyboardBridge() {
    }

    /** 当前是否有输入框持有键盘焦点（渲染侧画光标用）。 */
    public static boolean hasFocus() {
        return com.opendreamcore.client.render.Interactions.hasInputFocus();
    }

    /** 键入一个可打印字符（含中文等任意 code point 高代理位之外的单 char）。 */
    public static void type(char c) {
        com.opendreamcore.client.render.Interactions.typeInto(c);
    }

    /** 退格：删掉焦点输入框最后一个字符并回传 INPUT。 */
    public static void backspace() {
        com.opendreamcore.client.render.Interactions.backspace();
    }

    /** 回车：多行框插换行，单行框丢焦。 */
    public static void enter() {
        com.opendreamcore.client.render.Interactions.enter();
    }

    /** 页面关 / 焦点转移时清焦。 */
    public static void clearFocus() {
        com.opendreamcore.client.render.Interactions.clearInputFocus();
    }
}