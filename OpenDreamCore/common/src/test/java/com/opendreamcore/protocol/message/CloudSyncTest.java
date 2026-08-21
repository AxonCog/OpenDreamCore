package com.opendreamcore.protocol.message;

import com.opendreamcore.protocol.Crypto;
import com.opendreamcore.protocol.OdcByteArrayBuf;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 云同步消息编解码 + 加密往返。
 */
class CloudSyncTest {

    @Test
    void manifestRoundTrip() {
        CloudManifest original = new CloudManifest(List.of(
                new CloudManifest.Entry("gui/logo.png", 12345, "abc123"),
                new CloudManifest.Entry("ui/shop.yaml", 999, "def456")));
        OdcByteArrayBuf buf = new OdcByteArrayBuf();
        original.encode(buf);
        CloudManifest decoded = CloudManifest.decode(new OdcByteArrayBuf(buf.toByteArray()));
        assertEquals(2, decoded.entries().size());
        assertEquals("gui/logo.png", decoded.entries().get(0).path());
        assertEquals(12345, decoded.entries().get(0).size());
        assertEquals("abc123", decoded.entries().get(0).sha256());
    }

    @Test
    void diffRoundTrip() {
        CloudDiff original = new CloudDiff(List.of("a.png", "b.png"));
        OdcByteArrayBuf buf = new OdcByteArrayBuf();
        original.encode(buf);
        CloudDiff decoded = CloudDiff.decode(new OdcByteArrayBuf(buf.toByteArray()));
        assertEquals(List.of("a.png", "b.png"), decoded.paths());
    }

    @Test
    void fileRoundTrip() {
        CloudFile original = new CloudFile("assets/x.png", new byte[]{1, 2, 3, 4, 5});
        OdcByteArrayBuf buf = new OdcByteArrayBuf();
        original.encode(buf);
        CloudFile decoded = CloudFile.decode(new OdcByteArrayBuf(buf.toByteArray()));
        assertEquals("assets/x.png", decoded.path());
        assertArrayEquals(new byte[]{1, 2, 3, 4, 5}, decoded.encrypted());
    }

    @Test
    void deleteAndDoneRoundTrip() {
        CloudDelete del = new CloudDelete(List.of("old.png"));
        OdcByteArrayBuf buf = new OdcByteArrayBuf();
        del.encode(buf);
        assertEquals(List.of("old.png"), CloudDelete.decode(new OdcByteArrayBuf(buf.toByteArray())).paths());

        CloudDone done = new CloudDone();
        OdcByteArrayBuf buf2 = new OdcByteArrayBuf();
        done.encode(buf2);
        assertNotNull(CloudDone.decode(new OdcByteArrayBuf(buf2.toByteArray())));
    }

    @Test
    void cryptoRoundTrip() {
        byte[] key = Crypto.randomKey();
        byte[] data = "你好，OpenDreamCore 云资源".getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = Crypto.encrypt(key, data);
        assertFalse(new String(encrypted).contains("OpenDreamCore"));
        assertArrayEquals(data, Crypto.decrypt(key, encrypted));
    }
}
