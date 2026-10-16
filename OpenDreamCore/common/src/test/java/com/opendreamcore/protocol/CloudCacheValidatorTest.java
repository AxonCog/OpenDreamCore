package com.opendreamcore.protocol;

import com.opendreamcore.protocol.message.CloudManifest;
import org.junit.jupiter.api.Test;

import java.security.MessageDigest;
import java.nio.file.Path;
import java.nio.file.Files;
import static org.junit.jupiter.api.Assertions.*;

class CloudCacheValidatorTest {
    @Test
    void matchesPlainAndEncryptedCache() throws Exception {
        Path dir = Files.createTempDirectory("odc-cache-");
        byte[] key = Crypto.randomKey();
        byte[] plain = "updated cloud resource".getBytes("UTF-8");
        CloudManifest.Entry entry = new CloudManifest.Entry("textures/icon.png", plain.length, hex(plain));
        Path file = CloudCacheValidator.resolve(dir, key, entry.path());
        Files.createDirectories(file.getParent());
        Files.write(file, Crypto.encrypt(key, plain));
        assertTrue(CloudCacheValidator.matches(file, key, entry));
        Files.write(file, Crypto.encrypt(key, "corrupt".getBytes("UTF-8")));
        assertFalse(CloudCacheValidator.matches(file, key, entry));
    }

    @Test
    void rejectsUnsafePathsWithoutLeavingCache() throws Exception {
        Path dir = Files.createTempDirectory("odc-cache-");
        assertFalse(CloudCacheValidator.isSafeRelativePath("../outside.txt"));
        assertFalse(CloudCacheValidator.isSafeRelativePath("/absolute.txt"));
        assertFalse(CloudCacheValidator.isSafeRelativePath("dir\\file.txt"));
        assertThrows(IllegalArgumentException.class,
                () -> CloudCacheValidator.resolve(dir, new byte[0], "../outside.txt"));
    }

    private static String hex(byte[] data) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder out = new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            out.append(String.format("%02x", value & 0xff));
        }
        return out.toString();
    }
}
