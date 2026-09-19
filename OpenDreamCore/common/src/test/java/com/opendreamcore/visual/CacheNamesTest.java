package com.opendreamcore.visual;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 云缓存哈希命名：落盘文件名 = SHA-256 十六进制。
 */
class CacheNamesTest {

    @Test
    void ciphertextHashesToKnownSha256Hex() {
        // SHA-256("abc") 的公认向量，验证哈希实现正确性
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                CacheNames.forCiphertext("abc".getBytes()));
    }

    @Test
    void textHashIsDeterministicAnd64Hex() {
        String h1 = CacheNames.textHash("odc/icons/gold.png");
        String h2 = CacheNames.textHash("odc/icons/gold.png");
        assertEquals(h1, h2);
        assertEquals(64, h1.length());
        assertTrue(h1.chars().allMatch(c -> Character.isDigit(c)
                || (c >= 'a' && c <= 'f')));
    }

    @Test
    void differentInputsProduceDifferentNames() {
        assertNotEquals(CacheNames.textHash("a.png"), CacheNames.textHash("b.png"));
    }

    @Test
    void pathNameIsKeyedAndKeepsExtension() {
        byte[] key = "session-key".getBytes();
        String a = CacheNames.forPath(key, "textures/lz.png");
        assertTrue(a.endsWith(".png"), "扩展名后缀保留，扫描型 reader 可按类型过滤");
        assertEquals(64 + 4, a.length());                    // 64 位哈希 + ".png"
        // 同 key 同路径 → 同名（幂等定位）；换 key 或换路径 → 不同名
        assertEquals(a, CacheNames.forPath(key, "textures/lz.png"));
        assertNotEquals(a, CacheNames.forPath("别的key".getBytes(), "textures/lz.png"));
        assertNotEquals(a, CacheNames.forPath(key, "textures/other.png"));
    }
}
