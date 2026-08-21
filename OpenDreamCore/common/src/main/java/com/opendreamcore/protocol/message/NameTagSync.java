package com.opendreamcore.protocol.message;

import com.opendreamcore.protocol.OdcByteBuf;

/**
 * 头顶名牌同步（S→C）：给指定实体 id 设置/移除世界内名牌文字（客户端 billboard 渲染）。
 * text 为空 = 移除。
 */
public final class NameTagSync implements Message {

    private final int entityId;
    private final String text;
    private final String color;

    public NameTagSync(int entityId, String text, String color) {
        this.entityId = entityId;
        this.text = text == null ? "" : text;
        this.color = color == null ? "" : color;
    }

    public int entityId() {
        return entityId;
    }

    public String text() {
        return text;
    }

    public String color() {
        return color;
    }

    @Override
    public void encode(OdcByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeString(text);
        buf.writeString(color);
    }

    public static NameTagSync decode(OdcByteBuf buf) {
        int entityId = buf.readVarInt();
        String text = buf.readString();
        String color = buf.readString();
        return new NameTagSync(entityId, text, color);
    }
}
