package com.opendreamcore.client.render;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 世界面板页签状态：页面 id → 当前激活页签。
 * 页签切换是客户端本地状态（点哪切哪），服务器只管把页面发下来；
 * 接鼠标交互后点页签调 switchTab，动画淡入靠 PageRenderer 的过渡时钟。
 */
public final class WorldPanels {

    private static final Map<String, String> ACTIVE_TAB = new ConcurrentHashMap<>();

    private WorldPanels() {
    }

    /** 页面当前激活页签；页面没声明 tab 时给 null（全部元素可见）。 */
    public static String activeTab(com.opendreamcore.page.Page page) {
        if (page == null) {
            return null;
        }
        String declared = declaredTab(page);
        if (declared == null) {
            return null;
        }
        return ACTIVE_TAB.getOrDefault(key(page), declared);
    }

    /** 从页签条元素里找默认激活页签（tabs.active，没有就用第一个）。 */
    private static String declaredTab(com.opendreamcore.page.Page page) {
        for (com.opendreamcore.page.Element e : page.elements()) {
            if (!"tabs".equals(norm(e.type()))) {
                continue;
            }
            Object tabsRaw = e.props().get("tabs");
            if (tabsRaw instanceof Map) {
                Map<?, ?> tabs = (Map<?, ?>) tabsRaw;
                Object active = tabs.get("active");
                if (active != null) {
                    return String.valueOf(active);
                }
                Object options = tabs.get("options");
                if (options instanceof List && !((List<?>) options).isEmpty()) {
                    return String.valueOf(((List<?>) options).get(0));
                }
            }
        }
        return null;
    }

    /** 切页签（点击页签时调）；不认识的页签名直接忽略。 */
    public static void switchTab(com.opendreamcore.page.Page page, String tab) {
        if (page == null || tab == null) {
            return;
        }
        if (page.options() != null && page.options().containsKey("world")) {
            ACTIVE_TAB.put(key(page), tab);
        }
    }

    /** 渲染层用：激活页签名兜底逻辑（无声明时取列表第一个）。 */
    public static String activeTabOf(List<String> options, Object declaredActive) {
        if (options == null || options.isEmpty()) {
            return null;
        }
        return declaredActive == null ? options.get(0) : String.valueOf(declaredActive);
    }

    public static void dropPage(String pageId) {
        ACTIVE_TAB.remove(String.valueOf(pageId) + "|");
    }

    private static String key(com.opendreamcore.page.Page page) {
        return String.valueOf(page.id()) + "|";
    }

    private static String norm(String type) {
        return type == null ? "" : type.toLowerCase(java.util.Locale.ROOT).replace("_", "");
    }
}
