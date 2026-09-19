package com.opendreamcore.client.bridge;

import com.opendreamcore.client.entity.EntityViews;
import com.opendreamcore.client.entity.ItemModelViews;

/**
 * 桥总注册口：target 在客户端入口只调一次 bootstrap(impl)，
 * 所有跨版本 SPI 的注册在这里统一完成，不再散落各 target。
 *
 * 骨架周期：首次启动 bootstrap 且未注册过对应桥时执行注册；
 * 重复调用幂等（Views 内部分别是同步锁 + 已注册复用）。
 */
public final class BridgeBootstrap {

    private static volatile boolean done;

    private BridgeBootstrap() {
    }

    /** target 入口调用一次：注册该版本全部桥。 */
    public static void bootstrap(TargetBridges bridges) {
        if (bridges == null) {
            return;
        }
        if (done) {
            return;
        }
        synchronized (BridgeBootstrap.class) {
            if (done) {
                return;
            }
            if (bridges.entityBridge() != null) {
                EntityViews.register(bridges.entityBridge());
            }
            if (bridges.itemModelBridge() != null) {
                ItemModelViews.register(bridges.itemModelBridge());
            }
            if (bridges.packInjector() != null) {
                com.opendreamcore.client.spi.ResourcePackInjector.register(bridges.packInjector());
            }
            done = true;
        }
    }
}