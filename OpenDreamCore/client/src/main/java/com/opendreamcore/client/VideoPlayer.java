package com.opendreamcore.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * 视频播放（帧序列方案）：目录下 frame_0000.png / frame_0001.png ... 按 fps 循环播放。
 * 引用写法：video: {src: "assets/video/xxx", fps: 24}，或 image 组件 src 指向帧序列目录。
 * 帧文件缓存在游戏目录 assets/（云同步下来的就是这种布局）。
 */
public final class VideoPlayer {

    private static final Map<String, VideoPlayer> CACHE = new ConcurrentHashMap<>();

    private final List<String> frames;
    private final ResourceLocation textureId;
    private int lastIndex = -1;

    private VideoPlayer(List<String> frames) {
        this.frames = frames;
        this.textureId = CompatRender.rl("opendreamcore",
                "video/" + Integer.toHexString(System.identityHashCode(this)));
    }

    /** 当前帧纹理（按 fps 与时间取帧，帧变化才重新加载贴图）。 */
    public ResourceLocation currentTexture(double fps) {
        if (frames.isEmpty()) {
            return null;
        }
        int index = (int) ((System.currentTimeMillis() / 1000.0 * Math.max(fps, 1)) % frames.size());
        if (index != lastIndex) {
            lastIndex = index;
            try {
                NativeImage image;
                try (var in = Files.newInputStream(Path.of(frames.get(index)))) {
                    image = NativeImage.read(in);
                }
                Minecraft.getInstance().getTextureManager()
                        .register(textureId, CompatRender.newDynamicTexture(image));
            } catch (IOException e) {
                return null;
            }
        }
        return textureId;
    }

    /** 解析帧序列（目录或目录+frame 前缀）。返回 null 表示没有帧。 */
    public static VideoPlayer of(String dirPath) {
        VideoPlayer cached = CACHE.get(dirPath);
        if (cached != null) {
            return cached;
        }
        Path dir = resolve(dirPath);
        if (dir == null || !Files.isDirectory(dir)) {
            return null;
        }
        List<String> frames;
        try (Stream<Path> stream = Files.list(dir)) {
            frames = stream.filter(p -> p.getFileName().toString().matches("frame_\\d+\\.png"))
                    .sorted()
                    .map(Path::toString)
                    .toList();
        } catch (IOException e) {
            return null;
        }
        if (frames.isEmpty()) {
            return null;
        }
        VideoPlayer player = new VideoPlayer(frames);
        CACHE.put(dirPath, player);
        return player;
    }

    /** 资源引用解析：assets/xxx → 游戏目录 assets/xxx。 */
    private static Path resolve(String src) {
        String s = src.trim();
        Path base = Minecraft.getInstance().gameDirectory.toPath();
        if (s.startsWith("assets/")) {
            return base.resolve("assets").resolve(s.substring("assets/".length()));
        }
        return base.resolve(s);
    }
}
