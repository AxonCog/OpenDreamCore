package com.opendreamcore.legacy;

import com.opendreamcore.script.NamespaceRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.common.MinecraftForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * OpenDreamCore 老版本线 · 1.12.2 入口。
 * 网络：原版协议 plugin message（VanillaBridge 在 Netty 管线里自己截包，
 * FML 通道在插件服务器上根本不存在）；渲染：Tessellator + GL11。
 */
@Mod(modid = OdcLegacy.MODID, name = "OpenDreamCore-Legacy", version = OdcLegacy.VERSION,
        acceptedMinecraftVersions = "[1.12.2]")
public final class OdcLegacy {
    public static final String MODID = "opendreamcore";
    public static final String VERSION = "0.1.2";

    public static final Logger LOGGER = LogManager.getLogger(MODID);

    // 这里别再拿静态字段 newSimpleChannel：静态字段在类加载（construct 阶段）
    // 就抢注一次"opendreamcore"，init 事件里再注册同名通道直接炸
    // "That channel is already registered"（实机 1.12.2 验证过的坑）。
    // 现在 FML 通道整个弃了，收发全走 VanillaBridge 的原版 plugin message

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        LegacyNamespaces.install();
        // mixin 初始化（原版侵入面：tooltip/名牌/盔甲/光标）
        org.spongepowered.asm.launch.MixinBootstrap.init();
        org.spongepowered.asm.mixin.Mixins.addConfiguration("opendreamcore.mixins.json");
        // 全局字符替换：换掉 Minecraft.fontRenderer 实例，聊天/输入框/书/UI 全接管
        GlobalFontRenderer.install(net.minecraft.client.Minecraft.getMinecraft());
        // 物品图标覆写：换掉 RenderItem 实例（老版本没 mixin，跟字体一个套路）
        GlobalRenderItem.install(net.minecraft.client.Minecraft.getMinecraft());
        // 散装贴图：中文文件名绕开资源包系统，运行时动态纹理注册
        com.opendreamcore.client.LooseTextureLoader.setRegistrar(
                new com.opendreamcore.client.TextureRegistrar1212());
        com.opendreamcore.client.LooseTextureLoader.scan(
                net.minecraft.client.Minecraft.getMinecraft().gameDir.toPath());
        // /odc 不在客户端注册命令：客户端一份+服务器插件一份的话，
        // 原版 TabCompleter 会把两边补全零去重拼在一起，TAB 弃两条 /odc
        // （实机截图实锤的“指令注册两遍”）。现在改为聊天事件拦截转发，
        // 见 ClientHooks.onClientChat，命令名补全全交给服务器
        MinecraftForge.EVENT_BUS.register(ClientHooks.class);
        // codc（客户端诊断）另走一家：命令名不啿 /odc，不会触发 Tab 补全重名
        CommandHandler1212.register();
        LOGGER.info("OpenDreamCore-Legacy 1.12.2 初始化完成");
    }
}
