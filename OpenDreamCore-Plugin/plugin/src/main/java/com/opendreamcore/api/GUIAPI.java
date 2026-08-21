package com.opendreamcore.api;

import com.opendreamcore.plugin.OpenDreamCorePlugin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 页面 API：给玩家开页面/子页、关页面、改变量与元素属性、绑定真实容器。
 * 底层复用服务端页面管线（扁平语法编译 + AES 加密下发 + 会话分配）。
 */
public final class GUIAPI {

    static final GUIAPI INSTANCE = new GUIAPI();

    private GUIAPI() {
    }

    /** 打开页面（编译 + 加密下发），返回会话 id（失败 null）。 */
    public String open(Player player, String pageId) {
        OpenDreamCorePlugin plugin = OpenDreamCoreAPI.plugin();
        if (plugin == null) {
            return null;
        }
        String yaml = plugin.pageManager().compiledYaml(pageId, player);
        if (yaml == null) {
            return null;
        }
        return plugin.networkLayer().openPage(player, pageId, yaml);
    }

    /** 打开子页（叠在当前页之上）。 */
    public boolean openSubPage(Player player, String pageId) {
        OpenDreamCorePlugin plugin = OpenDreamCoreAPI.plugin();
        if (plugin == null) {
            return false;
        }
        String yaml = plugin.pageManager().compiledYaml(pageId, player);
        if (yaml == null) {
            return false;
        }
        plugin.networkLayer().openSubPage(player, pageId, yaml);
        return true;
    }

    /** 关闭玩家当前页面。 */
    public boolean close(Player player) {
        OpenDreamCorePlugin plugin = OpenDreamCoreAPI.plugin();
        if (plugin == null) {
            return false;
        }
        plugin.closePage(player);
        return true;
    }

    /** 玩家当前是否有打开的页面。 */
    public boolean isOpen(Player player) {
        OpenDreamCorePlugin plugin = OpenDreamCoreAPI.plugin();
        return plugin != null && plugin.networkLayer().hasOpenPage(player);
    }

    /** 更新页面变量（state_patch，页面 {{vars.xxx}} 立即刷新）。 */
    public boolean setVariable(Player player, String key, Object value) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(key, value);
        return setVariables(player, values);
    }

    /** 批量更新页面变量。 */
    public boolean setVariables(Player player, Map<String, Object> values) {
        OpenDreamCorePlugin plugin = OpenDreamCoreAPI.plugin();
        if (plugin == null || values == null || values.isEmpty()) {
            return false;
        }
        plugin.sendStatePatch(player, values);
        return true;
    }

    /** 设置元素属性（"text.content" / "button.label" / "visible" 等点路径）。 */
    public boolean setElementProp(Player player, String elementId, String path, Object value) {
        OpenDreamCorePlugin plugin = OpenDreamCoreAPI.plugin();
        if (plugin == null || elementId == null || path == null) {
            return false;
        }
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("@" + elementId + "." + path, value);
        plugin.sendStatePatch(player, values);
        return true;
    }

    /** 打开并绑定真实容器（点击槽位由页面 actions 裁决，vars.slot / vars.container 可用）。 */
    public String openContainer(Player player, String pageId, Inventory inventory, String type, String title) {
        OpenDreamCorePlugin plugin = OpenDreamCoreAPI.plugin();
        if (plugin == null) {
            return null;
        }
        String sessionId = open(player, pageId);
        if (sessionId == null) {
            return null;
        }
        plugin.containerRegistry().bind(sessionId, player, inventory, pageId,
                type == null ? "minecraft:chest" : type, title == null ? "" : title);
        plugin.networkLayer().sendContainerSync(player, plugin.containerRegistry().snapshot(sessionId));
        return sessionId;
    }

    /** 关闭容器会话并关闭页面。 */
    public boolean closeContainer(Player player) {
        OpenDreamCorePlugin plugin = OpenDreamCoreAPI.plugin();
        if (plugin == null) {
            return false;
        }
        var binding = plugin.containerRegistry().ofPlayer(player);
        if (binding != null) {
            plugin.containerRegistry().unbind(binding.sessionId());
        }
        plugin.closePage(player);
        return true;
    }


}
