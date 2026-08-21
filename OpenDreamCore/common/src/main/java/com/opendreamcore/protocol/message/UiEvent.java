package com.opendreamcore.protocol.message;

import com.opendreamcore.protocol.OdcByteBuf;

/**
 * UI 事件（C→S）：点击/悬停/输入等。序列号防重放，服务端裁决。
 */
public final class UiEvent implements Message {

    public enum Trigger {
        CLICK(0),
        HOVER(1),
        PRESS(2),
        INPUT(3),
        SCROLL(4),
        KEY(5);

        private final int id;

        Trigger(int id) {
            this.id = id;
        }

        public int id() {
            return id;
        }

        public static Trigger byId(int id) {
            for (Trigger t : values()) {
                if (t.id == id) {
                    return t;
                }
            }
            throw new IllegalArgumentException("未知事件: " + id);
        }
    }

    private final String sessionId;
    private final String elementId;
    private final Trigger trigger;
    private final long sequence;
    private final String data;

    public UiEvent(String sessionId, String elementId, Trigger trigger, long sequence, String data) {
        if (sessionId == null || sessionId.isBlank() || sessionId.length() > 64) {
            throw new IllegalArgumentException("会话 id 非法");
        }
        if (elementId == null || elementId.isBlank() || elementId.length() > 64) {
            throw new IllegalArgumentException("元素 id 非法");
        }
        this.sessionId = sessionId;
        this.elementId = elementId;
        this.trigger = trigger;
        this.sequence = sequence;
        this.data = data;
    }

    public String sessionId() {
        return sessionId;
    }

    public String elementId() {
        return elementId;
    }

    public Trigger trigger() {
        return trigger;
    }

    public long sequence() {
        return sequence;
    }

    /** 事件附加数据（输入文本/滚轮量等，JSON 或明文）。 */
    public String data() {
        return data;
    }

    @Override
    public void encode(OdcByteBuf buf) {
        buf.writeString(sessionId);
        buf.writeString(elementId);
        buf.writeByte(trigger.id());
        buf.writeLong(sequence);
        buf.writeVarInt(data == null ? 0 : data.length());
        if (data != null) {
            buf.writeString(data);
        }
    }

    public static UiEvent decode(OdcByteBuf buf) {
        String sessionId = buf.readString();
        String elementId = buf.readString();
        Trigger trigger = Trigger.byId(buf.readByte());
        long sequence = buf.readLong();
        int dataLen = buf.readVarInt();
        String data = dataLen > 0 ? buf.readString() : null;
        return new UiEvent(sessionId, elementId, trigger, sequence, data);
    }
}
