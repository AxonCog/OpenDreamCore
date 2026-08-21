package com.opendreamcore.protocol.message;

import com.opendreamcore.protocol.Crypto;
import com.opendreamcore.protocol.OdcByteArrayBuf;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HUD 同步编解码（三模式 / 卸载 / 加密内容）。
 */
class HudSyncTest {

    @Test
    void roundTripMount() {
        for (HudSync.Mode mode : HudSync.Mode.values()) {
            HudSync original = new HudSync("hud_global", mode,
                    "display: hud\n".getBytes(StandardCharsets.UTF_8), false, "sess1");
            OdcByteArrayBuf buf = new OdcByteArrayBuf();
            original.encode(buf);
            HudSync decoded = HudSync.decode(new OdcByteArrayBuf(buf.toByteArray()));
            assertEquals("hud_global", decoded.pageId());
            assertEquals(mode, decoded.mode());
            assertFalse(decoded.remove());
            assertEquals("sess1", decoded.sessionId());
            assertEquals("display: hud\n", new String(decoded.content(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void roundTripEncryptedAndRemove() {
        byte[] key = Crypto.randomKey();
        byte[] cipher = Crypto.encrypt(key, "display: hud\n".getBytes(StandardCharsets.UTF_8));
        HudSync mounted = new HudSync("hud1", HudSync.Mode.GHUD, cipher, true, "s2");
        OdcByteArrayBuf buf = new OdcByteArrayBuf();
        mounted.encode(buf);
        HudSync decoded = HudSync.decode(new OdcByteArrayBuf(buf.toByteArray()));
        assertTrue(decoded.encrypted());
        assertEquals("display: hud\n",
                new String(Crypto.decrypt(key, decoded.content()), StandardCharsets.UTF_8));

        HudSync removed = new HudSync(HudSync.Mode.HUD, "s2");
        OdcByteArrayBuf buf2 = new OdcByteArrayBuf();
        removed.encode(buf2);
        HudSync decoded2 = HudSync.decode(new OdcByteArrayBuf(buf2.toByteArray()));
        assertTrue(decoded2.remove());
    }

    @Test
    void nullModeRejected() {
        assertThrows(IllegalArgumentException.class, () -> new HudSync(null, "s"));
    }
}
