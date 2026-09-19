package com.opendreamcore.client.visual;

/**
 * 按键名归一化与 LWJGL2 键码桥（1.6.4～1.12.2 同款）。
 *
 * 规则里写的是 KeyConfig 组合串（如 C+LEFT_SHIFT+左键）；主键段归一后
 * 要跟 org.lwjgl.input.Keyboard 的键码对上——组合串规则用键名字符串，
 * 事件轮询用键码，这张表就是两边的翻译官。修饰键采样也是 LWJGL2 的事，
 * 各版钩子直接用，1.16.5 那边的 LWJGL3 自己写。
 */
public final class KeyNames {

    private KeyNames() {
    }

    /** 归一化：trim + 大写。LWJGL2 键名本来就是大写下划线（LEFT_SHIFT）。 */
    public static String normalize(String keyName) {
        return keyName == null ? "" : keyName.trim().toUpperCase();
    }

    /** 修饰键别名归并：CTRL→LEFT_CTRL、SHIFT→LEFT_SHIFT、ALT→LEFT_ALT。 */
    public static String canonical(String name) {
        String n = normalize(name);
        if ("CTRL".equals(n) || "CONTROL".equals(n) || "C".equals(n)) {
            return "LEFT_CTRL";
        }
        if ("SHIFT".equals(n)) {
            return "LEFT_SHIFT";
        }
        if ("ALT".equals(n)) {
            return "LEFT_ALT";
        }
        return n;
    }

    /** 主键名 → LWJGL2 键码。查不到（鼠标键名等）给 -1。 */
    public static int lwjgl2KeyCode(String name) {
        String n = normalize(name);
        try {
            // 反射拿 Keyboard 键码：1.16.5 是 LWJGL3 没这个类，编不过也跑不了，
            // 反射让共享层不背编译期依赖，运行时没有就 -1（当“没按”）
            Class<?> kb = Class.forName("org.lwjgl.input.Keyboard");
            return kb.getField("KEY_" + n).getInt(null);
        } catch (Throwable ignored) {
            return -1;
        }
    }

    /**
     * 键名 → 是否按下（LWJGL2 Keyboard.isKeyDown 包装）。
     * 查不到键码/键盘未初始化一律返 false——轮询把它当“没按”。
     */
    public static boolean lwjgl2IsKeyDown(String name) {
        int code = lwjgl2KeyCode(name);
        if (code < 0) {
            return false;
        }
        try {
            Class<?> kb = Class.forName("org.lwjgl.input.Keyboard");
            return (Boolean) kb.getMethod("isKeyDown", int.class).invoke(null, code);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 修饰键采样口（LWJGL2）：规则里写的修饰键名全部按当前物理状态判定。 */
    public static java.util.Set<String> lwjgl2Modifiers() {
        java.util.Set<String> out = new java.util.HashSet<>();
        if (lwjgl2IsKeyDown("LCONTROL") || lwjgl2IsKeyDown("RCONTROL")) {
            out.add("LEFT_CTRL");
            out.add("C");
        }
        if (lwjgl2IsKeyDown("LSHIFT") || lwjgl2IsKeyDown("RSHIFT")) {
            out.add("LEFT_SHIFT");
        }
        if (lwjgl2IsKeyDown("LMENU") || lwjgl2IsKeyDown("RMENU")) {
            out.add("LEFT_ALT");
        }
        return out;
    }
}
