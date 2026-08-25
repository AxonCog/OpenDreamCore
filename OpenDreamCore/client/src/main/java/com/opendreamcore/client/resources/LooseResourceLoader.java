package com.opendreamcore.client.resources;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * 本地散装资源加载器。
 *
 * <p>扫描 gameDir/resourcepacks/OpenDreamCore/ 下的图片文件，经 {@link DynamicTexture}
 * 运行时注册进 TextureManager——完全绕开资源包系统，因此：
 * 一、无需注入器/mixin/重载；二、中文文件名天然支持（注册表键=原始文件名，
 * 仅合成 ResourceLocation 时做净化）；三、全部 target 由本类统一对齐，零平台差异。</p>
 *
 * <p>YAML 引用方式：texture 路径写文件名（含中文亦可），绘制层先查本注册表。</p>
 */
public final class LooseResourceLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(LooseResourceLoader.class);

    /** 原始文件名（含中文）→ 已注册纹理 RL。 */
    private static final Map<String, ResourceLocation> REGISTRY = new ConcurrentHashMap<>();

    /** 扫描根：gameDir/resourcepacks/OpenDreamCore（与 DreamCore 形态一致）。 */
    public static Path scanRoot(Path gameDir) {
        return gameDir.resolve("resourcepacks").resolve("OpenDreamCore");
    }

    /** 注册表查询：命中返回 RL，未命中 null。 */
    public static ResourceLocation lookup(String fileName) {
        return fileName == null ? null : REGISTRY.get(fileName);
    }

    /** 扫描并注册根目录及一层子目录下的全部图片。返回成功注册数。 */
    public static int loadAll(Path gameDir) {
        Path root = scanRoot(gameDir);
        if (!Files.isDirectory(root)) {
            return 0;
        }
        int n = 0;
        try (Stream<Path> walk = Files.walk(root, 2)) {
            for (Path p : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                String fn = String.valueOf(p.getFileName());
                if (!isImage(fn)) {
                    continue;
                }
                String key = root.relativize(p).toString().replace('\\', '/');
                if (register(key, p)) {
                    n++;
                }
            }
        } catch (Exception e) {
            LOGGER.warn("散装资源扫描失败: {}", e.toString());
        }
        LOGGER.info("本地散装资源已注册 {} 个", n);
        return n;
    }

    /** 单文件注册（重名覆盖）。 */
    public static boolean register(String key, Path imageFile) {
        if (!isImage(key)) {
            return false;
        }
        try (InputStream in = Files.newInputStream(imageFile)) {
            NativeImage img = NativeImage.read(in);
            String rlPath = sanitize(key);
            ResourceLocation rl = com.opendreamcore.client.CompatRender.rl("opendreamcore", "loose/" + rlPath);
            Minecraft.getInstance().getTextureManager().register(rl,
                    new DynamicTexture(img));
            REGISTRY.put(key, rl);
            return true;
        } catch (IOException e) {
            LOGGER.warn("散装资源读取失败 {}: {}", key, e.toString());
            return false;
        }
    }

    private static boolean isImage(String name) {
        String n = name.toLowerCase();
        return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg");
    }

    /** RL 路径净化：非 [a-z0-9_.-/] 字符替换为下划线+短哈希，保证唯一且合法。 */
    private static String sanitize(String rel) {
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

    private LooseResourceLoader() {
    }
}
