package com.opendreamcore.client;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.ImageIO;

/**
 * 资源云兜底（Legacy 侧，对齐现代端 CloudTextureFallback 的语义）。
 *
 * <p>云文件落在 {@code <游戏目录>/OpenDreamCore/cache}，不在散装资源扫描根里——同步下来
 * 没人注册，图标/字符贴图就一直取不到。规则里写短名（{@code sword.png}）而云里是
 * {@code item/icons/sword.png} 时按尾名反查。
 */
public final class LegacyCloudTextureFallback {

    /** 云里确实没有的路径：记下来，别每次都去问磁盘。 */
    private static final Set<String> MISSING = ConcurrentHashMap.newKeySet();

    private LegacyCloudTextureFallback() {
    }

    /**
     * 本地未命中时问一次云；命中就解码注册并返回纹理位置，云里也没有返回 null。
     * 查不到只记 MISSING，不抛——缺席即安全。
     */
    public static String tryRegister(String key) {
        if (key == null || key.isEmpty() || MISSING.contains(key)) {
            return null;
        }
        if (!CloudCache.isSynced()) {
            return null;   // 还没同步完就别猜，下一帧再来
        }
        byte[] data = CloudCache.textureBytes(key);
        if (data == null || data.length == 0) {
            MISSING.add(key);
            return null;
        }
        BufferedImage img;
        try {
            img = ImageIO.read(new ByteArrayInputStream(data));
        } catch (Throwable t) {
            img = null;
        }
        if (img == null) {
            MISSING.add(key);
            return null;
        }
        String rl = LooseTextureLoader.registerAdhoc(key, img);
        if (rl == null || rl.isEmpty()) {
            MISSING.add(key);
            return null;
        }
        return rl;
    }

    /** 云清单/同步进度变化时清缓存（MISSING 会挡住刚下来的文件）。 */
    public static void reset() {
        MISSING.clear();
    }
}