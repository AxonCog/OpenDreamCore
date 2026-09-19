package com.opendreamcore.legacy;

import com.opendreamcore.client.spi.ResourcePackInjector;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.network.NetworkMod;
import net.minecraftforge.common.MinecraftForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * OpenDreamCore 老版本线 · 1.6.4 入口。
 * 网络：Packet250CustomPayload 自定义通道；渲染：GL11 立即模式。
 */
@Mod(modid = OdcLegacy164.MODID, name = "OpenDreamCore-Legacy", version = OdcLegacy164.VERSION)
@NetworkMod(clientSideRequired = true, serverSideRequired = false,
        channels = {OdcLegacy164.CHANNEL}, packetHandler = PacketHandler164.class)
public final class OdcLegacy164 {
    public static final String MODID = "opendreamcore";
    public static final String VERSION = "0.1.2";
    public static final String CHANNEL = "ODC";

    public static final Logger LOGGER = LogManager.getLogger(MODID);

    @EventHandler
    public void init(FMLInitializationEvent event) {
        LegacyNamespaces164.install();
        // mixin 初始化（原版侵入面：tooltip/名牌/盔甲/光标）
        try {
            org.spongepowered.asm.launch.MixinBootstrap.init();
            org.spongepowered.asm.mixin.Mixins.addConfiguration("opendreamcore.mixins.json");
        } catch (Throwable t) {
            LOGGER.warn("[ODC] mixin 初始化失败：" + t);
        }
        // 全局字符替换：换掉 Minecraft.fontRenderer，聊天/输入/UI 全接管
        GlobalFontRenderer.install(net.minecraft.client.Minecraft.getMinecraft());
        // 物品图标覆写：换掉 RenderItem 实例（老版本没 mixin，跟字体一个套路）
        GlobalRenderItem.install(net.minecraft.client.Minecraft.getMinecraft());
        // 散装贴图：中文文件名绕开资源包系统，运行时动态纹理注册
        com.opendreamcore.client.LooseTextureLoader.setRegistrar(
                new com.opendreamcore.client.TextureRegistrar164());
        com.opendreamcore.client.LooseTextureLoader.scan(
                net.minecraft.client.Minecraft.getMinecraft().mcDataDir.toPath());
        // tick 里的握手和标题推进都挂在事件总线
        MinecraftForge.EVENT_BUS.register(ClientHooks164.class);
        // codc 本地诊断命令（聊天事件那代还没有，直接塞客户端命令表）
        CommandHandler164.register();
        LOGGER.info("OpenDreamCore-Legacy 1.6.4 初始化完成");
    }
}
