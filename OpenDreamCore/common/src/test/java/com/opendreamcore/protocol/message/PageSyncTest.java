package com.opendreamcore.protocol.message;

import com.opendreamcore.protocol.Crypto;
import com.opendreamcore.protocol.OdcByteArrayBuf;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 页面同步消息编解码（明文 + 加密标记 + 密文往返）。
 */
class PageSyncTest {

    @Test
    void roundTripPlain() {
        PageSync original = new PageSync("shop", "match: 商店\ncoin: 100\n");
        OdcByteArrayBuf buf = new OdcByteArrayBuf();
        original.encode(buf);
        PageSync decoded = PageSync.decode(new OdcByteArrayBuf(buf.toByteArray()));
        assertEquals("shop", decoded.pageId());
        assertFalse(decoded.encrypted());
        assertEquals("match: 商店\ncoin: 100\n", decoded.yaml());
    }

    @Test
    void roundTripEncrypted() {
        byte[] key = Crypto.randomKey();
        byte[] yaml = "match: 商店\ncoin: 100\n".getBytes(StandardCharsets.UTF_8);
        byte[] cipher = Crypto.encrypt(key, yaml);
        PageSync original = new PageSync("shop", cipher, true);
        OdcByteArrayBuf buf = new OdcByteArrayBuf();
        original.encode(buf);
        PageSync decoded = PageSync.decode(new OdcByteArrayBuf(buf.toByteArray()));
        assertTrue(decoded.encrypted());
        // 解密后内容一致
        byte[] plain = Crypto.decrypt(key, decoded.content());
        assertEquals("match: 商店\ncoin: 100\n", new String(plain, StandardCharsets.UTF_8));
        assertThrows(IllegalStateException.class, decoded::yaml, "加密包不能直接取 yaml");
    }

    @Test
    void blankPageRejected() {
        assertThrows(IllegalArgumentException.class, () -> new PageSync("x", ""));
        assertThrows(IllegalArgumentException.class, () -> new PageSync("x", new byte[0], false));
    }
}
