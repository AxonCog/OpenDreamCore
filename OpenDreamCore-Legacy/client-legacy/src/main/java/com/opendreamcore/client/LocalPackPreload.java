package com.opendreamcore.client;

import com.opendreamcore.client.spi.FolderPackInjector;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 本地材质包区：gameDir/resourcepacks/OpenDreamCore。
 *
 * 首启建骨架（README + 示例包），玩家把文件夹包或 zip 丢进来就生效，
 * 不用进原版资源包菜单启用。文件指纹变了自动重注入——改完贴图等两秒
 * 就能看到，这就是『即换即生效』。
 *
 * 目录语义和高版本同一套：下划线开头不参与加载，README 不当包。
 */
public final class LocalPackPreload {

    private static final Logger LOGGER = org.apache.logging.log4j.LogManager.getLogger("OpenDreamCore");

    /** 指纹检查节流：每 40 tick（两秒）看一眼目录有没有变。 */
    private static final int CHECK_EVERY = 40;

    private static volatile Supplier<Path> gameDirSupplier;
    private static boolean skeletonReady;
    private static boolean injected;
    private static int tickCounter;
    /** 上次注入时的目录指纹（文件名+大小+修改时间拼串），变了才重注入。 */
    private static String lastFingerprint = "";

    private LocalPackPreload() { }

    /** 开局调一次：记住 gameDir，首 tick 建骨架。 */
    public static void init(Supplier<Path> gameDir) {
        gameDirSupplier = gameDir;
    }

    private static Path root() {
        Supplier<Path> s = gameDirSupplier;
        if (s == null) {
            return null;
        }
        try {
            Path dir = s.get();
            return dir == null ? null : dir.resolve("resourcepacks").resolve("OpenDreamCore");
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 骨架：README + 示例包。已存在就不动，玩家改过的东西不能覆盖。 */
    private static void ensureSkeleton(Path root) {
        try {
            Files.createDirectories(root);
            Path readme = root.resolve("README.txt");
            if (!Files.exists(readme)) {
                Files.write(readme, README_TEXT.getBytes(StandardCharsets.UTF_8));
            }
            Path sample = root.resolve("_示例包");
            Path meta = sample.resolve("pack.mcmeta");
            if (!Files.exists(meta)) {
                Files.createDirectories(sample);
                Files.write(meta, SAMPLE_MCMETA.getBytes(StandardCharsets.UTF_8));
            }
            skeletonReady = true;
        } catch (Throwable t) {
            LOGGER.warn("[ODC] 材质包区初始化失败: {}", t.toString());
        }
    }

    /** 扫包：文件夹包 + zip 包，下划线开头跳过。 */
    private static List<File> scan(Path root) {
        List<File> out = new ArrayList<>();
        File[] children = root.toFile().listFiles();
        if (children == null) {
            return out;
        }
        for (File f : children) {
            if (f.getName().startsWith("_") || f.getName().equalsIgnoreCase("README.txt")) {
                continue;
            }
            if (f.isDirectory() || (f.isFile() && f.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".zip"))) {
                out.add(f);
            }
        }
        return out;
    }

    /** 指纹：同目录同内容就不折腾资源管理器。 */
    private static String fingerprint(List<File> packs) {
        StringBuilder sb = new StringBuilder();
        for (File f : packs) {
            sb.append(f.getName()).append(':').append(f.length()).append(':').append(f.lastModified()).append(';');
        }
        return sb.toString();
    }

    /** 每 tick 调：首帧建骨架+注入，之后每两秒查一次指纹，变了就重注入。 */
    public static void tick() {
        Path root = root();
        if (root == null) {
            return;
        }
        if (!skeletonReady) {
            ensureSkeleton(root);
        }
        // 托管根目录本身也挂成常驻包：贴图直接丢根下就生效（DreamEngine 同款）
        FolderPackInjector.Host.current().injectRoot(root);
        if (++tickCounter % (injected ? CHECK_EVERY : 1) != 0) {
            return;
        }
        List<File> packs = scan(root);
        String fp = fingerprint(packs);
        if (injected && fp.equals(lastFingerprint)) {
            return;
        }
        lastFingerprint = fp;
        int ok = FolderPackInjector.Host.current().injectAll(packs);
        if (!injected) {
            injected = true;
            LOGGER.info("[ODC] 材质包区就绪: {}（本次注入 {} 个，目录: {}）",
                    packs.size(), ok, root);
        } else {
            LOGGER.info("[ODC] 材质包区变化，重注入 {} 个", ok);
        }
    }

    private static final String README_TEXT =
            "OpenDreamCore 本地材质包区\r\n"
            + "=========================\r\n"
            + "\r\n"
            + "把整包文件夹或 zip 丢进本目录，游戏里自动加载，不用进原版菜单启用。\r\n"
            + "改了贴图等两秒自动生效，不用重启游戏。\r\n"
            + "\r\n"
            + "文件夹包结构（照 _示例包 里的 pack.mcmeta 抄一份）：\r\n"
            + "  我的材质/\r\n"
            + "    pack.mcmeta                        必须有，格式见示例\r\n"
            + "    assets/opendreamcore/textures/...  贴图按这个路径放\r\n"
            + "\r\n"
            + "下划线开头的文件夹不会被加载，放文档放草稿都行。\r\n"
            + "删掉的包要等下次开游戏才真正卸载（现场热换只管加和改）。\r\n";

    private static final String SAMPLE_MCMETA =
            "{\r\n"
            + "  \"pack\": {\r\n"
            + "    \"pack_format\": 3,\r\n"
            + "    \"description\": \"示例包：照这个结构做自己的包（1.12.2 用 3，1.7.10 用 1，1.16.5 用 6）\"\r\n"
            + "  }\r\n"
            + "}\r\n";
}
