package com.opendreamcore.plugin.cloud;

import com.opendreamcore.plugin.OpenDreamCorePlugin;
import com.opendreamcore.plugin.network.ProtocolListener;
import com.opendreamcore.protocol.CloudCacheValidator;
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
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 云资源管理器：plugins/OpenDreamCore/resources/ 目录的清单构建与差异下发。
 * 文件内容 AES-GCM 加密传输（每玩家独立 key，随 ready_ack 下发）。
 */
public final class CloudResourceManager {

    /** CloudFile 的协议包上限为 1 MiB；AES-GCM 额外包含 12 字节 IV 和 16 字节 tag。 */
    public static final int MAX_FILE_BYTES = (1 << 20) - 28;
    public static final int MAX_FILES = 4096;
    public static final long MAX_TOTAL_BYTES = 256L * 1024L * 1024L;
    /** 与清单文件数上限一致，兼容客户端一次提交完整差异清单。 */
    public static final int MAX_REQUEST_PATHS = MAX_FILES;
    private static final int MAX_PATH_LENGTH = 256;
    private static final int QUEUE_CAPACITY = 8;

    private final OpenDreamCorePlugin plugin;
    private volatile ProtocolListener network;
    private final Path resourceDir;
    private final ConcurrentMap<Player, byte[]> sessionKeys = new ConcurrentHashMap<>();
    private final java.util.concurrent.ThreadPoolExecutor ioExecutor;
    private volatile boolean stopped;

    public CloudResourceManager(OpenDreamCorePlugin plugin) {
        this.plugin = plugin;
        this.resourceDir = plugin.getDataFolder().toPath().toAbsolutePath().normalize().resolve("resources");
        this.ioExecutor = new java.util.concurrent.ThreadPoolExecutor(1, 1, 30L,
                java.util.concurrent.TimeUnit.SECONDS,
                new java.util.concurrent.ArrayBlockingQueue<Runnable>(QUEUE_CAPACITY),
                new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
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

    /** 在有界后台线程扫描并哈希，完成后回到 Bukkit 主线程发送清单。 */
    public void buildManifest(final Player player, final java.util.function.Consumer<CloudManifest> callback) {
        if (player == null || callback == null || stopped) {
            return;
        }
        final byte[] sessionKey = sessionKeys.get(player);
        if (sessionKey == null) {
            return;
        }
        try {
            ioExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    final CloudManifest manifest = scanManifest();
                    dispatchMain(new Runnable() {
                        @Override
                        public void run() {
                            if (isCurrentSession(player, sessionKey)) {
                                callback.accept(manifest);
                            }
                        }
                    });
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException e) {
            plugin.getLogger().warning("云资源清单任务队列已满");
        }
    }

    /** 响应差异请求：后台读取/加密，主线程只做受会话保护的发包。 */
    public void sendDiff(final Player player, List<String> paths) {
        final byte[] key = sessionKeys.get(player);
        if (key == null) {
            plugin.getLogger().warning("玩家没有会话 key: " + player.getName());
            return;
        }
        if (paths == null || paths.size() > MAX_REQUEST_PATHS) {
            plugin.getLogger().warning("云资源差异请求数量超限: " + player.getName());
            dispatchDone(player, key, 0);
            return;
        }
        final List<String> requested = new ArrayList<String>();
        java.util.HashSet<String> unique = new java.util.HashSet<String>();
        for (String path : paths) {
            if (CloudCacheValidator.isSafeRelativePath(path) && path.length() <= MAX_PATH_LENGTH && unique.add(path)) {
                requested.add(path);
            }
        }
        try {
            ioExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    int sent = 0;
                    long totalBytes = 0L;
                    for (String path : requested) {
                        if (stopped || !isCurrentSession(player, key)) {
                            return;
                        }
                        byte[] data = readBounded(path);
                        if (data == null || totalBytes > MAX_TOTAL_BYTES - data.length) {
                            if (data != null) {
                                plugin.getLogger().warning("云资源差异总量超过上限: " + player.getName());
                            }
                            continue;
                        }
                        totalBytes += data.length;
                        final byte[] encrypted;
                        try {
                            encrypted = Crypto.encrypt(key, data);
                        } catch (RuntimeException e) {
                            plugin.getLogger().warning("资源加密失败 " + path + ": " + e);
                            continue;
                        }
                        final CloudFile file;
                        try {
                            file = new CloudFile(path, encrypted);
                        } catch (RuntimeException e) {
                            plugin.getLogger().warning("资源路径不在允许名单: " + path);
                            continue;
                        }
                        ++sent;
                        dispatchMain(new Runnable() {
                            @Override
                            public void run() {
                                if (isCurrentSession(player, key) && network != null) {
                                    network.send(player, com.opendreamcore.protocol.Protocol.CLOUD_FILE, file);
                                }
                            }
                        });
                    }
                    final int totalSent = sent;
                    dispatchMain(new Runnable() {
                        @Override
                        public void run() {
                            if (isCurrentSession(player, key) && network != null) {
                                network.send(player, com.opendreamcore.protocol.Protocol.CLOUD_DONE, new CloudDone());
                                plugin.getLogger().info("云资源差异下发 " + player.getName() + ": " + totalSent + " 个文件");
                            }
                        }
                    });
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException e) {
            plugin.getLogger().warning("云资源下发任务队列已满: " + player.getName());
            dispatchDone(player, key, 0);
        }
    }

    /** 删除会话（玩家离线）。 */
    public void removeSession(Player player) {
        sessionKeys.remove(player);
    }

    /** 删除所有会话（插件停用）。 */
    public void clearSessions() {
        sessionKeys.clear();
    }

    /** 插件停用时终止后台读盘/加密任务，避免旧任务在换服后发包。 */
    public void shutdown() {
        stopped = true;
        clearSessions();
        ioExecutor.shutdownNow();
        network = null;
    }

    private CloudManifest scanManifest() {
        List<CloudManifest.Entry> entries = new ArrayList<CloudManifest.Entry>();
        Path root;
        try {
            root = realRoot();
        } catch (IOException e) {
            return new CloudManifest(entries);
        }
        if (root == null) {
            return new CloudManifest(entries);
        }
        long total = 0L;
        try (java.util.stream.Stream<Path> stream = Files.walk(root)) {
            java.util.Iterator<Path> iterator = stream.iterator();
            while (iterator.hasNext()) {
                Path file = iterator.next();
                if (entries.size() >= MAX_FILES || total >= MAX_TOTAL_BYTES) {
                    plugin.getLogger().warning("云资源清单达到数量或总量上限");
                    break;
                }
                if (Files.isSymbolicLink(file) || !Files.isRegularFile(file, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                String relative = root.relativize(file).toString().replace('\\', '/');
                if (!validRelativePath(relative)) {
                    continue;
                }
                Path real;
                try {
                    real = file.toRealPath();
                } catch (IOException e) {
                    continue;
                }
                if (!real.startsWith(root) || Files.isSymbolicLink(file)) {
                    continue;
                }
                FileInfo info = hashFile(real, MAX_FILE_BYTES);
                if (info == null || total > MAX_TOTAL_BYTES - info.size) {
                    continue;
                }
                entries.add(new CloudManifest.Entry(relative, info.size, info.hash));
                total += info.size;
            }
        } catch (IOException e) {
            plugin.getLogger().warning("资源目录扫描失败: " + e);
        }
        entries.sort((a, b) -> a.path().compareTo(b.path()));
        return new CloudManifest(entries);
    }

    private byte[] readBounded(String path) {
        Path file = safeFile(path);
        if (file == null) {
            plugin.getLogger().warning("资源路径不在允许名单: " + path);
            return null;
        }
        try {
            long size = Files.size(file);
            if (size > MAX_FILE_BYTES) {
                plugin.getLogger().warning("资源文件超过协议上限: " + path);
                return null;
            }
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream((int) size);
            try (java.io.InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                long total = 0L;
                while ((read = input.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_FILE_BYTES) {
                        return null;
                    }
                    out.write(buffer, 0, read);
                }
                if (total != size) {
                    plugin.getLogger().warning("资源文件在读取时发生变化: " + path);
                    return null;
                }
            }
            return out.toByteArray();
        } catch (IOException e) {
            plugin.getLogger().warning("资源下发失败 " + path + ": " + e);
            return null;
        }
    }

    private FileInfo hashFile(Path file, long limit) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long size = 0L;
            byte[] buffer = new byte[8192];
            try (java.io.InputStream input = Files.newInputStream(file)) {
                int read;
                while ((read = input.read(buffer)) != -1) {
                    size += read;
                    if (size > limit) {
                        return null;
                    }
                    digest.update(buffer, 0, read);
                }
            }
            return new FileInfo(size, hex(digest.digest()));
        } catch (IOException e) {
            plugin.getLogger().warning("清单读取失败 " + file + ": " + e);
            return null;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private Path realRoot() throws IOException {
        if (!Files.isDirectory(resourceDir, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            return null;
        }
        return resourceDir.toRealPath();
    }

    private Path safeFile(String path) {
        if (!validRelativePath(path)) {
            return null;
        }
        try {
            Path root = realRoot();
            if (root == null) {
                return null;
            }
            Path candidate = root.resolve(path).normalize();
            if (!candidate.startsWith(root)) {
                return null;
            }
            Path current = root;
            for (Path part : root.relativize(candidate)) {
                current = current.resolve(part);
                if (Files.isSymbolicLink(current)) {
                    return null;
                }
            }
            if (!Files.isRegularFile(candidate, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                return null;
            }
            Path real = candidate.toRealPath();
            return real.startsWith(root) ? real : null;
        } catch (IOException e) {
            return null;
        }
    }

    private static boolean validRelativePath(String path) {
        return path != null && path.length() <= MAX_PATH_LENGTH
                && CloudCacheValidator.isSafeRelativePath(path);
    }


    private boolean isCurrentSession(Player player) {
        return !stopped && player != null && sessionKeys.get(player) != null;
    }

    private boolean isCurrentSession(Player player, byte[] key) {
        return isCurrentSession(player) && sessionKeys.get(player) == key;
    }

    private void dispatchDone(final Player player, final byte[] key, final int sent) {
        dispatchMain(new Runnable() {
            @Override
            public void run() {
                if (isCurrentSession(player, key) && network != null) {
                    network.send(player, com.opendreamcore.protocol.Protocol.CLOUD_DONE, new CloudDone());
                    if (sent > 0) {
                        plugin.getLogger().info("云资源差异下发 " + player.getName() + ": " + sent + " 个文件");
                    }
                }
            }
        });
    }

    private void dispatchMain(Runnable task) {
        if (stopped) {
            return;
        }
        try {
            plugin.getServer().getScheduler().runTask(plugin, task);
        } catch (RuntimeException e) {
            if (!stopped) {
                plugin.getLogger().warning("云资源主线程任务投递失败: " + e);
            }
        }
    }

    private static final class FileInfo {
        private final long size;
        private final String hash;

        private FileInfo(long size, String hash) {
            this.size = size;
            this.hash = hash;
        }
    }

    /** Java17 才有 HexFormat，手工转 hex。 */
    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
