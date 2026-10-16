package com.opendreamcore.protocol;

import com.opendreamcore.protocol.message.CloudManifest;
import com.opendreamcore.visual.CacheNames;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 校验云缓存中的明文资源，并统一处理缓存路径定位。
 * 缓存文件仍按协议保存为密文；校验时解密到内存后比对清单中的明文大小和 SHA-256。
 */
public final class CloudCacheValidator {

    private CloudCacheValidator() {
    }

    /** 将协议中的相对资源路径定位到缓存目录，非法路径直接拒绝。 */
    public static Path resolve(Path cacheDir, byte[] sessionKey, String resourcePath) {
        if (cacheDir == null || !isSafeRelativePath(resourcePath)) {
            throw new IllegalArgumentException("非法资源路径: " + resourcePath);
        }
        Path dir = cacheDir.toAbsolutePath().normalize();
        Path resolved;
        if (sessionKey == null || sessionKey.length == 0) {
            resolved = dir.resolve(resourcePath).normalize();
        } else {
            String name = CacheNames.forPath(sessionKey, resourcePath);
            int slash = resourcePath.lastIndexOf('/');
            String dirPart = slash >= 0 ? resourcePath.substring(0, slash) : "";
            resolved = (dirPart.isEmpty() ? dir : dir.resolve(dirPart))
                    .resolve(name).normalize();
        }
        if (!resolved.startsWith(dir)) {
            throw new IllegalArgumentException("非法资源路径: " + resourcePath);
        }
        Path current = dir;
        if (Files.isSymbolicLink(current)) {
            throw new IllegalArgumentException("缓存目录不能是符号链接: " + dir);
        }
        for (Path part : dir.relativize(resolved)) {
            current = current.resolve(part);
            if (Files.isSymbolicLink(current)) {
                throw new IllegalArgumentException("缓存路径不能包含符号链接: " + resourcePath);
            }
        }
        return resolved;
    }

    /** 文件不存在、解密失败或明文元数据不符均返回 false。 */
    public static boolean matches(Path cacheFile, byte[] sessionKey, CloudManifest.Entry entry) {
        if (cacheFile == null || entry == null || entry.size() < 0
                || entry.size() > Protocol.MAX_PACKET_BYTES
                || entry.sha256() == null || !isSafeRelativePath(entry.path())) {
            return false;
        }
        try {
            if (!Files.isRegularFile(cacheFile)
                    || Files.size(cacheFile) > Protocol.MAX_PACKET_BYTES) {
                return false;
            }
            byte[] encrypted = Files.readAllBytes(cacheFile);
            byte[] plain = sessionKey == null || sessionKey.length == 0
                    ? encrypted : Crypto.decrypt(sessionKey, encrypted);
            if (plain.length != entry.size()) {
                return false;
            }
            return entry.sha256().equalsIgnoreCase(sha256(plain));
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /** 仅接受使用正斜杠分隔的非空相对路径，拒绝路径穿越和平台绝对路径语法。 */
    public static boolean isSafeRelativePath(String resourcePath) {
        if (resourcePath == null || resourcePath.isEmpty()
                || resourcePath.charAt(0) == '/' || resourcePath.charAt(0) == '\\'
                || resourcePath.indexOf('\\') >= 0 || resourcePath.indexOf(':') >= 0) {
            return false;
        }
        int start = 0;
        for (int i = 0; i <= resourcePath.length(); i++) {
            if (i < resourcePath.length()) {
                char c = resourcePath.charAt(i);
                if (c < 32 || "<>\"|?*".indexOf(c) >= 0) {
                    return false;
                }
                if (c != '/') {
                    continue;
                }
            }
            int length = i - start;
            if (length == 0 || resourcePath.charAt(i - 1) == '.'
                    || resourcePath.charAt(i - 1) == ' ') {
                return false;
            }
            start = i + 1;
        }
        return true;
    }

    private static String sha256(byte[] data) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
            char[] hex = new char[digest.length * 2];
            final char[] digits = "0123456789abcdef".toCharArray();
            for (int i = 0; i < digest.length; i++) {
                int value = digest[i] & 0xff;
                hex[i * 2] = digits[value >>> 4];
                hex[i * 2 + 1] = digits[value & 0x0f];
            }
            return new String(hex);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
