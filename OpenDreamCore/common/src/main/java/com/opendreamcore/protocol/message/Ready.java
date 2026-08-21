package com.opendreamcore.protocol.message;

import com.opendreamcore.protocol.OdcByteBuf;

/**
 * 客户端就绪（C→S）：协议版本 + 客户端模组版本 + 能力位。
 */
public final class Ready implements Message {

    private final int protocolVersion;
    private final String modVersion;
    private final int capabilities;

    public Ready(int protocolVersion, String modVersion, int capabilities) {
        if (protocolVersion <= 0) {
            throw new IllegalArgumentException("协议版本非法: " + protocolVersion);
        }
        if (modVersion == null || modVersion.isBlank() || modVersion.length() > 32) {
            throw new IllegalArgumentException("模组版本非法: " + modVersion);
        }
        this.protocolVersion = protocolVersion;
        this.modVersion = modVersion;
        this.capabilities = capabilities;
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

    @Override
    public void encode(OdcByteBuf buf) {
        buf.writeVarInt(protocolVersion);
        buf.writeString(modVersion);
        buf.writeByte(capabilities);
    }

    public static Ready decode(OdcByteBuf buf) {
        int version = buf.readVarInt();
        String modVersion = buf.readString();
        int caps = buf.readByte();
        return new Ready(version, modVersion, caps);
    }
}
