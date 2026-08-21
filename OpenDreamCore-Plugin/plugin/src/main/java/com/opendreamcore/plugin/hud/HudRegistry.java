package com.opendreamcore.plugin.hud;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HUD 三型注册表（P3-15）：
 * - 个人 HUD：显式挂载给指定玩家，进服自动重挂
 * - GHUD（全局常驻）：全体广播 + 新进服玩家自动挂载
 * - HUDStatic（静态广播）：同 GHUD，内容为纯公告（页面不用变量/占位符）
 * 客户端同一时刻只挂一个 HUD；进服重挂优先级：个人 HUD > match:hud 页面 > GHUD > 静态。
 */
public final class HudRegistry {

    private final Map<UUID, String> playerHud = new ConcurrentHashMap<>();
    private final Map<com.opendreamcore.protocol.message.HudSync.Mode, String> globalHud = new ConcurrentHashMap<>();

    public void mountPlayer(Player player, String pageId) {
        playerHud.put(player.getUniqueId(), pageId);
    }

    /** 玩家显式挂载的个人 HUD 页面（无则 null）。 */
    public String playerHudOf(Player player) {
        return playerHud.get(player.getUniqueId());
    }

    public void unmountPlayer(Player player) {
        playerHud.remove(player.getUniqueId());
    }

    public void setGlobal(com.opendreamcore.protocol.message.HudSync.Mode mode, String pageId) {
        globalHud.put(mode, pageId);
    }

    /** 全局 HUD 页面（GHUD/STATIC，无则 null）。 */
    public String globalOf(com.opendreamcore.protocol.message.HudSync.Mode mode) {
        return globalHud.get(mode);
    }

    public void clearGlobal(com.opendreamcore.protocol.message.HudSync.Mode mode) {
        globalHud.remove(mode);
    }

    /** 注册数（个人 + 全局）。 */
    public int size() {
        return playerHud.size() + globalHud.size();
    }
}
