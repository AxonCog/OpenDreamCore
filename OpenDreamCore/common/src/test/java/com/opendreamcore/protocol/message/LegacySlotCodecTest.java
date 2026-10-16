package com.opendreamcore.protocol.message;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LegacySlotCodecTest {
    @Test
    void containerIdentifierOwnsSlotNotMouseButton() {
        assertEquals(45, LegacySlotCodec.identifierSlot("container_45"));
        assertEquals(46, LegacySlotCodec.identifierSlot("container_46"));
        assertEquals("L", LegacySlotCodec.action(0, "PICKUP"));
        assertEquals("R", LegacySlotCodec.action(1, "PICKUP"));
    }

    @Test
    void mapsSupportedServerActionsAndRejectsUnknown() {
        assertEquals("Q", LegacySlotCodec.action(0, "QUICK_MOVE"));
        assertEquals("A", LegacySlotCodec.action(0, "PICKUP_ALL"));
        assertEquals("D", LegacySlotCodec.action(0, "SWAP"));
        assertNull(LegacySlotCodec.action(0, "THROW"));
        assertNull(LegacySlotCodec.action(2, "PICKUP"));
    }

    @Test
    void doesNotTreatButtonAsContainerSlot() {
        assertEquals(-1, LegacySlotCodec.identifierSlot("container_button"));
        assertEquals(-1, LegacySlotCodec.numericSlot(0.0));
        assertEquals(-1, LegacySlotCodec.numericSlot(-1));
    }
}
