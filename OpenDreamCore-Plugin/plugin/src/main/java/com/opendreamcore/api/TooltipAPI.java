package com.opendreamcore.api;

import com.opendreamcore.plugin.OpenDreamCorePlugin;

/**
 * Tooltip API：运行时注册/移除元素悬停提示（服务端动态 tooltip，覆盖 YAML 静态 tooltip）。
 */
public final class TooltipAPI {

    static final TooltipAPI INSTANCE = new TooltipAPI();

    private TooltipAPI() {
    }

    /** 设置元素提示（所有玩家可见；文本支持颜色码）。 */
    public boolean setTooltip(String elementId, String text) {
        OpenDreamCorePlugin plugin = OpenDreamCoreAPI.plugin();
        if (plugin == null || elementId == null) {
            return false;
        }
        plugin.tooltipManager().register(elementId, text == null ? "" : text);
        rebroadcast();
        return true;
    }

    /** 移除元素提示。 */
    public boolean removeTooltip(String elementId) {
        OpenDreamCorePlugin plugin = OpenDreamCoreAPI.plugin();
        if (plugin == null || elementId == null) {
            return false;
        }
        plugin.tooltipManager().unregister(elementId);
        rebroadcast();
        return true;
    }

    /** 当前注册的提示文本（无则 null）。 */
    public String getTooltip(String elementId) {
        OpenDreamCorePlugin plugin = OpenDreamCoreAPI.plugin();
        return plugin == null ? null : plugin.tooltipManager().tooltipOf(elementId);
    }

    /** 手动重发注册表（其他插件直接操作后调用）。 */
    public boolean rebroadcast() {
        OpenDreamCorePlugin plugin = OpenDreamCoreAPI.plugin();
        if (plugin == null) {
            return false;
        }
        plugin.networkLayer().broadcastTooltips();
        return true;
    }
}
