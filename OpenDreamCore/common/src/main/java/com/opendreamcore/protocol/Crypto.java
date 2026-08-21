package com.opendreamcore.protocol;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;

/**
 * 云资源传输加密：AES-128-GCM。
 * key 由服务端在 ready_ack 里下发（每个玩家独立），包体内容加密防窃听。
 * 布局：iv(12) + ciphertext（GCM 自带 tag）。
 */
public final class Crypto {

    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private Crypto() {
    }

    public static byte[] randomKey() {
        byte[] key = new byte[16];
        RANDOM.nextBytes(key);
        return key;
    }

    public static byte[] encrypt(byte[] key, byte[] data) {
        try {
            byte[] iv = new byte[IV_LEN];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
            byte[] out = cipher.doFinal(data);
            byte[] result = new byte[IV_LEN + out.length];
            System.arraycopy(iv, 0, result, 0, IV_LEN);
            System.arraycopy(out, 0, result, IV_LEN, out.length);
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("加密失败", e);
        }
    }

    public static byte[] decrypt(byte[] key, byte[] data) {
        try {
            if (data.length < IV_LEN) {
                throw new IllegalStateException("密文太短");
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_BITS, data, 0, IV_LEN));
            return cipher.doFinal(data, IV_LEN, data.length - IV_LEN);
        } catch (Exception e) {
            throw new IllegalStateException("解密失败", e);
        }
    }
}
