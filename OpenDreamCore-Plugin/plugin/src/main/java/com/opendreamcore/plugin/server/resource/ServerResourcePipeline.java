package com.opendreamcore.plugin.server.resource;

import com.opendreamcore.plugin.OpenDreamCorePlugin;
import com.opendreamcore.plugin.network.CustomPacketRegistry;
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
 * 服务端资源云——把资源文件夹变成玩家眼里的贴图/音效，顺手给附属插件开了整扇门。
 *
 * 服主视角：往 plugins/OpenDreamCoreResource/ 丢贴图音频（随便套文件夹），
 * 玩家进服自动拿到、本地加密缓存、下次进服只比 MD5，缺啥补啥多啥删啥。
 *
 * 附属插件视角（这是 API，比丢文件夹更硬核）：
 *   OpenDreamCorePlugin.get().resourcePipeline().putResource("icon.png", bytes) // 内存注入
 *   resourcePipeline().putResourceFile("bgm.ogg", file)                        // 塞磁盘文件
 *   resourcePipeline().removeResource("icon.png")                             // 撤下
 *   resourcePipeline().resourceNames() / getResource(name)                     // 随手翻
 *   put 之后在线玩家立刻进对账，缺这文件的当场补上；收工还会亮 {@link ResourceCloudEvent}。
 *
 * 干活的流程：
 *   1. 建文件夹：没有就建（资源夹直接放进来就进云）；
 *   2. 扫文件夹：摊成 相对路径 → MD5 清单（字节懒读，不占内存）；
 *   3. 发钥匙：玩家 ready 或资源变化后，把加密密码当"开始对账"信号丢过去；
 *   4. 收清单：玩家把本地缓存 (名字,MD5) 发上来（GZIP 压过）；
 *   5. 对账：缺的/变了的补发，多的让删，最后一声"收工"让客户端把资源挂上。
 *
 * 大实话：传输走 custom_packet 的保留通道（Protocol.CUSTOM_RESOURCE_*），分片是怕把
 * Bukkit 单报文撑死；密码从 config.yml 读（resource-password），只是给本机缓存上把锁。
 */
public final class ServerResourcePipeline {

    private static final int CHUNK_BYTES = 48 * 1024;

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
    }    //
    // 附属插件公开 API：塞进来就能被全网玩家拿到
    //

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
            } catch (IOException e) {
                plugin.getLogger().warning("资源文件夹扫描翻车: " + e);
            }
            disk = scanned;
            rebuild();
            plugin.getLogger().info("资源云已扫: " + size() + " 个文件 (" + root + ")");
            resyncAll();
        } catch (IOException e) {
            plugin.getLogger().warning("资源根目录建不起来: " + e);
        }
    }

    public void startSync(Player player) {
        if (player == null || !enabled) {
            return;
        }
        send(player, Protocol.CUSTOM_RESOURCE_KEY, password);
    }    public void handleReport(Player player, String gzipped) {
        if (player == null || gzipped == null || gzipped.isEmpty()) {
            return;
        }
        Player busy = syncing.putIfAbsent(player.getUniqueId().toString(), player);
        if (busy != null) {
            return;
        }
        try {
            byte[] raw = Base64.getDecoder().decode(gzipped);
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
            pushFiles(player, needPush);
            if (!needClear.isEmpty()) {
                send(player, Protocol.CUSTOM_RESOURCE_CLEAR, String.join(",", needClear));
            }
            send(player, Protocol.CUSTOM_RESOURCE_DONE, "");
            Bukkit.getPluginManager().callEvent(new ResourceCloudEvent(
                    player, needPush.size(), needClear.size()));
            if (!needPush.isEmpty() || !needClear.isEmpty()) {
                plugin.getLogger().info("资源同步 " + player.getName()
                        + ": 补 " + needPush.size() + " 删 " + needClear.size());
            }
        } catch (Exception e) {
            plugin.getLogger().warning("资源清单解析失败 (" + player.getName() + "): " + e);
        } finally {
            syncing.remove(player.getUniqueId().toString());
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

    private static Map<String, String> parseReport(String report) {
        Map<String, String> out = new LinkedHashMap<>();
        if (report == null || report.isEmpty()) {
            return out;
        }
        for (String pair : report.split(";")) {
            if (pair.isEmpty()) {
                continue;
            }
            String[] kv = pair.split(",", 2);
            if (kv.length == 2) {
                out.put(kv[0], kv[1]);
            }
        }
        return out;
    }

    private static String decompress(byte[] data) throws IOException {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        try (var in = new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(data))) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                bos.write(buf, 0, n);
            }
        }
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
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
            byte[] hash = d.digest(data);
            StringBuilder sb = new StringBuilder(32);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}