package com.opendreamcore.protocol.message;

import com.opendreamcore.protocol.OdcByteArrayBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 配置下发编解码。
 */
class ConfigPushTest {

    @Test
    void roundTrip() {
        ConfigPush original = new ConfigPush("versionCheck.enforce=true\nhud.autoMount=true\n");
        OdcByteArrayBuf buf = new OdcByteArrayBuf();
        original.encode(buf);
        ConfigPush decoded = ConfigPush.decode(new OdcByteArrayBuf(buf.toByteArray()));
        assertEquals("versionCheck.enforce=true\nhud.autoMount=true\n", decoded.properties());
    }

    @Test
    void emptyAllowed() {
        ConfigPush original = new ConfigPush("");
        OdcByteArrayBuf buf = new OdcByteArrayBuf();
        original.encode(buf);
        ConfigPush decoded = ConfigPush.decode(new OdcByteArrayBuf(buf.toByteArray()));
        assertEquals("", decoded.properties());
    }
}
