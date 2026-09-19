package com.opendreamcore.client;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * 散装贴图加载器（远古版）：扫 resourcepacks/OpenDreamCore 全深度 png/jpg/gif，
 * 运行时注册进各版 TextureManager——完全绕开资源包系统，所以中文文件名
 * 天然支持（键=原始文件名，ResourceLocation 用净化名）。跟现代端
 * LooseResourceLoader 一个意思，Java8 语法；gif 走 ImageIO 首帧当静态图。
 *
 * 注册动作各版 TextureManager API 不一样，收敛进 TextureRegistrar SPI，
 * 四个远古 target 各实现一个。
 */
public final class LooseTextureLoader {

    /** 注册 SPI：target 实现，把一张 BufferedImage 注册成可渲染纹理。 */
    public interface TextureRegistrar {
        /** rel 是相对 resourcepacks/OpenDreamCore 的路径（含文件名）。返回可渲染的 RL 字符串；失败 null。 */
        String register(String rel, java.awt.image.BufferedImage img);
    }

    private static volatile TextureRegistrar registrar;
    private static final Map<String, String> RL_BY_FILE = new ConcurrentHashMap<String, String>();
    private static volatile boolean scanned;

    private LooseTextureLoader() {
    }

    /** target 初始化时注册一次。 */
    public static void setRegistrar(TextureRegistrar r) {
        registrar = r;
    }

    /** 扫描注册：全深度递归，改完 Ctrl+R / 重进会重扫（scanned 标记在 reload 时清）。 */
    public static void scan(Path gameDir) {
        RL_BY_FILE.clear();
        if (gameDir == null) {
            scanned = true;
            return;
        }
        Path root = gameDir.resolve("resourcepacks").resolve("OpenDreamCore");
        if (!Files.isDirectory(root)) {
            scanned = true;
            return;
        }
        TextureRegistrar reg = registrar;
        if (reg == null) {
            scanned = true;
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path p : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                // 不按扩展名过滤：是不是图看解码结果，非图静默跳过
                String rel = root.relativize(p).toString().replace('\\', '/');
                try {
                    // gif 走 ImageIO 抽首帧，注册进各版纹理系统照样认
                    java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(p.toFile());
                    if (img == null) {
                        continue;
                    }
                    String rl = reg.register(rel, img);
                    if (rl != null) {
                        RL_BY_FILE.put(rel, rl);
                        // 顺带存尾名，方便只写文件名不写目录的配置
                        String tail = rel.substring(rel.lastIndexOf('/') + 1);
                        if (!RL_BY_FILE.containsKey(tail)) {
                            RL_BY_FILE.put(tail, rl);
                        }
                    }
                } catch (Throwable ignored) {
                    // 单张坏了跳过，别让整批加载崩
                }
            }
        } catch (Throwable ignored) {
        }
        scanned = true;
    }

    /** 按配置里写的路径（可中文、可带目录）查可渲染 RL；未命中 null。 */
    public static String lookup(String file) {
        if (file == null) {
            return null;
        }
        String want = file.replace('\\', '/');
        String hit = RL_BY_FILE.get(want);
        if (hit != null) {
            return hit;
        }
        // 尾名匹配兜底：配置写 肝.png，注册键可能是 testfonts/肝.png
        String tail = want.substring(want.lastIndexOf('/') + 1);
        for (Map.Entry<String, String> e : RL_BY_FILE.entrySet()) {
            String k = e.getKey();
            String kTail = k.substring(k.lastIndexOf('/') + 1);
            if (kTail.equalsIgnoreCase(tail)) {
                return e.getValue();
            }
        }
        return null;
    }

    /** 强制重扫（Ctrl+R / 视觉规则重载时清标记）。 */
    public static void invalidate() {
        scanned = false;
    }

    /** RL 净化：非 [a-z0-9_.-/] 替换成 _+短哈希（跟现代端一套，保证合法且不撞名）。 */
    public static String sanitize(String rel) {
        StringBuilder sb = new StringBuilder();
        for (char c : rel.toLowerCase().toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '.' || c == '/' || c == '-') {
                sb.append(c);
            } else {
                sb.append('_').append(Integer.toHexString(c));
            }
        }
        return sb.toString();
    }
}