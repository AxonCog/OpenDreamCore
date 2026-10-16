package com.opendreamcore.plugin.server.resource;

import com.opendreamcore.plugin.OpenDreamCorePlugin;
import com.opendreamcore.plugin.network.CustomPacketRegistry;
import com.opendreamcore.protocol.CloudCacheValidator;
import com.opendreamcore.protocol.LegacyFraming;
import com.opendreamcore.protocol.OdcByteArrayBuf;
import com.opendreamcore.protocol.Protocol;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;

/**
 * 服务端资源云：扫描资源目录，按清单向客户端补发、删除和确认资源。
 */
public final class ServerResourcePipeline {

    private static final int CHUNK_BYTES = 48 * 1024;
    private static final int MAX_REPORT_BASE64_CHARS = OdcByteArrayBuf.MAX_STRING_LEN;
    private static final int MAX_REPORT_COMPRESSED_BYTES = MAX_REPORT_BASE64_CHARS / 4 * 3;
    private static final int MAX_REPORT_BYTES = LegacyFraming.MAX_ASSEMBLED_PAYLOAD;
    private static final int MAX_REPORT_FILES = Protocol.MAX_PACKET_BYTES / (1 + 1 + 32 + 1);
    private static final int MAX_REPORT_NAME_CHARS = Protocol.MAX_PACKET_BYTES / 256;

    private final OpenDreamCorePlugin plugin;
    private Path root;
    private String password;
    private volatile boolean enabled = true;
    private Map<String, Entry> disk = java.util.Collections.emptyMap();
    private final Map<String, Entry> injected = new ConcurrentHashMap<>();
    private volatile Map<String, Entry> files = java.util.Collections.emptyMap();
    private final ConcurrentMap<String, Player> syncing = new ConcurrentHashMap<>();

    private static final class Entry {
        final String md5;
        final Path path;
        final byte[] data;
        Entry(String md5, Path path, byte[] data) {
            this.md5 = md5;
            this.path = path;
            this.data = data;
        }
    }

    public ServerResourcePipeline(OpenDreamCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void configure() {
        String dir = plugin.getConfig().getString("resource-pack", "");
        Path configured = dir == null || dir.trim().isEmpty()
                ? plugin.getDataFolder().getParentFile().toPath().resolve("OpenDreamCoreResource")
                : java.nio.file.Paths.get(dir.trim());
        this.root = configured;
        this.password = plugin.getConfig().getString("resource-password", "OpenDreamCore");
        this.enabled = plugin.getConfig().getBoolean("resource-sync", true);
    }

    public Path root() {
        return root;
    }

    public int size() {
        return files.size();
    }

    public void putResource(String name, byte[] data) {
        if (name == null || data == null || data.length == 0) {
            return;
        }
        injected.put(normalize(name), new Entry(md5Hex(data), null, data));
        rebuild();
        resyncAll();
    }

    public void putResourceFile(String name, Path file) {
        if (name == null || file == null || !Files.isRegularFile(file)) {
            return;
        }
        injected.put(normalize(name), new Entry(md5(file), file, null));
        rebuild();
        resyncAll();
    }

    public void removeResource(String name) {
        if (name == null) {
            return;
        }
        injected.remove(normalize(name));
        rebuild();
        resyncAll();
    }

    public Set<String> resourceNames() {
        return new LinkedHashSet<>(files.keySet());
    }

    public byte[] getResource(String name) {
        Entry e = files.get(name == null ? null : normalize(name));
        if (e == null) {
            return null;
        }
        if (e.data != null) {
            return e.data;
        }
        try {
            return Files.readAllBytes(e.path);
        } catch (IOException ex) {
            return null;
        }
    }

    private static String normalize(String name) {
        return name.replace('\\', '/').trim();
    }

    private void rebuild() {
        Map<String, Entry> merged = new LinkedHashMap<>(disk);
        merged.putAll(injected);
        files = merged;
    }

    public void resyncAll() {
        if (!enabled) {
            return;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (plugin.networkLayer().isReady(p)) {
                startSync(p);
            }
        }
    }

    public synchronized void reload() {
        try {
            Files.createDirectories(root);
            Map<String, Entry> scanned = new LinkedHashMap<>();
            try (Stream<Path> walk = Files.walk(root)) {
                for (Path p : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                    String rel = normalize(root.relativize(p).toString());
                    if (rel.startsWith(".") || rel.contains("/.")) {
                        continue;
                    }
                    scanned.put(rel, new Entry(md5(p), p, null));
                }
            }
            disk = scanned;
            rebuild();
            plugin.getLogger().info("资源云已扫描: " + size() + " 个文件 (" + root + ")");
            resyncAll();
        } catch (IOException | java.io.UncheckedIOException e) {
            plugin.getLogger().warning("资源目录扫描失败，保留上一份清单: " + e.getClass().getSimpleName());
        }
    }

    public void startSync(Player player) {
        if (player == null || !enabled) {
            return;
        }
        send(player, Protocol.CUSTOM_RESOURCE_KEY, password);
    }

    /** 完整校验有界客户端清单后才对账；非法报告不发补丁、删除或完成信号。 */
    public void handleReport(Player player, String gzipped) {
        if (player == null || gzipped == null || gzipped.isEmpty()) {
            return;
        }
        Player busy = syncing.putIfAbsent(player.getUniqueId().toString(), player);
        if (busy != null) {
            return;
        }
        try {
            if (gzipped.length() > MAX_REPORT_BASE64_CHARS) {
                throw new IOException("Base64输入超过协议上限");
            }
            byte[] raw;
            try {
                raw = Base64.getDecoder().decode(gzipped);
            } catch (IllegalArgumentException e) {
                throw new IOException("Base64输入无效");
            }
            Map<String, String> clientMap = parseReport(decompress(raw));
            List<String> needPush = new ArrayList<>();
            for (Map.Entry<String, Entry> e : files.entrySet()) {
                String clientMd5 = clientMap.get(e.getKey());
                if (clientMd5 == null || !clientMd5.equals(e.getValue().md5)) {
                    needPush.add(e.getKey());
                }
            }
            List<String> needClear = new ArrayList<>();
            for (String clientName : clientMap.keySet()) {
                if (!files.containsKey(clientName)) {
                    needClear.add(clientName);
                }
            }
            if (!needClear.isEmpty()) {
                buildClearBatches(needClear);
            }
            pushFiles(player, needPush);
            if (!needClear.isEmpty()) {
                sendClearBatches(player, needClear);
            }
            send(player, Protocol.CUSTOM_RESOURCE_DONE, "");
            Bukkit.getPluginManager().callEvent(new ResourceCloudEvent(
                    player, needPush.size(), needClear.size()));
            if (!needPush.isEmpty() || !needClear.isEmpty()) {
                plugin.getLogger().info("资源同步 " + player.getName()
                        + ": 补 " + needPush.size() + " 删 " + needClear.size());
            }
        } catch (IOException e) {
            plugin.getLogger().warning("资源清单已拒绝 (" + player.getUniqueId() + "): " + e.getMessage());
        } catch (Exception e) {
            plugin.getLogger().warning("资源同步失败 (" + player.getUniqueId() + "): "
                    + e.getClass().getSimpleName());
        } finally {
            syncing.remove(player.getUniqueId().toString());
        }
    }

    private List<String> buildClearBatches(List<String> names) throws IOException {
        List<String> batches = new ArrayList<>();
        StringBuilder batch = new StringBuilder();
        int batchBytes = 0;
        for (String name : names) {
            if (!CloudCacheValidator.isSafeRelativePath(name)) {
                throw new IOException("资源清理路径非法");
            }
            int nameBytes = name.getBytes(StandardCharsets.UTF_8).length;
            if (nameBytes > OdcByteArrayBuf.MAX_STRING_LEN) {
                throw new IOException("资源清理路径超过协议上限");
            }
            int separatorBytes = batchBytes == 0 ? 0 : 1;
            if (batchBytes > 0 && batchBytes + separatorBytes + nameBytes
                    > OdcByteArrayBuf.MAX_STRING_LEN) {
                batches.add(batch.toString());
                batch.setLength(0);
                batchBytes = 0;
                separatorBytes = 0;
            }
            if (batchBytes > 0) {
                batch.append(',');
            }
            batch.append(name);
            batchBytes += separatorBytes + nameBytes;
        }
        if (batchBytes > 0) {
            batches.add(batch.toString());
        }
        return batches;
    }

    private void sendClearBatches(Player player, List<String> names) throws IOException {
        for (String payload : buildClearBatches(names)) {
            send(player, Protocol.CUSTOM_RESOURCE_CLEAR, payload);
        }
    }

    private void pushFiles(Player player, List<String> names) {
        for (String name : names) {
            Entry entry = files.get(name);
            if (entry == null) {
                continue;
            }
            byte[] bytes = entry.data != null ? entry.data : read(entry.path);
            if (bytes == null || bytes.length == 0) {
                continue;
            }
            int total = (bytes.length + CHUNK_BYTES - 1) / CHUNK_BYTES;
            for (int seq = 0; seq < total; seq++) {
                int from = seq * CHUNK_BYTES;
                int len = Math.min(CHUNK_BYTES, bytes.length - from);
                byte[] chunk = new byte[len];
                System.arraycopy(bytes, from, chunk, 0, len);
                String payload = name + "|" + entry.md5 + "|" + seq + "|" + total + "|"
                        + Base64.getEncoder().encodeToString(chunk);
                send(player, Protocol.CUSTOM_RESOURCE_PUSH, payload);
            }
        }
    }

    private static byte[] read(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            return null;
        }
    }

    private void send(Player player, String channel, String payload) {
        if (player.isOnline()) {
            CustomPacketRegistry.send(plugin, player, channel, payload);
        }
    }

    private static Map<String, String> parseReport(String report) throws IOException {
        Map<String, String> out = new LinkedHashMap<>();
        if (report == null || report.isEmpty()) {
            return out;
        }
        int entries = 0;
        int offset = 0;
        while (offset < report.length()) {
            int end = report.indexOf(';', offset);
            if (end < 0) {
                end = report.length();
            }
            if (end > offset) {
                if (++entries > MAX_REPORT_FILES) {
                    throw new IOException("资源清单文件数超过协议上限");
                }
                int comma = report.indexOf(',', offset);
                if (comma <= offset || comma >= end) {
                    throw new IOException("资源清单条目格式无效");
                }
                if (comma - offset > MAX_REPORT_NAME_CHARS || end - comma - 1 != 32) {
                    throw new IOException("资源清单条目长度超过上限");
                }
                String name = report.substring(offset, comma);
                String md5 = report.substring(comma + 1, end);
                if (!CloudCacheValidator.isSafeRelativePath(name) || !isMd5(md5)) {
                    throw new IOException("资源清单条目校验失败");
                }
                out.put(name, md5);
            }
            if (end == report.length()) {
                break;
            }
            offset = end + 1;
        }
        return out;
    }

    private static boolean isMd5(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!(c >= '0' && c <= '9') && !(c >= 'a' && c <= 'f')
                    && !(c >= 'A' && c <= 'F')) {
                return false;
            }
        }
        return true;
    }

    private static String decompress(byte[] data) throws IOException {
        if (data.length > MAX_REPORT_COMPRESSED_BYTES) {
            throw new IOException("GZIP压缩输入超过协议上限");
        }
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        try (var in = new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(data))) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf, 0, Math.min(buf.length, MAX_REPORT_BYTES - bos.size() + 1))) != -1) {
                if (n > MAX_REPORT_BYTES - bos.size()) {
                    throw new IOException("GZIP解压输出超过协议上限");
                }
                bos.write(buf, 0, n);
            }
        } catch (java.util.zip.ZipException e) {
            throw new IOException("GZIP输入无效");
        } catch (java.io.EOFException e) {
            throw new IOException("GZIP输入不完整");
        }
        return bos.toString("UTF-8");
    }

    private static String md5(Path file) {
        byte[] data;
        try {
            data = Files.readAllBytes(file);
        } catch (IOException e) {
            return "";
        }
        return md5Hex(data);
    }

    private static String md5Hex(byte[] data) {
        try {
            var d = java.security.MessageDigest.getInstance("MD5");
            byte[] h = d.digest(data);
            StringBuilder sb = new StringBuilder(32);
            for (byte b : h) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}