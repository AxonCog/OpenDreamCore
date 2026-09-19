package com.opendreamcore;

import com.opendreamcore.protocol.Protocol;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * OpenDreamCore 老版本线 · 1.16.5 入口。
 * 架构与主仓一致：common 层承载全部逻辑（协议/页面/脚本/方法桥），
 * target 只做平台接线（网络通道、生命周期、事件挂接）。
 */
@Mod(OdcLegacy.MODID)
public final class OdcLegacy {
    public static final String MODID = "opendreamcore";
    public static final String VERSION = "0.1.2";

    public static final Logger LOGGER = LogManager.getLogger(MODID);

    public OdcLegacy() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        bus.addListener(this::setup);
        MinecraftForge.EVENT_BUS.register(this);
        // tick 驱动握手/标题，聊天拦截 /odc 转发
        MinecraftForge.EVENT_BUS.register(ClientHooks1165.class);
        com.opendreamcore.network.ForgeChannel.init();
    }

    private void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            LegacyNamespaces.install();
            // 散装贴图：中文文件名绕开资源包系统，运行时动态纹理注册
            com.opendreamcore.client.LooseTextureLoader.setRegistrar(
                    new com.opendreamcore.client.TextureRegistrar1165());
            try {
                com.opendreamcore.client.LooseTextureLoader.scan(
                        net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath());
            } catch (Throwable t) {
                LOGGER.warn("[ODC] 散装贴图扫描失败: {}", t.toString());
            }
        });
        LOGGER.info("OpenDreamCore-Legacy 1.16.5 初始化完成（协议 v{}）", Protocol.VERSION);
    }
}
