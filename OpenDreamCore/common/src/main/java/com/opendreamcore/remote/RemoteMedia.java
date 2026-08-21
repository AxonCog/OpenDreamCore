package com.opendreamcore.remote;

import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 远程媒体下载（http/https 图片/视频等）。
 * 作者：梦幻 QQ:2496599413
 *
 * SSRF 防护：只允许 http/https；DNS 解析后内网/回环/链路本地/组播/保留段全部拒绝；
 * 禁止重定向跟随；单文件上限 16MB；禁止 URL 带 userinfo。
 *
 * 缓存：按 URL 的 SHA-256 命名落盘，重复请求直接读缓存。
 * 并发去重：同一 URL 同时只发起一次下载。
 * 纯 JDK，客户端/服务端共用。
 */
public final class RemoteMedia {

    /** 单文件下载上限（字节）。 */
    public static final int MAX_BYTES = 16 * 1024 * 1024;

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

    private static final ExecutorService DOWNLOADS = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "odc-remote-media");
        t.setDaemon(true);
        return t;
    });

    /** 进行中的下载（URL → future），避免同一 URL 并发重复下载。 */
    private static final Map<String, CompletableFuture<Path>> IN_FLIGHT = new ConcurrentHashMap<>();

    private RemoteMedia() {
    }

    /** 同步下载到缓存（主调线程阻塞；失败返回 null）。 */
    public static Path download(String url, Path cacheDir) {
        CompletableFuture<Path> future = get(url, cacheDir);
        try {
            return future.get(60, TimeUnit.SECONDS);
        } catch (Exception e) {
            return null;
        }
    }

    /** 异步下载（返回的 future 成功即缓存文件就绪）。 */
    public static CompletableFuture<Path> get(String url, Path cacheDir) {
        return IN_FLIGHT.computeIfAbsent(url, u -> {
            CompletableFuture<Path> f = new CompletableFuture<>();
            DOWNLOADS.execute(() -> {
                try {
                    f.complete(downloadNow(u, cacheDir));
                } catch (Throwable t) {
                    f.completeExceptionally(t);
                } finally {
                    IN_FLIGHT.remove(u);
                }
            });
            return f;
        });
    }

    private static Path downloadNow(String url, Path cacheDir) throws Exception {
        if (!isSafeUrl(url)) {
            throw new IOException("不安全的远程地址: " + url);
        }
        Path cached = cacheFile(url, cacheDir);
        if (Files.isRegularFile(cached) && Files.size(cached) > 0) {
            return cached; // 缓存命中
        }
        URI uri = URI.create(url);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", "OpenDreamCore/1.0")
                .GET()
                .build();
        HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
        int code = response.statusCode();
        if (code >= 300 && code < 400) {
            String loc = response.headers().firstValue("location").orElse(null);
            response.body().close();
            if (loc != null && !isSafeUrl(loc)) {
                throw new IOException("重定向目标不安全: " + loc);
            }
            throw new IOException("HTTP " + code + " 重定向被拦截: " + url + " -> " + loc);
        }
        if (code != 200) {
            response.body().close();
            throw new IOException("HTTP " + code + " 下载失败: " + url);
        }
        // Content-Length 预检：声明超过 16MB 直接拒绝，避免流式读取压力
        String cl = response.headers().firstValue("content-length").orElse(null);
        if (cl != null) {
            try {
                if (Long.parseLong(cl.trim()) > MAX_BYTES) {
                    response.body().close();
                    throw new IOException("Content-Length 超过 16MB 上限: " + url);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        String ct = response.headers().firstValue("content-type").orElse(null);
        if (ct != null) {
            String lower = ct.toLowerCase(Locale.ROOT);
            boolean ok = lower.contains("image/") || lower.contains("video/")
                    || lower.contains("application/octet-stream");
            if (!ok) {
                response.body().close();
                throw new IOException("Content-Type 不被允许: " + ct + " (" + url + ")");
            }
        }
        Files.createDirectories(cacheDir);
        Path tmp = cacheDir.resolve(cached.getFileName() + ".part");
        try (InputStream in = response.body()) {
            long total = 0;
            byte[] buf = new byte[8192];
            try (var out = Files.newOutputStream(tmp)) {
                int n;
                while ((n = in.read(buf)) > 0) {
                    total += n;
                    if (total > MAX_BYTES) {
                        throw new IOException("文件超过 16MB 上限: " + url);
                    }
                    out.write(buf, 0, n);
                }
            }
        } catch (Throwable downloadEx) {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
            }
            try {
                response.body().close();
            } catch (Exception ignored) {
            }
            if (downloadEx instanceof IOException ioe) {
                throw ioe;
            }
            throw new IOException(downloadEx);
        }
        try {
            Files.move(tmp, cached, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Files.deleteIfExists(tmp);
            throw e;
        }
        evictIfNeeded(cacheDir);
        return cached;
    }

    // ========== SSRF 防护 ==========

    /**
     * 校验远程 URL 是否安全：协议白名单 + 无 userinfo + DNS 解析后无内网地址。
     * 解析失败按"不安全"处理（fail-closed）。
     */
    public static boolean isSafeUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        URI uri;
        try {
            uri = new URI(url.trim());
        } catch (URISyntaxException e) {
            return false;
        }
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            return false;
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return false;
        }
        if (uri.getUserInfo() != null) {
            return false; // user:pass@host 一律拒绝
        }
        try {
            InetAddress[] resolved = InetAddress.getAllByName(host);
            if (resolved.length == 0) {
                return false;
            }
            for (InetAddress addr : resolved) {
                if (!isPublicAddress(addr)) {
                    return false;
                }
            }
            return true;
        } catch (UnknownHostException e) {
            return false; // 解析失败 → 不安全
        }
    }

    /** 公网地址判定：内网/回环/链路本地/组播/保留段均视为非公网。 */
    public static boolean isPublicAddress(InetAddress addr) {
        if (addr == null) {
            return false;
        }
        if (addr.isAnyLocalAddress() || addr.isLoopbackAddress()
                || addr.isLinkLocalAddress() || addr.isSiteLocalAddress()
                || addr.isMulticastAddress()) {
            return false;
        }
        if (addr instanceof Inet4Address a4) {
            byte[] b = a4.getAddress();
            int first = b[0] & 0xFF;
            // 文档保留段（RFC 5737 测试网段等）与 0.x 开头
            if (first == 0 || first == 192 && (b[1] & 0xFF) == 0 && (b[2] & 0xFF) == 2) {
                return false;
            }
            // 100.64.0.0/10 CGNAT 段
            if (first == 100 && (b[1] & 0xC0) == 0x40) {
                return false;
            }
            // 198.18.0.0/15 基准测试段
            if (first == 198 && (b[1] & 0xFE) == 18) {
                return false;
            }
            // 240.0.0.0/4 保留段
            if (first >= 240) {
                return false;
            }
            // 169.254.x.x 链路本地（部分 JVM 不识别 IPv4 链路本地）
            if (first == 169 && (b[1] & 0xFF) == 254) {
                return false;
            }
        } else if (addr instanceof Inet6Address a6) {
            byte[] b = a6.getAddress();
            int first = b[0] & 0xFF;
            // fc00::/7 唯一本地地址
            if ((first & 0xFE) == 0xFC) {
                return false;
            }
            // fe80::/10 链路本地（部分 JVM 不识别）
            if ((first & 0xFF) == 0xFE && (b[1] & 0xC0) == 0x80) {
                return false;
            }
            // ::ffff:0:0/96 IPv4 映射地址 → 递归校验 IPv4
            boolean v4Mapped = true;
            for (int i = 0; i < 10; i++) {
                if (b[i] != 0) {
                    v4Mapped = false;
                    break;
                }
            }
            if (v4Mapped && b[10] == 0xFF && b[11] == 0xFF) {
                byte[] v4 = {b[12], b[13], b[14], b[15]};
                try {
                    return isPublicAddress(Inet4Address.getByAddress(v4));
                } catch (UnknownHostException ignored) {
                    return false;
                }
            }
        }
        return true;
    }

    // ========== 缓存 ==========

    /** 缓存文件名：URL 的 SHA-256 + 原扩展名（无扩展名 → .bin）。 */
    public static String cacheFileName(String url) {
        String digest = sha256(url == null ? "" : url);
        String ext = extensionOf(url);
        return digest + ext;
    }

    public static Path cacheFile(String url, Path cacheDir) {
        return cacheDir.resolve(cacheFileName(url));
    }

    private static final long MAX_CACHE_BYTES = 200L * 1024 * 1024;
    private static final int MAX_CACHE_FILES = 500;

    private static void evictIfNeeded(Path cacheDir) {
        try {
            if (!Files.isDirectory(cacheDir)) {
                return;
            }
            java.util.List<Path> files;
            try (var stream = Files.list(cacheDir)) {
                files = stream.filter(p -> {
                    String n = p.getFileName().toString();
                    return !n.endsWith(".part") && Files.isRegularFile(p);
                }).collect(java.util.stream.Collectors.toList());
            }
            if (files.isEmpty()) {
                return;
            }
            long total = 0;
            for (var f : files) {
                try {
                    total += Files.size(f);
                } catch (IOException ignored) {
                }
            }
            if (total <= MAX_CACHE_BYTES && files.size() <= MAX_CACHE_FILES) {
                return;
            }
            files.sort(java.util.Comparator.comparing(p -> {
                try {
                    return Files.getLastModifiedTime(p);
                } catch (IOException e) {
                    return java.nio.file.attribute.FileTime.fromMillis(0);
                }
            }));
            int remaining = files.size();
            for (var f : files) {
                if (total <= MAX_CACHE_BYTES && remaining <= MAX_CACHE_FILES) {
                    break;
                }
                try {
                    long sz = Files.size(f);
                    Files.deleteIfExists(f);
                    total -= sz;
                    remaining--;
                } catch (IOException ignored) {
                }
            }
        } catch (IOException ignored) {
        }
    }

    /** 从 URL 提取扩展名（.png/.mp4 等，限字母数字，最长 5 位）。 */
    public static String extensionOf(String url) {
        if (url == null) {
            return ".bin";
        }
        String path = url;
        int q = path.indexOf('?');
        if (q >= 0) {
            path = path.substring(0, q);
        }
        int f = path.indexOf('#');
        if (f >= 0) {
            path = path.substring(0, f);
        }
        int slash = path.lastIndexOf('/');
        String last = slash >= 0 ? path.substring(slash + 1) : path;
        int dot = last.lastIndexOf('.');
        if (dot >= 0 && dot < last.length() - 1) {
            String ext = last.substring(dot).toLowerCase(Locale.ROOT);
            if (ext.matches("\\.[a-z0-9]{1,5}")) {
                return ext;
            }
        }
        return ".bin";
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 必然存在
            return Integer.toHexString(s.hashCode());
        }
    }
}
