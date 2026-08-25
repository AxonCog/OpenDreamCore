package com.opendreamcore.client.spi;

import java.nio.file.Path;

/**
 * 自定义材质包注入 SPI（平台实现注册，编排层调用）。
 * 实现：neoforge/fabric/forge 各自的 PackRepository 写入差异下沉到本接口之后。
 */
public interface ResourcePackInjector {

    /**
     * 注入一个本地 zip 材质包。
     *
     * @param zipFile  zip 文件（已下载/已存在）
     * @param password 密码（加密 zip；null/空 = 明文包）
     * @param top      true = 置顶覆盖（优先级最高），false = 追加
     * @return 是否成功
     */
    boolean inject(Path zipFile, String password, boolean top);

    // ---- 持有器（ClientSetup 时平台壳调用 register）----

    /** 可变持有器：接口字段必须是常量，故移入嵌套类。 */
    final class Holder {
        private static volatile ResourcePackInjector instance;

        private Holder() {
        }
    }

    public static void register(ResourcePackInjector injector) {
        Holder.instance = injector;
    }

    public static ResourcePackInjector current() {
        return Holder.instance;
    }
}
