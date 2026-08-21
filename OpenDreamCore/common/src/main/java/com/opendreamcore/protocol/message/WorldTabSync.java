package com.opendreamcore.protocol.message;

import com.opendreamcore.protocol.OdcByteBuf;

/**
 * 世界页签同步（S→C）：服务端远程切换玩家世界面板的激活页签
 * （Screen.设置世界页签 / Screen.广播世界页签）。
 */
public final class WorldTabSync implements Message {

    private final String pageId;
    private final String tab;

    public WorldTabSync(String pageId, String tab) {
        if (pageId == null || pageId.isBlank() || pageId.length() > 64) {
            throw new IllegalArgumentException("页面 id 非法: " + pageId);
        }
        this.pageId = pageId;
        this.tab = tab == null ? "" : tab;
    }

    public String pageId() {
        return pageId;
    }

    public String tab() {
        return tab;
    }

    @Override
    public void encode(OdcByteBuf buf) {
        buf.writeString(pageId);
        buf.writeString(tab);
    }

    public static WorldTabSync decode(OdcByteBuf buf) {
        String pageId = buf.readString();
        String tab = buf.readString();
        return new WorldTabSync(pageId, tab);
    }
}
