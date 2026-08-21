package com.opendreamcore.protocol.message;

import com.opendreamcore.protocol.OdcByteBuf;

import java.nio.charset.StandardCharsets;

/**
 * 页面同步（S→C）：服务端把页面 YAML 推给客户端。
 * 默认明文；encrypted=true 时 content 为 AES-GCM 密文（会话 key 随 ready_ack 下发），
 * 客户端用会话 key 解密后解析入库，再等 page_control 决定何时打开。
 */
public final class PageSync implements Message {

    private final String pageId;
    private final byte[] content;
    private final boolean encrypted;

    /** 明文构造。 */
    public PageSync(String pageId, String yaml) {
        this(pageId, yaml.getBytes(StandardCharsets.UTF_8), false);
    }

    /** 密文构造（服务端用会话 key 加密后传字节）。 */
    public PageSync(String pageId, byte[] content, boolean encrypted) {
        if (pageId == null || pageId.isBlank() || pageId.length() > 64) {
            throw new IllegalArgumentException("页面 id 非法: " + pageId);
        }
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("页面内容为空: " + pageId);
        }
        this.pageId = pageId;
        this.content = content;
        this.encrypted = encrypted;
    }

    public String pageId() {
        return pageId;
    }

    /** 内容字节（encrypted=true 时是密文，需先解密）。 */
    public byte[] content() {
        return content;
    }

    public boolean encrypted() {
        return encrypted;
    }

    /** 明文内容（仅未加密时可用；加密包请用 content()+会话 key 解密）。 */
    public String yaml() {
        if (encrypted) {
            throw new IllegalStateException("页面已加密，请先解密");
        }
        return new String(content, StandardCharsets.UTF_8);
    }

    @Override
    public void encode(OdcByteBuf buf) {
        buf.writeString(pageId);
        buf.writeByte(encrypted ? 1 : 0);
        buf.writeVarInt(content.length);
        buf.writeBytes(content);
    }

    public static PageSync decode(OdcByteBuf buf) {
        String pageId = buf.readString();
        boolean encrypted = buf.readByte() != 0;
        int len = buf.readVarInt();
        if (len < 0 || len > 1 << 20) {
            throw new IllegalStateException("页面内容长度非法: " + len);
        }
        byte[] content = buf.readBytes(len);
        return new PageSync(pageId, content, encrypted);
    }
}
