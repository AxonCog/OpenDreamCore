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
        com.opendreamcore.client.spi.ResourcePackInjector.register(new com.opendreamcore.client.LegacyPackInjector());
        com.opendreamcore.script.CommonMethods.registerAll();
        com.opendreamcore.client.ClientPlaceholders.registerAll();
        com.opendreamcore.client.ClientMethods.registerAll();
        // mod 加载即创建托管材质包目录并扫描（版本隔离下 = 版本目录/resourcepacks/OpenDreamCore）
        try {
            com.opendreamcore.client.ClientController.get().ensureManagedPacks(
                    net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir());
        } catch (Throwable ignored) {
        }
        com.opendreamcore.network.FabricChannel.registerClient();
        com.opendreamcore.client.FabricEvents.register();
        // 文本自动高度（字体懒解析：入口阶段窗口可能未就绪）
        com.opendreamcore.ui.LayoutEngine.setTextAutoHeight((content, maxWidth, vars, lineHeight, fallback) -> {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (content == null || content.isEmpty() || mc == null || mc.font == null) {
                return fallback;
            }
            String resolved = com.opendreamcore.client.UiRenderer.interpolate(null, content, vars);
            int maxPx = (int) Math.min(1_000_000, Math.max(8, maxWidth));
            String[] lines = com.opendreamcore.client.UiRenderer.wrapLinesFlat(mc.font, resolved, maxPx);
            return Math.max(1, lines.length) * Math.max(1, lineHeight);
        });
        // 本地文件监听：UI/fonts 目录自动热重载
        new com.opendreamcore.client.UiFileWatcher().start();
        // 窗口 branding：OpenDreamCore/branding/title.txt + icon.png
        com.opendreamcore.client.WindowBranding.apply();
        LOGGER.info("OpenDreamCore Fabric 1.20.1 客户端已加载");
    }
}
