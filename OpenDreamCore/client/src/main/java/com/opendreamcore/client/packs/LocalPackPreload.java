package com.opendreamcore.client.packs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.opendreamcore.packs.PackInstaller;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * 本地材质包预置加载（无服务器也能生效）：
 * 启动时扫描 gameDir/OpenDreamCore/resourcepacks/ 下的 zip / 加密 zip / 文件夹包，
 * 经统一 PackInstaller 管线安装（解压到托管目录 + 平台注入器置顶注入）。
 *
 * <p>架构铁律：本能力属 common 共享管线，全部 target 自动对齐——平台差异只允许
 * 出现在 ResourcePackInjector SPI 实现内。</p>
 */
public final class LocalPackPreload {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalPackPreload.class);

    private LocalPackPreload() {
    }

    /** 扫描根：gameDir/resourcepacks/OpenDreamCore——版本隔离下即版本目录内的原版资源包区。 */
    public static Path scanRoot(Path gameDir) {
        return gameDir.resolve("resourcepacks").resolve("OpenDreamCore");
    }

    /**
     * 预置扫描安装。逐个包独立容错：单包失败不影响其余。
     *
     * @return 成功安装数
     */
    public static int preload(Path gameDir) {
        Path root = scanRoot(gameDir);
        try {
            Files.createDirectories(root); // 目录常驻，玩家可直接丢包
        } catch (Exception e) {
            LOGGER.warn("托管目录创建失败: {}", e.toString());
            return 0;
        }
        if (!Files.isDirectory(root)) {
            return 0;
        }
        int ok = 0;
        try (Stream<Path> list = Files.list(root)) {
            for (Path p : list.sorted().toList()) {
                String name = String.valueOf(p.getFileName());
                if (name.startsWith("_") || name.equals("OpenDreamCore")) {
                    continue; // 下划线开头 = 不参与预置；托管目录自身跳过
                }
                if (!Files.isRegularFile(p) && !Files.isDirectory(p)) {
                    continue;
                }
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
        } catch (Exception e) {
            LOGGER.warn("本地材质包扫描失败: {}", e.toString());
        }
        return ok;
    }
}
