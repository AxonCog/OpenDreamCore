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

    /** 本地 PNG 动态纹理缓存：绝对路径 → 纹理 id。文件变了自动重载（mtime 对比）。 */
    private static final Map<String, ResourceLocation> TEXTURE_CACHE = new ConcurrentHashMap<>();
    /** 文件上次加载时的 mtime，变化则重读文件重新注册。 */
    private static final Map<String, Long> FILE_MTIME = new ConcurrentHashMap<>();

    private UiStyle() {
    }

    /** 解析颜色：null/空 → 默认色。返回 ARGB int。支持 {color.xxx} 占位符。 */
    public static int color(Object raw, int fallback) {
        if (raw == null) {
            return fallback;
        }
        String s = String.valueOf(raw).trim();
        try {
            // 占位符：{color.red} / {color.primary} 等
            if (s.startsWith("{") && s.endsWith("}")) {
                int dot = s.indexOf('.');
                if (dot > 1) {
                    Object resolved = com.opendreamcore.script.PlaceholderRegistry.resolveOne(
                            s.substring(1, dot), s.substring(dot + 1, s.length() - 1));
                    if (resolved != null) {
                        return color(resolved, fallback);
                    }
                }
            }
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
     * https:// 或 http:// → 远程图片（RemoteImageStore 下载 + SSRF 防护 + 磁盘缓存；未就绪返回 null）
     */
    public static ResourceLocation texture(String src) {
        if (src == null || src.isBlank()) {
            return null;
        }
        String s = src.trim();
        if (s.startsWith("https://") || s.startsWith("http://")) {
            return RemoteImageStore.get(s);
        }
        if (s.startsWith("assets/")) {
            return localTexture(Minecraft.getInstance().gameDirectory.toPath()
                    .resolve("assets").resolve(s.substring("assets/".length())));
        }
        if (s.startsWith("minecraft:")) {
            return ResourceLocation.tryParse(s);
        }
        // 托管目录优先：玩家丢进 resourcepacks/OpenDreamCore 的贴图直接生效，
        // 不用打 jar、不用开资源包界面，改完即热载
        ResourceLocation managed = managedTexture(s);
        if (managed != null) {
            return managed;
        }
        // 资源云贴图兜底：服务端 OpenDreamCoreResource 下发、客户端缓存注册在
        // LooseResourceLoader——image/全息图写云里的路径或短文件名都能吃到
        ResourceLocation cloud = com.opendreamcore.client.resources.LooseResourceLoader.lookup(s);
        if (cloud != null) {
            return cloud;
        }
        // 默认 opendreamcore 命名空间，补 textures 前缀（gui/logo.png → textures/gui/logo.png）
        String path = s.startsWith("textures/") ? s : "textures/" + s;
        return CompatRender.rl("opendreamcore", path.toLowerCase(Locale.ROOT));
    }

    /** 托管目录：resourcepacks/OpenDreamCore —— 玩家把贴图直接丢进来就生效（跟老朋友 DreamEngine 一个手感）。 */
    public static Path managedRoot() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("resourcepacks").resolve("OpenDreamCore");
    }

    /**
     * 托管目录直读：src 依次按散文件、原版包布局查找。
     * gui/hp.png → 根目录/gui/hp.png → 根目录/textures/gui/hp.png → 根目录/assets/opendreamcore/textures/gui/hp.png
     * 都没命中返回 null，走后续 mod 内置资源链。
     */
    private static ResourceLocation managedTexture(String src) {
        try {
            Path root = managedRoot().toAbsolutePath().normalize();
            String normalized = src.replace('\\', '/');
            while (normalized.startsWith("/")) {
                normalized = normalized.substring(1);
            }
            if (normalized.isEmpty()) {
                return null;
            }
            Path direct = root.resolve(normalized).normalize();
            if (direct.startsWith(root) && Files.isRegularFile(direct)) {
                return localTexture(direct);
            }
            Path standard = root.resolve("assets").resolve("opendreamcore")
                    .resolve("textures").resolve(normalized).normalize();
            if (standard.startsWith(root) && Files.isRegularFile(standard)) {
                return localTexture(standard);
            }
        } catch (Throwable ignored) {
            // 托管目录不可用不拖累默认链
        }
        return null;
    }

    /** 本地 PNG → 动态纹理（失败返回 null，不抛错）；mtime 变了自动重载，老纹理顺带释放。 */
    private static ResourceLocation localTexture(Path file) {
        try {
            if (!Files.isRegularFile(file)) {
                return null;
            }
            long mtime = Files.getLastModifiedTime(file).toMillis();
            String key = file.toAbsolutePath().normalize().toString();
            ResourceLocation cached = TEXTURE_CACHE.get(key);
            Long seen = FILE_MTIME.get(key);
            if (cached != null && seen != null && seen == mtime) {
                return cached; // 文件没动过：直接用热缓存
            }
            NativeImage image;
            try (var in = Files.newInputStream(file)) {
                image = NativeImage.read(in);
            }
            DynamicTexture texture = CompatRender.newDynamicTexture(image);
            ResourceLocation id = CompatRender.rl("opendreamcore",
                    "local/" + Integer.toHexString(key.hashCode()));
            // 同 id 重注册：TextureManager 会自动释放旧纹理，文件改了不用手动清缓存
            Minecraft.getInstance().getTextureManager().register(id, texture);
            TEXTURE_CACHE.put(key, id);
            FILE_MTIME.put(key, mtime);
            return id;
        } catch (IOException e) {
            return null;
        } catch (Throwable t) {
            return null;
        }
    }
}
