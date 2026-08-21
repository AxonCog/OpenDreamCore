package com.opendreamcore.client;

import com.mojang.logging.LogUtils;
import com.opendreamcore.protocol.Crypto;
import com.opendreamcore.protocol.message.CloudDelete;
import com.opendreamcore.protocol.message.CloudDiff;
import com.opendreamcore.protocol.message.CloudDone;
import com.opendreamcore.protocol.message.CloudFile;
import com.opendreamcore.protocol.message.CloudManifest;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * 客户端云资源同步：收清单 → 对比本地缓存（游戏目录 OpenDreamCore/cache/）→
 * 差异回请求 → 收加密文件原样落盘（不解密，使用时在内存解密）→ 完成标记。
 * 会话 key 来自 ready_ack（空 = 未加密，直写）。
 * 缓存文件为加密态，不可逆向。
 */
public final class CloudSyncClient {

    public static final Logger LOGGER = LogUtils.getLogger();

    private byte[] sessionKey = new byte[0];
    private boolean synced;

    public CloudSyncClient() {
    }

    /** 缓存目录：游戏目录/OpenDreamCore/cache（加密态文件，不可逆向）。 */
    public Path cacheDir() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("OpenDreamCore").resolve("cache");
    }

    /** ready_ack 到达时记录会话 key。 */
    public void onReadyAck(byte[] key) {
        sessionKey = key == null ? new byte[0] : key;
        synced = false;
    }

    /** 清单到达：对比本地缓存（只检查存在性；加密态文件每次 key 不同密文不同，不对比 hash），差异走 cloud_diff。 */
    public void handleManifest(CloudManifest manifest) {
        try {
            Files.createDirectories(cacheDir());
        } catch (IOException e) {
            LOGGER.warn("云缓存目录创建失败: {}", e.toString());
            return;
        }
        List<String> missing = new ArrayList<>();
        for (CloudManifest.Entry entry : manifest.entries()) {
            Path file = safeResolve(entry.path());
            if (!Files.isRegularFile(file)) {
                missing.add(entry.path());
            }
        }
        if (missing.isEmpty()) {
            synced = true;
            LOGGER.info("云资源已是最新（{} 个文件）", manifest.entries().size());
            return;
        }
        LOGGER.info("云资源差异 {} 个文件，开始拉取", missing.size());
        var buf = new com.opendreamcore.protocol.OdcByteArrayBuf();
        new CloudDiff(missing).encode(buf);
        ClientController.get().sendRaw(com.opendreamcore.protocol.Protocol.CLOUD_DIFF, buf.toByteArray());
    }

    /** 文件到达：加密态原样落盘（不解析、不解密），使用时在内存解密。 */
    public void handleFile(CloudFile file) {
        try {
            Path target = safeResolve(file.path());
            Files.createDirectories(target.getParent());
            Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
            Files.write(tmp, file.encrypted());
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            LOGGER.warn("云文件落盘失败 {}: {}", file.path(), e.toString());
        }
    }

    /** 删除：清单里消失的文件同步删本地。 */
    public void handleDelete(CloudDelete delete) {
        for (String path : delete.paths()) {
            try {
                Files.deleteIfExists(safeResolve(path));
            } catch (IOException e) {
                LOGGER.warn("云文件删除失败 {}: {}", path, e.toString());
            }
        }
    }

    public void handleDone(CloudDone done) {
        synced = true;
        LOGGER.info("云资源同步完成");
    }

    public boolean isSynced() {
        return synced;
    }

    /** 从缓存加载并解密资源到内存（使用时调用；加密态文件 → 内存明文）。 */
    public byte[] loadCached(String path) {
        try {
            byte[] encrypted = Files.readAllBytes(safeResolve(path));
            if (sessionKey.length == 0) {
                return encrypted;
            }
            return Crypto.decrypt(sessionKey, encrypted);
        } catch (Exception e) {
            LOGGER.warn("云缓存读取失败 {}: {}", path, e.toString());
            return null;
        }
    }

    /** 防路径穿越：只允许落在缓存目录内。 */
    private Path safeResolve(String path) {
        Path resolved = cacheDir().resolve(path).normalize();
        if (!resolved.startsWith(cacheDir())) {
            throw new IllegalArgumentException("非法资源路径: " + path);
        }
        return resolved;
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
