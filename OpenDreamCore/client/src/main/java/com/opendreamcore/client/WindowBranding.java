package com.opendreamcore.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 窗口 Branding：OpenDreamCore/branding/ 下的标题与图标覆盖游戏窗口。
 * - title.txt    → 窗口标题（纯文本，首行有效，自动去空白）
 * - title.json   → 打字机/轮播标题（优先于 txt；TypewriterSequencer 时序，tick() 驱动）
 * - icon.png     → 窗口图标（任意尺寸，系统缩放）
 * 随 /odc reload 或启动时应用；文件缺失则保持原样。
 */
public final class WindowBranding {

    private WindowBranding() {
    }

    /** 打字机时序器（title.json 存在时非空）。 */
    private static volatile com.opendreamcore.branding.TypewriterSequencer sequencer;
    /** 上次写入的标题（去重，避免每帧重复调用系统 API）。 */
    private static volatile String lastAppliedTitle;
    /** 服务端覆盖中的静态标题（SET_STATIC；时序器模式由 sequencer 驱动，此字段为空）。 */
    private static volatile String serverTitle;
    /** 服务端覆盖中（DreamCore serverTitleOverride 语义）：本地 title.txt/title.json 序列静默。 */
    private static volatile boolean serverOverride;

    /**
     * 服务端下发完整标题配置（window_title SET_CONFIG）：
     * 覆盖本地 branding 并立即驱动时序器；缓存由调用方负责。
     */
    public static void applyServerConfig(com.opendreamcore.branding.TitleConfig cfg) {
        if (cfg == null || cfg.sequence().isEmpty()) {
            return;
        }
        sequencer = new com.opendreamcore.branding.TypewriterSequencer(cfg);
        lastAppliedTitle = null;
        serverTitle = null;
        serverOverride = true;
        tick();
    }

    /** 服务端单文本直设（window_title SET_STATIC）。 */
    public static void applyServerStatic(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        sequencer = null;
        serverTitle = text;
        serverOverride = true;
        setWindowTitleIfChanged(text);
    }

    /** 解除服务端覆盖（window_title RESET / 断线），还原本地 branding 序列。 */
    public static void resetToLocal() {
        serverOverride = false;
        serverTitle = null;
        reload();
    }

    /** 当前是否处于服务端覆盖（供缓存层判断与测试）。 */
    public static boolean isServerOverride() {
        return serverOverride;
    }

    private static void setWindowTitleIfChanged(String text) {
        if (!text.equals(lastAppliedTitle)) {
            try {
                Minecraft.getInstance().getWindow().setTitle(text);
                lastAppliedTitle = text;
            } catch (Throwable ignored) {
                // 窗口未就绪/无 GL 上下文：保持原值，下帧 tick 重试
            }
        }
    }

    public static Path brandingDir() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("OpenDreamCore").resolve("branding");
    }

    /** 应用 branding（无文件时静默跳过；仅主线程 GL 上下文有效时执行，仅执行一次）。 */
    private static volatile boolean applied;

    public static void apply() {
        if (applied) {
            return;
        }
        var mc = Minecraft.getInstance();
        if (mc == null || mc.gameDirectory == null || mc.getWindow() == null) {
            return;
        }
        // 仅渲染线程 GL 上下文有效时执行图标写入（避免非主线程/早期启动崩溃）
        if (!isMainThread() || !glContextReady(mc)) {
            return;
        }
        Path dir = brandingDir();
        if (!Files.isDirectory(dir)) {
            return;
        }
        boolean didTitle = applyTitle(dir);
        boolean didIcon = applyIcon(dir);
        HudLogo.load(); // logo_hud.png + logo.json（缺失静默）
        if (didTitle || didIcon) {
            applied = true;
        }
    }

    /** 强制重刷（/odc reload 后）：无论单次标记均重试，成功后再次进入单次语义。 */
    public static void reload() {
        sequencer = null; // title.json 重新加载（打字机从头开始）
        lastAppliedTitle = null;
        var mc = Minecraft.getInstance();
        if (mc == null || mc.gameDirectory == null || mc.getWindow() == null) {
            return;
        }
        if (!isMainThread() || !glContextReady(mc)) {
            return;
        }
        Path dir = brandingDir();
        if (!Files.isDirectory(dir)) {
            return;
        }
        boolean didTitle = applyTitle(dir);
        boolean didIcon = applyIcon(dir);
        HudLogo.load(); // logo_hud.png + logo.json（缺失静默）
        if (didTitle || didIcon) {
            applied = true;
        }
    }

    private static boolean isMainThread() {
        try {
            return Minecraft.getInstance().isSameThread();
        } catch (Throwable ignored) {
            return true;
        }
    }

    private static boolean glContextReady(Minecraft mc) {
        try {
            long handle = mc.getWindow().getWindow();
            return handle != 0 && org.lwjgl.glfw.GLFW.glfwGetCurrentContext() != 0;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean applyTitle(Path dir) {
        // 优先 title.json（打字机/轮播）；缺失或损坏回退 title.txt 静态
        Path json = dir.resolve("title.json");
        if (Files.isRegularFile(json)) {
            com.opendreamcore.branding.TitleConfig cfg = com.opendreamcore.branding.TitleConfig.load(json);
            if (cfg != null && !cfg.sequence().isEmpty()) {
                sequencer = new com.opendreamcore.branding.TypewriterSequencer(cfg);
                lastAppliedTitle = null;
                tick();
                return true;
            }
        }
        Path title = dir.resolve("title.txt");
        if (!Files.isRegularFile(title)) {
            return false;
        }
        try {
            String text = Files.readString(title, StandardCharsets.UTF_8).trim();
            if (!text.isEmpty()) {
                Minecraft.getInstance().getWindow().setTitle(text);
                return true;
            }
        } catch (IOException ignored) {
            // 读取失败保持原标题
        }
        return false;
    }

    /** 打字机帧推进：标题变化才写系统 API。由各平台客户端 tick 事件驱动。 */
    public static void tick() {
        com.opendreamcore.client.controller.TitlePushService.consumeStartupPending();
        try {
            ClientController.get().ensureManagedPacks(null);
        } catch (Throwable ignored) {
        }
        if (serverOverride) {
            // 服务端接管期间本地序列静默；
            // 无序列 = 静态标题，上次写入失败（窗口未就绪）此处自愈重试；
            // 有序列 = 服务端下发的打字机配置，必须在这里推进，否则永远停在原标题
            var sseq = sequencer;
            // 每 tick 无条件重写：原版/其他 mod 会随时覆盖窗口标题，去重会导致覆盖后不再恢复
            lastAppliedTitle = null;
            if (sseq == null) {
                setWindowTitleIfChanged(serverTitle);
            } else {
                try {
                    setWindowTitleIfChanged(sseq.tick(System.currentTimeMillis()));
                } catch (Throwable ignored) {
                }
            }
            return;
        }
        var seq = sequencer;
        if (seq == null) {
            return;
        }
        try {
            String s = seq.tick(System.currentTimeMillis());
            setWindowTitleIfChanged(s);
            if (seq.isFinished(System.currentTimeMillis())) {
                sequencer = null; // 定格完毕，停止 tick 开销
            }
        } catch (Throwable ignored) {
            sequencer = null;
        }
    }

    private static boolean applyIcon(Path dir) {
        Path icon = dir.resolve("icon.png");
        if (!Files.isRegularFile(icon)) {
            return false;
        }
        // 尺寸限制：避免超大图标（如 4K）造成 Direct 内存峰值与 OS 拒绝
        try {
            long len = Files.size(icon);
            if (len > 4 * 1024 * 1024) {
                return false;
            }
        } catch (IOException ignored) {
        }
        try (NativeImage image = NativeImage.read(Files.newInputStream(icon))) {
            int w = image.getWidth();
            int h = image.getHeight();
            if (w <= 0 || h <= 0 || w > 1024 || h > 1024) {
                return false;
            }
            // 1.21 起 Window.setIcon 走资源包签名，直接 GLFW 设置（跨版本稳定）
            long handle = Minecraft.getInstance().getWindow().getWindow();
            int[] rgba = CompatRender.nativeGetPixels(image);
            java.nio.ByteBuffer pixels = java.nio.ByteBuffer.allocateDirect(rgba.length * 4)
                    .order(java.nio.ByteOrder.nativeOrder());
            for (int c : rgba) {
                pixels.put((byte) ((c >>> 24) & 0xFF)); // R
                pixels.put((byte) ((c >>> 16) & 0xFF)); // G
                pixels.put((byte) ((c >>> 8) & 0xFF));  // B
                pixels.put((byte) (c & 0xFF));          // A
            }
            pixels.flip();
            try (org.lwjgl.glfw.GLFWImage.Buffer icons = org.lwjgl.glfw.GLFWImage.malloc(1);
                 org.lwjgl.glfw.GLFWImage iconImage = org.lwjgl.glfw.GLFWImage.malloc()) {
                iconImage.set(w, h, pixels);
                icons.put(0, iconImage);
                org.lwjgl.glfw.GLFW.glfwSetWindowIcon(handle, icons);
                return true;
            } catch (Throwable t) {
                // OS 拒绝图标（过旧/过新 GLFW）保持原图标
                return false;
            }
        } catch (Throwable ignored) {
            // 图标损坏/解码失败保持原图标
            return false;
        }
    }
}
