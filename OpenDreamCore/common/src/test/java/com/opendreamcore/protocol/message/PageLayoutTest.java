package com.opendreamcore.protocol.message;

import com.opendreamcore.protocol.OdcByteArrayBuf;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 页面布局消息编解码（double 位级往返）。
 */
class PageLayoutTest {

    @Test
    void roundTrip() {
        PageLayout original = new PageLayout("shop", List.of(
                new PageLayout.Entry("buy_sword", 320.5, 85.25),
                new PageLayout.Entry("coin_line", 10, 48)));
        OdcByteArrayBuf buf = new OdcByteArrayBuf();
        original.encode(buf);
        PageLayout decoded = PageLayout.decode(new OdcByteArrayBuf(buf.toByteArray()));
        assertEquals("shop", decoded.pageId());
        assertEquals(2, decoded.entries().size());
        assertEquals("buy_sword", decoded.entries().get(0).elementId());
        assertEquals(320.5, decoded.entries().get(0).x());
        assertEquals(85.25, decoded.entries().get(0).y());
        assertEquals(10, decoded.entries().get(1).x());
    }
}
