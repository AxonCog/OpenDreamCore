package com.opendreamcore.model.format.ysm;

import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/** 自造 YSGP 容器：v1/v2 全链加解密往返 + md5 校验 + zip 摊平。 */
class YsgpUnpackerTest {

    // ---- 样例构造工具（与容器布局同一套语义，反向加密） ----

    private static byte[] encrypt(byte[] key, byte[] iv, byte[] plain) throws Exception {
        Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"),
                new IvParameterSpec(iv), new SecureRandom());
        return c.doFinal(plain);
    }

    private static byte[] zlib(byte[] raw) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DeflaterOutputStream out = new DeflaterOutputStream(bos);
        out.write(raw);
        out.close();
        return bos.toByteArray();
    }

    private static byte[] zip(Map<String, byte[]> files) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ZipOutputStream zip = new ZipOutputStream(bos);
        for (Map.Entry<String, byte[]> e : files.entrySet()) {
            zip.putNextEntry(new ZipEntry(e.getKey()));
            zip.write(e.getValue());
            zip.closeEntry();
        }
        zip.close();
        return bos.toByteArray();
    }

    private static void putLen(java.io.ByteArrayOutputStream bos, byte[] b) throws IOException {
        bos.write(be(b.length));
        bos.write(b);
    }

    private static byte[] be(int v) {
        return new byte[]{(byte) (v >>> 24), (byte) (v >>> 16), (byte) (v >>> 8), (byte) v};
    }

    /** v1 容器：一条内嵌明文密钥条目，内容是 zip（内含 ysm.json）。 */
    private static byte[] v1Container(Map<String, byte[]> innerZip) throws Exception {
        byte[] key = "0123456789abcdef".getBytes(StandardCharsets.US_ASCII);
        byte[] iv = "fedcba9876543210".getBytes(StandardCharsets.US_ASCII);
        byte[] cipher = encrypt(key, iv, zlib(zip(innerZip)));

        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(be("model.zip".length()));
        body.write("model.zip".getBytes(StandardCharsets.UTF_8));
        body.write(be(cipher.length));   // 与解析器同序：size → key → iv → cipher
        body.write(key);
        body.write(iv);
        body.write(cipher);              // size 已单独给出，不再带长度前缀

        return container(1, body.toByteArray());
    }

    /** v2 容器：真密钥被包裹密钥加密，包裹密钥由文件 md5 种子派生。 */
    private static byte[] v2Container(Map<String, byte[]> innerZip) throws Exception {
        byte[] realKey = "AbCdEf0123456789".getBytes(StandardCharsets.US_ASCII);
        byte[] iv = "0f1e2d3c4b5a6978".getBytes(StandardCharsets.US_ASCII);
        byte[] cipher = encrypt(realKey, iv, zlib(zip(innerZip)));

        byte[] wrapKey = new byte[16];
        byte[] seed = MessageDigest.getInstance("MD5").digest(cipher);
        long s = 0;
        for (byte b : seed) {
            s = (s << 8) + (b & 0xFF);
        }
        new Random(s).nextBytes(wrapKey);
        byte[] cipherKey = encrypt(wrapKey, iv, realKey);

        ByteArrayOutputStream body = new ByteArrayOutputStream();
        byte[] b64 = java.util.Base64.getEncoder().encode("model.zip".getBytes(StandardCharsets.UTF_8));
        body.write(be(b64.length));
        body.write(b64);
        body.write(be(cipher.length));
        putLen(body, cipherKey);
        body.write(iv);
        body.write(cipher);   // size 已在前缀给出，cipher 不再带长度

        return container(2, body.toByteArray());
    }

    private static byte[] container(int version, byte[] payload) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[]{0x59, 0x53, 0x47, 0x50});   // YSGP
        out.write(be(version));
        out.write(MessageDigest.getInstance("MD5").digest(payload));
        out.write(payload);
        return out.toByteArray();
    }

    private static Map<String, byte[]> innerFiles() {
        return Map.of("mymodel/ysm.json", "{\"geometry\":{}}".getBytes(StandardCharsets.UTF_8));
    }

    // ---- 用例 ----

    @Test
    void v1解包到zip摊平() throws Exception {
        byte[] container = v1Container(innerFiles());
        assertEquals(1, YsgpUnpacker.version(container));
        Map<String, byte[]> files = YsgpUnpacker.unzip(YsgpUnpacker.unpack(container));
        assertEquals(1, files.size());
        assertTrue(files.containsKey("mymodel/ysm.json"));
        assertTrue(new String(files.get("mymodel/ysm.json"), StandardCharsets.UTF_8)
                .contains("geometry"));
    }

    @Test
    void v2解密链往返() throws Exception {
        byte[] container = v2Container(innerFiles());
        assertEquals(2, YsgpUnpacker.version(container));
        Map<String, byte[]> files = YsgpUnpacker.unzip(YsgpUnpacker.unpack(container));
        assertTrue(files.containsKey("mymodel/ysm.json"));
    }

    @Test
    void md5校验拦截篡改() throws Exception {
        byte[] container = v1Container(innerFiles());
        container[container.length - 1] ^= 0x55;
        assertThrows(IOException.class, () -> YsgpUnpacker.unpack(container));
    }

    @Test
    void v3识别但不支持() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 0x59, 0x53, 0x47, 0x50});
        out.write(new byte[20]);
        byte[] data = out.toByteArray();
        assertEquals(3, YsgpUnpacker.version(data));
        IOException e = assertThrows(IOException.class, () -> YsgpUnpacker.unpack(data));
        assertTrue(e.getMessage().contains("v3"));
    }

    @Test
    void 非容器拒绝() {
        assertEquals(-1, YsgpUnpacker.version("not a ysm".getBytes(StandardCharsets.UTF_8)));
    }
}
