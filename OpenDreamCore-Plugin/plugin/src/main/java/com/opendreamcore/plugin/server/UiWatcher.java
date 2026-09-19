package com.opendreamcore.plugin.server;

import com.opendreamcore.plugin.OpenDreamCorePlugin;
import org.bukkit.Bukkit;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 文件监听热重载。监听 UI/resources/tooltip 目录，
 * 文件增改删后去抖再主线程 reload。
 * 作者：梦幻 QQ:2496599413
 */
public final class UiWatcher {

    private final OpenDreamCorePlugin plugin;
    private final long debounceMs;
    private final Set<Path> dirs = new HashSet<>();
    private final AtomicBoolean running = new AtomicBoolean();
    private WatchService service;
    private Thread thread;
    private long lastFire;

    public UiWatcher(OpenDreamCorePlugin plugin, long debounceMs) {
        this.plugin = plugin;
        this.debounceMs = Math.max(50, debounceMs);
    }

    public void watch(Path dir) {
        if (dir != null) {
            dirs.add(dir);
        }
    }

    public void start() {
        if (running.getAndSet(true)) {
            return;
        }
        try {
            service = FileSystems.getDefault().newWatchService();
            for (Path dir : dirs) {
                if (java.nio.file.Files.isDirectory(dir)) {
                    dir.register(service, StandardWatchEventKinds.ENTRY_CREATE,
                            StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
                }
            }
        } catch (IOException e) {
            plugin.getLogger().warning("文件监听启动失败: " + e);
            running.set(false);
            return;
        }
        thread = new Thread(this::loop, "odc-file-watcher");
        thread.setDaemon(true);
        thread.start();
        plugin.getLogger().info("文件监听已启动（自动热重载，去抖 " + debounceMs + "ms）");
    }

    private void loop() {
        while (running.get()) {
            try {
                WatchKey key = service.take();
                key.pollEvents(); // 有变化就行，不细分事件
                key.reset();
                long now = System.currentTimeMillis();
                if (now - lastFire < debounceMs) {
                    continue;
                }
                lastFire = now;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    reload();
                    plugin.getLogger().info("文件变化：页面与配置已自动重载");
                });
            } catch (InterruptedException e) {
                return;
            } catch (Exception e) {
                plugin.getLogger().warning("文件监听异常: " + e);
            }
        }
    }

    /**
     * 主线程：全量重载（页面+tooltip+type别名+视觉规则+主题），
     * 变化广播给已握手玩家。/odc reload 与文件监听共用，语义完全一致。
     */
    public void reload() {
        try {
            // config.yml 也在监听目录里，得一并重读，不然改了 client-title
            // 这类配置项日志历打“已重载”实际用的还是启动时那份
            plugin.reloadConfig();
            plugin.pageManager().load();
            plugin.tooltipManager().load();
            plugin.loadTypeAliases();
            int v = plugin.visualRules().reload();
            // 资源云重扫：资源文件夹里的文件变了 → 在线玩家全部重新对账
            plugin.resourcePipeline().configure();
            plugin.resourcePipeline().reload();
            // 扩展随视觉规则重载：脚本命名空间与方言适配器清场重登
            plugin.extensions().loadAll();
            int t = com.opendreamcore.api.ThemeAPI.get()
                    .loadFromDir(plugin.getDataFolder().toPath().resolve("themes"));
            if (v + t > 0) {
                plugin.getLogger().info("热重载 视觉" + v + " 主题" + t);
            }
            plugin.networkLayer().broadcastPages();
            for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
                if (plugin.networkLayer().isReady(p)) {
                    plugin.networkLayer().send(p, com.opendreamcore.protocol.Protocol.VISUAL_RULES, plugin.visualRules().buildSync());
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("自动重载失败: " + e);
        }
    }

    public void stop() {
        running.set(false);
        if (thread != null) {
            thread.interrupt();
        }
        if (service != null) {
            try {
                service.close();
            } catch (IOException ignored) {
            }
        }
    }
}
