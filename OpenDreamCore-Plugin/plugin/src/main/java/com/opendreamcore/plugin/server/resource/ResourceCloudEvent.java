package com.opendreamcore.plugin.server.resource;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * 资源云对账完成事件：某位玩家的本地缓存和服务端资源云对完账后响一声。
 *
 * 附属插件想跟着资源云动手脚就订阅它：
 *   Bukkit.getPluginManager().registerEvents(new Listener() {
 *       @EventHandler public void on(ResourceCloudEvent e) { … e.getPlayer()/getPushed()/getCleared() … }
 *   }, 自己的插件实例)
 *
 * 常见玩法顺手列几条：
 *   - 玩家刚进服资源还没到手时等他 push 数归零，再放他进大厅；
 *   - push 数很大说明玩家在换网络或者第一次进，可以顺手塞个 toast；
 *   - 想给玩家下发"显式指定"的资源，用 ServerResourcePipeline.putResource 注入
 *     之后自己监听本事件确认对方拿到了。
 */
public final class ResourceCloudEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final int pushed;
    private final int cleared;

    public ResourceCloudEvent(Player player, int pushed, int cleared) {
        super(false); // 同步事件：主线程 callEvent 合法，异步线程会被 Bukkit 拦下
        this.player = player;
        this.pushed = pushed;
        this.cleared = cleared;
    }

    /** 对账完成的那位玩家。 */
    public Player getPlayer() {
        return player;
    }

    /** 本次补发给他的文件数。 */
    public int getPushed() {
        return pushed;
    }

    /** 本次让他删掉的过期缓存数。 */
    public int getCleared() {
        return cleared;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}