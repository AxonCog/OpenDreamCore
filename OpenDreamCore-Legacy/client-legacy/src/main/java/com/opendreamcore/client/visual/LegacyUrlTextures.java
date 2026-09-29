package com.opendreamcore.client.visual;

import com.opendreamcore.client.LegacyGifPlayer;
import com.opendreamcore.client.LooseTextureLoader;
import com.opendreamcore.client.spi.ChatNotifier;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 远端贴图直连（远古版）：FontConfig 里的贴图写成 http(s):// 时，这里负责把图取回来、
 * 当成一张普通散装贴图注册进纹理系统。注册用的键就是那条 URL 本身，所以规则层的
 * lookup / sheetOf / sizeOf 一个字都不用改——它们照旧按配置里写的字符串去查，
 * 查到的是直连取回来的那张图。
 *
 * 取图分两段：先按 gif 解一次帧表，解得出来说明真是动图，就首帧 + 帧表两路注册，
 * 跟本地散装贴图完全同一个流程，动图自然就能动；解不出来按静态图整张注册。
 * 解码全走 JDK 自带 ImageIO 与既有动图解码器，不引第三方库。
 *
 * 全程后台线程取图，绝不占渲染线程；取回来只在注册那一下碰纹理系统。失败重试有限次，
 * 之后在聊天栏提醒一次（同一地址只提醒一次），玩家能知道是规则写错了还是网络不通，
 * 而不是对着一行没替换的字符猜。任何异常都吞在取图线程里，渲染路径永远看到的是
 * 「没有这张图」这个普通结果，不会把网络异常带进字体渲染。
 */
public final class LegacyUrlTextures {

    /** 每个地址最多试这么多次（第一次 + 两次重试），再多就是网络问题，提醒玩家比硬耗着强。 */
    private static final int MAX_ATTEMPTS = 3;

    /** 单张图大小上限：防止一个畸形地址把内存吃干。 */
    private static final int MAX_BYTES = 32 * 1024 * 1024;

    /** 连接与读取各自的超时（毫秒）。 */
    private static final int CONNECT_TIMEOUT_MS = 8000;
    private static final int READ_TIMEOUT_MS = 15000;

    /** 已处理过的地址 → 是否成功。成功的不用再取；失败的记着，避免每条规则重复刷。 */
    private static final Map<String, Boolean> DONE = new ConcurrentHashMap<String, Boolean>();
    /** 进行中的地址，避免同一张图被并发取两遍。 */
    private static final Map<String, Boolean> RUNNING = new ConcurrentHashMap<String, Boolean>();

    private LegacyUrlTextures() {
    }

    /** 这台机器上是不是远端地址。 */
    public static boolean isRemote(String texture) {
        if (texture == null) {
            return false;
        }
        String s = texture.trim().toLowerCase(java.util.Locale.ROOT);
        return s.startsWith("http://") || s.startsWith("https://");
    }

    /**
     * 把所有远端贴图提前取回来（规则重解析后调一次）。已经在取的、已经取过的、
     * 非远端的都直接跳过。每个地址起一条后台线程，互不阻塞。
     */
    public static void prefetch(List<String> textures) {
        if (textures == null || textures.isEmpty()) {
            return;
        }
        // 同一批里可能重复出现，去个重省几条线程
        List<String> todo = new ArrayList<String>();
        for (String t : textures) {
            if (t == null || !isRemote(t)) {
                continue;
            }
            String key = normalize(t);
            if (key == null || DONE.containsKey(key) || RUNNING.containsKey(key) || todo.contains(key)) {
                continue;
            }
            todo.add(key);
        }
        for (final String key : todo) {
            RUNNING.put(key, Boolean.TRUE);
            Thread worker = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        fetch(key);
                    } catch (Throwable t) {
                        // 后台线程里任何意外都不许外泄，取不到就是取不到
                    } finally {
                        RUNNING.remove(key);
                    }
                }
            }, "odc-url-texture");
            worker.setDaemon(true);
            worker.start();
        }
    }

    /** 取一张远端图并注册；失败按次数重试，最终失败提醒一次。 */
    private static void fetch(String url) {
        byte[] bytes = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS && bytes == null; attempt++) {
            bytes = download(url);
            if (bytes == null && attempt < MAX_ATTEMPTS) {
                try {
                    Thread.sleep(1000L * attempt); // 退避一下再试，别连着捶服务器
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        if (bytes == null) {
            DONE.put(url, Boolean.FALSE);
            ChatNotifier.Host.warnOnce("odc-url-tex:" + url,
                    "§c[ODC] 远端贴图取不回来：" + url + "（规则里的地址或网络有问题，该字符保持原样）");
            return;
        }
        if (register(url, bytes)) {
            DONE.put(url, Boolean.TRUE);
        } else {
            DONE.put(url, Boolean.FALSE);
            ChatNotifier.Host.warnOnce("odc-url-tex:" + url,
                    "§c[ODC] 远端贴图不是有效图片：" + url + "（该字符保持原样）");
        }
    }

    /**
     * 下载字节。只认 http/https，跟着重定向走，读到的字节有上限。
     * 任何失败（包括超时、404、非图片）都返回 null，由调用方决定重试。
     */
    private static byte[] download(String url) {
        HttpURLConnection conn = null;
        InputStream in = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "OpenDreamCore");
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                return null;
            }
            in = conn.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                if (out.size() + n > MAX_BYTES) {
                    return null; // 超大，判定不是正常贴图
                }
                out.write(buf, 0, n);
            }
            byte[] data = out.toByteArray();
            return data.length == 0 ? null : data;
        } catch (Throwable t) {
            return null;
        } finally {
            closeQuietly(in);
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * 把取到的字节当散装贴图注册：先按动图解帧表（解得出来才说明真是多帧 gif），
     * 动图首帧 + 帧表两路注册，静态图整张注册。注册键就是那条 URL，
     * 于是规则层查询天然命中。返回是否注册成功。
     */
    private static boolean register(String url, byte[] bytes) {
        try {
            // 帧率取配置里给这条地址写的 fps（没写就用 gif 自带节奏），
            // 与本地动图同一套语义
            LegacyGifPlayer gif = decodeGif(bytes, LegacyFontReplace.gifFpsOf(url));
            BufferedImage img = gif != null ? firstFrameOf(gif) : decodeStatic(bytes);
            if (img == null) {
                return false;
            }
            LooseTextureLoader.registerRemote(url, img, gif);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 帧表切成首帧：帧表是横向排的一行，首帧就是最左边那一格。 */
    private static BufferedImage firstFrameOf(LegacyGifPlayer gif) {
        try {
            return gif.sheet().getSubimage(0, 0, gif.frameW(), gif.frameH());
        } catch (Throwable t) {
            return null;
        }
    }

    /** 静态解码（非动图）。失败返回 null。 */
    private static BufferedImage decodeStatic(byte[] bytes) {
        try {
            return javax.imageio.ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 按 gif 解帧表。解不出来（静态图、或不是 gif）返回 null。
     * 帧表超宽的交给既有加载器判断——这里只负责解，宽度上限在注册那一步统一裁。
     */
    private static LegacyGifPlayer decodeGif(byte[] bytes, double fpsOverride) {
        java.io.File tmp = null;
        try {
            tmp = java.io.File.createTempFile("odc-url-gif", ".gif");
            java.nio.file.Files.write(tmp.toPath(), bytes);
            return LegacyGifPlayer.decode(tmp, fpsOverride);
        } catch (Throwable t) {
            return null;
        } finally {
            if (tmp != null) {
                try {
                    tmp.delete();
                } catch (Throwable ignored) {
                    // 临时文件删不掉无所谓，系统会收
                }
            }
        }
    }

    /** 规范化：去空白、统一斜杠（URL 本身不用转斜杠，只做首尾清理）。 */
    private static String normalize(String url) {
        return url == null ? null : url.trim();
    }

    private static void closeQuietly(InputStream in) {
        if (in != null) {
            try {
                in.close();
            } catch (Throwable ignored) {
                // 关不掉就算了
            }
        }
    }
}
