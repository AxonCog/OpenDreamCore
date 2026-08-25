package com.opendreamcore.plugin.server;

/**
 * 脚本执行上下文（线程绑定）：
 * 让 Screen.设置变量 等 Screen.* 命名空间方法知道"当前正在执行哪个页面的脚本"。
 * FunctionTriggers.run() 执行前 bind，finally 里 clear。
 */
public final class ScriptContext {

    private static final ThreadLocal<String> PAGE = new ThreadLocal<>();

    private ScriptContext() {
    }

    public static void bind(String pageId) {
        PAGE.set(pageId);
    }

    /** 当前正在执行的脚本所属页面 id（无上下文返回 null，例如控制台手动执行）。 */
    public static String currentPageId() {
        return PAGE.get();
    }

    public static void clear() {
        PAGE.remove();
    }
}
