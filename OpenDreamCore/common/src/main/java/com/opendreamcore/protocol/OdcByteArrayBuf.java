package com.opendreamcore.protocol;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * 测试/纯逻辑用的字节缓冲实现（大端，varint）。
 * 各平台对接 MC 缓冲时实现 OdcByteBuf 接口即可。
 */
public final class OdcByteArrayBuf implements OdcByteBuf {

    private final ByteArrayOutputStream out;
    private ByteArrayInputStream in;

    public OdcByteArrayBuf() {
        this.out = new ByteArrayOutputStream();
    }

    public OdcByteArrayBuf(byte[] data) {
        this.out = new ByteArrayOutputStream();
        this.in = new ByteArrayInputStream(data);
    }

    public byte[] toByteArray() {
        return out.toByteArray();
    }

    @Override
    public void writeByte(int b) {
        out.write(b & 0xFF);
    }

    @Override
    public void writeVarInt(int value) {
        int v = value;
        while ((v & ~0x7F) != 0) {
            out.write((v & 0x7F) | 0x80);
            v >>>= 7;
        }
        out.write(v);
    }

    @Override
    public void writeVarLong(long value) {
        long v = value;
        while ((v & ~0x7FL) != 0) {
            out.write((int) (v & 0x7F) | 0x80);
            v >>>= 7;
        }
        out.write((int) v);
    }

    @Override
    public void writeString(String s) {
        byte[] data = s.getBytes(StandardCharsets.UTF_8);
        writeVarInt(data.length);
        out.writeBytes(data);
    }

    @Override
    public void writeBytes(byte[] data) {
        out.writeBytes(data);
    }

    @Override
    public void writeLong(long value) {
        for (int i = 7; i >= 0; i--) {
            out.write((int) (value >>> (i * 8)) & 0xFF);
        }
    }

    @Override
    public void writeInt(int value) {
        out.write((value >>> 24) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    @Override
    public int readByte() {
        return in.read();
    }

    @Override
    public int readVarInt() {
        int result = 0;
        int shift = 0;
        while (true) {
            int b = in.read();
            if (b == -1) {
                throw new IllegalStateException("varint 越界");
            }
            result |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return result;
            }
            shift += 7;
            if (shift > 28) {
                throw new IllegalStateException("varint 过长");
            }
        }
    }

    @Override
    public long readVarLong() {
        long result = 0;
        int shift = 0;
        while (true) {
            int b = in.read();
            if (b == -1) {
                throw new IllegalStateException("varlong 越界");
            }
            result |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return result;
            }
            shift += 7;
            if (shift > 63) {
                throw new IllegalStateException("varlong 过长");
            }
        }
    }

    @Override
    public String readString() {
        int len = readVarInt();
        if (len < 0 || len > 65536) {
            throw new IllegalStateException("字符串长度非法: " + len);
        }
        byte[] data = readBytes(len);
        return new String(data, StandardCharsets.UTF_8);
    }

    @Override
    public byte[] readBytes(int length) {
        if (length < 0) {
            throw new IllegalStateException("长度非法: " + length);
        }
        byte[] data = new byte[length];
        int read = in.readNBytes(data, 0, length);
        if (read != length) {
            throw new IllegalStateException("数据不足: 需要 " + length + " 实得 " + read);
        }
        return data;
    }

    @Override
    public long readLong() {
        long result = 0;
        for (int i = 0; i < 8; i++) {
            result = (result << 8) | (readByte() & 0xFFL);
        }
        return result;
    }

    @Override
    public int readInt() {
        return (readByte() << 24) | (readByte() << 16) | (readByte() << 8) | readByte();
    }

    @Override
    public int readableBytes() {
        return in == null ? 0 : in.available();
    }
}
