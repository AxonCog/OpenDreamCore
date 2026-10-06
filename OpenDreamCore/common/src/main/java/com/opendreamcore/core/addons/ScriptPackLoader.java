package com.opendreamcore.core.addons;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 脚本包通道：addons 目录内 *.scriptpack（zip：manifest.yml + scripts/*.js）。
 *
 * <p>脚本引擎可用（javax.script 有 js 引擎）→ 装载为 type="script" 的句柄；
 * 引擎缺席（现代 JDK 自带 Nashorn 已移除）→ 整包跳过 + 日志提示（缺席即安全，不阻断 jar 通道）。
 */
public final class ScriptPackLoader {

    private final Path addonsDir;
    private final AddonRegistry registry;

    public ScriptPackLoader(Path addonsDir, AddonRegistry registry) {
        this.addonsDir = addonsDir;
        this.registry = registry;
    }

    /** 脚本引擎是否可用（引擎缺席 = 通道整体跳过）。 */
    public static boolean engineAvailable() {
        try {
            Class.forName("javax.script.ScriptEngineManager");
            return new javax.script.ScriptEngineManager().getEngineByName("js") != null
                    || new javax.script.ScriptEngineManager().getEngineByName("javascript") != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 扫描并装载脚本包；返回装载条数（引擎缺席返回 0 并记日志）。 */
    public synchronized int load() {
        if (!engineAvailable()) {
            AddonLog.info("脚本引擎缺席（javax.script 无 js 引擎），脚本包通道跳过");
            return 0;
        }
        if (addonsDir == null || !Files.isDirectory(addonsDir)) {
            return 0;
        }
        int count = 0;
        List<Path> packs = new ArrayList<Path>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(addonsDir, "*.scriptpack")) {
            for (Path p : ds) {
                packs.add(p);
            }
        } catch (Exception e) {
            AddonLog.warn("scriptpack 目录扫描失败: " + e);
            return count;
        }
        for (Path p : packs) {
            String id = p.getFileName().toString().replaceFirst("\\.scriptpack$", "");
            try {
                String manifest = readEntry(p.toFile(), "manifest.yml");
                if (manifest == null) {
                    AddonLog.warn("scriptpack 缺 manifest.yml，跳过: " + id);
                    continue;
                }
                String version = manifestValue(manifest, "version");
                // 脚本包本体注册为句柄；执行语义由 p6 脚本联动实现（此处只负责装载/卸载）
                if (registry.register(id, "script", version, p.toFile().getAbsolutePath(),
                        ScriptPackLoader.class.getClassLoader())) {
                    count++;
                    AddonLog.info("scriptpack 装载: " + id + " (" + version + ")");
                } else {
                    AddonLog.warn("scriptpack id 冲突，拒绝: " + id);
                }
            } catch (Throwable t) {
                AddonLog.warn("scriptpack 装载失败（跳过）: " + id + " -> " + t);
            }
        }
        return count;
    }

    /** 读 zip 内单文件文本（找不到返回 null）。 */
    static String readEntry(File file, String entryName) throws Exception {
        InputStream in = Files.newInputStream(file.toPath());
        try {
            ZipInputStream zip = new ZipInputStream(in, StandardCharsets.UTF_8);
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                if (entryName.equals(e.getName())) {
                    java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = zip.read(buf)) > 0) {
                        bos.write(buf, 0, n);
                    }
                    return new String(bos.toByteArray(), StandardCharsets.UTF_8);
                }
            }
            return null;
        } finally {
            in.close();
        }
    }

    /** manifest 极简键值（key: value 首个冒号切分）。 */
    static String manifestValue(String manifest, String key) {
        for (String line : manifest.split("\r?\n")) {
            String t = line.trim();
            int c = t.indexOf(':');
            if (c > 0 && key.equals(t.substring(0, c).trim())) {
                return t.substring(c + 1).trim();
            }
        }
        return "?";
    }
}
