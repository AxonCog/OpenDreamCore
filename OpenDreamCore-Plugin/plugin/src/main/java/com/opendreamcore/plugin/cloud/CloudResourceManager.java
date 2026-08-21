package com.opendreamcore.plugin.cloud;

import com.opendreamcore.plugin.OpenDreamCorePlugin;
import com.opendreamcore.plugin.network.ProtocolListener;
import com.opendreamcore.protocol.Crypto;
import com.opendreamcore.protocol.message.CloudDone;
import com.opendreamcore.protocol.message.CloudFile;
import com.opendreamcore.protocol.message.CloudManifest;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 云资源管理器：plugins/OpenDreamCore/resources/ 目录的清单构建与差异下发。
 * 文件内容 AES-GCM 加密传输（每玩家独立 key，随 ready_ack 下发）。
 */
public final class CloudResourceManager {

    private final OpenDreamCorePlugin plugin;
    private ProtocolListener network;
    private final Path resourceDir;
    private final ConcurrentMap<Player, byte[]> sessionKeys = new ConcurrentHashMap<>();

    public CloudResourceManager(OpenDreamCorePlugin plugin) {
        this.plugin = plugin;
        this.resourceDir = plugin.getDataFolder().toPath().resolve("resources");
    }

    /** 网络层就绪后挂上（发消息用）。 */
    public void attach(ProtocolListener network) {
        this.network = network;
    }

    /** 生成玩家会话 key（ready_ack 里下发）。 */
    public byte[] newSessionKey(Player player) {
        byte[] key = Crypto.randomKey();
        sessionKeys.put(player, key);
        return key;
    }

    /** 玩家会话 key（页面加密下发用；无则 null）。 */
    public byte[] keyOf(Player player) {
        return sessionKeys.get(player);
    }

    /** 构建清单（资源目录全文件）。目录不存在返回空清单。 */
    public CloudManifest buildManifest() {
        List<CloudManifest.Entry> entries = new ArrayList<>();
        if (!Files.isDirectory(resourceDir)) {
            return new CloudManifest(entries);
        }
        try (var stream = Files.walk(resourceDir)) {
            stream.filter(Files::isRegularFile).forEach(file -> {
                String path = resourceDir.relativize(file).toString().replace('\\', '/');
                try {
                    byte[] data = Files.readAllBytes(file);
                    entries.add(new CloudManifest.Entry(path, data.length, sha256(data)));
                } catch (IOException e) {
                    plugin.getLogger().warning("清单读取失败 " + path + ": " + e);
                }
            });
        } catch (IOException e) {
            plugin.getLogger().warning("资源目录扫描失败: " + e);
        }
        entries.sort((a, b) -> a.path().compareTo(b.path()));
        return new CloudManifest(entries);
    }

    /** 响应差异请求：逐文件加密下发，最后 cloud_done。 */
    public void sendDiff(Player player, List<String> paths) {
        byte[] key = sessionKeys.get(player);
        if (key == null) {
            plugin.getLogger().warning("玩家没有会话 key: " + player.getName());
            return;
        }
        int sent = 0;
        for (String path : paths) {
            Path file = resourceDir.resolve(path).normalize();
            if (!file.startsWith(resourceDir) || !Files.isRegularFile(file)) {
                continue;
            }
            try {
                byte[] data = Files.readAllBytes(file);
                network.send(player, com.opendreamcore.protocol.Protocol.CLOUD_FILE,
                        new CloudFile(path, Crypto.encrypt(key, data)));
                sent++;
            } catch (IOException e) {
                plugin.getLogger().warning("资源下发失败 " + path + ": " + e);
            }
        }
        network.send(player, com.opendreamcore.protocol.Protocol.CLOUD_DONE, new CloudDone());
        plugin.getLogger().info("云资源差异下发 " + player.getName() + ": " + sent + " 个文件");
    }

    /** 删除会话（玩家离线）。 */
    public void removeSession(Player player) {
        sessionKeys.remove(player);
    }

    private static String sha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
