package com.opendreamcore.client.controller;

import com.opendreamcore.branding.TitleCacheStore;
import com.opendreamcore.branding.TitleConfig;
import com.opendreamcore.client.WindowBranding;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 服务端窗口标题下发服务（C6 自 ClientController 抽出）：
 * window_title 消息处理、覆盖语义挂接（WindowBranding）、按服务器地址的磁盘缓存维护。
 *
 * 缓存文件：&lt;gameDir&gt;/OpenDreamCore/cache/title_cache.json
 * 磁盘缓存：哈希去重 + LRU；进服先预载缓存消除首包前空窗；
 * RESET/断线删除条目并还原本地 branding。
 */
public final class TitlePushService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TitlePushService.class);

    private final TitleCacheStore titleCache = new TitleCacheStore();
    private boolean titleCacheLoaded;

    /** 启动待应用配置：模组加载读出的最近缓存标题，窗口就绪后的第一次 branding tick 消费。 */
    private static volatile com.opendreamcore.branding.TitleConfig startupPending;

    {
        // 构造即读盘：模组加载阶段缓存就绪，不等首次进服
        loadTitleCacheIfNeeded();
        if (startupPending == null) {
            String key = titleCache.latestKey();
            var e = key == null ? null : titleCache.get(key);
            if (e != null && e.config != null && !e.config.sequence().isEmpty()) {
                startupPending = e.config;
            }
        }
    }

    /** 窗口就绪后的首个 branding tick 调用：应用启动标题（仅一次）。 */
    public static void consumeStartupPending() {
        var cfg = startupPending;
        if (cfg != null) {
            startupPending = null;
            if (!WindowBranding.isServerOverride()) {
                WindowBranding.applyServerConfig(cfg);
                LOGGER.info("已从缓存恢复标题（模组加载预读）");
            }
        }
    }

    /** window_title 到达：SET_CONFIG/SET_STATIC 应用覆盖，RESET 解除；磁盘缓存随操作维护。 */
    public void handleWindowTitle(com.opendreamcore.protocol.message.WindowTitlePush push) {
        LOGGER.info("收到窗口标题推送（enabled 推送文本 {} 字）", push == null || push.text() == null ? 0 : push.text().length());
        if (push == null) {
            return;
        }
        String key = currentServerKey();
        switch (push.op()) {
            case RESET -> {
                WindowBranding.resetToLocal();
                if (key != null && titleCache.remove(key)) {
                    saveTitleCache();
                }
            }
            case SET_STATIC -> {
                WindowBranding.applyServerStatic(push.text());
                var cfg = new TitleConfig();
                cfg.text = push.text();
                cacheServerTitle(key, cfg);
            }
            case SET_CONFIG -> {
                var cfg = new TitleConfig();
                cfg.text = push.text();
                cfg.titles = new java.util.ArrayList<>(push.titles());
                cfg.typewriter = push.typewriter();
                cfg.random = push.random();
                cfg.speed = push.speed();
                cfg.interval = push.interval();
                cfg.holdMs = push.holdMs();
                cfg.loop = push.loop();
                WindowBranding.applyServerConfig(cfg);
                cacheServerTitle(key, cfg);
            }
        }
    }

    private void cacheServerTitle(String key, TitleConfig cfg) {
        if (key == null) {
            return;
        }
        loadTitleCacheIfNeeded();
        if (titleCache.put(key, cfg)) {
            saveTitleCache();
        }
    }

    /** 进服早期调用（JOIN 事件）：按服务器地址预载缓存标题——首包到达前即生效，消除空窗。 */
    public void preloadServerTitle() {
        String key = currentServerKey();
        if (key == null || WindowBranding.isServerOverride()) {
            return;
        }
        loadTitleCacheIfNeeded();
        var e = titleCache.get(key);
        if (e != null && e.config != null && !e.config.sequence().isEmpty()) {
            WindowBranding.applyServerConfig(e.config);
            LOGGER.info("已应用服务端标题缓存: {}", key);
        }
    }

    /** 断线/退出服务器：解除服务端覆盖，回退到最近缓存的标题继续展示（不裸奔原版标题）。 */
    public void clearServerTitle() {
        if (WindowBranding.isServerOverride()) {
            WindowBranding.resetToLocal();
        }
        applyLatestCachedTitle();
    }

    /** 当前服务器缓存键（地址小写；单机为 null 不参与缓存）。 */
    private String currentServerKey() {
        try {
            var server = Minecraft.getInstance().getCurrentServer();
            if (server == null || server.ip == null || server.ip.isBlank()) {
                return null;
            }
            return server.ip.toLowerCase(java.util.Locale.ROOT).trim();
        } catch (Throwable t) {
            return null;
        }
    }

    /** 缓存文件混淆加解密（XOR 固定密钥 + Base64），防手改；encrypt=false 时解码。 */
    private static String crypt(String text, boolean encrypt) {
        try {
            if (encrypt) {
                byte[] data = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                byte[] key = "odc-title-v1".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                for (int i = 0; i < data.length; i++) {
                    data[i] ^= key[i % key.length];
                }
                return java.util.Base64.getEncoder().encodeToString(data);
            }
            byte[] data = java.util.Base64.getDecoder().decode(text.trim());
            byte[] key = "odc-title-v1".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            for (int i = 0; i < data.length; i++) {
                data[i] ^= key[i % key.length];
            }
            return new String(data, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            // 兼容旧明文缓存：解不开时按明文返回
            return text;
        }
    }

    /** 模组启动即应用最近一次缓存的标题（无需等进服；服务端推送后按哈希去重更新）。 */
    public void applyLatestCachedTitle() {
        loadTitleCacheIfNeeded();
        String key = titleCache.latestKey();
        if (key == null || WindowBranding.isServerOverride()) {
            return;
        }
        var e = titleCache.get(key);
        if (e != null && e.config != null && !e.config.sequence().isEmpty()) {
            WindowBranding.applyServerConfig(e.config);
            LOGGER.info("已应用最近缓存的标题（{}）", key);
        }
    }

    private java.nio.file.Path titleCachePath() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("OpenDreamCore").resolve("cache").resolve("title_cache.json");
    }

    private void loadTitleCacheIfNeeded() {
        if (titleCacheLoaded) {
            return;
        }
        titleCacheLoaded = true;
        try {
            java.nio.file.Path p = titleCachePath();
            if (!java.nio.file.Files.isRegularFile(p)) {
                return;
            }
            String raw = java.nio.file.Files.readString(p, java.nio.charset.StandardCharsets.UTF_8);
            if (raw.isBlank()) {
                return;
            }
            raw = crypt(raw, false);
            TitleCacheDto dto = new com.google.gson.Gson().fromJson(raw, TitleCacheDto.class);
            if (dto != null && dto.entries != null) {
                titleCache.restore(dto.entries);
            }
        } catch (Exception ignored) {
            // 缓存损坏静默丢弃，等下次服务端下发重建
        }
    }

    private void saveTitleCache() {
        try {
            java.nio.file.Path p = titleCachePath();
            java.nio.file.Files.createDirectories(p.getParent());
            TitleCacheDto dto = new TitleCacheDto();
            dto.entries = titleCache.snapshot();
            java.nio.file.Files.writeString(p,
                    crypt(new com.google.gson.Gson().toJson(dto), true),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // 写盘失败不影响本次会话的内存缓存
        }
    }

    /** 标题缓存文件 DTO（Gson 序列化形态：{entries:{地址:{hash,config}}}）。 */
    private static final class TitleCacheDto {
        java.util.Map<String, TitleCacheStore.Entry> entries;
    }
}
