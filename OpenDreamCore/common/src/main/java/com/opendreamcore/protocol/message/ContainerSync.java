package com.opendreamcore.protocol.message;

import com.opendreamcore.protocol.OdcByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * 容器内容同步（S→C）：服务端把真实容器（箱子/熔炉等）的槽位内容推给客户端。
 * 打开容器 UI 时发全量快照；脚本 Container.刷新 / 外部变更后重发。
 * 槽位物品用注册表 id（"minecraft:diamond_sword"）+ 数量表示，空槽不发送。
 * 末尾携带**光标物品**（cursorItemId/cursorCount）：槽位拖放交互时鼠标上拾起的物品
 * （服务端权威；null/空 = 无光标）。
 */
public final class ContainerSync implements Message {

    /** 单个槽位：slot 槽位号，itemId 物品注册表 id（空串 = 空槽），count 数量。 */
    public record Slot(int slot, String itemId, int count) {
    }

    private final String sessionId;
    private final String type;
    private final String title;
    private final int size;
    private final List<Slot> slots;
    private final String cursorItemId;
    private final int cursorCount;

    public ContainerSync(String sessionId, String type, String title, int size, List<Slot> slots) {
        this(sessionId, type, title, size, slots, null, 0);
    }

    public ContainerSync(String sessionId, String type, String title, int size, List<Slot> slots,
                         String cursorItemId, int cursorCount) {
        if (sessionId == null || sessionId.isBlank() || sessionId.length() > 64) {
            throw new IllegalArgumentException("会话 id 非法");
        }
        this.sessionId = sessionId;
        this.type = type == null ? "" : type;
        this.title = title == null ? "" : title;
        this.size = size;
        this.slots = slots == null ? List.of() : List.copyOf(slots);
        this.cursorItemId = cursorItemId == null || cursorItemId.isEmpty() ? null : cursorItemId;
        this.cursorCount = this.cursorItemId == null ? 0 : Math.max(0, cursorCount);
    }

    public String sessionId() {
        return sessionId;
    }

    public String type() {
        return type;
    }

    public String title() {
        return title;
    }

    public int size() {
        return size;
    }

    public List<Slot> slots() {
        return slots;
    }

    /** 光标物品 id（null = 无光标）。 */
    public String cursorItemId() {
        return cursorItemId;
    }

    public int cursorCount() {
        return cursorCount;
    }

    @Override
    public void encode(OdcByteBuf buf) {
        buf.writeString(sessionId);
        buf.writeString(type);
        buf.writeString(title);
        buf.writeVarInt(size);
        buf.writeVarInt(slots.size());
        for (Slot slot : slots) {
            buf.writeVarInt(slot.slot());
            buf.writeString(slot.itemId() == null ? "" : slot.itemId());
            buf.writeVarInt(slot.count());
        }
        buf.writeString(cursorItemId == null ? "" : cursorItemId);
        buf.writeVarInt(cursorCount);
    }

    public static ContainerSync decode(OdcByteBuf buf) {
        String sessionId = buf.readString();
        String type = buf.readString();
        String title = buf.readString();
        int size = buf.readVarInt();
        int count = buf.readVarInt();
        if (count < 0 || count > 10000) {
            throw new IllegalStateException("槽位数非法: " + count);
        }
        List<Slot> slots = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int slot = buf.readVarInt();
            String itemId = buf.readString();
            int itemCount = buf.readVarInt();
            slots.add(new Slot(slot, itemId.isEmpty() ? null : itemId, itemCount));
        }
        String cursorItemId = buf.readString();
        int cursorCount = buf.readVarInt();
        return new ContainerSync(sessionId, type, title, size, slots,
                cursorItemId.isEmpty() ? null : cursorItemId, cursorCount);
    }
}
