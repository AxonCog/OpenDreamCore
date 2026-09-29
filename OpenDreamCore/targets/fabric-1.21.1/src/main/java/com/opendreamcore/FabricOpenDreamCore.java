package com.opendreamcore;

import com.mojang.logging.LogUtils;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;

/**
 * OpenDreamCore Fabric 入口（main + client 双 entrypoint）。
 */
public final class FabricOpenDreamCore implements ModInitializer, ClientModInitializer {

    public static final String MODID = "opendreamcore";
    public static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public void onInitialize() {
        LOGGER.info("OpenDreamCore Fabric 服务端侧加载（本 target 以客户端为主）");
    }

    @Override
    public void onInitializeClient() {
        com.opendreamcore.script.CommonMethods.registerAll();
        com.opendreamcore.client.ClientMethods.registerAll();
        com.opendreamcore.network.FabricChannel.registerClient();
        com.opendreamcore.client.FabricEvents.register();
        // 直接注入客户端版本（编译期 Fabric API，反射探测老是挂成 unknown）
        try {
            net.fabricmc.loader.api.FabricLoader.getInstance()
                    .getModContainer(MODID)
                    .ifPresent(c -> {
                        String v = String.valueOf(c.getMetadata().getVersion());
                        if (v != null && !v.isBlank()) {
                            com.opendreamcore.client.ClientController.setClientVersion(v);
                            LOGGER.info("OpenDreamCore 客户端版本已注入: {}", v);
                        } else {
                            LOGGER.info("OpenDreamCore 版本注入跳过（空版本）");
                        }
                    });
        } catch (Throwable t) {
            LOGGER.warn("OpenDreamCore 版本注入失败: {}", String.valueOf(t));
        }
        LOGGER.info("OpenDreamCore Fabric 客户端已加载");
    }
}
