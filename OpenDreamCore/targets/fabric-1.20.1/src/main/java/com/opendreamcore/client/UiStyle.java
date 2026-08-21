package com.opendreamcore.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 样式工具：颜色与资源引用解析。
 * 颜色：#RRGGBB / #RRGGBBAA / solid:r,g,b,a
 * 资源：gui/xxx → opendreamcore:textures/...；minecraft:xxx → 原样；assets/xxx → 本地文件纹理。
 */
public final class UiStyle {

    private static final Map<String, ResourceLocation> TEXTURE_CACHE = new ConcurrentHashMap<>();

    private UiStyle() {
    }

    /** 解析颜色：null/空 → 默认色。返回 ARGB int。 */
    public static int color(Object raw, int fallback) {
        if (raw == null) {
            return fallback;
        }
        String s = String.valueOf(raw).trim();
        try {
            if (s.startsWith("#")) {
                String hex = s.substring(1);
                if (hex.length() == 6) {
                    return 0xFF000000 | Integer.parseInt(hex, 16);
                }
                if (hex.length() == 8) {
                    return (int) Long.parseLong(hex, 16);
                }
                return fallback;
            }
            if (s.startsWith("solid:")) {
                String[] parts = s.substring(6).split(",");
                int r = Integer.parseInt(parts[0].trim());
                int g = Integer.parseInt(parts[1].trim());
                int b = Integer.parseInt(parts[2].trim());
                int a = parts.length > 3 ? Integer.parseInt(parts[3].trim()) : 255;
                return (a << 24) | (r << 16) | (g << 8) | b;
            }
        } catch (RuntimeException ignored) {
            // 解析失败用默认色
        }
        return fallback;
    }

    /**
     * 解析资源引用为可渲染纹理。
     * gui/logo.png → opendreamcore:textures/gui/logo.png（mod 资源包）
     * minecraft:xxx → 原样（原版纹理）
     * assets/xxx.png → 游戏目录下本地文件（动态纹理，缓存）
     * https:// → 预留（云缓存，未下载前返回 null）
     */
    public static ResourceLocation texture(String src) {
        if (src == null || src.isBlank()) {
            return null;
        }
        String s = src.trim();
        if (s.startsWith("https://") || s.startsWith("http://")) {
            return null; // 云同步后才有本地文件，先不渲染
        }
        if (s.startsWith("assets/")) {
            return localTexture(Minecraft.getInstance().gameDirectory.toPath()
                    .resolve("assets").resolve(s.substring("assets/".length())));
        }
        if (s.startsWith("minecraft:")) {
            return ResourceLocation.tryParse(s);
        }
        // 默认 opendreamcore 命名空间，补 textures 前缀（gui/logo.png → textures/gui/logo.png）
        String path = s.startsWith("textures/") ? s : "textures/" + s;
        return ResourceLocation.fromNamespaceAndPath("opendreamcore", path.toLowerCase(Locale.ROOT));
    }

    /** 本地 PNG 文件 → 动态纹理（失败返回 null，不抛错）。 */
    private static ResourceLocation localTexture(Path file) {
        String key = file.toString();
        ResourceLocation cached = TEXTURE_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            NativeImage image = NativeImage.read(Files.newInputStream(file));
            DynamicTexture texture = new DynamicTexture(image);
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("opendreamcore",
                    "local/" + Integer.toHexString(key.hashCode()));
            Minecraft.getInstance().getTextureManager().register(id, texture);
            TEXTURE_CACHE.put(key, id);
            return id;
        } catch (IOException e) {
            return null;
        }
    }
}
