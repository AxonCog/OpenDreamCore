package com.opendreamcore.client;

import com.mojang.logging.LogUtils;
import com.opendreamcore.ui.TtfFont;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自定义字体注册表：扫描 OpenDreamCore/fonts/（本地）与 OpenDreamCore/cache/fonts/（云端加密缓存）
 * 目录下的 *.ttf，按文件名（去扩展名）注册；元素 font: "字体名" 引用。
 * 云端字体随云资源清单自动同步（resources/fonts/xxx.ttf → cache/fonts/xxx.ttf，加密态缓存）。
 */
public final class CustomFonts {

    public static final Logger LOGGER = LogUtils.getLogger();

    private static final Map<String, TtfRenderer> RENDERERS = new ConcurrentHashMap<>();
    private static boolean loaded;

    private CustomFonts() {
    }

    /** 重新扫描字体目录（客户端初始化 / 本地页面重载时调用）。 */
    public static void loadAll() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gameDirectory == null) {
            return;
        }
        RENDERERS.clear();
        Path game = mc.gameDirectory.toPath();
        scanDir(game.resolve("OpenDreamCore").resolve("fonts"));
        scanCloudFonts();
        loaded = true;
        if (!RENDERERS.isEmpty()) {
            LOGGER.info("自定义字体已加载 {} 个: {}", RENDERERS.size(), names());
        }
    }

    /** 扫描云端字体（加密缓存 → 内存解密 → AWT Font）。 */
    private static void scanCloudFonts() {
        CloudSyncClient cloud = ClientController.get().cloud();
        if (cloud == null) {
            return;
        }
        Path dir = cloud.cacheDir().resolve("fonts");
        if (!Files.isDirectory(dir)) {
            return;
        }
        List<Path> files;
        try (var stream = Files.list(dir)) {
            files = stream.filter(p -> p.getFileName().toString().toLowerCase(java.util.Locale.ROOT)
                    .endsWith(".ttf")).toList();
        } catch (IOException e) {
            return;
        }
        for (Path file : files) {
            String name = file.getFileName().toString().replaceFirst("(?i)\\.ttf$", "");
            String relPath = "fonts/" + file.getFileName().toString();
            try {
                byte[] data = cloud.loadCached(relPath);
                if (data == null || data.length == 0) {
                    LOGGER.warn("字体解密失败（空数据）: {}", name);
                    continue;
                }
                java.awt.Font base = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT,
                        new java.io.ByteArrayInputStream(data));
                TtfFont font = new TtfFont(name, base);
                RENDERERS.put(name, new TtfRenderer(font));
                LOGGER.info("字体加载(云) {} <- {}", name, relPath);
            } catch (Exception e) {
                LOGGER.warn("字体加载失败(云) {}: {}", name, e.toString());
            }
        }
    }

    private static void scanDir(Path dir) {
        if (!Files.isDirectory(dir)) {
            return;
        }
        List<Path> files;
        try (var stream = Files.list(dir)) {
            files = stream.filter(p -> p.getFileName().toString().toLowerCase(java.util.Locale.ROOT)
                    .endsWith(".ttf")).toList();
        } catch (IOException e) {
            return;
        }
        for (Path file : files) {
            String name = file.getFileName().toString().replaceFirst("(?i)\\.ttf$", "");
            try {
                TtfFont font = new TtfFont(name, file.toFile());
                RENDERERS.put(name, new TtfRenderer(font));
                LOGGER.info("字体加载 {} <- {}", name, file);
            } catch (Exception e) {
                LOGGER.warn("字体加载失败 {}: {}", file.getFileName(), e.toString());
            }
        }
    }

    /** 按名称取渲染器（未加载/不存在返回 null，调用方回退默认字体）。 */
    public static TtfRenderer get(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        if (!loaded) {
            loadAll();
        }
        return RENDERERS.get(name);
    }

    /** 已加载字体名（排序）。 */
    public static List<String> names() {
        List<String> out = new ArrayList<>(RENDERERS.keySet());
        out.sort(String::compareTo);
        return out;
    }
}
