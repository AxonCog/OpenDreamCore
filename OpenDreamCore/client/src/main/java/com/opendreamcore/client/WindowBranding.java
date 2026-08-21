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
 * - icon.png     → 窗口图标（任意尺寸，系统缩放）
 * 随 /odc reload 或启动时应用；文件缺失则保持原样。
 */
public final class WindowBranding {

    private WindowBranding() {
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
        if (didTitle || didIcon) {
            applied = true;
        }
    }

    /** 强制重刷（/odc reload 后）：无论单次标记均重试，成功后再次进入单次语义。 */
    public static void reload() {
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
            int[] rgba = image.getPixelsRGBA();
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
