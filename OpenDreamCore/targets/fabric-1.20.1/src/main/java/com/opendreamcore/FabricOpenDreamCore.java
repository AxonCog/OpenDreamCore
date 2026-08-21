package com.opendreamcore;

import com.mojang.logging.LogUtils;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;

/**
 * OpenDreamCore Fabric 1.20.1 入口（main + client 双 entrypoint）。
 */
public final class FabricOpenDreamCore implements ModInitializer, ClientModInitializer {

    public static final String MODID = "opendreamcore";
    public static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public void onInitialize() {
        LOGGER.info("OpenDreamCore Fabric 1.20.1 服务端侧加载");
    }

    @Override
    public void onInitializeClient() {
        com.opendreamcore.client.ClientMethods.registerAll();
        com.opendreamcore.network.FabricChannel.registerClient();
        com.opendreamcore.client.FabricEvents.register();
        LOGGER.info("OpenDreamCore Fabric 1.20.1 客户端已加载");
    }
}
