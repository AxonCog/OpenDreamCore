package com.opendreamcore.legacy;

import com.opendreamcore.client.ClientControllerLegacy;
import com.opendreamcore.client.MessageDispatcher;
import com.opendreamcore.client.WindowBranding;
import com.opendreamcore.client.spi.ResourcePackInjector;

/** 客户端挂接：READY 握手、消息分发、overlay 页面渲染、窗口标题。 */
public final class ClientHooks164 {
    private static final MessageDispatcher DISPATCHER =
            new MessageDispatcher(OdcLegacy164.LOGGER);

    /** codc 本地命令用：全局唯一 dispatcher。 */
    public static MessageDispatcher dispatcher() {
        return DISPATCHER;
    }
    private static boolean sentReady;
    /** 当前 OPEN 的页面 id；onOpen/onClose 回调在 static 块里就要用，必须先声明。 */
    private static volatile String activePageId;

    static {
        // 发送函数交出去，握手和转发都走 ClientControllerLegacy
        ClientControllerLegacy.setSender((ch, p, d) ->
                PacketHandler164.sendToServer(p, d));
        // 服务端下发的标题/打字机统一进 WindowBranding；
        // 注册时顺便抓原版标题，RESET/断线后还原用
        DISPATCHER.onWindowTitle(WindowBranding::onWindowTitle);
        WindowBranding.register(org.lwjgl.opengl.Display::setTitle,
                org.lwjgl.opengl.Display::getTitle);
        // 标题缓存：gameDir/OpenDreamCore/window-title.txt，重启未进服也顶上
        WindowBranding.setCacheDir(() -> net.minecraft.client.Minecraft.getMinecraft().mcDataDir.toPath());
                ResourcePackInjector.Host.register(ClientHooks164::packHint);
        // 聊天栏通知：页面解析失败/材质包异常这些要跟玩家说的事全走这
        com.opendreamcore.client.spi.ChatNotifier.Host.register(ClientHooks164::chat);
        // 本地材质包区：托管根直读 + 文件夹包/zip 即换即生效
        com.opendreamcore.client.spi.FolderPackInjector.Host.register(new FolderPackInjector164());
        com.opendreamcore.client.LocalPackPreload.init(
                () -> net.minecraft.client.Minecraft.getMinecraft().mcDataDir.toPath());
        // 云缓存：gameDir/OpenDreamCore/cache，哈希名落盘、内存解密（现代端 CloudSyncClient 同链路）
        com.opendreamcore.client.CloudCache.setGameDir(
                () -> net.minecraft.client.Minecraft.getMinecraft().mcDataDir.toPath());
        // 实体真身画笔：entity 元素从占位框升级成村民/盔甲架本尊
        com.opendreamcore.client.spi.EntityPainter.Host.register(new EntityPainter164());
        com.opendreamcore.client.spi.LegacyEntityRenderBridge.Host.register(
                new com.opendreamcore.client.spi.LegacyEntityRenderBridgeImpl());
        com.opendreamcore.client.spi.LegacyItemRenderBridge.Host.register(
                new com.opendreamcore.client.spi.LegacyItemModelBridgeImpl());
        // 物品真身画笔：item_slot/hot_slot 从格底占位升级成本尊图标
        com.opendreamcore.client.spi.ItemPainter.Host.register(new ItemPainter164());
// click/hover 音效：直接走 SoundHandler（自管，不需要 mixin）
        com.opendreamcore.client.spi.LegacySoundBridge.Host.register(
                (soundId, volume, pitch) -> {
                    try {
                        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
                        if (mc.thePlayer == null) {
                            return;
                        }
                        String id = soundId == null || soundId.isEmpty()
                                ? "gui.button.press"
                                : soundId.indexOf(':') < 0 ? "minecraft:" + soundId : soundId;
                        mc.thePlayer.playSound(id, (float) volume, (float) pitch);
                    } catch (Throwable ignored) {
                        // 音效失败静默，页面照常
                    }
                });
        DISPATCHER.onOpen(id -> {
            activePageId = id;
            com.opendreamcore.client.LocalOdc.activePageId.set(id);
        });
        // 关页回调：activePageId 摘掉，世界页关了 HUD/屏幕页才回得来
        DISPATCHER.onClose(id -> {
            if (id != null && id.equals(activePageId)) {
                activePageId = null;
                com.opendreamcore.client.LocalOdc.activePageId.set("");
            }
        });
        // 字符替换的数据源：{{player.name}}/{{player.x}} 这些全从这取。
        // 没进世界时空兜底顶上，替换链不会炸
        com.opendreamcore.client.spi.PlayerInfoSource.Host.register(
                new com.opendreamcore.client.spi.PlayerInfoSource() {
                    @Override
                    public String name() {
                        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
                        return mc.thePlayer != null ? mc.thePlayer.getCommandSenderName() : "玩家";
                    }

                    @Override
                    public String dimension() {
                        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
                        if (mc.thePlayer == null || mc.theWorld == null) {
                            return "overworld";
                        }
                        return mc.theWorld.provider.getDimensionName().toLowerCase(java.util.Locale.ROOT);
                    }

                    @Override
                    public double x() {
                        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
                        return mc.thePlayer != null ? mc.thePlayer.posX : 0;
                    }

                    @Override
                    public double y() {
                        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
                        return mc.thePlayer != null ? mc.thePlayer.posY : 0;
                    }

                    @Override
                    public double z() {
                        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
                        return mc.thePlayer != null ? mc.thePlayer.posZ : 0;
                    }

                    @Override
                    public double yaw() {
                        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
                        return mc.thePlayer != null ? mc.thePlayer.rotationYaw : 0;
                    }

                    @Override
                    public double pitch() {
                        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
                        return mc.thePlayer != null ? mc.thePlayer.rotationPitch : 0;
                    }

                    @Override
                    public java.util.Map<String, Object> extras() {
                        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
                        java.util.Map<String, Object> extras = new java.util.LinkedHashMap<>();
                        if (mc.thePlayer != null) {
                            extras.put("health", Math.round(mc.thePlayer.getHealth() * 10.0) / 10.0);
                            extras.put("hunger", mc.thePlayer.getFoodStats().getFoodLevel());
                            extras.put("max_health", Math.round(mc.thePlayer.getMaxHealth() * 10.0) / 10.0);
                            extras.put("level", mc.thePlayer.experienceLevel);
                            extras.put("exp", Math.round(mc.thePlayer.experience * 100.0) / 100.0);
                            try {
                                // currentGameType 是私有的：反射读（生产环境字段是 SRG 名，两个名字挨个试）
                                String[] names = {"currentGameType", "field_78779_k"};
                                for (String name : names) {
                                    try {
                                        java.lang.reflect.Field f = mc.playerController.getClass()
                                                .getDeclaredField(name);
                                        f.setAccessible(true);
                                        Object gt = f.get(mc.playerController);
                                        extras.put("gamemode", ((Enum<?>) gt).name().toLowerCase());
                                        break;
                                    } catch (NoSuchFieldException e) {
                                        // 下一个名字接着试
                                    }
                                }
                            } catch (Throwable ignore) {
                            }
                            try {
                                extras.put("biome", mc.theWorld.getBiomeGenForCoords(
                                        (int) mc.thePlayer.posX, (int) mc.thePlayer.posZ).biomeName);
                            } catch (Throwable ignore) {
                            }
                        }
                        return extras;
                    }
                });
    }

    /**
     * 1.6.4 没有 tick 事件可挂，握手和标题推进就搭在 overlay 渲染事件上：
     * 每帧都会进来，频率足够，进世界前这段渲染不跑也不会误发。
     */
    @net.minecraftforge.event.ForgeSubscribe
    // register(ClientHooks164.class) 按类注册只认静态方法，实例方法订阅静默失效
    public static void onRenderOverlay(net.minecraftforge.client.event.RenderGameOverlayEvent event) {
        // 原版层开关：Pre 阶段取消页面 hideVanilla 点名的层（基类本身
        // 就是 @Cancelable，所有子类都能取消），血条这些就整层不画了
        if (event instanceof net.minecraftforge.client.event.RenderGameOverlayEvent.Pre
                && com.opendreamcore.client.VanillaHud.isHidden(event.type.name())) {
            event.setCanceled(true);
            return;
        }
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
        boolean connected = mc.getNetHandler() != null;
        if (connected && !sentReady) {
            sentReady = true;
            // 先报备再握手，顺序不能反：插件收到 READY 后立刻往下发
            // 标题/页面，下行通道不报备好全被丢包
            PacketHandler164.registerChannels();
            com.opendreamcore.client.render.RenderSupport.markJoined();
            ClientControllerLegacy.sendReady(OdcLegacy164.VERSION, 0);
        } else if (!connected && sentReady) {
            sentReady = false;
            WindowBranding.reset();
            com.opendreamcore.client.VanillaHud.clear();
        }
        // overlay 事件每个元素类型都发一遍（准星/血条/盔甲…），
        // 只挑 ALL 那一次画页面，不然一帧画几十遍
        if (event.type == net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType.ALL
                && mc.theWorld != null) {
            render(event.resolution);
        }
    }

    /** 每帧喂鼠标：开着界面或不聚焦时喂远处假坐标——hover 不粘、点击不漏进页面。 */
    private static void feedMouse(net.minecraft.client.gui.ScaledResolution sr) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
        if (mc.currentScreen != null || !mc.inGameHasFocus) {
            com.opendreamcore.client.render.MouseState.set(-9999, -9999, false);
            return;
        }
        double scale = (double) mc.displayWidth / Math.max(1, sr.getScaledWidth());
        double mx = org.lwjgl.input.Mouse.getX() / scale;
        double my = sr.getScaledHeight() - 1 - org.lwjgl.input.Mouse.getY() / scale;
        com.opendreamcore.client.render.MouseState.set(mx, my, org.lwjgl.input.Mouse.isButtonDown(0));
    }

    /** 服务端下发资源包时的提醒：老版本装不了，至少把地址告诉玩家。 */
    static boolean packHint(String url) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
        if (mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(
                    "§6[ODC]§r 服务器请求资源包: " + url + "（老版本请手动下载安装）");
            return true;
        }
        return false;
    }

    /** 聊天栏提示（ChatNotifier SPI 用）。没进世界就当没说。 */
    static void chat(String text) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
        if (mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(text);
        }
    }

    private ClientHooks164() { }

    private static final Renderer164 RENDERER = new Renderer164();

    /**
     * 世界相位：RenderWorldLast 时矩阵已是相机相对系，减回渲染视点
     * （RenderManager.renderPos，静态）就回到世界坐标。与 1710 同配方。
     */
    @net.minecraftforge.event.ForgeSubscribe
    public static void onRenderWorldLast(net.minecraftforge.client.event.RenderWorldLastEvent event) {
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
            if (mc.thePlayer == null) {
                return;
            }
            float pt = event.partialTicks;
            double px = mc.thePlayer.lastTickPosX + (mc.thePlayer.posX - mc.thePlayer.lastTickPosX) * pt;
            double py = mc.thePlayer.lastTickPosY + (mc.thePlayer.posY - mc.thePlayer.lastTickPosY) * pt;
            double pz = mc.thePlayer.lastTickPosZ + (mc.thePlayer.posZ - mc.thePlayer.lastTickPosZ) * pt;
            com.opendreamcore.client.render.PageDirector.renderWorldPhase(
                    DISPATCHER, RENDERER, activePageId,
                    net.minecraft.client.renderer.entity.RenderManager.renderPosX,
                    net.minecraft.client.renderer.entity.RenderManager.renderPosY,
                    net.minecraft.client.renderer.entity.RenderManager.renderPosZ,
                    px, py, pz, mc.thePlayer.rotationYaw);
        } catch (Throwable t) {
            OdcLegacy164.LOGGER.warn("[ODC] 世界相位渲染异常: {}", t.toString());
        }
    }

    /** HUD/世界渲染钩子调用：注入分辨率后画当前页 + HUD 页。 */
    public static void render(net.minecraft.client.gui.ScaledResolution sr) {
        WindowBranding.tick();
        hookScreenBridge();
        // 本地材质包区：首帧建骨架，之后每两秒查指纹、变了重注入
        com.opendreamcore.client.LocalPackPreload.tick();
        // KeyConfig 组合键：1.6.4 事件只有 MouseEvent，键盘轮询走这里（每渲染帧）
        net.minecraft.client.Minecraft _mc = net.minecraft.client.Minecraft.getMinecraft();
        com.opendreamcore.client.visual.LegacyVisualSkins.keyTickPoll(
                _mc.currentScreen == null && _mc.inGameHasFocus,
                com.opendreamcore.client.visual.KeyNames::lwjgl2IsKeyDown,
                com.opendreamcore.client.visual.KeyNames.lwjgl2Modifiers());
        feedMouse(sr);
        RENDERER.setScaledResolution(sr);
        com.opendreamcore.client.render.RenderSupport.frame(sr.getScaledWidth(), sr.getScaledHeight());
        com.opendreamcore.client.render.RenderSupport.probeFps(net.minecraft.client.Minecraft.class);
        com.opendreamcore.client.render.RenderSupport.put("gui_scale", _mc.gameSettings.guiScale);
        // 互斥策略（世界页独占/屏幕页单槽/HUD 常驻）收拉到 PageDirector，四版共用
        com.opendreamcore.client.render.PageDirector.render(DISPATCHER, RENDERER, activePageId);
    }

    /** 指针屏桥：交互页开隐形屏放真鼠标（同 1.12.2）。首帧挂上即可。 */
    private static boolean bridgeHooked;

    private static void hookScreenBridge() {
        if (bridgeHooked) {
            return;
        }
        bridgeHooked = true;
        com.opendreamcore.client.render.ScreenBridge.setHost(
                new com.opendreamcore.client.render.ScreenBridge.Host() {
                    @Override
                    public boolean open(String pageId) {
                        return LegacyOdcScreen164.requestOpen(pageId);
                    }

                    @Override
                    public void close() {
                        LegacyOdcScreen164.requestClose();
                    }

                    @Override
                    public boolean isOpen() {
                        return LegacyOdcScreen164.isOpen();
                    }

                    @Override
                    public void onDismissed(String pageId) {
                        // ESC 关页：本地同步清活跃页，状态检查不会又把屏开回去
                        if (pageId != null && pageId.equals(activePageId)) {
                            activePageId = null;
                        }
                    }
                });
    }

    public static void onServerMessage(String path, byte[] data) {
        if (DISPATCHER.dispatch(path, data)) {
            return;
        }
        OdcLegacy164.LOGGER.info("[ODC-Legacy] 未识别的服务端消息: {} ({} 字节)", path, data.length);
    }
}
