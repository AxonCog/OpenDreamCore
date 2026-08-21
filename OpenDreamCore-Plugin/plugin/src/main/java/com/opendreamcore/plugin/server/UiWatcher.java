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
                Bukkit.getScheduler().runTask(plugin, this::reload);
            } catch (InterruptedException e) {
                return;
            } catch (Exception e) {
                plugin.getLogger().warning("文件监听异常: " + e);
            }
        }
    }

    /** 主线程：页面+tooltip+type别名 重载，变化页面广播给已握手玩家 */
    private void reload() {
        try {
            plugin.pageManager().load();
            plugin.tooltipManager().load();
            plugin.loadTypeAliases();
            plugin.getLogger().info("文件变化：页面与配置已自动重载");
            plugin.networkLayer().broadcastPages();
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
