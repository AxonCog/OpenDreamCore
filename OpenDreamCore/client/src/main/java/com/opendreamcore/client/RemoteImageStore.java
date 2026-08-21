package com.opendreamcore.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.opendreamcore.remote.RemoteMedia;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 远程图片加载：http(s) 图片 URL → 动态纹理。
 * 下载走 {@link RemoteMedia}（SSRF 防护 + 16MB 上限 + 磁盘缓存），
 * 下载完成切回渲染线程注册纹理，之后 UiStyle.texture 直接命中。
 * 未就绪时返回 null（页面先渲染占位，纹理就绪自动出现）。
 */
public final class RemoteImageStore {

    /** URL → 已注册纹理。 */
    private static final Map<String, ResourceLocation> TEXTURES = new ConcurrentHashMap<>();
    /** 加载中的 URL（避免重复下载）。 */
    private static final Set<String> LOADING = ConcurrentHashMap.newKeySet();

    private RemoteImageStore() {
    }

    public static Path cacheDir() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("OpenDreamCore").resolve("cache").resolve("http-cache");
    }

    /** 取远程图片纹理；未就绪/不安全返回 null（调用方按缺图处理）。 */
    public static ResourceLocation get(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        ResourceLocation ready = TEXTURES.get(url);
        if (ready != null) {
            return ready;
        }
        if (!RemoteMedia.isSafeUrl(url)) {
            return null;
        }
        if (!LOADING.add(url)) {
            return null; // 已在加载中
        }
        RemoteMedia.get(url, cacheDir()).thenAccept(path -> {
            Minecraft.getInstance().execute(() -> loadTexture(url, path));
        }).exceptionally(t -> {
            LOADING.remove(url);
            return null;
        });
        return null;
    }

    /** 渲染线程：缓存文件 → NativeImage → 动态纹理。 */
    private static void loadTexture(String url, Path file) {
        try (NativeImage image = NativeImage.read(Files.newInputStream(file))) {
            DynamicTexture texture = new DynamicTexture(image);
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("opendreamcore",
                    "remote/" + Integer.toHexString(url.hashCode()));
            Minecraft.getInstance().getTextureManager().register(id, texture);
            TEXTURES.put(url, id);
        } catch (IOException ignored) {
            // 文件损坏/解码失败：不注册，下次请求会重试
        } finally {
            LOADING.remove(url);
        }
    }

    /** 清空全部（重载/断线重连时释放纹理）。 */
    public static void clear() {
        var manager = Minecraft.getInstance().getTextureManager();
        TEXTURES.values().forEach(manager::release);
        TEXTURES.clear();
        LOADING.clear();
    }
}
