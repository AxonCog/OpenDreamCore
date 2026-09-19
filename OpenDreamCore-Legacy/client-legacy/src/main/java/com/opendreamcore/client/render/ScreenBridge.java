package com.opendreamcore.client.render;

/**
 * 屏幕桥：交互页（菜单页/可点击的世界面板）需要真鼠标——各版壳注入自家
 * 的隐形指针屏实现（释放指针、回发 ESC），共享层只回答"这页该不该有指针"。
 * 没注入的壳全部退化为无指针模式（安全旧行为，HUD 叠加照常）。
 */
public final class ScreenBridge {

    public interface Host {
        /** 请求开指针屏（幂等：同一页重复开是无操作）；false = 这版没实现。 */
        boolean open(String pageId);

        /** 关掉指针屏（幂等，没开就空转）。 */
        void close();

        /** 指针屏当前是否开着。 */
        boolean isOpen();

        /** 页面被本地关掉（ESC）：壳清本地活跃页状态，别让状态检查又开回去。 */
        default void onDismissed(String pageId) {
        }
    }

    private static volatile Host host;

    private ScreenBridge() {
    }

    public static void setHost(Host h) {
        host = h;
    }

    public static boolean open(String pageId) {
        Host h = host;
        return h != null && h.open(pageId);
    }

    public static void close() {
        Host h = host;
        if (h != null) {
            h.close();
        }
    }

    public static boolean isOpen() {
        Host h = host;
        return h != null && h.isOpen();
    }

    public static void notifyDismissed(String pageId) {
        Host h = host;
        if (h != null) {
            h.onDismissed(pageId);
        }
    }
}
