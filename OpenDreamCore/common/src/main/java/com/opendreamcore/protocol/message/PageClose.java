package com.opendreamcore.protocol.message;

import com.opendreamcore.protocol.OdcByteBuf;

/**
 * 页面关闭通知（C→S）：客户端关闭页面时告知服务端。
 * 服务端据此清理会话与容器绑定（玩家 ESC 关页时服务端无法主动感知）。
 */
public final class PageClose implements Message {

    private final String sessionId;

    public PageClose(String sessionId) {
        if (sessionId == null || sessionId.isBlank() || sessionId.length() > 64) {
            throw new IllegalArgumentException("会话 id 非法");
        }
        this.sessionId = sessionId;
    }

    public String sessionId() {
        return sessionId;
    }

    @Override
    public void encode(OdcByteBuf buf) {
        buf.writeString(sessionId);
    }

    public static PageClose decode(OdcByteBuf buf) {
        return new PageClose(buf.readString());
    }
}
