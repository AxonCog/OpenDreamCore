package com.opendreamcore.protocol.message;

import com.opendreamcore.protocol.OdcByteArrayBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 握手消息编解码往返测试。
 */
class ReadyTest {

    @Test
    void readyRoundTrip() {
        Ready ready = new Ready(1, "0.1.0", 0b111);
        OdcByteArrayBuf buf = new OdcByteArrayBuf();
        ready.encode(buf);

        Ready decoded = Ready.decode(new OdcByteArrayBuf(buf.toByteArray()));
        assertEquals(1, decoded.protocolVersion());
        assertEquals("0.1.0", decoded.modVersion());
        assertEquals(0b111, decoded.capabilities());
    }

    @Test
    void readyRejectsBadVersion() {
        assertThrows(IllegalArgumentException.class, () -> new Ready(0, "0.1.0", 0));
    }

    @Test
    void readyRejectsBlankModVersion() {
        assertThrows(IllegalArgumentException.class, () -> new Ready(1, "  ", 0));
    }

    @Test
    void readyAckWithKeyRoundTrip() {
        byte[] key = {1, 2, 3, 4, 5};
        ReadyAck ack = new ReadyAck(1, "1.0.0", 0b101, key);
        OdcByteArrayBuf buf = new OdcByteArrayBuf();
        ack.encode(buf);

        ReadyAck decoded = ReadyAck.decode(new OdcByteArrayBuf(buf.toByteArray()));
        assertEquals(1, decoded.protocolVersion());
        assertEquals("1.0.0", decoded.modVersion());
        assertArrayEquals(key, decoded.resourceKey());
    }

    @Test
    void readyAckWithoutKeyRoundTrip() {
        ReadyAck ack = new ReadyAck(1, "1.0.0", 0, null);
        OdcByteArrayBuf buf = new OdcByteArrayBuf();
        ack.encode(buf);

        ReadyAck decoded = ReadyAck.decode(new OdcByteArrayBuf(buf.toByteArray()));
        assertEquals(0, decoded.resourceKey().length);
    }

    @Test
    void varIntRoundTrip() {
        for (int v : new int[]{0, 1, 127, 128, 255, 300, 16383, 16384, 2097151, 2097152, Integer.MAX_VALUE}) {
            OdcByteArrayBuf buf = new OdcByteArrayBuf();
            buf.writeVarInt(v);
            assertEquals(v, new OdcByteArrayBuf(buf.toByteArray()).readVarInt(), "varint: " + v);
        }
    }
}
