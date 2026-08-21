package com.opendreamcore.api;

import org.bukkit.entity.Player;

/**
 * 元素 API：运行时控制页面元素（显隐、属性修改）。
 * 所有操作通过服务端 state_patch 下发到客户端，立即生效。
 */
public final class ElementAPI {

    static final ElementAPI INSTANCE = new ElementAPI();

    private ElementAPI() {
    }

    /** 显示元素。 */
    public boolean show(Player player, String elementId) {
        return gui().setElementProp(player, elementId, "visible", true);
    }

    /** 隐藏元素。 */
    public boolean hide(Player player, String elementId) {
        return gui().setElementProp(player, elementId, "visible", false);
    }

    /** 设置元素属性（支持 dotted path："text.content"、"button.label" 等）。 */
    public boolean setProp(Player player, String elementId, String path, Object value) {
        return gui().setElementProp(player, elementId, path, value);
    }



    private GUIAPI gui() {
        return OpenDreamCoreAPI.gui();
    }
}
