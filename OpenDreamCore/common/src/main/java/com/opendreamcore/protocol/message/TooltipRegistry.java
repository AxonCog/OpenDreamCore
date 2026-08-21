package com.opendreamcore.protocol.message;

import com.opendreamcore.protocol.OdcByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端 tooltip 注册表（S→C）：元素 id → 提示文本 + 可选样式 + 权限白名单。
 * 客户端渲染时优先于 YAML 静态 tooltip；样式缺省走客户端默认。
 * 进服后服务端主动下发全量；客户端缺块可用 tooltip_resync 重传（简化实现不分块）。
 */
public final class TooltipRegistry implements Message {

    /**
     * 条目：元素 id + 提示文本（可含 {{vars.x}} 插值）+ 可选样式
     * （color 文字色 / background 背景 / border 边框 / width 最大宽度，均为 "#RRGGBB" 或 "0xAARRGGBB" 格式，
     * width 为像素；空/null = 缺省）+ 权限白名单（空 = 所有人）。
     */
    public record Entry(String elementId, String text, String color, String background,
                        String border, double width, String permission) {
        public Entry(String elementId, String text) {
            this(elementId, text, null, null, null, 0, null);
        }
    }

    private final List<Entry> entries;

    public TooltipRegistry(List<Entry> entries) {
        this.entries = entries == null ? new ArrayList<>() : List.copyOf(entries);
    }

    public List<Entry> entries() {
        return entries;
    }

    @Override
    public void encode(OdcByteBuf buf) {
        buf.writeVarInt(entries.size());
        for (Entry entry : entries) {
            buf.writeString(entry.elementId());
            buf.writeString(entry.text());
            int flags = 0;
            if (entry.color() != null && !entry.color().isEmpty()) {
                flags |= 1;
            }
            if (entry.background() != null && !entry.background().isEmpty()) {
                flags |= 2;
            }
            if (entry.border() != null && !entry.border().isEmpty()) {
                flags |= 4;
            }
            if (entry.width() > 0) {
                flags |= 8;
            }
            if (entry.permission() != null && !entry.permission().isEmpty()) {
                flags |= 16;
            }
            buf.writeByte(flags);
            if ((flags & 1) != 0) {
                buf.writeString(entry.color());
            }
            if ((flags & 2) != 0) {
                buf.writeString(entry.background());
            }
            if ((flags & 4) != 0) {
                buf.writeString(entry.border());
            }
            if ((flags & 8) != 0) {
                buf.writeString(String.valueOf(entry.width()));
            }
            if ((flags & 16) != 0) {
                buf.writeString(entry.permission());
            }
        }
    }

    public static TooltipRegistry decode(OdcByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > 100000) {
            throw new IllegalStateException("tooltip 数量非法: " + count);
        }
        List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String elementId = buf.readString();
            String text = buf.readString();
            int flags = buf.readByte();
            String color = (flags & 1) != 0 ? buf.readString() : null;
            String background = (flags & 2) != 0 ? buf.readString() : null;
            String border = (flags & 4) != 0 ? buf.readString() : null;
            double width = (flags & 8) != 0 ? Double.parseDouble(buf.readString()) : 0;
            String permission = (flags & 16) != 0 ? buf.readString() : null;
            entries.add(new Entry(elementId, text, color, background, border, width, permission));
        }
        return new TooltipRegistry(entries);
    }
}
