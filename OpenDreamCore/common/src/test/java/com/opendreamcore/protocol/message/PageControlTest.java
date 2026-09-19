package com.opendreamcore.protocol.message;

import com.opendreamcore.protocol.OdcByteArrayBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * PageControl 的可空字段走"空串 = null"约定，而空串在流尾解码时踩过
 * readBytes(0) 返回 -1 的坑（『需要 0 实得 -1』）。这组测试把空 sessionId
 * 的往返钉死，回归了就知道。
 */
class PageControlTest {

    @Test
    void 带会话往返() {
        PageControl p = new PageControl(PageControl.Action.OPEN, "world_promo", "s-123", null);
        OdcByteArrayBuf buf = new OdcByteArrayBuf();
        p.encode(buf);
        PageControl back = PageControl.decode(new OdcByteArrayBuf(buf.toByteArray()));
        assertEquals(PageControl.Action.OPEN, back.action());
        assertEquals("world_promo", back.pageId());
        assertEquals("s-123", back.sessionId());
        assertNull(back.parentSessionId());
    }

    @Test
    void 空会话往返_不炸EOF() {
        // 这就是实机上炸『需要 0 实得 -1』的形态：sessionId/parentSessionId 全 null
        PageControl p = new PageControl(PageControl.Action.OPEN, "world_board", null, null);
        OdcByteArrayBuf buf = new OdcByteArrayBuf();
        p.encode(buf);
        PageControl back = PageControl.decode(new OdcByteArrayBuf(buf.toByteArray()));
        assertEquals(PageControl.Action.OPEN, back.action());
        assertEquals("world_board", back.pageId());
        assertNull(back.sessionId());
        assertNull(back.parentSessionId());
    }

    @Test
    void 关闭动作空载荷往返() {
        PageControl p = new PageControl(PageControl.Action.CLOSE, "hud_promo", null, null);
        OdcByteArrayBuf buf = new OdcByteArrayBuf();
        p.encode(buf);
        PageControl back = PageControl.decode(new OdcByteArrayBuf(buf.toByteArray()));
        assertEquals(PageControl.Action.CLOSE, back.action());
        assertEquals("hud_promo", back.pageId());
        assertNull(back.sessionId());
    }

    @Test
    void 纯空串读写() {
        // readBytes(0) 在流中间和流尾都得返回空数组，这是 PageControl 之外的
        // 所有消息类型共用的兜底契约
        OdcByteArrayBuf buf = new OdcByteArrayBuf();
        buf.writeString("");
        buf.writeString("非空");
        OdcByteArrayBuf in = new OdcByteArrayBuf(buf.toByteArray());
        assertEquals("", in.readString());
        assertEquals("非空", in.readString());
    }
}
