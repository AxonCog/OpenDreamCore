package com.opendreamcore.protocol.message;

import com.opendreamcore.protocol.OdcByteBuf;

/**
 * 世界布局保存确认（S→C）：服务端烘焙完成后回执，客户端据此显示成功/失败反馈
 * （baked &gt; 0 = 已写入 N 项/键；0 = 无租约或文件不可写）。
 */
public final class WorldSaveAck implements Message {

    private final String pageId;
    private final int baked;
    private final String message;

    public WorldSaveAck(String pageId, int baked, String message) {
        this.pageId = pageId == null ? "" : pageId;
        this.baked = baked;
        this.message = message == null ? "" : message;
    }

    public String pageId() {
        return pageId;
    }

    public int baked() {
        return baked;
    }

    public String message() {
        return message;
    }

    @Override
    public void encode(OdcByteBuf buf) {
        buf.writeString(pageId);
        buf.writeVarInt(baked);
        buf.writeString(message);
    }

    public static WorldSaveAck decode(OdcByteBuf buf) {
        String pageId = buf.readString();
        int baked = buf.readVarInt();
        if (baked < 0 || baked > 1000000) {
            throw new IllegalStateException("保存确认烘焙数非法: " + baked);
        }
        String message = buf.readString();
        return new WorldSaveAck(pageId, baked, message);
    }
}
