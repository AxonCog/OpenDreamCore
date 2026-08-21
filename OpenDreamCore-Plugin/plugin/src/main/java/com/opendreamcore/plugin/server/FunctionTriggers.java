package com.opendreamcore.plugin.server;

import com.opendreamcore.page.Page;
import com.opendreamcore.plugin.OpenDreamCorePlugin;
import com.opendreamcore.plugin.page.ServerPageManager;
import com.opendreamcore.script.DreamLang;
import com.opendreamcore.script.Scope;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Functions 触发器（P3-19）：所有页面 Functions 表里的服务端触发器。
 * 键名：join 进服 / quit 退出 / chat 聊天（注入 message）/ death 死亡 / respawn 重生 / tick 全局每秒。
 * 脚本作用域：页面变量 + player.*（tick 除外）+ 触发器附加变量。
 */
public final class FunctionTriggers implements Listener {

    private final OpenDreamCorePlugin plugin;
    private final ServerPageManager pages;

    public FunctionTriggers(OpenDreamCorePlugin plugin, ServerPageManager pages) {
        this.plugin = plugin;
        this.pages = pages;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        run("join", event.getPlayer(), null);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        run("quit", event.getPlayer(), null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        // 聊天事件是异步的：切主线程再执行脚本
        String message = event.getMessage();
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            var extra = new java.util.LinkedHashMap<String, Object>();
            extra.put("message", message);
            run("chat", player, extra);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        run("death", event.getEntity(), null);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        run("respawn", event.getPlayer(), null);
    }

    /** 全局 tick：每 20 tick 执行一次所有页面的 tick 触发器（无玩家上下文）。 */
    public void startTick() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> run("tick", null, null), 20L, 20L);
    }

    /** 遍历所有页面，执行匹配的触发器脚本。 */
    private void run(String name, Player player, java.util.Map<String, Object> extra) {
        for (String pageId : pages.ids()) {
            Page page = pages.get(pageId);
            if (page == null || page.functions() == null) {
                continue;
            }
            String script = page.functions().get(name);
            if (script == null || script.isBlank()) {
                continue;
            }
            try {
                Scope scope = new Scope();
                page.variables().forEach(scope::assignVar);
                if (player != null) {
                    scope.assignPlayer("name", player.getName());
                    scope.assignPlayer("uuid", player.getUniqueId().toString());
                    scope.assignPlayer("health", (double) player.getHealth());
                    scope.assignPlayer("level", (double) player.getLevel());
                }
                if (extra != null) {
                    extra.forEach(scope::assignVar);
                }
                DreamLang.execute(script, scope);
            } catch (Exception e) {
                plugin.getLogger().warning("触发器 " + name + " 执行失败 (" + pageId + "): " + e);
            }
        }
    }
}
