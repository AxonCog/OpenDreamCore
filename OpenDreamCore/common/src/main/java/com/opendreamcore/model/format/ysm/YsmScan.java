package com.opendreamcore.model.format.ysm;

/** 字节扫描小工具：魔数查找/十六进制串/可读性/回看 varint。 */
final class YsmScan {

    private YsmScan() {
    }

static int indexOf(byte[] data, byte[] pat, int from) {
        outer:
        for (int i = Math.max(0, from); i <= data.length - pat.length; i++) {
            for (int j = 0; j < pat.length; j++) {
                if (data[i + j] != pat[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    static boolean isHex(byte[] data, int off, int len) {
        if (off < 0 || off + len > data.length) {
            return false;
        }
        for (int i = 0; i < len; i++) {
            int c = data[off + i] & 0xff;
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
                return false;
            }
        }
        return true;
    }

    static boolean isPrintable(byte[] data, int off, int len) {
        if (off < 0 || off + len > data.length) {
            return false;
        }
        for (int i = 0; i < len; i++) {
            int c = data[off + i] & 0xff;
            if (c < 32 && c != 9) {
                return false;
            }
        }
        return true;
    }

    static boolean isPrintable(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 32 && c != 9) {
                return false;
            }
        }
        return true;
    }

    static long peekVarint(byte[] data, int off) {
        try {
            YsmCodec.Bin bin = new YsmCodec.Bin(data);
            bin.p = off;
            return bin.varint();
        } catch (RuntimeException e) {
            return -1;
        }
    }

    static int varintSize(long v) {
        int n = 1;
        while ((v & ~0x7fL) != 0) {
            v >>>= 7;
            n++;
        }
        return n;
    }


    /** 顺手的小端读入器，越界即抛。 */}
