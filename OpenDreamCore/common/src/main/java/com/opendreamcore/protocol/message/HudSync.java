package com.opendreamcore.protocol.message;

import com.opendreamcore.protocol.OdcByteBuf;

/**
 * HUD 同步（S→C）：服务端控制客户端常驻 HUD。
 * 模式：HUD 按玩家（只发给指定玩家）；GHUD 全局常驻（全体 + 新进服自动挂）；
 * STATIC 静态广播（全体，内容为纯公告，页面不用变量/占位符）。
 * remove=true 时卸载当前 HUD；否则 content 为页面 YAML（加密同 PageSync）。
 */
public final class HudSync implements Message {

    public enum Mode {
        HUD(0), GHUD(1), STATIC(2);

        final int id;

        Mode(int id) {
            this.id = id;
        }

        static Mode byId(int id) {
            for (Mode mode : values()) {
                if (mode.id == id) {
                    return mode;
                }
            }
            throw new IllegalArgumentException("未知 HUD 模式: " + id);
        }
    }

    private final String pageId;
    private final Mode mode;
    private final byte[] content;
    private final boolean encrypted;
    private final boolean remove;
    private final String sessionId;

    /** 卸载构造。 */
    public HudSync(Mode mode, String sessionId) {
        this("", mode, new byte[]{0}, false, true, sessionId);
    }

    /** 挂载构造（明文或密文）。 */
    public HudSync(String pageId, Mode mode, byte[] content, boolean encrypted, String sessionId) {
        this(pageId, mode, content, encrypted, false, sessionId);
    }

    private HudSync(String pageId, Mode mode, byte[] content, boolean encrypted, boolean remove, String sessionId) {
        if (mode == null) {
            throw new IllegalArgumentException("HUD 模式不能为空");
        }
        this.pageId = pageId == null ? "" : pageId;
        this.mode = mode;
        this.content = content == null ? new byte[0] : content;
        this.encrypted = encrypted;
        this.remove = remove;
        this.sessionId = sessionId == null ? "" : sessionId;
    }

    public String pageId() {
        return pageId;
    }

    public Mode mode() {
        return mode;
    }

    public byte[] content() {
        return content;
    }

    public boolean encrypted() {
        return encrypted;
    }

    public boolean remove() {
        return remove;
    }

    public String sessionId() {
        return sessionId;
    }

    @Override
    public void encode(OdcByteBuf buf) {
        buf.writeString(pageId);
        buf.writeByte(mode.id);
        buf.writeByte(remove ? 1 : 0);
        buf.writeString(sessionId);
        buf.writeByte(encrypted ? 1 : 0);
        buf.writeVarInt(content.length);
        buf.writeBytes(content);
    }

    public static HudSync decode(OdcByteBuf buf) {
        String pageId = buf.readString();
        Mode mode = Mode.byId(buf.readByte());
        boolean remove = buf.readByte() != 0;
        String sessionId = buf.readString();
        boolean encrypted = buf.readByte() != 0;
        int len = buf.readVarInt();
        if (len < 0 || len > 1 << 20) {
            throw new IllegalStateException("HUD 内容长度非法: " + len);
        }
        byte[] content = buf.readBytes(len);
        return new HudSync(pageId, mode, content, encrypted, remove, sessionId);
    }
}
