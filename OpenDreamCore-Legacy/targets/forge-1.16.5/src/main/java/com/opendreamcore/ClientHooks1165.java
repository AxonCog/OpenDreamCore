package com.opendreamcore;

import com.opendreamcore.client.ClientControllerLegacy;
import com.opendreamcore.client.MessageDispatcher;
import com.opendreamcore.client.WindowBranding;
import com.opendreamcore.client.spi.ResourcePackInjector;
import com.opendreamcore.protocol.Protocol;
import com.opendreamcore.network.EntityPainter1165;
import com.opendreamcore.network.VanillaBridge165;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 1.16.5 客户端挂接：tick 里挂管线钩子、声明下行通道、握手、推进窗口标题、
 * 画页面（overlay 事件驱动）。
 */
public final class ClientHooks1165 {
    private static final MessageDispatcher DISPATCHER = new MessageDispatcher(OdcLegacy.LOGGER);

    /** codc 本地命令用：全局唯一 dispatcher。 */
    public static MessageDispatcher dispatcher() {
        return DISPATCHER;
    }
    private static boolean sentReady;

    private ClientHooks1165() { }

    /** 握手 + 每 tick 推进一次窗口标题。 */
    @SubscribeEvent
    // register(ClientHooks1165.class) 按类注册只认静态方法，实例方法订阅静默失效
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        boolean connected = mc.getConnection() != null;
        if (connected && !sentReady) {
            // 连接刚建立：先把解码钩子挂进 Netty 管线，再声明下行通道
            // （Bukkit 只给声明过的通道发包），都齐了才发 ready。
            // 管线每次进服都是新的，所以每条连接都要重来
            VanillaBridge165.install();
            VanillaBridge165.registerChannels();
            sentReady = true;
            com.opendreamcore.client.render.RenderSupport.markJoined();
            ClientControllerLegacy.sendReady(OdcLegacy.VERSION,
                    Protocol.CAPABILITY_LOCAL_UI | Protocol.CAPABILITY_CLOUD);
        } else if (!connected && sentReady) {
            sentReady = false;
            WindowBranding.reset();
            // 视觉规则跟着会话走，断线清仓
            com.opendreamcore.client.visual.LegacyVisualSkins.clear();
            com.opendreamcore.client.VanillaHud.clear();
        }
        WindowBranding.tick();
        // 本地材质包区：首帧建骨架，之后每两秒查指纹、变了重注入
        com.opendreamcore.client.LocalPackPreload.tick();
    }

    /**
     * 聊天栏拦截：本地命令都从命令注册表里取（核心自带 codc + 附属自注的）。
     * 这代没有客户端命令树可挂（命令全走原版服务器校验），所以只能从聊天事件里截；
     * 和另外三版注册进 ClientCommandHandler 效果一致。
     */
    @SubscribeEvent
    public static void onClientChat(ClientChatEvent event) {
        if (com.opendreamcore.client.api.LegacyCommandRegistry.dispatch(
                event.getMessage(), com.opendreamcore.client.codc.CodcCommandBridge.CHAT_OUT)) {
            event.setCanceled(true);
        }
    }

    /** 聊天栏提示（ChatNotifier SPI 用）。没进世界就当没说。 */
    static void chat(String text) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendMessage(new net.minecraft.util.text.StringTextComponent(text),
                    mc.player.getUUID());
        }
    }

    /** 服务端下发资源包时的提醒。 */
    static boolean packHint(String url) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(
                    new net.minecraft.util.text.StringTextComponent(
                            "\u00a76[ODC]\u00a7r 服务器请求资源包: " + url), false);
            return true;
        }
        return false;
    }

    /** 服务端消息统一入口（管线钩子打进来）。 */
    public static void onServerMessage(String path, byte[] data) {
        if (!DISPATCHER.dispatch(path, data)) {
            OdcLegacy.LOGGER.info("[ODC-Legacy] 未识别的服务端消息: {} ({} 字节)", path, data.length);
        }
    }

    private static final Renderer1165 RENDERER = new Renderer1165();
    private static volatile String activePageId;
    private static final java.util.Set<String> lastRenderedPages =
            java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    /** 每帧喂鼠标：GLFW 问窗口要逻辑坐标，开着界面/失焦时喂远处假坐标。 */
    private static void feedMouse(net.minecraft.client.Minecraft mc) {
        if (mc.screen != null || !mc.isWindowActive()) {
            com.opendreamcore.client.render.MouseState.set(-9999, -9999, false);
            return;
        }
        net.minecraft.client.MainWindow win = mc.getWindow();
        long hwnd = win.getWindow();
        java.nio.DoubleBuffer pos = org.lwjgl.BufferUtils.createDoubleBuffer(2);
        org.lwjgl.glfw.GLFW.glfwGetCursorPos(hwnd, pos, pos);
        // 物理像素按 GUI 缩放折算成逻辑像素，y 轴从底往上要翻
        double scale = (double) win.getWidth() / Math.max(1, win.getGuiScaledWidth());
        double mx = pos.get(0) / scale;
        double my = win.getGuiScaledHeight() - 1 - pos.get(1) / scale;
        boolean down = org.lwjgl.glfw.GLFW.glfwGetMouseButton(hwnd, 0)
                == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        com.opendreamcore.client.render.MouseState.set(mx, my, down);
    }

    /** 每页只记首帧：证明渲染链通了就够了，每 tick 打会把日志淹掉。 */
    private static void logRendered(String pageId) {
        if (lastRenderedPages.add(pageId)) {
            OdcLegacy.LOGGER.info("[ODC] 页面渲染首帧: {}", pageId);
        }
    }

    /**
     * 原版层开关：页面 options.hideVanilla 点名的层，Pre 阶段直接取消
     * 原版落笔，血条/热键栏这些就整层不画了。
     */
    @SubscribeEvent
    public static void onRenderOverlayPre(net.minecraftforge.client.event.RenderGameOverlayEvent.Pre event) {
        if (com.opendreamcore.client.VanillaHud.isHidden(event.getType().name())) {
            event.setCanceled(true);
        }
    }

    /**
     * 页面渲染入口：搭在血条那格 overlay 的 Post 上，每帧稳定触发一次。
     * 1.16.5 的 overlay 事件自带 MatrixStack，但坐标口径和逻辑像素对不上，
     * 渲染器里自建栈，这里只管送尺寸进来。
     */
    @SubscribeEvent
    public static void onRenderOverlay(net.minecraftforge.client.event.RenderGameOverlayEvent.Post event) {
        if (event.getType() != net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType.HOTBAR) {
            return;
        }
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null || LegacyOdcScreen1165.isOpen()) {
            return; // 指针屏开着时它自己的 render 接手画（overlay 那时有界面不触发）
        }
        feedMouse(mc);
        renderPage();
    }

    /**
     * 页面渲染主体：overlay 事件和隐形指针屏的 render 都调这里，口径一套。
     * 鼠标喂坐标由调用方自己负。
     */
    static void renderPage() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        try {
            RENDERER.updateViewport(mc);
            com.opendreamcore.client.render.RenderSupport.frame(RENDERER.screenWidth(), RENDERER.screenHeight());
            com.opendreamcore.client.render.RenderSupport.probeFps(net.minecraft.client.Minecraft.class);
            com.opendreamcore.client.render.RenderSupport.put("gui_scale",
                    mc.options.guiScale);
            com.opendreamcore.client.render.Interactions.beginFrame();
            // 页面出场互斥（世界页独占/屏幕页单槽/HUD 常驻）收拟到 PageDirector，四版共用
            com.opendreamcore.client.render.PageDirector.render(DISPATCHER, RENDERER, activePageId);
        } catch (Throwable t) {
            OdcLegacy.LOGGER.warn("[ODC] 页面渲染异常: {}", t.toString());
        }
    }

    /**
     * 世界相位：世界页/世界类视觉页住在这层，錨到玩家面前、billboard 朝镜头。
     * 矩阵已扣相机（事件栈是相机相对系），这里把渲染视点和玩家插值位、
     * 朝向一并递给 PageDirector，它自己算錨点。
     */
    @SubscribeEvent
    public static void onRenderWorldLast(net.minecraftforge.client.event.RenderWorldLastEvent event) {
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.level == null || mc.player == null || mc.gameRenderer == null
                    || mc.gameRenderer.getMainCamera() == null) {
                return;
            }
            net.minecraft.util.math.vector.Vector3d cam = mc.gameRenderer.getMainCamera().getPosition();
            float pt = event.getPartialTicks();
            double px = mc.player.xo + (mc.player.getX() - mc.player.xo) * pt;
            double py = mc.player.yo + (mc.player.getY() - mc.player.yo) * pt;
            double pz = mc.player.zo + (mc.player.getZ() - mc.player.zo) * pt;
            com.opendreamcore.client.render.PageDirector.renderWorldPhase(
                    DISPATCHER, RENDERER, activePageId,
                    cam.x, cam.y, cam.z, px, py, pz, mc.player.yRot);
        } catch (Throwable t) {
            OdcLegacy.LOGGER.warn("[ODC] 世界相位渲染异常: {}", t.toString());
        }
    }

    static {
        // 原版协议桥：插件服务器认的是 Bukkit plugin messaging，
        // SimpleChannel 只在 Forge 服务器上存在，那套发出去就是死包
        ClientControllerLegacy.setSender((ch, p, d) ->
                VanillaBridge165.sendToServer(p, d));
                ResourcePackInjector.Host.register(ClientHooks1165::packHint);
        // 聊天栏通知：页面解析失败/材质包异常这些要跟玩家说的事全走这
        com.opendreamcore.client.spi.ChatNotifier.Host.register(ClientHooks1165::chat);
        // 实体真身画笔：entity 元素从占位框升级成村民/盔甲架本尊
        com.opendreamcore.client.spi.EntityPainter.Host.register(new EntityPainter1165());
        com.opendreamcore.client.spi.LegacyEntityRenderBridge.Host.register(
                new com.opendreamcore.client.spi.LegacyEntityRenderBridgeImpl());
        com.opendreamcore.client.spi.LegacyItemRenderBridge.Host.register(
                new com.opendreamcore.client.spi.LegacyItemModelBridgeImpl());
        // 本地材质包区：文件夹包/zip 即换即生效
        com.opendreamcore.client.spi.FolderPackInjector.Host.register(new FolderPackInjector1165());
        com.opendreamcore.client.LocalPackPreload.init(
                () -> net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get());
        // 云缓存：gameDir/OpenDreamCore/cache，哈希名落盘、内存解密（现代端 CloudSyncClient 同链路）
        com.opendreamcore.client.CloudCache.setGameDir(
                () -> net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get());
        // 物品真身画笔：item_slot/hot_slot 从格底占位升级成本尊图标
        com.opendreamcore.client.spi.ItemPainter.Host.register(new ItemPainter1165());
// click/hover 音效：直接走 SoundHandler（自管，不需要 mixin）
        com.opendreamcore.client.spi.LegacySoundBridge.Host.register(
                (soundId, volume, pitch) -> {
                    try {
                        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                        if (mc.player == null || mc.getSoundManager() == null) {
                            return;
                        }
                        net.minecraft.util.SoundEvent event;
                        if (soundId == null || soundId.isEmpty()) {
                            event = net.minecraft.util.SoundEvents.UI_BUTTON_CLICK;
                        } else {
                            String id = soundId.indexOf(':') < 0 ? "minecraft:" + soundId : soundId;
                            net.minecraft.util.ResourceLocation rl =
                                    net.minecraft.util.ResourceLocation.tryParse(id);
                            if (rl == null) {
                                return;
                            }
                            event = new net.minecraft.util.SoundEvent(rl);
                        }
                        mc.getSoundManager().play(net.minecraft.client.audio.SimpleSound.forUI(
                                event, (float) pitch));
                    } catch (Throwable ignored) {
                        // 音效失败静默，页面照常
                    }
                });
        // 字符替换的数据源：{{player.name}}/{{player.x}} 这些全从这取。
        // 没进世界时空兜底顶上，替换链不会炸
        com.opendreamcore.client.spi.PlayerInfoSource.Host.register(
                new com.opendreamcore.client.spi.PlayerInfoSource() {
                    @Override
                    public String name() {
                        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                        return mc.player != null ? mc.player.getGameProfile().getName() : "玩家";
                    }

                    @Override
                    public String dimension() {
                        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                        if (mc.player == null || mc.level == null) {
                            return "overworld";
                        }
                        return mc.level.dimension().location().getNamespace() + ":" + mc.level.dimension().location().getPath();
                    }

                    @Override
                    public double x() {
                        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                        return mc.player != null ? mc.player.getX() : 0;
                    }

                    @Override
                    public double y() {
                        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                        return mc.player != null ? mc.player.getY() : 0;
                    }

                    @Override
                    public double z() {
                        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                        return mc.player != null ? mc.player.getZ() : 0;
                    }

                    @Override
                    public double yaw() {
                        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                        return mc.player != null ? mc.player.yRot : 0;
                    }

                    @Override
                    public double pitch() {
                        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                        return mc.player != null ? mc.player.xRot : 0;
                    }

                    @Override
                    public java.util.Map<String, Object> extras() {
                        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                        java.util.Map<String, Object> extras = new java.util.LinkedHashMap<>();
                        if (mc.player != null) {
                            extras.put("health", Math.round(mc.player.getHealth() * 10.0) / 10.0);
                            extras.put("hunger", mc.player.getFoodData().getFoodLevel());
                            extras.put("max_health", Math.round(mc.player.getMaxHealth() * 10.0) / 10.0);
                            extras.put("level", mc.player.experienceLevel);
                            extras.put("exp", Math.round(mc.player.experienceProgress * 100.0) / 100.0);
                            try {
                                extras.put("gamemode", mc.gameMode.getPlayerMode().getName());
                            } catch (Throwable ignore) {
                            }
                            try {
                                Object biome = mc.level.getBiome(mc.player.blockPosition());
                                net.minecraft.util.ResourceLocation key =
                                        net.minecraftforge.registries.ForgeRegistries.BIOMES.getKey(
                                                (net.minecraft.world.biome.Biome) biome);
                                if (key != null) {
                                    extras.put("biome", key.toString());
                                }
                            } catch (Throwable ignore) {
                            }
                        }
                        return extras;
                    }
                });
        // 标题口子必须交给 WindowBranding：tick() 每 tick 重写，
        // 直写一次的话原版随手覆盖就没了
        WindowBranding.register(title -> {
            try {
                Minecraft.getInstance().getWindow().setTitle(title);
            } catch (Throwable ignored) {
                // 窗口没起来时写不进去，下个 tick 会重试
            }
        }, null);
        // 标题缓存目录：1.16.5 的 gameDir 走 FMLPaths，重启未进服也显示上次标题
        WindowBranding.setCacheDir(() -> net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get());
        DISPATCHER.onWindowTitle(WindowBranding::onWindowTitle);
        // 指针屏桥：菜单页要点按钮得放真鼠标，这家用隐形 LegacyOdcScreen1165 接
        com.opendreamcore.client.render.ScreenBridge.setHost(
                new com.opendreamcore.client.render.ScreenBridge.Host() {
                    @Override
                    public boolean open(String pageId) {
                        return LegacyOdcScreen1165.requestOpen(pageId);
                    }

                    @Override
                    public void close() {
                        LegacyOdcScreen1165.requestClose();
                    }

                    @Override
                    public boolean isOpen() {
                        return LegacyOdcScreen1165.isOpen();
                    }

                    @Override
                    public void onDismissed(String pageId) {
                        // ESC 关页：本地同步清活跃页，状态检查不会又把屏开回去
                        if (pageId != null && pageId.equals(activePageId)) {
                            activePageId = null;
                        }
                    }
                });
        DISPATCHER.onOpen(id -> {
            // 渲染链关键节点：OPEN 落地才算接上，这条日志是排查『面板不渲染』的第一站
            OdcLegacy.LOGGER.info("[ODC] 页面控制落地: activePageId = {}", id);
            activePageId = id;
            com.opendreamcore.client.LocalOdc.activePageId.set(id);
            lastRenderedPages.clear();
        });
        // 关页回调：activePageId 摘掉，世界页关了 HUD/屏幕页才回得来
        DISPATCHER.onClose(id -> {
            if (id != null && id.equals(activePageId)) {
                activePageId = null;
                com.opendreamcore.client.LocalOdc.activePageId.set("");
                lastRenderedPages.clear();
            }
        });
    }
}
