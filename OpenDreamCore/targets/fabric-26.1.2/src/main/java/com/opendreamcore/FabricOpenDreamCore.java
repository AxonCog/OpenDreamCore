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
        // 世界语义渲染类型：世界面板/名牌挂上光影认得的可归类渲染类型，否则会被
        // 错误归类进半透明阶段、把深度缓冲写乱（物品与生物部分透明）。
        com.opendreamcore.client.render.WorldRenderTypes.register(
                new com.opendreamcore.client.render.OdcWorldRenderTypeProvider());
        com.opendreamcore.client.render.WorldRenderTypes.setEnabled(true);
        LOGGER.info("OpenDreamCore Fabric 客户端已加载");
    }
}
