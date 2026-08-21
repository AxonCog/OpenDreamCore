package com.opendreamcore.protocol.message;

import com.opendreamcore.protocol.OdcByteArrayBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 编辑器消息编解码。
 */
class EditorTest {

    @Test
    void leaseRoundTrip() {
        EditorLease request = new EditorLease(EditorLease.Action.REQUEST, "shop", null);
        OdcByteArrayBuf buf = new OdcByteArrayBuf();
        request.encode(buf);
        EditorLease decoded = EditorLease.decode(new OdcByteArrayBuf(buf.toByteArray()));
        assertEquals(EditorLease.Action.REQUEST, decoded.action());
        assertEquals("shop", decoded.pageId());
        assertNull(decoded.holder());

        EditorLease grant = new EditorLease(EditorLease.Action.GRANT, "shop", "Steve");
        OdcByteArrayBuf buf2 = new OdcByteArrayBuf();
        grant.encode(buf2);
        assertEquals("Steve", EditorLease.decode(new OdcByteArrayBuf(buf2.toByteArray())).holder());
    }

    @Test
    void saveRoundTrip() {
        EditorSave save = new EditorSave("shop", "match: 商店\ncoin: 100\n");
        OdcByteArrayBuf buf = new OdcByteArrayBuf();
        save.encode(buf);
        EditorSave decoded = EditorSave.decode(new OdcByteArrayBuf(buf.toByteArray()));
        assertEquals("shop", decoded.pageId());
        assertEquals("match: 商店\ncoin: 100\n", decoded.yaml());
    }
}
