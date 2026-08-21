package com.opendreamcore.plugin.server;

import com.opendreamcore.page.Page;
import com.opendreamcore.plugin.OpenDreamCorePlugin;
import com.opendreamcore.plugin.container.ContainerRegistry;
import com.opendreamcore.plugin.network.ProtocolListener;
import com.opendreamcore.plugin.page.ServerPageManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * 服务端 match 触发：进服/开容器时按 match 下发页面（多人模式的服务端配置源）。
 * 容器页面额外做会话绑定：真实容器 ↔ 自定义 UI（内容快照下发 + 外部变更重同步）。
 */
public final class MatchListener implements Listener {

    private final OpenDreamCorePlugin plugin;
    private final ServerPageManager pages;
    private final ProtocolListener network;

    public MatchListener(OpenDreamCorePlugin plugin, ServerPageManager pages, ProtocolListener network) {
        this.plugin = plugin;
        this.pages = pages;
        this.network = network;
    }

    /** 进服：扫描所有页面，按 display 模式独立下发（HUD/World/Screen 互不互斥）。
     * HUD 优先级：显式个人 HUD > match:hud 页面 > GHUD > STATIC。
     * World 和 Screen 页面：各自 match 命中即下发（可同时存在多个）。 */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        var hudRegistry = plugin.hudRegistry();

        // ---- HUD 下发 ----
        boolean hudSent = false;
        String saved = hudRegistry.playerHudOf(player);
        if (saved != null) {
            sendHudLater(player, saved, com.opendreamcore.protocol.message.HudSync.Mode.HUD);
            hudSent = true;
        }
        if (!hudSent) {
            Page hudPage = pages.match("hud", null, player);
            if (hudPage != null && hudPage.displayMode() == com.opendreamcore.page.DisplayMode.HUD) {
                sendHudLater(player, hudPage.id() == null ? "page" : hudPage.id(),
                        com.opendreamcore.protocol.message.HudSync.Mode.HUD);
                hudSent = true;
            }
        }
        if (!hudSent) {
            String ghud = hudRegistry.globalOf(com.opendreamcore.protocol.message.HudSync.Mode.GHUD);
            if (ghud != null) {
                sendHudLater(player, ghud, com.opendreamcore.protocol.message.HudSync.Mode.GHUD);
                hudSent = true;
            }
        }
        if (!hudSent) {
            String stat = hudRegistry.globalOf(com.opendreamcore.protocol.message.HudSync.Mode.STATIC);
            if (stat != null) {
                sendHudLater(player, stat, com.opendreamcore.protocol.message.HudSync.Mode.STATIC);
            }
        }

        // ---- World 面板下发（所有 match:world 且 display:world 的页面，可多个） ----
        for (Page page : pages.allPages()) {
            if (page.match() == null) {
                continue;
            }
            if (page.displayMode() == com.opendreamcore.page.DisplayMode.WORLD
                    && matchesTarget(page.match().target(), "world")) {
                sendLater(player, page);
            }
        }

        // ---- Screen 面板下发（match:screen 且 display:screen 的页面） ----
        for (Page page : pages.allPages()) {
            if (page.match() == null) {
                continue;
            }
            if (page.displayMode() == com.opendreamcore.page.DisplayMode.SCREEN
                    && matchesTarget(page.match().target(), "screen")) {
                sendLater(player, page);
            }
        }
    }

    /** 检查 match target 是否匹配指定模式（不区分大小写）。 */
    private static boolean matchesTarget(String matchTarget, String expected) {
        return matchTarget != null && matchTarget.equalsIgnoreCase(expected);
    }

    /** 稍后挂载 HUD（等玩家数据就绪）。 */
    private void sendHudLater(Player player, String pageId,
                              com.opendreamcore.protocol.message.HudSync.Mode mode) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                String yaml = pages.compiledYaml(pageId, player);
                if (yaml != null) {
                    network.openHud(player, pageId, yaml, mode);
                }
            }
        }, 10L);
    }

    /** 退出：清理该玩家的容器会话、个人 HUD 与版本记录。 */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.containerRegistry().unbindAll(event.getPlayer());
        plugin.hudRegistry().unmountPlayer(event.getPlayer());
        network.removeSession(event.getPlayer());
    }

    /** 开容器：按容器类型/标题匹配并替换界面（自定义 UI + 内容快照）。 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        // 其他玩家打开已绑定的容器：原版界面改动 → 重同步给看自定义 UI 的玩家
        for (ContainerRegistry.Binding binding : plugin.containerRegistry().ofInventory(event.getInventory())) {
            if (!binding.player().equals(player)) {
                network.sendContainerSync(binding.player(), plugin.containerRegistry().snapshot(binding));
            }
        }
        String target = switch (event.getInventory().getType()) {
            case PLAYER -> "inventory";
            case CHEST, BARREL, SHULKER_BOX -> "minecraft:chest";
            case FURNACE -> "minecraft:furnace";
            case CRAFTING -> "minecraft:crafting_table";
            case ANVIL -> "minecraft:anvil";
            case ENCHANTING -> "minecraft:enchanting_table";
            case BREWING -> "minecraft:brewing_stand";
            case HOPPER -> "minecraft:hopper";
            case DISPENSER, DROPPER -> "minecraft:dispenser";
            default -> null;
        };
        if (target == null) {
            return;
        }
        String title = event.getView().getTitle();
        Page page = pages.match(target, title, player);
        if (page == null) {
            page = pages.match(title, title, player);
        }
        if (page != null) {
            event.setCancelled(true); // 阻止原版容器打开
            String id = page.id() == null ? "page" : page.id();
            String yaml = pages.compiledYaml(id, player);
            if (yaml == null) {
                return;
            }
            String sessionId = network.openPage(player, id, yaml);
            // 绑定真实容器 + 下发内容快照
            plugin.containerRegistry().bind(sessionId, player, event.getInventory(), id, target, title);
            network.sendContainerSync(player, plugin.containerRegistry().snapshot(sessionId));
        }
    }

    /** 漏斗等外部搬运动作：容器内容变了 → 重同步给看自定义 UI 的玩家。 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        for (ContainerRegistry.Binding binding : plugin.containerRegistry().ofInventory(event.getSource())) {
            network.sendContainerSync(binding.player(), plugin.containerRegistry().snapshot(binding));
        }
        for (ContainerRegistry.Binding binding : plugin.containerRegistry().ofInventory(event.getDestination())) {
            network.sendContainerSync(binding.player(), plugin.containerRegistry().snapshot(binding));
        }
    }

    /** 稍后下发（等玩家数据就绪；扁平语法按玩家编译）。 */
    private void sendLater(Player player, Page page) {
        String id = page.id() == null ? "page" : page.id();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                String yaml = pages.compiledYaml(id, player);
                if (yaml != null) {
                    network.openPage(player, id, yaml);
                }
            }
        }, 10L);
    }
}
