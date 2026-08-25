package com.opendreamcore;

import com.mojang.logging.LogUtils;
import com.opendreamcore.client.ClientEvents;
import com.opendreamcore.client.ClientMethods;
import com.opendreamcore.client.ClientPlaceholders;
import com.opendreamcore.client.UiFileWatcher;
import com.opendreamcore.client.UiRenderer;
import com.opendreamcore.client.WindowBranding;
import com.opendreamcore.network.ForgeChannel;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * OpenDreamCore Forge 1.20.1 客户端模组入口。
 */
@Mod(OpenDreamCore.MODID)
public final class OpenDreamCore {

    public static final String MODID = "opendreamcore";
    public static final Logger LOGGER = LogUtils.getLogger();

    public OpenDreamCore() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(this::onClientSetup);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            com.opendreamcore.script.CommonMethods.registerAll();
            ClientPlaceholders.registerAll();
            ClientEvents.registerScriptMethods();
            ClientMethods.registerAll();
            com.opendreamcore.client.spi.ResourcePackInjector.register(new com.opendreamcore.client.LegacyPackInjector());
            // 托管目录常驻注入：resourcepacks/OpenDreamCore 文件夹本身就是材质包，
            // 直接放 assets/<命名空间>/... 即加载，无需 zip、无需服务端下发
            try {
                var managed = net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
                        .resolve("resourcepacks").resolve("OpenDreamCore");
                java.nio.file.Files.createDirectories(managed);
                new com.opendreamcore.client.LegacyPackInjector().inject(managed, null, false);
                com.opendreamcore.client.ClientController.get().ensureManagedPacks(
                        net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get());
            } catch (Exception e) {
                com.opendreamcore.client.ClientController.LOGGER.warn("托管材质包目录注入失败: {}", e.toString());
            }
        });
        // 网络发送：直发 vanilla ServerboundCustomPayloadPacket（与 NeoForge sendRaw 同路径，
        // 线格式 = 通道名 + 协议消息完整二进制，Paper 服务端 Bukkit messenger 兜底接收）
        com.opendreamcore.client.ClientController.get().setSender(ForgeChannel::send);
        ForgeChannel.registerHandlers();
        // 文本自动高度测量（text.autoHeight / text.wrap → 布局按字体折行算高度）
        com.opendreamcore.ui.LayoutEngine.setTextAutoHeight((content, maxWidth, vars, lineHeight, fallback) -> {
            if (content == null || content.isEmpty()) {
                return fallback;
            }
            var mc = net.minecraft.client.Minecraft.getInstance();
            String resolved = UiRenderer.interpolate(null, content, vars);
            int maxPx = (int) Math.min(1_000_000, Math.max(8, maxWidth));
            String[] lines = UiRenderer.wrapLinesFlat(mc.font, resolved, maxPx);
            return Math.max(1, lines.length) * Math.max(1, lineHeight);
        });
        MinecraftForge.EVENT_BUS.addListener(ClientEvents::onLoggingIn);
        MinecraftForge.EVENT_BUS.addListener(ClientEvents::onLoggingOut);
        MinecraftForge.EVENT_BUS.addListener(ClientEvents::onRegisterCommands);
        MinecraftForge.EVENT_BUS.addListener(ClientEvents::onRenderGui);
        MinecraftForge.EVENT_BUS.addListener(ClientEvents::onScreenOpening);
        MinecraftForge.EVENT_BUS.addListener(ClientEvents::onRenderLevel);
        MinecraftForge.EVENT_BUS.addListener(ClientEvents::onChatReceived);
        MinecraftForge.EVENT_BUS.addListener(ClientEvents::onClientTick);
        // 本地文件监听：UI/fonts 目录自动热重载
        new UiFileWatcher().start();
        // 窗口 branding：OpenDreamCore/branding/title.txt + icon.png
        WindowBranding.apply();
    }
}
