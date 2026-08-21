package com.opendreamcore.protocol.message;

import com.opendreamcore.protocol.OdcByteBuf;

/**
 * 物品提示同步（S→C）：屏幕中央显示物品图标+名字的浮窗（原版拾取提示风格），
 * 时长后淡出。itemId 为注册表 id（"minecraft:diamond"）。
 */
public final class ItemTipSync implements Message {

    private final String itemId;
    private final int count;
    private final int durationMs;

    public ItemTipSync(String itemId, int count, int durationMs) {
        if (itemId == null || itemId.isBlank()) {
            throw new IllegalArgumentException("物品 id 非法");
        }
        this.itemId = itemId;
        this.count = Math.max(1, count);
        this.durationMs = Math.max(200, durationMs);
    }

    public String itemId() {
        return itemId;
    }

    public int count() {
        return count;
    }

    public int durationMs() {
        return durationMs;
    }

    @Override
    public void encode(OdcByteBuf buf) {
        buf.writeString(itemId);
        buf.writeVarInt(count);
        buf.writeVarInt(durationMs);
    }

    public static ItemTipSync decode(OdcByteBuf buf) {
        String itemId = buf.readString();
        int count = buf.readVarInt();
        int durationMs = buf.readVarInt();
        return new ItemTipSync(itemId, count, durationMs);
    }
}
