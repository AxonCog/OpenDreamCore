package com.opendreamcore.client.packs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.opendreamcore.packs.PackInstaller;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * 本地材质包预置加载（无服务器也能生效）：
 * 启动时扫描 gameDir/OpenDreamCore/resourcepacks/ 下的 zip / 加密 zip / 文件夹包 / 散图，
 * 经统一 PackInstaller 管线安装（解压到托管目录 + 平台注入器置顶注入）。
 * 散图（1.png、gui/标题.png）会被自动铺成迷你包，丢文件就生效。
 *
 * 本能力属 common 共享管线（targets 只放平台胶水），全部 target 自动对齐：
 * 平台差异只允许出现在 ResourcePackInjector SPI 实现内。
 */
public final class LocalPackPreload {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalPackPreload.class);

    private LocalPackPreload() {
    }

    /** 扫描根：gameDir/resourcepacks/OpenDreamCore——版本隔离下即版本目录内的原版资源包区。 */
    public static Path scanRoot(Path gameDir) {
        return gameDir.resolve("resourcepacks").resolve("OpenDreamCore");
    }

    /** 骨架文件名（首启创建）。 */
    private static final String README_NAME = "README.txt";

    /**
     * 预置扫描安装。逐个包独立容错：单包失败不影响其余。
     *
     * 返回：成功安装数
     */
    public static int preload(Path gameDir) {
        Path root = scanRoot(gameDir);
        try {
            Files.createDirectories(root); // 目录常驻，玩家可直接丢包
            ensureSkeleton(root);
        } catch (Exception e) {
            LOGGER.warn("托管目录创建失败: {}", e.toString());
            return 0;
        }
        if (!Files.isDirectory(root)) {
            return 0;
        }
        int ok = 0;
        // 安装单元收集：zip/文件夹包整包装；散图目录（不带 pack.mcmeta 的）整棵递归，
        // 见一张装一张——玩家只管往文件夹里丢文件，不用自己套包壳
        java.util.List<Path> units = new java.util.ArrayList<>();
        try {
            collectUnits(root, units, 0);
        } catch (Exception e) {
            LOGGER.warn("本地材质包扫描失败: {}", e.toString());
        }
        for (Path p : units) {
            String name = String.valueOf(p.getFileName());
            try {
                var r = PackInstaller.install(p.toString(), null, true);
                if (r.ok()) {
                    ok++;
                    com.opendreamcore.client.resource.PackRegistry.markInstalled(
                            name, p.toString(), true);
                    LOGGER.info("本地材质包预置成功: {}", name);
                } else {
                    LOGGER.warn("本地材质包预置失败 {}: {}", name, r.message());
                }
            } catch (Throwable t) {
                LOGGER.warn("本地材质包预置异常 {}: {}", name, t.toString());
            }
        }
        return ok;
    }

    /** 扫描深度上限：散图目录最多往下钻三层，防手滑把整盘收进来。 */
    private static final int MAX_SCAN_DEPTH = 3;

    /**
     * 收集安装单元。规则：
     * _ 开头、README.txt、pack.mcmeta 这些骨架/排除项跳过；
     * zip 和带 pack.mcmeta 的目录整包算一个单元（不再往里钻）；
     * 普通目录递归收 png（子目录名就是贴图路径语义，见 PackInstaller.singleImagePack）。
     */
    private static void collectUnits(Path dir, java.util.List<Path> out, int depth) throws java.io.IOException {
        try (Stream<Path> list = Files.list(dir)) {
            for (Path p : list.sorted().toList()) {
                String name = String.valueOf(p.getFileName());
                if (name.startsWith("_") || name.equals("OpenDreamCore")
                        || name.equals("README.txt") || name.equals("pack.mcmeta")) {
                    continue; // 下划线开头 = 不参与预置；骨架文件自身跳过
                }
                if (Files.isDirectory(p)) {
                    if (Files.isRegularFile(p.resolve("pack.mcmeta"))) {
                        out.add(p); // 像个正经文件夹包，整包装，不往里钻
                    } else if (depth < MAX_SCAN_DEPTH) {
                        collectUnits(p, out, depth + 1); // 散图容器：接着找图
                    }
                } else if (Files.isRegularFile(p)) {
                    String lower = name.toLowerCase(java.util.Locale.ROOT);
                    if (lower.endsWith(".zip") || lower.endsWith(".png")) {
                        out.add(p); // zip 整包；png 交给 PackInstaller 铺迷你包
                    }
                }
            }
        }
    }

    /**
     * 首启骨架：说明文件 + 原版扫描器能认的 pack.mcmeta。
     * 有了 mcmeta，原版资源包界面也能直接看到并手动启用这个目录（散文件布局也认）。
     * supported_formats 超集写法，全版本解析器都认。
     */
    private static void ensureSkeleton(Path root) {
        Path readme = root.resolve(README_NAME);
        if (!Files.exists(readme)) {
            try {
                Files.writeString(readme, String.join("\n",
                        "OpenDreamCore 托管材质包目录",
                        "",
                        "把贴图直接丢进来就生效（改完自动热载，不用重启）：",
                        "  gui/logo.png            → 页面里写 src: gui/logo.png",
                        "  textures/gui/logo.png   → 同上（带 textures 前缀也认）",
                        "  assets/opendreamcore/textures/gui/logo.png → 同上（原版包布局也认）",
                        "",
                        "也可以放完整的 zip / 文件夹材质包，启动时自动注入置顶。",
                        "下划线开头的文件/文件夹不参与预置。"));
            } catch (Exception e) {
                LOGGER.warn("骨架说明写入失败: {}", e.toString());
            }
        }
        Path meta = root.resolve("pack.mcmeta");
        if (!Files.exists(meta)) {
            try {
                Files.writeString(meta, "{\n"
                        + "  \"pack\": {\n"
                        + "    \"description\": \"OpenDreamCore 托管材质包\",\n"
                        + "    \"pack_format\": 15,\n"
                        + "    \"supported_formats\": [4, 99],\n"
                        + "    \"min_format\": 4,\n"
                        + "    \"max_format\": 99\n"
                        + "  }\n"
                        + "}\n");
            } catch (Exception e) {
                LOGGER.warn("骨架 pack.mcmeta 写入失败: {}", e.toString());
            }
        }
    }
}
