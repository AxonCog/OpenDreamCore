package com.opendreamcore.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.opendreamcore.remote.RemoteMedia;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.awt.image.BufferedImage;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 真视频播放器（FFmpeg，可选接入）：把 JavaCV 相关 jar（javacv + javacv-platform 各平台原生包）
 * 丢进 mods 文件夹即自动启用，解码 mp4/webm/mov/mkv 等一切 FFmpeg 支持的格式；
 * 未安装 JavaCV 时回退帧序列方案（{@link VideoPlayer}）。
 *
 * 能力：
 * - 本地文件（assets/videos/x.mp4）与远程 URL（https://...，直连流式解码，走 SSRF 防护）
 * - 后台线程按视频帧率解码 → 最新帧换到渲染线程上传纹理（帧缓存，不卡主线程）
 * - loop 循环 / 单次播放（video: {src: "...", loop: false}）
 * - fit: contain 按原比例居中显示（未取到尺寸前先拉伸）
 *
 * 无音频（视频轨仅画面；音频由 Music.播放 单独配 BGM）。
 */
public final class FfmpegVideoPlayer {

    // ---- JavaCV 反射句柄（类缺失 = 未启用） ----
    private static final boolean AVAILABLE;
    private static final Constructor<?> GRABBER_CTOR; // FFmpegFrameGrabber(String)
    private static final Method START;
    private static final Method GET_FRAME_RATE;
    private static final Method GRAB_IMAGE;
    private static final Method SET_TIMESTAMP;
    private static final Method STOP;
    private static final Constructor<?> CONVERTER_CTOR; // Java2DFrameConverter()
    private static final Method CONVERT; // convert(Frame) → BufferedImage
        private static final Method GET_TIMESTAMP;  // getTimestamp() → long us
        private static final Method GET_LENGTH_IN_TIME; // getLengthInTime() → long us

    static {
        boolean ok = false;
        Constructor<?> grabberCtor = null;
        Method start = null, fps = null, grab = null, seek = null, stop = null;
        Method timestamp = null, lengthInTime = null;
        Constructor<?> convCtor = null;
        Method convert = null;
        try {
            Class<?> grabberClass = Class.forName("org.bytedeco.javacv.FFmpegFrameGrabber");
            Class<?> frameClass = Class.forName("org.bytedeco.javacv.Frame");
            grabberCtor = grabberClass.getConstructor(String.class);
            start = grabberClass.getMethod("start");
            fps = grabberClass.getMethod("getFrameRate");
            grab = grabberClass.getMethod("grabImage");
            seek = grabberClass.getMethod("setTimestamp", long.class);
            timestamp = grabberClass.getMethod("getTimestamp");
            lengthInTime = grabberClass.getMethod("getLengthInTime");
            stop = grabberClass.getMethod("stop");
            Class<?> converterClass = Class.forName("org.bytedeco.javacv.Java2DFrameConverter");
            convCtor = converterClass.getConstructor();
            convert = converterClass.getMethod("convert", frameClass);
            ok = true;
        } catch (Throwable ignored) {
            // JavaCV 不在类路径 → 真视频不可用（回退帧序列）
        }
        AVAILABLE = ok;
        GRABBER_CTOR = grabberCtor;
        START = start;
        GET_FRAME_RATE = fps;
        GRAB_IMAGE = grab;
        SET_TIMESTAMP = seek;
        STOP = stop;
        GET_TIMESTAMP = timestamp;
        GET_LENGTH_IN_TIME = lengthInTime;
        CONVERTER_CTOR = convCtor;
        CONVERT = convert;
    }

    private static final Map<String, FfmpegVideoPlayer> CACHE = new ConcurrentHashMap<>();
    private static final int MAX_CACHE = 16;
    private static long lastEvictMs;
    /** 元素 id → 播放器（Screen.视频暂停/继续/停止/重播 用）。 */
    private static final Map<String, FfmpegVideoPlayer> REGISTRY = new ConcurrentHashMap<>();

    private final String src;
    private final boolean loop;
    private final String fit;

    private volatile NativeImage pending;          // 后台线程最新帧
    private volatile int videoW;
    private volatile int videoH;
    private ResourceLocation textureId;
    private DynamicTexture uploaded;
    private volatile boolean failed;
    private volatile boolean paused;
    private volatile boolean stopped;
    private volatile boolean restartRequested;
    private volatile long seekToUs = -1; // 待跳转时间（微秒），-1 = 无
        private volatile long currentUs = 0;   // 当前播放时间（微秒，解码线程更新）
        private volatile long durationUs = -1; // 视频总时长（微秒，start 后读取）
    private volatile Thread thread;

    private FfmpegVideoPlayer(String src, boolean loop, String fit) {
        this.src = src;
        this.loop = loop;
        this.fit = fit;
    }

    /** JavaCV 是否可用（javacv 相关 jar 已丢进 mods）。 */
    public static boolean available() {
        return AVAILABLE;
    }

    /** 取播放器：src 为 http(s) URL 或本地路径；未启用/不支持返回 null。 */
    public static FfmpegVideoPlayer of(String src, boolean loop, String fit) {
        if (!AVAILABLE || src == null || src.isBlank()) {
            return null;
        }
        String key = src + "|" + loop + "|" + fit;
        FfmpegVideoPlayer existing = CACHE.get(key);
        if (existing != null) {
            return existing;
        }
        FfmpegVideoPlayer player = new FfmpegVideoPlayer(src.trim(), loop, fit);
        if (!player.start()) {
            return null;
        }
        FfmpegVideoPlayer prev = CACHE.putIfAbsent(key, player);
        FfmpegVideoPlayer result = prev != null ? prev : player;
        if (CACHE.size() > MAX_CACHE) {
            evictIfNeeded();
        }
        return result;
    }

    /** 元素 id → 播放器绑定（渲染时自动注册；脚本按元素 id 控制）。 */
    public static void register(String elementId, FfmpegVideoPlayer player) {
        if (elementId != null && player != null) {
            REGISTRY.put(elementId, player);
        }
    }

    public static FfmpegVideoPlayer byElement(String elementId) {
        return elementId == null ? null : REGISTRY.get(elementId);
    }

    // ---------- 播放控制 ----------

    /** 暂停解码（画面冻结在最后一帧）。 */
    public void pause() {
        paused = true;
    }

    /** 继续解码。 */
    public void resume() {
        paused = false;
        synchronized (this) {
            notifyAll();
        }
    }

    /** 停止并丢弃画面（页面移除后清理用）。 */
    public void stop() {
        stopped = true;
        paused = false;
        Thread t = thread;
        if (t != null) {
            t.interrupt();
        }
        synchronized (this) {
            notifyAll();
        }
        NativeImage last = pending;
        pending = null;
        if (uploaded != null) {
            uploaded.close();
            uploaded = null;
        }
        textureId = null;
        if (last != null) {
            last.close();
        }
    }

    /** 是否正在解码（未暂停未停止）。 */
    public boolean isPlaying() {
        return !paused && !stopped && !failed && thread != null && thread.isAlive();
    }

    /** 是否已解码出帧（加载指示用：false = 缓冲中）。 */
    public boolean hasFrame() {
        return pending != null || textureId != null;
    }

    /** 解码是否失败（原生库缺失/文件损坏等）。 */
    public boolean isFailed() {
        return failed;
    }

    private static synchronized void evictIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastEvictMs < 5000) {
            return;
        }
        lastEvictMs = now;
        if (CACHE.size() <= MAX_CACHE) {
            return;
        }
        int toRemove = CACHE.size() - MAX_CACHE;
        var it = CACHE.entrySet().iterator();
        for (int i = 0; i < toRemove && it.hasNext(); i++) {
            var e = it.next();
            it.remove();
            try {
                e.getValue().stop();
            } catch (Throwable ignored) {
            }
        }
    }

    /** 跳转到指定秒（seek；线程未启动时忽略）。 */
    public void seek(double seconds) {
        if (seconds < 0) {
            seconds = 0;
        }
        seekToUs = (long) (seconds * 1_000_000L);
        Thread t = thread;
        if (t != null) {
            t.interrupt(); // 打断帧间睡眠，循环里立即 seek
        }
        synchronized (this) {
            notifyAll();
        }
    }

    /** 重播：从头开始（线程已结束时重新拉起）。 */
        /** 当前播放秒数（seek 条用；无时间轴信息返回 -1）。 */
        public double currentSeconds() {
            return currentUs < 0 ? -1 : currentUs / 1_000_000.0;
        }

        /** 视频总秒数（无时间轴信息返回 -1）。 */
        public double durationSeconds() {
            return durationUs < 0 ? -1 : durationUs / 1_000_000.0;
        }

    public void restart() {
        if (stopped) {
            // 已停止：重新拉起解码线程
            stopped = false;
            paused = false;
            pending = null;
            thread = null;
            start();
            return;
        }
        restartRequested = true;
        Thread t = thread;
        if (t != null) {
            t.interrupt(); // 打断帧间睡眠，循环里立刻 seek 回 0
        }
        synchronized (this) {
            notifyAll();
        }
    }

    /** 本地路径解析（assets/xxx → 游戏目录 assets/xxx）。 */
    private static Path resolveLocal(String s) {
        Path base = Minecraft.getInstance().gameDirectory.toPath();
        if (s.startsWith("assets/")) {
            return base.resolve("assets").resolve(s.substring("assets/".length()));
        }
        return base.resolve(s);
    }

    /** 后台解码线程启动。 */
    private boolean start() {
        if (failed || stopped) {
            return false;
        }
        boolean remote = src.startsWith("https://") || src.startsWith("http://");
        if (remote) {
            if (!RemoteMedia.isSafeUrl(src)) {
                return false; // SSRF 防护拒绝
            }
        } else {
            Path file = resolveLocal(src);
            if (!Files.isRegularFile(file)) {
                return false;
            }
        }
        Thread t = new Thread(this::decodeLoop, "odc-video-" + Integer.toHexString(System.identityHashCode(this)));
        t.setDaemon(true);
        thread = t;
        t.start();
        return true;
    }

    /** 解码循环：按视频帧率抓帧 → 最新帧待上传；EOF 后按 loop 决定重播/停。 */
    private void decodeLoop() {
        Object grabber = null;
        try {
            grabber = GRABBER_CTOR.newInstance(src);
            START.invoke(grabber);
            try {
                durationUs = ((Number) GET_LENGTH_IN_TIME.invoke(grabber)).longValue();
            } catch (Throwable ignored) {
            }
            double fps = ((Number) GET_FRAME_RATE.invoke(grabber)).doubleValue();
            if (fps <= 0 || fps > 120) {
                fps = 24;
            }
            long intervalMs = Math.max(10, (long) (1000.0 / fps));
            Object converter = CONVERTER_CTOR.newInstance();
            while (!Thread.currentThread().isInterrupted() && !stopped) {
                // 暂停：冻结在最后一帧，等待继续/停止
                if (paused) {
                    synchronized (this) {
                        while (paused && !stopped) {
                            wait(100);
                        }
                    }
                    continue;
                }
                if (restartRequested) {
                    restartRequested = false;
                    SET_TIMESTAMP.invoke(grabber, 0L);
                }
                long seek = seekToUs;
                if (seek >= 0) {
                    seekToUs = -1;
                    SET_TIMESTAMP.invoke(grabber, seek);
                }
                long t0 = System.currentTimeMillis();
                Object frame = GRAB_IMAGE.invoke(grabber);
                if (frame == null) {
                    if (loop) {
                        SET_TIMESTAMP.invoke(grabber, 0L);
                        continue;
                    }
                    break; // 单次播放结束，保持最后一帧
                }
                try {
                    currentUs = ((Number) GET_TIMESTAMP.invoke(grabber)).longValue();
                } catch (Throwable ignored) {
                }
                BufferedImage image = (BufferedImage) CONVERT.invoke(converter, frame);
                if (image != null) {
                    videoW = image.getWidth();
                    videoH = image.getHeight();
                    NativeImage newImg = toNativeImage(image);
                    synchronized (FfmpegVideoPlayer.this) {
                        NativeImage old = pending;
                        pending = newImg;
                        if (old != null) {
                            old.close();
                        }
                    }
                }
                long elapsed = System.currentTimeMillis() - t0;
                long sleep = intervalMs - elapsed;
                if (sleep > 0) {
                    Thread.sleep(sleep);
                }
            }
        } catch (InterruptedException ignored) {
            // 停止/重播打断
        } catch (Throwable t) {
            failed = true; // 解码失败（原生库缺失/文件损坏等）：停播，页面显示占位
        } finally {
            if (grabber != null) {
                try {
                    STOP.invoke(grabber);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /** BufferedImage → NativeImage（ARGB → RGBA 逐像素拷贝）。 */
    private static NativeImage toNativeImage(BufferedImage source) {
        int w = source.getWidth();
        int h = source.getHeight();
        NativeImage out = new NativeImage(w, h, false);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                CompatRender.nativeSetPixel(out, x, y, source.getRGB(x, y));
            }
        }
        return out;
    }

    /** 视频原始宽（未取到帧前 0）。 */
    public int videoWidth() {
        return videoW;
    }

    public int videoHeight() {
        return videoH;
    }

    /** 渲染线程取当前帧纹理；无帧/失败/已停止返回 null。 */
    public synchronized ResourceLocation currentTexture() {
        if (failed || stopped) {
            return null;
        }
        NativeImage next = pending;
        if (next == null) {
            return textureId; // 尚未解码出帧：已有纹理继续显示
        }
        if (textureId == null) {
            textureId = CompatRender.rl("opendreamcore",
                    "video/" + Integer.toHexString(System.identityHashCode(this)));
        }
        DynamicTexture tex = CompatRender.newDynamicTexture(next);
        Minecraft.getInstance().getTextureManager().register(textureId, tex);
        if (uploaded != null) {
            uploaded.close(); // 旧帧纹理释放（其 NativeImage 一并释放）
        }
        uploaded = tex;
        pending = null;
        return textureId;
    }

    /** 按 fit 模式算绘制矩形：contain 保持原比例居中，其他拉伸铺满。 */
    public int[] drawRect(double nodeX, double nodeY, double nodeW, double nodeH) {
        int x = (int) nodeX;
        int y = (int) nodeY;
        int w = (int) nodeW;
        int h = (int) nodeH;
        if ("contain".equals(fit) && videoW > 0 && videoH > 0) {
            double scale = Math.min(nodeW / videoW, nodeH / videoH);
            w = (int) (videoW * scale);
            h = (int) (videoH * scale);
            x = (int) (nodeX + (nodeW - w) / 2.0);
            y = (int) (nodeY + (nodeH - h) / 2.0);
        }
        return new int[]{x, y, w, h};
    }
}
