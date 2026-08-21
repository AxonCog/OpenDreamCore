package com.opendreamcore.protocol.message;

import com.opendreamcore.protocol.OdcByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * 页面布局（双向）：
 * C→S 保存编辑（客户端把元素位置补丁发回，服务端校验租约后落盘）；
 * S→C 广播（服务端把已保存的布局覆盖下发，打开页面时也会附带）。
 */
public final class PageLayout implements Message {

    /** 条目：元素 id + 绝对坐标。 */
    public record Entry(String elementId, double x, double y) {
    }

    private final String pageId;
    private final List<Entry> entries;

    public PageLayout(String pageId, List<Entry> entries) {
        if (pageId == null || pageId.isBlank() || pageId.length() > 64) {
            throw new IllegalArgumentException("页面 id 非法: " + pageId);
        }
        this.pageId = pageId;
        this.entries = entries == null ? new ArrayList<>() : List.copyOf(entries);
    }

    public String pageId() {
        return pageId;
    }

    public List<Entry> entries() {
        return entries;
    }

    @Override
    public void encode(OdcByteBuf buf) {
        buf.writeString(pageId);
        buf.writeVarInt(entries.size());
        for (Entry entry : entries) {
            buf.writeString(entry.elementId());
            buf.writeLong(Double.doubleToLongBits(entry.x()));
            buf.writeLong(Double.doubleToLongBits(entry.y()));
        }
    }

    public static PageLayout decode(OdcByteBuf buf) {
        String pageId = buf.readString();
        int count = buf.readVarInt();
        if (count < 0 || count > 100000) {
            throw new IllegalStateException("布局条目数非法: " + count);
        }
        List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String elementId = buf.readString();
            double x = Double.longBitsToDouble(buf.readLong());
            double y = Double.longBitsToDouble(buf.readLong());
            entries.add(new Entry(elementId, x, y));
        }
        return new PageLayout(pageId, entries);
    }
}
