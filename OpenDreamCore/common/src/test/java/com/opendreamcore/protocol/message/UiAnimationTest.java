package com.opendreamcore.protocol.message;

import com.opendreamcore.protocol.OdcByteArrayBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 动画触发编解码（动作 + 名称序列）。
 */
class UiAnimationTest {

    @Test
    void roundTripPlaySequence() {
        UiAnimation original = new UiAnimation(UiAnimation.Action.PLAY, "a", "b", "c");
        OdcByteArrayBuf buf = new OdcByteArrayBuf();
        original.encode(buf);
        UiAnimation decoded = UiAnimation.decode(new OdcByteArrayBuf(buf.toByteArray()));
        assertEquals(UiAnimation.Action.PLAY, decoded.action());
        assertEquals(java.util.List.of("a", "b", "c"), decoded.names());
    }

    @Test
    void roundTripSingle() {
        for (UiAnimation.Action action : new UiAnimation.Action[]{
                UiAnimation.Action.STOP, UiAnimation.Action.PAUSE, UiAnimation.Action.RESUME}) {
            UiAnimation original = new UiAnimation(action, "title_bounce");
            OdcByteArrayBuf buf = new OdcByteArrayBuf();
            original.encode(buf);
            UiAnimation decoded = UiAnimation.decode(new OdcByteArrayBuf(buf.toByteArray()));
            assertEquals(action, decoded.action());
            assertEquals(java.util.List.of("title_bounce"), decoded.names());
        }
    }

    @Test
    void blankNamesFiltered() {
        UiAnimation original = new UiAnimation(UiAnimation.Action.PLAY, "a", "", null, "b");
        assertEquals(java.util.List.of("a", "b"), original.names());
        assertThrows(IllegalArgumentException.class, () -> new UiAnimation(null, "x"));
    }
}
