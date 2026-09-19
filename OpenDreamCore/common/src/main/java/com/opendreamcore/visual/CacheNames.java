package com.opendreamcore.visual;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 云缓存文件命名。
 *
 * 客户端云缓存目录（OpenDreamCore/cache/）里的文件一律以
 * SHA-256(密文) 命名——磁盘上只有无意义哈希文件，不暴露原始目录结构。
 * 原始路径与哈希的对应关系由加密索引表维护，渲染解析时先查索引再读内容。
 */
public final class CacheNames {

    private CacheNames() {
    }

    /** 密文 → 缓存文件名：SHA-256 十六进制小写。 */
    public static String forCiphertext(byte[] ciphertext) {
        try {
            var d = MessageDigest.getInstance("SHA-256");
            byte[] hash = d.digest(ciphertext);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    /**
     * 会话键化的路径定位名：
     * SHA-256(会话key ∥ 相对路径) 十六进制，保留原扩展名后缀——
     * 没有 key 算不出名字，磁盘上既看不到原始路径也无法枚举内容。
     */
    public static String forPath(byte[] key, String relPath) {
        try {
            var d = MessageDigest.getInstance("SHA-256");
            d.update(key == null ? new byte[0] : key);
            d.update(relPath.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d.digest()) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            int dot = relPath.lastIndexOf('.');
            return dot >= 0 ? sb + relPath.substring(dot) : sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    /** 纯文本快速哈希（索引用；非安全场景）。 */
    public static String textHash(String text) {
        return forCiphertext(text.getBytes(StandardCharsets.UTF_8));
    }
}
