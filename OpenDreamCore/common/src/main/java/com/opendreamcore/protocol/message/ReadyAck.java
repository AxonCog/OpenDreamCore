package com.opendreamcore.protocol.message;

import com.opendreamcore.protocol.OdcByteBuf;

/**
 * 就绪确认（S→C）：服务端模组版本 + 能力位 + 资源包密钥（可空）。
 */
public final class ReadyAck implements Message {

    private final int protocolVersion;
    private final String modVersion;
    private final int capabilities;
    private final byte[] resourceKey;

    public ReadyAck(int protocolVersion, String modVersion, int capabilities, byte[] resourceKey) {
        if (protocolVersion <= 0) {
            throw new IllegalArgumentException("协议版本非法: " + protocolVersion);
        }
        if (modVersion == null || modVersion.isBlank() || modVersion.length() > 32) {
            throw new IllegalArgumentException("模组版本非法: " + modVersion);
        }
        this.protocolVersion = protocolVersion;
        this.modVersion = modVersion;
        this.capabilities = capabilities;
        this.resourceKey = resourceKey == null ? new byte[0] : resourceKey.clone();
    }

    public int protocolVersion() {
        return protocolVersion;
    }

    public String modVersion() {
        return modVersion;
    }

    public int capabilities() {
        return capabilities;
    }

    /** 空数组 = 资源包未加密。 */
    public byte[] resourceKey() {
        return resourceKey.clone();
    }

    @Override
    public void encode(OdcByteBuf buf) {
        buf.writeVarInt(protocolVersion);
        buf.writeString(modVersion);
        buf.writeByte(capabilities);
        buf.writeVarInt(resourceKey.length);
        buf.writeBytes(resourceKey);
    }

    public static ReadyAck decode(OdcByteBuf buf) {
        int version = buf.readVarInt();
        String modVersion = buf.readString();
        int caps = buf.readByte();
        int keyLen = buf.readVarInt();
        if (keyLen < 0 || keyLen > 256) {
            throw new IllegalArgumentException("资源密钥长度非法: " + keyLen);
        }
        byte[] key = keyLen == 0 ? new byte[0] : buf.readBytes(keyLen);
        return new ReadyAck(version, modVersion, caps, key);
    }
}
