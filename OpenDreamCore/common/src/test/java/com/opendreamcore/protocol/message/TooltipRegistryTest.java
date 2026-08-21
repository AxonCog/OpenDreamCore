package com.opendreamcore.protocol.message;

import com.opendreamcore.protocol.OdcByteArrayBuf;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * tooltip 注册表消息编解码。
 */
class TooltipRegistryTest {

    @Test
    void roundTrip() {
        TooltipRegistry original = new TooltipRegistry(List.of(
                new TooltipRegistry.Entry("buy_sword", "点击购买钻石剑（50 金币）"),
                new TooltipRegistry.Entry("coin_line", "你的金币余额")));
        OdcByteArrayBuf buf = new OdcByteArrayBuf();
        original.encode(buf);
        TooltipRegistry decoded = TooltipRegistry.decode(new OdcByteArrayBuf(buf.toByteArray()));
        assertEquals(2, decoded.entries().size());
        assertEquals("buy_sword", decoded.entries().get(0).elementId());
        assertEquals("点击购买钻石剑（50 金币）", decoded.entries().get(0).text());
    }

    @Test
    void resyncRoundTrip() {
        OdcByteArrayBuf buf = new OdcByteArrayBuf();
        new TooltipResync().encode(buf);
        assertNotNull(TooltipResync.decode(new OdcByteArrayBuf(buf.toByteArray())));
    }
}
