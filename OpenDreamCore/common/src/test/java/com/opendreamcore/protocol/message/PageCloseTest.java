package com.opendreamcore.protocol.message;

import com.opendreamcore.protocol.OdcByteArrayBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 页面关闭通知编解码。
 */
class PageCloseTest {

    @Test
    void roundTrip() {
        PageClose original = new PageClose("sess42");
        OdcByteArrayBuf buf = new OdcByteArrayBuf();
        original.encode(buf);
        PageClose decoded = PageClose.decode(new OdcByteArrayBuf(buf.toByteArray()));
        assertEquals("sess42", decoded.sessionId());
    }

    @Test
    void blankSessionRejected() {
        assertThrows(IllegalArgumentException.class, () -> new PageClose(""));
        assertThrows(IllegalArgumentException.class, () -> new PageClose(null));
    }
}
