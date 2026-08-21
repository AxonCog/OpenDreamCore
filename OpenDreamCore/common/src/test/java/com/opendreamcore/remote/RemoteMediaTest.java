package com.opendreamcore.remote;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 远程媒体下载器测试：SSRF 防护（内网/回环/链路本地/保留段拒绝）、缓存命名。
 */
class RemoteMediaTest {

    @Test
    void rejectsNonHttpSchemes() {
        assertFalse(RemoteMedia.isSafeUrl("file:///etc/passwd"));
        assertFalse(RemoteMedia.isSafeUrl("ftp://example.com/a.png"));
        assertFalse(RemoteMedia.isSafeUrl("javascript:alert(1)"));
        assertFalse(RemoteMedia.isSafeUrl(""));
        assertFalse(RemoteMedia.isSafeUrl(null));
    }

    @Test
    void rejectsUserInfo() {
        assertFalse(RemoteMedia.isSafeUrl("http://admin:secret@example.com/a.png"));
    }

    @Test
    void rejectsPrivateAndLoopbackIps() {
        assertFalse(RemoteMedia.isSafeUrl("http://127.0.0.1/ssrf.png"));
        assertFalse(RemoteMedia.isSafeUrl("http://localhost/x.png"));
        assertFalse(RemoteMedia.isSafeUrl("http://10.0.0.5/x.png"));
        assertFalse(RemoteMedia.isSafeUrl("http://172.16.0.1/x.png"));
        assertFalse(RemoteMedia.isSafeUrl("http://192.168.1.1/x.png"));
        assertFalse(RemoteMedia.isSafeUrl("http://169.254.169.254/latest/meta-data/"));
        assertFalse(RemoteMedia.isSafeUrl("http://0.0.0.0/x.png"));
        assertFalse(RemoteMedia.isSafeUrl("http://100.64.0.1/x.png")); // CGNAT
        assertFalse(RemoteMedia.isSafeUrl("http://198.18.0.1/x.png")); // 基准测试段
        assertFalse(RemoteMedia.isSafeUrl("http://240.0.0.1/x.png")); // 保留段
    }

    @Test
    void acceptsPublicIps() {
        // TEST-NET-1（RFC 5737 文档保留段是安全测试用公网示例）
        assertTrue(RemoteMedia.isSafeUrl("http://93.184.216.34/x.png"));
        assertTrue(RemoteMedia.isSafeUrl("http://8.8.8.8/x.png"));
        assertTrue(RemoteMedia.isSafeUrl("https://example.com/a.png"));
    }

    @Test
    void addressClassification() throws Exception {
        assertFalse(RemoteMedia.isPublicAddress(InetAddress.getByName("127.0.0.1")));
        assertFalse(RemoteMedia.isPublicAddress(InetAddress.getByName("10.1.2.3")));
        assertFalse(RemoteMedia.isPublicAddress(InetAddress.getByName("192.168.0.1")));
        assertFalse(RemoteMedia.isPublicAddress(InetAddress.getByName("169.254.1.1")));
        assertFalse(RemoteMedia.isPublicAddress(InetAddress.getByName("224.0.0.1"))); // 组播
        assertFalse(RemoteMedia.isPublicAddress(InetAddress.getByName("::1"))); // IPv6 回环
        assertFalse(RemoteMedia.isPublicAddress(InetAddress.getByName("fc00::1"))); // IPv6 ULA
        assertTrue(RemoteMedia.isPublicAddress(InetAddress.getByName("8.8.8.8")));
    }

    @Test
    void cacheFileNameStableAndSuffixed() {
        String url = "https://example.com/logo.png?size=big";
        String name1 = RemoteMedia.cacheFileName(url);
        String name2 = RemoteMedia.cacheFileName(url);
        assertEquals(name1, name2, "同 URL 缓存名稳定");
        assertTrue(name1.endsWith(".png"), "保留扩展名: " + name1);
        assertEquals(64 + 4, name1.length(), "SHA-256 64 位十六进制 + 扩展名");

        assertTrue(RemoteMedia.cacheFileName("https://example.com/video.mp4").endsWith(".mp4"));
        assertEquals(".bin", RemoteMedia.extensionOf("https://example.com/noext"));
        assertEquals(".png", RemoteMedia.extensionOf("https://example.com/a.png?v=1#frag"));
    }

    @Test
    void extensionKeepsOnlySaneSuffix() {
        assertEquals(".bin", RemoteMedia.extensionOf("https://example.com/a"));
        assertEquals(".jsx", RemoteMedia.extensionOf("https://example.com/a.html.jsx")); // 取最后一个后缀
        assertEquals(".bin", RemoteMedia.extensionOf("https://example.com/"));
        assertEquals(".png", RemoteMedia.extensionOf("http://example.com/x.PNG")); // 小写化
    }
}
