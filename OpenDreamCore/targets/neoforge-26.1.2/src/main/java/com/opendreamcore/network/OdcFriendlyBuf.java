package com.opendreamcore.network;

import com.opendreamcore.protocol.OdcByteBuf;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * MC 网络缓冲 → 协议读写接口的适配。
 */
public final class OdcFriendlyBuf implements OdcByteBuf {

    private final FriendlyByteBuf buf;

    public OdcFriendlyBuf(FriendlyByteBuf buf) {
        this.buf = buf;
    }

    public static OdcFriendlyBuf of(FriendlyByteBuf buf) {
        return new OdcFriendlyBuf(buf);
    }

    @Override
    public void writeByte(int b) {
        buf.writeByte(b);
    }

    @Override
    public void writeVarInt(int value) {
        buf.writeVarInt(value);
    }

    @Override
    public void writeVarLong(long value) {
        buf.writeVarLong(value);
    }

    @Override
    public void writeString(String s) {
        buf.writeUtf(s);
    }

    @Override
    public void writeBytes(byte[] data) {
        buf.writeBytes(data);
    }

    @Override
    public void writeLong(long value) {
        buf.writeLong(value);
    }

    @Override
    public void writeInt(int value) {
        buf.writeInt(value);
    }

    @Override
    public int readByte() {
        return buf.readByte();
    }

    @Override
    public int readVarInt() {
        return buf.readVarInt();
    }

    @Override
    public long readVarLong() {
        return buf.readVarLong();
    }

    @Override
    public String readString() {
        return buf.readUtf();
    }

    @Override
    public byte[] readBytes(int length) {
        byte[] data = new byte[length];
        buf.readBytes(data);
        return data;
    }

    @Override
    public long readLong() {
        return buf.readLong();
    }

    @Override
    public int readInt() {
        return buf.readInt();
    }

    @Override
    public int readableBytes() {
        return buf.readableBytes();
    }

    public static Identifier channel(String path) {
        return Identifier.fromNamespaceAndPath(com.opendreamcore.protocol.Protocol.NAMESPACE, path);
    }
}
