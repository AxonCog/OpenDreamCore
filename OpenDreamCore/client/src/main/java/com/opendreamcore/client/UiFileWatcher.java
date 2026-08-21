package com.opendreamcore.client;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.Map;

/**
 * 客户端文件监听（P3-17）：监听 OpenDreamCore/UI（递归子目录）与 fonts 目录，
 * 文件增改删去抖后调度渲染线程重载本地页面（含字体重扫），无需 /odc reload。
 *
 * 支持子目录监听：UI/hud/help.yaml 修改也会触发热重载。
 * 新建子目录时自动注册监听。
 */
public final class UiFileWatcher {

    public static final Logger LOGGER = LogUtils.getLogger();

    private static final long DEBOUNCE_MS = 400;

    private WatchService service;
    private Thread thread;
    private final Path uiDir;
    /** WatchKey → 目录路径（用于解析子目录文件事件的完整路径）。 */
    private final Map<WatchKey, Path> watchedDirs = new HashMap<>();

    public UiFileWatcher() {
        Minecraft mc = Minecraft.getInstance();
        Path game = mc.gameDirectory.toPath();
        this.uiDir = game.resolve("OpenDreamCore").resolve("UI");
        Path fontsDir = game.resolve("OpenDreamCore").resolve("fonts");
        try {
            Files.createDirectories(uiDir);
            Files.createDirectories(fontsDir);
        } catch (IOException ignored) {
        }
        try {
            service = FileSystems.getDefault().newWatchService();
            registerRecursive(uiDir);
            if (Files.isDirectory(fontsDir)) {
                registerSingle(fontsDir);
            }
        } catch (IOException e) {
            LOGGER.warn("本地文件监听启动失败: {}", e.toString());
            service = null;
        }
    }

    /** 递归注册目录及所有子目录到 WatchService。 */
    private void registerRecursive(Path dir) throws IOException {
        registerSingle(dir);
        try (var stream = Files.walk(dir)) {
            stream.filter(Files::isDirectory)
                    .filter(p -> !p.equals(dir))
                    .forEach(p -> {
                        try {
                            registerSingle(p);
                        } catch (IOException e) {
                            LOGGER.debug("子目录注册失败 {}: {}", p, e.toString());
                        }
                    });
        } catch (IOException ignored) {
        }
    }

    private void registerSingle(Path dir) throws IOException {
        WatchKey key = dir.register(service,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE);
        watchedDirs.put(key, dir);
    }

    public void start() {
        if (service == null || thread != null) {
            return;
        }
        thread = new Thread(this::loop, "odc-ui-watcher");
        thread.setDaemon(true);
        thread.start();
        LOGGER.info("本地文件监听已启动（UI/fonts 自动热重载，递归子目录）");
    }

    private void loop() {
        long lastFire = 0;
        while (true) {
            try {
                WatchKey key = service.take();
                if (!key.isValid()) {
                    try {
                        key.cancel();
                    } catch (Throwable ignored) {
                    }
                    return;
                }
                Path watchedDir = watchedDirs.get(key);
                var events = key.pollEvents();
                boolean valid = key.reset();
                if (!valid) {
                    watchedDirs.remove(key);
                    try {
                        if (watchedDirs.isEmpty()) {
                            service.close();
                        }
                    } catch (Throwable ignored) {
                    }
                    if (watchedDirs.isEmpty()) return;
                    continue;
                }
                boolean relevant = false;
                boolean newDirCreated = false;
                for (var ev : events) {
                    Object ctx = ev.context();
                    String name = ctx == null ? "" : String.valueOf(ctx);
                    // 新建子目录：需要注册监听
                    if (watchedDir != null && ev.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                        Path created = watchedDir.resolve(name);
                        if (Files.isDirectory(created)) {
                            try {
                                registerSingle(created);
                                newDirCreated = true;
                            } catch (IOException ignored) {
                            }
                        }
                    }
                    // 仅关注页面与资源：.yaml/.yml/.json/.png/.jpg/.jpeg/.webp/.gif/.mp4/.webm/.mov/.mkv/.ttf/.otf
                    String low = name.toLowerCase(java.util.Locale.ROOT);
                    if (low.endsWith(".yaml") || low.endsWith(".yml") || low.endsWith(".json")
                            || low.endsWith(".png") || low.endsWith(".jpg") || low.endsWith(".jpeg")
                            || low.endsWith(".webp") || low.endsWith(".gif") || low.endsWith(".mp4")
                            || low.endsWith(".webm") || low.endsWith(".mov") || low.endsWith(".mkv")
                            || low.endsWith(".ttf") || low.endsWith(".otf")) {
                        relevant = true;
                        break;
                    }
                }
                if (!relevant && !newDirCreated) {
                    continue;
                }
                long now = System.currentTimeMillis();
                if (now - lastFire < DEBOUNCE_MS) {
                    continue;
                }
                lastFire = now;
                // 渲染线程重载本地页面（load 内部同时重扫字体）
                Minecraft.getInstance().execute(() -> ClientController.get().localPages().load(uiDir));
            } catch (InterruptedException e) {
                return;
            } catch (java.nio.file.ClosedWatchServiceException e) {
                return;
            } catch (Exception e) {
                LOGGER.warn("本地文件监听异常: {}", e.toString());
            }
        }
    }

    public void stop() {
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
        if (service != null) {
            try {
                service.close();
            } catch (IOException ignored) {
            }
        }
        watchedDirs.clear();
    }
}
