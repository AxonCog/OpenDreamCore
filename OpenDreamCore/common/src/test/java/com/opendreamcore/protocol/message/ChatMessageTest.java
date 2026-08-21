package com.opendreamcore.protocol.message;

import com.opendreamcore.protocol.OdcByteArrayBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 聊天通道消息编解码（动作/通道/id/富文本内容）。
 */
class ChatMessageTest {

    @Test
    void roundTripAdd() {
        ChatMessage original = new ChatMessage("系统", ChatMessage.Action.ADD, 42L,
                "§a[系统] §f欢迎");
        OdcByteArrayBuf buf = new OdcByteArrayBuf();
        original.encode(buf);
        ChatMessage decoded = ChatMessage.decode(new OdcByteArrayBuf(buf.toByteArray()));

        assertEquals("系统", decoded.channel());
        assertEquals(ChatMessage.Action.ADD, decoded.action());
        assertEquals(42L, decoded.id());
        assertEquals("§a[系统] §f欢迎", decoded.text());
    }

    @Test
    void roundTripAllActionsAndBigIds() {
        for (ChatMessage.Action action : ChatMessage.Action.values()) {
            ChatMessage original = new ChatMessage("all", action, 0x1_0000_0000L + 7, "内容");
            OdcByteArrayBuf buf = new OdcByteArrayBuf();
            original.encode(buf);
            ChatMessage decoded = ChatMessage.decode(new OdcByteArrayBuf(buf.toByteArray()));
            assertEquals(action, decoded.action());
            assertEquals(0x1_0000_0000L + 7, decoded.id(), "64 位 id 保持");
            assertEquals("内容", decoded.text());
        }
    }

    @Test
    void blankChannelRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new ChatMessage("", ChatMessage.Action.ADD, 1, "x"));
        assertThrows(IllegalArgumentException.class,
                () -> new ChatMessage(null, ChatMessage.Action.CLEAR, 1, "x"));
    }
}
