package com.opendreamcore.client;

import com.opendreamcore.protocol.Crypto;
import com.opendreamcore.protocol.OdcByteArrayBuf;
import com.opendreamcore.protocol.Protocol;
import com.opendreamcore.protocol.message.CloudDelete;
import com.opendreamcore.protocol.message.CloudDiff;
import com.opendreamcore.protocol.message.CloudDone;
import com.opendreamcore.protocol.message.CloudFile;
import com.opendreamcore.protocol.message.CloudManifest;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 云资源缓存（老壳版）：收清单 → 对比本地缓存（gameDir/OpenDreamCore/cache/）→
 * 差异回请求 → 收加密文件原样落盘（不解密，使用时内存解密）→ 完成标记。
 * 与现代端 CloudSyncClient 同一套协议同一套落盘规则，会话 key 来自 ready_ack。
 *
 * 落盘文件名 = SHA-256(会话key ∥ 相对路径)（common 的 CacheNames 管）：
 * 磁盘上只有哈希名，看不到原始路径也枚举不了内容；读取时拿同一把 key 重算定位。
 */
public final class CloudCache {

    private static final Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger("OpenDreamCore");

    private static volatile Supplier<Path> gameDirSupplier;
    private static volatile byte[] sessionKey = new byte[0];
    private static volatile boolean synced;

    private CloudCache() { }

    /** 开局调一次：记住 gameDir（各版 ClientHooks 传，跟 LocalPackPreload 一个套路）。 */
    public static void setGameDir(Supplier<Path> gameDir) {
        gameDirSupplier = gameDir;
    }

    private static Path cacheDir() {
        Supplier<Path> s = gameDirSupplier;
        if (s == null) {
            return null;
        }
        try {
            Path dir = s.get();
            return dir == null ? null : dir.resolve("OpenDreamCore").resolve("cache");
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** ready_ack 到达时记录会话 key：key 换了旧缓存文件全部失配，视为未同步。 */
    public static void onReadyAck(byte[] key) {
        boolean changed = key == null || key.length == 0
                ? sessionKey.length != 0
                : !java.util.Arrays.equals(key, sessionKey);
        sessionKey = key == null ? new byte[0] : key;
        if (changed) {
            synced = false;
        }
    }

    public static boolean isSynced() {
        return synced;
    }

    /** 清单到达：对比本地缓存（加密态文件只查存在性，密文随 key 变没法比 hash）。 */
    public static void handleManifest(CloudManifest manifest) {
        Path dir = cacheDir();
        if (dir == null) {
            return;
        }
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            LOGGER.warn("[ODC] 云缓存目录创建失败: {}", e.toString());
            return;
        }
        List<String> missing = new ArrayList<>();
        for (CloudManifest.Entry entry : manifest.entries()) {
            Path file;
            try {
                file = safeResolve(entry.path());
            } catch (Exception e) {
                continue;
            }
            if (!Files.isRegularFile(file)) {
                missing.add(entry.path());
            }
        }
        if (missing.isEmpty()) {
            synced = true;
            LOGGER.info("[ODC] 云资源已是最新（{} 个文件）", manifest.entries().size());
            return;
        }
        LOGGER.info("[ODC] 云资源差异 {} 个文件，开始拉取", missing.size());
        OdcByteArrayBuf buf = new OdcByteArrayBuf();
        new CloudDiff(missing).encode(buf);
        ClientControllerLegacy.sendRawForCloud(Protocol.CLOUD_DIFF, buf.toByteArray());
    }

    /** 文件到达：加密态原样落盘，不解析不解密。 */
    public static void handleFile(CloudFile file) {
        Path dir = cacheDir();
        if (dir == null || file.encrypted() == null) {
            return;
        }
        try {
            Path target = safeResolve(file.path());
            Files.createDirectories(target.getParent());
            Path tmp = target.resolveSibling(target.getFileName().toString() + ".tmp");
            Files.write(tmp, file.encrypted());
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            LOGGER.warn("[ODC] 云文件落盘失败 {}: {}", file.path(), e.toString());
        }
    }

    /** 删除：清单里消失的文件同步删本地。 */
    public static void handleDelete(CloudDelete delete) {
        for (String path : delete.paths()) {
            try {
                Files.deleteIfExists(safeResolve(path));
            } catch (Exception e) {
                LOGGER.warn("[ODC] 云文件删除失败 {}: {}", path, e.toString());
            }
        }
    }

    public static void handleDone(CloudDone done) {
        synced = true;
        LOGGER.info("[ODC] 云资源同步完成");
    }

    /** 从缓存加载并解密到内存：加密态文件 → 明文字节。 */
    public static byte[] loadCached(String path) {
        try {
            byte[] encrypted = Files.readAllBytes(safeResolve(path));
            if (sessionKey.length == 0) {
                return encrypted;
            }
            return Crypto.decrypt(sessionKey, encrypted);
        } catch (Exception e) {
            LOGGER.warn("[ODC] 云缓存读取失败 {}: {}", path, e.toString());
            return null;
        }
    }

    /**
     * 页面纹理引用 → 云缓存字节。渲染层 drawImage 拿不到资源包里的图时兜底。
     * 认三种写法：opendreamcore:textures/x.png、textures/x.png、x.png，
     * 统一折算成相对路径（服务器 resources/ 目录结构一致）再去缓存找。
     */
    public static byte[] textureBytes(String texture) {
        if (texture == null || texture.isEmpty() || !synced) {
            return null;
        }
        String s = texture.trim().replace('\\', '/');
        if (s.startsWith("minecraft:")) {
            return null;
        }
        int colon = s.indexOf(':');
        if (colon >= 0) {
            s = s.substring(colon + 1);
        }
        while (s.startsWith("/")) {
            s = s.substring(1);
        }
        if (s.startsWith("assets/")) {
            s = s.substring("assets/".length());
        }
        if (s.startsWith("opendreamcore/")) {
            s = s.substring("opendreamcore/".length());
        }
        if (!s.startsWith("textures/")) {
            s = "textures/" + s;
        }
        return loadCached(s);
    }

    /** 防路径穿越 + 哈希命名：有 key 时文件名=SHA-256(key∥路径)，无 key 回退原路径。 */
    private static Path safeResolve(String path) {
        Path dir = cacheDir();
        Path resolved;
        if (sessionKey == null || sessionKey.length == 0) {
            resolved = dir.resolve(path).normalize();
        } else {
            String name = com.opendreamcore.visual.CacheNames.forPath(sessionKey, path);
            int slash = path.lastIndexOf('/');
            String dirPart = slash >= 0 ? path.substring(0, slash) : "";
            resolved = (dirPart.isEmpty() ? dir : dir.resolve(dirPart))
                    .resolve(name).normalize();
        }
        if (!resolved.startsWith(dir)) {
            throw new IllegalArgumentException("非法资源路径: " + path);
        }
        return resolved;
    }
}
