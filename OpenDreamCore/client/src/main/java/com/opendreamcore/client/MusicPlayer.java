package com.opendreamcore.client;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 背景音乐播放器（javax.sound）：播放 OpenDreamCore/music/ 与 OpenDreamCore/cache/music/（云端加密缓存）下的音频。
 * WAV 原生支持；MP3/OGG 需要 JVM 装了对应 SPI（mp3spi/vorbisspi，可选依赖）。
 * 加载在后台线程（AudioSystem 解码可能阻塞），音量用 MASTER_GAIN 换算 dB。
 */
public final class MusicPlayer {

    public static final Logger LOGGER = LogUtils.getLogger();

    private static final MusicPlayer INSTANCE = new MusicPlayer();

    public static MusicPlayer get() {
        return INSTANCE;
    }

    private final ExecutorService loader = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "odc-music");
        t.setDaemon(true);
        return t;
    });

    private volatile Clip clip;
    private volatile String current;
    private volatile float volume = 0.8F;
    private volatile boolean loop = true;

    private MusicPlayer() {
    }

    /**
     * 播放音频（名字含扩展名；找不到文件/格式不支持时静默）。
     * 本地：OpenDreamCore/music/；
     * 云端：OpenDreamCore/cache/music/（加密态缓存，内存解密 → 临时文件播放）；
     * 远程：http(s) URL（RemoteMedia 下载缓存 + SSRF 防护，缓存于 cache/http-cache/）。
     */
    public void play(String file, double vol, boolean loop) {
        if (file == null || file.isBlank()) {
            return;
        }
        stopNow();
        this.volume = (float) Math.max(0, Math.min(1, vol));
        this.loop = loop;
        String remote = file.trim();
        if (remote.startsWith("https://") || remote.startsWith("http://")) {
            if (!com.opendreamcore.remote.RemoteMedia.isSafeUrl(remote)) {
                LOGGER.warn("远程音频地址不安全（SSRF 防护拒绝）: {}", remote);
                return;
            }
            Path cacheDir = RemoteImageStore.cacheDir();
            loader.execute(() -> {
                try {
                    Path cached = com.opendreamcore.remote.RemoteMedia.download(remote, cacheDir);
                    if (cached == null) {
                        LOGGER.warn("远程音频下载失败: {}", remote);
                        return;
                    }
                    playPath(cached, remote);
                } catch (Exception e) {
                    LOGGER.warn("远程音频播放失败 {}: {}", remote, e.toString());
                }
            });
            return;
        }
        Path resolved = resolve(file);
        if (resolved == null) {
            LOGGER.warn("音乐文件不存在: {}（OpenDreamCore/music/ 或云端 music/）", file);
            return;
        }
        loader.execute(() -> playPath(resolved, file));
    }

    /** 后台线程：解码并播放本地缓存文件。 */
    private void playPath(Path resolved, String label) {
        try {
            AudioInputStream in = AudioSystem.getAudioInputStream(resolved.toFile());
            Clip newClip = AudioSystem.getClip();
            newClip.open(in);
            applyVolume(newClip);
            if (loop) {
                newClip.loop(Clip.LOOP_CONTINUOUSLY);
            } else {
                newClip.start();
            }
            synchronized (this) {
                stopNow();
                this.clip = newClip;
                this.current = label;
            }
            LOGGER.info("背景音乐播放 {}（{}，循环 {}）", label, resolved, loop);
        } catch (Exception e) {
            LOGGER.warn("背景音乐播放失败 {}: {}", label, e.toString());
        }
    }

    public void stop() {
        loader.execute(this::stopNow);
    }

    private synchronized void stopNow() {
        Clip old = clip;
        clip = null;
        current = null;
        if (old != null) {
            try {
                old.stop();
                old.close();
            } catch (Exception ignored) {
            }
        }
    }

    /** 实时调音量（0-1）。 */
    public void volume(double vol) {
        volume = (float) Math.max(0, Math.min(1, vol));
        Clip c = clip;
        if (c != null && c.isOpen()) {
            applyVolume(c);
        }
    }

    public double volume() {
        return volume;
    }

    public boolean isPlaying() {
        Clip c = clip;
        return c != null && c.isRunning();
    }

    public String current() {
        return current;
    }

    private void applyVolume(Clip c) {
        try {
            if (c.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl gain = (FloatControl) c.getControl(FloatControl.Type.MASTER_GAIN);
                float db = (float) (20 * Math.log10(Math.max(0.001, volume)));
                gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), db)));
            }
        } catch (Exception ignored) {
        }
    }

    /** 解析音乐文件（本地目录优先，其次云端同步目录）。 */
    private static Path resolve(String file) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gameDirectory == null) {
            return null;
        }
        Path game = mc.gameDirectory.toPath();
        Path local = game.resolve("OpenDreamCore").resolve("music").resolve(file).normalize();
        if (Files.isRegularFile(local)) {
            return local;
        }
        // 云端音乐：加密态缓存，解密到临时文件播放
        CloudSyncClient cloud = ClientController.get().cloud();
        if (cloud != null) {
            byte[] data = cloud.loadCached("music/" + file);
            if (data != null && data.length > 0) {
                try {
                    Path tmp = Files.createTempFile("odc-music-", ".tmp");
                    tmp.toFile().deleteOnExit();
                    Files.write(tmp, data);
                    return tmp;
                } catch (Exception e) {
                    LOGGER.warn("云端音乐解密失败 {}: {}", file, e.toString());
                }
            }
        }
        return null;
    }
}
