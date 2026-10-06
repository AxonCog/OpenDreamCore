package com.opendreamcore.client.resources;

import com.opendreamcore.client.ClientController;
import com.opendreamcore.client.CloudSyncClient;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 资源云兜底：本地散装资源根（resourcepacks/OpenDreamCore）里找不到的贴图，
 * 去服务端云缓存里取——磁盘上的云文件是加密+哈希名，这里按服务端清单里的原路径解密注册。
 *
 * <p>为什么必须有：云文件落在 {@code <游戏目录>/OpenDreamCore/cache/}，不在散装资源扫描根里，
 * 同步下来没人注册，图标/字符贴图就一直取不到（2026-10 实机：资源云已同步、图标不替换）。
 * 规则里写短名（{@code sword.png}）而云里是 {@code item/icons/sword.png} 时按尾名反查。
 */
public final class CloudTextureFallback {

    /** 云里也没有的路径：记下来，别每帧去问。 */
    private static final Set<String> MISSING = ConcurrentHashMap.newKeySet();
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private CloudTextureFallback() {
    }

    /**
     * 本地未命中时问一次云。命中就注册并返回纹理；云里也没有返回 null。
     * 首次调用会把纹理排进待注册队列，顺手在渲染线程冲一次（本方法都在渲染线程跑）。
     */
    public static ResourceLocation tryRegister(String key) {
        if (key == null || key.isEmpty() || MISSING.contains(key)) {
            return null;
        }
        CloudSyncClient cloud;
        try {
            cloud = ClientController.get().cloud();
        } catch (Throwable t) {
            return null;
        }
        if (cloud == null || !cloud.isSynced()) {
            return null;   // 还没同步完就别猜，等下一帧
        }
        List<String> paths = cloud.manifestPaths();
        if (paths.isEmpty()) {
            return null;
        }
        String hit = pick(paths, key);
        if (hit == null) {
            MISSING.add(key);
            return null;
        }
        byte[] data = cloud.loadCached(hit);
        if (data == null || data.length == 0) {
            MISSING.add(key);
            return null;
        }
        // 云里的原路径和规则里写的名字都注册一份，短名/带目录两种写法都认
        boolean ok = register(hit, data);
        if (!hit.equals(key)) {
            ok |= register(key, data);
        }
        if (!ok) {
            MISSING.add(key);
            return null;
        }
        if (WARNED.add(key)) {
            LOGGER.info("贴图走资源云兜底: {} -> {}", key, hit);
        }
        flush();
        ResourceLocation rl = LooseResourceLoader.lookup(key);
        return rl;
    }

    /** 清单里找同名或同尾名的路径。 */
    private static String pick(List<String> paths, String key) {
        String want = key.replace('\\', '/');
        for (String p : paths) {
            if (p.equals(want)) {
                return p;
            }
        }
        String tail = tailOf(want);
        for (String p : paths) {
            if (tailOf(p).equalsIgnoreCase(tail)) {
                return p;
            }
        }
        return null;
    }

    private static String tailOf(String p) {
        int i = p.lastIndexOf('/');
        return (i >= 0 ? p.substring(i + 1) : p).toLowerCase(Locale.ROOT);
    }

    private static boolean register(String key, byte[] data) {
        try {
            return LooseResourceLoader.registerBytes(key, data);
        } catch (Throwable t) {
            return false;
        }
    }

    private static void flush() {
        try {
            LooseResourceLoader.flushPending();
        } catch (Throwable ignored) {
            // 非渲染线程时 flushPending 自己会转交渲染线程
        }
    }

    /** 规则集/云清单变化时清缓存（MISSING 会让新到的云资源查不到）。 */
    public static void reset() {
        MISSING.clear();
        WARNED.clear();
    }

    private static final org.slf4j.Logger LOGGER =
            org.slf4j.LoggerFactory.getLogger("OpenDreamCore-CloudTex");
}