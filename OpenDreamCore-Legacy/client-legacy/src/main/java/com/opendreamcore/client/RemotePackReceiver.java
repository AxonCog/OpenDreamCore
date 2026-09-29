package com.opendreamcore.client;

import org.apache.logging.log4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 服务端字节分片材质包接收器（odc/packdata 通道，四版老壳共用）。
 *
 * 服务端（PackAPI.DATA_CHANNEL）把本地 zip 拆成 48KB 片推过来：
 * 先 "push|名字|md5|大小|片数|置顶|密码B64" 报头，再逐片 "part|md5|序号|b64"。
 * 收齐 → 拼接 → MD5 校验 → 落本地材质包区（{@link LocalPackPreload}）：
 *   无密码 → 直接写成包区里的 zip（指纹变化，两秒内自动重注入）；
 *   带密码 → 先试明文流、碰到加密症状转 zip4j（可选依赖）解成目录包。
 * md5 没变的重推直接跳过，不折腾资源管理器。
 *
 * 线程：MessageDispatcher 把 custom_packet 的下行转成 "custom:<通道>" 总线话题，
 * 回调在包到达的那一帧（netty 线程）执行——这里只做文件 IO，注入交给包区监视线程，
 * 聊天反馈经 ChatNotifier（壳没注册就只落日志）。
 */
public final class RemotePackReceiver {

    private static final Logger LOGGER = org.apache.logging.log4j.LogManager.getLogger("OpenDreamCore");

    /** 同时装配中的包上限（防刷屏占内存）。 */
    private static final int MAX_ASSEMBLIES = 8;

    private RemotePackReceiver() { }

    private static final class Assembly {
        final String name;
        final long size;
        final int total;
        final String password;
        final byte[][] parts;
        final AtomicInteger received = new AtomicInteger();
        volatile long lastTouch;

        Assembly(String name, long size, int total, String password) {
            this.name = name;
            this.size = size;
            this.total = total;
            this.password = password;
            this.parts = new byte[total][];
            this.lastTouch = System.currentTimeMillis();
        }
    }

    private static final ConcurrentHashMap<String, Assembly> ASSEMBLIES =
            new ConcurrentHashMap<String, Assembly>();

    /** 装配过期时间：服务端中途死亡/断线留下的半截包，超时自动腾位。 */
    private static final long ASSEMBLY_TTL_MS = 10 * 60 * 1000L;

    /** 开局调一次：把 odc/packdata 挂上消息总线（各版本壳 init 里跟 LocalPackPreload.init 一起调）。 */
    public static void init() {
        com.opendreamcore.client.api.BridgeEvents.on("custom:odc/packdata",
                new com.opendreamcore.client.api.BridgeEvents.Listener() {
                    @Override
                    public void accept(String payload) {
                        onMessage(payload);
                    }
                });
        LOGGER.info("[ODC] 远程材质包接收器已挂载（odc/packdata）");
    }

    /** 清掉超时的半截装配（服务端掉线/重推前的残留），腾出队列位。 */
    private static void evictStaleAssemblies() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Assembly> en : ASSEMBLIES.entrySet()) {
            if (now - en.getValue().lastTouch > ASSEMBLY_TTL_MS) {
                ASSEMBLIES.remove(en.getKey());
                LOGGER.warn("[ODC] 材质包装配超时弃收: {}", en.getValue().name);
            }
        }
    }

    private static void onMessage(String payload) {
        if (payload == null || payload.isEmpty()) {
            return;
        }
        int bar = payload.indexOf('|');
        String op = bar < 0 ? payload : payload.substring(0, bar);
        if ("push".equals(op)) {
            String[] f = payload.split("\\|", 7);
            if (f.length < 7) {
                return;
            }
            try {
                String name = sanitizePackName(f[1]);
                String md5 = f[2];
                long size = Long.parseLong(f[3]);
                int total = Integer.parseInt(f[4]);
                String pw = "-".equals(f[6]) ? null
                        : new String(Base64.getDecoder().decode(f[6]), StandardCharsets.UTF_8);
                if (md5.length() != 32 || size < 0 || total <= 0 || total > 200000) {
                    return;
                }
                if (!ASSEMBLIES.containsKey(md5) && ASSEMBLIES.size() >= MAX_ASSEMBLIES) {
                    evictStaleAssemblies();
                    if (ASSEMBLIES.size() >= MAX_ASSEMBLIES) {
                        say("§c[ODC] 材质包接收队列已满，忽略 " + name);
                        return;
                    }
                }
                ASSEMBLIES.put(md5, new Assembly(name, size, total, pw));
                LOGGER.info("[ODC] 开始接收服务端材质包: {}（{} 字节，{} 片）", name, size, total);
            } catch (RuntimeException ignored) {
                // 报头坏了就当没看见
            }
        } else if ("part".equals(op)) {
            String[] f = payload.split("\\|", 4);
            if (f.length < 4) {
                return;
            }
            Assembly a = ASSEMBLIES.get(f[1]);
            if (a == null) {
                return;
            }
            int seq;
            byte[] piece;
            try {
                seq = Integer.parseInt(f[2]);
                piece = Base64.getDecoder().decode(f[3]);
            } catch (RuntimeException ignored) {
                return;
            }
            if (seq < 0 || seq >= a.total || a.parts[seq] != null) {
                return; // 越界或重复片
            }
            a.lastTouch = System.currentTimeMillis();
            a.parts[seq] = piece;
            if (a.received.incrementAndGet() >= a.total) {
                ASSEMBLIES.remove(f[1]); // 先摘牌，失败也不占队列
                completeAssembly(f[1], a);
            }
        }
    }

    /** 收齐 → 拼接 → MD5 校验 → 落包区（无密码落 zip，带密码解成目录包）。 */
    private static void completeAssembly(String md5, Assembly a) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream((int) Math.max(64, a.size));
            for (byte[] p : a.parts) {
                if (p == null) {
                    say("§c[ODC] 材质包接收不完整: " + a.name);
                    return;
                }
                bos.write(p);
            }
            byte[] whole = bos.toByteArray();
            if (!md5Hex(whole).equalsIgnoreCase(md5) || whole.length != a.size) {
                say("§c[ODC] 材质包校验失败（md5/大小不符），请让服主重推: " + a.name);
                return;
            }
            Path root = LocalPackPreload.packRoot();
            if (root == null) {
                LOGGER.warn("[ODC] 材质包区未就绪，丢弃收到的包: {}", a.name);
                return;
            }
            Files.createDirectories(root);
            Path sidecar = root.resolve(stemOf(a.name) + ".packmd5");
            if (Files.isRegularFile(sidecar)
                    && md5.equalsIgnoreCase(new String(Files.readAllBytes(sidecar), StandardCharsets.UTF_8).trim())) {
                LOGGER.info("[ODC] 材质包 {} 内容未变，跳过重装", a.name);
                return;
            }
            boolean ok;
            if (a.password != null) {
                ok = unzipWithPassword(whole, root.resolve(stemOf(a.name)), a.password);
                if (!ok) {
                    say("§c[ODC] 加密材质包解压失败（需要 zip4j 库）: " + a.name);
                    return;
                }
            } else {
                Files.write(root.resolve(a.name), whole);
            }
            Files.write(sidecar, md5.getBytes(StandardCharsets.UTF_8));
            say("§a[ODC] 收到服务端材质包: " + a.name + "，两秒内自动生效");
            LOGGER.info("[ODC] 远程材质包已落包区: {}（{} 字节）", a.name, whole.length);
        } catch (Throwable t) {
            LOGGER.warn("[ODC] 材质包装配失败 {}: {}", a.name, t.toString());
        }
    }

    /** 带密码包：先试明文流，碰到加密症状转 zip4j（可选依赖）。 */
    private static boolean unzipWithPassword(byte[] whole, Path dest, String password) {
        boolean encryptedHit = false;
        int entries = 0;
        try {
            ZipInputStream zis = new ZipInputStream(new InputStream() {
                private int pos;
                @Override
                public int read() {
                    return pos < whole.length ? (whole[pos++] & 0xFF) : -1;
                }
            });
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if (e.isDirectory()) {
                    continue;
                }
                Path out = dest.resolve(e.getName()).normalize();
                if (!out.startsWith(dest)) {
                    LOGGER.warn("[ODC] zip 内非法路径: {}", e.getName());
                    return false;
                }
                Files.createDirectories(out.getParent());
                Files.copy(zis, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                entries++;
            }
            zis.close();
        } catch (IOException ioe) {
            encryptedHit = true; // 典型加密症状：明文流读条目抛 ZipException
        }
        if (!encryptedHit && entries > 0) {
            return true;
        }
        return extractWithZip4j(whole, dest, password);
    }

    /** zip4j 反射解压（可选依赖）：成功 true；库缺失/密码错/解压失败 false。 */
    private static boolean extractWithZip4j(byte[] whole, Path dest, String password) {
        Path tmp = null;
        try {
            if (Files.isDirectory(dest)) {
                deleteRecursively(dest);
            }
            Files.createDirectories(dest);
            tmp = dest.getParent().resolve(dest.getFileName() + ".unpacking");
            Files.write(tmp, whole);
            Class<?> zipFileClz = Class.forName("net.lingala.zip4j.ZipFile");
            Object zf = zipFileClz.getConstructor(java.io.File.class, char[].class)
                    .newInstance(tmp.toFile(), password.toCharArray());
            zf.getClass().getMethod("setPassword", char[].class).invoke(zf, password.toCharArray());
            zf.getClass().getMethod("extractAll", String.class).invoke(zf, dest.toString());
            return true;
        } catch (Throwable t) {
            LOGGER.warn("[ODC] zip4j 解压失败: {}", t.toString());
            return false;
        } finally {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        Files.walkFileTree(root, new java.nio.file.SimpleFileVisitor<Path>() {
            @Override
            public java.nio.file.FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attrs)
                    throws IOException {
                Files.deleteIfExists(file);
                return java.nio.file.FileVisitResult.CONTINUE;
            }

            @Override
            public java.nio.file.FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.deleteIfExists(dir);
                return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
    }

    /** 包名安全化：接 basename、掐掉前导下划线（包区语义：_ 开头不参与加载）、非法字符换下划线（中文保留）、补 .zip——与服务端 PackAPI.safeName 同款。 */
    private static String sanitizePackName(String raw) {
        String name = raw.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceFirst("^_+", "").replaceAll("[^\\w\\u4e00-\\u9fa5.-]", "_").trim();
        if (name.isEmpty()) {
            name = "pack.zip";
        }
        if (!name.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            name = name + ".zip";
        }
        return name;
    }

    private static String stemOf(String zipName) {
        return zipName.endsWith(".zip") ? zipName.substring(0, zipName.length() - 4) : zipName;
    }

    private static String md5Hex(byte[] data) {
        try {
            byte[] hash = MessageDigest.getInstance("MD5").digest(data);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /** 聊天栏反馈（壳没注册就只落日志）。 */
    private static void say(String text) {
        com.opendreamcore.client.spi.ChatNotifier n = com.opendreamcore.client.spi.ChatNotifier.Host.current();
        if (n != null) {
            try {
                n.say(text);
            } catch (Throwable ignored) {
            }
        }
    }
}
