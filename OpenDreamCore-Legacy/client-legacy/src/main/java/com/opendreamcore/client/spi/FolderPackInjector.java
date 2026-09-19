package com.opendreamcore.client.spi;

import java.io.File;
import java.util.List;

/**
 * 文件夹/zip 材质包注入口子：LocalPackPreload 扫出来的包交进来，
 * 各版本壳拿自家的资源管理器注册。注入方式因版本差异很大，共享层
 * 只管扫包和指纹，真挂载全在这条口子后面。
 */
public interface FolderPackInjector {

    /** 注册全部包并触发一次资源重载；返回成功注册的个数。 */
    int injectAll(List<File> packs);

    /**
     * 把托管根目录本身挂成常驻包（DreamEngine 同款体验）：玩家把贴图直接丢进
     * resourcepacks/OpenDreamCore 根下就能被 opendreamcore 命名空间读走，
     * 不用自己拼 assets/ 目录。实现要自己去重（重复调用应幂等）。默认不挂。
     */
    default void injectRoot(java.nio.file.Path root) {
    }

    /** 没人 register 时的默认行为：什么都不做。 */
    FolderPackInjector DEFAULT = new FolderPackInjector() {
        @Override
        public int injectAll(List<File> packs) {
            return 0;
        }
    };

    /** 存当前实现的地方，平台壳开局 register 一次。 */
    final class Host {
        private static volatile FolderPackInjector current;

        private Host() { }

        public static void register(FolderPackInjector injector) {
            if (injector != null) {
                current = injector;
            }
        }

        public static FolderPackInjector current() {
            FolderPackInjector c = current;
            return c == null ? DEFAULT : c;
        }
    }
}
