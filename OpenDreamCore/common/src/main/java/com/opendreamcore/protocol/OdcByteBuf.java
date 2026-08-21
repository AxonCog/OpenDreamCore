package com.opendreamcore.protocol;

/**
 * 二进制读写接口（大端，varint）。
 * common 零 MC 依赖：各平台（NeoForge/Fabric/Bukkit）把 MC 的字节缓冲适配成这个接口。
 */
public interface OdcByteBuf {

    // ---- 写 ----
    void writeByte(int b);

    void writeVarInt(int value);

    /** 64 位 varint（无符号 zigzag 前的原值，负数按补码高位续写）。 */
    void writeVarLong(long value);

    void writeString(String s);

    void writeBytes(byte[] data);

    void writeLong(long value);

    void writeInt(int value);

    // ---- 读 ----
    int readByte();

    int readVarInt();

    long readVarLong();

    String readString();

    byte[] readBytes(int length);

    long readLong();

    int readInt();

    int readableBytes();
}
