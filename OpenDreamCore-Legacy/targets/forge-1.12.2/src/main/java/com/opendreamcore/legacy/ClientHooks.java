package com.opendreamcore.legacy;

import com.opendreamcore.client.ClientControllerLegacy;
import com.opendreamcore.client.MessageDispatcher;
import com.opendreamcore.client.WindowBranding;
import com.opendreamcore.client.spi.ResourcePackInjector;
import com.opendreamcore.protocol.Protocol;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** 客户端挂接：READY 握手、消息分发、overlay 页面渲染、窗口标题。 */
public final class ClientHooks {
    private static final MessageDispatcher DISPATCHER =
            new MessageDispatcher(OdcLegacy.LOGGER);

    /** codc 客户端诊断命令用：运行时唯一的 dispatcher 实例。 */
    public static MessageDispatcher dispatcher() {
        return DISPATCHER;
    }
    private static final Renderer1212 RENDERER = new Renderer1212();
    private static volatile String activePageId;

    /** ScreenBridge 只登记一次。 */
    private static boolean bridgeHooked;

    /** 本帧世界相位是否跑过：命中盒清帧只归先跑的那一路管。 */
    private static boolean worldPhaseRan;
    private static boolean sentReady;
    /** 渲染首帧已记过的页面，配合 logRendered 每页只打一条。 */
    private static final java.util.Set<String> lastRenderedPages = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    /** 服务端下发资源包时的提醒：老版本装不了，至少把地址告诉玩家。 */
    static boolean packHint(String url) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player != null) {
            mc.player.sendMessage(new net.minecraft.util.text.TextComponentString(
                    "§6[ODC]§r 服务器请求资源包: " + url + "（老版本请手动下载安装）"));
            return true;
        }
        return false;
    }

    /**
     * 这里以前拦 /odc 再转发，结果把聊天上下历史弄没了（玩家敲过的命令上箭头翻不到），
     * 转发出去的字符串又和原样发出去一模一样——图啥呢。
     * 现在不拦了，让它原生走服务器命令链，历史/TAB 补全/单命名空间全都正常。
     */

    /** 聊天栏提示（ChatNotifier SPI 用）。没进世界就当没说。 */
    static void chat(String text) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player != null) {
            mc.player.sendMessage(new net.minecraft.util.text.TextComponentString(text));
        }
    }

    static {
        ClientControllerLegacy.setSender((ch, p, d) ->
                VanillaBridge.sendToServer(p, d));
        ResourcePackInjector.Host.register(ClientHooks::packHint);
        // 字符替换的数据源：{player.name}/{player.x} 这些全从这取。
        // 匿名类直接挂，等连接后 player/world 就位时读值，
        // 没进世界时空兜底顶上，替换链不会炸
        com.opendreamcore.client.spi.PlayerInfoSource.Host.register(
                new com.opendreamcore.client.spi.PlayerInfoSource() {
                    @Override
                    public String name() {
                        Minecraft mc = Minecraft.getMinecraft();
                        return mc.player != null ? mc.player.getName() : "玩家";
                    }

                    @Override
                    public String dimension() {
                        Minecraft mc = Minecraft.getMinecraft();
                        if (mc.player == null || mc.world == null) {
                            return "overworld";
                        }
                        return mc.world.provider.getDimensionType().getName().toLowerCase(java.util.Locale.ROOT);
                    }

                    @Override
                    public double x() {
                        Minecraft mc = Minecraft.getMinecraft();
                        return mc.player != null ? mc.player.posX : 0;
                    }

                    @Override
                    public double y() {
                        Minecraft mc = Minecraft.getMinecraft();
                        return mc.player != null ? mc.player.posY : 0;
                    }

                    @Override
                    public double z() {
                        Minecraft mc = Minecraft.getMinecraft();
                        return mc.player != null ? mc.player.posZ : 0;
                    }

                    @Override
                    public double yaw() {
                        Minecraft mc = Minecraft.getMinecraft();
                        return mc.player != null ? mc.player.rotationYaw : 0;
                    }

                    @Override
                    public double pitch() {
                        Minecraft mc = Minecraft.getMinecraft();
                        return mc.player != null ? mc.player.rotationPitch : 0;
                    }

                    @Override
                    public java.util.Map<String, Object> extras() {
                        Minecraft mc = Minecraft.getMinecraft();
                        java.util.Map<String, Object> extras = new java.util.LinkedHashMap<>();
                        if (mc.player != null) {
                            extras.put("health", Math.round(mc.player.getHealth() * 10.0) / 10.0);
                            extras.put("hunger", mc.player.getFoodStats().getFoodLevel());
                            extras.put("max_health", Math.round(mc.player.getMaxHealth() * 10.0) / 10.0);
                            extras.put("level", mc.player.experienceLevel);
                            extras.put("exp", Math.round(mc.player.experience * 100.0) / 100.0);
                            try {
                                extras.put("gamemode", mc.playerController.getCurrentGameType().getName());
                            } catch (Throwable ignore) {
                            }
                            try {
                                extras.put("biome", mc.world.getBiome(mc.player.getPosition()).getBiomeName());
                            } catch (Throwable ignore) {
                            }
                        }
                        return extras;
                    }
                });
        // 标题口子必须交给 WindowBranding：tick() 每 tick 重写，
        // 直写一次的话原版随手覆盖就没了。
        // 缓存目录给 gameDir：重启游戏未进服也显示上次服务端标题
        WindowBranding.register(org.lwjgl.opengl.Display::setTitle, org.lwjgl.opengl.Display::getTitle);
        WindowBranding.setCacheDir(() -> Minecraft.getMinecraft().gameDir.toPath());
        // 聊天栏通知：页面解析失败/材质包异常这些要跟玩家说的事全走这
        com.opendreamcore.client.spi.ChatNotifier.Host.register(ClientHooks::chat);
        // 实体真身画笔：entity 元素从占位框升级成村民/盔甲架本尊
        com.opendreamcore.client.spi.EntityPainter.Host.register(new EntityPainter1212());
        com.opendreamcore.client.spi.LegacyEntityRenderBridge.Host.register(
                new com.opendreamcore.client.spi.LegacyEntityRenderBridgeImpl());
        com.opendreamcore.client.spi.LegacyItemRenderBridge.Host.register(
                new com.opendreamcore.client.spi.LegacyItemModelBridgeImpl());
        // 物品真身画笔：item_slot/hot_slot 从格底占位升级成本尊图标
        com.opendreamcore.client.spi.ItemPainter.Host.register(new ItemPainter1212());
// click/hover 音效：直接走 SoundHandler（自管，不需要 mixin）
        com.opendreamcore.client.spi.LegacySoundBridge.Host.register(
                (soundId, volume, pitch) -> {
                    try {
                        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
                        if (mc.player == null || mc.getSoundHandler() == null) {
                            return;
                        }
                        net.minecraft.util.SoundEvent event;
                        if (soundId == null || soundId.isEmpty()) {
                            event = net.minecraft.init.SoundEvents.UI_BUTTON_CLICK;
                        } else {
                            String id = soundId.indexOf(':') < 0 ? "minecraft:" + soundId : soundId;
                            net.minecraft.util.ResourceLocation rl =
                                    new net.minecraft.util.ResourceLocation(id);
                            event = net.minecraft.util.SoundEvent.REGISTRY.getObject(rl);
                            if (event == null) {
                                return; // 没注册的没人会播
                            }
                        }
                        mc.getSoundHandler().playSound(
                                net.minecraft.client.audio.PositionedSoundRecord.getMasterRecord(
                                        event, (float) pitch));
                    } catch (Throwable ignored) {
                        // 音效失败静默，页面照常
                    }
                });
        // 本地材质包区：首启建骨架+文件夹包/zip 即换即生效
        com.opendreamcore.client.spi.FolderPackInjector.Host.register(new FolderPackInjector1212());
        com.opendreamcore.client.LocalPackPreload.init(() -> Minecraft.getMinecraft().gameDir.toPath());
    }

    static {
        DISPATCHER.onOpen(id -> {
            // 渲染链关键节点：OPEN 落地才算接上，这条日志是排查『面板不渲染』的第一站
            OdcLegacy.LOGGER.info("[ODC] 页面控制落地: activePageId = {}", id);
            activePageId = id;
            lastRenderedPages.clear();
        });
        DISPATCHER.onHud(id -> { });
        DISPATCHER.onWindowTitle(WindowBranding::onWindowTitle);
        // 关页回调：activePageId 摘掉，世界页关了 HUD/屏幕页才回得来
        DISPATCHER.onClose(id -> {
            if (id != null && id.equals(activePageId)) {
                activePageId = null;
                lastRenderedPages.clear();
            }
        });
    }

    private ClientHooks() { }

    // 这两个钩子必须是 static：OdcLegacy 那边用 register(ClientHooks.class)
    // 按类注册，Forge 只认静态方法；挂成实例方法的话订阅静默失效，
    // 握手一个包都发不出去（实机踩过）
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        boolean connected = mc.getConnection() != null;
        if (connected && !sentReady) {
            // 连接刚建立：先把解码钩子挂进 Netty 管线（晚一点握手包就收不到），
            // 再向服务器报备下行通道（Bukkit 只给注册过的通道发包），
            // 都齐了才发 ready。管线每次进服都是新的，所以每条连接都要重来
            VanillaBridge.install();
            VanillaBridge.registerChannels();
            sentReady = true;
            com.opendreamcore.client.render.RenderSupport.markJoined();
            ClientControllerLegacy.sendReady(OdcLegacy.VERSION,
                    Protocol.CAPABILITY_LOCAL_UI | Protocol.CAPABILITY_CLOUD);
        } else if (!connected && sentReady) {
            sentReady = false;
            VanillaBridge.onDisconnect();
            WindowBranding.reset();
            // 视觉规则跟着会话走，断线清仓
            com.opendreamcore.client.visual.LegacyVisualSkins.clear();
            com.opendreamcore.client.VanillaHud.clear();
        }
        WindowBranding.tick();
        // 本地材质包区：首帧建骨架，之后每两秒查指纹、变了重注入
        com.opendreamcore.client.LocalPackPreload.tick();
        // KeyConfig 组合键：1.12.2 没有游戏内按键总线事件，tick 轮询边沿检测。
        // 键盘走共享层反射桥，LWJGL2 键码在，主菜单期Keyboard 未初始化也不炸
        com.opendreamcore.client.visual.LegacyVisualSkins.keyTickPoll(
                mc.currentScreen == null && mc.inGameHasFocus,
                com.opendreamcore.client.visual.KeyNames::lwjgl2IsKeyDown,
                com.opendreamcore.client.visual.KeyNames.lwjgl2Modifiers());
    }

    /**
     * 原版层开关：页面 options.hideVanilla 点名的层，Pre 阶段直接取消
     * 原版落笔，血条/热键栏这些就整层不画了。VanillaHud 集合每帧由
     * PageDirector.sync 重算，页关了自动恢复。
     */
    @SubscribeEvent
    public static void onRenderOverlayPre(RenderGameOverlayEvent.Pre event) {
        if (com.opendreamcore.client.VanillaHud.isHidden(event.getType().name())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.HOTBAR) {
            return;
        }
        try {
            hookScreenBridge();
            feedMouse(event.getResolution());
            RENDERER.setScaledResolution(event.getResolution());
            net.minecraft.client.gui.ScaledResolution _sr = event.getResolution();
            com.opendreamcore.client.render.RenderSupport.frame(_sr.getScaledWidth(), _sr.getScaledHeight());
            com.opendreamcore.client.render.RenderSupport.probeFps(Minecraft.class);
            com.opendreamcore.client.render.RenderSupport.put("gui_scale", Minecraft.getMinecraft().gameSettings.guiScale);
            // 帧序：世界相位先跑（RenderWorldLast）→ 覆盖层后跑。命中盒清帧
            // 归世界相位管；它没跑（无世界页）才在这清，别把世界刚登记的抹了
            if (!worldPhaseRan) {
                com.opendreamcore.client.render.Interactions.beginFrame();
            }
            worldPhaseRan = false;
            // 页面出场互斥（世界页独占/屏幕页单槽/HUD 常驻）收拟到 PageDirector，四版共用
            com.opendreamcore.client.render.PageDirector.render(DISPATCHER, RENDERER, activePageId);
            if (activePageId != null) {
                logRendered(activePageId);
            }
        } catch (Throwable t) {
            OdcLegacy.LOGGER.warn("[ODC] 页面渲染异常: {}", t.toString());
        }
    }

    /**
     * 世界相位：RenderWorldLast 时矩阵已是相机相对系，减回渲染视点
     * （viewerPos = renderViewEntity 插值位）就回到世界坐标。
     * 世界页/世界类视觉页在这层锚到玩家面前，billboard 朝镜头。
     */
    @SubscribeEvent
    public static void onRenderWorldLast(net.minecraftforge.client.event.RenderWorldLastEvent event) {
        try {
            com.opendreamcore.client.render.Interactions.beginFrame();
            worldPhaseRan = true;
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.player == null || mc.getRenderManager() == null) {
                return;
            }
            float pt = event.getPartialTicks();
            double px = mc.player.lastTickPosX + (mc.player.posX - mc.player.lastTickPosX) * pt;
            double py = mc.player.lastTickPosY + (mc.player.posY - mc.player.lastTickPosY) * pt;
            double pz = mc.player.lastTickPosZ + (mc.player.posZ - mc.player.lastTickPosZ) * pt;
            com.opendreamcore.client.render.PageDirector.renderWorldPhase(
                    DISPATCHER, RENDERER, activePageId,
                    mc.getRenderManager().viewerPosX, mc.getRenderManager().viewerPosY,
                    mc.getRenderManager().viewerPosZ,
                    px, py, pz, mc.player.rotationYaw, mc.player.rotationPitch,
                    Minecraft.getMinecraft().gameSettings.fovSetting);
        } catch (Throwable t) {
            OdcLegacy.LOGGER.warn("[ODC] 世界相位渲染异常: {}", t.toString());
        }
    }

    /** 每帧喂鼠标：开着界面或不聚焦时喂远处假坐标——hover 不粘、点击不漏进页面。 */
    /** 指针屏桥：交互页（菜单/可点世界面板）开隐形 GuiScreen 放真鼠标。 */
    private static void hookScreenBridge() {
        if (bridgeHooked) {
            return;
        }
        bridgeHooked = true;
        com.opendreamcore.client.render.ScreenBridge.setHost(
                new com.opendreamcore.client.render.ScreenBridge.Host() {
                    @Override
                    public boolean open(String pageId) {
                        return LegacyOdcScreen.requestOpen(pageId);
                    }

                    @Override
                    public void close() {
                        LegacyOdcScreen.requestClose();
                    }

                    @Override
                    public boolean isOpen() {
                        return LegacyOdcScreen.isOpen();
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

    private static void feedMouse(net.minecraft.client.gui.ScaledResolution sr) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen != null || !mc.inGameHasFocus) {
            com.opendreamcore.client.render.MouseState.set(-9999, -9999, false);
            return;
        }
        double scale = (double) mc.displayWidth / Math.max(1, sr.getScaledWidth());
        double mx = org.lwjgl.input.Mouse.getX() / scale;
        double my = sr.getScaledHeight() - 1 - org.lwjgl.input.Mouse.getY() / scale;
        com.opendreamcore.client.render.MouseState.set(mx, my, org.lwjgl.input.Mouse.isButtonDown(0));
    }

    /** 每页只记首帧：证明渲染链通了就够了，每 tick 打会把日志淹掉。 */
    private static void logRendered(String pageId) {
        if (lastRenderedPages.add(pageId)) {
            OdcLegacy.LOGGER.info("[ODC] 页面渲染首帧: {}", pageId);
        }
    }

    public static void onServerMessage(String path, byte[] data) {
        if (!DISPATCHER.dispatch(path, data)) {
            OdcLegacy.LOGGER.info("[ODC-Legacy] 未识别的服务端消息: {} ({} 字节)", path, data.length);
        }
    }
}
