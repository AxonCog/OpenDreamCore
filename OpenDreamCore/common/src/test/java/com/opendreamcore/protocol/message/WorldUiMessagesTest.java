package com.opendreamcore.protocol.message;

import com.opendreamcore.protocol.OdcByteArrayBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 世界 UI 消息编解码：Boss 条 / 名牌 / 物品提示。
 */
class WorldUiMessagesTest {

    @Test
    void bossBarRoundTrip() {
        for (BossBarSync.Action action : BossBarSync.Action.values()) {
            BossBarSync original = new BossBarSync("boss1", action, "末影龙", 66.5, "#E53935");
            OdcByteArrayBuf buf = new OdcByteArrayBuf();
            original.encode(buf);
            BossBarSync decoded = BossBarSync.decode(new OdcByteArrayBuf(buf.toByteArray()));
            assertEquals("boss1", decoded.id());
            assertEquals(action, decoded.action());
            assertEquals("末影龙", decoded.text());
            assertEquals(66.5, decoded.progress());
            assertEquals("#E53935", decoded.color());
        }
        assertThrows(IllegalArgumentException.class,
                () -> new BossBarSync("", BossBarSync.Action.ADD, "", 0, ""));
    }

    @Test
    void nameTagRoundTrip() {
        NameTagSync original = new NameTagSync(42, "★ VIP ★", "#FFD54F");
        OdcByteArrayBuf buf = new OdcByteArrayBuf();
        original.encode(buf);
        NameTagSync decoded = NameTagSync.decode(new OdcByteArrayBuf(buf.toByteArray()));
        assertEquals(42, decoded.entityId());
        assertEquals("★ VIP ★", decoded.text());
        assertEquals("#FFD54F", decoded.color());
    }

    @Test
    void itemTipRoundTrip() {
        ItemTipSync original = new ItemTipSync("minecraft:diamond", 5, 2000);
        OdcByteArrayBuf buf = new OdcByteArrayBuf();
        original.encode(buf);
        ItemTipSync decoded = ItemTipSync.decode(new OdcByteArrayBuf(buf.toByteArray()));
        assertEquals("minecraft:diamond", decoded.itemId());
        assertEquals(5, decoded.count());
        assertEquals(2000, decoded.durationMs());
        assertThrows(IllegalArgumentException.class, () -> new ItemTipSync("", 1, 100));
    }
}
