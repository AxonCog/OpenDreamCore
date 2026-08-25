package com.opendreamcore.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GIF 播放器：解码 GIF 帧 + 帧延迟，按时间切帧渲染。
 * 资源引用（image 组件 src）以 .gif 结尾时走这里；本地文件/云缓存均可。
 */
public final class GifPlayer {

    /** 一帧：图片 + 显示时长（毫秒）。 */
    private record Frame(NativeImage image, int delayMs) {
    }

    private static final Map<String, GifPlayer> CACHE = new ConcurrentHashMap<>();

    private final List<Frame> frames = new ArrayList<>();
    private final List<Integer> starts = new ArrayList<>();
    private final int totalMs;
    private final ResourceLocation textureId;
    private int lastIndex = -1;

    private GifPlayer(List<Frame> frames, int totalMs) {
        this.frames.addAll(frames);
        this.totalMs = totalMs;
        this.textureId = CompatRender.rl("opendreamcore",
                "gif/" + Integer.toHexString(System.identityHashCode(this)));
        int acc = 0;
        for (Frame frame : frames) {
            starts.add(acc);
            acc += frame.delayMs();
        }
    }

    /** 取当前帧纹理（按时间循环，帧变化才重建动态纹理）。 */
    public ResourceLocation currentTexture() {
        if (frames.isEmpty()) {
            return null;
        }
        int t = (int) (System.currentTimeMillis() % Math.max(totalMs, 1));
        int index = 0;
        for (int i = 0; i < starts.size(); i++) {
            if (t >= starts.get(i)) {
                index = i;
            }
        }
        if (index != lastIndex) {
            lastIndex = index;
            DynamicTexture tex = CompatRender.newDynamicTexture(frames.get(index).image());
            Minecraft.getInstance().getTextureManager().register(textureId, tex);
        }
        return textureId;
    }

    /**
     * 解析 GIF（文件或远程 URL）。返回 null 表示不是 GIF/失败/尚未下载完成。
     * 远程 GIF：RemoteMedia 下载缓存（SSRF 防护）→ 渲染线程解码注册，就绪后自动显示。
     */
    public static GifPlayer of(String src) {
        if (src == null) {
            return null;
        }
        String s = src.trim();
        if (s.startsWith("https://") || s.startsWith("http://")) {
            return ofRemote(s);
        }
        GifPlayer cached = CACHE.get(src);
        if (cached != null) {
            return cached;
        }
        try {
            Path file = resolve(src);
            if (file == null || !Files.isRegularFile(file)) {
                return null;
            }
            List<Frame> frames;
            try (var in = Files.newInputStream(file)) {
                frames = decode(in);
            }
            if (frames.isEmpty()) {
                return null;
            }
            GifPlayer player = new GifPlayer(frames, frames.stream().mapToInt(Frame::delayMs).sum());
            CACHE.put(src, player);
            return player;
        } catch (Exception e) {
            return null;
        }
    }

    /** 远程 GIF：下载完成后在渲染线程解码注册（未就绪返回 null，页面先占位）。 */
    private static GifPlayer ofRemote(String url) {
        if (!com.opendreamcore.remote.RemoteMedia.isSafeUrl(url)) {
            return null; // SSRF 防护拒绝
        }
        GifPlayer cached = CACHE.get(url);
        if (cached != null) {
            return cached;
        }
        com.opendreamcore.remote.RemoteMedia.get(url, RemoteImageStore.cacheDir()).thenAccept(path -> {
            Minecraft.getInstance().execute(() -> {
                try {
                    List<Frame> frames;
                    try (var in = Files.newInputStream(path)) { frames = decode(in); }
                    if (!frames.isEmpty()) {
                        GifPlayer player = new GifPlayer(frames, frames.stream().mapToInt(Frame::delayMs).sum());
                        CACHE.put(url, player);
                    }
                } catch (Exception ignored) {
                    // 解码失败：保持占位，下次请求可重试
                }
            });
        }).exceptionally(t -> null);
        return null;
    }

    /** 资源引用解析为本地路径（与 UiStyle.texture 同规则）。 */
    private static Path resolve(String src) {
        String s = src.trim();
        Path base = Minecraft.getInstance().gameDirectory.toPath();
        if (s.startsWith("assets/")) {
            return base.resolve("assets").resolve(s.substring("assets/".length()));
        }
        if (s.startsWith("minecraft:") || s.startsWith("http")) {
            return null; // 原版/网络 GIF 暂不支持
        }
        // gui/xxx → mod 资源包（classpath），先查本地 OpenDreamCore/UI 同名目录
        Path local = base.resolve("OpenDreamCore").resolve(s);
        if (Files.isRegularFile(local)) {
            return local;
        }
        return null;
    }

    /** ImageIO 解码 GIF 全部帧与延迟。 */
    private static List<Frame> decode(InputStream in) throws IOException {
        List<Frame> frames = new ArrayList<>();
        ImageReader reader = ImageIO.getImageReadersBySuffix("gif").next();
        try (ImageInputStream stream = ImageIO.createImageInputStream(in)) {
            reader.setInput(stream);
            int count = reader.getNumImages(true);
            for (int i = 0; i < count; i++) {
                BufferedImage img = reader.read(i);
                frames.add(new Frame(toNative(img), frameDelay(reader, i)));
            }
        } finally {
            reader.dispose();
        }
        return frames;
    }

    /** BufferedImage → NativeImage（逐像素拷贝，GIF 帧通常不大）。 */
    private static NativeImage toNative(BufferedImage img) {
        NativeImage out = new NativeImage(img.getWidth(), img.getHeight(), true);
        for (int x = 0; x < img.getWidth(); x++) {
            for (int y = 0; y < img.getHeight(); y++) {
                CompatRender.nativeSetPixel(out, x, y, img.getRGB(x, y));
            }
        }
        return out;
    }

    /** 读帧延迟（毫秒），缺省 100ms。 */
    private static int frameDelay(ImageReader reader, int index) {
        try {
            IIOMetadata meta = reader.getImageMetadata(index);
            IIOMetadataNode root = (IIOMetadataNode) meta.getAsTree(meta.getNativeMetadataFormatName());
            for (int i = 0; i < root.getLength(); i++) {
                IIOMetadataNode node = (IIOMetadataNode) root.item(i);
                if ("GraphicControlExtension".equals(node.getNodeName())) {
                    String delay = node.getAttribute("delay");
                    if (delay != null && !delay.isEmpty()) {
                        int ms = Integer.parseInt(delay) * 10;
                        return ms > 0 ? ms : 100;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return 100;
    }
}
