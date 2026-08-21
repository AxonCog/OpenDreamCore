package com.opendreamcore.protocol.message;

import com.opendreamcore.protocol.OdcByteArrayBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 屏幕特效指令编解码。
 */
class UiEffectTest {

    @Test
    void roundTripShake() {
        UiEffect original = new UiEffect(UiEffect.Kind.SHAKE, 5.0, 300.0, "");
        OdcByteArrayBuf buf = new OdcByteArrayBuf();
        original.encode(buf);
        UiEffect decoded = UiEffect.decode(new OdcByteArrayBuf(buf.toByteArray()));
        assertEquals(UiEffect.Kind.SHAKE, decoded.kind());
        assertEquals(5.0, decoded.arg1());
        assertEquals(300.0, decoded.arg2());
        assertEquals("", decoded.color());
    }

    @Test
    void roundTripFlashAndTransition() {
        for (UiEffect.Kind kind : new UiEffect.Kind[]{UiEffect.Kind.FLASH, UiEffect.Kind.TRANSITION}) {
            UiEffect original = new UiEffect(kind, 200.0, 0.0, "#FF0000");
            OdcByteArrayBuf buf = new OdcByteArrayBuf();
            original.encode(buf);
            UiEffect decoded = UiEffect.decode(new OdcByteArrayBuf(buf.toByteArray()));
            assertEquals(kind, decoded.kind());
            assertEquals(200.0, decoded.arg1());
            assertEquals("#FF0000", decoded.color());
        }
    }

    @Test
    void nullKindRejected() {
        assertThrows(IllegalArgumentException.class, () -> new UiEffect(null, 1, 1, null));
    }
}
