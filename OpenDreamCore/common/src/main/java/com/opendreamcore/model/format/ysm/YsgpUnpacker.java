package com.opendreamcore.model.format.ysm;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.zip.InflaterInputStream;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * YSGP 容器（.ysm）解包：魔数 + 版本 + 全载荷 md5 + 逐条目加密链。
 *
 * <p>容器布局（大端）：4 字节魔数 "YSGP"；4 字节容器版本（1 或 2）；
 * 16 字节 = 后续全部载荷的 md5；其余为条目流。条目流循环到耗尽：
 * <ul>
 *   <li>v1：int 长度 + UTF-8 名（非 base64）；16 字节条目密钥（明文内嵌）+
 *       16 字节 IV；int 密文长度 + 密文。AES/CBC/PKCS5Padding 解密后 zlib 解压。</li>
 *   <li>v2：int 长度 + base64 名；int 密文长度；int 包裹密钥长度 + 包裹密钥
 *       （它本身是被加密的真密钥）+ 16 字节 IV + 密文。包裹密钥由
 *       「文件 md5 种子化的 Random 取 16 字节」派生；解出真密钥后再解正文。</li>
 * </ul>
 *
 * <p>v3（BOM+"YSGP" 开头）：容器可识别，正文方案未公开且手上无可靠依据，
 * 明确不支持（抛带版本说明的异常），见 docs/Model-格式.md 的 v3 节。
 *
 * <p>解包产物通常是 zip（根部 ysm.json / main.json 等），用 {@link #unzip}
 * 摊平成文件表；直接是单文件内容的情形也兼容。
 */
public final class YsgpUnpacker {

    /** 一条解包产物：模型包内文件名 → 内容。 */
    public static final class Entry {
        public final String name;
        public final byte[] data;

        Entry(String name, byte[] data) {
            this.name = name;
            this.data = data;
        }
    }

    private YsgpUnpacker() {
    }

    /** 容器版本嗅探：1/2 = 可解包；3 = 识别到但明确不支持；-1 = 非本格式。 */
    public static int version(byte[] data) {
        if (data == null || data.length < 8) {
            return -1;
        }
        // BOM + YSGP = v3 容器（正文方案未公开，不支持）
        if ((data[0] & 0xFF) == 0xEF && (data[1] & 0xFF) == 0xBB
                && (data[2] & 0xFF) == 0xBF && matchMagic(data, 3)) {
            return 3;
        }
        if (!matchMagic(data, 0)) {
            return -1;
        }
        int v = beInt(data, 4);
        return (v == 1 || v == 2) ? v : -1;
    }

    public static List<Entry> unpack(byte[] data) throws IOException {
        int v = version(data);
        if (v == -1) {
            throw new IOException("不是 YSGP 容器");
        }
        if (v == 3) {
            throw new IOException("YSGP v3 容器暂不支持（正文加密方案未公开，见文档 v3 节）");
        }
        byte[] payload = new byte[data.length - 24];
        System.arraycopy(data, 24, payload, 0, payload.length);
        byte[] expect = new byte[16];
        System.arraycopy(data, 8, expect, 0, 16);
        byte[] actual = md5(payload);
        for (int i = 0; i < 16; i++) {
            if (expect[i] != actual[i]) {
                throw new IOException("载荷 md5 不匹配（文件损坏或被改动）");
            }
        }
        ByteArrayInputStream in = new ByteArrayInputStream(payload);
        List<Entry> out = new ArrayList<Entry>();
        while (in.available() > 0) {
            out.add(v == 1 ? entryV1(in) : entryV2(in));
        }
        return out;
    }

    /** 解包结果若是 zip，摊平成 文件名→内容（去重保留首个）。 */
    public static Map<String, byte[]> unzip(List<Entry> entries) throws IOException {
        Map<String, byte[]> files = new LinkedHashMap<String, byte[]>();
        for (Entry e : entries) {
            if (e.data.length > 4 && e.data[0] == 'P' && e.data[1] == 'K') {
                java.util.zip.ZipInputStream zip = new java.util.zip.ZipInputStream(
                        new ByteArrayInputStream(e.data));
                java.util.zip.ZipEntry ze;
                while ((ze = zip.getNextEntry()) != null) {
                    if (ze.isDirectory()) {
                        continue;
                    }
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = zip.read(buf)) > 0) {
                        bos.write(buf, 0, n);
                    }
                    String nm = ze.getName();
                    if (!files.containsKey(nm)) {
                        files.put(nm, bos.toByteArray());
                    }
                }
                zip.close();
            } else {
                files.putIfAbsent(e.name, e.data);
            }
        }
        return files;
    }

    // ---- v1：内嵌明文密钥 ----

    private static Entry entryV1(ByteArrayInputStream in) throws IOException {
        String name = readString(in);
        int size = readInt(in);
        byte[] key = new byte[16];
        byte[] iv = new byte[16];
        fill(in, key);
        fill(in, iv);
        byte[] cipher = new byte[size];
        fill(in, cipher);
        try {
            return new Entry(name, inflate(aes(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(key, "AES"), new IvParameterSpec(iv), cipher)));
        } catch (GeneralSecurityException e) {
            throw new IOException("条目解密失败: " + name, e);
        }
    }

    // ---- v2：md5 派生包裹密钥包真密钥 ----

    private static Entry entryV2(ByteArrayInputStream in) throws IOException {
        String name = readString(in);
        int size = readInt(in);
        int keySize = readInt(in);
        byte[] cipherKey = new byte[keySize];
        fill(in, cipherKey);
        byte[] iv = new byte[16];
        fill(in, iv);
        byte[] cipher = new byte[size];
        fill(in, cipher);

        // 包裹密钥：文件 md5 → 64 位种子 → Random 取 16 字节
        byte[] seed = md5(cipher);
        long s = 0;
        for (byte b : seed) {
            s = (s << 8) + (b & 0xFF);
        }
        Random rng = new Random(s);
        byte[] wrap = new byte[16];
        rng.nextBytes(wrap);
        try {
            byte[] realKey = aes(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(wrap, "AES"), new IvParameterSpec(iv), cipherKey);
            return new Entry(name, inflate(aes(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(realKey, "AES"), new IvParameterSpec(iv), cipher)));
        } catch (GeneralSecurityException e) {
            throw new IOException("条目解密失败: " + name, e);
        }
    }

    // ---- 小工具 ----

    private static byte[] aes(int mode, SecretKeySpec key, IvParameterSpec iv,
                              byte[] data) throws IOException, GeneralSecurityException {
        Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
        c.init(mode, key, iv, new SecureRandom());
        return c.doFinal(data);
    }

    /** zlib 解压（条目正文是 Deflate 流）。 */
    private static byte[] inflate(byte[] data) throws IOException {
        InflaterInputStream in = new InflaterInputStream(new ByteArrayInputStream(data));
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length);
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        in.close();
        return out.toByteArray();
    }

    private static byte[] md5(byte[] data) throws IOException {
        try {
            return MessageDigest.getInstance("MD5").digest(data);
        } catch (GeneralSecurityException e) {
            throw new IOException("MD5 不可用", e);
        }
    }

    private static String readString(ByteArrayInputStream in) throws IOException {
        int len = readInt(in);
        byte[] b = new byte[len];
        fill(in, b);
        return new String(b, StandardCharsets.UTF_8).trim();
    }

    private static int readInt(ByteArrayInputStream in) throws IOException {
        byte[] b = new byte[4];
        fill(in, b);
        return beInt(b, 0);
    }

    private static void fill(ByteArrayInputStream in, byte[] b) throws IOException {
        int got = in.read(b, 0, b.length);
        if (got != b.length) {
            throw new IOException("条目流提前耗尽");
        }
    }

    private static boolean matchMagic(byte[] d, int off) {
        return d.length >= off + 4 && d[off] == 0x59 && d[off + 1] == 0x53
                && d[off + 2] == 0x47 && d[off + 3] == 0x50;
    }

    private static int beInt(byte[] b, int off) {
        return ((b[off] & 0xFF) << 24) | ((b[off + 1] & 0xFF) << 16)
                | ((b[off + 2] & 0xFF) << 8) | (b[off + 3] & 0xFF);
    }
}
