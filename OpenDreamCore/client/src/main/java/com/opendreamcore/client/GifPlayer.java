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
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
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

    /** 一帧：合成后的全尺寸图片 + 显示时长（毫秒）。解码线程产生，纹理注册在渲染线程。 */
    private record Frame(BufferedImage image, int delayMs) {
    }

    private static final Map<String, GifPlayer> CACHE = new ConcurrentHashMap<>();

    private final List<Frame> frames = new ArrayList<>();
    private final List<Integer> starts = new ArrayList<>();
    private final ResourceLocation textureId;
    private volatile int totalMs;
    /** 统一帧间隔覆盖：>0 时忽略 gif 自带延迟，全部帧按 1000/fps 切（FontConfig fps 字段）。 */
    private volatile int uniformDelayMs;

    /** 当前帧索引（客户端 tickAll→tick 推进，纯 int 运算，任意线程安全）。 */
    private volatile int activeIndex;

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

    /** FontConfig 帧率落地：>0 用统一间隔（1000/fps 毫秒），否则回到 gif 自带帧间隔。 */
    public void setUniformFps(double fps) {
        uniformDelayMs = fps > 0 && fps <= 1000 ? (int) Math.max(1, Math.round(1000.0 / fps)) : 0;
        int acc = 0;
        starts.clear();
        for (Frame f : frames) {
            starts.add(acc);
            acc += delayOf(f);
        }
        totalMs = Math.max(acc, 1);
    }

    private int delayOf(Frame f) {
        return uniformDelayMs > 0 ? uniformDelayMs : f.delayMs();
    }

    /** 首帧整图纹理（UI/业务走静态整图：字形层动画走帧表 sheet，二者互不干扰）。 */
    private DynamicTexture staticTex;

    private boolean sheetReady;

    /** 帧表纹理 RL（全帧横向拼一张：字形按 activeIndex 切 uv，永不 upload 永不换纹理对象）。 */
    private ResourceLocation sheetRl;

    /** 纹理 RL（opendreamcore:gif/<hash>），供 LooseResourceLoader 反查播放器。 */
    public ResourceLocation textureLocation() {
        return textureId;
    }

    /** 当前帧索引（0..n-1），渲染时切 uv 用。 */
    public int currentFrame() {
        return frames.isEmpty() ? 0 : Math.min(activeIndex, frames.size() - 1);
    }

    /** 帧数（字形层算 uStep 用）。 */
    public int frameCount() {
        return frames.size();
    }

    /** 单帧像素宽（帧表横向等分）。 */
    public int frameW() {
        return frames.isEmpty() ? 1 : frames.get(0).image().getWidth();
    }

    /** 单帧像素高（帧表高方向完整一帧）。 */
    public int frameH() {
        return frames.isEmpty() ? 1 : frames.get(0).image().getHeight();
    }

    /** 取当前帧纹理（渲染线程静态整图首帧）：纯读、固定注册一次、绝不 upload。 */
    public ResourceLocation currentTexture() {
        if (frames.isEmpty()) {
            return null;
        }
        try {
            if (staticTex == null) {
                staticTex = CompatRender.newDynamicTexture(toNative(frames.get(0).image()));
                Minecraft.getInstance().getTextureManager().register(textureId, staticTex);
            }
        } catch (Throwable ignored) {
            // 注册失败（非渲染线程等）静默，渲染线程重试
        }
        return textureId;
    }

    /** 字形层帧表：全部帧横向拼成一张纹理（内容在构造时既定，运行时零 upload 零重建）。
     *  DIRECT 每帧按 currentFrame() 换 uv 切片——动画只是顶点 UV 变化，驱动层零风险。 */
    public ResourceLocation sheetTexture() {
        if (frames.isEmpty()) {
            return null;
        }
        if (sheetReady) {
            return sheetRl;
        }
        try {
            int w = frames.get(0).image().getWidth();
            int h = frames.get(0).image().getHeight();
            if (w <= 0 || h <= 0) {
                return null;
            }
            BufferedImage sheet = new BufferedImage(w * frames.size(), h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = sheet.createGraphics();
            for (int i = 0; i < frames.size(); i++) {
                g2.drawImage(frames.get(i).image(), i * w, 0, null);
            }
            g2.dispose();
            sheetRl = CompatRender.rl("opendreamcore", textureId.getPath() + "/sheet");
            Minecraft.getInstance().getTextureManager().register(sheetRl,
                    CompatRender.newDynamicTexture(toNative(sheet)));
            sheetReady = true;
            return sheetRl;
        } catch (Throwable ignored) {
            return sheetRl != null ? sheetRl : null;
        }
    }

    /** 客户端 tick 全局推进：时间→帧索引。纯 CPU 运算，绝不 upload/建纹理
     *  （NVIDIA 驱动 glTexSubImage2D 硬崩实测规避）。 */
    public void tick() {
        if (frames.isEmpty()) {
            return;
        }
        int t = (int) (System.currentTimeMillis() % Math.max(totalMs, 1));
        int index = 0;
        for (int i = 0; i < starts.size(); i++) {
            if (t >= starts.get(i)) {
                index = i;
            }
        }
        activeIndex = index;
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
                        // 远程 gif 就绪：清字形烘焙缓存，引用该 url 的替换字形下帧重新 bake 显示
                        com.opendreamcore.client.visual.ReplaceFontProvider.clear();
                    }
                } catch (Exception ignored) {
                    // 解码失败：保持占位，下次请求可重试
                }
            });
        }).exceptionally(t -> null);
        return null;
    }

    /** 内存字节流解析（散装扫描/资源云统一入口）：按 key 缓存，失败/非 gif 返回 null。 */
    public static GifPlayer ofBytes(String key, byte[] data) {
        if (key == null || data == null || data.length == 0) {
            return null;
        }
        GifPlayer cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        try (var in = new ByteArrayInputStream(data)) {
            List<Frame> frames = decode(in);
            if (frames.isEmpty()) {
                return null;
            }
            GifPlayer player = new GifPlayer(frames, frames.stream().mapToInt(Frame::delayMs).sum());
            CACHE.put(key, player);
            return player;
        } catch (Exception e) {
            return null;
        }
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
            if (count == 0) {
                return frames;
            }
            // GIF 优化帧只存变化区域（后续帧可能 1x1）——必须逐帧合成到逻辑屏幕画布，
            // 否则帧尺寸不一致（DynamicTexture.setPixels 会崩）且画面残缺。
            int width = reader.getWidth(0);
            int height = reader.getHeight(0);
            BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            for (int i = 0; i < count; i++) {
                BufferedImage frame = reader.read(i);
                int dx = 0;
                int dy = 0;
                int disposal = 1;
                try {
                    IIOMetadata meta = reader.getImageMetadata(i);
                    IIOMetadataNode root = (IIOMetadataNode) meta.getAsTree(meta.getNativeMetadataFormatName());
                    dx = Integer.parseInt(gifAttr(root, "imageLeftPosition", "0"));
                    dy = Integer.parseInt(gifAttr(root, "imageTopPosition", "0"));
                    disposal = Integer.parseInt(gifAttr(root, "disposalMethod", "1"));
                } catch (Exception ignored) {
                    // 元数据读不到就用默认（偏移 0 / 保留画布）
                }
                // disposal 2（恢复背景色）或 3（恢复上一帧前）：重置画布
                if (disposal == 2 || disposal == 3) {
                    canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                }
                Graphics2D g2 = canvas.createGraphics();
                g2.drawImage(frame, dx, dy, null);
                g2.dispose();
                // 快照当前画布（每帧独立 NativeImage，避免共享引用）
                BufferedImage snap = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                Graphics2D gs = snap.createGraphics();
                gs.drawImage(canvas, 0, 0, null);
                gs.dispose();
                // 铁定同尺寸：合成/快照万一受优化帧元数据影响出了非全尺寸帧，强制 resize 到逻辑尺寸
                if (snap.getWidth() != width || snap.getHeight() != height) {
                    BufferedImage fixed = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D gf = fixed.createGraphics();
                    gf.drawImage(snap, 0, 0, width, height, null);
                    gf.dispose();
                    snap = fixed;
                }
                frames.add(new Frame(snap, frameDelay(reader, i)));
            }
        } finally {
            reader.dispose();
        }
        return frames;
    }

    /** 遍历 GIF 元数据节点找属性（imageDescriptor 的偏移 / GraphicControlExtension 的 disposalMethod）。 */
    private static String gifAttr(IIOMetadataNode root, String attrName, String fallback) {
        for (int i = 0; i < root.getLength(); i++) {
            IIOMetadataNode node = (IIOMetadataNode) root.item(i);
            String v = node.getAttribute(attrName);
            if (v != null && !v.isEmpty()) {
                return v;
            }
            String child = gifAttr(node, attrName, null);
            if (child != null) {
                return child;
            }
        }
        return fallback;
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
