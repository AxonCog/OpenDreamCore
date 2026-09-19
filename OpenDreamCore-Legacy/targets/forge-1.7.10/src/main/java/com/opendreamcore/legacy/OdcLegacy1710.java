package com.opendreamcore.legacy;

import com.opendreamcore.script.NamespaceRegistry;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.common.MinecraftForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * OpenDreamCore 老版本线 · 1.7.10 入口。
 * 网络：FML SimpleNetworkWrapper（1.7.10 已内置）；渲染：GL11 立即模式。
 */
@Mod(modid = OdcLegacy1710.MODID, name = "OpenDreamCore-Legacy", version = OdcLegacy1710.VERSION,
        guiFactory = "com.opendreamcore.legacy.ClientGuiFactory1710")
public final class OdcLegacy1710 {
    public static final String MODID = "opendreamcore";
    public static final String VERSION = "0.1.2";

    public static final Logger LOGGER = LogManager.getLogger(MODID);
    public static final SimpleNetworkWrapper CHANNEL =
            NetworkRegistry.INSTANCE.newSimpleChannel(MODID);

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        LegacyChannel1710.init(CHANNEL);
        // mixin 初始化（原版侵入面：tooltip/名牌/盔甲/光标）
        try {
            org.spongepowered.asm.launch.MixinBootstrap.init();
            org.spongepowered.asm.mixin.Mixins.addConfiguration("opendreamcore.mixins.json");
        } catch (Throwable t) {
            LOGGER.warn("[ODC] mixin 初始化失败（原版侵入面不生效）：{}", t.toString());
        }
        LegacyNamespaces1710.install();
        // 全局字符替换：换掉 Minecraft.fontRenderer，聊天/输入/UI 全接管
        GlobalFontRenderer.install(net.minecraft.client.Minecraft.getMinecraft());
        // 物品图标覆写：换掉 RenderItem 实例（老版本没 mixin，跟字体一个套路）
        GlobalRenderItem.install(net.minecraft.client.Minecraft.getMinecraft());
        // 散装贴图：中文文件名绕开资源包系统，运行时动态纹理注册
        com.opendreamcore.client.LooseTextureLoader.setRegistrar(
                new com.opendreamcore.client.TextureRegistrar1710());
        com.opendreamcore.client.LooseTextureLoader.scan(
                net.minecraft.client.Minecraft.getMinecraft().mcDataDir.toPath());
        MinecraftForge.EVENT_BUS.register(ClientHooks1710.class);
        // codc 本地诊断命令：不走事件总线（那代 ClientChatEvent 根本没有），
        // 直接塞 ClientCommandHandler，参数从 split args 拼回
        CommandHandler1710.register();
        LOGGER.info("OpenDreamCore-Legacy 1.7.10 初始化完成");
    }
}
