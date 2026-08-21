package com.opendreamcore.protocol.message;

import com.opendreamcore.protocol.OdcByteBuf;

/**
 * 云资源文件（S→C）：单个文件内容（AES-GCM 加密后传输）。
 * 落盘前用会话 key 解密。
 */
public final class CloudFile implements Message {

    private final String path;
    private final byte[] encrypted;

    public CloudFile(String path, byte[] encrypted) {
        if (path == null || path.isBlank() || path.length() > 256) {
            throw new IllegalArgumentException("文件路径非法: " + path);
        }
        this.path = path;
        this.encrypted = encrypted;
    }

    public String path() {
        return path;
    }

    /** 密文（含 iv 前缀）。 */
    public byte[] encrypted() {
        return encrypted;
    }

    @Override
    public void encode(OdcByteBuf buf) {
        buf.writeString(path);
        buf.writeVarInt(encrypted.length);
        buf.writeBytes(encrypted);
    }

    public static CloudFile decode(OdcByteBuf buf) {
        String path = buf.readString();
        int len = buf.readVarInt();
        if (len < 0 || len > 1 << 20) {
            throw new IllegalStateException("文件过大: " + len);
        }
        return new CloudFile(path, buf.readBytes(len));
    }
}
