package com.opendreamcore.client.resources;

import com.mojang.blaze3d.platform.NativeImage;
import com.opendreamcore.client.GifPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

/**
 * 本地散装资源加载器。
 *
 * 扫描 gameDir/resourcepacks/OpenDreamCore/ 下的所有文件，凡能解码成图的经 DynamicTexture
 * 运行时注册进 TextureManager——完全绕开资源包系统，因此：
 * 一、无需注入器/mixin/重载；二、中文文件名天然支持（注册表键=原始文件名，
 * 仅合成 ResourceLocation 时做净化）；三、全部 target 由本类统一对齐，零平台差异。
 *
 * YAML 引用方式：texture 路径写文件名（含中文亦可），绘制层先查本注册表。
 *
 * 不挑食：什么文件都收，能不能进看解码结果不看扩展名（pnm/坏文件静默跳过）。
 * gif 不止首帧：注册时顺带起个 GifPlayer 按时间切帧，lookup 永远给当前帧——
 * 字符替换/图标写 .gif 就是动画；FontConfig 还能带可选 fps 字段改播放帧率
 * （缺省用 gif 自带的帧间隔）。
 */
public final class LooseResourceLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(LooseResourceLoader.class);

    /** 原始文件名（含中文）→ 已注册纹理 RL。 */
    private static final Map<String, ResourceLocation> REGISTRY = new ConcurrentHashMap<>();

    /** 原始文件名 → 图片像素尺寸（range 批量替换按贴图横向等分帧宽用）。 */
    private static final Map<String, Size> SIZES = new ConcurrentHashMap<>();

    /** gif 动画播放器：纹理名 → 播放器（跟静态注册同键）。 */
    private static final Map<String, GifPlayer> GIFS = new ConcurrentHashMap<>();

    /** gif 帧率覆盖：纹理名 → fps（0=遵循 gif 自带帧间隔）。 */
    private static final Map<String, Double> GIF_FPS = new ConcurrentHashMap<>();

    /** 待渲染线程注册队列：解码任何线程安全（NativeImage.read/ImageIO 不碰 GL），
     *  但 TextureManager.register 只在渲染线程合法——启动资源重载跑在 Worker 线程，
     *  直接在非渲染线程 register 会抛 "RenderSystem called from wrong thread"，
     *  导致全部注册失败（字符替换/图标/世界贴图全查不到）。解码先进队，渲染 tick 统一注册。 */
    private static final java.util.concurrent.ConcurrentLinkedQueue<Object[]> PENDING
            = new java.util.concurrent.ConcurrentLinkedQueue<>();

    /** 图片尺寸（像素）。 */
    public record Size(int width, int height) {
    }

    /** 扫描根：gameDir/resourcepacks/OpenDreamCore（与 DreamCore 形态一致）。 */
    public static Path scanRoot(Path gameDir) {
        return gameDir.resolve("resourcepacks").resolve("OpenDreamCore");
    }

    /** 清空注册表（Ctrl+R / reload 时旧贴图先清再重扫，防脏残留）。 */
    public static void clear() {
        REGISTRY.clear();
        SIZES.clear();
        GIFS.clear();
        GIF_FPS.clear();
        PENDING.clear();
    }

    /** 注册表查询：命中返回 RL（gif 给当帧），未命中 null。 */
    public static ResourceLocation lookup(String fileName) {
        if (fileName == null) {
            return null;
        }
        String want = fileName.replace('\\', '/');
        // 远程 url：gif 走 GifPlayer 远程解码、静态图走 RemoteImageStore（未就绪返回 null，下载完成自动出现）
        if (want.startsWith("https://") || want.startsWith("http://")) {
            if (want.toLowerCase(java.util.Locale.ROOT).endsWith(".gif")) {
                GifPlayer remoteGif = GifPlayer.of(want);
                return remoteGif == null ? null : remoteGif.currentTexture();
            }
            return com.opendreamcore.client.RemoteImageStore.get(want);
        }
        // gif 动画优先：播放器按时间切帧，每次拿当前帧
        ResourceLocation anim = animated(want);
        if (anim != null) {
            return anim;
        }
        ResourceLocation hit = REGISTRY.get(want);
        if (hit != null) {
            return hit;
        }
        // 尾名匹配兜底：规则里写的是短文件名，资源云的注册键可能带子目录
        // （icons/sword.png 注册的，规则里写 sword.png 也认得出来）
        String tail = tailOf(want);
        for (Map.Entry<String, ResourceLocation> e : REGISTRY.entrySet()) {
            if (tailOf(e.getKey()).equalsIgnoreCase(tail)) {
                return e.getValue();
            }
        }
        return null;
    }

    /** FontConfig 的 fps 字段落地：纹理名 → gif 播放帧率（0=用 gif 自带帧间隔）。 */
    public static void setGifFps(String fileName, double fps) {
        if (fileName == null) {
            return;
        }
        String want = fileName.replace('\\', '/');
        GIF_FPS.put(want, fps);
        GifPlayer p = gifOf(want);
        if (p != null) {
            p.setUniformFps(fps);
        }
    }

    /** 渲染线程客户端 tick 全局驱动：推进所有 gif 播放器帧上传（与每帧查询解耦，渲染路径零 upload）。 */
    public static void tickAll() {
        if (GIFS.isEmpty()) {
            return;
        }
        for (GifPlayer p : GIFS.values()) {
            try {
                p.tick();
            } catch (Throwable ignored) {
                // 单播放器异常不拖垮其他 gif/客户端 tick
            }
        }
    }

    /** 帧信息：字形层动画切 uv 用（未匹配/静态恒 0 帧 / 1 帧）。 */
    public record GifFrames(int frame, int frames) {
    }

    /** 替换字形贴图绘制信息：rl=要绑定的纹理，frame=当前帧，frames=总帧数，
     *  frameW/frameH=单帧像素（png 就是整图尺寸）。 */
    public record SheetInfo(ResourceLocation rl, int frame, int frames, int frameW, int frameH) {
    }

    /** 替换字形贴图取图信息：gif 返回帧表纹理+当前帧（自绘路径切 uv 出动画），png 走静态整图。 */
    public static SheetInfo sheetOf(String fileName) {
        if (fileName == null) {
            return null;
        }
        String want = fileName.replace('\\', '/');
        // 远程 url：gif 走帧表（未就绪 null），静态图走 RemoteImageStore
        if (want.startsWith("https://") || want.startsWith("http://")) {
            GifPlayer remoteGif = GifPlayer.of(want);
            if (remoteGif != null) {
                ResourceLocation rl = remoteGif.sheetTexture();
                if (rl != null) {
                    int total = Math.max(remoteGif.frameCount(), 1);
                    return new SheetInfo(rl, Math.min(Math.max(remoteGif.currentFrame(), 0), total - 1), total,
                            Math.max(remoteGif.frameW(), 1), Math.max(remoteGif.frameH(), 1));
                }
            }
            ResourceLocation staticRl = com.opendreamcore.client.RemoteImageStore.get(want);
            if (staticRl == null) {
                return null;
            }
            var sz = com.opendreamcore.client.RemoteImageStore.sizeOf(want);
            return new SheetInfo(staticRl, 0, 1, sz != null ? Math.max(sz.width(), 1) : 1,
                    sz != null ? Math.max(sz.height(), 1) : 1);
        }

        GifPlayer p = gifOf(want);
        if (p != null) {
            ResourceLocation rl = p.sheetTexture();
            if (rl != null) {
                int total = Math.max(p.frameCount(), 1);
                return new SheetInfo(rl, Math.min(Math.max(p.currentFrame(), 0), total - 1), total,
                        Math.max(p.frameW(), 1), Math.max(p.frameH(), 1));
            }
        }
        ResourceLocation rl = lookup(want);
        if (rl == null) {
            return null;
        }
        Size sz = sizeOf(want);
        return new SheetInfo(rl, 0, 1, sz != null ? Math.max(sz.width(), 1) : 16,
                sz != null ? Math.max(sz.height(), 1) : 16);
    }

    /** 按纹理 RL path（gif/<hash> 或 gif/<hash>/sheet）反查播放器当前帧信息。 */
    public static GifFrames gifFrames(String rlPath) {
        if (rlPath == null || GIFS.isEmpty()) {
            return new GifFrames(0, 1);
        }
        for (GifPlayer p : GIFS.values()) {
            ResourceLocation loc = p.textureLocation();
            if (loc != null) {
                String base = loc.getPath();
                if (rlPath.equals(base) || rlPath.startsWith(base + "/")) {
                    int total = Math.max(p.frameCount(), 1);
                    return new GifFrames(Math.min(Math.max(p.currentFrame(), 0), total - 1), total);
                }
            }
        }
        return new GifFrames(0, 1);
    }

    /** gif 当前帧纹理 RL（纯读返回动画同一纹理对象，绝不 upload——帧推进由 tickAll 驱动）；不是 gif/没注册返回 null。 */
    private static ResourceLocation animated(String want) {
        GifPlayer p = gifOf(want);
        return p == null ? null : p.currentTexture();
    }

    /** gif 播放器按名查（精确 + 尾名兜底）。 */
    private static GifPlayer gifOf(String want) {
        if (GIFS.isEmpty()) {
            return null;
        }
        GifPlayer hit = GIFS.get(want);
        if (hit != null) {
            return hit;
        }
        String tail = tailOf(want);
        for (Map.Entry<String, GifPlayer> e : GIFS.entrySet()) {
            if (tailOf(e.getKey()).equalsIgnoreCase(tail)) {
                return e.getValue();
            }
        }
        return null;
    }

    /** 字形层按文件名拿播放器（null=非 gif/未注册）。远程 gif 也认（未就绪返回 null）。 */
    public static GifPlayer gifPlayerOf(String fileName) {
        if (fileName == null) {
            return null;
        }
        String want = fileName.replace('\\', '/');
        if (want.startsWith("https://") || want.startsWith("http://")) {
            return GifPlayer.of(want);
        }
        return gifOf(want);
    }

    private static String tailOf(String name) {
        int slash = name.lastIndexOf('/');
        return slash >= 0 ? name.substring(slash + 1) : name;
    }

    /** gif 的动画纹理 RL（字形层/静态引用统一拿动画同纹理：对象不变 view 不失效，内容由 tickAll 更新）；非 gif/未注册返回 null。 */
    public static ResourceLocation staticOf(String fileName) {
        if (fileName == null) {
            return null;
        }
        String want = fileName.replace('\\', '/');
        GifPlayer p = gifOf(want);
        return p == null ? null : p.currentTexture();
    }

    /** 按文件名查图片尺寸（range 批量替换要按贴图横向等分算帧宽）。未注册返回 null。 */
    public static Size sizeOf(String fileName) {
        if (fileName == null) {
            return null;
        }
        Size hit = SIZES.get(fileName);
        if (hit != null) {
            return hit;
        }
        String want = fileName.replace('\\', '/');
        int slash = want.lastIndexOf('/');
        String tail = slash >= 0 ? want.substring(slash + 1) : want;
        for (Map.Entry<String, Size> e : SIZES.entrySet()) {
            String k = e.getKey();
            int ks = k.lastIndexOf('/');
            String kTail = ks >= 0 ? k.substring(ks + 1) : k;
            if (kTail.equalsIgnoreCase(tail)) {
                return e.getValue();
            }
        }
        return null;
    }

    /**
     * 扫描并注册根目录下全部文件（深度不限，用户想放几层放几层：
     * testfonts/1.png、a/b/c/2.png 都认，键名=相对路径）。
     * 不按扩展名过滤——是不是图看解码结果。
     * 返回成功注册数。
     */
    public static int loadAll(Path gameDir) {
        Path root = scanRoot(gameDir);
        if (!Files.isDirectory(root)) {
            return 0;
        }
        int n = 0;
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path p : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                String key = root.relativize(p).toString().replace('\\', '/');
                if (register(key, p)) {
                    n++;
                }
            }
        } catch (Exception e) {
            LOGGER.warn("散装资源扫描失败: {}", e.toString());
        }
        LOGGER.info("本地散装资源已扫描 {} 个（纹理由渲染线程统一注册）", n);
        return n;
    }

    /** 单文件注册（重名覆盖）：任何文件都试解码，是图就能进。 */
    public static boolean register(String key, Path imageFile) {
        try {
            return registerContent(key, Files.readAllBytes(imageFile));
        } catch (IOException e) {
            if (isKnownImageName(key)) {
                LOGGER.warn("散装资源读取失败 {}: {}", key, e.toString());
            }
            return false;
        }
    }

    /**
     * 内存注册：资源云解密完的字节直接进纹理注册表（gif 顺带起播放器），不落明文盘。
     * 键名沿用资源在云里的相对路径（含目录），和扫描出来的条目同一套。
     */
    public static boolean registerBytes(String key, byte[] data) {
        return registerContent(key, data);
    }

    /** 统一落地：gif 起播放器；图片按解码结果注册静态纹理；非图静默跳过（不按扩展名挑食）。 */
    private static boolean registerContent(String key, byte[] data) {
        if (key == null || data == null || data.length == 0) {
            return false;
        }
        String want = key.replace('\\', '/');
        // gif 播放器含帧纹理注册，必须在渲染线程建——先入队等 flush（静态首帧照走 decode 入队）
        if (isGifName(want)) {
            PENDING.add(new Object[]{Boolean.TRUE, want, data});
        }
        try {
            return enqueueFromBytes(want, data);
        } catch (IOException e) {
            if (isKnownImageName(want)) {
                LOGGER.warn("散装资源读取失败 {}: {}", want, e.toString());
            }
            return false;
        }
    }

    /** png/jpg 直读 NativeImage；gif/其他格式走 ImageIO 抽图转 PNG 字节再进 NativeImage。
     *  解码不碰 GL（任意线程安全），纹理注册入队等渲染线程 flush。 */
    private static boolean enqueueFromBytes(String key, byte[] data) throws IOException {
        NativeImage img = readAnyImage(key, data);
        String rlPath = sanitize(key);
        ResourceLocation rl = com.opendreamcore.client.CompatRender.rl("opendreamcore", "loose/" + rlPath);
        PENDING.add(new Object[]{Boolean.FALSE, key, rl, img});
        return true;
    }

    /** 渲染线程冲刷待注册队列（每帧渲染入口兜底 + reload/ensureManagedPacks 后立即调）。
     *  TextureManager.register / GifPlayer 创建全在渲染线程完成；
     *  非渲染线程调用时原样返回 0 并挂到渲染线程下次跑（一次性）。 */
    public static int flushPending() {
        if (!com.opendreamcore.client.CompatRender.isRenderThread()) {
            Minecraft.getInstance().execute(LooseResourceLoader::flushPending);
            return 0;
        }
        int n = 0;
        while (true) {
            Object[] item = PENDING.poll();
            if (item == null) {
                break;
            }
            try {
                if (Boolean.TRUE.equals(item[0])) {
                    String key = (String) item[1];
                    GifPlayer player = GifPlayer.ofBytes(key, (byte[]) item[2]);
                    if (player != null) {
                        GIFS.put(key, player);
                        Double fps = GIF_FPS.get(key);
                        if (fps != null) {
                            player.setUniformFps(fps);
                        }
                    }
                } else {
                    String key = (String) item[1];
                    ResourceLocation rl = (ResourceLocation) item[2];
                    NativeImage img = (NativeImage) item[3];
                    Minecraft.getInstance().getTextureManager().register(rl, new DynamicTexture(img));
                    REGISTRY.put(key, rl);
                    SIZES.put(key, new Size(img.getWidth(), img.getHeight()));
                }
                n++;
            } catch (Throwable t) {
                LOGGER.warn("散装资源纹理注册失败: {}", t.toString());
            }
        }
        if (n > 0) {
            LOGGER.info("本机散装资源渲染线程注册完成：{} 个", n);
        }
        return n;
    }

    private static NativeImage readAnyImage(String key, byte[] data) throws IOException {
        if (isGifName(key)) {
            return readGifFirstFrame(new ByteArrayInputStream(data));
        }
        try (InputStream in = new ByteArrayInputStream(data)) {
            return NativeImage.read(in);
        } catch (IOException e) {
            // png/jpg 之外格式 NativeImage 不认，回退 ImageIO（加载了对应插件的格式也能吃）
            BufferedImage bi = ImageIO.read(new ByteArrayInputStream(data));
            if (bi == null) {
                throw e;
            }
            return toPngNative(bi);
        }
    }

    /** BufferedImage → PNG 字节 → NativeImage（透明通道保留）。 */
    private static NativeImage toPngNative(BufferedImage bi) throws IOException {
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        if (!ImageIO.write(bi, "png", png)) {
            throw new IOException("图片转 png 失败");
        }
        return NativeImage.read(new ByteArrayInputStream(png.toByteArray()));
    }

    /** gif 首帧 → NativeImage：ImageIO 抽帧转 PNG 字节（透明通道保留），解码不了抛 IOException。 */
    private static NativeImage readGifFirstFrame(InputStream in) throws IOException {
        BufferedImage bi;
        try {
            bi = ImageIO.read(in);
        } catch (RuntimeException e) {
            throw new IOException("gif 解码异常", e);
        }
        if (bi == null) {
            throw new IOException("gif 解码无帧");
        }
        return toPngNative(bi);
    }

    /** 已知图片后缀（纯用于失败时打日志；注册本身不按扩展名卡）。 */
    private static boolean isKnownImageName(String name) {
        String n = name.toLowerCase();
        return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".gif");
    }

    private static boolean isGifName(String name) {
        return name.toLowerCase().endsWith(".gif");
    }

    /** RL 路径净化：非 [a-z0-9_.-/] 字符替换为下划线+短哈希，保证唯一且合法。 */
    private static String sanitize(String rel) {
        StringBuilder sb = new StringBuilder();
        for (char c : rel.toLowerCase().toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '.' || c == '/' || c == '-') {
                sb.append(c);
            } else {
                sb.append('_').append(Integer.toHexString(c));
            }
        }
        return sb.toString();
    }

    private LooseResourceLoader() {
    }
}
