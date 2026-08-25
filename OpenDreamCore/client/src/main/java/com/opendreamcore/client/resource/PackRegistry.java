package com.opendreamcore.client.resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 材质包注册表：全局可用包表 + 统一重载入口。
 *
 * <p>平台差异（如何把目录/zip 变成引擎资源包对象）全部收敛在
 * {@link com.opendreamcore.client.spi.ResourcePackInjector} 的各 target 实现里；
 * 本类与其余内容处理器只面向"已安装"语义工作，跨版本零改动。</p>
 */
public final class PackRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(PackRegistry.class);

    /** 已安装包：id → 安装来源描述（供 /odc 指令族展示与重装）。 */
    private static final Map<String, InstalledPack> INSTALLED = new ConcurrentHashMap<>();

    /** 重载监听：reload 时逐个回调（内容处理器在此重读自己的资源）。 */
    private static final Map<String, Runnable> RELOAD_LISTENERS = new ConcurrentHashMap<>();

    public record InstalledPack(String id, String sourcePath, boolean top) {
    }

    private PackRegistry() {
    }

    /** 记录一个已安装包（注入成功后调用）。 */
    public static void markInstalled(String id, String sourcePath, boolean top) {
        INSTALLED.put(id, new InstalledPack(id, sourcePath, top));
        LOGGER.info("材质包已注册: {} ({})", id, sourcePath);
    }

    /** 移除记录。 */
    public static void markRemoved(String id) {
        INSTALLED.remove(id);
    }

    /** 已安装表快照（/odc pack list 用）。 */
    public static Map<String, InstalledPack> snapshot() {
        return Map.copyOf(INSTALLED);
    }

    /** 注册重载监听（id 冲突覆盖）。 */
    public static void onReload(String id, Runnable task) {
        RELOAD_LISTENERS.put(id, task);
    }

    /** 注入器引用（延迟取，避免类加载顺序问题）。 */
    private static volatile Supplier<com.opendreamcore.client.spi.ResourcePackInjector> injectorSupplier;

    public static void bindInjector(Supplier<com.opendreamcore.client.spi.ResourcePackInjector> supplier) {
        injectorSupplier = supplier;
    }

    /**
     * 重载全部：对每个已安装包按原路径重新走一遍安装管线，再逐个回调内容处理器。
     * 单包失败不影响其余。
     */
    public static void reload() {
        var injector = injectorSupplier != null ? injectorSupplier.get() : null;
        if (injector == null) {
            LOGGER.warn("重载跳过：注入器未就绪");
            return;
        }
        int ok = 0;
        for (InstalledPack p : INSTALLED.values()) {
            try {
                if (injector.inject(java.nio.file.Path.of(p.sourcePath()), null, p.top())) {
                    ok++;
                }
            } catch (Throwable t) {
                LOGGER.warn("重载失败 {}: {}", p.id(), t.toString());
            }
        }
        LOGGER.info("材质包重载完成 {}/{}", ok, INSTALLED.size());
        for (Map.Entry<String, Runnable> e : RELOAD_LISTENERS.entrySet()) {
            try {
                e.getValue().run();
            } catch (Throwable t) {
                LOGGER.warn("重载监听 {} 异常: {}", e.getKey(), t.toString());
            }
        }
    }
}
