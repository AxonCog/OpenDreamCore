package com.opendreamcore.client;

import net.minecraft.client.Minecraft;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPOutputStream;

/**
 * 客户端资源缓存——服务端资源云的收货仓库（说人话：网盘客户端）。
 *
 * 服务端把 OpenDreamCoreResource 文件夹摊成云，咱这边把云下回来的文件
 * 加密摆进 游戏目录/ResourceCache/：
 *   - .data   文件真身，AES-GCM 加密（钥匙是服务端发来的密码派生的），防拷包；
 *   - .data1  两行小抄：第一行资源名、第二行 MD5。扫缓存全靠它。
 *
 * 走流程全家桶：
 *   1. resource_key（密码）→ 记住密码 → 把本地缓存清单报上去（名字,md5 串 GZIP+base64）；
 *   2. resource_push（分片：名字|md5|序号/总数|base64块）→ 一块块攒，攒齐解密写盘；
 *      图片直接进纹理不落明文，音频/gif 收工后端到工作区；
 *   3. resource_clear（过期名单）→ 按名删除；
 *   4. resource_done（收工）→ 把攒下的音频挂进播放表，回头谁喊谁响。
 *
 * 分片坏一半咋办？写盘前有 MD5 兜底，对不上整条作废；服务端下轮对账自己会发现差，
 * 真没救就重进一次，缓存会自愈。
 */
public final class ResourceCacheStore {

    private static final ResourceCacheStore INSTANCE = new ResourceCacheStore();

    public static ResourceCacheStore get() {
        return INSTANCE;
    }

    private ResourceCacheStore() {
    }

    private Path cacheDir;
    private volatile String password = "";
    private final Map<String, Path> index = new ConcurrentHashMap<>();
    private final Map<String, Chunks> assembling = new ConcurrentHashMap<>();
    private final List<String> pendingAudio = new ArrayList<>();

    private static final class Chunks {
        final int total;
        final byte[][] parts;
        int received;
        Chunks(int total) {
            this.total = total;
            this.parts = new byte[total][];
        }
    }

    public void init(Path gameDir) {
        this.cacheDir = gameDir.resolve("ResourceCache");
        index.clear();
        assembling.clear();
        pendingAudio.clear();
        try {
            Files.createDirectories(cacheDir);
        } catch (Exception ignored) {
        }
        reindex();
    }

    private void reindex() {
        index.clear();
        if (cacheDir == null || !Files.isDirectory(cacheDir)) {
            return;
        }
        try (var stream = Files.list(cacheDir)) {
            for (Path p : (Iterable<Path>) stream.filter(Files::isRegularFile)::iterator) {
                if (!p.getFileName().toString().endsWith(".data1")) {
                    continue;
                }
                String name = readMeta(p);
                if (name != null) {
                    Path dataFile = cacheDir.resolve(p.getFileName().toString()
                            .replace(".data1", ".data"));
                    if (Files.isRegularFile(dataFile)) {
                        index.put(name, dataFile);
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static String readMeta(Path metaFile) {
        try {
            List<String> lines = Files.readAllLines(metaFile, StandardCharsets.UTF_8);
            return lines.isEmpty() ? null : lines.get(0);
        } catch (Exception e) {
            return null;
        }
    }    //
    // 通道入场
    //

    public void receiveKey(String key) {
        if (key == null || key.isEmpty()) {
            key = "";
        }
        this.password = key;
        report();
    }

    public void report() {
        reindex();
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Path> e : index.entrySet()) {
            if (sb.length() > 0) {
                sb.append(';');
            }
            sb.append(e.getKey()).append(',').append(md5Of(e.getValue()));
        }
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             GZIPOutputStream gz = new GZIPOutputStream(bos)) {
            gz.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            gz.finish();
            String payload = Base64.getEncoder().encodeToString(bos.toByteArray());
            com.opendreamcore.client.ClientController.get().sendCustomPacket(
                    com.opendreamcore.protocol.Protocol.CUSTOM_RESOURCE_REPORT, payload);
        } catch (Exception ignored) {
        }
    }

    public void receivePush(String chunk) {
        if (chunk == null) {
            return;
        }
        ensureDir();
        String[] parts = chunk.split("\\|", 5);
        if (parts.length < 5) {
            return;
        }
        String name = parts[0];
        String md5 = parts[1];
        int seq;
        int total;
        try {
            seq = Integer.parseInt(parts[2]);
            total = Integer.parseInt(parts[3]);
        } catch (NumberFormatException e) {
            return;
        }
        if (seq < 0 || total <= 0 || seq >= total) {
            return;
        }
        byte[] piece;
        try {
            piece = Base64.getDecoder().decode(parts[4]);
        } catch (IllegalArgumentException e) {
            return;
        }
        Chunks holder = assembling.computeIfAbsent(name, k -> new Chunks(total));
        if (holder.total != total || holder.received > total) {
            assembling.remove(name);
            return;
        }
        if (holder.parts[seq] != null) {
            return;
        }
        holder.parts[seq] = piece;
        holder.received++;
        if (holder.received < total) {
            return;
        }
        assembling.remove(name);
        byte[] whole = concat(holder.parts, total);
        if (md5 != null && !md5.equals(md5Hex(whole))) {
            return;
        }
        store(name, whole);
    }

    public void receiveClear(String names) {
        if (names == null || names.isEmpty()) {
            return;
        }
        for (String name : names.split(",")) {
            Path meta = metaOf(name);
            if (meta != null) {
                try {
                    Files.deleteIfExists(meta);
                } catch (Exception ignored) {
                }
            }
            Path data = dataOf(name);
            if (data != null) {
                try {
                    Files.deleteIfExists(data);
                } catch (Exception ignored) {
                }
            }
            index.remove(name);
        }
    }

    public void receiveDone() {
        for (String name : pendingAudio) {
            Path plain = materialize(name);
            if (plain != null) {
                com.opendreamcore.client.visual.VisualSoundStore.registerExternal(name, plain);
            }
        }
        pendingAudio.clear();
    }    //
    // 落盘与解锁
    //

    private void store(String name, byte[] plain) {
        if (name == null || plain == null || plain.length == 0) {
            return;
        }
        if (isPlainImage(name) || isGif(name)) {
            // gif 双挂：首帧注册成纹理（字符替换/图标拿它当静态图），帧动画走 GifPlayer
            com.opendreamcore.client.resources.LooseResourceLoader.registerBytes(name, plain);
        }
        if (isMedia(name)) {
            pendingAudio.add(name);
        }
        byte[] encrypted = com.opendreamcore.protocol.Crypto.encrypt(keyOf(), plain);
        try {
            Path data = ensureDir().resolve(safeName(name) + ".data");
            Files.write(data, encrypted);
            Path meta = ensureDir().resolve(safeName(name) + ".data1");
            Files.write(meta, (name + "\n" + md5Hex(plain)).getBytes(StandardCharsets.UTF_8));
            index.put(name, data);
        } catch (Exception ignored) {
        }
    }

    public byte[] readDecrypted(String name) {
        Path data = index.get(name);
        if (data == null) {
            return null;
        }
        try {
            return com.opendreamcore.protocol.Crypto.decrypt(keyOf(), Files.readAllBytes(data));
        } catch (Exception e) {
            return null;
        }
    }

    private Path materialize(String name) {
        byte[] plain = readDecrypted(name);
        if (plain == null) {
            return null;
        }
        try {
            Path work = Minecraft.getInstance().gameDirectory.toPath().resolve("OpenDreamCore");
            if (isAudio(name)) {
                Path sndRoot = work.resolve("sounds");
                Files.createDirectories(sndRoot);
                Path out = sndRoot.resolve(shortName(name));
                Files.write(out, plain);
                return out;
            }
            if (isGif(name)) {
                Files.createDirectories(work);
                Path out = work.resolve(shortName(name));
                Files.write(out, plain);
                return out;
            }
            Path dir = ensureDir().resolve("plain");
            Files.createDirectories(dir);
            Path out = dir.resolve(safeName(name));
            Files.write(out, plain);
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    private static String shortName(String name) {
        String n = name.replace('\\', '/');
        int slash = n.lastIndexOf('/');
        return slash >= 0 ? n.substring(slash + 1) : n;
    }

    private static byte[] concat(byte[][] parts, int total) {
        int size = 0;
        for (byte[] p : parts) {
            size += p == null ? 0 : p.length;
        }
        byte[] out = new byte[size];
        int pos = 0;
        for (byte[] p : parts) {
            if (p == null) {
                continue;
            }
            System.arraycopy(p, 0, out, pos, p.length);
            pos += p.length;
        }
        return out;
    }

    private byte[] keyOf() {
        try {
            var d = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = d.digest((password + "odc-cache").getBytes(StandardCharsets.UTF_8));
            byte[] key = new byte[16];
            System.arraycopy(hash, 0, key, 0, 16);
            return key;
        } catch (Exception e) {
            return new byte[16];
        }
    }

    private Path ensureDir() {
        if (cacheDir == null) {
            init(Minecraft.getInstance().gameDirectory.toPath());
        }
        return cacheDir;
    }

    private Path dataOf(String name) {
        return cacheDir == null ? null : cacheDir.resolve(safeName(name) + ".data");
    }

    private Path metaOf(String name) {
        return cacheDir == null ? null : cacheDir.resolve(safeName(name) + ".data1");
    }

    private static String safeName(String name) {
        return name.replace('/', '_').replace('\\', '_');
    }

    private static boolean isPlainImage(String name) {
        String n = name.toLowerCase();
        return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg");
    }

    private static boolean isGif(String name) {
        return name.toLowerCase().endsWith(".gif");
    }

    private static boolean isAudio(String name) {
        String n = name.toLowerCase();
        return n.endsWith(".ogg") || n.endsWith(".wav") || n.endsWith(".mp3");
    }

    private static boolean isMedia(String name) {
        return isGif(name) || isAudio(name);
    }

    private static String md5Of(Path file) {
        try {
            return md5Hex(Files.readAllBytes(file));
        } catch (Exception e) {
            return "";
        }
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