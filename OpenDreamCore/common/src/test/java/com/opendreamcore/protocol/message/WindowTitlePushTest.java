package com.opendreamcore.protocol.message;

import com.opendreamcore.protocol.OdcByteArrayBuf;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 窗口标题下发编解码。
 */
class WindowTitlePushTest {

    @Test
    void roundTripSetConfig() {
        WindowTitlePush original = WindowTitlePush.config(
                "静态兜底", List.of("第一句", "第二句", "第三句"),
                true, false, 120, 3000, -1, true);
        OdcByteArrayBuf buf = new OdcByteArrayBuf();
        original.encode(buf);
        WindowTitlePush decoded = WindowTitlePush.decode(new OdcByteArrayBuf(buf.toByteArray()));
        assertEquals(WindowTitlePush.Op.SET_CONFIG, decoded.op());
        assertEquals("静态兜底", decoded.text());
        assertEquals(List.of("第一句", "第二句", "第三句"), decoded.titles());
        assertTrue(decoded.typewriter());
        assertFalse(decoded.random());
        assertEquals(120, decoded.speed());
        assertEquals(3000, decoded.interval());
        assertEquals(-1, decoded.holdMs());
        assertTrue(decoded.loop());
    }

    @Test
    void roundTripRandomFlag() {
        WindowTitlePush original = WindowTitlePush.config(
                "", List.of("甲", "乙", "丙"), false, true, 100, 2000, -1, true);
        OdcByteArrayBuf buf = new OdcByteArrayBuf();
        original.encode(buf);
        WindowTitlePush decoded = WindowTitlePush.decode(new OdcByteArrayBuf(buf.toByteArray()));
        assertTrue(decoded.random());
        assertFalse(decoded.typewriter());
    }

    @Test
    void roundTripSetStatic() {
        WindowTitlePush original = WindowTitlePush.statik("梦幻小屋");
        OdcByteArrayBuf buf = new OdcByteArrayBuf();
        original.encode(buf);
        WindowTitlePush decoded = WindowTitlePush.decode(new OdcByteArrayBuf(buf.toByteArray()));
        assertEquals(WindowTitlePush.Op.SET_STATIC, decoded.op());
        assertEquals("梦幻小屋", decoded.text());
        assertTrue(decoded.titles().isEmpty());
    }

    @Test
    void roundTripReset() {
        OdcByteArrayBuf buf = new OdcByteArrayBuf();
        WindowTitlePush.reset().encode(buf);
        WindowTitlePush decoded = WindowTitlePush.decode(new OdcByteArrayBuf(buf.toByteArray()));
        assertEquals(WindowTitlePush.Op.RESET, decoded.op());
        assertEquals("", decoded.text());
        // RESET 载荷最短：仅一个 op 字节
        assertEquals(1, buf.toByteArray().length);
    }

    @Test
    void emptyStaticRejected() {
        assertThrows(IllegalArgumentException.class, () -> WindowTitlePush.statik(""));
        assertThrows(IllegalArgumentException.class, () -> WindowTitlePush.statik(null));
    }

    @Test
    void nullOpRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new WindowTitlePush(null, "", null, false, false, 0, 0, -1, false));
    }

    @Test
    void decodeRejectsAbsurdTitleCount() {
        // 手工编码非法数量前缀（> MAX_TITLES），按库约定重绕后解码
        OdcByteArrayBuf w = new OdcByteArrayBuf();
        w.writeByte(WindowTitlePush.Op.SET_CONFIG.id);
        w.writeString("");
        w.writeVarInt(99999);
        assertThrows(IllegalStateException.class,
                () -> WindowTitlePush.decode(new OdcByteArrayBuf(w.toByteArray())));
    }

    @Test
    void longTextTruncatedOnDecode() {
        String huge = "标".repeat(2000);
        OdcByteArrayBuf w = new OdcByteArrayBuf();
        WindowTitlePush.statik(huge).encode(w);
        WindowTitlePush decoded = WindowTitlePush.decode(new OdcByteArrayBuf(w.toByteArray()));
        assertEquals(WindowTitlePush.MAX_TEXT_LEN, decoded.text().length());
    }
}
