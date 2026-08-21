package com.opendreamcore.protocol.message;

import com.opendreamcore.protocol.OdcByteArrayBuf;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 容器内容同步编解码（会话/类型/标题/尺寸/槽位物品）。
 */
class ContainerSyncTest {

    @Test
    void roundTrip() {
        ContainerSync original = new ContainerSync("sess01", "minecraft:chest", "箱子", 27,
                List.of(new ContainerSync.Slot(0, "minecraft:diamond_sword", 1),
                        new ContainerSync.Slot(5, "minecraft:bread", 64),
                        new ContainerSync.Slot(13, null, 0)));

        OdcByteArrayBuf buf = new OdcByteArrayBuf();
        original.encode(buf);
        ContainerSync decoded = ContainerSync.decode(new OdcByteArrayBuf(buf.toByteArray()));

        assertEquals("sess01", decoded.sessionId());
        assertEquals("minecraft:chest", decoded.type());
        assertEquals("箱子", decoded.title());
        assertEquals(27, decoded.size());
        assertEquals(3, decoded.slots().size());
        assertEquals(0, decoded.slots().get(0).slot());
        assertEquals("minecraft:diamond_sword", decoded.slots().get(0).itemId());
        assertEquals(1, decoded.slots().get(0).count());
        assertEquals("minecraft:bread", decoded.slots().get(1).itemId());
        assertEquals(64, decoded.slots().get(1).count());
        assertNull(decoded.slots().get(2).itemId(), "空槽 itemId 应为 null");
    }

    @Test
    void emptySlots() {
        ContainerSync original = new ContainerSync("s2", "", "", 0, List.of());
        OdcByteArrayBuf buf = new OdcByteArrayBuf();
        original.encode(buf);
        ContainerSync decoded = ContainerSync.decode(new OdcByteArrayBuf(buf.toByteArray()));
        assertEquals("s2", decoded.sessionId());
        assertTrue(decoded.slots().isEmpty());
    }
}
