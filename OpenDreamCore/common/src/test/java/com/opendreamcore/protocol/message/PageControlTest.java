package com.opendreamcore.protocol.message;

import com.opendreamcore.protocol.OdcByteArrayBuf;
import com.opendreamcore.protocol.OdcByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PageControlTest {

    @Test
    void openRoundTrip() {
        PageControl msg = new PageControl(PageControl.Action.OPEN, "shop:main", "sess-1", null);
        OdcByteArrayBuf buf = new OdcByteArrayBuf();
        msg.encode(buf);

        PageControl decoded = PageControl.decode(new OdcByteArrayBuf(buf.toByteArray()));
        assertEquals(PageControl.Action.OPEN, decoded.action());
        assertEquals("shop:main", decoded.pageId());
        assertEquals("sess-1", decoded.sessionId());
        assertNull(decoded.parentSessionId());
    }

    @Test
    void subOpenRoundTrip() {
        PageControl msg = new PageControl(PageControl.Action.SUB_OPEN, "shop:confirm", "sess-2", "sess-1");
        OdcByteArrayBuf buf = new OdcByteArrayBuf();
        msg.encode(buf);

        PageControl decoded = PageControl.decode(new OdcByteArrayBuf(buf.toByteArray()));
        assertEquals(PageControl.Action.SUB_OPEN, decoded.action());
        assertEquals("sess-2", decoded.sessionId());
        assertEquals("sess-1", decoded.parentSessionId());
    }

    @Test
    void rejectsBadPageId() {
        assertThrows(IllegalArgumentException.class,
                () -> new PageControl(PageControl.Action.OPEN, "", null, null));
    }
}

class UiEventTest {

    @Test
    void clickRoundTrip() {
        UiEvent event = new UiEvent("sess-1", "buy_sword", UiEvent.Trigger.CLICK, 42, "{\"x\":10,\"y\":20}");
        OdcByteArrayBuf buf = new OdcByteArrayBuf();
        event.encode(buf);

        UiEvent decoded = UiEvent.decode(new OdcByteArrayBuf(buf.toByteArray()));
        assertEquals("buy_sword", decoded.elementId());
        assertEquals(UiEvent.Trigger.CLICK, decoded.trigger());
        assertEquals(42L, decoded.sequence());
        assertEquals("{\"x\":10,\"y\":20}", decoded.data());
    }

    @Test
    void inputWithoutDataRoundTrip() {
        UiEvent event = new UiEvent("sess-1", "chat_input", UiEvent.Trigger.INPUT, 7, null);
        OdcByteArrayBuf buf = new OdcByteArrayBuf();
        event.encode(buf);

        UiEvent decoded = UiEvent.decode(new OdcByteArrayBuf(buf.toByteArray()));
        assertNull(decoded.data());
        assertEquals(UiEvent.Trigger.INPUT, decoded.trigger());
    }
}
