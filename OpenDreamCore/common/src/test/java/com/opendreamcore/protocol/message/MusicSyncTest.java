package com.opendreamcore.protocol.message;

import com.opendreamcore.protocol.OdcByteArrayBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 背景音乐同步编解码。
 */
class MusicSyncTest {

    @Test
    void roundTripPlay() {
        MusicSync original = new MusicSync(MusicSync.Action.PLAY, "bgm.wav", 0.6, true);
        OdcByteArrayBuf buf = new OdcByteArrayBuf();
        original.encode(buf);
        MusicSync decoded = MusicSync.decode(new OdcByteArrayBuf(buf.toByteArray()));
        assertEquals(MusicSync.Action.PLAY, decoded.action());
        assertEquals("bgm.wav", decoded.file());
        assertEquals(0.6, decoded.volume());
        assertTrue(decoded.loop());
    }

    @Test
    void roundTripStopAndVolume() {
        for (MusicSync.Action action : new MusicSync.Action[]{MusicSync.Action.STOP, MusicSync.Action.VOLUME}) {
            MusicSync original = new MusicSync(action, "", 0.3, false);
            OdcByteArrayBuf buf = new OdcByteArrayBuf();
            original.encode(buf);
            MusicSync decoded = MusicSync.decode(new OdcByteArrayBuf(buf.toByteArray()));
            assertEquals(action, decoded.action());
            assertEquals(0.3, decoded.volume());
        }
    }
}
