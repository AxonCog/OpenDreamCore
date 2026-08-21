package com.opendreamcore.protocol.message;

import com.opendreamcore.protocol.OdcByteBuf;

/**
 * 世界元素状态同步（S→C）：服务端远程切换世界面板元素的可见性/可用性
 * （Screen.设置元素可见 / Screen.设置元素可用 及其广播版本）。
 */
public final class WorldElementState implements Message {

    /** 状态模式：0 = 可见性，1 = 可用性。 */
    public static final int MODE_VISIBLE = 0;
    public static final int MODE_ENABLED = 1;

    private final String pageId;
    private final String elementId;
    private final int mode;
    private final boolean value;

    public WorldElementState(String pageId, String elementId, int mode, boolean value) {
        if (pageId == null || pageId.isBlank() || pageId.length() > 64) {
            throw new IllegalArgumentException("页面 id 非法: " + pageId);
        }
        if (elementId == null || elementId.isBlank() || elementId.length() > 64) {
            throw new IllegalArgumentException("元素 id 非法: " + elementId);
        }
        this.pageId = pageId;
        this.elementId = elementId;
        this.mode = mode == MODE_VISIBLE || mode == MODE_ENABLED ? mode : MODE_VISIBLE;
        this.value = value;
    }

    public String pageId() {
        return pageId;
    }

    public String elementId() {
        return elementId;
    }

    public int mode() {
        return mode;
    }

    public boolean value() {
        return value;
    }

    @Override
    public void encode(OdcByteBuf buf) {
        buf.writeString(pageId);
        buf.writeString(elementId);
        buf.writeByte(mode);
        buf.writeByte(value ? 1 : 0);
    }

    public static WorldElementState decode(OdcByteBuf buf) {
        String pageId = buf.readString();
        String elementId = buf.readString();
        int mode = buf.readByte();
        boolean value = buf.readByte() != 0;
        return new WorldElementState(pageId, elementId, mode, value);
    }
}
