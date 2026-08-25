package com.opendreamcore.client;

import com.opendreamcore.client.methods.ClientMethodSupport;

import com.mojang.logging.LogUtils;
import com.opendreamcore.page.Element;
import com.opendreamcore.page.Page;
import com.opendreamcore.protocol.message.PageControl;
import com.opendreamcore.protocol.message.Ready;
import com.opendreamcore.protocol.message.ReadyAck;
import com.opendreamcore.protocol.message.UiEvent;
import com.opendreamcore.ui.LayoutEngine;
import com.opendreamcore.ui.RenderNode;
import com.opendreamcore.ui.UiSession;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端 UI 控制器：会话、页面开关、事件发送、版本检查。
 * 所有屏幕操作都在渲染线程（enqueueWork/execute）上做。
 * 网络发送走 UiSender（target 平台各自实现，NeoForge/Fabric 注入）。
 */
public final class ClientController {

    /** 平台网络发送：通道路径（如 "ready"）→ 完整协议消息字节。 */
    public interface UiSender {
        void send(String channelPath, byte[] bytes);
    }

    public static final Logger LOGGER = LogUtils.getLogger();

    private static final ClientController INSTANCE = new ClientController();
    /** 客户端 mod 版本：首次使用时从加载器元数据读取（Fabric/Forge/NeoForge 反射择路）。 */
    private static volatile String CLIENT_VERSION;

    private static String clientVersion() {
        String v = CLIENT_VERSION;
        if (v == null || v.isBlank()) {
            synchronized (ClientController.class) {
                v = CLIENT_VERSION;
                if (v == null || v.isBlank()) {
                    v = detectModVersion();
                    CLIENT_VERSION = v;
                }
            }
        }
        return v;
    }

    private static String detectModVersion() {
        // 方案1：Fabric
        try {
            Class<?> fl = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Object loader = fl.getMethod("getInstance").invoke(null);
            Object container = ((java.util.Optional<?>) fl.getMethod("getModContainer", String.class)
                    .invoke(loader, "opendreamcore")).orElse(null);
            if (container != null) {
                Object meta = container.getClass().getMethod("metadata").invoke(container);
                return (String) meta.getClass().getMethod("getVersion").invoke(meta);
            }
        } catch (Throwable ignored) { }
        // 方案2：Forge/NeoForge ModList
        for (String mlClass : new String[]{"net.minecraftforge.fml.ModList", "net.neoforged.fml.ModList"}) {
            try {
                Class<?> ml = Class.forName(mlClass);
                Object modList = ml.getMethod("get").invoke(null);
                Object container = ((java.util.Optional<?>) ml.getMethod("getModContainerById", String.class)
                        .invoke(modList, "opendreamcore")).orElse(null);
                if (container != null) {
                    // Forge: getModInfo().getVersion()
                    try {
                        Object info = container.getClass().getMethod("getModInfo").invoke(container);
                        return (String) info.getClass().getMethod("getVersion").invoke(info);
                    } catch (Throwable ignored2) { }
                    // NeoForge: getModInfo().getVersion() 同理但可能有差异
                }
            } catch (Throwable ignored) { }
        }
        // 方案3：从 jar manifest 读 Implementation-Version
        try {
            String ver = ClientController.class.getPackage().getImplementationVersion();
            if (ver != null && !ver.isBlank()) return ver;
        } catch (Throwable ignored) { }
        return "unknown";
    }

    /** 平台壳可显式覆盖版本号（加载器探测失败时兜底）。 */
    public static void setClientVersion(String v) {
        if (v != null && !v.isBlank()) {
            CLIENT_VERSION = v;
        }
    }

    private volatile UiSender sender;

    private final LocalPageManager localPages = new LocalPageManager();
    private final Map<String, Page> serverPages = new ConcurrentHashMap<>();

    /** 握手期密钥未到时先扣住的加密页面/同步包，ready_ack 后按序回放。 */
    private final java.util.List<com.opendreamcore.protocol.message.PageSync> pendingPageSyncs = new java.util.ArrayList<>();
    private final java.util.List<com.opendreamcore.protocol.message.HudSync> pendingHudSyncs = new java.util.ArrayList<>();
    private final java.util.List<com.opendreamcore.protocol.message.PageControl> pendingControls = new java.util.ArrayList<>();
    private final CloudSyncClient cloud = new CloudSyncClient();
    private final TooltipStore tooltips = new TooltipStore();
    private final ElementEditStore elementEdits = new ElementEditStore();
    private final Map<String, Object> globals = new ConcurrentHashMap<>();
    private volatile boolean leaseHeld;

    /** 聊天记录缓存（chat_display 组件数据源）。 */
    private final java.util.ArrayDeque<String> chatMessages = new java.util.ArrayDeque<>();

    /** 容器内容缓存（container_sync 推送，chest_slot/container 组件数据源）。 */
    private final ContainerStore containerStore = new ContainerStore();

    /** 聊天通道缓存（chat_message 推送，chat_display channel 数据源）。 */
    private final ChatStore chatStore = new ChatStore();

    /** 世界 UI 存储（boss_bar / name_tag / item_tip 推送）。 */
    private final WorldUiStore worldUi = new WorldUiStore();

    public WorldUiStore worldUi() {
        return worldUi;
    }

    /** 服务端 Boss 条同步。 */
    public void handleBossBar(com.opendreamcore.protocol.message.BossBarSync sync) {
        worldUi.handleBossBar(sync);
    }

    /** 服务端名牌同步。 */
    public void handleNameTag(com.opendreamcore.protocol.message.NameTagSync sync) {
        worldUi.handleNameTag(sync);
    }

    /** 服务端物品提示同步。 */
    public void handleItemTip(com.opendreamcore.protocol.message.ItemTipSync sync) {
        worldUi.handleItemTip(sync);
    }

    /** 世界 UI 屏幕层渲染（Boss 条 + 物品提示），HUD 与页面共用。 */
    public void renderWorldUi(net.minecraft.client.gui.GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        int sw = mc.getWindow().getGuiScaledWidth();
        // Boss 条：顶部叠放
        var bars = worldUi.bossBars();
        for (int i = 0; i < bars.size(); i++) {
            WorldUiStore.BossBar bar = bars.get(i);
            int w = 182;
            int h = 10;
            int x = (sw - w) / 2;
            int y = 4 + i * 14;
            g.fill(x, y, x + w, y + h, 0xFF000000);
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF330000);
            double fill = Math.max(0, bar.progress() / 100.0) * (w - 2);
            g.fill(x + 1, y + 1, (int) (x + 1 + fill), y + h - 1, bar.color());
            for (double sx = x + 12; sx < x + 1 + fill; sx += 11) {
                g.fill((int) sx, y + 1, (int) sx + 1, y + h - 1, 0xFF000000);
            }
            if (!bar.text().isEmpty()) {
                g.drawString(mc.font, bar.text(), x + (w - mc.font.width(bar.text())) / 2, y - 9, 0xFFFFFFFF, true);
            }
        }
        // 物品提示：屏幕中央浮窗（图标 + 名字）
        double alpha = worldUi.tipAlpha();
        if (alpha > 0) {
            String itemId = worldUi.tipItemId();
            var stack = UiRenderer.parseItemStatic(itemId);
            if (!stack.isEmpty()) {
                stack.setCount(worldUi.tipCount());
                String name = stack.getHoverName().getString();
                int nameW = mc.font.width(name);
                int boxW = 24 + nameW + 12;
                int bx = (sw - boxW) / 2;
                int by = 46;
                int a = (int) (alpha * 200);
                int bg = (a << 24) | 0x101418;
                g.fill(bx, by, bx + boxW, by + 26, bg);
                g.fill(bx, by, bx + boxW, by + 1, (a << 24) | 0x505868);
                var pose = g.pose();
                CompatRender.posePush(pose);
                CompatRender.poseTranslate(pose, bx + 4, by + 5);
                CompatRender.poseScale(pose, 1.0F, 1.0F);
                g.renderItem(stack, 0, 0);
                if (stack.getCount() > 1) {
                    g.renderItemDecorations(mc.font, stack, 0, 0);
                }
                CompatRender.posePop(pose);
                int textColor = (a << 24) | 0xFFFFFF;
                g.drawString(mc.font, name, bx + 24, by + 9, textColor, true);
            }
        }
    }

    /** 世界内名牌渲染（RenderLevelStageEvent AFTER_ENTITIES 调用）。 */
    public void renderNameTags(net.minecraft.client.Camera camera, float partialTick) {
        WorldHologram.renderNameTags(worldUi.nameTags(), camera);
    }

    public ChatStore chatStore() {
        return chatStore;
    }

    /** 服务端聊天通道消息：增删改清对应通道。 */
    public void handleChatMessage(com.opendreamcore.protocol.message.ChatMessage message) {
        chatStore.handle(message);
        LOGGER.debug("聊天通道 {} {}", message.channel(), message.action());
    }

    public ContainerStore containerStore() {
        return containerStore;
    }

    /** 当前打开页面的会话 id（无页面 null；多人模式为服务端分配的会话）。 */
    public String currentSessionId() {
        return screen == null ? null : screen.session().sessionId();
    }

    /** 服务端容器同步到达：缓存槽位数据（渲染时按会话取用，无需重建布局）。 */
    public void handleContainerSync(com.opendreamcore.protocol.message.ContainerSync sync) {
        containerStore.handleSync(sync);
        LOGGER.info("容器同步 {}（{} 个槽位）", sync.sessionId(), sync.slots().size());
    }

    // ---------- 键鼠绑定（页面 keybinds/mousebinds 选项 → ui_event KEY 上报服务端） ----------

    private final Map<String, String> keyBinds = new ConcurrentHashMap<>();
    private final Map<String, Integer> mouseBinds = new ConcurrentHashMap<>();
    private final Map<String, Boolean> mousePrev = new ConcurrentHashMap<>();
    /** 全局热键（HUD 页面 keybinds/mousebinds：常驻生效，页面外也响应；经 HUD 会话路由）。 */
    private final Map<String, String> globalKeyBinds = new ConcurrentHashMap<>();
    private final Map<String, Integer> globalMouseBinds = new ConcurrentHashMap<>();
    private final Map<String, Boolean> globalMousePrev = new ConcurrentHashMap<>();

    /** 应用页面键鼠绑定（打开页面时调用）。 */
    public void applyBindings(Map<String, Object> options) {
        keyBinds.clear();
        mouseBinds.clear();
        mousePrev.clear();
        if (options == null) {
            return;
        }
        Object keys = options.get("keybinds");
        if (keys instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                keyBinds.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
            }
        }
        Object mouse = options.get("mousebinds");
        if (mouse instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                Object button = e.getValue();
                int b = button instanceof Number n ? n.intValue() : 0;
                mouseBinds.put(String.valueOf(e.getKey()), b);
            }
        }
        if (!keyBinds.isEmpty() || !mouseBinds.isEmpty()) {
            LOGGER.info("页面键鼠绑定 {} 键 / {} 鼠标", keyBinds.size(), mouseBinds.size());
        }
    }

    /** 应用全局热键（HUD 页面挂载时调用；常驻生效，页面外也响应）。 */
    public void applyGlobalBindings(Map<String, Object> options) {
        globalKeyBinds.clear();
        globalMouseBinds.clear();
        globalMousePrev.clear();
        if (options == null) {
            return;
        }
        Object keys = options.get("keybinds");
        if (keys instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                globalKeyBinds.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
            }
        }
        Object mouse = options.get("mousebinds");
        if (mouse instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                Object button = e.getValue();
                int b = button instanceof Number n ? n.intValue() : 0;
                globalMouseBinds.put(String.valueOf(e.getKey()), b);
            }
        }
        if (!globalKeyBinds.isEmpty() || !globalMouseBinds.isEmpty()) {
            LOGGER.info("全局热键 {} 键 / {} 鼠标", globalKeyBinds.size(), globalMouseBinds.size());
        }
    }

    public void clearBindings() {
        keyBinds.clear();
        mouseBinds.clear();
        mousePrev.clear();
    }

    public void clearGlobalBindings() {
        globalKeyBinds.clear();
        globalMouseBinds.clear();
        globalMousePrev.clear();
    }

    /** 每 tick 检查绑定（边沿触发，一次按压只上报一次）。全局热键常驻；页面绑定仅页面打开时。 */
    public void tickBindings() {
        tickScriptTasks();
        tickAnimateValues();
        tickHudClicks();
        tickHudHover();
        tickHudEditKeys();
        // 全局热键（HUD 页面声明，页面外也响应；经 HUD 会话路由）
        if (hudSession != null) {
            for (Map.Entry<String, String> e : globalKeyBinds.entrySet()) {
                var mapping = ClientMethodSupport.keyMapping(e.getValue());
                if (mapping != null && mapping.consumeClick()) {
                    sendKeyEvent(hudSession, "key:" + e.getKey(), "keybind:" + e.getKey());
                }
            }
            var handler = Minecraft.getInstance().mouseHandler;
            for (Map.Entry<String, Integer> e : globalMouseBinds.entrySet()) {
                boolean down = switch (e.getValue()) {
                    case 1 -> handler.isRightPressed();
                    case 2 -> handler.isMiddlePressed();
                    default -> handler.isLeftPressed();
                };
                Boolean prev = globalMousePrev.get(e.getKey());
                if (Boolean.TRUE.equals(down) && !Boolean.TRUE.equals(prev)) {
                    sendKeyEvent(hudSession, "mouse:" + e.getKey() + ":" + e.getValue(),
                            "mousebind:" + e.getKey());
                }
                globalMousePrev.put(e.getKey(), down);
            }
        }
        if (screen == null) {
            return;
        }
        for (Map.Entry<String, String> e : keyBinds.entrySet()) {
            var mapping = ClientMethodSupport.keyMapping(e.getValue());
            if (mapping != null && mapping.consumeClick()) {
                sendKeyEvent("key:" + e.getKey(), "keybind:" + e.getKey());
            }
        }
        var handler = Minecraft.getInstance().mouseHandler;
        for (Map.Entry<String, Integer> e : mouseBinds.entrySet()) {
            boolean down = switch (e.getValue()) {
                case 1 -> handler.isRightPressed();
                case 2 -> handler.isMiddlePressed();
                default -> handler.isLeftPressed();
            };
            Boolean prev = mousePrev.get(e.getKey());
            if (Boolean.TRUE.equals(down) && !Boolean.TRUE.equals(prev)) {
                sendKeyEvent("mouse:" + e.getKey() + ":" + e.getValue(), "mousebind:" + e.getKey());
            }
            mousePrev.put(e.getKey(), down);
        }
    }

    /** HUD 左键按下边沿（tickHudClicks 用）。 */
    private boolean hudMousePrev;

    /** HUD 编辑模式（/odc edithud on）：显示元素边框，拖动改位置。 */
    private boolean hudEditMode;
    /** HUD 编辑模式下选中的元素 id。 */
    private String hudEditSelectedId;
    /** HUD 编辑模式下正在拖动的元素 id。 */
    private String hudEditDragId;
    /** HUD 编辑模式拖动起点：鼠标 GUI 坐标 + 元素原始位置（相对增量拖动，修复按住跳到鼠标处）。 */
    private double hudEditDragStartMx;
    private double hudEditDragStartMy;
    private double hudEditDragOriginX;
    private double hudEditDragOriginY;
    /** HUD 编辑模式下面板系统。 */
    private EditorPanels hudEditorPanels;
    /** HUD 编辑面板宿主（键盘轮询操作入口）。 */
    private HudEditorHost hudEditorHost;
    /** HUD 编辑键盘边沿状态。 */
    private boolean hudEditEscPrev;
    private boolean hudEditDelPrev;
    private boolean hudEditZPrev;
    private boolean hudEditYPrev;
    private boolean hudEditCPrev;
    private boolean hudEditVPrev;
    private boolean hudEditTabPrev;

    /**
     * HUD 点击交互：无任何屏幕打开时左键按下边沿 → 命中 HUD 元素 → click 事件
     * （多人上报 HUD 会话；单机本地执行 click actions）。原版/ODC 屏幕打开时不拦截。
     */
    private void tickHudClicks() {
        var mc = Minecraft.getInstance();
        if (hudNodes == null || hudPage == null || mc.player == null) {
            return;
        }
        var mouse = mc.mouseHandler;
        boolean left = mouse.isLeftPressed();
        if (left && !hudMousePrev && mc.screen == null) {
            double mx = mouse.xpos();
            double my = mouse.ypos();
            // HUD 编辑模式：面板优先（调色板/树/检查器区域点击不落到元素），再命中元素
            if (hudEditMode) {
                if (hudEditorPanels != null && hudEditorPanels.mouseClicked(mx, my, 0)) {
                    hudMousePrev = true;
                    return;
                }
                RenderNode hit = null;
                for (int i = hudNodes.size() - 1; i >= 0 && hit == null; i--) {
                    hit = hudNodes.get(i).hitTest(mx, my);
                }
                hudEditSelectedId = hit == null ? null : hit.id();
                if (hit != null) {
                    hudEditDragId = hit.id();
                    // 记录拖动起点：相对增量拖动（元素跟随鼠标位移，而不是跳到鼠标坐标）
                    hudEditDragStartMx = mx;
                    hudEditDragStartMy = my;
                    hudEditDragOriginX = hit.x();
                    hudEditDragOriginY = hit.y();
                }
                hudMousePrev = left;
                return;
            }
            RenderNode hit = null;
            for (int i = hudNodes.size() - 1; i >= 0 && hit == null; i--) {
                hit = hudNodes.get(i).hitTest(mx, my);
            }
            if (hit != null && hit.source() != null && hit.enabled()) {
                if (hudSession != null && isServerMode()) {
                    sendEvent(hudSession.event(hit.id(), UiEvent.Trigger.CLICK, null));
                } else {
                    String script = hit.source().actions().get("click");
                    if (script != null && !script.isBlank()) {
                        runLocalAction(hudPage, script, null);
                    }
                }
            }
        }
        // HUD 编辑模式拖动（相对增量：起点元素位置 + 鼠标位移）
        if (hudEditMode && hudEditDragId != null && left) {
            double mx = mouse.xpos();
            double my = mouse.ypos();
            String pid = hudPage.id() == null ? "hud" : hudPage.id();
            elementEdits.set(pid, hudEditDragId,
                    hudEditDragOriginX + (mx - hudEditDragStartMx),
                    hudEditDragOriginY + (my - hudEditDragStartMy));
            refreshHud();
        }
        if (!left) {
            hudEditDragId = null;
        }
        hudMousePrev = left;
    }

    /** HUD 悬停目标（变更时上报；屏幕打开时清空）。 */
    private String hudHoverId;
    /** HUD 悬停目标变更时间戳（tooltip 延迟显示用）。 */
    private long hudHoverSince;

    /**
     * HUD hover 事件：无屏幕打开时按 tick 命中 HUD 元素，目标变更才上报
     * （多人上报 HUD 会话 HOVER；单机本地执行 hover actions；移出/清空不上报）。
     */
    private void tickHudHover() {
        var mc = Minecraft.getInstance();
        if (hudNodes == null || hudPage == null || mc.player == null) {
            hudHoverId = null;
            return;
        }
        if (mc.screen != null) {
            hudHoverId = null;
            return;
        }
        var mouse = mc.mouseHandler;
        double mx = mouse.xpos();
        double my = mouse.ypos();
        RenderNode hit = null;
        for (int i = hudNodes.size() - 1; i >= 0 && hit == null; i--) {
            hit = hudNodes.get(i).hitTest(mx, my);
        }
        String id = hit != null && hit.source() != null && hit.enabled() ? hit.id() : null;
        if (java.util.Objects.equals(id, hudHoverId)) {
            return;
        }
        hudHoverId = id;
        hudHoverSince = System.currentTimeMillis();
        if (id != null) {
            if (hudSession != null && isServerMode()) {
                sendEvent(hudSession.event(id, UiEvent.Trigger.HOVER, null));
            } else {
                String script = hit.source().actions().get("hover");
                if (script != null && !script.isBlank()) {
                    runLocalAction(hudPage, script, null);
                }
            }
        }
    }

    /** 发送 KEY 触发事件（页面会话）。 */
    private void sendKeyEvent(String data, String elementId) {
        sendKeyEvent(session, data, elementId);
    }

    /** 发送 KEY 触发事件（指定会话：页面或 HUD 全局）。 */
    private void sendKeyEvent(UiSession s, String data, String elementId) {
        if (s == null || !isServerMode()) {
            return;
        }
        UiEvent event = s.event(elementId, UiEvent.Trigger.KEY, data);
        sendEvent(event);
    }

    /** 关闭通知（C→S）：告知服务端清理会话/容器绑定。 */
    public void sendPageClose(String sessionId) {
        if (sessionId == null || !isServerMode()) {
            return;
        }
        var buf = new com.opendreamcore.protocol.OdcByteArrayBuf();
        new com.opendreamcore.protocol.message.PageClose(sessionId).encode(buf);
        sendRaw(com.opendreamcore.protocol.Protocol.PAGE_CLOSE, buf.toByteArray());
    }

    // 屏幕特效：震动 / 闪屏
    private long shakeUntil;
    private double shakeStrength;
    private long flashUntil;
    private int flashColor;

    /** 屏幕震动（Screen.屏幕震动）。 */
    public void shake(double strength, int durationMs) {
        shakeStrength = strength;
        shakeUntil = System.currentTimeMillis() + durationMs;
    }

    /** 屏幕闪屏（Screen.闪屏）。 */
    public void flash(int colorArgb, int durationMs) {
        flashColor = colorArgb;
        flashUntil = System.currentTimeMillis() + durationMs;
    }

    /** 当前屏幕震动偏移（无则 null）。 */
    public double[] shakeOffset() {
        long now = System.currentTimeMillis();
        if (now >= shakeUntil) {
            return null;
        }
        double t = (shakeUntil - now) / 1000.0;
        double amp = shakeStrength * Math.min(1, t);
        return new double[]{Math.sin(now / 40.0) * amp, Math.cos(now / 53.0) * amp};
    }

    /** 当前闪屏颜色（无则 0）。 */
    public int flashColor() {
        return System.currentTimeMillis() < flashUntil ? flashColor : 0;
    }

    // 屏幕过渡（淡入淡出遮罩）
    private long transitionStart;
    private long transitionUntil;
    private int transitionColor;

    /** 过渡效果（Screen.过渡 / 服务端 UiEffect TRANSITION）。 */
    public void transition(int colorArgb, int durationMs) {
        transitionColor = colorArgb;
        transitionStart = System.currentTimeMillis();
        transitionUntil = transitionStart + durationMs;
    }

    /** 过渡进度 {p 0..1}（无则 null）；alpha = sin(p*π) 淡入淡出。 */
    public double[] transitionProgress() {
        long now = System.currentTimeMillis();
        if (now >= transitionUntil) {
            return null;
        }
        double p = (now - transitionStart) / (double) Math.max(1, transitionUntil - transitionStart);
        return new double[]{Math.max(0, Math.min(1, p)), transitionColor};
    }

    /** 服务端屏幕特效指令（ui_effect）：震动/闪屏/过渡。 */
    public void applyEffect(com.opendreamcore.protocol.message.UiEffect effect) {
        switch (effect.kind()) {
            case SHAKE -> shake(effect.arg1(), (int) effect.arg2());
            case FLASH -> flash(UiStyle.color(effect.color(), 0xFFFFFFFF), (int) effect.arg1());
            case TRANSITION -> transition(UiStyle.color(effect.color(), 0xFF000000), (int) effect.arg1());
        }
        LOGGER.info("屏幕特效 {}（渲染线程）", effect.kind());
    }

    /** 追加一条聊天消息（客户端收到聊天时调用）。 */
    public void addChatMessage(String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        synchronized (chatMessages) {
            chatMessages.addLast(message);
            while (chatMessages.size() > 200) {
                chatMessages.removeFirst();
            }
        }
    }

    /** 取最近 n 条聊天（新→旧）。 */
    public java.util.List<String> latestChat(int n) {
        synchronized (chatMessages) {
            java.util.List<String> out = new java.util.ArrayList<>(chatMessages);
            java.util.Collections.reverse(out);
            return out.size() > n ? out.subList(0, n) : out;
        }
    }

    private UiSession session;
    private OdcScreen screen;
    private String serverVersion;
    private int serverProtocol;

    // HUD 常驻页面（display: hud 或 match: hud）
    private Page hudPage;
    private List<RenderNode> hudNodes;
    private UiSession hudSession;

    // 世界全息页面（display: world 或 match: world）
    /** 世界面板列表（多面板同屏：每页一面板，独立锚点/页签/悬停/会话）。 */
    final java.util.List<WorldPanel> worldPanels = new java.util.concurrent.CopyOnWriteArrayList<>();
    /** 聚焦面板别名（worldPage/worldNodes/worldSession 始终指向聚焦面板，交互代码零改动）。 */
    Page worldPage;
    List<RenderNode> worldNodes;
    UiSession worldSession;

    /** 世界面板（一个页面一个 3D 面板；可多面板同时挂载）。 */
    static final class WorldPanel {
        Page page;
        List<RenderNode> nodes;
        UiSession session;
        String hoverId;
        String pendingHoverId;
        long tabSwitchAt;
        /** 生效锚点（每帧更新：相对跟随 / 绝对 anchor / 固定 follow:false / 平滑插值）。 */
        net.minecraft.world.phys.Vec3 anchor;
        /** follow: false 时打开瞬间的锚点（此后固定）。 */
        net.minecraft.world.phys.Vec3 pinnedAnchor;

        WorldPanel(Page page, List<RenderNode> nodes, UiSession session) {
            this.page = page;
            this.nodes = nodes;
            this.session = session;
        }
    }

    /** 面板级状态键：pageId + "/" + elementId（跨面板元素 id 隔离）。 */
    public static String wkey(String pageId, String elementId) {
        return pageId + "/" + elementId;
    }

    /** 焦点环：Tab 聚焦控件统一白色描边（原版按钮无焦点描边；EditBox 自带边框跳过）。各编辑屏 render 末尾调用。 */
    public static void renderFocusRing(net.minecraft.client.gui.GuiGraphics g,
                                       net.minecraft.client.gui.screens.Screen screen) {
        for (var child : screen.children()) {
            if (child instanceof net.minecraft.client.gui.components.EditBox) {
                continue;
            }
            if (child instanceof net.minecraft.client.gui.components.AbstractWidget w
                    && w.isFocused() && w.visible) {
                int fx0 = w.getX() - 1, fy0 = w.getY() - 1;
                int fx1 = w.getX() + w.getWidth() + 1, fy1 = w.getY() + w.getHeight() + 1;
                g.fill(fx0, fy0, fx1, fy0 + 1, 0xFFFFFFFF);
                g.fill(fx0, fy1 - 1, fx1, fy1, 0xFFFFFFFF);
                g.fill(fx0, fy0, fx0 + 1, fy1, 0xFFFFFFFF);
                g.fill(fx1 - 1, fy0, fx1, fy1, 0xFFFFFFFF);
                break;
            }
        }
    }

    /** 屏幕取色（GUI 坐标 → 当前帧像素 → #RRGGBB；世界场景取色用）。 */
    public static String sampleWorldHex(double guiX, double guiY) {
        var mc = Minecraft.getInstance();
        var window = mc.getWindow();
        double scale = window.getScreenWidth() / (double) window.getGuiScaledWidth();
        int px = (int) (guiX * scale);
        int py = (int) (guiY * scale);
        var target = mc.getMainRenderTarget();
        try {
            java.nio.ByteBuffer buf = java.nio.ByteBuffer.allocateDirect(4)
                    .order(java.nio.ByteOrder.nativeOrder());
            if (!CompatRender.targetBindRead(target)) {
                return null;
            }
            org.lwjgl.opengl.GL11.glReadPixels(px, target.viewHeight - py - 1, 1, 1,
                    org.lwjgl.opengl.GL11.GL_RGBA, org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE, buf);
            CompatRender.targetUnbindRead(target);
            int r = buf.get(0) & 0xFF;
            int g = buf.get(1) & 0xFF;
            int b = buf.get(2) & 0xFF;
            return String.format(java.util.Locale.ROOT, "#%02X%02X%02X", r, g, b);
        } catch (Exception e) {
            return null;
        }
    }
    private long lastWorldTickAt;
    /** 服务端悬停上报节流：快速扫过多个元素时每 50ms 至多 1 包（永远发最新的）。 */
    static final long HOVER_THROTTLE_MS = 50;
    String pendingHoverId;
    long lastHoverSentAt;
    final Map<String, double[]> worldDragOffsets = new ConcurrentHashMap<>();
    /** 编辑模式剪贴板（Ctrl+C 复制 / Ctrl+V 粘贴）与按键边沿状态。 */
    final java.util.List<Element> worldClipboard = new java.util.ArrayList<>();
    boolean worldEditCtrlCPrev;
    boolean worldEditCtrlVPrev;
    /** 编辑模式多选集合（Ctrl+点击切换；批量删除/对齐/分布用）。 */
    final java.util.Set<String> worldEditMulti = java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** 框选预览集（拖框时框内元素实时高亮；松手提交/取消后清空）。 */
    final java.util.Set<String> worldMarqueePreview = java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** 属性编辑屏高亮目标（编辑屏打开时世界侧选中框常亮，关闭即清）。 */
    private final java.util.Set<String> worldEditHighlight = java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** 镜像翻转 ghost 预览轴集合（对齐屏悬停镜像按钮驱动：x/y 可同时预览双轴）。 */
    private final java.util.Set<String> worldMirrorPreviewAxes = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** 对齐参考包围盒预览开关（悬停 left/right/hcenter/top/bottom/vcenter 时高亮可见包围盒）。 */
    public void setWorldAlignBoundsPreview(double[] boundsOrNull) {
        WorldEditor.get().worldAlignBoundsPreview = boundsOrNull;
    }

    /** 分布 ghost 预览开关（对齐屏悬停 dist_x/dist_y 按钮驱动；轴 null = 清除）。 */
    public void setWorldDistributeGhost(String axisOrNull, String elementId) {        WorldEditor.get().worldDistributeGhost = null;
        if (axisOrNull == null || worldPage == null || elementId == null) {
            return;
        }
        List<String> members = null;
        String group = worldGroupOf(elementId);
        if (group != null && worldGroupMembers(group).size() > 1) {
            members = worldGroupMembers(group);
        } else if (worldEditMulti.size() >= 2) {
            members = new java.util.ArrayList<>(worldEditMulti);
        }
        if (members == null || members.size() < 2) {
            return;
        }
        double[] bounds = WorldHologram.visibleBounds(worldNodes,
                worldTabActive(worldPage.id()), worldPage.variables());
        if (bounds == null) {
            return;
        }
        var vars = worldPage.variables();
        boolean horizontal = "x".equals(axisOrNull);
        java.util.List<Object[]> sorted = new java.util.ArrayList<>();
        for (String memberId : members) {
            var el = findElement(worldPage, memberId);
            if (el == null) {
                continue;
            }
            Object raw = el.props().get("hologram");
            if (!(raw instanceof Map<?, ?> holo)) {
                continue;
            }
            String type = String.valueOf(el.props().get("type"));
            double size = WorldHologram.holoNum(holo, horizontal ? "width" : "height",
                    "text".equals(type) ? (horizontal ? 2.0 : 0.25) : 1.0, vars);
            double center = WorldHologram.holoNum(holo, horizontal ? "x" : "y", 0, vars);
            double ox = WorldHologram.holoNum(holo, "x", 0, vars);
            double oy = WorldHologram.holoNum(holo, "y", 0, vars);
            double w = WorldHologram.holoNum(holo, "width", "text".equals(type) ? 2.0 : 1.0, vars);
            double h = WorldHologram.holoNum(holo, "height", "text".equals(type) ? 0.25 : 1.0, vars);
            sorted.add(new Object[]{center, size, ox, oy, w, h});
        }
        if (sorted.size() < 2) {
            return;
        }
        sorted.sort(java.util.Comparator.comparingDouble(o -> (double) o[0]));
        double lo = horizontal ? bounds[0] : bounds[1];
        double hi = horizontal ? bounds[2] : bounds[3];
        double totalSize = 0;
        for (Object[] o : sorted) {
            totalSize += (double) o[1];
        }
        double gap = (hi - lo - totalSize) / (sorted.size() - 1);
        double cursor = lo;
        java.util.List<double[]> boxes = new java.util.ArrayList<>();
        for (Object[] o : sorted) {
            double center = cursor + (double) o[1] / 2;
            double cx = horizontal ? center : (double) o[2];
            double cy = horizontal ? (double) o[3] : center;
            boxes.add(new double[]{cx, cy, (double) o[4], (double) o[5]});
            cursor += (double) o[1] + gap;
        }
        WorldEditor.get().worldDistributeGhost = boxes;
    }

    /** 统一尺寸 ghost 预览开关（对齐屏悬停 size_w/size_h 按钮驱动；轴 null = 清除；目标 = 成员最大尺寸）。 */
    public void setWorldSizeGhost(String axisOrNull, String elementId) {
        WorldEditor.get().worldDistributeGhost = null;
        if (axisOrNull == null || worldPage == null || elementId == null) {
            return;
        }
        List<String> members = null;
        String group = worldGroupOf(elementId);
        if (group != null && worldGroupMembers(group).size() > 1) {
            members = worldGroupMembers(group);
        } else if (worldEditMulti.size() >= 2) {
            members = new java.util.ArrayList<>(worldEditMulti);
        }
        if (members == null || members.size() < 2) {
            return;
        }
        var vars = worldPage.variables();
        boolean width = "w".equals(axisOrNull);
        double target = 0;
        for (String memberId : members) {
            var el = findElement(worldPage, memberId);
            if (el == null) {
                continue;
            }
            Object raw = el.props().get("hologram");
            if (!(raw instanceof Map<?, ?> holo)) {
                continue;
            }
            String type = String.valueOf(el.props().get("type"));
            double size = WorldHologram.holoNum(holo, width ? "width" : "height",
                    "text".equals(type) ? (width ? 2.0 : 0.25) : 1.0, vars);
            target = Math.max(target, size);
        }
        if (target <= 0) {
            return;
        }
        java.util.List<double[]> boxes = new java.util.ArrayList<>();
        for (String memberId : members) {
            var el = findElement(worldPage, memberId);
            if (el == null) {
                continue;
            }
            Object raw = el.props().get("hologram");
            if (!(raw instanceof Map<?, ?> holo)) {
                continue;
            }
            String type = String.valueOf(el.props().get("type"));
            double ox = WorldHologram.holoNum(holo, "x", 0, vars);
            double oy = WorldHologram.holoNum(holo, "y", 0, vars);
            double ow = WorldHologram.holoNum(holo, "width", "text".equals(type) ? 2.0 : 1.0, vars);
            double oh = WorldHologram.holoNum(holo, "height", "text".equals(type) ? 0.25 : 1.0, vars);
            boxes.add(new double[]{ox, oy, width ? target : ow, width ? oh : target});
        }
        WorldEditor.get().worldDistributeGhost = boxes;
    }

    /** 镜像预览开关（对齐屏悬停镜像按钮驱动：axis x/y，on=false 清除该轴）。 */
    public void setWorldMirrorPreview(String axis, boolean on) {
        if (on) {
            worldMirrorPreviewAxes.add(axis);
        } else {
            worldMirrorPreviewAxes.remove(axis);
        }
    }

    /** 跨面板参考预览开关（对齐屏跨面模式悬停模式按钮驱动）。 */
    public void setWorldCrossPreview(boolean on) {
        WorldEditor.get().worldCrossPreview = null;
        WorldEditor.get().worldCrossAnchorPreview = null;
        if (!on || !worldCrossAlignAvailable() || worldPage == null) {
            return;
        }
        WorldPanel other = findWorldPanel(WorldEditor.get().worldLastPanelPid);
        WorldPanel panelA = findWorldPanel(worldPage.id() == null ? "world" : worldPage.id());
        if (other == null || other.anchor == null || panelA == null || panelA.anchor == null) {
            return;
        }
        WorldEditor.get().worldCrossAnchorPreview = new double[]{
                other.anchor.x - panelA.anchor.x,
                other.anchor.y - panelA.anchor.y,
                other.anchor.z - panelA.anchor.z};
        String otherId = WorldEditor.get().worldPanelSelections.get(WorldEditor.get().worldLastPanelPid);
        var elB = findElement(other.page, otherId);
        if (elB == null) {
            return;
        }
        Map<?, ?> hB = elB.props().get("hologram") instanceof Map<?, ?> h ? h : Map.of();
        String tB = String.valueOf(elB.props().get("type"));
        var varsB = other.page.variables();
        double bx = WorldHologram.holoNum(hB, "x", 0, varsB);
        double by = WorldHologram.holoNum(hB, "y", 0, varsB);
        double bw = WorldHologram.holoNum(hB, "width", "text".equals(tB) ? 2.0 : 1.0, varsB);
        double bh = WorldHologram.holoNum(hB, "height", "text".equals(tB) ? 0.25 : 1.0, varsB);
        WorldEditor.get().worldCrossPreview = new double[]{
                bx + other.anchor.x - panelA.anchor.x,
                by + other.anchor.y - panelA.anchor.y,
                bw, bh};
    }
    final Map<String, double[]> worldPanelMoveOrig = new ConcurrentHashMap<>();

    /** 点击弹跳缩放系数（1 = 无；0.88 谷值，300ms 正弦回弹；渲染线程每帧读取，按页隔离）。 */
    public double worldClickBounceScale(String pageId, String elementId) {
        Long start = WorldEditor.get().worldClickBounces.get(wkey(pageId, elementId));
        if (start == null) {
            return 1;
        }
        double p = (System.currentTimeMillis() - start) / 300.0;
        if (p >= 1) {
            WorldEditor.get().worldClickBounces.remove(wkey(pageId, elementId));
            return 1;
        }
        return 1 - 0.12 * Math.sin(p * Math.PI);
    }

    /** 元素可见性覆盖（无覆盖返回默认 true；按页隔离）。会话级覆盖优先；否则读 hologram.hidden（持久隐藏）。 */
    public boolean worldElementVisible(String pageId, String elementId) {
        Boolean[] state = WorldEditor.get().worldElementStates.get(wkey(pageId, elementId));
        if (state != null && state[0] != null) {
            return state[0];
        }
        WorldPanel panel = findWorldPanel(pageId);
        Page page = panel != null ? panel.page : worldPage;
        if (page != null && elementId != null) {
            var el = findElement(page, elementId);
            if (el != null) {
                Object raw = el.props().get("hologram");
                if (raw instanceof Map<?, ?> h && Boolean.parseBoolean(String.valueOf(h.get("hidden")))) {
                    return false;
                }
            }
        }
        return true;
    }

    /** 元素可用性覆盖（无覆盖返回默认 true；按页隔离）。 */
    public boolean worldElementEnabled(String pageId, String elementId) {
        Boolean[] state = WorldEditor.get().worldElementStates.get(wkey(pageId, elementId));
        return state == null || state[1] == null || state[1];
    }

    /** 多选集合是否包含元素（渲染选中框用；仅聚焦面板有效）。框选拖拽预览集与属性编辑高亮集同样高亮；组选中 = 整组高亮。 */
    public boolean worldElementMultiSelected(String pageId, String elementId) {
        if (worldPage == null || !java.util.Objects.equals(worldPage.id(), pageId)) {
            return false;
        }
        if (worldEditMulti.contains(elementId) || worldMarqueePreview.contains(elementId)
                || worldEditHighlight.contains(elementId)) {
            return true;
        }
        // 组选中高亮：选中元素所在组（>1 成员）的其它成员也画选中框（自身走单选框，避免双框）
        if (WorldEditor.get().worldEditSelected == null || WorldEditor.get().worldEditSelected.equals(elementId)) {
            return false;
        }
        String grp = worldGroupOf(WorldEditor.get().worldEditSelected);
        if (grp == null || worldGroupMembers(grp).size() <= 1) {
            return false;
        }
        return grp.equals(worldGroupOf(elementId));
    }

    /** 透视预览透明度：按住 H 时选中元素（多选 = 整组，组选中 = 整组）返回 0.35（渲染乘算淡出，露出下层），否则 1.0。 */
    public double worldElementGhostFade(String pageId, String elementId) {
        if (!WorldEditor.get().worldGhostOn || worldPage == null || !java.util.Objects.equals(worldPage.id(), pageId)) {
            return 1.0;
        }
        if (worldEditMulti.size() >= 2) {
            return worldEditMulti.contains(elementId) ? 0.35 : 1.0;
        }
        // 组选中透视：选中元素所在组（>1 成员）整组淡化
        if (WorldEditor.get().worldEditSelected != null && !WorldEditor.get().worldEditSelected.equals(elementId)) {
            String grp = worldGroupOf(WorldEditor.get().worldEditSelected);
            if (grp != null && worldGroupMembers(grp).size() > 1
                    && grp.equals(worldGroupOf(elementId))) {
                return 0.35;
            }
        }
        if (WorldEditor.get().worldEditSelected != null && WorldEditor.get().worldEditSelected.equals(elementId)) {
            return 0.35;
        }
        return 1.0;
    }

    /** 属性编辑屏高亮目标（世界侧选中框常亮，编辑哪个亮哪个）。 */
    public void setWorldEditHighlight(java.util.List<String> ids) {
        worldEditHighlight.clear();
        if (ids != null) {
            worldEditHighlight.addAll(ids);
        }
        WorldEditor.get().worldEditLabel = "属性编辑";
    }

    /** 当前编辑属性路径（世界侧浮签显示；快捷屏覆盖为具体路径）。 */
    public void setWorldEditLabel(String label) {
        WorldEditor.get().worldEditLabel = label;
    }

    public void clearWorldEditHighlight() {
        worldEditHighlight.clear();
        WorldEditor.get().worldEditLabel = null;
    }

    /** 元素是否正在拖拽（拖拽倾斜反馈用；按页隔离）。 */
    public boolean worldElementDragging(String pageId, String elementId) {
        return worldDragOffsets.containsKey(wkey(pageId, elementId));
    }

    /** 服务端世界元素状态同步（Screen.设置/广播元素可见/可用）。 */
    public void handleWorldElementState(com.opendreamcore.protocol.message.WorldElementState state) {
        if (findWorldPanel(state.pageId() == null ? "world" : state.pageId()) == null) {
            return; // 页面未打开时不记录
        }
        String key = wkey(state.pageId() == null ? "world" : state.pageId(), state.elementId());
        Boolean[] entry = WorldEditor.get().worldElementStates.computeIfAbsent(key, k -> new Boolean[2]);
        if (state.mode() == com.opendreamcore.protocol.message.WorldElementState.MODE_VISIBLE) {
            entry[0] = state.value();
        } else {
            entry[1] = state.value();
        }
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§a[OpenDreamCore] §f元素状态: " + state.elementId() + " "
                        + (state.mode() == 0 ? "可见" : "可用") + " = " + state.value()), false);
        LOGGER.info("服务端世界元素状态 {} -> {} {} = {}", state.pageId(), state.elementId(),
                state.mode() == 0 ? "visible" : "enabled", state.value());
    }

    /** 世界布局保存回执（服务端烘焙结果；baked > 0 = 成功）。 */
    public void handleWorldSaveAck(com.opendreamcore.protocol.message.WorldSaveAck ack) {
        if (ack == null || Minecraft.getInstance().player == null) {
            return;
        }
        if (ack.baked() > 0) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§a[OpenDreamCore] §f世界面板已写入 " + ack.pageId()
                            + "（" + ack.baked() + " 项/键）"), false);
        } else {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§c[OpenDreamCore] §f世界面板保存失败: "
                            + (ack.message() == null || ack.message().isEmpty() ? "无租约或文件不可写" : ack.message())),
                    false);
        }
    }

    // 世界开关状态（toggle 元素点击切换）
    final Map<String, Boolean> worldToggle = new ConcurrentHashMap<>();
    // 世界滑块状态（slider 元素拖拽改值）
    final Map<String, Double> worldSlider = new ConcurrentHashMap<>();
    // 世界下拉状态（dropdown 元素点击轮换选项）
    final Map<String, Integer> worldDropdown = new ConcurrentHashMap<>();
    // 世界页签激活状态（页面 id → 页签名；tabs 元素点击切换，重开页面回到定义值）
    final Map<String, String> worldTab = new ConcurrentHashMap<>();

    // 世界面板 WYSIWYG 编辑模式（/odc edit world：租约 + 世界页打开时启用）
    private String leasePageId;
    final Map<String, Double> worldZScrubBase = new java.util.LinkedHashMap<>();
    final Map<String, Double> worldOpacityScrubBase = new java.util.LinkedHashMap<>();
    /** 描边色板（BORDER_PALETTE 各色 + 关闭格；渲染于描边手柄旁，点击改色）。 */
    final java.util.List<int[]> worldBorderPaletteRects = new java.util.ArrayList<>();
    final java.util.List<int[]> worldCtxRects = new java.util.ArrayList<>();
    /** 流光动画时钟（K 暂停：时钟冻结，流光定格便于对齐观察）。 */
    long worldFlowClock;
    private long worldFlowLastTick;
    boolean worldFlowPaused;

    /** 流光时钟推进（渲染入口每帧调用；暂停时冻结）。 */
    public void tickWorldFlowClock() {
        long now = System.currentTimeMillis();
        if (worldFlowLastTick == 0) {
            worldFlowLastTick = now;
            return;
        }
        if (!worldFlowPaused) {
            worldFlowClock += now - worldFlowLastTick;
        }
        worldFlowLastTick = now;
    }

    /** 流光动画时间基（渲染读取；暂停 = 冻结值）。 */
    public long worldFlowTime() {
        return worldFlowClock;
    }

    /** 最近使用的背景色（会话内最多 2 条；色板尾部动态格）。 */
    final java.util.List<String> worldRecentBg = new java.util.ArrayList<>();

    /** 最近背景色列表（色板尾部格；不足时调用方用默认色补齐）。 */
    public java.util.List<String> worldRecentBackgrounds() {
        return new java.util.ArrayList<>(worldRecentBg);
    }

    /** 待写入的页面标题（对齐屏改标题；null = 未修改）。 */
    public String worldPageTitle() {
        return WorldEditor.get().worldEditPageTitle;
    }

    /** 设置待写入页面标题（保存时随 EDITOR_WORLD 写回 YAML 顶层 title；空输入 = 取消修改）。 */
    public void setWorldPageTitle(String t) {
        if (!WorldEditor.get().worldEditMode || worldPage == null) {
            return;
        }
        String v = t == null ? "" : t.trim().replace("\r", " ").replace("\n", " ");
        if (v.length() > 64) {
            v = v.substring(0, 64);
        }
        if (v.isEmpty()) {
            WorldEditor.get().worldEditPageTitle = null;
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§e[OpenDreamCore] §f已取消标题修改"), false);
            return;
        }
        WorldEditor.get().worldEditPageTitle = v;
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§a[OpenDreamCore] §f页面标题待写入: " + v + "（保存后生效）"), false);
    }

    /** 当前页面变量（运行时值；变量屏显示用）。 */
    public java.util.Map<String, Object> worldVars() {
        return worldPage == null ? java.util.Map.of() : worldPage.variables();
    }

    /** 设置页面变量（对齐屏变量▽；值按 YAML 标量解析；保存后写回页面文件）。 */
    public void applyWorldVar(String key, String value) {
        if (!WorldEditor.get().worldEditMode || worldPage == null) {
            return;
        }
        String k = key == null ? "" : key.trim();
        if (k.isEmpty() || k.length() > 64 || k.indexOf(':') >= 0) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§c[OpenDreamCore] §f变量名需 1~64 字符且不含冒号"), false);
            return;
        }
        String v = value == null ? "" : value.trim();
        worldPage.variables().put(k, WorldBackgroundEditor.parseYamlValue(v));
        WorldEditor.get().worldEditVars.put(k, v);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§e[OpenDreamCore] §f变量 " + k + " = " + v + "（保存后写回页面文件）"), false);
    }

    /** 删除页面变量（变量屏 Shift+点击；保存后写回页面文件）。 */
    public void removeWorldVar(String key) {
        if (!WorldEditor.get().worldEditMode || worldPage == null) {
            return;
        }
        String k = key == null ? "" : key.trim();
        if (k.isEmpty() || (!worldPage.variables().containsKey(k) && !WorldEditor.get().worldEditVars.containsKey(k))) {
            return;
        }
        worldPage.variables().remove(k);
        WorldEditor.get().worldEditVars.put(k, "__unset__");
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§e[OpenDreamCore] §f变量 " + k + " 已删除（保存后写回页面文件）"), false);
    }

    /** 面板背景色快速切换（对齐屏色板；null = 移除背景；运行时生效，YAML 写回持久化）。 */

    /** 面板透明度快捷档（world.alpha 0~1 乘算全部元素；运行时生效）。 */
    public void setWorldPanelAlpha(float alpha) {
        setWorldPanelAlpha(alpha, false);
    }

    /** 面板透明度快捷档（quiet = 拖拽微调静默）。 */
    public void setWorldPanelAlpha(float alpha, boolean quiet) {
        if (worldPage == null) {
            return;
        }
        pushWorldBackgroundUndo("背景: 透明度", "bg:alpha");
        Map<String, Object> options = worldPage.options();
        Map<String, Object> world = new java.util.LinkedHashMap<String, Object>();
        if (options.get("world") instanceof Map<?, ?> w) {
            w.forEach((k, v) -> world.put(String.valueOf(k), v));
        }
        world.put("alpha", Math.round(alpha * 100) / 100.0F);
        options.put("world", world);
        if (!quiet) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§b[OpenDreamCore] §f面板透明度: " + Math.round(alpha * 100)
                            + "%（运行时生效；页面 world 段持久化需在 YAML 写回）"), false);
        }
    }

    /** 当前面板透明度（world.alpha，缺省 1.0）。 */
    public double worldPanelAlpha() {
        if (worldPage == null) {
            return 1.0;
        }
        Object worldObj = worldPage.options().get("world");
        if (worldObj instanceof Map<?, ?> w && w.get("alpha") instanceof Number n) {
            return n.doubleValue();
        }
        return 1.0;
    }

    /** 收藏色板文件（OpenDreamCore/UI/_bg_palette.json，hex 数组）。 */

    /** 收藏色板（hex 列表；文件缺失/损坏 = 空）。 */

    /** 收藏当前色到自定义色板（Shift+点击主行色板格；去重，上限 16）。 */

    /** 从自定义色板移除（Shift+点击收藏格）。 */

    /** 导出收藏色板 JSON（["#RRGGBB",...]）到剪贴板。 */

    /** 从剪贴板导入收藏色板（hex 数组；合并去重，上限 16）。 */

    /** 悬停高亮色循环（world.hoverColor：亮蓝→金→青→白→红→绿→默认；渲染/交互共用；可撤消）。 */
    public void cycleWorldHoverColor() {
        if (worldPage == null) {
            return;
        }
        String[] colors = {"#7A8BFF", "#FFB300", "#4FC3F7", "#FFFFFF", "#E57373", "#66BB6A"};
        Map<String, Object> options = worldPage.options();
        Map<String, Object> world = new java.util.LinkedHashMap<String, Object>();
        if (options.get("world") instanceof Map<?, ?> w) {
            w.forEach((k, v) -> world.put(String.valueOf(k), v));
        }
        Object cur = world.get("hoverColor");
        String curS = cur == null ? "" : String.valueOf(cur).toUpperCase(java.util.Locale.ROOT);
        String next = null;
        if (cur == null) {
            next = colors[0];
        } else {
            for (String c : colors) {
                if (curS.equals(c.toUpperCase(java.util.Locale.ROOT))) {
                    next = colors[(java.util.Arrays.asList(colors).indexOf(c) + 1) % colors.length];
                    break;
                }
            }
        }
        pushWorldBackgroundUndo("面板: 悬停色", "bg:hovercolor");
        if (next == null) {
            world.remove("hoverColor"); // 回默认亮蓝
        } else {
            world.put("hoverColor", next);
        }
        options.put("world", world);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§b[OpenDreamCore] §f悬停高亮色: "
                        + (next == null ? "默认亮蓝（移除）" : next)
                        + "（渲染/交互共用；可 Ctrl+Z 撤）"), false);
    }

    /** 背景色透明度循环（background.color 或纯色背景的 AARRGGBB 前缀 FF→CC→99→66→33→FF；可撤消）。 */

    /** 面板背景渐变是否水平（gradientDir: horizontal；缺省上下）。 */

    /** 渐变方向循环（上下 ⇄ 左右；background map 增删 gradientDir；保留其它；运行时生效）。 */

    /** 渐变主副色互换（background map 交换 color ↔ gradient；保留其它；运行时生效）。 */

    /** 渐变中段色（无 = null）。 */

    /** 渐变中段色循环（Ctrl+点击方向按钮：无 → 深 #0D1B2A → 浅 #3A4A66；background map 增删 gradientMid；运行时生效）。 */

    /** 渐变中段位置档（Ctrl+Shift+点击方向按钮：0.3 / 0.5 / 0.7 循环；background map 增删 gradientMidPos；运行时生效）。 */

    /** 渐变预设库（Alt+点击方向按钮：深蓝/暗金/赛博青/暖橙 循环；写 color+gradient，清中段色；运行时生效）。 */

    /** 背景明暗微调（Alt+Shift+点击 = +10% 亮；Ctrl+Alt+点击 = -10% 暗；background color/bg.color RGB 缩放；运行时生效）。 */
    public void nudgeWorldPanelBrightness(boolean brighter) {
        if (worldPage == null) {
            return;
        }
        Map<String, Object> options = worldPage.options();
        Map<String, Object> world = new java.util.LinkedHashMap<String, Object>();
        if (options.get("world") instanceof Map<?, ?> w) {
            w.forEach((k, v) -> world.put(String.valueOf(k), v));
        }
        Object bgObj = world.get("background");
        Map<String, Object> bg;
        if (bgObj instanceof Map<?, ?> bm) {
            bg = new java.util.LinkedHashMap<String, Object>();
            bm.forEach((k, v) -> bg.put(String.valueOf(k), v));
        } else if (bgObj != null) {
            bg = new java.util.LinkedHashMap<String, Object>();
            bg.put("color", String.valueOf(bgObj));
        } else {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§c[OpenDreamCore] §f面板无背景，无法调明暗（先设背景色）"), false);
            return;
        }
        Object colorObj = bg.get("color");
        String hex = colorObj == null ? "" : String.valueOf(colorObj).trim();
        if (!hex.matches("#[0-9a-fA-F]{6,8}")) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§c[OpenDreamCore] §f背景色格式需 #RRGGBB 或 #AARRGGBB"), false);
            return;
        }
        pushWorldBackgroundUndo("背景: 明暗", "bg:brightness");
        double f = brighter ? 1.1 : 1 / 1.1;
        String body = hex.substring(1);
        boolean alpha8 = body.length() == 8;
        String rgbPart = alpha8 ? body.substring(2) : body;
        int r = (int) Math.min(255, Math.max(0, Math.round(Integer.parseInt(rgbPart.substring(0, 2), 16) * f)));
        int g = (int) Math.min(255, Math.max(0, Math.round(Integer.parseInt(rgbPart.substring(2, 4), 16) * f)));
        int b = (int) Math.min(255, Math.max(0, Math.round(Integer.parseInt(rgbPart.substring(4, 6), 16) * f)));
        String next = String.format(java.util.Locale.ROOT, "#%02X%02X%02X", r, g, b);
        if (alpha8) {
            next = hex.substring(0, 3) + next.substring(1); // 保留 alpha 前缀 #AA
        }
        bg.put("color", next);
        world.put("background", bg);
        options.put("world", world);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§b[OpenDreamCore] §f背景明暗: " + (brighter ? "+10%" : "-10%")
                        + " → " + next + "（运行时生效；页面 world 段持久化需在 YAML 写回）"), false);
    }

    /** 当前背景色（#RRGGBB 规范化；无背景 = null；色板白圈高亮用）。 */

    /** 渐变双色摘要（"#AABBCC→#DDEEFF"；无渐变/无副色 = null）。 */

    /** 当前面板背景 YAML 片段（world.background 键值行；无背景 = null）。 */

    /** 从剪贴板解析背景 YAML 片段并应用（world.background 替换；支持复制导出的格式）。 */

    /** YAML 标量解析（引号去壳 / 布尔 / 数字 / 字符串）。 */

    /** 随机背景配色（Alt+双击方向按钮触发；随机色相/饱和/明度生成 color+gradient，清中段色；运行时生效）。 */

    /** HSL → RGB（0xRRGGBB）。 */

    /** 边框辉光循环（Shift+点击 边框按钮：无 → 蓝 #7A8BFF → 金 #FFB300 → 青 #4FC3F7；background map 增删 borderGlow；运行时生效）。 */
    public void cycleWorldPanelBorderGlow() {
        if (worldPage == null) {
            return;
        }
        Map<String, Object> options = worldPage.options();
        Map<String, Object> world = new java.util.LinkedHashMap<String, Object>();
        if (options.get("world") instanceof Map<?, ?> w) {
            w.forEach((k, v) -> world.put(String.valueOf(k), v));
        }
        Object bgObj = world.get("background");
        Map<String, Object> bg;
        if (bgObj instanceof Map<?, ?> bm) {
            bg = new java.util.LinkedHashMap<String, Object>();
            bm.forEach((k, v) -> bg.put(String.valueOf(k), v));
        } else if (bgObj != null) {
            bg = new java.util.LinkedHashMap<String, Object>();
            bg.put("color", String.valueOf(bgObj));
        } else {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§c[OpenDreamCore] §f面板无背景，无法设辉光（先设背景色）"), false);
            return;
        }
        pushWorldBackgroundUndo("背景: 边框辉光", "bg:glow");
        Object cur = bg.get("borderGlow");
        String next;
        if (cur == null) {
            next = "#7A8BFF";
        } else if ("#7A8BFF".equalsIgnoreCase(String.valueOf(cur))) {
            next = "#FFB300";
        } else if ("#FFB300".equalsIgnoreCase(String.valueOf(cur))) {
            next = "#4FC3F7";
        } else {
            next = null;
        }
        if (next == null) {
            bg.remove("borderGlow");
        } else {
            bg.put("borderGlow", next);
        }
        world.put("background", bg);
        options.put("world", world);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§b[OpenDreamCore] §f边框辉光: "
                        + (next == null ? "无" : next) + "（运行时生效；页面 world 段持久化需在 YAML 写回）"), false);
    }

    /** 辉光强度循环（Shift+双击 边框按钮：0.03 / 0.06 / 0.12；background map 增删 borderGlowSize；运行时生效）。 */
    public void cycleWorldPanelBorderGlowSize() {
        if (worldPage == null) {
            return;
        }
        Map<String, Object> options = worldPage.options();
        Map<String, Object> world = new java.util.LinkedHashMap<String, Object>();
        if (options.get("world") instanceof Map<?, ?> w) {
            w.forEach((k, v) -> world.put(String.valueOf(k), v));
        }
        Object bgObj = world.get("background");
        Map<String, Object> bg;
        if (bgObj instanceof Map<?, ?> bm) {
            bg = new java.util.LinkedHashMap<String, Object>();
            bm.forEach((k, v) -> bg.put(String.valueOf(k), v));
        } else if (bgObj != null) {
            bg = new java.util.LinkedHashMap<String, Object>();
            bg.put("color", String.valueOf(bgObj));
        } else {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§c[OpenDreamCore] §f面板无背景，无法设辉光强度（先设背景色）"), false);
            return;
        }
        pushWorldBackgroundUndo("背景: 辉光强度", "bg:glowsize");
        double cur = bg.get("borderGlowSize") instanceof Number n ? n.doubleValue() : 0.06;
        double next = cur < 0.05 ? 0.06 : cur < 0.1 ? 0.12 : 0.03;
        bg.put("borderGlowSize", Math.round(next * 100) / 100.0);
        world.put("background", bg);
        options.put("world", world);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§b[OpenDreamCore] §f辉光强度: " + next
                        + "（运行时生效；页面 world 段持久化需在 YAML 写回）"), false);
    }

    /** 边框色循环（Ctrl+点击 边框按钮：蓝灰→金→青→白→红→绿；background map 写 border；运行时生效）。 */
    public void cycleWorldPanelBorderColor() {
        if (worldPage == null) {
            return;
        }
        String[] colors = {"#3A4A66", "#FFB300", "#4FC3F7", "#FFFFFF", "#E57373", "#66BB6A"};
        Map<String, Object> options = worldPage.options();
        Map<String, Object> world = new java.util.LinkedHashMap<String, Object>();
        if (options.get("world") instanceof Map<?, ?> w) {
            w.forEach((k, v) -> world.put(String.valueOf(k), v));
        }
        Object bgObj = world.get("background");
        Map<String, Object> bg;
        if (bgObj instanceof Map<?, ?> bm) {
            bg = new java.util.LinkedHashMap<String, Object>();
            bm.forEach((k, v) -> bg.put(String.valueOf(k), v));
        } else if (bgObj != null) {
            bg = new java.util.LinkedHashMap<String, Object>();
            bg.put("color", String.valueOf(bgObj));
        } else {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§c[OpenDreamCore] §f面板无背景，无法设边框色（先设背景色）"), false);
            return;
        }
        pushWorldBackgroundUndo("背景: 边框色", "bg:bordercolor");
        String cur = bg.get("border") == null
                ? "" : String.valueOf(bg.get("border")).toUpperCase(java.util.Locale.ROOT);
        String next = colors[0];
        for (String c : colors) {
            if (cur.equals(c.toUpperCase(java.util.Locale.ROOT))) {
                next = colors[(java.util.Arrays.asList(colors).indexOf(c) + 1) % colors.length];
                break;
            }
        }
        bg.put("border", next);
        world.put("background", bg);
        options.put("world", world);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§b[OpenDreamCore] §f边框色: " + next
                        + "（运行时生效；页面 world 段持久化需在 YAML 写回）"), false);
    }

    /** 当前面板背景 JSON 片段（键值对象；无背景 = null）。 */

    /** 背景预设文件（OpenDreamCore/UI/_bg_presets.json，数组）。 */
    static java.nio.file.Path bgPresetFile() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("OpenDreamCore").resolve("UI").resolve("_bg_presets.json");
    }

    /** 背景 map → JSON 片段。 */
    static String bgMapToJson(Map<String, Object> bg) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (java.util.Map.Entry<String, Object> e : bg.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(e.getKey()).append("\":");
            Object v = e.getValue();
            if (v instanceof Number || v instanceof Boolean) {
                sb.append(v);
            } else {
                sb.append('"').append(String.valueOf(v)).append('"');
            }
        }
        sb.append('}');
        return sb.toString();
    }

    /** 保存当前背景到预设文件（Alt+Ctrl+S；追加数组尾，自动时间戳名）。 */

    /** 载入背景预设（Alt+Ctrl+L 循环：按保存顺序逐条应用，越界回到首条）。 */

    /** 重命名当前预设条目（Alt+Ctrl+R 后 hex 输入回车提交；name 字段写入文件）。 */

    /** 简易 JSON 对象解析（{"k":"v","n":1.5,"b":true}；失败返回空 map）。 */
    static java.util.Map<String, Object> parseBgJsonObject(String s) {
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        String t = s.trim();
        if (!t.startsWith("{") || !t.endsWith("}")) {
            return out;
        }
        String inner = t.substring(1, t.length() - 1);
        for (String part : inner.split(",")) {
            int colon = part.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String k = part.substring(0, colon).trim().replace("\"", "");
            String v = part.substring(colon + 1).trim();
            if (v.startsWith("\"") && v.endsWith("\"")) {
                out.put(k, v.substring(1, v.length() - 1));
            } else if ("true".equals(v) || "false".equals(v)) {
                out.put(k, Boolean.parseBoolean(v));
            } else if (v.matches("-?\\d+(\\.\\d+)?")) {
                out.put(k, v.contains(".") ? Double.parseDouble(v) : (long) Long.parseLong(v));
            } else {
                out.put(k, v);
            }
        }
        return out;
    }

    /** 删除当前载入的预设条目（Alt+Shift+Ctrl+L；删当前游标前一条，即最近载入的）。 */

    /** 设置背景指定键的颜色（色条 hex 编辑用；background map 写 key；运行时生效）。 */

    /** 随机背景指定键颜色（色条点击；HSL 随机；color/gradient 键）。 */

    /** 单键明暗微调（色条 Alt+左键 = +10%；Ctrl+Alt+左键 = -10%；background key RGB 缩放；运行时生效）。 */

    /** 该键与背景主色互换（色条双击；color ↔ key；运行时生效）。 */

    /** 追加指定背景 JSON 为预设（快照转预设；自动时间戳名）。 */

    /** 面板背景是否带渐变（对齐屏渐变开关状态）。 */

    /** 面板背景渐变快捷开关（background map 增删 gradient 键：顶色 → 底渐变；保留颜色；运行时生效）。 */

    /** 面板背景当前圆角（world 单位；无背景或未设 = 0）。 */
    public double worldPanelRadius() {
        if (worldPage == null) {
            return 0;
        }
        Object worldObj = worldPage.options().get("world");
        if (!(worldObj instanceof Map<?, ?> w)) {
            return 0;
        }
        Object bg = w.get("background");
        if (bg instanceof Map<?, ?> bm && bm.get("radius") instanceof Number n) {
            return n.doubleValue();
        }
        return 0;
    }

    /** 面板背景当前 padding（world 单位；缺省 0.25）。 */
    public double worldPanelPadding() {
        if (worldPage == null) {
            return 0.25;
        }
        Object worldObj = worldPage.options().get("world");
        if (!(worldObj instanceof Map<?, ?> w)) {
            return 0.25;
        }
        Object bg = w.get("background");
        if (bg instanceof Map<?, ?> bm && bm.get("padding") instanceof Number n) {
            return n.doubleValue();
        }
        return 0.25;
    }

    /** 面板当前淡出带宽度（world.fadeRange 米；缺省 3）。 */
    public double worldPanelFadeRange() {
        if (worldPage == null) {
            return 3;
        }
        Object worldObj = worldPage.options().get("world");
        if (!(worldObj instanceof Map<?, ?> w)) {
            return 3;
        }
        if (w.get("fadeRange") instanceof Number n) {
            return n.doubleValue();
        }
        return 3;
    }

    /** 淡出带宽度循环档（陡 1 / 中 3 / 缓 6 米，循环；运行时生效）。 */
    public void cycleWorldPanelFadeRange() {
        if (worldPage == null) {
            return;
        }
        double[] presets = {1, 3, 6};
        double cur = worldPanelFadeRange();
        double next = presets[0];
        for (double p : presets) {
            if (cur < p - 0.01) {
                next = p;
                break;
            }
        }
        pushWorldBackgroundUndo("面板: 淡出带", "bg:fade");
        Map<String, Object> options = worldPage.options();
        Map<String, Object> world = new java.util.LinkedHashMap<String, Object>();
        if (options.get("world") instanceof Map<?, ?> w) {
            w.forEach((k, v) -> world.put(String.valueOf(k), v));
        }
        world.put("fadeRange", next);
        options.put("world", world);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§b[OpenDreamCore] §f面板淡出带: " + next
                        + " 米（运行时生效；页面 world 段持久化需在 YAML 写回）"), false);
    }

    /** 面板背景当前边框宽度（world 单位；缺省 0.02）。 */
    public double worldPanelBorderWidth() {
        if (worldPage == null) {
            return 0.02;
        }
        Object worldObj = worldPage.options().get("world");
        if (!(worldObj instanceof Map<?, ?> w)) {
            return 0.02;
        }
        Object bg = w.get("background");
        if (bg instanceof Map<?, ?> bm && bm.get("borderWidth") instanceof Number n) {
            return n.doubleValue();
        }
        return 0.02;
    }

    /** 面板当前淡出距离（world.fadeDistance 米；0 = 关）。 */
    public double worldPanelFadeDistance() {
        if (worldPage == null) {
            return 0;
        }
        Object worldObj = worldPage.options().get("world");
        if (!(worldObj instanceof Map<?, ?> w)) {
            return 0;
        }
        if (w.get("fadeDistance") instanceof Number n) {
            return n.doubleValue();
        }
        return 0;
    }

    /** 面板淡出距离档（world.fadeDistance 米，0 = 移除关闭；运行时生效）。 */
    public void setWorldPanelFadeDistance(double d) {
        setWorldPanelFadeDistance(d, false);
    }

    /** 面板淡出距离档（quiet = 拖拽微调静默，不刷消息）。 */
    public void setWorldPanelFadeDistance(double d, boolean quiet) {
        if (worldPage == null) {
            return;
        }
        pushWorldBackgroundUndo("面板: 淡出距离", "bg:fade");
        Map<String, Object> options = worldPage.options();
        Map<String, Object> world = new java.util.LinkedHashMap<String, Object>();
        if (options.get("world") instanceof Map<?, ?> w) {
            w.forEach((k, v) -> world.put(String.valueOf(k), v));
        }
        if (d <= 0) {
            world.remove("fadeDistance");
        } else {
            world.put("fadeDistance", Math.round(d * 10) / 10.0);
        }
        options.put("world", world);
        if (!quiet) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§b[OpenDreamCore] §f面板淡出距离: "
                            + (d <= 0 ? "关（移除）" : d + " 米")
                            + "（运行时生效；页面 world 段持久化需在 YAML 写回）"), false);
        }
    }

    /** 面板背景边框宽度档（background map 增删 borderWidth 键，0 = 移除回默认 0.02；保留其它；运行时生效）。 */
    public void setWorldPanelBorderWidth(double t) {
        setWorldPanelBorderWidth(t, false);
    }

    /** 面板背景边框宽度档（quiet = 拖拽微调静默）。 */
    public void setWorldPanelBorderWidth(double t, boolean quiet) {
        if (worldPage == null) {
            return;
        }
        Map<String, Object> options = worldPage.options();
        Map<String, Object> world = new java.util.LinkedHashMap<String, Object>();
        if (options.get("world") instanceof Map<?, ?> w) {
            w.forEach((k, v) -> world.put(String.valueOf(k), v));
        }
        Object bgObj = world.get("background");
        Map<String, Object> bg;
        if (bgObj instanceof Map<?, ?> bm) {
            bg = new java.util.LinkedHashMap<String, Object>();
            bm.forEach((k, v) -> bg.put(String.valueOf(k), v));
        } else if (bgObj != null) {
            bg = new java.util.LinkedHashMap<String, Object>();
            bg.put("color", String.valueOf(bgObj));
        } else {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§c[OpenDreamCore] §f面板无背景，无法设边框宽度（先设背景色）"), false);
            return;
        }
        pushWorldBackgroundUndo("背景: 边框宽度", "bg:border");
        if (t <= 0) {
            bg.remove("borderWidth");
        } else {
            bg.put("borderWidth", Math.round(t * 1000) / 1000.0);
        }
        world.put("background", bg);
        options.put("world", world);
        if (!quiet) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§b[OpenDreamCore] §f面板背景边框宽度: "
                            + (t <= 0 ? "默认 0.02（移除）" : t)
                            + "（运行时生效；页面 world 段持久化需在 YAML 写回）"), false);
        }
    }

    /** 面板背景 padding 快捷档（background map 增删 padding 键，0 = 移除回默认；保留颜色/渐变/边框/圆角；运行时生效）。 */
    public void setWorldPanelPadding(double p) {
        setWorldPanelPadding(p, false);
    }

    /** 面板背景 padding 快捷档（quiet = 拖拽微调静默）。 */
    public void setWorldPanelPadding(double p, boolean quiet) {
        if (worldPage == null) {
            return;
        }
        Map<String, Object> options = worldPage.options();
        Map<String, Object> world = new java.util.LinkedHashMap<String, Object>();
        if (options.get("world") instanceof Map<?, ?> w) {
            w.forEach((k, v) -> world.put(String.valueOf(k), v));
        }
        Object bgObj = world.get("background");
        Map<String, Object> bg;
        if (bgObj instanceof Map<?, ?> bm) {
            bg = new java.util.LinkedHashMap<String, Object>();
            bm.forEach((k, v) -> bg.put(String.valueOf(k), v));
        } else if (bgObj != null) {
            bg = new java.util.LinkedHashMap<String, Object>();
            bg.put("color", String.valueOf(bgObj));
        } else {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§c[OpenDreamCore] §f面板无背景，无法设 padding（先设背景色）"), false);
            return;
        }
        pushWorldBackgroundUndo("背景: padding", "bg:padding");
        if (p <= 0) {
            bg.remove("padding");
        } else {
            bg.put("padding", Math.round(p * 100) / 100.0);
        }
        world.put("background", bg);
        options.put("world", world);
        if (!quiet) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§b[OpenDreamCore] §f面板背景 padding: " + (p <= 0 ? "默认 0.25（移除）" : p)
                            + "（运行时生效；页面 world 段持久化需在 YAML 写回）"), false);
        }
    }

    /** 面板背景圆角快捷档（background map 增删 radius 键，0 = 移除；保留颜色/渐变/边框；运行时生效）。 */
    public void setWorldPanelRadius(double r) {
        setWorldPanelRadius(r, false);
    }

    /** 面板背景圆角快捷档（quiet = 拖拽微调静默）。 */
    public void setWorldPanelRadius(double r, boolean quiet) {
        if (worldPage == null) {
            return;
        }
        Map<String, Object> options = worldPage.options();
        Map<String, Object> world = new java.util.LinkedHashMap<String, Object>();
        if (options.get("world") instanceof Map<?, ?> w) {
            w.forEach((k, v) -> world.put(String.valueOf(k), v));
        }
        Object bgObj = world.get("background");
        Map<String, Object> bg;
        if (bgObj instanceof Map<?, ?> bm) {
            bg = new java.util.LinkedHashMap<String, Object>();
            bm.forEach((k, v) -> bg.put(String.valueOf(k), v));
        } else if (bgObj != null) {
            bg = new java.util.LinkedHashMap<String, Object>();
            bg.put("color", String.valueOf(bgObj));
        } else {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§c[OpenDreamCore] §f面板无背景，无法设圆角（先设背景色）"), false);
            return;
        }
        pushWorldBackgroundUndo("背景: 圆角", "bg:radius");
        if (r <= 0) {
            bg.remove("radius");
        } else {
            bg.put("radius", Math.round(r * 100) / 100.0);
        }
        world.put("background", bg);
        options.put("world", world);
        if (!quiet) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§b[OpenDreamCore] §f面板背景圆角: " + (r <= 0 ? "直角（移除）" : r)
                            + "（运行时生效；页面 world 段持久化需在 YAML 写回）"), false);
        }
    }

    /** 面板背景是否带边框（对齐屏边框开关状态）。 */
    public boolean hasWorldPanelBorder() {
        if (worldPage == null) {
            return false;
        }
        Object worldObj = worldPage.options().get("world");
        if (!(worldObj instanceof Map<?, ?> w)) {
            return false;
        }
        Object bg = w.get("background");
        return bg instanceof Map<?, ?> bm && bm.get("border") != null;
    }

    /** 面板背景边框快捷开关（background map 增删 border 键；保留颜色；运行时生效）。 */
    public void toggleWorldPanelBorder() {
        if (worldPage == null) {
            return;
        }
        Map<String, Object> options = worldPage.options();
        Map<String, Object> world = new java.util.LinkedHashMap<String, Object>();
        if (options.get("world") instanceof Map<?, ?> w) {
            w.forEach((k, v) -> world.put(String.valueOf(k), v));
        }
        Object bgObj = world.get("background");
        Map<String, Object> bg;
        if (bgObj instanceof Map<?, ?> bm) {
            bg = new java.util.LinkedHashMap<String, Object>();
            bm.forEach((k, v) -> bg.put(String.valueOf(k), v));
        } else if (bgObj != null) {
            bg = new java.util.LinkedHashMap<String, Object>();
            bg.put("color", String.valueOf(bgObj));
        } else {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§c[OpenDreamCore] §f面板无背景，无法开关边框（先设背景色）"), false);
            return;
        }
        boolean on = bg.get("border") == null;
        if (on) {
            bg.put("border", "#3A4A66");
        } else {
            bg.remove("border");
        }
        world.put("background", bg);
        options.put("world", world);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§b[OpenDreamCore] §f面板边框: " + (on ? "开" : "关")
                        + "（运行时生效；页面 world 段持久化需在 YAML 写回）"), false);
    }

    /** 锚点偏移复位：移除 offsetX/Y/Z → 默认（0, 1.6, 3）；立即重建锚点。 */
    void resetWorldAnchor() {
        if (worldPage == null) {
            return;
        }
        pushWorldBackgroundUndo("锚点: 偏移复位", "anchor:reset");
        Map<String, Object> options = worldPage.options();
        Map<String, Object> world = new java.util.LinkedHashMap<String, Object>();
        if (options.get("world") instanceof Map<?, ?> w) {
            w.forEach((k, v) -> world.put(String.valueOf(k), v));
        }
        world.remove("offsetX");
        world.remove("offsetY");
        world.remove("offsetZ");
        options.put("world", world);
        updateWorldPanelAnchors(Minecraft.getInstance().gameRenderer.getMainCamera());
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§b[OpenDreamCore] §f锚点偏移已复位（默认 0, 1.6, 3）"), false);
    }

    /** M 键是否按下（锚点微移修饰键）。 */
    static boolean mKeyHeld(Minecraft mc) {
        long win = mc.getWindow().getWindow();
        return org.lwjgl.glfw.GLFW.glfwGetKey(win, org.lwjgl.glfw.GLFW.GLFW_KEY_M) == 1;
    }

    /** 当前锚点微调步进。 */
    double worldAnchorStep() {
        return switch (WorldEditor.get().worldAnchorStepIdx) {
            case 1 -> 0.01;
            case 2 -> 0.001;
            default -> 0.1;
        };
    }

    /** 锚点微移：world.offsetX/Y/Z ± 步进（两种锚点模式均生效；Shift+↑↓ = z；立即重建锚点）。 */

    /** 锚点偏移数值解析（数字字符串/表达式；表达式求值失败回退）。 */
    double parseAnchorNum(Object v, double fallback) {
        String s = String.valueOf(v).trim();
        if (s.matches("-?\\d+(\\.\\d+)?")) {
            return Double.parseDouble(s);
        }
        try {
            com.opendreamcore.script.Scope scope = new com.opendreamcore.script.Scope();
            if (worldPage != null) {
                worldPage.variables().forEach(scope::assignVar);
            }
            Object r = com.opendreamcore.script.DreamLang.evaluate(s, scope);
            if (r instanceof Number n) {
                return n.doubleValue();
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    /** 锚点跟随模式循环（聚焦面板）：跟随 → 固定（follow:false）→ 平滑跟随（smooth:0.5）→ 跟随。 */
    void cycleWorldAnchorMode() {
        if (worldPage == null) {
            return;
        }
        pushWorldBackgroundUndo("锚点: 模式", "anchor:mode");
        Map<String, Object> options = worldPage.options();
        Object worldObj = options.get("world");
        Map<String, Object> world = new java.util.LinkedHashMap<String, Object>();
        if (worldObj instanceof Map<?, ?> w) {
            w.forEach((k, v) -> world.put(String.valueOf(k), v));
        }
        boolean follow = world.get("follow") == null
                || Boolean.parseBoolean(String.valueOf(world.get("follow")));
        boolean smooth = world.get("smooth") instanceof Number n && n.doubleValue() > 0;
        String mode;
        if (follow && !smooth) {
            world.put("follow", false);
            world.remove("smooth");
            mode = "固定（不跟随玩家）";
        } else if (!follow) {
            world.put("follow", true);
            world.put("smooth", 0.5);
            mode = "平滑跟随（0.5）";
        } else {
            world.put("follow", true);
            world.remove("smooth");
            mode = "跟随";
        }
        options.put("world", world);
        updateWorldPanelAnchors(Minecraft.getInstance().gameRenderer.getMainCamera()); // 立即重建锚点
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§b[OpenDreamCore] §f锚点模式: " + mode
                        + "（P 循环；页面 world 段保存需在 YAML 中写回）"), false);
    }

    /** 面板背景是否隐藏（按住 U 时 true，渲染层跳过 background 绘制）。 */
    public boolean worldBackgroundHidden() {
        return WorldEditor.get().worldHideBackground;
    }

    // 子页栈（SUB_OPEN 压栈，SUB_CLOSE 弹栈）
    private final java.util.ArrayDeque<OdcScreen> screenStack = new java.util.ArrayDeque<>();

    // 拖拽位置记忆（页面 id → 偏移）
    private final Map<String, double[]> rememberedPositions = new ConcurrentHashMap<>();

    private long loginTime;

    private ClientController() {
    }

    public static ClientController get() {
        return INSTANCE;
    }

    /** 平台网络层注入（入口类在 setup 时调用）。 */
    public void setSender(UiSender sender) {
        this.sender = sender;
        // 统一管线绑定（平台差异仅在注入器 SPI 内）；预载延至首帧（入口期窗口可能未就绪）
        com.opendreamcore.client.resource.PackRegistry.bindInjector(
                () -> com.opendreamcore.client.spi.ResourcePackInjector.current());
    }

    /**
     * 连服时把 /odc 子命令转发给服务端执行；单人世界返回 false 走本地命令。
     * 反射适配两代命令包：1.21.2+ 单参构造 / 1.20.x 全参构造。
     * 全壳共用本入口——各 target 不再自持转发实现（一个链路铁律）。
     */
    public boolean tryForwardOdcCommand(String subCommand) {
        var conn = Minecraft.getInstance().getConnection();
        if (conn == null) {
            return false;
        }
        String cmd = subCommand == null || subCommand.isEmpty() ? "odc" : "odc " + subCommand;
        try {
            // 新版：ServerboundChatCommandPacket(String)
            var pkt = Class.forName("net.minecraft.network.protocol.game.ServerboundChatCommandPacket")
                    .getConstructor(String.class)
                    .newInstance(cmd);
            conn.send((net.minecraft.network.protocol.Packet<?>) pkt);
            return true;
        } catch (NoSuchMethodException legacy) {
            try {
                var cls = Class.forName("net.minecraft.network.protocol.game.ServerboundChatCommandPacket");
                var pkt = cls.getConstructor(String.class, java.time.Instant.class, long.class,
                                Class.forName("net.minecraft.commands.arguments.ArgumentSignatures"),
                                Class.forName("net.minecraft.network.chat.LastSeenMessages$Update"))
                        .newInstance(cmd, java.time.Instant.now(), 0L,
                                Class.forName("net.minecraft.commands.arguments.ArgumentSignatures")
                                        .getField("EMPTY").get(null),
                                Class.forName("net.minecraft.network.chat.LastSeenMessages$Update")
                                        .getConstructor(int.class, java.util.BitSet.class)
                                        .newInstance(0, new java.util.BitSet()));
                conn.send((net.minecraft.network.protocol.Packet<?>) pkt);
                return true;
            } catch (Throwable t) {
                LOGGER.warn("命令转发失败: {}", t.toString());
                return false;
            }
        } catch (Throwable t) {
            LOGGER.warn("命令转发失败: {}", t.toString());
            return false;
        }
    }

    /** 托管材质包目录创建+扫描注入；入口期传加载器 gameDir 立即执行，未传则首帧兜底。 */
    public synchronized void ensureManagedPacks(java.nio.file.Path gameDir) {
        if (managedPacksDone) {
            return;
        }
        managedPacksDone = true;
        try {
            java.nio.file.Path dir = gameDir != null ? gameDir
                    : Minecraft.getInstance().gameDirectory.toPath();
            int n = com.opendreamcore.client.packs.LocalPackPreload.preload(dir);
            if (n > 0) {
                LOGGER.info("本地材质包预置完成：{} 个", n);
            }
        } catch (Throwable t) {
            LOGGER.warn("本地材质包预置失败: {}", t.toString());
        }
    }

    /** 发送协议消息（target 网络层转发到对应通道）。 */
    public void sendRaw(String channelPath, byte[] bytes) {
        UiSender s = sender;
        if (s != null) {
            s.send(channelPath, bytes);
        }
    }

    // ---------- 自定义双向通道（custom_packet：Network.发送 / 订阅 / 取消订阅） ----------

    /** 上行：客户端 → 服务端自定义通道（无连接时丢弃）。 */
    public boolean sendCustomPacket(String channel, String payload) {
        if (channel == null || channel.isBlank()) {
            return false;
        }
        try {
            var buf = new com.opendreamcore.protocol.OdcByteArrayBuf();
            new com.opendreamcore.protocol.message.CustomPacket(channel, payload).encode(buf);
            sendRaw(com.opendreamcore.protocol.Protocol.CUSTOM_PACKET, buf.toByteArray());
            return true;
        } catch (Exception e) {
            LOGGER.warn("custom_packet 发送失败: {}", e.toString());
            return false;
        }
    }

    /** 下行分发：服务端 → 客户端 → 对应通道订阅者（EventBus "custom:<通道>"）。 */
    public void handleCustomPacket(String channel, String payload) {
        // D3 保留通道：odc/pack → 服务端下发的材质包安装指令
        if ("odc/pack".equals(channel)) {
            com.opendreamcore.packs.PackInstaller.installFromPayload(payload);
            return;
        }
        try {
            com.opendreamcore.script.EventBus.publish("custom:" + channel, payload);
        } catch (Exception e) {
            LOGGER.warn("custom_packet 分发失败: {}", e.toString());
        }
    }

    // ---------- 页面 ----------

    /** 打开页面（本地或服务端下发）。 */
    public void open(Page page) {
        open(page, null);
    }

    /** 打开页面；serverSessionId 非空表示服务端分配的会话（多人模式）。 */
    public void open(Page page, String serverSessionId) {
        if (page == null) {
            return;
        }
        // display: world 的服务端/本地页面 → 世界 3D 面板（不进屏幕）
        if (page.displayMode() == com.opendreamcore.page.DisplayMode.WORLD) {
            openWorld(page, serverSessionId);
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) {
            return;
        }
        double w = mc.getWindow().getGuiScaledWidth();
        double h = mc.getWindow().getGuiScaledHeight();
        String id = page.id() == null ? "page" : page.id();
        UiSession newSession = serverSessionId == null
                ? new UiSession(id) : new UiSession(id, serverSessionId);
        List<RenderNode> nodes = layoutPage(page, w, h);
        OdcScreen newScreen = new OdcScreen(page, nodes, newSession);
        // 拖拽位置记忆恢复
        double[] pos = rememberedPositions.get(id);
        if (pos != null) {
            newScreen.setOffset(pos[0], pos[1]);
        }
        this.session = newSession;
        this.screen = newScreen;
        screenStack.push(newScreen);
        AnimationEngine.get().reset();
        applyBindings(page.options()); // 页面键鼠绑定（keybinds/mousebinds 选项）
        applyMusic(page.options()); // 页面背景音乐（music 选项）
        // 先卸载底层原版界面：服务端插件下发页面时，原版箱子/背包界面可能仍开着，
        // 背包物品会从自定义 UI 缝隙透出（NeoForge/Fabric 同修）
        if (mc.screen != null && !(mc.screen instanceof OdcScreen)) {
            mc.setScreen(null);
        }
        mc.setScreen(newScreen);
        runLifecycle(page, "open");
        LOGGER.info("打开页面 {}（{} 个元素）", page.id(), count(nodes));
    }

    /** 打开子页（SUB_OPEN）：压栈显示在父页之上。 */
    public void openSubPage(Page page, String sessionId) {
        if (screenStack.isEmpty()) {
            open(page, sessionId);
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        double w = mc.getWindow().getGuiScaledWidth();
        double h = mc.getWindow().getGuiScaledHeight();
        String id = page.id() == null ? "sub" : page.id();
        UiSession newSession = new UiSession(id, sessionId);
        OdcScreen sub = new OdcScreen(page, layoutPage(page, w, h), newSession);
        this.session = newSession;
        this.screen = sub;
        screenStack.push(sub);
        applyBindings(page.options());
        applyMusic(page.options());
        if (mc.screen != null && !(mc.screen instanceof OdcScreen)) {
            mc.setScreen(null); // 卸载底层原版界面（容器物品不再透出）
        }
        mc.setScreen(sub);
        runLifecycle(page, "open");
    }

    /** 关闭当前页面（发 close 生命周期脚本 + 服务端关闭通知）。 */
    public void close() {
        if (screen != null) {
            runLifecycle(screen.page(), "close");
            // 服务端场景：通知服务端清理会话/容器绑定（ESC 关页服务端无法感知）
            String closingSession = screen.session().sessionId();
            sendPageClose(closingSession);
            containerStore.remove(closingSession);
            cancelScriptsForPage(screen.page().id()); // 页面关闭 → 该页定时任务清理
        }
        clearBindings(); // 页面键鼠绑定随页面关闭清除
        stopPageMusic(); // 页面音乐随页面关闭停止
        screenStack.pop();
        if (!screenStack.isEmpty()) {
            // 回到父页
            OdcScreen parent = screenStack.peek();
            this.session = parent.session();
            this.screen = parent;
            Minecraft.getInstance().setScreen(parent);
        } else {
            session = null;
            screen = null;
            Minecraft.getInstance().setScreen(null);
        }
    }

    /** 当前是否有打开的页面。 */
    public boolean isOpen() {
        return screen != null;
    }

    // ---------- HUD 常驻 ----------

    /** 打开 HUD 页面（覆盖旧 HUD）。 */
    public void openHud(Page page) {
        openHud(page, null);
    }

    /** 打开 HUD 页面；serverSessionId 非空表示服务端控制的 HUD（事件路由用）。 */
    public void openHud(Page page, String serverSessionId) {
        if (page == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        double w = mc.getWindow().getGuiScaledWidth();
        double h = mc.getWindow().getGuiScaledHeight();
        hudPage = page;
        hudNodes = layoutPage(page, w, h);
        hudSession = serverSessionId == null
                ? new UiSession(page.id() == null ? "hud" : page.id())
                : new UiSession(page.id() == null ? "hud" : page.id(), serverSessionId);
        applyGlobalBindings(page.options()); // HUD keybinds/mousebinds → 全局热键（页面外常驻）
        updateVanillaHide(page); // hideVanilla 选项 → 隐藏原版 HUD 层（NeoForge 逐层 / Fabric 整层）
        runLifecycle(page, "open");
        LOGGER.info("HUD 页面已挂载 {}（{}）", page.id(), serverSessionId == null ? "本地" : "服务端");
    }

    public void closeHud() {
        if (hudPage != null) {
            runLifecycle(hudPage, "close");
        }
        String hudPageId = hudPage == null ? null : hudPage.id();
        hudPage = null;
        hudNodes = null;
        hudSession = null;
        clearGlobalBindings(); // 全局热键随 HUD 卸载失效
        vanillaHiddenLayers.clear(); // HUD 关闭 → 原版 HUD 恢复
        cancelScriptsForPage(hudPageId); // HUD 关闭 → 其定时任务清理
    }

    public boolean isHudOpen() {
        return hudPage != null;
    }

    // ---------- 隐藏原版 HUD 层（hideVanilla 页面选项） ----------

    /** 需要隐藏的原版 HUD 层（ResourceLocation 全名；"*" = 全部）。 */
    private final java.util.Set<String> vanillaHiddenLayers = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** 简名 → minecraft 层名（与 NeoForge VanillaGuiLayers 一致；Fabric 逐层支持除 boss/debug 外全部）。 */
    private static final java.util.Map<String, String> VANILLA_LAYER_NAMES = java.util.Map.ofEntries(
            java.util.Map.entry("health", "minecraft:player_health"),
            java.util.Map.entry("food", "minecraft:food_level"),
            java.util.Map.entry("armor", "minecraft:armor_level"),
            java.util.Map.entry("air", "minecraft:air_level"),
            java.util.Map.entry("vehicle", "minecraft:vehicle_health"),
            java.util.Map.entry("hotbar", "minecraft:hotbar"),
            java.util.Map.entry("crosshair", "minecraft:crosshair"),
            java.util.Map.entry("exp", "minecraft:experience_bar"),
            java.util.Map.entry("experience", "minecraft:experience_bar"),
            java.util.Map.entry("jump", "minecraft:jump_meter"),
            java.util.Map.entry("boss", "minecraft:boss_overlay"),
            java.util.Map.entry("chat", "minecraft:chat"),
            java.util.Map.entry("effects", "minecraft:effects"),
            java.util.Map.entry("scoreboard", "minecraft:scoreboard_sidebar"),
            java.util.Map.entry("item_name", "minecraft:selected_item_name"),
            java.util.Map.entry("title", "minecraft:title"),
            java.util.Map.entry("subtitle", "minecraft:subtitle_overlay"),
            java.util.Map.entry("overlay", "minecraft:overlay_message"),
            java.util.Map.entry("tab", "minecraft:tab_list"),
            java.util.Map.entry("camera", "minecraft:camera_overlays"),
            java.util.Map.entry("sleep", "minecraft:sleep_overlay"),
            java.util.Map.entry("demo", "minecraft:demo_overlay"),
            java.util.Map.entry("saving", "minecraft:saving_indicator"),
            java.util.Map.entry("debug", "minecraft:debug_overlay"));

    /** 解析页面 hideVanilla 选项（true/all = 全部；列表/逗号串 = 指定层，支持简名与完整层名）。 */
    private void updateVanillaHide(Page page) {
        vanillaHiddenLayers.clear();
        if (page == null) {
            return;
        }
        Object raw = page.options() == null ? null : page.options().get("hideVanilla");
        if (raw == null) {
            return;
        }
        java.util.List<String> names = new java.util.ArrayList<>();
        if (raw instanceof List<?> list) {
            list.forEach(v -> names.add(String.valueOf(v)));
        } else if (raw instanceof Boolean b) {
            if (b) {
                names.add("*");
            }
        } else {
            String s = String.valueOf(raw).trim();
            if (s.isEmpty() || "false".equalsIgnoreCase(s)) {
                return;
            }
            if ("true".equalsIgnoreCase(s) || "all".equalsIgnoreCase(s) || "*".equals(s)) {
                names.add("*");
            } else {
                for (String part : s.split(",")) {
                    String t = part.trim();
                    if (!t.isEmpty()) {
                        names.add(t);
                    }
                }
            }
        }
        for (String name : names) {
            String lower = name.toLowerCase(java.util.Locale.ROOT);
            if ("*".equals(lower) || "all".equals(lower) || "true".equals(lower)) {
                vanillaHiddenLayers.add("*");
            } else if (lower.contains(":")) {
                vanillaHiddenLayers.add(lower);
            } else {
                String mapped = VANILLA_LAYER_NAMES.get(lower);
                if (mapped != null) {
                    vanillaHiddenLayers.add(mapped);
                }
            }
        }
    }

    /** 指定原版 HUD 层是否被页面隐藏（层名 = ResourceLocation 字符串；"*" = 全部）。 */
    public boolean isVanillaLayerHidden(String layerName) {
        return vanillaHiddenLayers.contains("*") || (layerName != null && vanillaHiddenLayers.contains(layerName));
    }

    /** 是否整层隐藏原版 HUD（hideVanilla: all/true；Fabric 侧走 Gui.render HEAD 取消）。 */
    public boolean vanillaHudHidden() {
        return vanillaHiddenLayers.contains("*");
    }

    /** HUD 渲染回调（RenderGuiEvent 里调用）。 */
    public void renderHud(net.minecraft.client.gui.GuiGraphics g) {
        if (hudNodes == null || hudPage == null) {
            return;
        }
        if (!hudDiagDone) {
            hudDiagDone = true;
            var w = Minecraft.getInstance().getWindow();
            LOGGER.info("HUD 首帧诊断: 元素 {} 画布 {}x{} guiScale {}", hudNodes.size(),
                    w.getGuiScaledWidth(), w.getGuiScaledHeight(), w.calculateScale(0, false));
        }
        // HUD 页面背景（options.background：颜色值 = 半透明遮罩；true = 默认深色；缺省/false = 透明）
        Object hudBg = hudPage.options() == null ? null : hudPage.options().get("background");
        int hudBgColor = 0;
        if (hudBg instanceof Number || hudBg instanceof String) {
            hudBgColor = UiStyle.color(hudBg, 0);
        }
        if (hudBgColor != 0) {
            g.fill(0, 0, (int) g.guiWidth(), (int) g.guiHeight(), hudBgColor);
        } else if (Boolean.TRUE.equals(hudBg)) {
            g.fill(0, 0, (int) g.guiWidth(), (int) g.guiHeight(), 0xA0000000);
        }
        // HUD 页面动画注册（自动播放 + 命名动画，服务端 ui_animation 远程触发）
        AnimationEngine.get().tick(hudPage.id(), hudPage.options(), hudPage.variables());
        Minecraft mc = Minecraft.getInstance();
        double scale = mc.getWindow().getGuiScaledWidth() / (double) mc.getWindow().getScreenWidth();
        int mouseX = (int) mc.mouseHandler.xpos(); // xpos() 已是 GUI 坐标(修复 GUI 缩放≠1 时双重缩放)
        int mouseY = (int) mc.mouseHandler.ypos();
        UiRenderer.draw(g, mc.font, hudNodes, mouseX, mouseY, null, hudPage.variables(), hudPage.id());
        renderWorldUi(g); // Boss 条 + 物品提示（无页面时也显示）
        renderHudTooltip(g, mc, mouseX, mouseY); // HUD 悬停 tooltip（服务端注册表/静态 tooltip 同管线）
        // HUD 编辑模式覆盖层
        if (hudEditMode) {
            renderHudEditOverlay(g, mc, mouseX, mouseY);
        }
    }

    /** HUD 编辑覆盖层：元素边框 + 选中高亮 + 顶部提示 + 专业面板（调色板/树/检查器）。 */
    private void renderHudEditOverlay(net.minecraft.client.gui.GuiGraphics g, Minecraft mc, int mouseX, int mouseY) {
        for (RenderNode node : hudNodes) {
            drawHudEditNode(g, node);
        }
        // 顶部提示
        g.fill(0, 0, 300, 16, 0xC0000000);
        g.drawString(mc.font, "§eHUD 编辑模式: 拖动元素 | ESC 退出", 2, 4, 0xFFFFD54F);
        // 专业面板（与屏幕编辑器同套：调色板/元素树/属性检查器）
        if (hudEditorPanels != null) {
            hudEditorPanels.render(g, mouseX, mouseY);
        }
    }

    private void drawHudEditNode(net.minecraft.client.gui.GuiGraphics g, RenderNode node) {
        if (!node.visible()) return;
        int x1 = (int) node.x();
        int y1 = (int) node.y();
        int x2 = (int) (node.x() + Math.max(node.width(), 0));
        int y2 = (int) (node.y() + Math.max(node.height(), 0));
        int color = node.id().equals(hudEditSelectedId) ? 0xFFFFFF00 : 0x80FFFFFF;
        g.fill(x1, y1, x2, y1 + 1, color);
        g.fill(x1, y2 - 1, x2, y2, color);
        g.fill(x1, y1, x1 + 1, y2, color);
        g.fill(x2 - 1, y1, x2, y2, color);
        g.drawString(Minecraft.getInstance().font, node.id(), x1 + 2, y1 + 2, color);
        for (RenderNode child : node.children()) {
            drawHudEditNode(g, child);
        }
    }

    /** 刷新 HUD 布局（编辑后重布局）。 */
    public void refreshHud() {
        if (hudPage == null) return;
        Minecraft mc = Minecraft.getInstance();
        double w = mc.getWindow().getGuiScaledWidth();
        double h = mc.getWindow().getGuiScaledHeight();
        String id = hudPage.id() == null ? "hud" : hudPage.id();
        hudNodes = layoutPage(hudPage, w, h);
    }

    /** 切换 HUD 编辑模式。 */
    public void setHudEditMode(boolean on) {
        this.hudEditMode = on;
        this.hudEditSelectedId = null;
        this.hudEditDragId = null;
        if (on) {
            this.hudEditorHost = new HudEditorHost();
            this.hudEditorPanels = new EditorPanels(this.hudEditorHost);
            this.hudEditorPanels.setCompactMode(true); // HUD 编辑：面板收起+半透明，不挡视野
        } else {
            this.hudEditorPanels = null;
            this.hudEditorHost = null;
        }
    }

    public boolean isHudEditMode() {
        return hudEditMode;
    }

    /**
     * HUD 编辑模式键盘轮询（无 Screen 接收按键，tick 边沿检测）：
     * ESC 退出 / Del 删除选中 / Ctrl+Z 撤消 / Ctrl+Y 重做 / Ctrl+C 复制 / Ctrl+V 粘贴。
     */
    private void tickHudEditKeys() {
        if (!hudEditMode || hudEditorHost == null || hudPage == null) {
            return;
        }
        var mc = Minecraft.getInstance();
        if (mc.screen != null || mc.player == null) {
            return;
        }
        long win = mc.getWindow().getWindow();
        boolean esc = org.lwjgl.glfw.GLFW.glfwGetKey(win, org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) == 1;
        if (esc && !hudEditEscPrev) {
            setHudEditMode(false);
            mc.player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("§e[OpenDreamCore] §f已退出 HUD 编辑模式"), false);
        }
        hudEditEscPrev = esc;
        boolean ctrl = org.lwjgl.glfw.GLFW.glfwGetKey(win, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL) == 1
                || org.lwjgl.glfw.GLFW.glfwGetKey(win, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL) == 1;
        boolean del = org.lwjgl.glfw.GLFW.glfwGetKey(win, org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE) == 1;
        if (del && !hudEditDelPrev && hudEditSelectedId != null) {
            hudEditorHost.deleteSelected();
        }
        hudEditDelPrev = del;
        boolean z = org.lwjgl.glfw.GLFW.glfwGetKey(win, org.lwjgl.glfw.GLFW.GLFW_KEY_Z) == 1;
        if (ctrl && z && !hudEditZPrev) {
            hudEditorHost.undoOrRedo(org.lwjgl.glfw.GLFW.glfwGetKey(win, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT) == 1
                    || org.lwjgl.glfw.GLFW.glfwGetKey(win, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT) == 1);
        }
        hudEditZPrev = ctrl && z;
        boolean y = org.lwjgl.glfw.GLFW.glfwGetKey(win, org.lwjgl.glfw.GLFW.GLFW_KEY_Y) == 1;
        if (ctrl && y && !hudEditYPrev) {
            hudEditorHost.undoOrRedo(true);
        }
        hudEditYPrev = ctrl && y;
        boolean c = org.lwjgl.glfw.GLFW.glfwGetKey(win, org.lwjgl.glfw.GLFW.GLFW_KEY_C) == 1;
        if (ctrl && c && !hudEditCPrev && hudEditSelectedId != null) {
            hudEditorHost.copySelected();
        }
        hudEditCPrev = ctrl && c;
        boolean v = org.lwjgl.glfw.GLFW.glfwGetKey(win, org.lwjgl.glfw.GLFW.GLFW_KEY_V) == 1;
        if (ctrl && v && !hudEditVPrev) {
            hudEditorHost.pasteClipboard();
        }
        hudEditVPrev = ctrl && v;
    }

    /** HUD 编辑面板宿主（EditorPanels 桥接；操作走 elementEdits，刷新走 refreshHud）。 */
    private final class HudEditorHost implements EditorPanels.Host {
        private final java.util.Deque<String> undo = new java.util.ArrayDeque<>();
        private final java.util.Deque<String> redo = new java.util.ArrayDeque<>();
        private Element clipboard;

        @Override public com.opendreamcore.page.Page page() { return hudPage; }
        @Override public List<RenderNode> nodes() { return hudNodes; }
        @Override public net.minecraft.client.gui.Font font() { return Minecraft.getInstance().font; }
        @Override public int width() { return Minecraft.getInstance().getWindow().getGuiScaledWidth(); }
        @Override public int height() { return Minecraft.getInstance().getWindow().getGuiScaledHeight(); }
        @Override public RenderNode findNode(String id) { return findHudNode(id); }
        @Override public Element findElement(String id) { return ClientController.findElement(hudPage, id); }
        @Override public void selectElement(String id) { hudEditSelectedId = id; }
        @Override public void refreshCurrent() { refreshHud(); }
        @Override public void pushUndo() {
            try {
                String pid = hudPage.id() == null ? "hud" : hudPage.id();
                undo.push(elementEditsSnapshot(pid));
                if (undo.size() > 64) undo.removeLast();
                redo.clear();
            } catch (Exception ignored) {}
        }
        @Override public String editSnapshot() {
            return elementEditsSnapshot(hudPage.id() == null ? "hud" : hudPage.id());
        }
        @Override public void restoreEdit(String json) {
            restoreElementEdits(hudPage.id() == null ? "hud" : hudPage.id(), json);
            refreshHud();
        }
        @Override public void setElementProp(String elementId, String prop, Object value) {
            Element el = ClientController.findElement(hudPage, elementId);
            if (el != null) el.props().put(prop, value);
            refreshHud();
        }
        @Override public void setElementPropDeep(String elementId, String prop, Object value) {
            Element el = ClientController.findElement(hudPage, elementId);
            if (el != null && prop.contains(".")) {
                String[] parts = prop.split("\\.", 2);
                if (el.props().get(parts[0]) instanceof java.util.Map<?, ?> rawSpec) {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> spec = (java.util.Map<String, Object>) rawSpec;
                    spec.put(parts[1], value);
                }
            } else if (el != null) {
                el.props().put(prop, value);
            }
            refreshHud();
        }
        @Override public void moveElementInTree(String elementId, int direction) {
            // 简化实现：HUD 编辑暂用位置微调代替树排序
        }
        @Override public void reparentElement(String elementId, String newParentId) {
            // HUD 编辑暂不支持重挂载
        }
        @Override public void setElementPos(String elementId, double x, double y) {
            String pid = hudPage.id() == null ? "hud" : hudPage.id();
            elementEdits.set(pid, elementId, x, y);
            refreshHud();
        }
        @Override public void deleteElement(String elementId) {
            String pid = hudPage.id() == null ? "hud" : hudPage.id();
            elementEdits.markDeleted(pid, elementId);
            hudEditSelectedId = null;
            refreshHud();
        }
        @Override public void toggleElementHidden(String elementId) {
            String pid = hudPage.id() == null ? "hud" : hudPage.id();
            if (elementEdits.isHidden(pid, elementId)) {
                elementEdits.unmarkHidden(pid, elementId);
            } else {
                pushUndo();
                elementEdits.markHidden(pid, elementId);
            }
            refreshHud();
        }
        @Override public void copyElement(String elementId) {
            Element el = ClientController.findElement(hudPage, elementId);
            if (el != null) {
                clipboard = (Element) copyElementTree(el, el.id(), new java.util.HashMap<>());
            }
        }
        @Override public void addElement(String type, double x, double y) {
            String id = type + "_" + System.currentTimeMillis() % 10000;
            int[] size = com.opendreamcore.client.screen.EditSpecs.defaultSizeFor(type);
            com.opendreamcore.page.Layout layout = new com.opendreamcore.page.Layout(
                    String.valueOf((int) x), String.valueOf((int) y),
                    String.valueOf(size[0]), String.valueOf(size[1]));
            Element el = new Element(id, type, layout, com.opendreamcore.client.screen.EditSpecs.defaultPropsFor(type),
                    null, null, new java.util.LinkedHashMap<>(), List.of(), null);
            String pid = hudPage.id() == null ? "hud" : hudPage.id();
            elementEdits.addCopy(pid, el);
            hudEditSelectedId = id;
            refreshHud();
        }
        @Override public String selectedId() { return hudEditSelectedId; }

        /** 删除选中元素（键盘 Del）。 */
        void deleteSelected() {
            if (hudEditSelectedId == null) return;
            pushUndo();
            deleteElement(hudEditSelectedId);
        }

        /** 撤消/重做（键盘 Ctrl+Z / Ctrl+Y、Ctrl+Shift+Z）。 */
        void undoOrRedo(boolean isRedo) {
            String pid = hudPage.id() == null ? "hud" : hudPage.id();
            if (isRedo) {
                if (isRedo ? redo.isEmpty() : undo.isEmpty()) return;
                undo.push(editSnapshot());
                restoreEdit(redo.pop());
            } else {
                if (undo.isEmpty()) return;
                redo.push(editSnapshot());
                restoreEdit(undo.pop());
            }
        }

        /** 复制选中元素到 HUD 剪贴板（键盘 Ctrl+C）。 */
        void copySelected() {
            if (hudEditSelectedId == null) return;
            Element el = ClientController.findElement(hudPage, hudEditSelectedId);
            if (el != null) {
                clipboard = (Element) copyElementTree(el, el.id(), new java.util.HashMap<>());
                Minecraft.getInstance().player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("§a[OpenDreamCore] §f已复制 " + el.id()), false);
            }
        }

        /** 粘贴剪贴板元素（键盘 Ctrl+V，偏移 +10px 防重叠）。 */
        void pasteClipboard() {
            if (clipboard == null) return;
            pushUndo();
            String newId = clipboard.type() + "_" + System.currentTimeMillis() % 10000;
            Element tree = (Element) copyElementTree(clipboard, newId, new java.util.HashMap<>());
            com.opendreamcore.page.Layout old = clipboard.layout();
            com.opendreamcore.page.Layout moved = new com.opendreamcore.page.Layout(
                    String.valueOf(numSafe(old.x()) + 10), String.valueOf(numSafe(old.y()) + 10),
                    old.width(), old.height());
            Element copy = new Element(newId, tree.type(), moved, tree.props(),
                    tree.visibleWhen(), tree.enabledWhen(), tree.actions(), tree.children(), tree.parent());
            String pid = hudPage.id() == null ? "hud" : hudPage.id();
            elementEdits.addCopy(pid, copy);
            hudEditSelectedId = newId;
            refreshHud();
        }

        private double numSafe(String v) {
            try {
                return Double.parseDouble(v == null ? "0" : v.trim());
            } catch (NumberFormatException e) {
                return 0;
            }
        }
    }

    /** HUD 悬停 tooltip：当前悬停元素（hudHoverId，250ms 延迟防滑动闪烁）的 tooltip 气泡（复用屏幕样式管线）。 */
    private void renderHudTooltip(net.minecraft.client.gui.GuiGraphics g, Minecraft mc, int mouseX, int mouseY) {
        String ttId = hudHoverId;
        if (ttId == null || mc.screen != null || hudPage == null
                || System.currentTimeMillis() - hudHoverSince < 250) {
            return;
        }
        RenderNode ttNode = findHudNode(ttId);
        if (ttNode == null || ttNode.source() == null) {
            return;
        }
        String text = null;
        int textColor = 0xFFE0E0E0;
        int background = 0xE610151F;
        int border = 0xFF42A5F5;
        int maxW = 200;
        com.opendreamcore.protocol.message.TooltipRegistry.Entry server = tooltips().get(ttId);
        Object raw = ttNode.source().props().get("tooltip");
        if (server != null && !server.text().isEmpty()) {
            text = server.text();
            if (server.color() != null && !server.color().isEmpty()) {
                textColor = UiStyle.color(server.color(), textColor);
            }
            if (server.background() != null && !server.background().isEmpty()) {
                background = UiStyle.color(server.background(), background);
            }
            if (server.border() != null && !server.border().isEmpty()) {
                border = UiStyle.color(server.border(), border);
            }
            if (server.width() > 0) {
                maxW = (int) server.width();
            }
        } else if (raw instanceof Map<?, ?> m) {
            Object content = m.get("content");
            if (content == null) {
                content = m.get("text");
            }
            if (content != null) {
                text = String.valueOf(content);
            }
            textColor = UiStyle.color(m.get("textColor"), UiStyle.color(m.get("color"), textColor));
            background = UiStyle.color(m.get("background"), background);
            border = UiStyle.color(m.get("border"), border);
            if (m.get("width") instanceof Number n) {
                maxW = n.intValue();
            }
        } else if (raw instanceof java.util.List<?> list) {
            StringBuilder sb = new StringBuilder();
            for (Object line : list) {
                if (line == null) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(String.valueOf(line));
            }
            text = sb.length() == 0 ? null : sb.toString();
        } else if (raw != null) {
            text = String.valueOf(raw);
        }
        if (text == null || text.isEmpty()) {
            return;
        }
        String interpolated = UiRenderer.interpolate(ttNode, text, hudPage.variables());
        if (interpolated == null || interpolated.isEmpty()) {
            return;
        }
        drawWorldTooltip(g, mc, interpolated, textColor, background, border, maxW);
    }

    /** HUD 节点树按 id 查找（含嵌套）。 */
    private RenderNode findHudNode(String id) {
        if (hudNodes == null || id == null) {
            return null;
        }
        for (RenderNode root : hudNodes) {
            RenderNode found = findHudNodeIn(root, id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private RenderNode findHudNodeIn(RenderNode node, String id) {
        if (id.equals(node.id())) {
            return node;
        }
        for (RenderNode child : node.children()) {
            RenderNode found = findHudNodeIn(child, id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /**
     * 克隆当前聚焦面板为独立新面板：id 加 _copy 后缀（冲突递增），元素/选项/变量深拷贝，
     * 新面板 x 偏移 +2.5（避免叠在原面板上）；注册进本地页面仓库，服务端模式申请编辑租约
     * （保存 = 写为独立页面文件）。
     */
    public void cloneWorldPanel() {
        if (worldPage == null) {
            return;
        }
        String pid = worldPage.id() == null ? "world" : worldPage.id();
        String newId = pid + "_copy";
        int suffix = 2;
        while (findWorldPanel(newId) != null || localPages().get(newId) != null) {
            newId = pid + "_copy" + (suffix++);
        }
        java.util.List<Element> els = new java.util.ArrayList<>();
        for (Element el : worldPage.elements()) {
            Element copy = elementFromJsonMap(elementToJson(el));
            if (copy != null) {
                els.add(copy);
            }
        }
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> opts = (java.util.Map<String, Object>) deepCopy(worldPage.options());
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> vars = (java.util.Map<String, Object>) deepCopy(worldPage.variables());
        Page clone = new Page(newId, worldPage.title(), null, worldPage.displayMode(),
                vars, els, worldPage.functions(), opts);
        localPages().add(clone);
        // 偏移：克隆面板 x +2.5 格
        double ox = opts.get("offsetX") instanceof Number n ? n.doubleValue() : 0;
        opts.put("offsetX", Math.round((ox + 2.5) * 100) / 100.0);
        openWorld(clone, null);
        if (isServerMode()) {
            requestLease(newId);
        }
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§a[OpenDreamCore] §f已克隆面板 " + pid + " → " + newId
                        + "（" + els.size() + " 个元素；保存写为独立页面）"), false);
    }

    /** 进服时按 match 自动挂载 HUD 页面（本地仓库）。 */
    public void autoMountHud() {        Page hud = localPages.match("hud", null, com.opendreamcore.page.DisplayMode.HUD);
        if (hud != null) {
            openHud(hud);
        }
        Page world = localPages.match("world", null, com.opendreamcore.page.DisplayMode.WORLD);
        if (world != null) {
            openWorld(world, null);
        }
    }

    // ---------- 世界全息 ----------

    /** 打开世界全息页面（同 id 重开 = 原位刷新；不同 id = 追加新面板，多面板同屏）；
     *  serverSessionId 非空 = 服务端页面（事件上报裁决）。打开后聚焦该面板。 */
    public void openWorld(Page page, String serverSessionId) {
        if (page == null) {
            return;
        }
        String pid = page.id() == null ? "world" : page.id();
        WorldPanel panel = findWorldPanel(pid);
        UiSession session = serverSessionId == null
                ? null  // 无服务端会话 → 事件走本地脚本（不发 ui_event）
                : new UiSession(pid, serverSessionId);
        if (panel != null) {
            // 同页重开：原位刷新节点/会话，面板位置（偏移）保持不变
            panel.nodes = layoutPage(page, 800, 600);
            panel.session = session;
            panel.page = page;
        } else {
            panel = new WorldPanel(page, layoutPage(page, 800, 600), session);
            worldPanels.add(panel);
        }
        // 该页状态回到定义值（其余面板状态不动）
        String prefix = pid + "/";
        WorldEditor.get().worldElementStates.keySet().removeIf(k -> k.startsWith(prefix));
        worldToggle.keySet().removeIf(k -> k.startsWith(prefix));
        worldSlider.keySet().removeIf(k -> k.startsWith(prefix));
        worldDropdown.keySet().removeIf(k -> k.startsWith(prefix));
        worldTab.remove(pid); // 页签回到定义值（active 表达式/首个选项）
        panel.hoverId = null;
        panel.pendingHoverId = null;
        panel.tabSwitchAt = 0;
        panel.anchor = null;      // 锚点重置（follow:false 重新固定、平滑跟随重新起算）
        panel.pinnedAnchor = null;
        focusWorldPanel(panel);
        WorldEditor.get().worldEditDirty.clear(); // 页面重开（保存后重发/放弃）→ 未保存编辑清空
        WorldEditor.get().worldEditProps.clear();
        WorldEditor.get().worldEditDeletes.clear();
        WorldEditor.get().worldEditDeletedElements.clear();
        clearWorldUndo(); // 页面重开 → 撤消/重做历史清空
        WorldEditor.get().worldOptionsBaseline = worldPage == null ? null : snapshotWorldOptions(); // 页面重开 → options 基线跟随新定义值
        WorldEditor.get().worldEditPageTitle = null; // 页面重开 → 标题待写清空（保存后重发已生效）
        WorldEditor.get().worldVariablesBaseline = worldPage == null ? null : snapshotWorldVariables(); // 页面重开 → 变量基线跟随新定义值
        WorldEditor.get().worldEditVars.clear(); // 页面重开 → 变量待写清空
        WorldEditor.get().worldRotateId = null; // 手柄拖拽态（旋转/缩放/描边）重开即清
        WorldEditor.get().worldResizeId = null;
        WorldEditor.get().worldBorderId = null;
        WorldEditor.get().worldBorderStartPt = null;
        updateWorldEditMode();
        runLifecycle(page, "open");
        LOGGER.info("世界全息已挂载 {}（{}），共 {} 面板", pid,
                session == null ? "本地" : "服务端", worldPanels.size());
    }

    /** 关闭当前聚焦的世界面板；还有其余面板则聚焦下一个。 */
    public void closeWorld() {
        closeWorldPanel(worldPage == null ? null
                : (worldPage.id() == null ? "world" : worldPage.id()));
    }

    /** 按服务端会话关闭对应世界面板（会话不匹配时忽略）。 */
    public void closeWorld(String serverSessionId) {        if (serverSessionId == null) {
            closeWorld();
            return;
        }
        for (WorldPanel panel : worldPanels) {
            if (panel.session != null && serverSessionId.equals(panel.session.sessionId())) {
                closeWorldPanel(panel.page.id() == null ? "world" : panel.page.id());
                return;
            }
        }
    }

    /** 关闭指定页面 id 的世界面板。 */
    private void closeWorldPanel(String pageId) {
        WorldPanel panel = findWorldPanel(pageId);
        if (panel != null) {
            runLifecycle(panel.page, "close");
            String pid = panel.page.id() == null ? "world" : panel.page.id();
            String prefix = pid + "/";
            WorldEditor.get().worldElementStates.keySet().removeIf(k -> k.startsWith(prefix));
            worldToggle.keySet().removeIf(k -> k.startsWith(prefix));
            worldSlider.keySet().removeIf(k -> k.startsWith(prefix));
            worldDropdown.keySet().removeIf(k -> k.startsWith(prefix));
            worldTab.remove(pid);
            worldPanels.remove(panel);
            cancelScriptsForPage(pid); // 世界面板关闭 → 其定时任务清理
        }
        WorldEditor.get().worldEditDirty.clear();
        WorldEditor.get().worldEditProps.clear();
        WorldEditor.get().worldEditDeletes.clear();
        WorldEditor.get().worldEditDeletedElements.clear();
        clearWorldUndo(); // 面板关闭 → 撤消/重做历史清空
        if (worldPanels.isEmpty()) {
            worldPage = null;
            worldNodes = null;
            worldSession = null;
            WorldEditor.get().worldHoverId = null;
            pendingHoverId = null;
            WorldEditor.get().worldElementStates.clear();
            worldToggle.clear();
            worldSlider.clear();
            worldDropdown.clear();
            worldTab.clear();
        } else {
            focusWorldPanel(worldPanels.get(0));
        }
        updateWorldEditMode();
    }

    /** 按页面 id 查找面板。 */
    WorldPanel findWorldPanel(String pageId) {
        for (WorldPanel p : worldPanels) {
            if (pageId.equals(p.page.id() == null ? "world" : p.page.id())) {
                return p;
            }
        }
        return null;
    }

    /** 聚焦面板：worldPage/worldNodes/worldSession 别名指向该面板（交互代码零改动）。 */
    void focusWorldPanel(WorldPanel panel) {
        if (panel == null) {
            return;
        }
        worldPage = panel.page;
        worldNodes = panel.nodes;
        worldSession = panel.session;
        WorldEditor.get().worldHoverId = panel.hoverId;
        pendingHoverId = panel.pendingHoverId;
        WorldEditor.get().worldPressedId = null;
        WorldEditor.get().worldPressCandidate = null;
        WorldEditor.get().worldDragId = null;
        worldDragOffsets.clear();
        WorldEditor.get().worldDragGuides = null;
        WorldEditor.get().worldSliderDragId = null;
        WorldEditor.get().worldMarquee = null;
        WorldEditor.get().worldPanelMove = false;
        WorldEditor.get().worldPanelMoveBase = null;
        worldPanelMoveOrig.clear();
        WorldEditor.get().worldAnchorDragActive = false;
        WorldEditor.get().worldAnchorDragBase = null;
        WorldEditor.get().worldTypeDrag = null;
        WorldEditor.get().worldTypeDragMoved = false;
        WorldEditor.get().worldTypeDropPoint = null;
        WorldEditor.get().worldZScrubId = null;
        worldZScrubBase.clear();
        WorldEditor.get().worldOpacityScrubId = null;
        worldOpacityScrubBase.clear();
        WorldEditor.get().worldCtxId = null;
        worldCtxRects.clear();
        WorldEditor.get().worldEditSelected = null;
        worldEditMulti.clear();
        worldMarqueePreview.clear();
        updateWorldEditMode();
    }

    /** 按索引聚焦面板。 */
    void focusWorldPanel(int index) {
        if (index >= 0 && index < worldPanels.size()) {
            focusWorldPanel(worldPanels.get(index));
        }
    }

    /** 对齐屏面板循环切换：聚焦下一块面板并选中元素（同名 id 优先，否则该页首个元素）；返回新元素 id（无可选 = null）。 */
    public String cycleWorldEditFocus(String currentElementId, int dir) {
        if (worldPanels.isEmpty()) {
            return null;
        }
        int idx = 0;
        WorldPanel cur = worldPage == null ? null
                : findWorldPanel(worldPage.id() == null ? "world" : worldPage.id());
        for (int i = 0; i < worldPanels.size(); i++) {
            if (worldPanels.get(i) == cur) {
                idx = i;
                break;
            }
        }
        if (cur != null) { // 记录离开面板的选择（跨面板对齐参考）
            String pid0 = cur.page.id() == null ? "world" : cur.page.id();
            if (currentElementId != null) {
                WorldEditor.get().worldPanelSelections.put(pid0, currentElementId);
            }
            WorldEditor.get().worldLastPanelPid = pid0;
        }
        int next = ((idx + dir) % worldPanels.size() + worldPanels.size()) % worldPanels.size();
        WorldPanel panel = worldPanels.get(next);
        focusWorldPanel(panel);
        String pid = worldPage.id() == null ? "world" : worldPage.id();
        String sel = null;
        String remembered = WorldEditor.get().worldPanelSelections.get(pid);
        if (remembered != null && findElement(worldPage, remembered) != null) {
            sel = remembered; // 该面板上次选择优先
        } else if (currentElementId != null && findElement(worldPage, currentElementId) != null) {
            sel = currentElementId; // 同名 id 跨页保留选择
        } else {
            for (Element el : worldPage.elements()) {
                if (el.id() != null && !el.id().isBlank()) {
                    sel = el.id();
                    break;
                }
            }
        }
        WorldEditor.get().worldEditSelected = sel;
        if (sel != null) {
            WorldEditor.get().worldPanelSelections.put(pid, sel);
        }
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§b[OpenDreamCore] §f聚焦面板: " + pid
                        + "（" + (next + 1) + "/" + worldPanels.size() + "）"
                        + (sel == null ? " · 该页无元素" : " · 选中: " + sel)), false);
        return sel;
    }

    /** 跨面板对齐参考是否存在（上次聚焦面板不同且其选择非空）。 */
    public boolean worldCrossAlignAvailable() {
        if (WorldEditor.get().worldLastPanelPid == null || worldPage == null) {
            return false;
        }
        String pid = worldPage.id() == null ? "world" : worldPage.id();
        if (pid.equals(WorldEditor.get().worldLastPanelPid)) {
            return false;
        }
        String otherId = WorldEditor.get().worldPanelSelections.get(WorldEditor.get().worldLastPanelPid);
        if (otherId == null) {
            return false;
        }
        WorldPanel other = findWorldPanel(WorldEditor.get().worldLastPanelPid);
        return other != null && findElement(other.page, otherId) != null;
    }

    /** 跨面板对齐：当前面板选中元素按模式对齐到上次聚焦面板的选中元素（世界坐标换算，含锚点差；一步撤消）。 */

    /**
     * 每帧更新各面板锚点：
     * - world.anchor: {x,y,z} → 绝对世界坐标（不跟随玩家）
     * - world.follow: false → 打开瞬间的位置固定（pin）
     * - world.smooth: 0~1 → 平滑跟随（每帧向目标插值，漂浮感）
     */
    void updateWorldPanelAnchors(net.minecraft.client.Camera camera) {
        for (WorldPanel panel : worldPanels) {
            Map<String, Object> options = panel.page.options();
            Object world = options == null ? null : options.get("world");
            boolean follow = world instanceof Map<?, ?> w && w.get("follow") != null
                    && Boolean.parseBoolean(String.valueOf(w.get("follow")));
            net.minecraft.world.phys.Vec3 target;
            if (!follow) {
                // 默认固定：打开瞬间 pin 住（除非 follow: true 显式跟随）
                if (panel.pinnedAnchor == null) {
                    panel.pinnedAnchor = WorldHologram.anchorFor(camera, options);
                }
                target = panel.pinnedAnchor;
            } else {
                target = WorldHologram.anchorFor(camera, options);
            }
            double smooth = 0;
            if (world instanceof Map<?, ?> w) {
                smooth = numOf(w.get("smooth"), 0);
            }
            smooth = Math.max(0, Math.min(1, smooth));
            if (smooth > 0 && panel.anchor != null) {
                net.minecraft.world.phys.Vec3 cur = panel.anchor;
                target = new net.minecraft.world.phys.Vec3(
                        cur.x + (target.x - cur.x) * smooth,
                        cur.y + (target.y - cur.y) * smooth,
                        cur.z + (target.z - cur.z) * smooth);
            }
            panel.anchor = target;
        }
    }

    /** 聚焦面板生效锚点（渲染/交互/编辑共用，保证拾取与渲染一致）。 */
    net.minecraft.world.phys.Vec3 focusedWorldAnchor(net.minecraft.client.Camera camera) {
        String pid = worldPage == null || worldPage.id() == null ? "world" : worldPage.id();
        WorldPanel panel = findWorldPanel(pid);
        if (panel != null && panel.anchor != null) {
            return panel.anchor;
        }
        return WorldHologram.anchorFor(camera,
                worldPage == null ? java.util.Map.of() : worldPage.options());
    }

    /** 世界编辑模式 = 持有该页租约 + 该世界页正在打开（/odc edit world 授予后自动进入）。 */
    private void updateWorldEditMode() {
        boolean on = leasePageId != null && worldPage != null
                && java.util.Objects.equals(leasePageId, worldPage.id());
        if (on == WorldEditor.get().worldEditMode) {
            return;
        }
        WorldEditor.get().worldEditMode = on;
        if (on) {
            WorldEditor.get().worldOptionsBaseline = snapshotWorldOptions(); // 页面级 options 基线（保存差异/放弃还原）
            WorldEditor.get().worldVariablesBaseline = snapshotWorldVariables(); // 页面 variables 基线（放弃还原）
        } else {
            WorldEditor.get().worldOptionsBaseline = null;
            WorldEditor.get().worldEditPageTitle = null;
            WorldEditor.get().worldVariablesBaseline = null;
            WorldEditor.get().worldEditVars.clear();
        }
        WorldEditor.get().worldPanelScaleAccum = 1.0; // 缩放读数归位
        WorldEditor.get().worldPanelScaleAt = 0;
        WorldEditor.get().worldPanelRotateAccum = 0; // 旋转读数归位
        WorldEditor.get().worldPanelRotateAt = 0;
        WorldEditor.get().worldEditSelected = null;
        worldEditMulti.clear();
        worldMarqueePreview.clear();
        worldEditHighlight.clear();
        WorldEditor.get().worldEditOriginal.clear();
        WorldEditor.get().worldEditOriginalProps.clear();
        WorldEditor.get().worldEditDeletes.clear();
        WorldEditor.get().worldEditDeletedElements.clear();
        // 手柄拖拽态清空（旋转/缩放/描边），避免模式重入后残留
        WorldEditor.get().worldRotateId = null;
        WorldEditor.get().worldResizeId = null;
        WorldEditor.get().worldBorderId = null;
        WorldEditor.get().worldBorderStartPt = null;
        // 拖入创建拖拽态清空（chip 拖到一半退出编辑 → 丢弃）
        WorldEditor.get().worldTypeDrag = null;
        WorldEditor.get().worldTypeDragMoved = false;
        WorldEditor.get().worldTypeDropPoint = null;
        // z 排序拖拽态清空（拖到一半退出编辑 → 丢弃）
        WorldEditor.get().worldZScrubId = null;
        worldZScrubBase.clear();
        // 透明度拖拽态清空（拖到一半退出编辑 → 丢弃）
        WorldEditor.get().worldOpacityScrubId = null;
        worldOpacityScrubBase.clear();
        // 右键菜单清空（退出编辑 → 关闭）
        WorldEditor.get().worldCtxId = null;
        worldCtxRects.clear();
        if (on) {
            // 快照当前位置（放弃编辑 = 还原到这里）
            var vars = worldPage.variables();
            for (RenderNode node : worldNodes) {
                Map<?, ?> holo = node.props().get("hologram") instanceof Map<?, ?> h ? h : Map.of();
                if (holo.isEmpty()) {
                    continue;
                }
                WorldEditor.get().worldEditOriginal.put(node.id(), new double[]{
                        WorldHologram.holoNum(holo, "x", 0, vars),
                        WorldHologram.holoNum(holo, "y", 0, vars),
                        WorldHologram.holoNum(holo, "z", 0, vars)});
                // 快照可编辑属性（文本/颜色/缩放）
                var src = node.source();
                if (src != null) {
                    Map<String, String> props = new java.util.LinkedHashMap<>();
                    String content = elementPropValue(src, "text.content");
                    if (content != null) {
                        props.put("text.content", content);
                    }
                    String color = elementPropValue(src, "text.color");
                    if (color != null) {
                        props.put("text.color", color);
                    }
                    String scale = elementPropValue(src, "hologram.scale");
                    if (scale != null) {
                        props.put("hologram.scale", scale);
                    }
                    if (!props.isEmpty()) {
                        WorldEditor.get().worldEditOriginalProps.put(node.id(), props);
                    }
                }
            }
        }
    }

    public boolean isWorldOpen() {
        return worldPage != null;
    }

    /** 世界页面变量（hologram 表达式求值用）。 */
    public java.util.Map<String, Object> worldVariables() {
        return worldPage == null ? java.util.Map.of() : worldPage.variables();
    }

    /** 世界开关当前值（toggle 元素渲染用；无本地状态返回 null → 用元素定义值；按页隔离）。 */
    public Boolean worldToggleValue(String pageId, String elementId) {
        return worldToggle.get(wkey(pageId, elementId));
    }

    /** 世界滑块当前值（slider 元素渲染用；无本地状态返回 null → 用元素定义值；按页隔离）。 */
    public Double worldSliderValue(String pageId, String elementId) {
        return worldSlider.get(wkey(pageId, elementId));
    }

    /** 世界下拉当前选项下标（dropdown 渲染用；无本地状态返回 null；按页隔离）。 */
    public Integer worldDropdownIndex(String pageId, String elementId) {
        return worldDropdown.get(wkey(pageId, elementId));
    }

    /** 下拉点击：切到下一个选项并上报 INPUT（选项值）。 */

    // ---------- 世界页签（tabs 元素 + 元素 tab 属性） ----------

    /** 当前激活页签名（无本地状态 → 页签元素定义值/第一个选项；无 tabs 元素返回 null；按页隔离）。 */
    public String worldTabActive(String pageId) {
        String pid = pageId == null ? "world" : pageId;
        String cur = worldTab.get(pid);
        if (cur != null) {
            return cur;
        }
        WorldPanel panel = findWorldPanel(pid);
        if (panel == null) {
            return null;
        }
        RenderNode tabs = findTabsNode(panel.nodes);
        if (tabs == null) {
            return null; // 没有页签栏 → 不过滤
        }
        Map<?, ?> spec = UiRenderer.propsMap(tabs, "tabs");
        List<?> options = spec.get("options") instanceof List<?> l ? l : List.of();
        Object active = spec.get("active");
        if (active != null) {
            String s = UiRenderer.interpolate(tabs, String.valueOf(active), panel.page.variables());
            if (s != null && !s.isBlank()) {
                return s;
            }
        }
        return options.isEmpty() ? null : String.valueOf(options.get(0));
    }

    /** 递归找页签栏元素（type: tabs）。 */
    private static RenderNode findTabsNode(List<RenderNode> nodes) {
        if (nodes == null) {
            return null;
        }
        for (RenderNode node : nodes) {
            if ("tabs".equals(node.type())) {
                return node;
            }
            RenderNode found = findTabsNode(node.children());
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** 页签点击：局部 X → 选项下标 → 切换激活页签（INPUT 上报选项值）。 */

    /** 世界页签循环切换（对齐屏 ◀/▶ 按钮；dir=±1 循环；无页签栏提示）。 */
    public void cycleWorldTab(int dir) {
        if (worldPage == null || worldNodes == null) {
            return;
        }
        RenderNode tabs = findTabsNode(worldNodes);
        if (tabs == null) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§e[OpenDreamCore] §f该页面无页签栏（tabs 元素）"), false);
            return;
        }
        Map<?, ?> spec = UiRenderer.propsMap(tabs, "tabs");
        List<?> options = spec.get("options") instanceof List<?> l ? l : List.of();
        if (options.isEmpty()) {
            return;
        }
        String pageId = worldPage.id() == null ? "world" : worldPage.id();
        String cur = worldTabActive(pageId);
        int idx = 0;
        for (int i = 0; i < options.size(); i++) {
            if (String.valueOf(options.get(i)).equals(cur)) {
                idx = i;
                break;
            }
        }
        int next = ((idx + dir) % options.size() + options.size()) % options.size();
        String value = String.valueOf(options.get(next));
        String prev = worldTab.get(pageId);
        worldTab.put(pageId, value);
        WorldPanel panel = findWorldPanel(pageId);
        if (panel != null) {
            panel.tabSwitchAt = System.currentTimeMillis(); // 页签内容淡入过渡
        }
        runTabChangeLifecycle(worldPage, value, prev);
        if (worldSession != null && isServerMode()) {
            sendEvent(worldSession.event(tabs.id(), UiEvent.Trigger.INPUT, value));
        } else {
            String script = tabs.source() != null ? tabs.source().actions().get("input") : null;
            if (script != null && !script.isBlank()) {
                runLocalAction(worldPage, script, value);
            }
        }
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§b[OpenDreamCore] §f页签: " + value), false);
    }

    /** 递归找 type=tabs 的元素（页面元素树；无 = null）。 */
    private static Element findTabsElement(java.util.List<Element> elements) {
        for (Element el : elements) {
            if ("tabs".equals(el.type())) {
                return el;
            }
            Element found = findTabsElement(el.children());
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** 当前页签栏标签（tabs 元素 options 列表；无页签栏 = null）。 */
    public java.util.List<String> worldTabLabels() {
        if (worldPage == null) {
            return java.util.List.of();
        }
        Element tabsEl = findTabsElement(worldPage.elements());
        if (tabsEl == null) {
            return java.util.List.of();
        }
        Object t = tabsEl.props().get("tabs");
        Object o = t instanceof Map<?, ?> tm ? tm.get("options") : null;
        if (o instanceof List<?> l) {
            java.util.List<String> out = new java.util.ArrayList<>();
            for (Object item : l) {
                out.add(String.valueOf(item));
            }
            return out;
        }
        return java.util.List.of();
    }

    /** 设置页签标签（对齐屏页签▽按钮；| / , / 、 分隔；空项忽略；≤32 个；保存后写回页面文件）。 */
    public void setWorldTabLabels(String raw) {
        if (worldPage == null || !WorldEditor.get().worldEditMode) {
            return;
        }
        Element tabsEl = findTabsElement(worldPage.elements());
        if (tabsEl == null) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§e[OpenDreamCore] §f该页面无页签栏（tabs 元素）"), false);
            return;
        }
        java.util.List<String> labels = new java.util.ArrayList<>();
        for (String part : raw.split("[|,、，]")) {
            String t = part.trim();
            if (!t.isEmpty() && labels.size() < 32) {
                labels.add(t);
            }
        }
        if (labels.isEmpty()) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§c[OpenDreamCore] §f页签标签不能为空（用 | 或 , 分隔多个）"), false);
            return;
        }
        WorldEditor.get().pushWorldUndo("页签标签", "tablabels", List.of(tabsEl.id()));
        Map<String, Object> tabs = new java.util.LinkedHashMap<>();
        if (tabsEl.props().get("tabs") instanceof Map<?, ?> tm) {
            tm.forEach((k, v) -> tabs.put(String.valueOf(k), v));
        }
        tabs.put("options", labels);
        tabsEl.props().put("tabs", tabs);
        // 激活页签不在新标签中 → 回退到定义值（避免按旧页签过滤全隐藏）
        String pid = worldPage.id() == null ? "world" : worldPage.id();
        String curTab = worldTab.get(pid);
        if (curTab != null && !labels.contains(curTab)) {
            worldTab.remove(pid);
        }
        String joined = String.join("|", labels);
        WorldEditor.get().worldEditProps.computeIfAbsent(tabsEl.id(), k -> new ConcurrentHashMap<>())
                .put("tabs.options", joined);
        refreshCreateBlock(tabsEl.id());
        invalidateLayout(worldPage);
        worldNodes = layoutPage(worldPage, 800, 600);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§e[OpenDreamCore] §f页签标签: " + joined + "（保存后写回页面文件）"), false);
    }

    /** 播放音效 id（音效反馈共用：hoverSound / clickSound）。 */
    private static void playSoundId(String soundId, float volume, float pitch) {
        if (soundId == null || soundId.isBlank() || "null".equals(soundId)) {
            return;
        }
        try {
            var sound = UiRenderer.soundEvent(
                    net.minecraft.resources.ResourceLocation.tryParse(soundId));
            Minecraft mc = Minecraft.getInstance();
            if (sound != null && mc.player != null) {
                mc.player.playSound(sound, volume, pitch);
            }
        } catch (Exception ignored) {
            // 音效解析失败不打断交互
        }
    }

    /** 悬停音效反馈：options.world.hoverSound（字符串或 {sound, volume, pitch}），悬停新元素时播放。 */
    void playWorldHoverSound() {
        Object worldOpt = worldPage == null ? null : worldPage.options().get("world");
        if (!(worldOpt instanceof Map<?, ?> w)) {
            return;
        }
        Object hs = w.get("hoverSound");
        if (hs == null) {
            return;
        }
        String soundId;
        float volume = 1.0F, pitch = 2.0F;
        if (hs instanceof Map<?, ?> m) {
            Object s = m.get("sound");
            if (s == null) {
                return;
            }
            soundId = String.valueOf(s);
            volume = (float) numOf(m.get("volume"), 1.0);
            pitch = (float) numOf(m.get("pitch"), 2.0);
        } else {
            soundId = String.valueOf(hs);
        }
        if (soundId.isBlank() || "null".equals(soundId)) {
            return;
        }
        playSoundId(soundId, volume, pitch);
    }

    /** 元素级点击音效（hologram.clickSound：字符串或 {sound, volume, pitch}）。 */
    void playWorldElementClickSound(RenderNode node) {
        Object raw = node == null ? null : node.props().get("hologram");
        if (!(raw instanceof Map<?, ?> holo)) {
            return;
        }
        Object cs = holo.get("clickSound");
        if (cs == null) {
            return;
        }
        String soundId;
        float volume = 1.0F, pitch = 1.0F;
        if (cs instanceof Map<?, ?> m) {
            Object s = m.get("sound");
            if (s == null) {
                return;
            }
            soundId = String.valueOf(s);
            volume = (float) numOf(m.get("volume"), 1.0);
            pitch = (float) numOf(m.get("pitch"), 1.0);
        } else {
            soundId = String.valueOf(cs);
        }
        playSoundId(soundId, volume, pitch);
    }

    /** 悬停世界元素 → 光标（hologram.cursor: hand/cross/move/text，缺省手型）；离开恢复默认。 */
    void setWorldCursor(RenderNode node) {
        try {
            long window = Minecraft.getInstance().getWindow().getWindow();
            if (node == null) {
                org.lwjgl.glfw.GLFW.glfwSetCursor(window, 0);
                return;
            }
            String style = "";
            Object raw = node.props().get("hologram");
            if (raw instanceof Map<?, ?> holo && holo.get("cursor") != null) {
                style = String.valueOf(holo.get("cursor"));
            }
            long cursor;
            switch (style) {
                case "cross", "move" -> {
                    if (WorldEditor.get().worldCrossCursor == -1) {
                        WorldEditor.get().worldCrossCursor = org.lwjgl.glfw.GLFW.glfwCreateStandardCursor(
                                org.lwjgl.glfw.GLFW.GLFW_CROSSHAIR_CURSOR);
                    }
                    cursor = WorldEditor.get().worldCrossCursor;
                }
                case "text" -> {
                    if (WorldEditor.get().worldIbeamCursor == -1) {
                        WorldEditor.get().worldIbeamCursor = org.lwjgl.glfw.GLFW.glfwCreateStandardCursor(
                                org.lwjgl.glfw.GLFW.GLFW_IBEAM_CURSOR);
                    }
                    cursor = WorldEditor.get().worldIbeamCursor;
                }
                default -> {
                    if (WorldEditor.get().worldHandCursor == -1) {
                        WorldEditor.get().worldHandCursor = org.lwjgl.glfw.GLFW.glfwCreateStandardCursor(
                                org.lwjgl.glfw.GLFW.GLFW_HAND_CURSOR);
                    }
                    cursor = WorldEditor.get().worldHandCursor;
                }
            }
            org.lwjgl.glfw.GLFW.glfwSetCursor(window, cursor);
        } catch (Throwable ignored) {
            // 光标设置失败不影响交互
        }
    }

    /** 滑块拖拽跟随：射线与过元素中心平面求交 → 局部 X → 比例 → 值（实时显示）。 */

    /** 滑块松手：上报 INPUT 数值（服务端裁决 / 本地 input 脚本）。 */

    /** 世界渲染回调（RenderLevelStageEvent 里调用）：多面板同屏，逐面板独立渲染。 */

    public void renderWorld(net.minecraft.client.Camera camera, float partialTick) {
        if (worldPanels.isEmpty()) {
            return;
        }
        tickWorldFlowClock(); // 流光动画时钟（K 暂停时冻结）
        long now = System.currentTimeMillis();
        boolean tickDue = now - lastWorldTickAt > 1000;
        if (tickDue) {
            lastWorldTickAt = now;
        }
        updateWorldPanelAnchors(camera); // 各面板锚点：相对/绝对/固定/平滑跟随
        for (WorldPanel panel : worldPanels) {
            String pid = panel.page.id() == null ? "world" : panel.page.id();
            // 自动呼吸（hologram.breathe: true / {amplitude, speed} → 合成 breathe 预设动画）
            Map<String, Object> animOptions = panel.page.options();
            Map<String, Object> breatheAnim = null;
            for (RenderNode node : panel.nodes) {
                Object raw = node.props().get("hologram");
                if (!(raw instanceof Map<?, ?> holo)) {
                    continue;
                }
                Object b = holo.get("breathe");
                if (b == null || Boolean.FALSE.equals(b)) {
                    continue;
                }
                if (breatheAnim == null) {
                    breatheAnim = new java.util.LinkedHashMap<>();
                }
                Map<String, Object> def = new java.util.LinkedHashMap<>();
                def.put("preset", "breathe");
                if (b instanceof Map<?, ?> bm) {
                    double amp = UiRenderer.num(bm.get("amplitude"), 0.06);
                    double speed = UiRenderer.num(bm.get("speed"), 1.0);
                    if (amp > 0) {
                        def.put("to", 1 + amp);
                    }
                    if (speed > 0 && speed != 1) {
                        def.put("duration", (int) (1500 / speed));
                    }
                }
                breatheAnim.put(node.id(), List.of(def));
            }
            if (breatheAnim != null) {
                animOptions = new java.util.LinkedHashMap<>(panel.page.options());
                if (panel.page.options().get("animations") instanceof Map<?, ?> existing) {
                    Map<String, Object> merged = new java.util.LinkedHashMap<>();
                    existing.forEach((k, v) -> merged.put(String.valueOf(k), v));
                    merged.putAll(breatheAnim);
                    animOptions.put("animations", merged);
                } else {
                    animOptions.put("animations", breatheAnim);
                }
            }
            // 世界页面动画注册（自动播放 + 命名动画，服务端 ui_animation 远程触发）
            AnimationEngine.get().tick(pid, animOptions, panel.page.variables());
            // 世界页面 tick 生命周期（每秒，各面板独立）
            if (tickDue) {
                runLifecycle(panel.page, "tick");
            }
            // 页签切换过渡：260ms easeOutCubic 淡入（带 tab 的元素，按面板）
            double tabReveal = 1.0;
            long switchAt = panel.tabSwitchAt;
            if (switchAt > 0) {
                double p = (now - switchAt) / 260.0;
                if (p < 1.0) {
                    p = Math.max(0, p);
                    tabReveal = 1 - (1 - p) * (1 - p) * (1 - p);
                }
            }
            try {
                WorldHologram.render(panel.nodes, panel.page.options(), camera, partialTick, panel.hoverId,
                        pid, panel.page.variables(),
                        worldDragOffsets.isEmpty() ? null : worldDragOffsets,
                        worldPage == panel.page ? (WorldEditor.get().worldEditPreview ? null : WorldEditor.get().worldEditSelected) : null,
                        worldTabActive(pid), tabReveal, panel.anchor);
            } catch (Throwable panelDrawFail) {
                // 面板绘制中断可能把 Tesselator 留在 building 态，立即闭合防止污染原版渲染
                LOGGER.warn("世界面板 {} 绘制异常: {}", pid, panelDrawFail.toString());
                forceCloseTesselator();
            }
            // 面板标题小字（billboard，面板包围盒上方居中；无标题/空跳过；编辑模式隐藏避免遮挡；
            // options.titleLabel: false 可关闭；title 支持 ${vars.xx}/{{vars.xx}} 插值 → 动态面板名）
            String pTitleRaw = panel.page.title();
            Object tlOpt = panel.page.options() == null ? null : panel.page.options().get("titleLabel");
            boolean titleVisible = pTitleRaw != null && !pTitleRaw.isBlank()
                    && !"false".equalsIgnoreCase(String.valueOf(tlOpt))
                    && !(WorldEditor.get().worldEditMode && panel.page == worldPage);
            if (titleVisible) {
                String pTitle = UiRenderer.interpolate(null, pTitleRaw, panel.page.variables());
                if (pTitle == null || pTitle.isBlank()) {
                    pTitle = pTitleRaw;
                }
                double[] pb = WorldHologram.visibleBounds(panel.nodes, worldTabActive(pid),
                        panel.page.variables());
                double tx = pb == null ? 0 : (pb[0] + pb[2]) / 2;
                double ty = pb == null ? 0.45 : pb[1] - 0.4;
                double tw = pb == null ? 1.0 : Math.max(0.5, pb[2] - pb[0]);
                WorldHologram.renderPanelTitle(camera, panel.page.options(), panel.anchor,
                        tx, ty, 0, tw, pTitle);
            }
        }
        // 编辑模式叠加层（聚焦面板）：对齐参考线 / 涟漪 / 幽灵影 / 三手柄（干净预览 I 时隐藏）
        if (!WorldEditor.get().worldEditPreview && worldNodes != null && worldPage != null) {
            // 镜像翻转 ghost 预览（对齐屏悬停镜像按钮：半透明轮廓显示翻转结果位置；x/y 可同时）
            if (WorldEditor.get().worldEditMode && !worldMirrorPreviewAxes.isEmpty() && WorldEditor.get().worldEditSelected != null) {
                List<String> members;
                String grp = worldGroupOf(WorldEditor.get().worldEditSelected);
                if (grp != null && worldGroupMembers(grp).size() > 1) {
                    members = worldGroupMembers(grp);
                } else if (worldEditMulti.size() >= 2) {
                    members = new java.util.ArrayList<>(worldEditMulti);
                } else {
                    members = List.of(WorldEditor.get().worldEditSelected);
                }
                java.util.List<double[]> boxes = worldMemberBoxes(members);
                if (!boxes.isEmpty()) {
                    double[] bounds = WorldHologram.visibleBounds(worldNodes,
                            worldTabActive(worldPage.id()), worldPage.variables());
                    if (bounds != null) {
                        for (String axis : worldMirrorPreviewAxes) {
                            double center = "x".equals(axis)
                                    ? (bounds[0] + bounds[2]) / 2 : (bounds[1] + bounds[3]) / 2;
                            WorldHoloEdit.renderMirrorPreview(camera, worldPage.options(),
                                    focusedWorldAnchor(camera), boxes, center, "x".equals(axis));
                        }
                    }
                }
            }
            // 分布 ghost 预览（对齐屏悬停 dist_x/dist_y：青色轮廓显示分布后位置）
            if (WorldEditor.get().worldEditMode && WorldEditor.get().worldDistributeGhost != null) {
                WorldHoloEdit.renderGhostBoxes(camera, worldPage.options(),
                        focusedWorldAnchor(camera), WorldEditor.get().worldDistributeGhost);
            }
            // 跨面板参考预览（跨面模式悬停模式按钮：参考面板选中元素位置框）
            if (WorldEditor.get().worldEditMode && WorldEditor.get().worldCrossPreview != null) {
                WorldHoloEdit.renderGhostBoxes(camera, worldPage.options(),
                        focusedWorldAnchor(camera), java.util.List.of(WorldEditor.get().worldCrossPreview));
            }
            // 跨面板参考锚点预览（跨面模式悬停：参考面板锚点十字）
            if (WorldEditor.get().worldEditMode && WorldEditor.get().worldCrossAnchorPreview != null) {
                net.minecraft.world.phys.Vec3 baseA = focusedWorldAnchor(camera);
                WorldHoloEdit.renderAnchorMarker(camera, worldPage.options(),
                        new net.minecraft.world.phys.Vec3(
                                baseA.x + WorldEditor.get().worldCrossAnchorPreview[0],
                                baseA.y + WorldEditor.get().worldCrossAnchorPreview[1],
                                baseA.z + WorldEditor.get().worldCrossAnchorPreview[2]));
            }
            // 对齐参考包围盒预览（悬停对齐模式：琥珀高亮可见包围盒）
            if (WorldEditor.get().worldEditMode && WorldEditor.get().worldAlignBoundsPreview != null) {
                WorldHoloEdit.renderSelectionBounds(camera, worldPage.options(),
                        focusedWorldAnchor(camera), WorldEditor.get().worldAlignBoundsPreview[0], WorldEditor.get().worldAlignBoundsPreview[1],
                        WorldEditor.get().worldAlignBoundsPreview[2], WorldEditor.get().worldAlignBoundsPreview[3]);
            }
            // 锚点指示（编辑模式：聚焦面板锚点十字，x/y = 0 的原点位置）
            if (WorldEditor.get().worldEditMode) {
                WorldHoloEdit.renderAnchorMarker(camera, worldPage.options(), focusedWorldAnchor(camera));
            }
            // 面板锁定标记（编辑模式：面板整体锁定 → 锚点右上方琥珀锁块）
            if (WorldEditor.get().worldEditMode && worldPanelLocked()) {
                WorldHoloEdit.renderLockMarker(camera, worldPage.options(),
                        focusedWorldAnchor(camera), 0, 0, 0.7, 0.7);
            }
            // 锁定角标（编辑模式：组/多选/单选 中所有锁定元素右上角琥珀小方块）
            if (WorldEditor.get().worldEditMode && WorldEditor.get().worldEditSelected != null && worldPage != null) {
                java.util.List<String> lockTargets = new java.util.ArrayList<>();
                String grpL = worldGroupOf(WorldEditor.get().worldEditSelected);
                if (grpL != null && worldGroupMembers(grpL).size() > 1) {
                    lockTargets.addAll(worldGroupMembers(grpL));
                } else if (worldEditMulti.size() >= 2) {
                    lockTargets.addAll(worldEditMulti);
                } else {
                    lockTargets.add(WorldEditor.get().worldEditSelected);
                }
                for (String idL : lockTargets) {
                    if (!worldElementLocked(idL)) {
                        continue;
                    }
                    var elL = findElement(worldPage, idL);
                    if (elL == null) {
                        continue;
                    }
                    Object rawL = elL.props().get("hologram");
                    if (rawL instanceof Map<?, ?> hL) {
                        var varsL = worldPage.variables();
                        String tL = String.valueOf(elL.props().get("type"));
                        double lx = WorldHologram.holoNum(hL, "x", 0, varsL);
                        double ly = WorldHologram.holoNum(hL, "y", 0, varsL);
                        double lw = WorldHologram.holoNum(hL, "width", "text".equals(tL) ? 2.0 : 1.0, varsL);
                        double lh = WorldHologram.holoNum(hL, "height", "text".equals(tL) ? 0.25 : 1.0, varsL);
                        WorldHoloEdit.renderLockMarker(camera, worldPage.options(),
                                focusedWorldAnchor(camera), lx, ly, lw, lh);
                    }
                }
            }
            // 淡出范围可视化（编辑模式：聚焦面板琥珀/红双圈 + 其余面板淡灰圈）
            if (WorldEditor.get().worldEditMode) {
                WorldHoloEdit.renderFadeRange(camera, worldPage.options(), focusedWorldAnchor(camera), false);
                for (WorldPanel other : worldPanels) {
                    if (other.page == worldPage) {
                        continue;
                    }
                    net.minecraft.world.phys.Vec3 oa = other.anchor != null ? other.anchor
                            : WorldHologram.anchorFor(camera, other.page.options());
                    WorldHoloEdit.renderFadeRange(camera, other.page.options(), oa, true);
                }
            }
            // 编辑网格（G 键切换：锚点平面网格，步长 = 吸附值或 0.25）
            if (WorldEditor.get().worldEditMode && WorldEditor.get().worldEditGrid) {
                WorldHoloEdit.renderEditGrid(camera, worldPage.options(),
                        focusedWorldAnchor(camera), WorldEditor.get().worldEditGridStep());
            }
            // 拖拽距离标注（拖拽中：到最近可见元素中心的间距，HUD 阶段画连线+数值）
            WorldEditor.get().worldDistAnno = null;
            if (WorldEditor.get().worldEditMode && WorldEditor.get().worldDragId != null && WorldEditor.get().worldDragBase != null) {
                String pkey = worldPage.id() == null ? "world" : worldPage.id();
                double[] off = worldDragOffsets.get(wkey(pkey, WorldEditor.get().worldDragId));
                if (off != null) {
                    double dragX = WorldEditor.get().worldDragBase.x + off[0];
                    double dragY = WorldEditor.get().worldDragBase.y + off[1];
                    double dragZ = WorldEditor.get().worldDragBase.z + off[2];
                    java.util.Set<String> skipSet = new java.util.HashSet<>();
                    String grp = worldGroupOf(WorldEditor.get().worldDragId);
                    if (grp != null) {
                        skipSet.addAll(worldGroupMembers(grp));
                    }
                    if (worldEditMulti.size() >= 2 && worldEditMulti.contains(WorldEditor.get().worldDragId)) {
                        skipSet.addAll(worldEditMulti);
                    }
                    skipSet.add(WorldEditor.get().worldDragId);
                    List<double[]> centers = new java.util.ArrayList<>();
                    WorldEditor.get().collectWorldCenters(worldNodes, worldTabActive(worldPage.id()),
                            worldPage.variables(), null, skipSet, centers);
                    double best = Double.MAX_VALUE;
                    double[] bestC = null;
                    for (double[] c : centers) {
                        double dx = c[0] - dragX;
                        double dy = c[1] - dragY;
                        double d = dx * dx + dy * dy;
                        if (d < best) {
                            best = d;
                            bestC = c;
                        }
                    }
                    if (bestC != null) {
                        net.minecraft.world.phys.Vec3 anchor = focusedWorldAnchor(camera);
                        WorldEditor.get().worldDistAnno = new double[]{anchor.x + dragX, anchor.y + dragY, anchor.z + dragZ,
                                anchor.x + bestC[0], anchor.y + bestC[1], anchor.z + dragZ,
                                Math.sqrt(best)};
                    }
                }
            }
            // 吸附磁吸圈（拖拽中：拖拽元素中心 ±tol 圆环，提示磁吸范围）
            if (WorldEditor.get().worldEditMode && WorldEditor.get().worldDragId != null && WorldEditor.get().worldDragBase != null) {
                String pkey = worldPage.id() == null ? "world" : worldPage.id();
                double[] off = worldDragOffsets.get(wkey(pkey, WorldEditor.get().worldDragId));
                if (off != null) {
                    net.minecraft.world.phys.Vec3 anchor = focusedWorldAnchor(camera);
                    WorldHoloEdit.renderMagnetRing(camera, worldPage.options(), anchor,
                            WorldEditor.get().worldDragBase.x + off[0] - anchor.x,
                            WorldEditor.get().worldDragBase.y + off[1] - anchor.y, 0.06);
                }
            }
            // 多选/框选预览包围盒（选中集边界框：琥珀描边 + 淡填充，实时跟随框选）
            java.util.Set<String> boundsSet = worldEditMulti.size() >= 2 ? worldEditMulti : worldMarqueePreview;
            if (WorldEditor.get().worldEditMode && boundsSet.size() >= 2) {
                double[] bb = worldSelectionBounds(boundsSet);
                if (bb != null) {
                    WorldHoloEdit.renderSelectionBounds(camera, worldPage.options(),
                            focusedWorldAnchor(camera), bb[0], bb[1], bb[2], bb[3]);
                }
            }
            // 编辑模式对齐参考线（拖拽吸附时显示；Shift 锁轴时被锁轴参考线增粗提亮）
            if (WorldEditor.get().worldEditMode && WorldEditor.get().worldDragGuides != null) {
                WorldHoloEdit.renderDragGuides(camera, worldPage.options(), WorldEditor.get().worldDragGuides,
                        focusedWorldAnchor(camera), WorldEditor.get().worldDragLockAxis);
            }
            // 点击涟漪（元素点击反馈，400ms 衰减）
            if (!WorldEditor.get().worldRipples.isEmpty()) {
                long nowMs = System.currentTimeMillis();
                WorldEditor.get().worldRipples.removeIf(r -> nowMs - (long) r[3] > 400);
                if (!WorldEditor.get().worldRipples.isEmpty()) {
                    WorldHoloEdit.renderRipples(camera, worldPage.options(), WorldEditor.get().worldRipples);
                }
            }
            // 拖拽幽灵影（编辑模式：原位置半透明框，拖到哪都能看到起点）
            if (WorldEditor.get().worldEditMode && WorldEditor.get().worldDragId != null && WorldEditor.get().worldDragBase != null) {
                RenderNode dragging = findWorldNode(WorldEditor.get().worldDragId);
                if (dragging != null) {
                    Object raw = dragging.props().get("hologram");
                    Map<?, ?> holo = raw instanceof Map<?, ?> h ? h : Map.of();
                    var vars = worldPage.variables();
                    double gw = WorldHologram.holoNum(holo, "width",
                            "text".equals(dragging.type()) ? 2.0 : 1.0, vars);
                    double gh = WorldHologram.holoNum(holo, "height",
                            "text".equals(dragging.type()) ? 0.25 : 1.0, vars);
                    WorldHoloEdit.renderDragGhost(camera, worldPage.options(), WorldEditor.get().worldDragBase, gw, gh);
                }
            }
            // 拖入创建幽灵影（按住类型 chip 拖拽中：落点预览框）
            if (WorldEditor.get().worldEditMode && WorldEditor.get().worldTypeDrag != null && WorldEditor.get().worldTypeDragMoved && WorldEditor.get().worldTypeDropPoint != null) {
                Map<String, Object> holo = defaultWorldHolo(WorldEditor.get().worldTypeDrag);
                double gw = holo.get("width") instanceof Number n ? n.doubleValue() : 2.0;
                double gh = holo.get("height") instanceof Number n ? n.doubleValue() : 0.5;
                WorldHoloEdit.renderDragGhost(camera, worldPage.options(), WorldEditor.get().worldTypeDropPoint, gw, gh);
            }
            // 旋转手柄（编辑模式选中元素顶部：圆形手柄 + 连线，拖拽旋转）
            if (WorldEditor.get().worldEditMode && WorldEditor.get().worldEditSelected != null) {
                double[][] pair = rotateHandleWorld(camera, Minecraft.getInstance());
                if (pair != null) {
                    WorldHoloEdit.renderRotateHandle(camera, worldPage.options(), pair[0], pair[1]);
                }
            }
            // 缩放手柄（编辑模式选中元素右下角方块，拖拽改尺寸）
            if (WorldEditor.get().worldEditMode && WorldEditor.get().worldEditSelected != null) {
                double[][] pair = resizeHandleWorld(camera, Minecraft.getInstance());
                if (pair != null) {
                    WorldHoloEdit.renderResizeHandle(camera, worldPage.options(), pair[0], pair[1]);
                }
            }
            // 描边手柄（编辑模式选中元素左边缘菱形，拖拽调边框宽；仅元素带 hologram.border 时出现）
            if (WorldEditor.get().worldEditMode && WorldEditor.get().worldEditSelected != null) {
                double[][] pair = WorldEditor.get().borderHandleWorld(camera, Minecraft.getInstance());
                if (pair != null) {
                    WorldHoloEdit.renderBorderHandle(camera, worldPage.options(), pair[0], pair[1],
                            worldBorderColorOf(findWorldNode(WorldEditor.get().worldEditSelected)));
                }
            }
        forceCloseTesselator();
        }
        WorldEditor.get().tickWorldInteraction(camera);

    }

    // 强制闭合可能残留的 Tesselator 缓冲（防止 "Already building!" 崩溃级联）
    private static void forceCloseTesselator() {
        // 按签名而非方法名反射：生产环境是 SRG 名，Mojmap 名匹配不到等于没修。
        // builder 获取器 = 无参且返回 BufferBuilder；building 探测 = BufferBuilder 上无参返回 boolean 的方法；
        // end = BufferBuilder 上无参 void 方法中调用后 building 变 false 的那个（逐个试探）。
        try {
            var t = com.mojang.blaze3d.vertex.Tesselator.getInstance();
            Object builder = null;
            for (var m : t.getClass().getMethods()) {
                if (m.getParameterCount() == 0 && m.getReturnType().getSimpleName().equals("BufferBuilder")) {
                    builder = m.invoke(t);
                    break;
                }
            }
            if (builder == null) {
                return;
            }
            Boolean building = null;
            java.lang.reflect.Method probe = null;
            for (var m : builder.getClass().getMethods()) {
                if (m.getParameterCount() == 0 && m.getReturnType() == boolean.class) {
                    boolean r;
                    try {
                        r = (Boolean) m.invoke(builder);
                    } catch (Throwable continueProbe) {
                        continue;
                    }
                    // 首个无副作用可调用的 boolean 方法视为 building 探针
                    building = r;
                    probe = m;
                    break;
                }
            }
            if (!Boolean.TRUE.equals(building)) {
                return;
            }
            for (var m : builder.getClass().getMethods()) {
                if (m.getParameterCount() == 0 && m.getReturnType() == void.class) {
                    try {
                        m.invoke(builder);
                        Boolean after = (Boolean) probe.invoke(builder);
                        if (!after) {
                            LOGGER.warn("已强制闭合残留的渲染缓冲");
                            return;
                        }
                    } catch (Throwable tryNext) {
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }


    /**
     * 屏幕外箭头（HUD 渲染阶段调用）：world.offScreenArrows: true 时，
     * 世界面板元素不在屏幕内 → 屏幕边缘画箭头指向其方向。
     * 投影：世界点 → 相机空间（相机旋转矩阵）→ NDC（投影矩阵）→ 屏幕坐标。
     */
    public void renderWorldArrows(net.minecraft.client.gui.GuiGraphics g, net.minecraft.client.Camera camera) {
        if (worldPanels.isEmpty() || Minecraft.getInstance().player == null) {
            WorldEditor.get().toolbarVisible = false;
            return;
        }
        // 编辑模式工具栏（HUD 阶段，先于箭头绘制）
        if (WorldEditor.get().worldEditMode) {
            renderWorldEditToolbar(g);
            renderWorldContextMenu(g);
        } else {
            WorldEditor.get().toolbarVisible = false;
        }
        // 拖拽距离标注（编辑模式拖拽中：最近元素连线 + 间距数值）
        if (WorldEditor.get().worldEditMode && WorldEditor.get().worldDragId != null && WorldEditor.get().worldDistAnno != null) {
            var mc = Minecraft.getInstance();
            var window = mc.getWindow();
            double scaledW = window.getGuiScaledWidth();
            double scaledH = window.getGuiScaledHeight();
            double[] anno = WorldEditor.get().worldDistAnno;
            double[] pa = project(camera, new net.minecraft.world.phys.Vec3(anno[0], anno[1], anno[2]),
                    scaledW, scaledH);
            double[] pb = project(camera, new net.minecraft.world.phys.Vec3(anno[3], anno[4], anno[5]),
                    scaledW, scaledH);
            if (pa != null && pb != null) {
                drawLine2D(g, pa[0], pa[1], pb[0], pb[1], 0x90FFE082);
                String label = String.format(java.util.Locale.ROOT, "%.2f", anno[6]);
                int mx = (int) ((pa[0] + pb[0]) / 2);
                int my = (int) ((pa[1] + pb[1]) / 2);
                int tw = mc.font.width(label);
                g.fill(mx - 3, my - 3, mx + tw + 3, my + 7, 0xCC10151F);
                g.fill(mx - 3, my - 3, mx + tw + 3, my - 2, 0xFF42A5F5);
                g.drawString(mc.font, label, mx, my - 3, 0xFFFFE082);
            }
        }
        // z 拖拽可视化：层级指示条（Z+拖 时显示，各元素 z 刻度 + 拖动元素实时位置）
        if (WorldEditor.get().worldEditMode && WorldEditor.get().worldZScrubId != null) {
            renderZIndicator(g);
        }
        // O 拖拽可视化：透明度指示条（上下拖动实时百分比）
        if (WorldEditor.get().worldEditMode && WorldEditor.get().worldOpacityScrubId != null) {
            renderOpacityIndicator(g);
        }
        // 流光暂停徽标（K 暂停时右上角常驻提示 + 微调组合键速查）
        if (WorldEditor.get().worldEditMode && worldFlowPaused) {
            var mc = Minecraft.getInstance();
            String label = "⏸ 流光暂停 (K)";
            int x = mc.getWindow().getGuiScaledWidth() - mc.font.width(label) - 14;
            g.fill(x - 4, 12, x + mc.font.width(label) + 4, 26, 0xCC10151F);
            g.fill(x - 4, 12, x + mc.font.width(label) + 4, 14, 0xFFFFB300);
            g.drawString(mc.font, label, x, 15, 0xFFFFE082);
            // 组合键速查（暂停定帧中可用的微调）
            String[] combos = {"Ctrl+,/. 速度", "Shift+,/. 相位", "Alt+,/. 段长",
                    "Ctrl+Shift+,/. 间距", "Ctrl+Alt+,/. 宽度", "Ctrl+Shift+Alt+,/. 副相", "9/0 段数"};
            int yy = 30;
            for (String combo : combos) {
                int lx = mc.getWindow().getGuiScaledWidth() - mc.font.width(combo) - 14;
                g.fill(lx - 4, yy, lx + mc.font.width(combo) + 4, yy + 10, 0xAA10151F);
                g.drawString(mc.font, combo, lx, yy + 1, 0xFFB0BEC5);
                yy += 12;
            }
        }
        // 吸附参考线计数（拖拽吸附中：元素上方显示当前吸附线数量）
        if (WorldEditor.get().worldEditMode && WorldEditor.get().worldDragId != null && WorldEditor.get().worldDragGuides != null) {
            int count = 0;
            if (!Double.isNaN(WorldEditor.get().worldDragGuides[0])) {
                count++;
            }
            if (!Double.isNaN(WorldEditor.get().worldDragGuides[1])) {
                count++;
            }
            if (count > 0 && WorldEditor.get().worldDragBase != null) {
                var mc = Minecraft.getInstance();
                String pkey = worldPage.id() == null ? "world" : worldPage.id();
                double[] off = worldDragOffsets.get(wkey(pkey, WorldEditor.get().worldDragId));
                String label = "吸附 ×" + count;
                renderHandleReadout(g, camera, label, new net.minecraft.world.phys.Vec3(
                        WorldEditor.get().worldDragBase.x + (off == null ? 0 : off[0]),
                        WorldEditor.get().worldDragBase.y + (off == null ? 0 : off[1]),
                        WorldEditor.get().worldDragBase.z + (off == null ? 0 : off[2])));
            }
        }
        // 流光微调读数（按住组合键或 9/0 微调时，元素上方实时显示当前值）
        if (WorldEditor.get().worldEditMode && WorldEditor.get().worldEditSelected != null
                && (WorldEditor.get().worldFlowSpeedHoldTicks > 0 || WorldEditor.get().worldFlowPhaseHoldTicks > 0
                || WorldEditor.get().worldFlowSegHoldTicks > 0 || WorldEditor.get().worldFlowGapHoldTicks > 0
                || WorldEditor.get().worldBorderWidthHoldTicks > 0 || WorldEditor.get().worldFlowPhase2HoldTicks > 0
                || WorldEditor.get().worldFlowSegmentsHoldTicks > 0)) {
            RenderNode fn = findWorldNode(WorldEditor.get().worldEditSelected);
            if (fn != null) {
                Object raw = fn.props().get("hologram");
                if (raw instanceof Map<?, ?> holo) {
                    Map<?, ?> bm = holo.get("border") instanceof Map<?, ?> m ? m : Map.of();
                    long spd = bm.get("flowSpeed") instanceof Number n ? n.longValue() : 1200;
                    if (spd <= 0) {
                        spd = 1200;
                    }
                    double ph = bm.get("flowPhase") instanceof Number np ? np.doubleValue() : 0;
                    double ph2 = bm.get("flowPhase2") instanceof Number np2 ? np2.doubleValue() : 0;
                    double sg = bm.get("flowSeg") instanceof Number ns ? ns.doubleValue() : 0;
                    double gp = bm.get("flowSegGap") instanceof Number ng ? ng.doubleValue() : 0;
                    double wd = bm.get("width") instanceof Number nw ? nw.doubleValue() : 0.02;
                    int segN = bm.get("flowSegments") instanceof Number nn ? nn.intValue() : 0;
                    String label = "速 " + spd + "ms 相 " + Math.round(ph * 100) + "% 长 "
                            + (sg > 0 ? String.format(java.util.Locale.ROOT, "%.2f", sg) : "auto")
                            + " 距 " + (gp > 0 ? String.format(java.util.Locale.ROOT, "%.2f", gp) : "auto")
                            + " 宽 " + String.format(java.util.Locale.ROOT, "%.3f", wd)
                            + " 段 " + (segN > 0 ? segN : "auto");
                    if (ph2 > 0) {
                        label += " 副相 " + Math.round(ph2 * 100) + "%";
                    }
                    renderHandleReadout(g, camera, label, worldElementCenter(fn, camera));
                }
            }
        }
        // 淡出实时读数（编辑模式：fadeDistance > 0 时锚点上方显示当前距离 + 透明度）
        if (WorldEditor.get().worldEditMode && worldPage != null) {
            Object worldOpt = worldPage.options().get("world");
            if (worldOpt instanceof Map<?, ?> w && w.get("fadeDistance") instanceof Number n
                    && n.doubleValue() > 0) {
                double fd = n.doubleValue();
                double range = w.get("fadeRange") instanceof Number nr ? nr.doubleValue() : 3;
                double dist = Minecraft.getInstance().player.position().distanceTo(focusedWorldAnchor(camera));
                double fade = 1.0;
                if (dist > fd) {
                    fade = Math.max(0, 1 - (dist - fd) / Math.max(range, 0.1));
                }
                String label = String.format(java.util.Locale.ROOT,
                        "距 %.1fm 透明 %d%%", dist, (int) Math.round(fade * 100));
                renderHandleReadout(g, camera, label, focusedWorldAnchor(camera));
            }
        }
        // 锚点世界坐标读数（编辑模式：锚点下方 XYZ，供写绝对锚点参考）
        if (WorldEditor.get().worldEditMode && worldPage != null) {
            net.minecraft.world.phys.Vec3 anc = focusedWorldAnchor(camera);
            String label = String.format(java.util.Locale.ROOT, "XYZ %.1f %.1f %.1f",
                    anc.x, anc.y, anc.z);
            renderHandleReadout(g, camera, label, anc, 6);
        }
        // 锚点模式徽标（编辑模式：锚点上方常显 跟随/固定/平滑，P 切换即见）
        if (WorldEditor.get().worldEditMode && worldPage != null) {
            Object worldObj = worldPage.options().get("world");
            String mode = "跟随";
            if (worldObj instanceof Map<?, ?> w) {
                boolean follow = w.get("follow") == null
                        || Boolean.parseBoolean(String.valueOf(w.get("follow")));
                boolean smooth = w.get("smooth") instanceof Number n && n.doubleValue() > 0;
                mode = !follow ? "固定" : (smooth ? "平滑" : "跟随");
            }
            renderHandleReadout(g, camera, "锚:" + mode, focusedWorldAnchor(camera), -34);
        }
        // 锚点偏移读数（M 按住时：offsetX/Y/Z 实时值）
        if (WorldEditor.get().worldEditMode && worldPage != null && mKeyHeld(Minecraft.getInstance())) {
            Object worldObj = worldPage.options().get("world");
            double ox = 0, oy = 1.6, oz = 3;
            if (worldObj instanceof Map<?, ?> w) {
                if (w.get("offsetX") instanceof Number n) {
                    ox = n.doubleValue();
                }
                if (w.get("offsetY") instanceof Number n) {
                    oy = n.doubleValue();
                }
                if (w.get("offsetZ") instanceof Number n) {
                    oz = n.doubleValue();
                }
            }
            String label = String.format(java.util.Locale.ROOT, "off %.3f %.3f %.3f · 步 %.3f",
                    ox, oy, oz, worldAnchorStep());
            renderHandleReadout(g, camera, label, focusedWorldAnchor(camera), 28);
        }
        // 整体缩放读数（Alt 按住时：面板累计缩放比例）
        if (WorldEditor.get().worldEditMode && worldPage != null && altHeld(Minecraft.getInstance())
                && WorldEditor.get().worldPanelScaleAccum != 1.0) {
            String label = String.format(java.util.Locale.ROOT, "面板缩放 ×%.2f（Alt+滚轮）",
                    WorldEditor.get().worldPanelScaleAccum);
            renderHandleReadout(g, camera, label, focusedWorldAnchor(camera), 50);
        }
        // 整体旋转读数（Ctrl+Alt 按住时：面板累计旋转角度）
        if (WorldEditor.get().worldEditMode && worldPage != null && ctrlDown(Minecraft.getInstance())
                && altHeld(Minecraft.getInstance()) && WorldEditor.get().worldPanelRotateAccum != 0) {
            String label = String.format(java.util.Locale.ROOT, "面板旋转 %+.0f°（Ctrl+Alt+滚轮）",
                    WorldEditor.get().worldPanelRotateAccum);
            renderHandleReadout(g, camera, label, focusedWorldAnchor(camera), 50);
        }
        // 属性编辑浮签（编辑屏打开时：高亮目标上方显示当前编辑属性名）
        if (WorldEditor.get().worldEditMode && !worldEditHighlight.isEmpty() && WorldEditor.get().worldEditLabel != null) {
            for (String id : worldEditHighlight) {
                RenderNode rn = findWorldNode(id);
                if (rn == null) {
                    continue;
                }
                renderHandleReadout(g, camera, "✎ " + WorldEditor.get().worldEditLabel, worldElementCenter(rn, camera));
            }
        }
        // 旋转拖拽可视化：角度指示（拖旋转手柄时实时 yaw 读数 + 圆盘指针）
        if (WorldEditor.get().worldEditMode && WorldEditor.get().worldRotateId != null) {
            renderYawIndicator(g);
        }
        // 缩放手柄尺寸读数（拖缩放手柄实时宽×高，标注在元素上方）
        if (WorldEditor.get().worldEditMode && WorldEditor.get().worldResizeId != null) {
            RenderNode rn = findWorldNode(WorldEditor.get().worldResizeId);
            if (rn != null) {
                Object raw = rn.props().get("hologram");
                Map<?, ?> holo = raw instanceof Map<?, ?> h ? h : Map.of();
                var vars = worldPage.variables();
                double w = WorldHologram.holoNum(holo, "width",
                        "text".equals(rn.type()) ? 2.0 : 1.0, vars);
                double h = WorldHologram.holoNum(holo, "height",
                        "text".equals(rn.type()) ? 0.25 : 1.0, vars);
                String label = String.format(java.util.Locale.ROOT, "%.2f × %.2f", w, h);
                renderHandleReadout(g, camera, label, worldElementCenter(rn, camera));
            }
        }
        // 描边手柄宽度读数（拖描边手柄实时宽度，标注在手柄旁）
        if (WorldEditor.get().worldEditMode && WorldEditor.get().worldBorderId != null) {
            double[][] pair = WorldEditor.get().borderHandleWorld(camera, Minecraft.getInstance());
            if (pair != null) {
                double width = worldBorderWidthOf(findWorldNode(WorldEditor.get().worldBorderId));
                if (width >= 0) {
                    String label = String.format(java.util.Locale.ROOT, "w %.3f", width);
                    renderHandleReadout(g, camera, label,
                            new net.minecraft.world.phys.Vec3(pair[1][0], pair[1][1], pair[1][2]));
                }
            }
        }
        // 描边色板（选中元素带描边时，手柄旁色板：点击改色 / 关闭描边）
        worldBorderPaletteRects.clear();
        if (WorldEditor.get().worldEditMode && WorldEditor.get().worldEditSelected != null) {
            var mc = Minecraft.getInstance();
            double[][] pair = WorldEditor.get().borderHandleWorld(camera, mc);
            if (pair != null) {
                var window = mc.getWindow();
                double scaledW = window.getGuiScaledWidth();
                double scaledH = window.getGuiScaledHeight();
                double[] p = project(camera,
                        new net.minecraft.world.phys.Vec3(pair[1][0], pair[1][1], pair[1][2]),
                        scaledW, scaledH);
                if (p != null) {
                    int sx = (int) p[0] + 10;
                    int sy = (int) p[1] - (WorldEditor.get().BORDER_PALETTE.length + 1) * 15 / 2;
                    var palEl = findElement(worldPage, WorldEditor.get().worldEditSelected);
                    for (int i = 0; i < WorldEditor.get().BORDER_PALETTE.length; i++) {
                        int y = sy + i * 15;
                        int rgb = 0xFF000000 | Integer.parseInt(WorldEditor.get().BORDER_PALETTE[i].substring(1), 16);
                        if (borderColorMatches(palEl, WorldEditor.get().BORDER_PALETTE[i])) {
                            g.fill(sx - 1, y - 1, sx + 13, y + 13, 0xFFFFFFFF);
                        }
                        g.fill(sx, y, sx + 12, y + 12, rgb);
                        g.fill(sx, y, sx + 12, y + 1, 0xFFB0BEC5);
                        g.fill(sx, y + 11, sx + 12, y + 12, 0xFF78909C);
                        worldBorderPaletteRects.add(new int[]{sx, y, sx + 12, y + 12});
                    }
                    int yOff = sy + WorldEditor.get().BORDER_PALETTE.length * 15;
                    if (palEl != null && elementBorder(palEl) == null) {
                        g.fill(sx - 1, yOff - 1, sx + 13, yOff + 13, 0xFFFFFFFF);
                    }
                    g.fill(sx, yOff, sx + 12, yOff + 12, 0xFF37474F);
                    g.fill(sx, yOff, sx + 12, yOff + 1, 0xFFE57373);
                    g.fill(sx + 5, yOff + 3, sx + 6, yOff + 9, 0xFFFFCDD2);
                    g.fill(sx + 3, yOff + 5, sx + 9, yOff + 6, 0xFFFFCDD2);
                    worldBorderPaletteRects.add(new int[]{sx, yOff, sx + 12, yOff + 12});
                    // 第二列：流光色（白色内点区分；点击 = flow:true + flowColor；Shift+点击 = flowColor2 副色）
                    int fx = sx + 17;
                    g.drawString(Minecraft.getInstance().font, "流", fx - 1, sy - 8, 0xFF90A4AE);
                    g.drawString(Minecraft.getInstance().font, "副", fx - 1, sy - 16, 0xFF78909C);
                    for (int i = 0; i < WorldEditor.get().BORDER_FLOW_PALETTE.length; i++) {
                        int y = sy + i * 15;
                        int rgb = 0xFF000000 | Integer.parseInt(WorldEditor.get().BORDER_FLOW_PALETTE[i].substring(1), 16);
                        if (borderFlowColorMatches(palEl, WorldEditor.get().BORDER_FLOW_PALETTE[i], false)
                                || borderFlowColorMatches(palEl, WorldEditor.get().BORDER_FLOW_PALETTE[i], true)) {
                            g.fill(fx - 1, y - 1, fx + 13, y + 13, 0xFFFFFFFF);
                        }
                        g.fill(fx, y, fx + 12, y + 12, rgb);
                        g.fill(fx, y, fx + 12, y + 1, 0xFFFFFFFF);
                        g.fill(fx, y + 11, fx + 12, y + 12, 0xFF90A4AE);
                        g.fill(fx + 5, y + 5, fx + 7, y + 7, 0x66FFFFFF);
                        worldBorderPaletteRects.add(new int[]{fx, y, fx + 12, y + 12});
                    }
                    // 第三列：流光速度档（慢 2400 / 中 1200 / 快 500 ms 每圈；-50/+50 微调档）
                    int vx = sx + 34;
                    g.drawString(Minecraft.getInstance().font, "速", vx - 1, sy - 8, 0xFF90A4AE);
                    g.drawString(Minecraft.getInstance().font, "微", vx - 1, sy - 16, 0xFF78909C);
                    for (int i = 0; i < WorldEditor.get().BORDER_FLOW_SPEEDS.length; i++) {
                        int y = sy + i * 15;
                        if (borderFlowSpeedOf(palEl) == WorldEditor.get().BORDER_FLOW_SPEEDS[i]) {
                            g.fill(vx - 1, y - 1, vx + 13, y + 13, 0xFFFFFFFF);
                        }
                        g.fill(vx, y, vx + 12, y + 12, 0xFF263238);
                        g.fill(vx, y, vx + 12, y + 1, 0xFFFFD54F);
                        g.drawString(Minecraft.getInstance().font, WorldEditor.get().BORDER_FLOW_SPEED_LABELS[i],
                                vx + 2, y + 2, 0xFFFFE082);
                        worldBorderPaletteRects.add(new int[]{vx, y, vx + 12, y + 12});
                    }
                    // 速度微调档：-50 / +50（点击即调，与 Ctrl+,/. 同源）
                    for (int i = 0; i < 2; i++) {
                        int y = sy + (WorldEditor.get().BORDER_FLOW_SPEEDS.length + i) * 15;
                        boolean minus = i == 0;
                        g.fill(vx, y, vx + 12, y + 12, 0xFF263238);
                        g.fill(vx, y, vx + 12, y + 1, minus ? 0xFFEF5350 : 0xFF66BB6A);
                        g.drawString(Minecraft.getInstance().font, minus ? "-" : "+",
                                vx + 4, y + 2, minus ? 0xFFFFCDD2 : 0xFFC8E6C9);
                        worldBorderPaletteRects.add(new int[]{vx, y, vx + 12, y + 12});
                    }
                    // 第四列：样式预设（实线 / 虚线 / 点线 / 双线）
                    int wx = sx + 51;
                    g.drawString(Minecraft.getInstance().font, "式", wx - 1, sy - 8, 0xFF90A4AE);
                    for (int i = 0; i < WorldEditor.get().BORDER_STYLE_LABELS.length; i++) {
                        int y = sy + i * 15;
                        if (borderStyleIdxOf(palEl) == i) {
                            g.fill(wx - 1, y - 1, wx + 13, y + 13, 0xFFFFFFFF);
                        }
                        g.fill(wx, y, wx + 12, y + 12, 0xFF1B2A38);
                        g.fill(wx, y, wx + 12, y + 1, 0xFF4FC3F7);
                        g.drawString(Minecraft.getInstance().font, WorldEditor.get().BORDER_STYLE_LABELS[i],
                                wx + 2, y + 2, 0xFFB3E5FC);
                        worldBorderPaletteRects.add(new int[]{wx, y, wx + 12, y + 12});
                    }
                    // 第五列：流光段长档（短 0.1 / 中 0.15 / 长 0.25 周长比例）
                    int qx = sx + 68;
                    g.drawString(Minecraft.getInstance().font, "长", qx - 1, sy - 8, 0xFF90A4AE);
                    for (int i = 0; i < WorldEditor.get().BORDER_FLOW_SEGS.length; i++) {
                        int y = sy + i * 15;
                        if (Math.abs(borderFlowSegOf(palEl) - WorldEditor.get().BORDER_FLOW_SEGS[i]) < 0.001) {
                            g.fill(qx - 1, y - 1, qx + 13, y + 13, 0xFFFFFFFF);
                        }
                        g.fill(qx, y, qx + 12, y + 12, 0xFF102027);
                        g.fill(qx, y, qx + 12, y + 1, 0xFFFFB300);
                        g.drawString(Minecraft.getInstance().font, WorldEditor.get().BORDER_FLOW_SEG_LABELS[i],
                                qx + 2, y + 2, 0xFFFFE082);
                        worldBorderPaletteRects.add(new int[]{qx, y, qx + 12, y + 12});
                    }
                    // 第六列：描边透明度档（淡 0.35 / 中 0.7 / 实 1.0）
                    int zx = sx + 85;
                    g.drawString(Minecraft.getInstance().font, "透", zx - 1, sy - 8, 0xFF90A4AE);
                    for (int i = 0; i < WorldEditor.get().BORDER_ALPHAS.length; i++) {
                        int y = sy + i * 15;
                        if (Math.abs(borderAlphaOf(palEl) - WorldEditor.get().BORDER_ALPHAS[i]) < 0.001) {
                            g.fill(zx - 1, y - 1, zx + 13, y + 13, 0xFFFFFFFF);
                        }
                        g.fill(zx, y, zx + 12, y + 12, 0xFF1A1A1A);
                        g.fill(zx, y, zx + 12, y + 1, 0xFFE0E0E0);
                        g.drawString(Minecraft.getInstance().font, WorldEditor.get().BORDER_ALPHA_LABELS[i],
                                zx + 2, y + 2, 0xFFCFD8DC);
                        worldBorderPaletteRects.add(new int[]{zx, y, zx + 12, y + 12});
                    }
                    // 第七列：流光渐变开关（flowGradient：段内主色→副色渐变）
                    int gx = sx + 102;
                    boolean gradOn = WorldEditor.get().worldEditSelected != null
                            && isBorderGradientOn(findElement(worldPage, WorldEditor.get().worldEditSelected));
                    g.drawString(Minecraft.getInstance().font, "渐", gx - 1, sy - 8, 0xFF90A4AE);
                    g.fill(gx, sy, gx + 12, sy + 12, gradOn ? 0xFF4A148C : 0xFF1B2A38);
                    g.fill(gx, sy, gx + 12, sy + 1, gradOn ? 0xFFCE93D8 : 0xFF90A4AE);
                    g.drawString(Minecraft.getInstance().font, gradOn ? "开" : "关",
                            gx + 2, sy + 2, gradOn ? 0xFFF3E5F5 : 0xFF90A4AE);
                    worldBorderPaletteRects.add(new int[]{gx, sy, gx + 12, sy + 12});
                    // 第八列：流光方向开关（flowReverse：反向流动）
                    int rx = sx + 119;
                    boolean revOn = WorldEditor.get().worldEditSelected != null
                            && isBorderFlowReverseOn(findElement(worldPage, WorldEditor.get().worldEditSelected));
                    g.drawString(Minecraft.getInstance().font, "向", rx - 1, sy - 8, 0xFF90A4AE);
                    g.fill(rx, sy, rx + 12, sy + 12, revOn ? 0xFF1B5E20 : 0xFF1B2A38);
                    g.fill(rx, sy, rx + 12, sy + 1, revOn ? 0xFF66BB6A : 0xFF90A4AE);
                    g.drawString(Minecraft.getInstance().font, revOn ? "逆" : "顺",
                            rx + 2, sy + 2, revOn ? 0xFFC8E6C9 : 0xFF90A4AE);
                    worldBorderPaletteRects.add(new int[]{rx, sy, rx + 12, sy + 12});
                    // 第九列：流光段数档（单/双/三 段同时流动）
                    int nx = sx + 136;
                    g.drawString(Minecraft.getInstance().font, "段", nx - 1, sy - 8, 0xFF90A4AE);
                    for (int i = 0; i < WorldEditor.get().BORDER_FLOW_SEGMENT_COUNTS.length; i++) {
                        int y = sy + i * 15;
                        g.fill(nx, y, nx + 12, y + 12, 0xFF0D1B2A);
                        g.fill(nx, y, nx + 12, y + 1, 0xFFFF7043);
                        g.drawString(Minecraft.getInstance().font, WorldEditor.get().BORDER_FLOW_SEGMENT_LABELS[i],
                                nx + 2, y + 2, 0xFFFFCCBC);
                        worldBorderPaletteRects.add(new int[]{nx, y, nx + 12, y + 12});
                    }
                    // 第十列：流光段间距档（密 0.15 / 均 等距 / 疏 0.45 周长比例）
                    int ux = sx + 153;
                    g.drawString(Minecraft.getInstance().font, "距", ux - 1, sy - 8, 0xFF90A4AE);
                    for (int i = 0; i < WorldEditor.get().BORDER_FLOW_GAPS.length; i++) {
                        int y = sy + i * 15;
                        g.fill(ux, y, ux + 12, y + 12, 0xFF14213D);
                        g.fill(ux, y, ux + 12, y + 1, 0xFF29B6F6);
                        g.drawString(Minecraft.getInstance().font, WorldEditor.get().BORDER_FLOW_GAP_LABELS[i],
                                ux + 2, y + 2, 0xFFB3E5FC);
                        worldBorderPaletteRects.add(new int[]{ux, y, ux + 12, y + 12});
                    }
                    // 第十一列：hover 加速开关（hoverBoost，与 渐/向 同款单格开关）
                    int kx = sx + 170;
                    boolean hbOn = WorldEditor.get().worldEditSelected != null
                            && isWorldFlowHoverBoostOn(findElement(worldPage, WorldEditor.get().worldEditSelected));
                    g.drawString(Minecraft.getInstance().font, "增", kx - 1, sy - 8, 0xFF90A4AE);
                    g.fill(kx, sy, kx + 12, sy + 12, hbOn ? 0xFF004D40 : 0xFF1B2A38);
                    g.fill(kx, sy, kx + 12, sy + 1, hbOn ? 0xFF80CBC4 : 0xFF90A4AE);
                    g.drawString(Minecraft.getInstance().font, hbOn ? "开" : "关",
                            kx + 2, sy + 2, hbOn ? 0xFFB2DFDB : 0xFF90A4AE);
                    worldBorderPaletteRects.add(new int[]{kx, sy, kx + 12, sy + 12});
                }
            }
        }
        // 编辑模式 Alt+悬停：属性摘要预览（id/type/全部属性，替代普通 tooltip）
        if (WorldEditor.get().worldEditMode && altHeld(Minecraft.getInstance()) && WorldEditor.get().worldHoverId != null) {
            RenderNode hovered = findWorldNode(WorldEditor.get().worldHoverId);
            if (hovered != null) {
                String summary = worldElementSummary(hovered);
                if (summary != null) {
                    drawWorldTooltip(g, Minecraft.getInstance(), summary, 0xFFE0E0E0,
                            0xEE10151F, 0xFFFFB300, 240);
                }
            }
        } else if (WorldEditor.get().worldHoverId != null) {
            RenderNode hovered = findWorldNode(WorldEditor.get().worldHoverId);
            if (hovered != null) {
                Object raw = hovered.props().get("hologram");
                if (raw instanceof Map<?, ?> h) {
                    Object tt = h.get("tooltip");
                    if (tt != null) {
                        String text = String.valueOf(tt);
                        int textColor = 0xFFE0E0E0;
                        int bg = 0xE610151F;
                        int border = 0xFF42A5F5;
                        int maxW = 200;
                        if (tt instanceof Map<?, ?> tm) {
                            Object t = tm.get("text");
                            if (t != null) {
                                text = String.valueOf(t);
                            }
                            textColor = com.opendreamcore.client.UiStyle.color(tm.get("textColor"),
                                    UiStyle.color(tm.get("color"), textColor));
                            bg = com.opendreamcore.client.UiStyle.color(tm.get("background"), bg);
                            border = com.opendreamcore.client.UiStyle.color(tm.get("border"), border);
                            if (tm.get("width") instanceof Number n) {
                                maxW = n.intValue();
                            }
                        }
                        String resolved = UiRenderer.interpolate(hovered, text, worldPage.variables());
                        if (resolved != null && !resolved.isBlank()) {
                            drawWorldTooltip(g, Minecraft.getInstance(), resolved, textColor, bg, border, maxW);
                        }
                    }
                }
            }
        }
        // 框选矩形（编辑模式拖框多选）
        if (WorldEditor.get().worldMarquee != null) {
            int mx0 = (int) Math.min(WorldEditor.get().worldMarquee[0], WorldEditor.get().worldMarquee[2]);
            int my0 = (int) Math.min(WorldEditor.get().worldMarquee[1], WorldEditor.get().worldMarquee[3]);
            int mx1 = (int) Math.max(WorldEditor.get().worldMarquee[0], WorldEditor.get().worldMarquee[2]);
            int my1 = (int) Math.max(WorldEditor.get().worldMarquee[1], WorldEditor.get().worldMarquee[3]);
            g.fill(mx0, my0, mx1, my1, 0x3342A5F5);
            g.fill(mx0, my0, mx1, my0 + 1, 0xFF42A5F5);
            g.fill(mx0, my1 - 1, mx1, my1, 0xFF42A5F5);
            g.fill(mx0, my0, mx0 + 1, my1, 0xFF42A5F5);
            g.fill(mx1 - 1, my0, mx1, my1, 0xFF42A5F5);
            // 框选计数（实时命中元素数；组自动展开会在提交时放大）
            int cnt = worldMarqueePreview.size();
            if (cnt > 0) {
                String cLabel = cnt + " 元素";
                var cmc = Minecraft.getInstance();
                g.fill(mx0, my1 + 1, mx0 + cmc.font.width(cLabel) + 8, my1 + 11, 0xCC10151F);
                g.drawString(cmc.font, cLabel, mx0 + 4, my1 + 3, 0xFFFFD54F);
            }
        }
        // 屏幕外箭头（逐面板：各自 world.offScreenArrows / arrowColor）
        var window = Minecraft.getInstance().getWindow();
        double scaledW = window.getGuiScaledWidth();
        double scaledH = window.getGuiScaledHeight();
        int margin = 18;
        try {
            for (WorldPanel panel : worldPanels) {
                Object worldOpt = panel.page.options() == null ? null : panel.page.options().get("world");
                if (!(worldOpt instanceof Map<?, ?> w)) {
                    continue;
                }
                if (!Boolean.parseBoolean(String.valueOf(w.get("offScreenArrows")))) {
                    continue;
                }
                net.minecraft.world.phys.Vec3 anchor = panel.anchor != null ? panel.anchor
                        : WorldHologram.anchorFor(camera, panel.page.options());
                int color = com.opendreamcore.client.UiStyle.color(w.get("arrowColor"), 0xFF7A8BFF);
                var vars = panel.page.variables();
                for (RenderNode node : panel.nodes) {
                    Map<?, ?> holo = node.props().get("hologram") instanceof Map<?, ?> h ? h : Map.of();
                    if (holo.isEmpty()) {
                        continue;
                    }
                    double x = WorldHologram.holoNum(holo, "x", 0, vars);
                    double y = WorldHologram.holoNum(holo, "y", 0, vars);
                    double z = WorldHologram.holoNum(holo, "z", 0, vars);
                    net.minecraft.world.phys.Vec3 center = anchor.add(x, y, z);
                    double[] screen = project(camera, center, scaledW, scaledH);
                    if (screen == null) {
                        continue; // 在屏幕内或相机背后
                    }
                    double sx = screen[0];
                    double sy = screen[1];
                    if (sx >= 0 && sx <= scaledW && sy >= 0 && sy <= scaledH) {
                        continue; // 在屏幕内不画箭头
                    }
                    // 屏幕边缘取点 + 指向角度
                    double clampedX = Math.max(margin, Math.min(scaledW - margin, sx));
                    double clampedY = Math.max(margin, Math.min(scaledH - margin, sy));
                    double angle = Math.atan2(sy - scaledH / 2, sx - scaledW / 2);
                    drawEdgeArrow(g, clampedX, clampedY, angle, color);
                }
            }
        } catch (Exception ignored) {
            // 箭头绘制失败不拖垮帧
        }
    }

    /** 世界点 → 屏幕坐标；相机背后或投影失败返回 null。 */
    static double[] project(net.minecraft.client.Camera camera, net.minecraft.world.phys.Vec3 world,
                                    double scaledW, double scaledH) {
        net.minecraft.world.phys.Vec3 cam = camera.getPosition();
        var rel = new org.joml.Vector4f(
                (float) (world.x - cam.x), (float) (world.y - cam.y), (float) (world.z - cam.z), 1.0F);
        var rot = new org.joml.Matrix4f().rotation(camera.rotation());
        var camSpace = rot.transform(rel);
        if (camSpace.z > 0) {
            return null; // 在相机背后
        }
        var mc = Minecraft.getInstance();
        var proj = mc.gameRenderer.getProjectionMatrix(mc.options.fov().get());
        var clip = proj.transform(camSpace);
        if (Math.abs(clip.w) < 1e-6) {
            return null;
        }
        double ndcX = clip.x / clip.w;
        double ndcY = clip.y / clip.w;
        return new double[]{(ndcX + 1) / 2 * scaledW, (1 - ndcY) / 2 * scaledH};
    }

    /** 屏幕空间细线（2px 步进小方块连成，任意角度；编辑距离标注用）。 */
    private static void drawLine2D(net.minecraft.client.gui.GuiGraphics g, double x0, double y0,
                                   double x1, double y1, int color) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len < 1) {
            return;
        }
        int steps = Math.max(1, (int) Math.ceil(len / 2.0));
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            g.fill((int) (x0 + dx * t), (int) (y0 + dy * t),
                    (int) (x0 + dx * t) + 2, (int) (y0 + dy * t) + 2, color);
        }
    }

    /** z 层级指示条（Z+拖 时显示）：右侧竖条 = 各可见元素 z 刻度（上高下低），拖动元素琥珀高亮实时移动。 */
    private void renderZIndicator(net.minecraft.client.gui.GuiGraphics g) {
        var mc = Minecraft.getInstance();
        if (worldPage == null || worldNodes == null) {
            return;
        }
        var vars = worldPage.variables();
        String activeTab = worldTabActive(worldPage.id());
        java.util.Map<String, Double> zById = new java.util.LinkedHashMap<>();
        collectWorldZ(worldNodes, activeTab, vars, zById);
        if (zById.isEmpty()) {
            return;
        }
        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        for (double z : zById.values()) {
            min = Math.min(min, z);
            max = Math.max(max, z);
        }
        double span = max - min;
        if (span < 1e-6) {
            span = 1;
        }
        int x = mc.getWindow().getGuiScaledWidth() - 26;
        int y0 = 64, h = 140;
        g.fill(x - 4, y0 - 4, x + 4, y0 + h + 4, 0xAA10151F);
        g.fill(x - 1, y0, x + 1, y0 + h, 0xFF3A4A66);
        for (var e : zById.entrySet()) {
            double t = (e.getValue() - min) / span;
            int y = y0 + (int) ((1 - t) * h);
            boolean scrubbing = e.getKey().equals(WorldEditor.get().worldZScrubId) || worldZScrubBase.containsKey(e.getKey());
            int color = scrubbing ? 0xFFFFD54F : 0xFF90CAF9;
            g.fill(x - 5, y, x + 5, y + 2, color);
        }
        double cur = zById.getOrDefault(WorldEditor.get().worldZScrubId, 0.0);
        String label = String.format(java.util.Locale.ROOT, "z %.2f", cur);
        g.drawString(mc.font, label, x - mc.font.width(label) - 10, y0 + h / 2 - 4, 0xFFFFD54F);
    }

    /** O 拖拽透明度指示条（右侧竖条 = 实时透明度填充 + 百分比）。 */
    private void renderOpacityIndicator(net.minecraft.client.gui.GuiGraphics g) {
        var mc = Minecraft.getInstance();
        if (worldPage == null || WorldEditor.get().worldOpacityScrubId == null) {
            return;
        }
        var element = findElement(worldPage, WorldEditor.get().worldOpacityScrubId);
        double v = 1.0;
        if (element != null) {
            Object raw = element.props().get("opacity");
            v = raw instanceof Number n ? n.doubleValue() : 1.0;
        }
        int x = mc.getWindow().getGuiScaledWidth() - 40;
        int y0 = 64, w = 10, h = 140;
        g.fill(x - 4, y0 - 4, x + w + 4, y0 + h + 4, 0xAA10151F);
        g.fill(x, y0, x + w, y0 + h, 0xFF3A4A66);
        int fill = (int) (h * Math.max(0, Math.min(1, v)));
        if (fill > 0) {
            g.fill(x, y0 + h - fill, x + w, y0 + h, 0xFF26A69A);
        }
        String label = String.format(java.util.Locale.ROOT, "%.0f%%", v * 100);
        g.drawString(mc.font, label, x - mc.font.width(label) - 10, y0 + h / 2 - 4, 0xFF26A69A);
    }

    /** 旋转拖拽角度指示：实时 yaw 读数 + 小圆盘指针（0° 上、90° 右，MC yaw 顺时针）。 */
    private void renderYawIndicator(net.minecraft.client.gui.GuiGraphics g) {
        var mc = Minecraft.getInstance();
        if (worldPage == null || WorldEditor.get().worldRotateId == null) {
            return;
        }
        var element = findElement(worldPage, WorldEditor.get().worldRotateId);
        double yaw = 0;
        if (element != null) {
            Object raw = element.props().get("hologram");
            if (raw instanceof Map<?, ?> holo && holo.get("yaw") instanceof Number n) {
                yaw = n.doubleValue();
            }
        }
        double norm = ((yaw % 360) + 360) % 360;
        int cx = mc.getWindow().getGuiScaledWidth() - 44;
        int cy = 64;
        String label = String.format(java.util.Locale.ROOT, "yaw %.1f°", yaw);
        g.drawString(mc.font, label, cx - mc.font.width(label) / 2, cy - 32, 0xFFFFD54F);
        int r = 16;
        g.fill(cx - r - 3, cy - r - 3, cx + r + 3, cy + r + 3, 0xAA10151F);
        g.fill(cx - r, cy - r, cx + r, cy + r, 0xFF1E2A38);
        double rad = Math.toRadians(norm - 90);
        int px = cx + (int) (Math.cos(rad) * r * 0.8);
        int py = cy + (int) (Math.sin(rad) * r * 0.8);
        g.fill(px - 2, py - 2, px + 2, py + 2, 0xFFFFD54F);
        g.fill(cx - 1, cy - 1, cx + 1, cy + 1, 0xFF90CAF9);
    }

    /** 手柄读数徽标：世界点投影 → 深底 + 琥珀上沿文字（尺寸/描边宽度实时读数）。 */
    private void renderHandleReadout(net.minecraft.client.gui.GuiGraphics g,
                                     net.minecraft.client.Camera camera, String label,
                                     net.minecraft.world.phys.Vec3 worldPos) {
        renderHandleReadout(g, camera, label, worldPos, -16);
    }

    /** 手柄读数（worldPos 投影到屏幕 y+yOff 处画琥珀标签；yOff 默认 -16 = 上方）。 */
    private void renderHandleReadout(net.minecraft.client.gui.GuiGraphics g,
                                     net.minecraft.client.Camera camera, String label,
                                     net.minecraft.world.phys.Vec3 worldPos, int yOff) {
        var mc = Minecraft.getInstance();
        var window = mc.getWindow();
        double scaledW = window.getGuiScaledWidth();
        double scaledH = window.getGuiScaledHeight();
        double[] p = project(camera, worldPos, scaledW, scaledH);
        if (p == null) {
            return;
        }
        int x = (int) p[0];
        int y = (int) p[1] + yOff;
        int tw = mc.font.width(label);
        g.fill(x - 3, y - 3, x + tw + 3, y + 6, 0xCC10151F);
        g.fill(x - 3, y - 3, x + tw + 3, y - 2, 0xFFFFB300);
        g.drawString(mc.font, label, x, y - 3, 0xFFFFE082);
    }

    /** 递归收集可见元素 z（锚点相对，仅带 hologram 的元素）。 */
    private static void collectWorldZ(List<RenderNode> nodes, String activeTab,
                                      java.util.Map<String, Object> vars,
                                      java.util.Map<String, Double> out) {
        if (nodes == null) {
            return;
        }
        for (RenderNode node : nodes) {
            if (!WorldHologram.tabVisible(node, activeTab)) {
                continue;
            }
            Object raw = node.props().get("hologram");
            if (raw instanceof Map<?, ?> holo) {
                out.put(node.id(), WorldHologram.holoNum(holo, "z", 0, vars));
            }
            collectWorldZ(node.children(), activeTab, vars, out);
        }
    }

    /** 屏幕边缘箭头（三角形，朝向 angle 弧度）。 */
    private static void drawEdgeArrow(net.minecraft.client.gui.GuiGraphics g, double x, double y,
                                      double angle, int color) {
        float red = ((color >> 16) & 0xFF) / 255.0F;
        float green = ((color >> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        float alpha = ((color >>> 24) & 0xFF) / 255.0F;
        if (alpha <= 0) {
            return;
        }
        double size = 5.0;
        double tipX = x + size * Math.cos(angle);
        double tipY = y + size * Math.sin(angle);
        double back = angle + Math.PI;
        double spread = 0.9;
        double b1x = x + size * Math.cos(back - spread);
        double b1y = y + size * Math.sin(back - spread);
        double b2x = x + size * Math.cos(back + spread);
        double b2y = y + size * Math.sin(back + spread);
        var matrix = CompatRender.guiMatrix(g);
        CompatRender.enableBlend();
        CompatRender.defaultBlendFunc();
        CompatRender.disableDepthTest();
        CompatRender.setColorShader();
        var builder = CompatRender.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.TRIANGLES,
                        com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR);
        builder.addVertex(matrix, (float) tipX, (float) tipY, 0).setColor(red, green, blue, alpha);
        builder.addVertex(matrix, (float) b1x, (float) b1y, 0).setColor(red, green, blue, alpha);
        builder.addVertex(matrix, (float) b2x, (float) b2y, 0).setColor(red, green, blue, alpha);
        builder.buildAndDraw();
        CompatRender.enableDepthTest();
        CompatRender.disableBlend();
    }

    static double numOf(Object v, double fallback) {
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        if (v != null) {
            try {
                return Double.parseDouble(String.valueOf(v));
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    /** 命中元素到相机的世界距离（跨面板最近命中比较用；anchor 为面板生效锚点）。 */

    /** 编辑网格步长：吸附开启跟随吸附值，否则 0.25。 */

    /** rect 圆角步进（rect.radius ±0.05，0~1 钳制，写 props + 未保存属性）。 */

    /** 读取元素 hologram.border（字符串或 map，无则 null）。 */
    static Object elementBorder(Element element) {
        Object raw = element.props().get("hologram");
        if (!(raw instanceof Map<?, ?> holo)) {
            return null;
        }
        return holo.get("border");
    }

    /** 描边颜色循环：调色板轮转 + 关闭态（无 → 5 色 → 无），保留 width/flow 等其余键。 */

    /** 关闭描边（移除 hologram.border，__unset__ 约定服务端删除键；可撤消）。 */

    /** 描边宽度步进（0.005~0.2 钳制）。 */

    /** 应用描边变更：更新元素 props（客户端渲染）+ 记入未保存（原始行内 YAML，服务端原样写回；可撤消）。 */

    /** recordUndo=false：拖拽手柄松手路径（按下时已快照，避免双撤消步）。 */

    /** 描边流光：设置 flow=true + flowColor（保留颜色/宽度；可撤消）。 */
    private void applyBorderFlowColor(Element element, String flowColor) {
        if (element == null || worldPage == null) {
            return;
        }
        WorldEditor.get().pushWorldUndo("描边", "border", List.of(element.id()));
        Object border = elementBorder(element);
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        if (border instanceof Map<?, ?> bm) {
            bm.forEach((k, v) -> m.put(String.valueOf(k), v));
        }
        m.put("flow", true);
        m.put("flowColor", flowColor);
        if (!m.containsKey("color")) {
            m.put("color", "#FFD700");
        }
        if (!m.containsKey("width")) {
            m.put("width", 0.02);
        }
        writeWorldBorder(element, m);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§e[OpenDreamCore] §f描边流光: " + flowColor
                        + "（保存后写回页面文件）"), false);
    }

    /** 描边写回共用：props + 行内 YAML（服务端 bakeProp 原样写入）+ __create__ 刷新 + 重建布局。 */
    void writeWorldBorder(Element element, Map<String, Object> m) {
        Object raw = element.props().get("hologram");
        if (raw instanceof Map<?, ?> holo) {
            Map<Object, Object> copy = new java.util.LinkedHashMap<>(holo);
            copy.put("border", m);
            element.props().put("hologram", copy);
        }
        // 原始行内 YAML（服务端 bakeProp 原样写入）
        StringBuilder sb = new StringBuilder("{");
        m.forEach((k, v) -> {
            sb.append(k).append(": ");
            if (v instanceof Number || v instanceof Boolean) {
                sb.append(v);
            } else {
                sb.append("'").append(v).append("'");
            }
            sb.append(", ");
        });
        String inline = sb.substring(0, sb.length() - 2) + "}";
        WorldEditor.get().worldEditProps.computeIfAbsent(element.id(), k -> new ConcurrentHashMap<>())
                .put("hologram.border", inline);
        refreshCreateBlock(element.id());
        invalidateLayout(worldPage);
        worldNodes = layoutPage(worldPage, 800, 600);
    }

    /** 文本字号步进（hologram.scale ± 0.005，0.002~0.5 钳制，写 props + 未保存属性）。 */

    /** 文本对齐循环：left → center → right（text 元素，写 text.align + 未保存属性）。 */

    /** 调节选中元素透明度（opacity 0~1 钳制，写 props + 未保存属性）。 */

    /** Shift 是否按下。 */
    static boolean shiftHeld(Minecraft mc) {
        long win = mc.getWindow().getWindow();
        return org.lwjgl.glfw.GLFW.glfwGetKey(win, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT) == 1
                || org.lwjgl.glfw.GLFW.glfwGetKey(win, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT) == 1;
    }

    /** 旋转选中元素 90° 增量（写 hologram.yaw + 未保存属性；可撤消）。 */

    /** 调整选中元素 z 层级（渲染/拾取排序，0~99 钳制）。 */

    /** 当前按下的世界元素（按下缩放反馈；渲染线程每帧读取；按页隔离）。 */
    public String worldPressedId(String pageId) {
        String key = WorldEditor.get().worldPressedId;
        if (key == null || pageId == null) {
            return null;
        }
        String prefix = (pageId.isEmpty() ? "world" : pageId) + "/";
        return key.startsWith(prefix) ? key.substring(prefix.length()) : null;
    }

    /** 世界节点树递归查找。 */
    static RenderNode findWorldNode(String id) {
        return findWorldNode(ClientController.get().worldNodes, id);
    }

    static RenderNode findWorldNode(List<RenderNode> nodes, String id) {
        if (nodes == null || id == null) {
            return null;
        }
        for (RenderNode node : nodes) {
            if (id.equals(node.id())) {
                return node;
            }
            RenderNode found = findWorldNode(node.children(), id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** 元素世界中心：锚点（聚焦面板生效锚点）+ 父链偏移 + hologram 偏移（支持表达式）。 */
    net.minecraft.world.phys.Vec3 worldElementCenter(RenderNode node, net.minecraft.client.Camera camera) {
        net.minecraft.world.phys.Vec3 anchor = focusedWorldAnchor(camera);
        Map<?, ?> holo = node.props().get("hologram") instanceof Map<?, ?> h ? h : Map.of();
        var vars = worldPage.variables();
        // 父链偏移：raycast 刚拾取过时用它（交互路径一致），否则只算自身
        double[] parent = WorldHologram.lastPickOffset();
        double px = parent == null ? 0 : parent[0];
        double py = parent == null ? 0 : parent[1];
        double pz = parent == null ? 0 : parent[2];
        double hx = px + WorldHologram.holoNum(holo, "x", 0, vars);
        double hy = py + WorldHologram.holoNum(holo, "y", 0, vars);
        double hz = pz + WorldHologram.holoNum(holo, "z", 0, vars);
        return anchor.add(hx, hy, hz);
    }

    /** 面板整体移动开始（Alt + 空白拖拽）：快照全部元素位置 + 记录拖拽基准。
     *  targets 非空 = 只移动该集合（多选 + Alt = 整组整体移动）。 */

    /** 面板整体移动跟随：射线平面位移 → 全部元素同偏移（实时渲染跟随）。 */

    /** 面板整体移动提交：全部元素记入未保存位置（保存时写回页面文件）。 */

    /** 锚点拖拽开始（M + 空白拖拽）：快照 offset 状态（合并键一步撤消）+ 记录拖拽基准。 */

    /** 射线与过锚点、垂直相机的平面求交（拖拽基准/更新共用）。 */
    double[] anchorPlaneHit(net.minecraft.client.Camera camera, Minecraft mc) {
        double[] ray = WorldHologram.mouseRayWorld(mc, camera);
        net.minecraft.world.phys.Vec3 anchor = focusedWorldAnchor(camera);
        var rot = new org.joml.Matrix4f().rotation(camera.rotation());
        var n = rot.transformDirection(new org.joml.Vector3f(0, 0, -1));
        double denom = ray[3] * n.x + ray[4] * n.y + ray[5] * n.z;
        if (Math.abs(denom) < 1e-9) {
            return null;
        }
        double t = ((anchor.x - ray[0]) * n.x + (anchor.y - ray[1]) * n.y + (anchor.z - ray[2]) * n.z) / denom;
        if (t < 0) {
            return null;
        }
        return new double[]{ray[0] + ray[3] * t, ray[1] + ray[4] * t, ray[2] + ray[5] * t};
    }

    /** 锚点拖拽更新：平面位移增量累计写 world.offsetX/Y/Z（增量基准跟随，全程一步撤消）。 */

    /** 锚点拖拽结束。 */

    /** Alt 是否按下。 */
    static boolean altHeld(Minecraft mc) {
        long win = mc.getWindow().getWindow();
        return org.lwjgl.glfw.GLFW.glfwGetKey(win, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT) == 1
                || org.lwjgl.glfw.GLFW.glfwGetKey(win, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_ALT) == 1;
    }

    /** Z 键是否按下（z 排序拖拽修饰键）。 */
    static boolean zHeld(Minecraft mc) {
        long win = mc.getWindow().getWindow();
        return org.lwjgl.glfw.GLFW.glfwGetKey(win, org.lwjgl.glfw.GLFW.GLFW_KEY_Z) == 1;
    }

    /** O 键是否按下（透明度拖拽修饰键）。 */
    static boolean oHeld(Minecraft mc) {
        long win = mc.getWindow().getWindow();
        return org.lwjgl.glfw.GLFW.glfwGetKey(win, org.lwjgl.glfw.GLFW.GLFW_KEY_O) == 1;
    }

    /** z 排序拖拽开始：快照目标 z（单选 = 按住元素；多选 = 按住组内元素 → 整组），记基准。 */

    /** z 排序拖拽提交：目标元素记入未保存位置（保存时写回页面文件）。 */

    /** 透明度拖拽开始：快照目标 opacity（单选 = 按住元素；多选 = 整组），记基准。 */

    /** 透明度拖拽提交：目标元素记入未保存属性（保存时写回页面文件）。 */

    /** 拖拽跟随：射线与"过基准点、法线朝相机"的平面求交 → 偏移。 */

    /** 选中集包围盒（锚点相对 x0,y0,x1,y1；不足 2 个可见元素返回 null）。 */
    double[] worldSelectionBounds(java.util.Set<String> ids) {
        if (worldNodes == null || ids == null || ids.size() < 2) {
            return null;
        }
        double[] out = {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        collectSelectionBounds(worldNodes, worldTabActive(worldPage.id()), worldPage.variables(),
                ids, null, out);
        if (out[0] == Double.MAX_VALUE) {
            return null;
        }
        return out;
    }

    /** 递归收集选中集可见元素包围盒（锚点相对，父链偏移累积，含元素宽高）。 */
    private static void collectSelectionBounds(List<RenderNode> nodes, String activeTab,
                                               java.util.Map<String, Object> vars,
                                               java.util.Set<String> ids, double[] parentOffset,
                                               double[] out) {
        if (nodes == null) {
            return;
        }
        for (RenderNode node : nodes) {
            if (!WorldHologram.tabVisible(node, activeTab)) {
                continue;
            }
            double bx = parentOffset == null ? 0 : parentOffset[0];
            double by = parentOffset == null ? 0 : parentOffset[1];
            double[] childOffset = parentOffset;
            Object raw = node.props().get("hologram");
            if (raw instanceof Map<?, ?> holo) {
                double x = bx + WorldHologram.holoNum(holo, "x", 0, vars);
                double y = by + WorldHologram.holoNum(holo, "y", 0, vars);
                if (ids.contains(node.id())) {
                    double w = WorldHologram.holoNum(holo, "width",
                            "text".equals(node.type()) ? 2.0 : 1.0, vars);
                    double h = WorldHologram.holoNum(holo, "height",
                            "text".equals(node.type()) ? 0.25 : 1.0, vars);
                    out[0] = Math.min(out[0], x - w / 2);
                    out[1] = Math.min(out[1], y - h / 2);
                    out[2] = Math.max(out[2], x + w / 2);
                    out[3] = Math.max(out[3], y + h / 2);
                }
                double z = WorldHologram.holoNum(holo, "z", 0, vars);
                double bz = parentOffset == null ? 0 : parentOffset[2];
                childOffset = new double[]{x, y, bz + z};
            }
            collectSelectionBounds(node.children(), activeTab, vars, ids, childOffset, out);
        }
    }

    /** 成员元素盒列表（锚点相对 {x, y, w, h}；镜像预览用；拖拽中的元素叠加实时偏移）。 */
    private java.util.List<double[]> worldMemberBoxes(java.util.List<String> members) {
        java.util.List<double[]> out = new java.util.ArrayList<>();
        if (worldNodes == null || members == null || members.isEmpty()) {
            return out;
        }
        java.util.Set<String> ids = new java.util.HashSet<>(members);
        String pkey = worldPage.id() == null ? "world" : worldPage.id();
        collectMemberBoxes(worldNodes, worldTabActive(worldPage.id()), worldPage.variables(), ids, null, out,
                worldDragOffsets.isEmpty() ? null : worldDragOffsets, pkey);
        return out;
    }

    /** 递归收集成员元素盒（锚点相对，父链偏移累积，含宽高；拖拽偏移叠加）。 */
    private static void collectMemberBoxes(List<RenderNode> nodes, String activeTab,
                                           java.util.Map<String, Object> vars,
                                           java.util.Set<String> ids, double[] parentOffset,
                                           java.util.List<double[]> out,
                                           java.util.Map<String, double[]> dragOffsets, String pageKey) {
        if (nodes == null) {
            return;
        }
        for (RenderNode node : nodes) {
            if (!WorldHologram.tabVisible(node, activeTab)) {
                continue;
            }
            double bx = parentOffset == null ? 0 : parentOffset[0];
            double by = parentOffset == null ? 0 : parentOffset[1];
            double[] childOffset = parentOffset;
            Object raw = node.props().get("hologram");
            if (raw instanceof Map<?, ?> holo) {
                double x = bx + WorldHologram.holoNum(holo, "x", 0, vars);
                double y = by + WorldHologram.holoNum(holo, "y", 0, vars);
                if (ids.contains(node.id())) {
                    double w = WorldHologram.holoNum(holo, "width",
                            "text".equals(node.type()) ? 2.0 : 1.0, vars);
                    double h = WorldHologram.holoNum(holo, "height",
                            "text".equals(node.type()) ? 0.25 : 1.0, vars);
                    double ox = 0, oy = 0;
                    if (dragOffsets != null) {
                        double[] off = dragOffsets.get(wkey(pageKey, node.id()));
                        if (off != null) {
                            ox = off[0];
                            oy = off[1];
                        }
                    }
                    out.add(new double[]{x + ox, y + oy, w, h});
                }
                double z = WorldHologram.holoNum(holo, "z", 0, vars);
                double bz = parentOffset == null ? 0 : parentOffset[2];
                childOffset = new double[]{x, y, bz + z};
            }
            collectMemberBoxes(node.children(), activeTab, vars, ids, childOffset, out, dragOffsets, pageKey);
        }
    }

    /** 递归收集可见元素中心（锚点相对坐标，父链偏移累积），排除 skip 集合。 */

    /** 收集可见元素对齐参考线：{axis(0=竖线 x, 1=横线 y), value}（左/中/右 + 上/中/下），排除 skip。 */

    /** 松手提交：偏移写回元素 hologram.x/y/z（支持 snap 网格吸附），重建布局；服务端页面上报 INPUT。 */

    /** 单个元素落位：偏移写回 hologram（编辑模式记 dirty，服务端页面逐元素上报 INPUT）。 */

    /** 点击涟漪：射线与过元素中心平面求交 → 世界坐标入列（渲染线程 400ms 衰减；元素级 rippleColor 覆盖）。 */

    /** 旋转手柄：选中元素中心 + 顶部偏移（世界坐标对），未选中返回 null。 */
    double[][] rotateHandleWorld(net.minecraft.client.Camera camera, Minecraft mc) {
        RenderNode node = findWorldNode(WorldEditor.get().worldEditSelected);
        if (node == null) {
            return null;
        }
        Object raw = node.props().get("hologram");
        Map<?, ?> holo = raw instanceof Map<?, ?> h ? h : Map.of();
        var vars = worldPage.variables();
        double h = WorldHologram.holoNum(holo, "height", "text".equals(node.type()) ? 0.25 : 1.0, vars);
        double lift = Math.max(0.35, h / 2 + 0.25);
        var rot = new org.joml.Matrix4f().rotation(camera.rotation());
        var up = rot.transformDirection(new org.joml.Vector3f(0, 1, 0));
        net.minecraft.world.phys.Vec3 center = worldElementCenter(node, camera);
        net.minecraft.world.phys.Vec3 handle = center.add(up.x * lift, up.y * lift, up.z * lift);
        return new double[][]{{center.x, center.y, center.z}, {handle.x, handle.y, handle.z}};
    }

    /** 旋转跟随：鼠标相对元素中心的屏幕角度 → hologram.yaw（手柄始终指向鼠标）。 */

    /** 旋转松手：yaw 记入未保存属性（保存时写回页面文件）。 */

    /** 缩放手柄：选中元素右下角（billboard 空间向外偏移，世界坐标对），未选中返回 null。 */
    double[][] resizeHandleWorld(net.minecraft.client.Camera camera, Minecraft mc) {
        RenderNode node = findWorldNode(WorldEditor.get().worldEditSelected);
        if (node == null) {
            return null;
        }
        Object raw = node.props().get("hologram");
        Map<?, ?> holo = raw instanceof Map<?, ?> h ? h : Map.of();
        var vars = worldPage.variables();
        double w = WorldHologram.holoNum(holo, "width", "text".equals(node.type()) ? 2.0 : 1.0, vars);
        double h = WorldHologram.holoNum(holo, "height", "text".equals(node.type()) ? 0.25 : 1.0, vars);
        var rot = new org.joml.Matrix4f().rotation(camera.rotation());
        var right = rot.transformDirection(new org.joml.Vector3f(1, 0, 0));
        var up = rot.transformDirection(new org.joml.Vector3f(0, 1, 0));
        double lift = 0.18;
        net.minecraft.world.phys.Vec3 center = worldElementCenter(node, camera);
        net.minecraft.world.phys.Vec3 handle = center.add(right.x * (w / 2 + lift), right.y * (w / 2 + lift),
                right.z * (w / 2 + lift)).add(up.x * (-h / 2 - lift), up.y * (-h / 2 - lift),
                up.z * (-h / 2 - lift));
        return new double[][]{{center.x, center.y, center.z}, {handle.x, handle.y, handle.z}};
    }

    /** 鼠标到元素中心的屏幕距离（缩放手柄比例基准）。 */

    /** 缩放跟随：鼠标距离比例 → hologram.width/height（最小 0.1 钳制）。 */

    /** 缩放松手：width/height 记入未保存属性（保存时写回页面文件）。 */

    /** 描边手柄：选中元素左边缘中心（billboard 空间向左偏移，世界坐标对）；元素无 hologram.border 返回 null。 */

    /** 当前元素描边宽度（缺省 0.02；元素无 border 返回 -1 表示无描边）。 */
    double worldBorderWidthOf(RenderNode node) {
        if (node == null) {
            return -1;
        }
        Object raw = node.props().get("hologram");
        if (!(raw instanceof Map<?, ?> holo) || holo.get("border") == null) {
            return -1;
        }
        Object border = holo.get("border");
        if (border instanceof Map<?, ?> bm) {
            Object w = bm.get("width");
            if (w instanceof Number n) {
                return n.doubleValue();
            }
        }
        return 0.02;
    }

    /** 当前元素描边颜色（缺省金色 #FFD700；字符串 border = 该色）。 */
    private int worldBorderColorOf(RenderNode node) {
        if (node == null) {
            return 0xFFFFD700;
        }
        Object raw = node.props().get("hologram");
        if (!(raw instanceof Map<?, ?> holo) || holo.get("border") == null) {
            return 0xFFFFD700;
        }
        Object border = holo.get("border");
        if (border instanceof Map<?, ?> bm) {
            return com.opendreamcore.client.UiStyle.color(bm.get("color"), 0xFFFFD700);
        }
        return com.opendreamcore.client.UiStyle.color(border, 0xFFFFD700);
    }

    /** 描边跟随：鼠标相对拖拽起点的射线平面位移沿 billboard 右轴 → hologram.border.width（实时预览）。 */

    /** 描边色板点击：前 N 格 = 改色；关闭格 = 关描边；流光列（Shift = 副色）；速度列；样式列。 */
    void applyBorderPaletteClick(int index, boolean shift) {
        if (WorldEditor.get().worldEditSelected == null || worldPage == null) {
            return;
        }
        var element = findElement(worldPage, WorldEditor.get().worldEditSelected);
        if (element == null) {
            return;
        }
        int solid = WorldEditor.get().BORDER_PALETTE.length;
        if (index >= 0 && index < solid) {
            WorldEditor.get().applyWorldBorder(element, WorldEditor.get().BORDER_PALETTE[index], null);
            WorldEditor.get().borderColorIdx = index;
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§e[OpenDreamCore] §f描边颜色: " + WorldEditor.get().BORDER_PALETTE[index]
                            + "（保存后写回页面文件）"), false);
        } else if (index == solid) {
            WorldEditor.get().closeWorldBorder(element);
        } else if (index > solid) {
            int flowIdx = index - solid - 1;
            if (flowIdx >= 0 && flowIdx < WorldEditor.get().BORDER_FLOW_PALETTE.length) {
                if (shift) {
                    applyBorderFlowColor2(element, WorldEditor.get().BORDER_FLOW_PALETTE[flowIdx]);
                } else {
                    applyBorderFlowColor(element, WorldEditor.get().BORDER_FLOW_PALETTE[flowIdx]);
                }
            } else {
                int speedIdx = flowIdx - WorldEditor.get().BORDER_FLOW_PALETTE.length;
                if (speedIdx >= 0 && speedIdx < WorldEditor.get().BORDER_FLOW_SPEEDS.length) {
                    applyBorderFlowSpeed(element, WorldEditor.get().BORDER_FLOW_SPEEDS[speedIdx]);
                } else if (speedIdx == WorldEditor.get().BORDER_FLOW_SPEEDS.length) {
                    WorldEditor.get().adjustWorldFlowSpeed(-1);
                } else if (speedIdx == WorldEditor.get().BORDER_FLOW_SPEEDS.length + 1) {
                    WorldEditor.get().adjustWorldFlowSpeed(1);
                } else {
                    int styleIdx = speedIdx - WorldEditor.get().BORDER_FLOW_SPEEDS.length - 2;
                    if (styleIdx >= 0 && styleIdx < WorldEditor.get().BORDER_STYLE_LABELS.length) {
                        applyBorderStyle(element, styleIdx);
                    } else {
                        int segIdx = styleIdx - WorldEditor.get().BORDER_STYLE_LABELS.length;
                        if (segIdx >= 0 && segIdx < WorldEditor.get().BORDER_FLOW_SEGS.length) {
                            applyBorderFlowSeg(element, WorldEditor.get().BORDER_FLOW_SEGS[segIdx]);
                        } else {
                            int alphaIdx = segIdx - WorldEditor.get().BORDER_FLOW_SEGS.length;
                            if (alphaIdx >= 0 && alphaIdx < WorldEditor.get().BORDER_ALPHAS.length) {
                                applyBorderAlpha(element, WorldEditor.get().BORDER_ALPHAS[alphaIdx]);
                            } else if (alphaIdx == WorldEditor.get().BORDER_ALPHAS.length) {
                                applyBorderGradient(element, !isBorderGradientOn(element));
                            } else if (alphaIdx == WorldEditor.get().BORDER_ALPHAS.length + 1) {
                                applyBorderFlowReverse(element, !isBorderFlowReverseOn(element));
                            } else {
                                int segCountIdx = alphaIdx - WorldEditor.get().BORDER_ALPHAS.length - 2;
                                if (segCountIdx >= 0 && segCountIdx < WorldEditor.get().BORDER_FLOW_SEGMENT_COUNTS.length) {
                                    applyBorderFlowSegments(element, WorldEditor.get().BORDER_FLOW_SEGMENT_COUNTS[segCountIdx]);
                                } else {
                                    int gapIdx = segCountIdx - WorldEditor.get().BORDER_FLOW_SEGMENT_COUNTS.length;
                                    if (gapIdx >= 0 && gapIdx < WorldEditor.get().BORDER_FLOW_GAPS.length) {
                                        applyBorderFlowGap(element, WorldEditor.get().BORDER_FLOW_GAPS[gapIdx]);
                                    } else if (gapIdx == WorldEditor.get().BORDER_FLOW_GAPS.length) {
                                        toggleWorldFlowHoverBoost();
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** 流光段长档（flow:true + flowSeg 周长比例；保留其它属性；可撤消）。 */    private void applyBorderFlowSeg(Element element, float seg) {
        if (element == null || worldPage == null) {
            return;
        }
        WorldEditor.get().pushWorldUndo("描边", "border", List.of(element.id()));
        Object border = elementBorder(element);
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        if (border instanceof Map<?, ?> bm) {
            bm.forEach((k, v) -> m.put(String.valueOf(k), v));
        }
        m.put("flow", true);
        m.put("flowSeg", seg);
        if (!m.containsKey("color")) {
            m.put("color", "#FFD700");
        }
        if (!m.containsKey("width")) {
            m.put("width", 0.02);
        }
        writeWorldBorder(element, m);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§e[OpenDreamCore] §f流光段长: " + seg
                        + "（周长比例，保存后写回页面文件）"), false);
    }

    /** 描边透明度档（alpha 0~1 乘算；保留其它属性；可撤消）。 */    private void applyBorderAlpha(Element element, float alpha) {
        if (element == null || worldPage == null) {
            return;
        }
        WorldEditor.get().pushWorldUndo("描边", "border", List.of(element.id()));
        Object border = elementBorder(element);
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        if (border instanceof Map<?, ?> bm) {
            bm.forEach((k, v) -> m.put(String.valueOf(k), v));
        }
        m.put("alpha", alpha);
        if (!m.containsKey("color")) {
            m.put("color", "#FFD700");
        }
        if (!m.containsKey("width")) {
            m.put("width", 0.02);
        }
        writeWorldBorder(element, m);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§e[OpenDreamCore] §f描边透明度: " + alpha
                        + "（保存后写回页面文件）"), false);
    }

    /** 元素描边是否开启流光渐变（色板 渐 开关状态）。 */
    private boolean isBorderGradientOn(Element element) {
        if (element == null) {
            return false;
        }
        Object border = elementBorder(element);
        return border instanceof Map<?, ?> bm
                && Boolean.parseBoolean(String.valueOf(bm.get("flowGradient")));
    }

    /** 元素描边流光是否反向（色板 向 开关状态）。 */
    private boolean isBorderFlowReverseOn(Element element) {
        if (element == null) {
            return false;
        }
        Object border = elementBorder(element);
        return border instanceof Map<?, ?> bm
                && Boolean.parseBoolean(String.valueOf(bm.get("flowReverse")));
    }

    // ---- 色板当前档回显读取（速度/样式/段长/透明度/颜色，默认值与渲染侧一致） ----

    /** 当前流光速度（ms/圈；0 未设 = 默认 1200）。 */
    private long borderFlowSpeedOf(Element element) {
        if (element == null) {
            return 1200;
        }
        Object border = elementBorder(element);
        if (border instanceof Map<?, ?> bm && bm.get("flowSpeed") instanceof Number n) {
            long v = n.longValue();
            return v > 0 ? v : 1200;
        }
        return 1200;
    }

    /** 当前描边样式档（0 实线 / 1 虚线 0.1 / 2 点线 0.04 / 3 双线）。 */
    private int borderStyleIdxOf(Element element) {
        if (element == null) {
            return 0;
        }
        Object border = elementBorder(element);
        if (!(border instanceof Map<?, ?> bm)) {
            return 0;
        }
        if (Boolean.parseBoolean(String.valueOf(bm.get("double")))) {
            return 3;
        }
        if (Boolean.parseBoolean(String.valueOf(bm.get("dash")))) {
            double l = bm.get("dashLen") instanceof Number n ? n.doubleValue() : 0.1;
            return Math.abs(l - 0.1) < 0.01 ? 1 : 2;
        }
        return 0;
    }

    /** 当前流光段长（周长比例；0 未设 = 自动均分）。 */
    private float borderFlowSegOf(Element element) {
        if (element == null) {
            return 0;
        }
        Object border = elementBorder(element);
        if (border instanceof Map<?, ?> bm && bm.get("flowSeg") instanceof Number n) {
            return n.floatValue();
        }
        return 0;
    }

    /** 当前描边透明度（1.0 = 实）。 */
    private float borderAlphaOf(Element element) {
        if (element == null) {
            return 1.0F;
        }
        Object border = elementBorder(element);
        if (border instanceof Map<?, ?> bm && bm.get("alpha") instanceof Number n) {
            return n.floatValue();
        }
        return 1.0F;
    }

    /** 描边主色是否匹配（忽略 alpha 通道，大小写不敏感）。 */
    private boolean borderColorMatches(Element element, String hex) {
        if (element == null) {
            return false;
        }
        Object border = elementBorder(element);
        if (!(border instanceof Map<?, ?> bm)) {
            return false;
        }
        Object c = bm.get("color");
        if (!(c instanceof String s)) {
            return false;
        }
        String cur = s.trim().toUpperCase(java.util.Locale.ROOT);
        if (cur.length() == 9) {
            cur = cur.substring(0, 7);
        }
        return cur.equals(hex.toUpperCase(java.util.Locale.ROOT));
    }

    /** 流光主/副色是否匹配（flowColor / flowColor2）。 */
    private boolean borderFlowColorMatches(Element element, String hex, boolean secondary) {
        if (element == null) {
            return false;
        }
        Object border = elementBorder(element);
        if (!(border instanceof Map<?, ?> bm)) {
            return false;
        }
        Object c = bm.get(secondary ? "flowColor2" : "flowColor");
        return c != null && String.valueOf(c).equalsIgnoreCase(hex);
    }

    /** 流光方向开关（flow:true + flowReverse；保留其它属性；可撤消）。 */
    private void applyBorderFlowReverse(Element element, boolean on) {        if (element == null || worldPage == null) {
            return;
        }
        WorldEditor.get().pushWorldUndo("描边", "border", List.of(element.id()));
        Object border = elementBorder(element);
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        if (border instanceof Map<?, ?> bm) {
            bm.forEach((k, v) -> m.put(String.valueOf(k), v));
        }
        m.put("flow", true);
        if (on) {
            m.put("flowReverse", true);
        } else {
            m.remove("flowReverse");
        }
        if (!m.containsKey("color")) {
            m.put("color", "#FFD700");
        }
        if (!m.containsKey("width")) {
            m.put("width", 0.02);
        }
        writeWorldBorder(element, m);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§e[OpenDreamCore] §f流光方向: " + (on ? "反向" : "正向")
                        + "（保存后写回页面文件）"), false);
    }

    /** 流光段数档（flow:true + flowSegments 1/2/3 段同时流动；保留其它属性；可撤消）。 */
    private void applyBorderFlowSegments(Element element, int n) {
        if (element == null || worldPage == null) {
            return;
        }
        WorldEditor.get().pushWorldUndo("描边", "border", List.of(element.id()));
        Object border = elementBorder(element);
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        if (border instanceof Map<?, ?> bm) {
            bm.forEach((k, v) -> m.put(String.valueOf(k), v));
        }
        m.put("flow", true);
        m.put("flowSegments", n);
        if (!m.containsKey("color")) {
            m.put("color", "#FFD700");
        }
        if (!m.containsKey("width")) {
            m.put("width", 0.02);
        }
        writeWorldBorder(element, m);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§e[OpenDreamCore] §f流光段数: " + n
                        + " 段（保存后写回页面文件）"), false);
    }

    /** 流光段间距档（flow:true + flowSegGap 周长比例；0 = 等距自动；保留其它属性；可撤消）。 */
    private void applyBorderFlowGap(Element element, float gap) {
        if (element == null || worldPage == null) {
            return;
        }
        WorldEditor.get().pushWorldUndo("描边", "border", List.of(element.id()));
        Object border = elementBorder(element);
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        if (border instanceof Map<?, ?> bm) {
            bm.forEach((k, v) -> m.put(String.valueOf(k), v));
        }
        m.put("flow", true);
        if (gap > 0) {
            m.put("flowSegGap", gap);
        } else {
            m.remove("flowSegGap");
        }
        if (!m.containsKey("color")) {
            m.put("color", "#FFD700");
        }
        if (!m.containsKey("width")) {
            m.put("width", 0.02);
        }
        writeWorldBorder(element, m);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§e[OpenDreamCore] §f流光段间距: "
                        + (gap > 0 ? String.valueOf(gap) : "均分（自动）")
                        + "（保存后写回页面文件）"), false);
    }

    /** 流光渐变开关（flow:true + flowGradient；段内主色→副色渐变；保留其它属性；可撤消）。 */
    private void applyBorderGradient(Element element, boolean on) {
        if (element == null || worldPage == null) {
            return;
        }
        WorldEditor.get().pushWorldUndo("描边", "border", List.of(element.id()));
        Object border = elementBorder(element);
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        if (border instanceof Map<?, ?> bm) {
            bm.forEach((k, v) -> m.put(String.valueOf(k), v));
        }
        if (on) {
            m.put("flow", true);
            m.put("flowGradient", true);
            if (!m.containsKey("flowColor2")) {
                m.put("flowColor2", "#FFD54F");
            }
        } else {
            m.remove("flowGradient");
        }
        if (!m.containsKey("color")) {
            m.put("color", "#FFD700");
        }
        if (!m.containsKey("width")) {
            m.put("width", 0.02);
        }
        writeWorldBorder(element, m);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§e[OpenDreamCore] §f流光渐变: " + (on ? "开" : "关")
                        + "（段内主色→副色渐变，保存后写回页面文件）"), false);
    }

    /** 描边流光副色（flow:true + flowColor2；对侧半周长第二段双色流光；可撤消）。 */
    private void applyBorderFlowColor2(Element element, String flowColor2) {
        if (element == null || worldPage == null) {
            return;
        }
        WorldEditor.get().pushWorldUndo("描边", "border", List.of(element.id()));
        Object border = elementBorder(element);
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        if (border instanceof Map<?, ?> bm) {
            bm.forEach((k, v) -> m.put(String.valueOf(k), v));
        }
        m.put("flow", true);
        m.put("flowColor2", flowColor2);
        if (!m.containsKey("color")) {
            m.put("color", "#FFD700");
        }
        if (!m.containsKey("width")) {
            m.put("width", 0.02);
        }
        writeWorldBorder(element, m);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§e[OpenDreamCore] §f描边流光副色: " + flowColor2
                        + "（Shift+点流光色设置；对侧第二段）"), false);
    }

    /** 描边样式预设：0 实线 / 1 虚线 / 2 点线 / 3 双线（保留颜色/宽度/流光；可撤消）。 */
    private void applyBorderStyle(Element element, int styleIdx) {
        if (element == null || worldPage == null || styleIdx < 0 || styleIdx >= WorldEditor.get().BORDER_STYLE_LABELS.length) {
            return;
        }
        WorldEditor.get().pushWorldUndo("描边", "border", List.of(element.id()));
        Object border = elementBorder(element);
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        if (border instanceof Map<?, ?> bm) {
            bm.forEach((k, v) -> m.put(String.valueOf(k), v));
        }
        m.remove("dash");
        m.remove("dashLen");
        m.remove("double");
        switch (styleIdx) {
            case 1 -> {
                m.put("dash", true);
                m.put("dashLen", 0.1);
            }
            case 2 -> {
                m.put("dash", true);
                m.put("dashLen", 0.04);
            }
            case 3 -> m.put("double", true);
            default -> {
            }
        }
        if (!m.containsKey("color")) {
            m.put("color", "#FFD700");
        }
        if (!m.containsKey("width")) {
            m.put("width", 0.02);
        }
        writeWorldBorder(element, m);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§e[OpenDreamCore] §f描边样式: "
                        + WorldEditor.get().BORDER_STYLE_LABELS[styleIdx] + "（保存后写回页面文件）"), false);
    }

    /** 描边流光速度档（flow:true + flowSpeed ms/圈；保留其它属性；可撤消）。 */
    void applyBorderFlowSpeed(Element element, long ms) {
        if (element == null || worldPage == null) {
            return;
        }
        WorldEditor.get().pushWorldUndo("描边", "border", List.of(element.id()));
        Object border = elementBorder(element);
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        if (border instanceof Map<?, ?> bm) {
            bm.forEach((k, v) -> m.put(String.valueOf(k), v));
        }
        m.put("flow", true);
        m.put("flowSpeed", ms);
        if (!m.containsKey("color")) {
            m.put("color", "#FFD700");
        }
        if (!m.containsKey("width")) {
            m.put("width", 0.02);
        }
        writeWorldBorder(element, m);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§e[OpenDreamCore] §f描边流光速度: " + ms + " ms/圈"
                        + "（保存后写回页面文件）"), false);
    }

    /** 流光速度实时微调（Ctrl+,/. 每次 ±50ms，下限 100；保留其它属性；可撤消）。 */

    /** 流光段相位微调（Shift+,/. 每次 ±0.05 周长比例，循环 0~1；保留其它属性；可撤消）。 */

    /** 流光段长实时微调（Alt+,/. 每次 ±0.02 周长比例，0.02~0.45 钳制；保留其它属性；可撤消）。 */

    /** 流光段间距实时微调（Ctrl+Shift+,/. 每次 ±0.03 周长比例，0.03~0.6 钳制；保留其它属性；可撤消）。 */

    /** 副色段独立相位微调（Ctrl+Shift+Alt+,/. 每次 ±0.05 周长比例，循环 0~1；仅双色流光生效；可撤消）。 */

    /** 流光段数快捷微调（9/0 每次 ±1 段，1~8 钳制；0 = 自动时从 1 起；保留其它属性；可撤消）。 */

    /** 元素 hover 加速是否开启（border.hoverBoost，缺省 true）。 */
    private boolean isWorldFlowHoverBoostOn(Element element) {
        if (element == null) {
            return true;
        }
        Object border = elementBorder(element);
        if (border instanceof Map<?, ?> bm && bm.get("hoverBoost") != null) {
            return Boolean.parseBoolean(String.valueOf(bm.get("hoverBoost")));
        }
        return true;
    }

    /** 流光 hover 加速开关（L 键：border.hoverBoost 增删，默认开；保留其它属性；可撤消）。 */
    void toggleWorldFlowHoverBoost() {
        if (worldPage == null) {
            return;
        }
        Element element = findElement(worldPage, WorldEditor.get().worldEditSelected);
        if (element == null) {
            return;
        }
        WorldEditor.get().pushWorldUndo("描边", "border", List.of(element.id()));
        Object border = elementBorder(element);
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        if (border instanceof Map<?, ?> bm) {
            bm.forEach((k, v) -> m.put(String.valueOf(k), v));
        }
        boolean on = isWorldFlowHoverBoostOn(element);
        m.put("flow", true);
        if (on) {
            m.put("hoverBoost", false);
        } else {
            m.remove("hoverBoost");
        }
        if (!m.containsKey("color")) {
            m.put("color", "#FFD700");
        }
        if (!m.containsKey("width")) {
            m.put("width", 0.02);
        }
        writeWorldBorder(element, m);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§e[OpenDreamCore] §f流光 hover 加速: "
                        + (on ? "关" : "开") + "（悬停不再加速/拉长段）"), false);
    }

    /** 描边松手：宽度记入未保存属性（保存时写回页面文件）。 */

    /** 鼠标 scaled 坐标 {x, y}。 */
    static double[] scaledMouse(Minecraft mc) {
        var window = mc.getWindow();
        double scale = window.getGuiScaledWidth() / (double) window.getScreenWidth();
        return new double[]{mc.mouseHandler.xpos(), mc.mouseHandler.ypos()}; // xpos() 已是 GUI 坐标(修复双重缩放)
    }

    /** 拖入创建落点：鼠标射线与"过聚焦面板锚点、法线朝相机"的平面交点（绝对世界坐标）。 */

    /** 框选提交：投影中心在框内的可见元素加入多选（空框清空多选）。 */

    /** 框选预览更新：投影中心在框内的可见元素实时加入预览集（渲染选中框同步高亮）。 */

    /** 递归收集投影中心在框内的元素 id。 */
    static void collectMarqueeHits(List<RenderNode> nodes, net.minecraft.client.Camera camera,
                                           net.minecraft.world.phys.Vec3 anchor,
                                           java.util.Map<String, Object> vars,
                                           double x0, double y0, double x1, double y1,
                                           double scaledW, double scaledH, String activeTab,
                                           List<String> out) {
        if (nodes == null) {
            return;
        }
        for (RenderNode node : nodes) {
            if (!WorldHologram.tabVisible(node, activeTab)) {
                continue;
            }
            Object raw = node.props().get("hologram");
            if (raw instanceof Map<?, ?> holo) {
                double x = WorldHologram.holoNum(holo, "x", 0, vars);
                double y = WorldHologram.holoNum(holo, "y", 0, vars);
                double z = WorldHologram.holoNum(holo, "z", 0, vars);
                net.minecraft.world.phys.Vec3 center = anchor.add(x, y, z);
                double[] s = project(camera, center, scaledW, scaledH);
                if (s != null && s[0] >= x0 && s[0] <= x1 && s[1] >= y0 && s[1] <= y1) {
                    out.add(node.id());
                }
            }
            collectMarqueeHits(node.children(), camera, anchor, vars, x0, y0, x1, y1, scaledW, scaledH,
                    activeTab, out);
        }
    }

    /** 右键/编辑：切换元素运行时可见（会话级，不持久化；隐藏仍可编辑操作）。 */
    public boolean toggleWorldElementHide(String elementId) {
        if (worldPage == null || elementId == null) {
            return false;
        }
        String k = wkey(worldPage.id() == null ? "world" : worldPage.id(), elementId);
        Boolean[] st = WorldEditor.get().worldElementStates.get(k);
        boolean curVisible = st == null || st[0] == null || st[0];
        if (st == null) {
            st = new Boolean[2];
            WorldEditor.get().worldElementStates.put(k, st);
        }
        st[0] = !curVisible;
        invalidateLayout(worldPage);
        worldNodes = layoutPage(worldPage, 800, 600);
        return true;
    }

    /** 持久隐藏/显示（hologram.hidden，保存写回页面文件；可撤消）。 */
    public void toggleWorldElementHidden(String elementId) {
        if (!WorldEditor.get().worldEditMode || worldPage == null || elementId == null) {
            return;
        }
        var el = findElement(worldPage, elementId);
        if (el == null) {
            return;
        }
        Object raw = el.props().get("hologram");
        if (!(raw instanceof Map<?, ?> holo)) {
            return;
        }
        boolean hidden = Boolean.parseBoolean(String.valueOf(holo.get("hidden")));
        WorldEditor.get().pushWorldUndo("持久隐藏", null, List.of(elementId));
        Map<Object, Object> copy = new java.util.LinkedHashMap<>(holo);
        if (hidden) {
            copy.remove("hidden");
        } else {
            copy.put("hidden", true);
        }
        el.props().put("hologram", copy);
        // 显式写入 false/true（保存烘焙可靠）
        WorldEditor.get().worldEditProps.computeIfAbsent(elementId, k -> new ConcurrentHashMap<>())
                .put("hologram.hidden", hidden ? "false" : "true");
        refreshCreateBlock(elementId);
        invalidateLayout(worldPage);
        worldNodes = layoutPage(worldPage, 800, 600);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§7[OpenDreamCore] §f元素已" + (hidden ? "显示" : "隐藏")
                        + "（hologram.hidden，保存写回页面文件）"), false);
    }

    /** 重置元素变换：yaw=0、scale=1、尺寸回默认（text 2.0×0.25 / 其它 1.0×1.0）；仅变更项写入；可撤消。 */
    public void resetWorldElementTransform(String elementId) {
        if (!WorldEditor.get().worldEditMode || worldPage == null || elementId == null) {
            return;
        }
        var el = findElement(worldPage, elementId);
        if (el == null) {
            return;
        }
        Object raw = el.props().get("hologram");
        if (!(raw instanceof Map<?, ?> holo)) {
            return;
        }
        String type = String.valueOf(el.props().get("type"));
        boolean text = "text".equals(type);
        double dw = text ? 2.0 : 1.0;
        double dh = text ? 0.25 : 1.0;
        var vars = worldPage.variables();
        Map<Object, Object> copy = new java.util.LinkedHashMap<>(holo);
        Map<String, String> propsOut = new java.util.LinkedHashMap<>();
        if (holo.get("yaw") != null) {
            copy.put("yaw", 0.0);
            propsOut.put("hologram.yaw", "0");
        }
        if (holo.get("scale") != null) {
            copy.put("scale", 1.0);
            propsOut.put("hologram.scale", "1");
        }
        double cw = WorldHologram.holoNum(holo, "width", dw, vars);
        double ch = WorldHologram.holoNum(holo, "height", dh, vars);
        if (Math.abs(cw - dw) > 1e-9) {
            copy.put("width", dw);
            propsOut.put("hologram.width", String.valueOf(dw));
        }
        if (Math.abs(ch - dh) > 1e-9) {
            copy.put("height", dh);
            propsOut.put("hologram.height", String.valueOf(dh));
        }
        if (propsOut.isEmpty()) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§e[OpenDreamCore] §f元素已是默认变换"), false);
            return;
        }
        WorldEditor.get().pushWorldUndo("重置变换", null, List.of(elementId));
        el.props().put("hologram", copy);
        WorldEditor.get().worldEditDirty.put(elementId, new double[]{
                WorldHologram.holoNum(copy, "x", 0, vars),
                WorldHologram.holoNum(copy, "y", 0, vars),
                WorldHologram.holoNum(copy, "z", 0, vars)});
        propsOut.forEach((k, v) -> WorldEditor.get().worldEditProps
                .computeIfAbsent(elementId, k2 -> new ConcurrentHashMap<>()).put(k, v));
        refreshCreateBlock(elementId);
        invalidateLayout(worldPage);
        worldNodes = layoutPage(worldPage, 800, 600);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§a[OpenDreamCore] §f已重置变换: " + String.join("+", propsOut.keySet())
                        + "（Ctrl+Z 撤消）"), false);
    }

    /** 阵列复制：当前选区（组/多选/单选）沿 x 轴复制 count 份（间距 gap，缺省 = 最大宽度 1.5 倍）；副本接管多选；可撤消。 */
    public void arrayDuplicateWorldSelection(String spec) {
        if (!WorldEditor.get().worldEditMode || worldPage == null || WorldEditor.get().worldEditSelected == null) {
            return;
        }
        int count = 2;
        double gap = -1;
        try {
            String[] parts = spec == null ? new String[0] : spec.trim().split(":");
            count = Math.max(1, Math.min(32, Integer.parseInt(parts[0].trim())));
            if (parts.length > 1) {
                gap = Double.parseDouble(parts[1].trim());
            }
        } catch (Exception e) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§c[OpenDreamCore] §f格式: 份数:间距（如 4:1.5，间距缺省 = 宽 1.5 倍）"), false);
            return;
        }
        java.util.List<String> members = new java.util.ArrayList<>();
        String grp = worldGroupOf(WorldEditor.get().worldEditSelected);
        if (grp != null && worldGroupMembers(grp).size() > 1) {
            members.addAll(worldGroupMembers(grp));
        } else if (worldEditMulti.size() >= 2) {
            members.addAll(worldEditMulti);
        } else {
            members.add(WorldEditor.get().worldEditSelected);
        }
        int skipped = members.size();
        members = filterLocked(members);
        skipped -= members.size();
        if (members.isEmpty()) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§e[OpenDreamCore] §f目标元素全部锁定，已跳过"), false);
            return;
        }
        var vars = worldPage.variables();
        double maxW = 0;
        for (String id : members) {
            var el = findElement(worldPage, id);
            if (el == null) {
                continue;
            }
            Object raw = el.props().get("hologram");
            if (!(raw instanceof Map<?, ?> holo)) {
                continue;
            }
            String type = String.valueOf(el.props().get("type"));
            maxW = Math.max(maxW, WorldHologram.holoNum(holo, "width",
                    "text".equals(type) ? 2.0 : 1.0, vars));
        }
        double step = gap >= 0 ? gap : Math.max(0.5, maxW * 1.5);
        java.util.List<String> newIds = new java.util.ArrayList<>();
        // 先快照全部新 id（撤消即移除）
        for (int i = 1; i <= count; i++) {
            for (String id : members) {
                newIds.add(uniqueWorldElementId());
            }
        }
        WorldEditor.get().pushWorldUndo("阵列复制", null, newIds);
        int idx = 0;
        for (int i = 1; i <= count; i++) {
            for (String id : members) {
                var src = findElement(worldPage, id);
                if (src == null) {
                    idx++;
                    continue;
                }
                String newId = newIds.get(idx++);
                Element copy = copyElementTree(src, newId, new java.util.HashMap<>());
                Object raw = copy.props().get("hologram");
                if (raw instanceof Map<?, ?> h) {
                    Map<Object, Object> holo = new java.util.LinkedHashMap<>(h);
                    double x = WorldHologram.holoNum(holo, "x", 0, vars);
                    holo.put("x", Math.round((x + i * step) * 100) / 100.0);
                    copy.props().put("hologram", holo);
                }
                worldPage.elements().add(copy);
                WorldEditor.get().worldEditProps.computeIfAbsent(newId, k -> new ConcurrentHashMap<>())
                        .put("__create__", elementYamlBlockFromProps(newId, copy));
            }
        }
        worldEditMulti.clear();
        worldEditMulti.addAll(newIds);
        WorldEditor.get().worldEditSelected = newIds.isEmpty() ? WorldEditor.get().worldEditSelected : newIds.get(newIds.size() - 1);
        invalidateLayout(worldPage);
        worldNodes = layoutPage(worldPage, 800, 600);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§a[OpenDreamCore] §f阵列复制: " + members.size() + " 元素 ×" + count
                        + " 份（间距 " + Math.round(step * 100) / 100.0 + "；副本已接管多选，Ctrl+Z 撤消"
                        + (skipped > 0 ? "；跳过 " + skipped + " 锁定" : "") + "）"), false);
    }

    /** 对齐到网格：选区（组/多选/单选）位置吸附到最近网格点（步长 = 编辑网格步长，缺省 0.25）；可撤消。 */
    public void snapWorldSelectionToGrid() {
        snapWorldSelectionToGrid(null);
    }

    /** 对齐到网格（显式成员列表；null = 从当前选区推导）。 */
    public void snapWorldSelectionToGrid(java.util.List<String> explicitMembers) {
        if (!WorldEditor.get().worldEditMode || worldPage == null || WorldEditor.get().worldEditSelected == null) {
            return;
        }
        double step = WorldEditor.get().worldEditGridStep();
        if (step <= 0) {
            step = 0.25;
        }
        java.util.List<String> members;
        if (explicitMembers != null && !explicitMembers.isEmpty()) {
            members = new java.util.ArrayList<>(explicitMembers);
        } else {
            members = new java.util.ArrayList<>();
            String grp = worldGroupOf(WorldEditor.get().worldEditSelected);
            if (grp != null && worldGroupMembers(grp).size() > 1) {
                members.addAll(worldGroupMembers(grp));
            } else if (worldEditMulti.size() >= 2) {
                members.addAll(worldEditMulti);
            } else {
                members.add(WorldEditor.get().worldEditSelected);
            }
        }
        int skipped = members.size();
        members = filterLocked(members);
        skipped -= members.size();
        if (members.isEmpty()) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§e[OpenDreamCore] §f目标元素全部锁定，已跳过"), false);
            return;
        }
        var vars = worldPage.variables();
        WorldEditor.get().pushWorldUndo("对齐网格", null, members);
        int changed = 0;
        for (String id : members) {
            var el = findElement(worldPage, id);
            if (el == null) {
                continue;
            }
            Object raw = el.props().get("hologram");
            if (!(raw instanceof Map<?, ?> holo)) {
                continue;
            }
            double x = WorldHologram.holoNum(holo, "x", 0, vars);
            double y = WorldHologram.holoNum(holo, "y", 0, vars);
            double nx = Math.round(x / step) * step;
            double ny = Math.round(y / step) * step;
            if (Math.abs(nx - x) < 1e-9 && Math.abs(ny - y) < 1e-9) {
                continue;
            }
            Map<Object, Object> copy = new java.util.LinkedHashMap<>(holo);
            copy.put("x", Math.round(nx * 100) / 100.0);
            copy.put("y", Math.round(ny * 100) / 100.0);
            el.props().put("hologram", copy);
            WorldEditor.get().worldEditDirty.put(id, new double[]{
                    WorldHologram.holoNum(copy, "x", 0, vars),
                    WorldHologram.holoNum(copy, "y", 0, vars),
                    WorldHologram.holoNum(copy, "z", 0, vars)});
            refreshCreateBlock(id);
            changed++;
        }
        if (changed > 0) {
            invalidateLayout(worldPage);
            worldNodes = layoutPage(worldPage, 800, 600);
        }
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§a[OpenDreamCore] §f已对齐网格: " + changed + " 个元素（步长 "
                        + Math.round(step * 100) / 100.0 + "；Ctrl+Z 撤消"
                        + (skipped > 0 ? "；跳过 " + skipped + " 锁定" : "") + "）"), false);
    }

    /** 选中整组：当前元素所在组全部成员进入多选（组一眼可见、批量操作直达）。 */
    public void selectWorldGroup(String elementId) {
        if (worldPage == null || elementId == null) {
            return;
        }
        String grp = worldGroupOf(elementId);
        if (grp == null) {
            return;
        }
        java.util.List<String> members = worldGroupMembers(grp);
        worldEditMulti.clear();
        worldEditMulti.addAll(members);
        if (!members.isEmpty()) {
            WorldEditor.get().worldEditSelected = members.get(members.size() - 1);
        }
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§a[OpenDreamCore] §f已选中整组（" + members.size() + " 个成员）"), false);
    }

    /** 页面已知页签列表（元素 tab: 属性去重，含嵌套）。 */
    public java.util.List<String> worldTabList(String pageId) {
        java.util.List<String> tabs = new java.util.ArrayList<>();
        WorldPanel panel = findWorldPanel(pageId);
        if (panel == null || panel.page == null) {
            return tabs;
        }
        java.util.Set<String> set = new java.util.LinkedHashSet<>();
        collectTabs(panel.page.elements(), set);
        tabs.addAll(set);
        return tabs;
    }

    private static void collectTabs(java.util.List<Element> els, java.util.Set<String> out) {
        if (els == null) {
            return;
        }
        for (Element el : els) {
            Object t = el.props().get("tab");
            if (t != null && !String.valueOf(t).isBlank()) {
                out.add(String.valueOf(t));
            }
            collectTabs(el.children(), out);
        }
    }

    /** 设置元素页签归属（tab prop；空 = 公共区；可撤消，保存写回）。 */
    public void setWorldElementTab(String elementId, String tab) {
        if (!WorldEditor.get().worldEditMode || worldPage == null || elementId == null) {
            return;
        }
        var el = findElement(worldPage, elementId);
        if (el == null) {
            return;
        }
        String cur = el.props().get("tab") == null ? null : String.valueOf(el.props().get("tab"));
        String next = tab == null || tab.isBlank() ? null : tab.trim();
        if (java.util.Objects.equals(cur, next == null ? null : next)) {
            return;
        }
        WorldEditor.get().pushWorldUndo("页签归属", null, List.of(elementId));
        if (next == null) {
            el.props().remove("tab");
        } else {
            el.props().put("tab", next);
        }
        WorldEditor.get().worldEditProps.computeIfAbsent(elementId, k -> new ConcurrentHashMap<>())
                .put("tab", next == null ? "" : next);
        refreshCreateBlock(elementId);
        invalidateLayout(worldPage);
        worldNodes = layoutPage(worldPage, 800, 600);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§a[OpenDreamCore] §f页签归属: " + elementId + " → "
                        + (next == null ? "公共区" : next) + "（Ctrl+Z 撤消）"), false);
    }

    /** 设置选区页签归属（tab prop；空 = 公共区；整体一步撤消，保存写回）。 */
    public void setWorldElementTabBatch(java.util.List<String> ids, String tab) {
        if (!WorldEditor.get().worldEditMode || worldPage == null || ids == null || ids.isEmpty()) {
            return;
        }
        String next = tab == null || tab.isBlank() ? null : tab.trim();
        java.util.List<String> alive = new java.util.ArrayList<>();
        for (String id : ids) {
            if (findElement(worldPage, id) != null) {
                alive.add(id);
            }
        }
        if (alive.isEmpty()) {
            return;
        }
        WorldEditor.get().pushWorldUndo("批量页签", null, alive);
        for (String id : alive) {
            var el = findElement(worldPage, id);
            if (el == null) {
                continue;
            }
            if (next == null) {
                el.props().remove("tab");
            } else {
                el.props().put("tab", next);
            }
            WorldEditor.get().worldEditProps.computeIfAbsent(id, k -> new ConcurrentHashMap<>())
                    .put("tab", next == null ? "" : next);
            refreshCreateBlock(id);
        }
        invalidateLayout(worldPage);
        worldNodes = layoutPage(worldPage, 800, 600);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§a[OpenDreamCore] §f页签归属: " + alive.size() + " 元素 → "
                        + (next == null ? "公共区" : next) + "（Ctrl+Z 撤消）"), false);
    }

    /** 拍平 Z：元素 z → 0（面板平面）；可撤消。 */
    public void setWorldElementZToOne(String elementId) {
        if (!WorldEditor.get().worldEditMode || worldPage == null || elementId == null) {
            return;
        }
        var el = findElement(worldPage, elementId);
        if (el == null) {
            return;
        }
        Object raw = el.props().get("hologram");
        if (!(raw instanceof Map<?, ?> holo)) {
            return;
        }
        var vars = worldPage.variables();
        double z = WorldHologram.holoNum(holo, "z", 0, vars);
        if (Math.abs(z) < 1e-9) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§e[OpenDreamCore] §f元素已在面板平面（z=0）"), false);
            return;
        }
        WorldEditor.get().pushWorldUndo("拍平Z", null, List.of(elementId));
        Map<Object, Object> copy = new java.util.LinkedHashMap<>(holo);
        copy.put("z", 0.0);
        el.props().put("hologram", copy);
        WorldEditor.get().worldEditDirty.put(elementId, new double[]{
                WorldHologram.holoNum(copy, "x", 0, vars),
                WorldHologram.holoNum(copy, "y", 0, vars),
                WorldHologram.holoNum(copy, "z", 0, vars)});
        WorldEditor.get().worldEditProps.computeIfAbsent(elementId, k -> new ConcurrentHashMap<>())
                .put("hologram.z", "0");
        refreshCreateBlock(elementId);
        invalidateLayout(worldPage);
        worldNodes = layoutPage(worldPage, 800, 600);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§a[OpenDreamCore] §f已拍平Z: " + elementId + " → 0（Ctrl+Z 撤消）"), false);
    }

    /** 拍平 Z（选区：组/多选/单选全部 z→0 面板平面；锁定跳过；可撤消）。 */
    public void flattenWorldZSelection() {
        flattenWorldZMembers(null);
    }

    /** 拍平 Z（显式成员列表；null = 从当前选区推导）。 */
    public void flattenWorldZMembers(java.util.List<String> explicitMembers) {
        if (!WorldEditor.get().worldEditMode || worldPage == null || WorldEditor.get().worldEditSelected == null) {
            return;
        }
        java.util.List<String> members;
        if (explicitMembers != null && !explicitMembers.isEmpty()) {
            members = new java.util.ArrayList<>(explicitMembers);
        } else {
            members = new java.util.ArrayList<>();
            String grp = worldGroupOf(WorldEditor.get().worldEditSelected);
            if (grp != null && worldGroupMembers(grp).size() > 1) {
                members.addAll(worldGroupMembers(grp));
            } else if (worldEditMulti.size() >= 2) {
                members.addAll(worldEditMulti);
            } else {
                members.add(WorldEditor.get().worldEditSelected);
            }
        }
        int skipped = members.size();
        members = filterLocked(members);
        skipped -= members.size();
        if (members.isEmpty()) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§e[OpenDreamCore] §f目标元素全部锁定，已跳过"), false);
            return;
        }
        var vars = worldPage.variables();
        java.util.List<String> alive = new java.util.ArrayList<>();
        for (String id : members) {
            var el = findElement(worldPage, id);
            if (el == null) {
                continue;
            }
            Object raw = el.props().get("hologram");
            if (!(raw instanceof Map<?, ?> holo)) {
                continue;
            }
            if (Math.abs(WorldHologram.holoNum(holo, "z", 0, vars)) < 1e-9) {
                continue;
            }
            alive.add(id);
        }
        if (alive.isEmpty()) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§e[OpenDreamCore] §f选中元素都已在面板平面（z=0）"), false);
            return;
        }
        WorldEditor.get().pushWorldUndo("拍平Z", null, alive);
        for (String id : alive) {
            var el = findElement(worldPage, id);
            Object raw = el.props().get("hologram");
            Map<?, ?> holo = (Map<?, ?>) raw;
            Map<Object, Object> copy = new java.util.LinkedHashMap<>(holo);
            copy.put("z", 0.0);
            el.props().put("hologram", copy);
            WorldEditor.get().worldEditDirty.put(id, new double[]{
                    WorldHologram.holoNum(copy, "x", 0, vars),
                    WorldHologram.holoNum(copy, "y", 0, vars),
                    WorldHologram.holoNum(copy, "z", 0, vars)});
            WorldEditor.get().worldEditProps.computeIfAbsent(id, k -> new ConcurrentHashMap<>())
                    .put("hologram.z", "0");
            refreshCreateBlock(id);
        }
        invalidateLayout(worldPage);
        worldNodes = layoutPage(worldPage, 800, 600);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§a[OpenDreamCore] §f已拍平Z: " + alive.size() + " 个元素 → 0（Ctrl+Z 撤消"
                        + (skipped > 0 ? "；跳过 " + skipped + " 锁定" : "") + "）"), false);
    }

    /** 选中元素第一个子元素（树导航）。 */
    public void selectWorldFirstChild(String elementId) {
        if (worldPage == null || elementId == null) {
            return;
        }
        var el = findElement(worldPage, elementId);
        if (el == null || el.children().isEmpty()) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§e[OpenDreamCore] §f该元素没有子元素"), false);
            return;
        }
        String child = el.children().get(0).id();
        worldEditMulti.clear();
        WorldEditor.get().worldEditSelected = child;
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§a[OpenDreamCore] §f已选中子元素: " + child), false);
    }

    /** 选中元素父元素（parent prop；树导航）。 */
    public void selectWorldParent(String elementId) {
        if (worldPage == null || elementId == null) {
            return;
        }
        var el = findElement(worldPage, elementId);
        if (el == null) {
            return;
        }
        Object p = el.props().get("parent");
        if (p == null) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§e[OpenDreamCore] §f该元素没有父元素"), false);
            return;
        }
        String pid = String.valueOf(p);
        if (findElement(worldPage, pid) == null) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§e[OpenDreamCore] §f父元素不可用: " + pid), false);
            return;
        }
        worldEditMulti.clear();
        WorldEditor.get().worldEditSelected = pid;
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§a[OpenDreamCore] §f已选中父元素: " + pid), false);
    }

    /** 选中下一个/上一个同级元素（树导航；delta=±1，环回）。 */
    public void selectWorldSibling(String elementId, int delta) {
        if (worldPage == null || elementId == null) {
            return;
        }
        var el = findElement(worldPage, elementId);
        if (el == null) {
            return;
        }
        Object p = el.props().get("parent");
        java.util.List<Element> siblings;
        if (p == null) {
            // 顶层元素：在同一级元素列表（page.elements 中同样无 parent 的）按序导航
            siblings = new java.util.ArrayList<>();
            for (Element e : worldPage.elements()) {
                if (e.props().get("parent") == null) {
                    siblings.add(e);
                }
            }
        } else {
            var parent = findElement(worldPage, String.valueOf(p));
            if (parent == null) {
                return;
            }
            siblings = parent.children();
        }
        if (siblings.size() < 2) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§e[OpenDreamCore] §f没有同级元素"), false);
            return;
        }
        int idx = -1;
        for (int i = 0; i < siblings.size(); i++) {
            if (siblings.get(i).id().equals(elementId)) {
                idx = i;
                break;
            }
        }
        if (idx < 0) {
            return;
        }
        int next = ((idx + delta) % siblings.size() + siblings.size()) % siblings.size();
        String nid = siblings.get(next).id();
        worldEditMulti.clear();
        WorldEditor.get().worldEditSelected = nid;
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§a[OpenDreamCore] §f已选中同级: " + nid), false);
    }

    /** 设置元素位置（"x,y" 输入，相对面板锚点；可撤消，保存写回）。 */
    public void setWorldElementPosition(String elementId, String xy) {
        if (!WorldEditor.get().worldEditMode || worldPage == null || elementId == null || xy == null) {
            return;
        }
        double x;
        double y;
        try {
            String[] parts = xy.trim().split(",");
            x = Double.parseDouble(parts[0].trim());
            y = parts.length > 1 ? Double.parseDouble(parts[1].trim()) : x;
        } catch (Exception e) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§c[OpenDreamCore] §f格式: x,y（如 2.5,-1）"), false);
            return;
        }
        var el = findElement(worldPage, elementId);
        if (el == null) {
            return;
        }
        Object raw = el.props().get("hologram");
        if (!(raw instanceof Map<?, ?> holo)) {
            return;
        }
        var vars = worldPage.variables();
        WorldEditor.get().pushWorldUndo("定位", null, List.of(elementId));
        Map<Object, Object> copy = new java.util.LinkedHashMap<>(holo);
        copy.put("x", Math.round(x * 100) / 100.0);
        copy.put("y", Math.round(y * 100) / 100.0);
        el.props().put("hologram", copy);
        WorldEditor.get().worldEditDirty.put(elementId, new double[]{
                WorldHologram.holoNum(copy, "x", 0, vars),
                WorldHologram.holoNum(copy, "y", 0, vars),
                WorldHologram.holoNum(copy, "z", 0, vars)});
        refreshCreateBlock(elementId);
        invalidateLayout(worldPage);
        worldNodes = layoutPage(worldPage, 800, 600);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§a[OpenDreamCore] §f已定位 " + elementId + " → "
                        + Math.round(x * 100) / 100.0 + ", " + Math.round(y * 100) / 100.0
                        + "（Ctrl+Z 撤消）"), false);
    }

    /** 组元素批量会话隐藏/显示（右键「隐藏/显示整组」；会话级，不持久化）。 */
    public void toggleWorldGroupHide(String elementId) {
        if (worldPage == null || elementId == null) {
            return;
        }
        String grp = worldGroupOf(elementId);
        if (grp == null) {
            return;
        }
        java.util.List<String> members = worldGroupMembers(grp);
        String pageKey = worldPage.id() == null ? "world" : worldPage.id();
        // 若任一成员当前可见 → 隐藏整组；否则显示整组
        boolean anyVisible = false;
        for (String m : members) {
            Boolean[] st = WorldEditor.get().worldElementStates.get(wkey(pageKey, m));
            if (st == null || st[0] == null || st[0]) {
                anyVisible = true;
                break;
            }
        }
        boolean hideNow = anyVisible;
        for (String m : members) {
            String k = wkey(pageKey, m);
            Boolean[] st = WorldEditor.get().worldElementStates.get(k);
            if (st == null) {
                st = new Boolean[2];
                WorldEditor.get().worldElementStates.put(k, st);
            }
            st[0] = !hideNow;
        }
        invalidateLayout(worldPage);
        worldNodes = layoutPage(worldPage, 800, 600);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§7[OpenDreamCore] §f整组已" + (hideNow ? "隐藏" : "显示")
                        + "（" + members.size() + " 个成员，会话级）"), false);
    }

    /** 元素所在面板组（hologram.group），无则 null。 */
    String worldGroupOf(String elementId) {
        RenderNode node = findWorldNode(elementId);
        if (node == null) {
            return null;
        }
        Object raw = node.props().get("hologram");
        Map<?, ?> holo = raw instanceof Map<?, ?> h ? h : Map.of();
        Object group = holo.get("group");
        return group == null ? null : String.valueOf(group);
    }

    /** 组内全部元素 id（含自己；递归世界节点树）。 */
    List<String> worldGroupMembers(String group) {
        List<String> out = new java.util.ArrayList<>();
        collectGroup(worldNodes, group, out);
        return out;
    }

    /** 元素父链（根 → 自身；沿 parent() 上溯，防环）。 */
    private List<String> worldParentChain(String elementId) {
        List<String> chain = new java.util.ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        String cur = elementId;
        while (cur != null && seen.add(cur)) {
            chain.add(cur);
            var el = findElement(worldPage, cur);
            if (el == null) {
                break;
            }
            cur = el.parent();
        }
        java.util.Collections.reverse(chain);
        return chain;
    }

    private static void collectGroup(List<RenderNode> nodes, String group, List<String> out) {
        if (nodes == null) {
            return;
        }
        for (RenderNode node : nodes) {
            Object raw = node.props().get("hologram");
            if (raw instanceof Map<?, ?> h && group.equals(String.valueOf(h.get("group")))) {
                out.add(node.id());
            }
            collectGroup(node.children(), group, out);
        }
    }

    // ---------- 世界面板 WYSIWYG 编辑 ----------

    /** 编辑模式：方向键微调选中元素（←→ = x，↑↓ = y，Shift+↑↓ = z，按住 200ms 自动重复；锁定元素除外）。 */

    /** 复制单个属性值（属性面板 [复制]；编辑属性时 [粘贴值] 应用到其它元素）。 */

    public boolean hasWorldPropClipboard() {
        return WorldEditor.get().worldPropClipboard != null;
    }

    public String getWorldPropClipboard() {
        return WorldEditor.get().worldPropClipboard;
    }

    public String getWorldPropClipboardPath() {
        return WorldEditor.get().worldPropClipboardPath;
    }

    /** 编辑模式：移动元素（写入 hologram.x/y/z + 记入未保存列表 + 重建布局；可撤消，连续微调合并）。 */

    /** 批量微调：整组同偏移（一步撤消 + 连续合并；锁定成员跳过）。 */

    /** 新建/复制元素的 __create__ YAML 块按当前 props 重新生成（拖拽/微调后位置同步）。 */
    void refreshCreateBlock(String elementId) {
        Map<String, String> props = WorldEditor.get().worldEditProps.get(elementId);
        if (props == null || !props.containsKey("__create__")) {
            return;
        }
        var element = findElement(worldPage, elementId);
        if (element != null) {
            props.put("__create__", elementYamlBlockFromProps(elementId, element));
        }
    }

    /** 工具栏保存：未保存的位置/属性/增删/页面级选项发回服务端（写回页面 YAML，成功后重发页面清空本地编辑态）。 */

    /** 值 → JSON 片段（递归 map/list/标量；字符串转义）。 */
    static String toJsonValue(Object v) {
        if (v == null) {
            return "null";
        }
        if (v instanceof Number || v instanceof Boolean) {
            return String.valueOf(v);
        }
        if (v instanceof Map<?, ?> m) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (java.util.Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append('"').append(String.valueOf(e.getKey()).replace("\"", "\\\"")).append("\":")
                        .append(toJsonValue(e.getValue()));
            }
            return sb.append('}').toString();
        }
        if (v instanceof List<?> l) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object o : l) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append(toJsonValue(o));
            }
            return sb.append(']').toString();
        }
        String s = String.valueOf(v);
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }

    /** 元素 → JSON map（id/type/props/actions/children）。 */
    private static java.util.Map<String, Object> elementToJson(Element el) {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("id", el.id());
        m.put("type", el.type());
        m.put("props", el.props());
        if (!el.actions().isEmpty()) {
            m.put("actions", el.actions());
        }
        if (!el.children().isEmpty()) {
            java.util.List<Object> kids = new java.util.ArrayList<>();
            for (Element child : el.children()) {
                kids.add(elementToJson(child));
            }
            m.put("children", kids);
        }
        return m;
    }

    /** 导出当前页面运行时状态为 JSON（含运行时编辑）：写文件（_page_snapshots）+ 剪贴板；返回 json 或 null。 */
    public String exportWorldPageJson() {
        if (worldPage == null) {
            return null;
        }
        java.util.Map<String, Object> root = new java.util.LinkedHashMap<>();
        root.put("id", worldPage.id());
        root.put("title", worldPage.title());
        root.put("options", worldPage.options());
        java.util.Map<String, Object> vars = new java.util.LinkedHashMap<>();
        worldPage.variables().forEach((k, v) -> vars.put(k, v));
        root.put("variables", vars);
        java.util.List<Object> els = new java.util.ArrayList<>();
        for (Element el : worldPage.elements()) {
            els.add(elementToJson(el));
        }
        root.put("elements", els);
        String json = toJsonValue(root);
        try {
            java.nio.file.Path dir = Minecraft.getInstance().gameDirectory.toPath()
                    .resolve("OpenDreamCore").resolve("UI").resolve("_page_snapshots");
            java.nio.file.Files.createDirectories(dir);
            String stamp = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss").format(new java.util.Date());
            String name = (worldPage.id() == null ? "world" : worldPage.id()) + "_" + stamp + ".json";
            java.nio.file.Files.writeString(dir.resolve(name), json);
            Minecraft.getInstance().keyboardHandler.setClipboard(json);
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§a[OpenDreamCore] §f页面状态已导出: " + name
                            + "（已复制到剪贴板）"), false);
        } catch (Exception e) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§c[OpenDreamCore] §f导出失败: " + e.getMessage()), false);
        }
        return json;
    }

    /** 从剪贴板导入页面级状态（导出按钮生成的 JSON：options/variables/title；元素保持当前）。 */
    public void importWorldPageJson() {
        importWorldPageJson(false);
    }

    /** Shift+点击导入 = 追加模式：仅元素段，id 冲突加后缀追加（不替换现有元素）。 */
    public void importWorldPageJsonAppend() {
        importWorldPageJson(true);
    }

    /** 导入主逻辑；append = 元素段追加（id 冲突加后缀）而非整体替换。 */
    private void importWorldPageJson(boolean append) {
        if (!WorldEditor.get().worldEditMode || worldPage == null) {
            return;
        }
        String text = Minecraft.getInstance().keyboardHandler.getClipboard();
        if (text == null || text.isBlank()) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§c[OpenDreamCore] §f剪贴板为空"), false);
            return;
        }
        try {
            Object parsed = new org.yaml.snakeyaml.Yaml().load(text);
            if (!(parsed instanceof Map<?, ?> root)) {
                Minecraft.getInstance().player.displayClientMessage(
                        Component.literal("§c[OpenDreamCore] §f剪贴板不是 JSON 对象（用导出按钮生成）"), false);
                return;
            }
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            root.forEach((k, v) -> m.put(String.valueOf(k), v));
            int applied = 0;
            // options（背景/锚点/淡出等 world 段整体替换；可撤消）
            if (m.get("options") instanceof Map<?, ?> opts) {
                java.util.Map<String, Object> options = new java.util.LinkedHashMap<>();
                opts.forEach((k, v) -> options.put(String.valueOf(k), v));
                pushWorldBackgroundUndo("导入: 选项", "bg:import");
                worldPage.options().clear();
                worldPage.options().putAll(options);
                applied++;
            }
            // 标题（待写，随保存写回 YAML）
            if (m.get("title") != null) {
                String t = String.valueOf(m.get("title")).trim();
                if (!t.isEmpty() && t.length() <= 64) {
                    WorldEditor.get().worldEditPageTitle = t;
                    applied++;
                }
            }
            // 变量（整体替换：导入键 = 写入；导入前存在而导入后没有的 = 删除）
            if (m.get("variables") instanceof Map<?, ?> vars) {
                java.util.Map<String, Object> oldKeys = new java.util.LinkedHashMap<>();
                worldPage.variables().forEach((k, v) -> oldKeys.put(String.valueOf(k), k));
                java.util.Map<String, Object> vm = new java.util.LinkedHashMap<>();
                vars.forEach((k, v) -> vm.put(String.valueOf(k), v));
                worldPage.variables().clear();
                worldPage.variables().putAll(vm);
                WorldEditor.get().worldEditVars.clear();
                for (java.util.Map.Entry<String, Object> e : vm.entrySet()) {
                    WorldEditor.get().worldEditVars.put(e.getKey(), String.valueOf(e.getValue()));
                }
                for (String k : oldKeys.keySet()) {
                    if (!vm.containsKey(k)) {
                        WorldEditor.get().worldEditVars.put(k, "__unset__");
                    }
                }
                applied++;
            }
            // elements（整体替换：现有全部元素保存时删除 + 导入元素以 __create__ 重建；运行时立即生效；
            // 追加模式：id 冲突加后缀追加到现有元素末尾）
            if (m.get("elements") instanceof List<?> els && !els.isEmpty()) {
                if (append) {
                    java.util.List<Element> appended = new java.util.ArrayList<>();
                    for (Object o : els) {
                        if (o instanceof Map<?, ?> em) {
                            Element e = elementFromJsonMap(em);
                            if (e != null) {
                                appended.add(e);
                            }
                        }
                    }
                    if (!appended.isEmpty()) {
                        java.util.List<String> newIds = new java.util.ArrayList<>();
                        java.util.List<Element> live = new java.util.ArrayList<>(worldPage.elements());
                        for (Element e : appended) {
                            String newId = e.id();
                            int suffix = 1;
                            while (findElement(worldPage, newId) != null) {
                                newId = e.id() + "_" + (suffix++);
                            }
                            live.add(new Element(newId, e.type(), e.layout(), e.props(),
                                    e.visibleWhen(), e.enabledWhen(), e.actions(),
                                    e.children(), e.parent()));
                            newIds.add(newId);
                        }
                        WorldEditor.get().pushWorldUndo("追加导入", null, newIds); // 创建前快照（撤消即移除）
                        Page np = new Page(worldPage.id(), worldPage.title(), worldPage.match(),
                                worldPage.displayMode(), worldPage.variables(), live,
                                worldPage.functions(), worldPage.options());
                        String pidA = worldPage.id() == null ? "world" : worldPage.id();
                        WorldPanel panelA = findWorldPanel(pidA);
                        if (panelA != null) {
                            panelA.page = np;
                            panelA.nodes = layoutPage(np, 800, 600);
                        }
                        worldPage = np;
                        worldNodes = panelA == null ? null : panelA.nodes;
                        for (String nid : newIds) {
                            var created = findElement(worldPage, nid);
                            if (created != null) {
                                WorldEditor.get().worldEditProps.computeIfAbsent(nid, k -> new ConcurrentHashMap<>())
                                        .put("__create__", elementYamlBlockFromProps(nid, created));
                                refreshCreateBlock(nid);
                            }
                        }
                        invalidateLayout(np);
                        WorldEditor.get().worldEditSelected = newIds.get(0);
                        applied++;
                    }
                } else {
                    int oldCount = countElements(worldPage.elements());
                if ((oldCount > 20 || els.size() > 20)
                        && System.currentTimeMillis() - WorldEditor.get().worldImportConfirmAt > 3000) {
                    WorldEditor.get().worldImportConfirmAt = System.currentTimeMillis();
                    Minecraft.getInstance().player.displayClientMessage(
                            Component.literal("§c[OpenDreamCore] §f元素替换影响 " + oldCount + "→" + els.size()
                                    + " 个元素（>20），3 秒内再次点导入确认执行"), false);
                    return;
                }
                WorldEditor.get().worldImportConfirmAt = 0;
                java.util.List<Element> imported = new java.util.ArrayList<>();
                for (Object o : els) {
                    if (o instanceof Map<?, ?> em) {
                        Element e = elementFromJsonMap(em);
                        if (e != null) {
                            imported.add(e);
                        }
                    }
                }
                if (!imported.isEmpty()) {
                    java.util.List<String> oldIds = new java.util.ArrayList<>();
                    collectElementIds(worldPage.elements(), oldIds);
                    for (String oldId : oldIds) {
                        WorldEditor.get().worldEditDeletes.add(oldId);
                    }
                    Page np = new Page(worldPage.id(), worldPage.title(), worldPage.match(),
                            worldPage.displayMode(), worldPage.variables(), imported,
                            worldPage.functions(), worldPage.options());
                    String pid2 = worldPage.id() == null ? "world" : worldPage.id();
                    WorldPanel panel2 = findWorldPanel(pid2);
                    if (panel2 != null) {
                        panel2.page = np;
                        panel2.nodes = layoutPage(np, 800, 600);
                    }
                    worldPage = np;
                    worldNodes = panel2 == null ? null : panel2.nodes;
                    for (Element e : imported) {
                        WorldEditor.get().worldEditProps.computeIfAbsent(e.id(), k -> new ConcurrentHashMap<>())
                                .put("__create__", elementYamlBlockFromProps(e.id(), e));
                        refreshCreateBlock(e.id());
                    }
                    invalidateLayout(np);
                    WorldEditor.get().worldEditSelected = imported.get(0).id();
                    applied++;
                }
                }
            }
            if (applied == 0) {
                Minecraft.getInstance().player.displayClientMessage(
                        Component.literal("§e[OpenDreamCore] §f剪贴板中没有可导入的页面级数据（options/title/variables/elements）"), false);
                return;
            }
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§a[OpenDreamCore] §f已导入 " + applied
                            + " 段页面状态（保存后写回页面文件）"), false);
        } catch (Exception e) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§c[OpenDreamCore] §f导入失败: " + e.getMessage()), false);
        }
    }

    /** 元素树递归收集 id。 */
    private static void collectElementIds(java.util.List<Element> els, java.util.List<String> out) {
        for (Element el : els) {
            out.add(el.id());
            collectElementIds(el.children(), out);
        }
    }

    /** 元素树递归计数。 */
    private static int countElements(java.util.List<Element> els) {
        int n = 0;
        for (Element el : els) {
            n += 1 + countElements(el.children());
        }
        return n;
    }

    /** JSON map → Element（layout/visibleWhen/enabledWhen/parent = null；children 递归；非法返回 null）。 */
    static Element elementFromJsonMap(Map<?, ?> m) {
        String id = m.get("id") == null ? null : String.valueOf(m.get("id"));
        if (id == null || id.isBlank() || id.length() > 64) {
            return null;
        }
        String type = m.get("type") == null ? "rect" : String.valueOf(m.get("type"));
        Map<String, Object> props = new java.util.LinkedHashMap<>();
        if (m.get("props") instanceof Map<?, ?> pm) {
            pm.forEach((k, v) -> props.put(String.valueOf(k), v));
        }
        Map<String, String> actions = new java.util.LinkedHashMap<>();
        if (m.get("actions") instanceof Map<?, ?> am) {
            am.forEach((k, v) -> actions.put(String.valueOf(k), String.valueOf(v)));
        }
        java.util.List<Element> kids = new java.util.ArrayList<>();
        if (m.get("children") instanceof List<?> cl) {
            for (Object o : cl) {
                if (o instanceof Map<?, ?> cm) {
                    Element c = elementFromJsonMap(cm);
                    if (c != null) {
                        kids.add(c);
                    }
                }
            }
        }
        return new Element(id, type, null, props, null, null, actions, kids, null);
    }

    /** 复制选中元素格式（props+actions，不含 id/type 定位键）到格式刷剪贴板（Ctrl+Shift+C）。 */

    /** 属性树扁平化（点路径 → 字符串；列表 → | 连接；粘贴格式用）。 */
    static void flattenPropsToPaths(String prefix, Map<?, ?> m, Map<String, String> out) {
        for (java.util.Map.Entry<?, ?> e : m.entrySet()) {
            String key = String.valueOf(e.getKey());
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            Object v = e.getValue();
            if (v instanceof Map<?, ?> mm) {
                flattenPropsToPaths(path, mm, out);
            } else if (v instanceof List<?> l) {
                java.util.List<String> items = new java.util.ArrayList<>();
                for (Object o : l) {
                    items.add(String.valueOf(o));
                }
                out.put(path, String.join("|", items));
            } else {
                out.put(path, optionScalar(v));
            }
        }
    }

    /** 粘贴格式到目标（多选/组/单选；仅同类型元素；props 同名键覆盖 + actions 合并；一步撤消）。 */

    /** 复制选中元素完整 YAML 块（含 actions/children；Ctrl+Shift+E；可粘贴到页面文件或 Ctrl+Shift+G 生成新元素）。 */

    /** 从剪贴板 YAML 粘贴为新元素（Ctrl+Shift+G；id 冲突自动加后缀；一步撤消；保存写回）。 */

    /** 未保存编辑摘要（关闭对齐屏时提示；无待写 = null）。 */
    public String worldPendingSummary() {
        if (worldPage == null || !WorldEditor.get().worldEditMode) {
            return null;
        }
        java.util.List<String> parts = new java.util.ArrayList<>();
        int els = WorldEditor.get().worldEditDirty.size() + WorldEditor.get().worldEditProps.size() + WorldEditor.get().worldEditDeletes.size();
        if (els > 0) {
            parts.add(els + " 个元素编辑");
        }
        int opts = diffWorldOptions().size();
        if (opts > 0) {
            parts.add(opts + " 键选项");
        }
        if (WorldEditor.get().worldEditPageTitle != null) {
            parts.add("标题待写");
        }
        if (!WorldEditor.get().worldEditVars.isEmpty()) {
            parts.add(WorldEditor.get().worldEditVars.size() + " 个变量");
        }
        if (parts.isEmpty()) {
            return null;
        }
        return "有未保存: " + String.join(" + ", parts) + "（工具栏保存后写回页面文件）";
    }

    /** 对齐屏世界点击选目标：scaled 屏幕坐标 → 聚焦面板射线拾取；命中 = 重选并返回 true（空白返回 false 由调用方关屏）。 */
    public boolean pickWorldElementAt(double guiX, double guiY) {
        if (worldPage == null || worldNodes == null || Minecraft.getInstance().player == null) {
            return false;
        }
        try {
            var mc = Minecraft.getInstance();
            var camera = mc.gameRenderer.getMainCamera();
            String pid = worldPage.id() == null ? "world" : worldPage.id();
            WorldPanel panel = findWorldPanel(pid);
            if (panel == null) {
                return false;
            }
            RenderNode hit = WorldHologram.raycast(panel.nodes, panel.page.options(), camera, mc,
                    worldTabActive(pid), pid, panel.page.variables(), panel.anchor);
            if (hit == null) {
                return false;
            }
            worldEditMulti.clear();
            WorldEditor.get().worldEditSelected = hit.id();
            WorldEditor.get().worldPanelSelections.put(pid, hit.id());
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§b[OpenDreamCore] §f已重选对齐目标: " + hit.id()), false);
            return true;
        } catch (Exception e) {
            return false; // 拾取失败按空白处理
        }
    }

    /** 背景预设清单（[名称, 主色摘要]；文件缺失/空 = 空列表；预设屏显示用）。 */
    public java.util.List<String[]> worldBgPresetList() {
        java.util.List<String[]> out = new java.util.ArrayList<>();
        try {
            java.nio.file.Path f = bgPresetFile();
            if (!java.nio.file.Files.exists(f)) {
                return out;
            }
            String body = java.nio.file.Files.readString(f).trim();
            if (!body.startsWith("[") || !body.endsWith("]")) {
                return out;
            }
            String inner = body.substring(1, body.length() - 1);
            int idx = 0;
            while (idx < inner.length()) {
                int open = inner.indexOf('{', idx);
                if (open < 0) {
                    break;
                }
                int close = inner.indexOf('}', open);
                if (close < 0) {
                    break;
                }
                java.util.Map<String, Object> bg = parseBgJsonObject(inner.substring(open, close + 1));
                if (!bg.isEmpty()) {
                    String name = bg.get("name") == null ? "未命名" : String.valueOf(bg.get("name"));
                    String color = bg.get("color") == null ? "#??????" : String.valueOf(bg.get("color"));
                    out.add(new String[]{name, color});
                }
                idx = close + 1;
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    /** 载入指定序号的背景预设（文件数组下标；不移动循环游标；可撤消）。 */

    /** 删除指定序号的背景预设（文件数组下标；预设屏 Shift+点击）。 */

    /** 选中元素是否锁定（hologram.locked；对齐屏锁定按钮状态显示用）。 */
    public boolean worldElementLocked(String elementId) {
        if (worldPage == null || elementId == null) {
            return false;
        }
        var el = findElement(worldPage, elementId);
        if (el == null) {
            return false;
        }
        Object raw = el.props().get("hologram");
        return raw instanceof Map<?, ?> h && Boolean.parseBoolean(String.valueOf(h.get("locked")));
    }

    /** 锁定/解锁选中元素（hologram.locked：锁定时世界侧不可拖拽/旋转/缩放；可撤消）。 */
    public void toggleWorldElementLock(String elementId) {
        if (!WorldEditor.get().worldEditMode || worldPage == null || elementId == null) {
            return;
        }
        var element = findElement(worldPage, elementId);
        if (element == null) {
            return;
        }
        Object raw = element.props().get("hologram");
        if (!(raw instanceof Map<?, ?> holo)) {
            return;
        }
        boolean locked = Boolean.parseBoolean(String.valueOf(holo.get("locked")));
        WorldEditor.get().pushWorldUndo("锁定", "lock", List.of(elementId));
        Map<Object, Object> copy = new java.util.LinkedHashMap<>(holo);
        if (locked) {
            copy.remove("locked");
        } else {
            copy.put("locked", true);
        }
        element.props().put("hologram", copy);
        WorldEditor.get().worldEditProps.computeIfAbsent(elementId, k -> new ConcurrentHashMap<>())
                .put("hologram.locked", locked ? "false" : "true");
        refreshCreateBlock(elementId);
        invalidateLayout(worldPage);
        worldNodes = layoutPage(worldPage, 800, 600);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§e[OpenDreamCore] §f" + elementId + (locked ? " 已解锁" : " 已锁定")
                        + "（锁定时不可拖拽/旋转/缩放；可 Ctrl+Z 撤）"), false);
    }

    /** 批量锁定/解锁（多选/组：统一设为与首个元素相反的状态；一步撤消）。 */
    public void toggleWorldElementLockBatch(List<String> elementIds) {
        if (!WorldEditor.get().worldEditMode || worldPage == null || elementIds == null || elementIds.isEmpty()) {
            return;
        }
        java.util.List<Element> alive = new java.util.ArrayList<>();
        for (String id : elementIds) {
            var el = findElement(worldPage, id);
            if (el != null && el.props().get("hologram") instanceof Map<?, ?>) {
                alive.add(el);
            }
        }
        if (alive.isEmpty()) {
            return;
        }
        boolean target = !worldElementLocked(alive.get(0).id());
        WorldEditor.get().pushWorldUndo("批量锁定", "batchlock", java.util.List.copyOf(
                alive.stream().map(Element::id).toList()));
        for (Element el : alive) {
            Object raw = el.props().get("hologram");
            Map<Object, Object> copy = new java.util.LinkedHashMap<>((Map<?, ?>) raw);
            if (target) {
                copy.put("locked", true);
            } else {
                copy.remove("locked");
            }
            el.props().put("hologram", copy);
            WorldEditor.get().worldEditProps.computeIfAbsent(el.id(), k -> new ConcurrentHashMap<>())
                    .put("hologram.locked", target ? "true" : "false");
            refreshCreateBlock(el.id());
        }
        invalidateLayout(worldPage);
        worldNodes = layoutPage(worldPage, 800, 600);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§e[OpenDreamCore] §f批量" + (target ? "锁定" : "解锁")
                        + ": " + alive.size() + " 个元素（可 Ctrl+Z 撤）"), false);
    }

    /** 面板整体是否锁定（options.world.locked：锁定 = 禁止对齐屏编辑与保存）。 */
    public boolean worldPanelLocked() {
        if (worldPage == null) {
            return false;
        }
        Object worldObj = worldPage.options().get("world");
        return worldObj instanceof Map<?, ?> w && Boolean.parseBoolean(String.valueOf(w.get("locked")));
    }

    /** 面板整体锁定切换（world.locked：锁定 = 禁止对齐屏编辑与保存；可撤消）。 */
    public void toggleWorldPanelLock() {
        if (worldPage == null || !WorldEditor.get().worldEditMode) {
            return;
        }
        pushWorldBackgroundUndo("面板: 锁定", "bg:panellock");
        Map<String, Object> options = worldPage.options();
        Map<String, Object> world = new java.util.LinkedHashMap<String, Object>();
        if (options.get("world") instanceof Map<?, ?> w) {
            w.forEach((k, v) -> world.put(String.valueOf(k), v));
        }
        boolean on = Boolean.parseBoolean(String.valueOf(world.get("locked")));
        if (on) {
            world.remove("locked");
        } else {
            world.put("locked", true);
        }
        options.put("world", world);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§e[OpenDreamCore] §f面板已" + (on ? "解锁" : "锁定")
                        + "（锁定 = 禁止对齐屏编辑与保存；可 Ctrl+Z 撤）"), false);
    }

    /** 跨面板锚点对齐（Ctrl+Shift+A）：当前面板锚点移动到参考面板锚点世界位置（offsetX/Y/Z 换算；可撤消）。 */

    /** 解锁全部页面元素（一键清空锁定；可撤消）。 */
    public void unlockWorldAll() {
        if (!WorldEditor.get().worldEditMode || worldPage == null) {
            return;
        }
        java.util.List<String> lockedIds = new java.util.ArrayList<>();
        collectLockedIds(worldPage.elements(), lockedIds);
        if (lockedIds.isEmpty()) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§e[OpenDreamCore] §f页面无锁定元素"), false);
            return;
        }
        WorldEditor.get().pushWorldUndo("解锁全部", "unlockall", lockedIds);
        for (String id : lockedIds) {
            var el = findElement(worldPage, id);
            if (el == null) {
                continue;
            }
            Object raw = el.props().get("hologram");
            if (!(raw instanceof Map<?, ?> holo)) {
                continue;
            }
            Map<Object, Object> copy = new java.util.LinkedHashMap<>(holo);
            copy.remove("locked");
            el.props().put("hologram", copy);
            WorldEditor.get().worldEditProps.computeIfAbsent(id, k -> new ConcurrentHashMap<>())
                    .put("hologram.locked", "false");
            refreshCreateBlock(id);
        }
        invalidateLayout(worldPage);
        worldNodes = layoutPage(worldPage, 800, 600);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§a[OpenDreamCore] §f已解锁全部 " + lockedIds.size()
                        + " 个元素（可 Ctrl+Z 撤）"), false);
    }

    /** 元素树递归收集锁定 id。 */
    private static void collectLockedIds(java.util.List<Element> els, java.util.List<String> out) {
        for (Element el : els) {
            Object raw = el.props().get("hologram");
            if (raw instanceof Map<?, ?> h && Boolean.parseBoolean(String.valueOf(h.get("locked")))) {
                out.add(el.id());
            }
            collectLockedIds(el.children(), out);
        }
    }

    /** 元素模板文件（OpenDreamCore/UI/_templates.json：模板名 → YAML 块数组）。 */
    static java.nio.file.Path worldTemplatesFile() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("OpenDreamCore").resolve("UI").resolve("_templates.json");
    }

    /** 保存当前选中集（组/多选/单选）为命名模板（Ctrl+Shift+T；文件 _templates.json）。 */

    /** 模板清单（[名称, 元素数]）。 */
    public java.util.List<String[]> worldTemplateList() {
        java.util.List<String[]> out = new java.util.ArrayList<>();
        try {
            java.nio.file.Path f = worldTemplatesFile();
            if (!java.nio.file.Files.exists(f)) {
                return out;
            }
            Object parsed = new org.yaml.snakeyaml.Yaml().load(java.nio.file.Files.readString(f));
            if (parsed instanceof Map<?, ?> pm) {
                for (java.util.Map.Entry<?, ?> e : pm.entrySet()) {
                    Object v = e.getValue();
                    int count = v instanceof List<?> l ? l.size() : 0;
                    out.add(new String[]{String.valueOf(e.getKey()), String.valueOf(count)});
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    /** 粘贴命名模板（元素整体创建；id 冲突自动加后缀；一步撤消；保存写回）。 */

    /**
     * 模板块递归展开（模板嵌套）：块内特殊键
     * {@code __template: <名称>} 引用其它模板（可配 {@code __dx/__dy} 相对偏移，全部子元素整体平移），
     * 深度上限 4，链上环检测；失败（缺失/环/超深）记入 failed 并跳过该块。
     */
    void expandTemplateBlocks(Map<?, ?> allTemplates, String name, int depth,
                                      java.util.List<String> chain, double dx, double dy,
                                      java.util.List<Element> created, java.util.List<String> failed) {
        if (depth > 4 || chain.contains(name)) {
            if (!failed.contains(name)) {
                failed.add(name);
            }
            return;
        }
        Object blocksObj = allTemplates.get(name);
        if (!(blocksObj instanceof List<?> blocks) || blocks.isEmpty()) {
            if (!failed.contains(name)) {
                failed.add(name);
            }
            return;
        }
        chain.add(name);
        for (Object b : blocks) {
            Object bParsed = new org.yaml.snakeyaml.Yaml().load(String.valueOf(b));
            if (!(bParsed instanceof Map<?, ?> bm)) {
                continue;
            }
            Object nestedRef = bm.get("__template");
            if (nestedRef != null) {
                double ndx = dx + UiRenderer.num(bm.get("__dx"), 0);
                double ndy = dy + UiRenderer.num(bm.get("__dy"), 0);
                expandTemplateBlocks(allTemplates, String.valueOf(nestedRef), depth + 1,
                        chain, ndx, ndy, created, failed);
                continue;
            }
            Element el = elementFromJsonMap(bm);
            if (el == null) {
                continue;
            }
            // 嵌套偏移：非零时整体平移 holo x/y（保留其余 props 原样）
            if (dx != 0 || dy != 0) {
                Map<String, Object> newProps = new java.util.LinkedHashMap<>();
                el.props().forEach((k, v) -> {
                    if ("hologram".equals(k) && v instanceof Map<?, ?> h) {
                        Map<String, Object> nh = new java.util.LinkedHashMap<>();
                        h.forEach((hk, hv) -> {
                            String hks = String.valueOf(hk);
                            if ("x".equals(hks) && hv instanceof Number n) {
                                nh.put(hks, Math.round((n.doubleValue() + dx) * 100) / 100.0);
                            } else if ("y".equals(hks) && hv instanceof Number n) {
                                nh.put(hks, Math.round((n.doubleValue() + dy) * 100) / 100.0);
                            } else {
                                nh.put(hks, hv);
                            }
                        });
                        newProps.put(k, nh);
                    } else {
                        newProps.put(k, v);
                    }
                });
                el = new Element(el.id(), el.type(), el.layout(), newProps,
                        el.visibleWhen(), el.enabledWhen(), el.actions(), el.children(), el.parent());
            }
            String newId = el.id();
            int suffix = 1;
            while (findElement(worldPage, newId) != null) {
                newId = el.id() + "_" + (suffix++);
            }
            // 子元素 id 也需去重（简单处理：仅顶层 id 去重；子元素沿用模板内 id）
            Element finalEl = new Element(newId, el.type(), el.layout(), el.props(),
                    el.visibleWhen(), el.enabledWhen(), el.actions(), el.children(), el.parent());
            created.add(finalEl);
        }
        chain.remove(chain.size() - 1);
    }

    /** 删除命名模板（模板屏 Shift+点击）。 */

    /** 对齐屏"重置页面级"：背景/锚点/淡出等选项、标题、变量全部还原到进入编辑时（元素编辑保留；撤消历史清空）。 */
    public void resetWorldPageState() {        if (!WorldEditor.get().worldEditMode || worldPage == null) {
            return;
        }
        restoreWorldOptions(WorldEditor.get().worldOptionsBaseline);
        WorldEditor.get().worldEditPageTitle = null;
        if (WorldEditor.get().worldVariablesBaseline != null) {
            worldPage.variables().clear();
            worldPage.variables().putAll((Map<String, Object>) deepCopy(WorldEditor.get().worldVariablesBaseline));
        }
        WorldEditor.get().worldEditVars.clear();
        clearWorldUndo(); // 页面级已整体还原 → 旧撤消步失效
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§e[OpenDreamCore] §f页面级修改已重置（背景/锚点/淡出/标题/变量回到进入编辑时；元素编辑保留）"), false);
    }

    /** 工具栏放弃：还原进入编辑模式时的位置/属性/页面选项/变量快照，取消增删/标题，清空未保存列表。 */

    /** 页面元素列表是否包含指定 id（递归）。 */
    static boolean containsElement(List<Element> elements, String id) {
        for (Element element : elements) {
            if (id.equals(element.id()) || containsElement(element.children(), id)) {
                return true;
            }
        }
        return false;
    }

    // ---------- 编辑撤消/重做（undo/redo） ----------

    /** 撤消条目：元素在某个时间点的完整编辑状态快照。 */

    /** 撤消操作：label 显示名；key 为合并键（同 id+key 的连续小步合并为一个撤消步）。 */

    /** 捕获元素当前编辑状态（不存在 → exists=false）。 */

    /** 在顶层元素 children 中找目标元素的下标（递归；找不到 -1）。 */
    static int childIndexOf(Element parent, Element target) {
        for (int i = 0; i < parent.children().size(); i++) {
            Element child = parent.children().get(i);
            if (child == target) {
                return i;
            }
            int ci = childIndexOf(child, target);
            if (ci >= 0) {
                return ci;
            }
        }
        return -1;
    }

    /** 记录撤消步（操作前调用）：捕获受影响元素当前状态；key 相同且元素集合相同 → 合并。 */

    /** 页面 options 深拷贝快照（背景/淡出等 world 段配置；撤消/重做恢复用）。 */
    Map<String, Object> snapshotWorldOptions() {
        Map<String, Object> copy = new java.util.LinkedHashMap<>();
        worldPage.options().forEach((k, v) -> copy.put(k, deepCopy(v)));
        return copy;
    }

    /** 页面 variables 深拷贝快照（放弃编辑还原用）。 */
    Map<String, Object> snapshotWorldVariables() {
        Map<String, Object> copy = new java.util.LinkedHashMap<>();
        worldPage.variables().forEach((k, v) -> copy.put(k, deepCopy(v)));
        return copy;
    }

    /** 恢复页面 options 快照（背景等 world 段修改的撤消/重做）。 */
    @SuppressWarnings("unchecked")
    void restoreWorldOptions(Map<String, Object> snapshot) {
        if (snapshot == null || worldPage == null) {
            return;
        }
        worldPage.options().clear();
        worldPage.options().putAll((Map<String, Object>) deepCopy(snapshot));
    }

    /** 页面 options 扁平化：map → 点路径 → 标量字符串（列表跳过，不参与持久化差异）。 */
    private static void flattenWorldOptions(String prefix, Map<?, ?> m, Map<String, String> out) {
        for (java.util.Map.Entry<?, ?> e : m.entrySet()) {
            String key = String.valueOf(e.getKey());
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            Object v = e.getValue();
            if (v instanceof Map<?, ?> mm) {
                flattenWorldOptions(path, mm, out);
            } else if (v instanceof List<?>) {
                out.put(path, "__unset__"); // 页面级无列表配置；防御
            } else {
                out.put(path, optionScalar(v));
            }
        }
    }

    /** 标量 → 写入值：数字去尾零（3.0 → 3）、布尔原样、其余字符串。 */
    private static String optionScalar(Object v) {
        if (v instanceof Boolean b) {
            return b ? "true" : "false";
        }
        if (v instanceof Number n) {
            double d = n.doubleValue();
            if (!Double.isInfinite(d) && !Double.isNaN(d) && d == Math.floor(d) && Math.abs(d) < 1e15) {
                return String.valueOf((long) d);
            }
            return String.valueOf(Math.round(d * 1000) / 1000.0);
        }
        return String.valueOf(v);
    }

    /** 当前页面 options 相对基线快照的差异（点路径 → 值；基线有而当前无 = __unset__）。 */
    Map<String, String> diffWorldOptions() {
        Map<String, String> out = new java.util.LinkedHashMap<>();
        if (worldPage == null || WorldEditor.get().worldOptionsBaseline == null) {
            return out;
        }
        Map<String, String> base = new java.util.LinkedHashMap<>();
        flattenWorldOptions("", WorldEditor.get().worldOptionsBaseline, base);
        Map<String, String> cur = new java.util.LinkedHashMap<>();
        flattenWorldOptions("", worldPage.options(), cur);
        for (java.util.Map.Entry<String, String> e : cur.entrySet()) {
            if (!e.getValue().equals(base.get(e.getKey()))) {
                out.put(e.getKey(), e.getValue());
            }
        }
        for (String k : base.keySet()) {
            if (!cur.containsKey(k)) {
                out.put(k, "__unset__");
            }
        }
        return out;
    }

    /** 页面级操作计数（对齐屏会话报告差值用）。 */
    public int worldBgOpCount() {
        return WorldEditor.get().worldBgOpCount;
    }

    /** 记录页面级（options.world 背景/淡出/锚点等）撤消步（修改前调用）；同 key 连续小步合并为一个撤消步。 */
    void pushWorldBackgroundUndo(String label, String key) {
        if (worldPage == null || !WorldEditor.get().worldEditMode) {
            return;
        }
        WorldEditor.get().worldBgOpCount++; // 会话统计（对齐屏报告用）
        if (key != null && !WorldEditor.get().worldUndoStack.isEmpty()) {
            WorldEditOp last = WorldEditor.get().worldUndoStack.peek();
            if (last.worldOptions != null && key.equals(last.key)) {
                return; // 连续同类背景小步（循环档/色条拖动）合并
            }
        }
        WorldEditor.get().worldUndoStack.push(new WorldEditOp(label, key, new java.util.ArrayList<>(), snapshotWorldOptions()));
        WorldEditor.get().worldRedoStack.clear();
        while (WorldEditor.get().worldUndoStack.size() > WorldEditor.get().WORLD_UNDO_LIMIT) {
            WorldEditor.get().worldUndoStack.removeLast();
        }
    }

    /** 撤消：恢复最近一次操作的"操作前"状态（Ctrl+Z）。 */

    /** 重做：恢复被撤消操作（Ctrl+Y / Ctrl+Shift+Z）。 */

    /** 把元素恢复到快照状态（存在性/位置/未保存属性/删除集/脏集）。 */

    /** 清空撤消/重做历史（页面重开/放弃编辑/退出编辑时）。 */
    void clearWorldUndo() {
        WorldEditor.get().worldUndoStack.clear();
        WorldEditor.get().worldRedoStack.clear();
        WorldEditor.get().worldEditUndoElements.clear();
    }

    /** 工具栏点击（步长/吸附/保存/放弃/退出/属性编辑），返回是否消费了该次按下。 */

    /** 打开属性输入屏（EditBox，Enter 提交 / ESC 取消）。 */
    void openPropEditor(String path, String title) {
        if (WorldEditor.get().worldEditSelected == null) {
            return;
        }
        var element = findElement(worldPage, WorldEditor.get().worldEditSelected);
        if (element == null) {
            return;
        }
        String current = elementPropValue(element, path);
        Minecraft mc = Minecraft.getInstance();
        String elementId = WorldEditor.get().worldEditSelected;
        mc.setScreen(new WorldEditPropQuickScreen(title, elementId, path, current));
    }

    /** 打开任意属性编辑面板（列出元素全部可编辑属性 → 点击编辑）。 */
    void openPropsScreen() {
        if (WorldEditor.get().worldEditSelected == null) {
            return;
        }
        var element = findElement(worldPage, WorldEditor.get().worldEditSelected);
        if (element == null) {
            return;
        }
        Minecraft.getInstance().setScreen(new WorldEditPropsScreen(WorldEditor.get().worldEditSelected, element));
    }

    /** 打开对齐工具面板（相对可见面板包围盒一键对齐）。 */
    void openAlignScreen() {
        if (WorldEditor.get().worldEditSelected == null) {
            return;
        }
        if (worldPanelLocked()) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§c[OpenDreamCore] §f面板已锁定（Ctrl+点击锁按钮解锁面板）"), false);
            return;
        }
        Minecraft.getInstance().setScreen(new WorldEditAlignScreen(WorldEditor.get().worldEditSelected));
    }

    /** 打开动作绑定面板（click/hover/input 脚本编辑，保存写回页面文件）；多选/编组时进入批量绑定。 */
    private void openActionScreen() {
        if (WorldEditor.get().worldEditSelected == null) {
            return;
        }
        if (worldPanelLocked()) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§c[OpenDreamCore] §f面板已锁定（Ctrl+点击锁按钮解锁面板）"), false);
            return;
        }
        var element = findElement(worldPage, WorldEditor.get().worldEditSelected);
        if (element == null) {
            return;
        }
        java.util.List<String> targets = new java.util.ArrayList<>();
        String grp = worldGroupOf(WorldEditor.get().worldEditSelected);
        if (grp != null && worldGroupMembers(grp).size() > 1) {
            targets.addAll(worldGroupMembers(grp));
        } else if (worldEditMulti.size() >= 2) {
            targets.addAll(worldEditMulti);
        }
        if (targets.size() >= 2) {
            Minecraft.getInstance().setScreen(new WorldEditBatchActionScreen(targets));
        } else {
            Minecraft.getInstance().setScreen(new WorldEditActionScreen(WorldEditor.get().worldEditSelected, element));
        }
    }

    /** 绑定/清除元素动作脚本：写 actions 映射 + 记入未保存（保存时烘焙 actions.<key> 到页面 YAML）。 */
    public void setWorldAction(String elementId, String key, String value) {
        if (worldPage == null || !WorldEditor.get().worldEditMode) {
            return;
        }
        var element = findElement(worldPage, elementId);
        if (element == null) {
            return;
        }
        WorldEditor.get().pushWorldUndo("动作 " + key, "action:" + key, List.of(elementId)); // 连续同槽编辑合并
        if (value == null || value.isBlank()) {
            element.actions().remove(key);
        } else {
            element.actions().put(key, value);
        }
        WorldEditor.get().worldEditProps.computeIfAbsent(elementId, k -> new ConcurrentHashMap<>())
                .put("actions." + key, value == null ? "" : value);
        refreshCreateBlock(elementId);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§e[OpenDreamCore] §f" + key + " 动作已"
                        + (value == null || value.isBlank() ? "清除" : "绑定") + "（保存后写回页面文件）"), false);
    }

    /** 设置选中元素文本色（吸色 Ctrl+点击应用；text 元素；一步撤消；保存写回）。 */
    public void setWorldElementColor(String elementId, String hex) {
        if (!WorldEditor.get().worldEditMode || worldPage == null || elementId == null || hex == null) {
            return;
        }
        var element = findElement(worldPage, elementId);
        if (element == null) {
            return;
        }
        if (!"text".equals(element.type())) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§e[OpenDreamCore] §f仅 text 元素支持文本色（当前 " + element.type() + "）"), false);
            return;
        }
        WorldEditor.get().pushWorldUndo("属性 text.color", "prop:text.color", List.of(elementId));
        Map<String, Object> text = new java.util.LinkedHashMap<>();
        if (element.props().get("text") instanceof Map<?, ?> tm) {
            tm.forEach((k, v) -> text.put(String.valueOf(k), v));
        }
        text.put("color", hex);
        element.props().put("text", text);
        WorldEditor.get().worldEditProps.computeIfAbsent(elementId, k -> new ConcurrentHashMap<>())
                .put("text.color", hex);
        refreshCreateBlock(elementId);
        invalidateLayout(worldPage);
        worldNodes = layoutPage(worldPage, 800, 600);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§e[OpenDreamCore] §f" + elementId + " 文本色: " + hex + "（保存后写回页面文件）"), false);
    }

    /** 批量设置文本色（Ctrl+吸色；多选/编组逐一套用；仅 text 元素；一步撤消；保存写回）。 */
    public void setWorldElementColorBatch(List<String> elementIds, String hex) {
        if (!WorldEditor.get().worldEditMode || worldPage == null || elementIds == null || elementIds.isEmpty() || hex == null) {
            return;
        }
        List<String> alive = new java.util.ArrayList<>();
        for (String id : elementIds) {
            var el = findElement(worldPage, id);
            if (el != null && "text".equals(el.type())) {
                alive.add(id);
            }
        }
        if (alive.isEmpty()) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§e[OpenDreamCore] §f所选元素中没有 text 元素"), false);
            return;
        }
        WorldEditor.get().pushWorldUndo("批量 text.color", "batchprop:text.color", alive);
        for (String id : alive) {
            var element = findElement(worldPage, id);
            if (element == null) {
                continue;
            }
            Map<String, Object> text = new java.util.LinkedHashMap<>();
            if (element.props().get("text") instanceof Map<?, ?> tm) {
                tm.forEach((k, v) -> text.put(String.valueOf(k), v));
            }
            text.put("color", hex);
            element.props().put("text", text);
            WorldEditor.get().worldEditProps.computeIfAbsent(id, k -> new ConcurrentHashMap<>())
                    .put("text.color", hex);
            refreshCreateBlock(id);
        }
        invalidateLayout(worldPage);
        worldNodes = layoutPage(worldPage, 800, 600);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§e[OpenDreamCore] §f批量文本色: " + hex
                        + "（" + alive.size() + " 个 text 元素，保存后写回页面文件）"), false);
    }

    /** 批量绑定/清除动作脚本（多选/编组目标；一步撤消；保存时逐元素烘焙 actions.<key>）。 */
    public void setWorldActionBatch(List<String> elementIds, String key, String value) {        if (worldPage == null || !WorldEditor.get().worldEditMode || elementIds == null || elementIds.isEmpty()) {
            return;
        }
        List<String> alive = new java.util.ArrayList<>();
        for (String id : elementIds) {
            if (findElement(worldPage, id) != null) {
                alive.add(id);
            }
        }
        if (alive.isEmpty()) {
            return;
        }
        WorldEditor.get().pushWorldUndo("批量动作 " + key, "batchaction:" + key, alive); // 连续同槽批量合并
        for (String id : alive) {
            var element = findElement(worldPage, id);
            if (element == null) {
                continue;
            }
            if (value == null || value.isBlank()) {
                element.actions().remove(key);
            } else {
                element.actions().put(key, value);
            }
            WorldEditor.get().worldEditProps.computeIfAbsent(id, k -> new ConcurrentHashMap<>())
                    .put("actions." + key, value == null ? "" : value);
            refreshCreateBlock(id);
        }
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§e[OpenDreamCore] §f批量 " + key + " 动作已"
                        + (value == null || value.isBlank() ? "清除" : "绑定")
                        + "（" + alive.size() + " 个元素，保存后写回页面文件）"), false);
    }

    /** 编组：多选元素（≥ 2）赋予同一 hologram.group（一起拖/对齐/分布，保存写回）。 */
    public void groupWorldSelection(String elementId) {
        if (worldPage == null) {
            return;
        }
        List<String> members = worldEditMulti.size() >= 2
                ? new java.util.ArrayList<>(worldEditMulti) : null;
        if (members == null) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§c[OpenDreamCore] §f编组需要多选（Ctrl+点击/框选 ≥ 2）"), false);
            return;
        }
        WorldEditor.get().pushWorldUndo("编组", null, members);
        String name = "group_" + (++WorldEditor.get().worldGroupSeq);
        for (String memberId : members) {
            var el = findElement(worldPage, memberId);
            if (el == null) {
                continue;
            }
            Object raw = el.props().get("hologram");
            if (!(raw instanceof Map<?, ?> holo)) {
                continue;
            }
            Map<Object, Object> copy = new java.util.LinkedHashMap<>(holo);
            copy.put("group", name);
            el.props().put("hologram", copy);
            WorldEditor.get().worldEditProps.computeIfAbsent(memberId, k -> new ConcurrentHashMap<>())
                    .put("hologram.group", name);
            refreshCreateBlock(memberId);
        }
        invalidateLayout(worldPage);
        worldNodes = layoutPage(worldPage, 800, 600);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§a[OpenDreamCore] §f已编组 " + name + "（" + members.size() + " 个元素）"), false);
    }

    /** 解组：移除选中元素所在组的 hologram.group（__unset__ 约定，服务端删除键）。 */
    public void ungroupWorldSelection(String elementId) {
        if (worldPage == null || elementId == null) {
            return;
        }
        String group = worldGroupOf(elementId);
        List<String> members = group == null ? null : worldGroupMembers(group);
        if (members == null) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§c[OpenDreamCore] §f该元素不在面板组中"), false);
            return;
        }
        WorldEditor.get().pushWorldUndo("解组", null, members);
        for (String memberId : members) {
            var el = findElement(worldPage, memberId);
            if (el == null) {
                continue;
            }
            Object raw = el.props().get("hologram");
            if (!(raw instanceof Map<?, ?> holo)) {
                continue;
            }
            Map<Object, Object> copy = new java.util.LinkedHashMap<>(holo);
            copy.remove("group");
            el.props().put("hologram", copy);
            WorldEditor.get().worldEditProps.computeIfAbsent(memberId, k -> new ConcurrentHashMap<>())
                    .put("hologram.group", "__unset__");
            refreshCreateBlock(memberId);
        }
        invalidateLayout(worldPage);
        worldNodes = layoutPage(worldPage, 800, 600);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§a[OpenDreamCore] §f已解组 " + group + "（" + members.size() + " 个元素）"), false);
    }

    /** 对齐选中元素：left/right/hcenter/top/bottom/vcenter（相对当前可见元素包围盒；组元素整体对齐）。 */

    /** 面板组整体对齐：组包围盒（成员中心 ± 尺寸）按模式对齐到可见包围盒，全体成员同偏移（可撤消；跳过锁定）。 */

    /** 统一旋转（align:yaw）：组/多选/单选全部成员 yaw 对齐到首元素 yaw（跳过锁定；可撤消）。 */

    /** 跨面板统一旋转：当前选中集 yaw 对齐到参考面板（上次聚焦面板）选中元素 yaw。 */
    public void alignWorldYawCross() {
        if (WorldEditor.get().worldLastPanelPid == null || worldPage == null || WorldEditor.get().worldEditSelected == null) {
            return;
        }
        WorldPanel ref = findWorldPanel(WorldEditor.get().worldLastPanelPid);
        if (ref == null || ref.page == null || ref.page == worldPage) {
            return;
        }
        String refId = WorldEditor.get().worldPanelSelections.get(WorldEditor.get().worldLastPanelPid);
        if (refId == null) {
            return;
        }
        var refEl = findElement(ref.page, refId);
        if (refEl == null) {
            return;
        }
        Object refRaw = refEl.props().get("hologram");
        if (!(refRaw instanceof Map<?, ?> refHolo)) {
            return;
        }
        double targetYaw = WorldHologram.holoNum(refHolo, "yaw", 0, ref.page.variables());
        java.util.List<String> members = new java.util.ArrayList<>();
        String grp = worldGroupOf(WorldEditor.get().worldEditSelected);
        if (grp != null && worldGroupMembers(grp).size() > 1) {
            members.addAll(worldGroupMembers(grp));
        } else if (worldEditMulti.size() >= 2) {
            members.addAll(worldEditMulti);
        } else {
            members.add(WorldEditor.get().worldEditSelected);
        }
        int skipped = members.size();
        members = filterLocked(members);
        skipped -= members.size();
        if (members.isEmpty()) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§e[OpenDreamCore] §f目标元素全部锁定，已跳过"), false);
            return;
        }
        WorldEditor.get().pushWorldUndo("跨面统一旋转", null, members);
        int changed = applyYawToMembers(members, targetYaw);
        if (changed > 0) {
            invalidateLayout(worldPage);
            worldNodes = layoutPage(worldPage, 800, 600);
        }
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§a[OpenDreamCore] §f跨面统一旋转: " + changed + " 个元素 → yaw "
                        + Math.round(targetYaw * 100) / 100.0 + "°（参考 " + WorldEditor.get().worldLastPanelPid
                        + "·" + refId + "；Ctrl+Z 撤消"
                        + (skipped > 0 ? "；跳过 " + skipped + " 锁定" : "") + "）"), false);
    }

    /** 成员 yaw 批量写入（跳过与目标一致的；返回变更数）。 */
    int applyYawToMembers(java.util.List<String> members, double targetYaw) {
        int changed = 0;
        for (String memberId : members) {
            var el = findElement(worldPage, memberId);
            if (el == null) {
                continue;
            }
            Object raw = el.props().get("hologram");
            if (!(raw instanceof Map<?, ?> holo)) {
                continue;
            }
            double cur = WorldHologram.holoNum(holo, "yaw", 0, worldPage.variables());
            if (Math.abs(cur - targetYaw) < 1e-9) {
                continue;
            }
            Map<Object, Object> copy = new java.util.LinkedHashMap<>(holo);
            copy.put("yaw", targetYaw);
            el.props().put("hologram", copy);
            WorldEditor.get().worldEditDirty.put(el.id(), new double[]{
                    WorldHologram.holoNum(copy, "x", 0, worldPage.variables()),
                    WorldHologram.holoNum(copy, "y", 0, worldPage.variables()),
                    WorldHologram.holoNum(copy, "z", 0, worldPage.variables())});
            refreshCreateBlock(el.id());
            changed++;
        }
        return changed;
    }

    /** 范围=全部：全部可见元素 yaw 统一到首个可见元素（跳过锁定；可撤消）。 */
    public void alignWorldYawAll() {
        if (!WorldEditor.get().worldEditMode || worldPage == null || worldNodes == null) {
            return;
        }
        java.util.Set<String> ids = new java.util.HashSet<>();
        WorldEditor.get().collectWorldIds(worldNodes, ids);
        List<String> all = new java.util.ArrayList<>(ids);
        if (all.isEmpty()) {
            return;
        }
        // 基准 = 首个未锁定元素
        String base = null;
        for (String id : all) {
            if (!worldElementLocked(id)) {
                base = id;
                break;
            }
        }
        if (base == null) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§e[OpenDreamCore] §f目标元素全部锁定，已跳过"), false);
            return;
        }
        var baseEl = findElement(worldPage, base);
        if (baseEl == null) {
            return;
        }
        Object fRaw = baseEl.props().get("hologram");
        if (!(fRaw instanceof Map<?, ?> fHolo)) {
            return;
        }
        double targetYaw = WorldHologram.holoNum(fHolo, "yaw", 0, worldPage.variables());
        List<String> members = filterLocked(all);
        int skipped = all.size() - members.size();
        WorldEditor.get().pushWorldUndo("全部统一旋转", null, members);
        int changed = applyYawToMembers(members, targetYaw);
        if (changed > 0) {
            invalidateLayout(worldPage);
            worldNodes = layoutPage(worldPage, 800, 600);
        }
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§a[OpenDreamCore] §f全部统一旋转: " + changed + " 个元素 → yaw "
                        + Math.round(targetYaw * 100) / 100.0 + "°（Ctrl+Z 撤消"
                        + (skipped > 0 ? "；跳过 " + skipped + " 锁定" : "") + "）"), false);
    }

    /** 网格排列：多选/组元素按 4 列网格重排（起点 = 当前包围盒左上；间隙 = 平均尺寸 0.4 倍；锁定跳过；可撤消）。 */
    public void arrangeWorldGrid() {
        arrangeWorldGrid(null);
    }

    /** 网格排列（显式成员列表；null = 从当前选区推导）。 */
    public void arrangeWorldGrid(java.util.List<String> explicitMembers) {
        if (!WorldEditor.get().worldEditMode || worldPage == null || worldNodes == null) {
            return;
        }
        java.util.List<String> members;
        if (explicitMembers != null && !explicitMembers.isEmpty()) {
            members = new java.util.ArrayList<>(explicitMembers);
        } else {
            members = new java.util.ArrayList<>();
            String grp = worldGroupOf(WorldEditor.get().worldEditSelected);
            if (grp != null && worldGroupMembers(grp).size() > 1) {
                members.addAll(worldGroupMembers(grp));
            } else if (worldEditMulti.size() >= 2) {
                members.addAll(worldEditMulti);
            }
        }
        if (members.size() < 2) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§e[OpenDreamCore] §f网格排列需要多选/组（≥2 元素）"), false);
            return;
        }
        int skipped = members.size();
        members = filterLocked(members);
        skipped -= members.size();
        if (members.isEmpty()) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§e[OpenDreamCore] §f目标元素全部锁定，已跳过"), false);
            return;
        }
        var vars = worldPage.variables();
        java.util.List<String> alive = new java.util.ArrayList<>();
        java.util.List<double[]> sizes = new java.util.ArrayList<>();
        for (String id : members) {
            var el = findElement(worldPage, id);
            if (el == null) {
                continue;
            }
            Object raw = el.props().get("hologram");
            if (!(raw instanceof Map<?, ?> holo)) {
                continue;
            }
            String type = String.valueOf(el.props().get("type"));
            double w = WorldHologram.holoNum(holo, "width", "text".equals(type) ? 2.0 : 1.0, vars);
            double h = WorldHologram.holoNum(holo, "height", "text".equals(type) ? 0.25 : 1.0, vars);
            sizes.add(new double[]{WorldHologram.holoNum(holo, "x", 0, vars),
                    WorldHologram.holoNum(holo, "y", 0, vars), w, h});
            alive.add(id);
        }
        if (alive.size() < 2) {
            return;
        }
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, sumW = 0, sumH = 0;
        for (double[] s : sizes) {
            minX = Math.min(minX, s[0] - s[2] / 2);
            minY = Math.min(minY, s[1] - s[3] / 2);
            sumW += s[2];
            sumH += s[3];
        }
        double gapX = Math.max(0.1, sumW / sizes.size() * 0.4);
        double gapY = Math.max(0.1, sumH / sizes.size() * 0.4);
        int cols = 4;
        WorldEditor.get().pushWorldUndo("网格排列", null, alive);
        double cx = minX;
        double cy = minY;
        double rowH = 0;
        for (int i = 0; i < alive.size(); i++) {
            var el = findElement(worldPage, alive.get(i));
            Object raw = el.props().get("hologram");
            Map<?, ?> holo = (Map<?, ?>) raw;
            double[] s = sizes.get(i);
            Map<Object, Object> copy = new java.util.LinkedHashMap<>(holo);
            copy.put("x", Math.round((cx + s[2] / 2) * 100) / 100.0);
            copy.put("y", Math.round((cy + s[3] / 2) * 100) / 100.0);
            el.props().put("hologram", copy);
            WorldEditor.get().worldEditDirty.put(el.id(), new double[]{
                    WorldHologram.holoNum(copy, "x", 0, vars),
                    WorldHologram.holoNum(copy, "y", 0, vars),
                    WorldHologram.holoNum(copy, "z", 0, vars)});
            refreshCreateBlock(el.id());
            cx += s[2] + gapX;
            rowH = Math.max(rowH, s[3]);
            if ((i + 1) % cols == 0) {
                cx = minX;
                cy += rowH + gapY;
                rowH = 0;
            }
        }
        invalidateLayout(worldPage);
        worldNodes = layoutPage(worldPage, 800, 600);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§a[OpenDreamCore] §f网格排列: " + alive.size() + " 个元素 → " + cols
                        + " 列网格（Ctrl+Z 撤消" + (skipped > 0 ? "；跳过 " + skipped + " 锁定" : "") + "）"), false);
    }

    /** 过滤锁定成员（批量对齐/分布/尺寸/镜像跳过锁定元素；锁定的不参与移动）。 */
    java.util.List<String> filterLocked(java.util.List<String> members) {        if (members == null) {
            return null;
        }
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String id : members) {
            if (!worldElementLocked(id)) {
                out.add(id);
            }
        }
        return out;
    }

    /** 对齐全部可见元素（范围=全部；组包围盒按模式对齐到页面可见包围盒；一步撤消）。 */

    /** 镜像：多选/面板组（或单选）位置绕可见包围盒中心水平/垂直翻转 + yaw 取反（写 props + dirty）。 */

    /** 跨面板镜像：当前成员绕上次聚焦面板的可见范围中心轴镜像（世界坐标换算含锚点差；一步撤消）。 */

    /** 统一尺寸：多选/面板组成员宽度或高度统一为最大值（写 props + dirty + 保存写回）。 */
    public void unifyWorldSize(String elementId, String axis) {
        if (worldPage == null || elementId == null) {
            return;
        }
        List<String> members = null;
        String group = worldGroupOf(elementId);
        if (group != null && worldGroupMembers(group).size() > 1) {
            members = worldGroupMembers(group);
        } else if (worldEditMulti.size() >= 2) {
            members = new java.util.ArrayList<>(worldEditMulti);
        }
        if (members == null || members.size() < 2) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§c[OpenDreamCore] §f统一尺寸需要多选（Ctrl+点击 ≥ 2）或面板组（≥ 2）"), false);
            return;
        }
        int skippedSize = members.size();
        members = filterLocked(members);
        skippedSize -= members.size();
        if (members == null || members.isEmpty()) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§e[OpenDreamCore] §f目标元素全部锁定，已跳过"), false);
            return;
        }
        WorldEditor.get().pushWorldUndo("统一尺寸", null, members);
        var vars = worldPage.variables();
        boolean width = "w".equals(axis);
        double target = 0;
        for (String memberId : members) {
            var el = findElement(worldPage, memberId);
            if (el == null) {
                continue;
            }
            Object raw = el.props().get("hologram");
            if (!(raw instanceof Map<?, ?> holo)) {
                continue;
            }
            String type = String.valueOf(el.props().get("type"));
            double size = WorldHologram.holoNum(holo, width ? "width" : "height",
                    "text".equals(type) ? (width ? 2.0 : 0.25) : 1.0, vars);
            target = Math.max(target, size);
        }
        if (target <= 0) {
            return;
        }
        target = Math.round(target * 100) / 100.0;
        for (String memberId : members) {
            var el = findElement(worldPage, memberId);
            if (el == null) {
                continue;
            }
            Object raw = el.props().get("hologram");
            if (!(raw instanceof Map<?, ?> holo)) {
                continue;
            }
            Map<Object, Object> copy = new java.util.LinkedHashMap<>(holo);
            copy.put(width ? "width" : "height", target);
            el.props().put("hologram", copy);
            WorldEditor.get().worldEditProps.computeIfAbsent(memberId, k -> new ConcurrentHashMap<>())
                    .put("hologram." + (width ? "width" : "height"), String.valueOf(target));
            refreshCreateBlock(memberId);
        }
        invalidateLayout(worldPage);
        worldNodes = layoutPage(worldPage, 800, 600);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§a[OpenDreamCore] §f统一"
                        + (width ? "宽度" : "高度") + ": " + members.size()
                        + " 元素 → " + target + "（Ctrl+Z 撤消"
                        + (skippedSize > 0 ? "；跳过 " + skippedSize + " 锁定" : "") + "）"), false);
    }

    /** 跨面板统一尺寸：当前成员宽/高统一为上次聚焦面板选中元素的值（可撤消）。 */
    public void unifyWorldSizeCross(String axis) {
        if (!WorldEditor.get().worldEditMode || worldPage == null || worldNodes == null) {
            return;
        }
        if (!worldCrossAlignAvailable()) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§e[OpenDreamCore] §f跨面板统一尺寸需先 ◀面板/面板▶ 切换到另一面板"), false);
            return;
        }
        List<String> members = null;
        if (WorldEditor.get().worldEditSelected != null) {
            String group = worldGroupOf(WorldEditor.get().worldEditSelected);
            if (group != null && worldGroupMembers(group).size() > 1) {
                members = worldGroupMembers(group);
            } else if (worldEditMulti.size() >= 2) {
                members = new java.util.ArrayList<>(worldEditMulti);
            }
        }
        if (members == null) {
            members = List.of(WorldEditor.get().worldEditSelected);
        }
        int skippedSizeX = members.size();
        members = filterLocked(members);
        skippedSizeX -= members.size();
        if (members == null || members.isEmpty()) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§e[OpenDreamCore] §f目标元素全部锁定，已跳过"), false);
            return;
        }
        WorldPanel other = findWorldPanel(WorldEditor.get().worldLastPanelPid);
        if (other == null) {
            return;
        }
        String otherId = WorldEditor.get().worldPanelSelections.get(WorldEditor.get().worldLastPanelPid);
        var elB = findElement(other.page, otherId);
        if (elB == null) {
            return;
        }
        Map<?, ?> hB = elB.props().get("hologram") instanceof Map<?, ?> h ? h : Map.of();
        boolean width = "w".equals(axis);
        String tB = String.valueOf(elB.props().get("type"));
        double target = WorldHologram.holoNum(hB, width ? "width" : "height",
                "text".equals(tB) ? (width ? 2.0 : 0.25) : 1.0, other.page.variables());
        if (target <= 0) {
            return;
        }
        target = Math.round(target * 100) / 100.0;
        WorldEditor.get().pushWorldUndo("跨面板统一尺寸", "sizecross", members);
        var vars = worldPage.variables();
        int applied = 0;
        for (String memberId : members) {
            var el = findElement(worldPage, memberId);
            if (el == null) {
                continue;
            }
            Object raw = el.props().get("hologram");
            if (!(raw instanceof Map<?, ?> holo)) {
                continue;
            }
            Map<Object, Object> copy = new java.util.LinkedHashMap<>(holo);
            copy.put(width ? "width" : "height", target);
            el.props().put("hologram", copy);
            WorldEditor.get().worldEditProps.computeIfAbsent(memberId, k -> new ConcurrentHashMap<>())
                    .put("hologram." + (width ? "width" : "height"), String.valueOf(target));
            refreshCreateBlock(memberId);
            applied++;
        }
        invalidateLayout(worldPage);
        worldNodes = layoutPage(worldPage, 800, 600);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§b[OpenDreamCore] §f跨面板统一"
                        + (width ? "宽度" : "高度") + ": " + applied + " 元素 → "
                        + target + "（参考 " + WorldEditor.get().worldLastPanelPid + "·" + otherId + "；Ctrl+Z 撤消"
                        + (skippedSizeX > 0 ? "；跳过 " + skippedSizeX + " 锁定" : "") + "）"), false);
    }

    /** 跨面板分布：当前成员按轴等间隙分布到上次聚焦面板的可见范围（世界坐标换算含锚点差；一步撤消）。 */

    /** 元素全部可编辑属性路径（跳过 id/type/嵌套结构），排序输出。 */
    static List<String[]> elementPropPaths(Element element) {
        List<String[]> out = new java.util.ArrayList<>();
        element.props().forEach((key, v) -> {
            if ("id".equals(key) || "type".equals(key)) {
                return;
            }
            if (v instanceof Map<?, ?> m) {
                m.forEach((subKey, sv) -> {
                    if (sv instanceof Map<?, ?> || sv instanceof List<?>) {
                        return; // 嵌套结构跳过（保持简单）
                    }
                    out.add(new String[]{key + "." + subKey, String.valueOf(sv)});
                });
            } else if (!(v instanceof List<?>)) {
                out.add(new String[]{key, String.valueOf(v)});
            }
        });
        out.sort(java.util.Comparator.comparing(a -> a[0]));
        return out;
    }

    /** 编辑模式 Alt+悬停属性摘要：id/type + 全部属性路径（富文本，截断到 14 行）。 */
    private String worldElementSummary(RenderNode node) {
        if (node == null || worldPage == null) {
            return null;
        }
        var element = findElement(worldPage, node.id());
        if (element == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("§e").append(node.id()).append("§r  §7").append(node.type()).append("§r");
        List<String[]> props = elementPropPaths(element);
        for (int i = 0; i < props.size() && i < 14; i++) {
            sb.append("\n§7").append(props.get(i)[0]).append("§r = ").append(shortText(props.get(i)[1]));
        }
        if (props.size() > 14) {
            sb.append("\n§8… 共 ").append(props.size()).append(" 个属性");
        }
        return sb.toString();
    }

    /** 任意属性编辑面板：元素属性路径列表，点击打开值编辑（复用 applyWorldEditProp）；顶部过滤输入框。 */

    /** 批量属性面板：以首元素属性清单为模板，点击属性 → 整组快捷编辑（批量管线，一步撤消）；
     *  组内值不一致的属性以 ⚠ 标注（差异高亮）。 */

    /** 属性快捷编辑屏：颜色属性带调色板（点击即应用），枚举/数值属性带常用值按钮，任意值可手输。 */

    /** 样式化悬停气泡（屏幕/世界共用）：富文本（§ 颜色码）+ 可配底色/描边/文字色/宽度。 */
    public static void drawWorldTooltip(net.minecraft.client.gui.GuiGraphics g, Minecraft mc, String text,
                                        int textColor, int background, int border, int maxW) {
        var window = mc.getWindow();
        double scale = window.getGuiScaledWidth() / (double) window.getScreenWidth();
        int mx = (int) mc.mouseHandler.xpos(); // xpos() 已是 GUI 坐标
        int my = (int) mc.mouseHandler.ypos();
        if (maxW <= 0) {
            maxW = 200;
        }
        // 按行拆 + 自动折行（§ 颜色码不计宽度）
        java.util.List<String> lines = new java.util.ArrayList<>();
        for (String rawLine : text.split("\n", -1)) {
            if (rawLine.isEmpty()) {
                lines.add("");
                continue;
            }
            String stripped = com.opendreamcore.script.RichText.strip(rawLine);
            StringBuilder cur = new StringBuilder();
            for (int i = 0; i < rawLine.length(); i++) {
                String ch = String.valueOf(rawLine.charAt(i));
                String trial = cur + ch;
                if (mc.font.width(com.opendreamcore.script.RichText.strip(trial)) > maxW && cur.length() > 0) {
                    lines.add(cur.toString());
                    cur = new StringBuilder(ch);
                } else {
                    cur.append(trial);
                }
            }
            lines.add(cur.toString());
        }
        int bw = 8, bh = 4;
        int w = 0;
        for (String line : lines) {
            w = Math.max(w, mc.font.width(com.opendreamcore.script.RichText.strip(line)));
        }
        int boxW = w + bw * 2;
        int boxH = lines.size() * 9 + bh * 2;
        int x = mx + 10;
        int y = my - boxH - 6;
        int sw = window.getGuiScaledWidth();
        int sh = window.getGuiScaledHeight();
        if (x + boxW > sw - 4) {
            x = mx - boxW - 10;
        }
        if (y < 4) {
            y = my + 12;
        }
        g.fill(x, y, x + boxW, y + boxH, background);
        if (((border >>> 24) & 0xFF) > 0) {
            g.fill(x, y, x + boxW, y + 1, border);
            g.fill(x, y + boxH - 1, x + boxW, y + boxH, border);
            g.fill(x, y, x + 1, y + boxH, border);
            g.fill(x + boxW - 1, y, x + boxW, y + boxH, border);
        }
        for (int i = 0; i < lines.size(); i++) {
            drawRichLine(g, mc.font, lines.get(i), x + bw, y + bh + i * 9, textColor);
        }
    }

    /** 富文本行绘制（§ 颜色码分片段，未指定色用 baseColor；屏幕/世界共用）。 */
    public static void drawRichLine(net.minecraft.client.gui.GuiGraphics g,
                                    net.minecraft.client.gui.Font font, String legacy, int x, int y,
                                    int baseColor) {
        int cx = x;
        for (com.opendreamcore.script.RichText.Segment seg : com.opendreamcore.script.RichText.parse(legacy)) {
            if (seg.text().isEmpty()) {
                continue;
            }
            int c = 0xFF000000 | seg.color();
            if ((c & 0xFF000000) == 0) {
                c = baseColor;
            }
            g.drawString(font, seg.text(), cx, y, c);
            cx += font.width(seg.text());
        }
    }

    static boolean inside(int mx, int my, int[] r) {
        return mx >= r[0] && mx <= r[2] && my >= r[1] && my <= r[3];
    }

    /** 读取元素点路径属性（text.content / hologram.scale 等），无则 null。 */
    static String elementPropValue(Element element, String path) {
        Object cur = element.props();
        for (String seg : path.split("\\.")) {
            if (!(cur instanceof Map<?, ?> m)) {
                return null;
            }
            cur = m.get(seg);
        }
        return cur == null ? null : String.valueOf(cur);
    }

    /** 写元素点路径属性（中间 map 逐级复制，保持 props 不可变链可安全重建）。 */
    static void setElementPropPath(Element element, String path, String value) {
        String[] seg = path.split("\\.");
        Map<String, Object> props = element.props();
        if (seg.length == 1) {
            props.put(seg[0], value);
            return;
        }
        Object cur = props.get(seg[0]);
        Map<Object, Object> map = new java.util.LinkedHashMap<>(
                cur instanceof Map<?, ?> m ? (Map<?, ?>) m : Map.of());
        props.put(seg[0], map);
        for (int i = 1; i < seg.length - 1; i++) {
            Object next = map.get(seg[i]);
            Map<Object, Object> sub = new java.util.LinkedHashMap<>(
                    next instanceof Map<?, ?> m ? (Map<?, ?>) m : Map.of());
            map.put(seg[i], sub);
            map = sub;
        }
        map.put(seg[seg.length - 1], value);
    }

    /** 属性编辑应用：写入元素 props + 重建布局 + 记入未保存列表（可撤消）。 */

    /** 批量属性编辑：多个元素同一路径一次设置（一步撤消、逐个记入未保存、各自刷新 __create__）。 */

    // ---------- 新增 / 删除元素（WYSIWYG 全流程） ----------

    /** Ctrl 是否按下（拖拽复制用）。 */
    static boolean ctrlDown(Minecraft mc) {
        long win = mc.getWindow().getWindow();
        return org.lwjgl.glfw.GLFW.glfwGetKey(win, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL) == 1
                || org.lwjgl.glfw.GLFW.glfwGetKey(win, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL) == 1;
    }

    /**
     * Ctrl 拖拽复制：深拷贝元素整树（新 id 唯一化 + parent 引用重映射），
     * 位置微偏移避免与原件重叠，记入 __create__（保存时服务端插入页面文件）。
     */
    /** 剪切：深拷贝右键目标到剪贴板 + 删除原元素（Ctrl+V 粘贴到本页/其它面板）。 */
    public void cutWorldElement(String elementId) {
        if (worldPage == null || elementId == null) {
            return;
        }
        var src = findElement(worldPage, elementId);
        if (src == null) {
            return;
        }
        worldClipboard.clear();
        worldClipboard.add(copyElementTree(src, src.id(), new java.util.HashMap<>()));
        WorldEditor.get().saveWorldClipboard();
        WorldEditor.get().deleteWorldElement(); // 删除当前选中（= elementId）
    }

    /** Ctrl+C：深拷贝选中元素（多选集 / 整组 / 单元素）到剪贴板（跨会话持久到磁盘）。 */

    /** 剪贴板持久化：序列化到 gameDir/opendreamcore/clipboard.json（跨会话/跨页复用）。 */

    void ensureWorldClipboardLoaded() {
        if (WorldEditor.get().worldClipboardLoaded) {
            return;
        }
        WorldEditor.get().worldClipboardLoaded = true;
        try {
            java.io.File file = new java.io.File(
                    new java.io.File(Minecraft.getInstance().gameDirectory, "opendreamcore"), "clipboard.json");
            if (!file.isFile()) {
                return;
            }
            String json = java.nio.file.Files.readString(file.toPath(), java.nio.charset.StandardCharsets.UTF_8);
            com.google.gson.JsonArray arr = com.google.gson.JsonParser.parseString(json).getAsJsonArray();
            worldClipboard.clear();
            for (com.google.gson.JsonElement el : arr) {
                if (el.isJsonObject()) {
                    Object m = new com.google.gson.Gson().fromJson(el, Map.class);
                    if (m instanceof Map<?, ?> mm) {
                        worldClipboard.add(elementFromClipboardMap(mm));
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    /** 元素 → 剪贴板持久化 Map（递归；layout/visibleWhen/enabledWhen 简单场不持久化）。 */
    static Map<String, Object> elementToClipboardMap(Element e) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("id", e.id());
        m.put("type", e.type());
        m.put("props", e.props());
        if (!e.actions().isEmpty()) {
            m.put("actions", e.actions());
        }
        if (e.parent() != null) {
            m.put("parent", e.parent());
        }
        if (!e.children().isEmpty()) {
            java.util.List<Object> kids = new java.util.ArrayList<>();
            for (Element c : e.children()) {
                kids.add(elementToClipboardMap(c));
            }
            m.put("children", kids);
        }
        return m;
    }

    /** 剪贴板持久化 Map → 元素（递归还原）。 */
    private static Element elementFromClipboardMap(Map<?, ?> m) {
        String id = String.valueOf(m.get("id"));
        String type = String.valueOf(m.get("type"));
        Map<String, Object> props = new java.util.LinkedHashMap<>();
        if (m.get("props") instanceof Map<?, ?> pm) {
            pm.forEach((k, v) -> props.put(String.valueOf(k), v));
        }
        Map<String, String> actions = new java.util.LinkedHashMap<>();
        if (m.get("actions") instanceof Map<?, ?> am) {
            am.forEach((k, v) -> actions.put(String.valueOf(k), String.valueOf(v)));
        }
        java.util.List<Element> children = new java.util.ArrayList<>();
        if (m.get("children") instanceof List<?> cl) {
            for (Object o : cl) {
                if (o instanceof Map<?, ?> om) {
                    children.add(elementFromClipboardMap(om));
                }
            }
        }
        String parent = m.get("parent") == null ? null : String.valueOf(m.get("parent"));
        return new Element(id, type, null, props, null, null, actions, children, parent);
    }

    /** Ctrl+V：剪贴板整组粘贴（新 id、相对布局保持、一步撤消、粘贴集接管多选；跨会话剪贴板懒加载）。 */

    /** 粘贴元素：深拷贝（新 id 唯一化 + parent 重映射）+ 位置微偏移 + 记入 __create__ + 重建布局（可撤消）。 */

    /** 粘贴主体（不推撤消栈、id 显式指定；组复制用：外部一次性快照全部新 id）。 */

    /** 粘贴主体（共享 idMap：剪贴板整组粘贴时跨元素 parent 引用重映射）。 */

    /** 粘贴主体（reserved：多元素粘贴时子元素 id 生成避让预分配的根 id，防碰撞）。 */

    /** 深拷贝元素整树：props/actions 深拷贝，子元素 id 唯一化，parent 引用重映射。 */
    Element copyElementTree(Element src, String newId, Map<String, String> idMap) {
        return copyElementTree(src, newId, idMap, java.util.Set.of());
    }

    /** 深拷贝元素整树（used：本次拷贝已占用的 id 集合，随分配增长防兄弟碰撞）。 */
    Element copyElementTree(Element src, String newId, Map<String, String> idMap,
                                    java.util.Set<String> used) {
        idMap.put(src.id(), newId);
        used.add(newId);
        Map<String, Object> props = new java.util.LinkedHashMap<>();
        src.props().forEach((k, v) -> props.put(k, deepCopy(v)));
        Object parent = props.get("parent");
        if (parent != null && idMap.containsKey(String.valueOf(parent))) {
            props.put("parent", idMap.get(String.valueOf(parent)));
        }
        Map<String, String> actions = src.actions() == null ? null : new java.util.LinkedHashMap<>(src.actions());
        List<Element> children = new java.util.ArrayList<>();
        for (Element child : src.children()) {
            children.add(copyElementTree(child, uniqueWorldElementId(used), idMap, used));
        }
        return new Element(newId, src.type(), src.layout(), props, src.visibleWhen(), src.enabledWhen(),
                actions, children, null);
    }

    /** 深拷贝 props 值（Map/List/基本类型；String/Number/Boolean 不可变直接复用）。 */
    static Object deepCopy(Object v) {
        if (v instanceof Map<?, ?> m) {
            Map<Object, Object> out = new java.util.LinkedHashMap<>();
            m.forEach((k, val) -> out.put(k, deepCopy(val)));
            return out;
        }
        if (v instanceof List<?> l) {
            List<Object> out = new java.util.ArrayList<>();
            l.forEach(item -> out.add(deepCopy(item)));
            return out;
        }
        return v;
    }

    /** 从元素完整 props 生成 YAML 块（复制用：保留 group/actions 等全部顶层键）。 */
    static String elementYamlBlockFromProps(String id, Element element) {
        Map<String, Object> el = new java.util.LinkedHashMap<>();
        el.put("id", id);
        el.put("type", element.type());
        element.props().forEach((k, v) -> {
            if (!"id".equals(k) && !"type".equals(k)) {
                el.put(k, v);
            }
        });
        if (!element.actions().isEmpty()) {
            el.put("actions", new java.util.LinkedHashMap<>(element.actions()));
        }
        String dumped = new org.yaml.snakeyaml.Yaml().dump(el);
        StringBuilder sb = new StringBuilder();
        String[] blockLines = dumped.split("\n", -1);
        for (int i = 0; i < blockLines.length; i++) {
            if (blockLines[i].isBlank()) {
                continue;
            }
            sb.append(i == 0 ? "- " : "  ").append(blockLines[i]).append('\n');
        }
        return sb.toString().trim();
    }

    /** 新增元素：默认 props + 面板中心位置，生成 YAML 块记入未保存（保存时服务端插入页面文件；可撤消）。 */

    /** 在指定锚点相对位置创建元素（工具栏拖入创建用）。 */

    /** 删除元素（多选时批量删除整树）：本地移除 + 记入未保存删除（保存时服务端从页面文件移除整块；可撤消）。 */

    static boolean removeElementRecursive(List<Element> elements, String id) {
        for (int i = 0; i < elements.size(); i++) {
            Element element = elements.get(i);
            if (id.equals(element.id())) {
                elements.remove(i);
                return true;
            }
            if (removeElementRecursive(element.children(), id)) {
                return true;
            }
        }
        return false;
    }

    String uniqueWorldElementId() {
        return uniqueWorldElementId(java.util.Set.of());
    }

    /** 唯一元素 id（扫描现有节点 + 额外避让 reserved，多元素粘贴防碰撞）。 */
    String uniqueWorldElementId(java.util.Set<String> reserved) {
        java.util.Set<String> existing = new java.util.HashSet<>();
        WorldEditor.get().collectWorldIds(worldNodes, existing);
        existing.addAll(reserved);
        int n = 1;
        String id;
        do {
            id = "el_" + n++;
        } while (existing.contains(id));
        return id;
    }

    /** 唯一组名（扫描全部元素 hologram.group，避开已有组名；reserved 额外避让）。 */
    private String uniqueWorldGroupId(java.util.Set<String> reserved) {
        java.util.Set<String> used = new java.util.HashSet<>();
        collectGroups(worldNodes, used);
        used.addAll(reserved);
        int n = 1;
        String g;
        do {
            g = "g" + n++;
        } while (used.contains(g));
        return g;
    }

    private String uniqueWorldGroupId() {
        return uniqueWorldGroupId(java.util.Set.of());
    }

    private static void collectGroups(List<RenderNode> nodes, java.util.Set<String> out) {
        if (nodes == null) {
            return;
        }
        for (RenderNode node : nodes) {
            Object raw = node.props().get("hologram");
            if (raw instanceof Map<?, ?> h && h.get("group") != null) {
                out.add(String.valueOf(h.get("group")));
            }
            collectGroups(node.children(), out);
        }
    }

    /** 重设元素组名（hologram.group 写回 + 刷新 __create__ 块）。 */
    private void setWorldElementGroup(String elementId, String group) {
        var element = findElement(worldPage, elementId);
        if (element == null) {
            return;
        }
        Object raw = element.props().get("hologram");
        Map<Object, Object> holo = new java.util.LinkedHashMap<>(
                raw instanceof Map<?, ?> h ? (Map<?, ?>) h : java.util.Map.of());
        holo.put("group", group);
        element.props().put("hologram", holo);
        refreshCreateBlock(elementId);
    }

    /** 批量复制指定元素集：一步撤消快照 + 整组重链接 + 副本集接管多选；返回被按元素副本 id。 */
    String copyElementsForDrag(List<String> sources, String pressedId, boolean regroup) {
        if (sources.isEmpty()) {
            return null;
        }
        // 预生成全部新 id（创建前一次性快照 → 撤消一步 = 整批移除）
        java.util.Set<String> existing = new java.util.HashSet<>();
        WorldEditor.get().collectWorldIds(worldNodes, existing);
        List<String> newIds = new java.util.ArrayList<>();
        int n = 1;
        for (int i = 0; i < sources.size(); i++) {
            String id;
            do {
                id = "el_" + n++;
            } while (existing.contains(id));
            existing.add(id);
            newIds.add(id);
        }
        WorldEditor.get().pushWorldUndo("复制 " + sources.size() + " 元素", null, newIds);
        // 整组重链接：组内全部成员都在 sources 内 → 该组副本统一换新组名（每组一个，互不冲突）
        java.util.Map<String, String> groupRename = new java.util.LinkedHashMap<>();
        java.util.Set<String> reserved = new java.util.HashSet<>();
        if (regroup) {
            for (String src : sources) {
                String g = worldGroupOf(src);
                if (g == null || groupRename.containsKey(g)) {
                    continue;
                }
                List<String> members = worldGroupMembers(g);
                boolean all = true;
                for (String m : members) {
                    if (!sources.contains(m)) {
                        all = false;
                        break;
                    }
                }
                if (all) {
                    String fresh = uniqueWorldGroupId(reserved);
                    reserved.add(fresh);
                    groupRename.put(g, fresh);
                }
            }
        }
        String pressedCopy = null;
        for (int i = 0; i < sources.size(); i++) {
            var member = findElement(worldPage, sources.get(i));
            if (member == null) {
                continue;
            }
            String copyId = WorldEditor.get().pasteWorldElementInto(member, newIds.get(i));
            if (copyId == null) {
                continue;
            }
            String g = worldGroupOf(sources.get(i));
            if (g != null && groupRename.containsKey(g)) {
                setWorldElementGroup(copyId, groupRename.get(g));
            }
            if (sources.get(i).equals(pressedId)) {
                pressedCopy = copyId;
            }
        }
        // 副本集接管多选（拖拽时副本整体联动）
        worldEditMulti.clear();
        worldEditMulti.addAll(newIds);
        WorldEditor.get().worldEditSelected = pressedCopy;
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§a[OpenDreamCore] §f已复制 " + sources.size()
                        + " 个元素（拖拽放置）"), false);
        return pressedCopy;
    }

    /** 新元素默认 hologram（面板中心）。 */
    static Map<String, Object> defaultWorldHolo(String type) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("x", 0);
        m.put("y", 0);
        m.put("z", 0);
        if ("text".equals(type)) {
            m.put("scale", 0.02);
            m.put("width", 2);
            m.put("height", 0.25);
        } else if ("tabs".equals(type)) {
            m.put("width", 3);
            m.put("height", 0.22);
        } else {
            m.put("width", 1);
            m.put("height", 1);
        }
        return m;
    }

    /** 新元素默认组件属性。 */
    static Map<String, Object> defaultWorldSpec(String type) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        switch (type) {
            case "text" -> {
                m.put("content", "新文本");
                m.put("color", "#FFFFFF");
            }
            case "rect" -> m.put("color", "#42A5F5");
            case "item_slot", "item_display" -> {
                m.put("item", "minecraft:diamond");
                m.put("count", 1);
            }
            case "slider" -> {
                m.put("min", 0);
                m.put("max", 100);
                m.put("value", 50);
            }
            case "toggle" -> m.put("value", false);
            case "dropdown" -> m.put("options", List.of("选项一", "选项二"));
            case "progress" -> {
                m.put("min", 0);
                m.put("max", 100);
                m.put("value", 30);
                m.put("color", "#66BB6A");
            }
            case "tabs" -> {
                m.put("options", List.of("页签一", "页签二"));
                m.put("active", "页签一");
                m.put("color", "#2A3A52");
                m.put("activeColor", "#42A5F5");
            }
            case "image" -> m.put("src", "gui/logo.png");
            default -> {
            }
        }
        return m;
    }

    /** 生成元素 YAML 块（相对缩进 0 的列表项：第一行 "- id:"，子行 +2；服务端插入时整体对齐）。 */
    static String elementYamlBlock(String id, String type, Map<String, Object> holo, Map<String, Object> spec) {
        Map<String, Object> el = new java.util.LinkedHashMap<>();
        el.put("id", id);
        el.put("type", type);
        el.put("hologram", holo);
        if (spec != null && !spec.isEmpty()) {
            el.put(type, spec);
        }
        String dumped = new org.yaml.snakeyaml.Yaml().dump(el);
        StringBuilder sb = new StringBuilder();
        String[] blockLines = dumped.split("\n", -1);
        for (int i = 0; i < blockLines.length; i++) {
            if (blockLines[i].isBlank()) {
                continue;
            }
            sb.append(i == 0 ? "- " : "  ").append(blockLines[i]).append('\n');
        }
        return sb.toString().trim();
    }

    /** 新增元素类型选择屏。 */

    /** 动作绑定面板：click/hover/input 槽位列表，点击编辑脚本，[清除] 移除绑定（保存写回页面文件）。 */

    /** 批量动作绑定屏（多选/编组）：click/hover/input 槽位，脚本应用到全部目标元素；清除 = 空提交。 */

    /** 页签归属设置屏：列出页面已知页签（点击设置，空项 = 公共区）；单元素或批量（多选）。 */

    /** 按 id 查找当前页面元素（编辑屏访问器）。 */

    /** Alt+滚轮整体缩放 / Ctrl+Alt+滚轮整体旋转（平台 GLFW 滚轮回调链入）：true = 已消费。 */
    public boolean consumeWorldScroll(double dx, double dy) {
        if (dy == 0 || !WorldEditor.get().worldEditMode || worldPage == null || worldNodes == null) {
            return false;
        }
        if (Minecraft.getInstance().screen != null) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        // M+Shift+滚轮：锚点微调步进循环（0.1 粗 / 0.01 细 / 0.001 微）
        if (mKeyHeld(mc) && shiftHeld(mc)) {
            WorldEditor.get().worldAnchorStepIdx = Math.max(0, Math.min(2, WorldEditor.get().worldAnchorStepIdx + (dy > 0 ? 1 : -1)));
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§b[OpenDreamCore] §f锚点微调步进: "
                            + worldAnchorStep() + "（M+方向键微移）"), false);
            return true;
        }
        if (ctrlDown(mc) && altHeld(mc)) {
            if (!rotateWorldSelection(dy > 0 ? 5 : -5)) {
                rotateWorldPanel(dy > 0 ? 5 : -5); // 无选区 → 回退全面板整体旋转
            }
            return true;
        }
        if (!altHeld(mc)) {
            return false;
        }
        double factor = dy > 0 ? 1.1 : 1 / 1.1;
        if (!scaleWorldSelection(factor)) {
            scaleWorldPanel(factor); // 无选区 → 回退全面板缩放
        }
        return true;
    }

    /** 组/多选/单选缩放（Alt+滚轮）：绕包围盒中心缩放（位置 + 尺寸 + 字号同步）；返回是否执行（无选区 false）。 */
    boolean scaleWorldSelection(double factor) {
        if (!WorldEditor.get().worldEditMode || worldPage == null || WorldEditor.get().worldEditSelected == null) {
            return false;
        }
        java.util.List<String> members = new java.util.ArrayList<>();
        String grp = worldGroupOf(WorldEditor.get().worldEditSelected);
        if (grp != null && worldGroupMembers(grp).size() > 1) {
            members.addAll(worldGroupMembers(grp));
        } else if (worldEditMulti.size() >= 2) {
            members.addAll(worldEditMulti);
        } else {
            members.add(WorldEditor.get().worldEditSelected);
        }
        int skipped = members.size();
        members = filterLocked(members);
        skipped -= members.size();
        if (members.isEmpty()) {
            return false;
        }
        var vars = worldPage.variables();
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        java.util.List<Element> els = new java.util.ArrayList<>();
        for (String id : members) {
            var el = findElement(worldPage, id);
            if (el == null) {
                continue;
            }
            Object raw = el.props().get("hologram");
            if (!(raw instanceof Map<?, ?> holo)) {
                continue;
            }
            String type = String.valueOf(el.props().get("type"));
            double w = WorldHologram.holoNum(holo, "width", "text".equals(type) ? 2.0 : 1.0, vars);
            double h = WorldHologram.holoNum(holo, "height", "text".equals(type) ? 0.25 : 1.0, vars);
            double x = WorldHologram.holoNum(holo, "x", 0, vars);
            double y = WorldHologram.holoNum(holo, "y", 0, vars);
            minX = Math.min(minX, x - w / 2);
            maxX = Math.max(maxX, x + w / 2);
            minY = Math.min(minY, y - h / 2);
            maxY = Math.max(maxY, y + h / 2);
            els.add(el);
        }
        if (els.isEmpty()) {
            return false;
        }
        double ccx = (minX + maxX) / 2;
        double ccy = (minY + maxY) / 2;
        WorldEditor.get().pushWorldUndo("选区缩放", "scale", members); // 连续滚轮合并
        for (Element element : els) {
            Object raw = element.props().get("hologram");
            Map<?, ?> holo = (Map<?, ?>) raw;
            boolean text = "text".equals(String.valueOf(element.props().get("type")));
            double x = WorldHologram.holoNum(holo, "x", 0, vars);
            double y = WorldHologram.holoNum(holo, "y", 0, vars);
            Map<Object, Object> copy = new java.util.LinkedHashMap<>(holo);
            copy.put("x", Math.round((ccx + (x - ccx) * factor) * 100) / 100.0);
            copy.put("y", Math.round((ccy + (y - ccy) * factor) * 100) / 100.0);
            copy.put("width", Math.round(WorldHologram.holoNum(holo, "width",
                    text ? 2.0 : 1.0, vars) * factor * 100) / 100.0);
            copy.put("height", Math.round(WorldHologram.holoNum(holo, "height",
                    text ? 0.25 : 1.0, vars) * factor * 100) / 100.0);
            if (holo.get("scale") instanceof Number sn) {
                copy.put("scale", Math.round(sn.doubleValue() * factor * 1000) / 1000.0); // 字号随缩放
            }
            element.props().put("hologram", copy);
            WorldEditor.get().worldEditDirty.put(element.id(), new double[]{
                    WorldHologram.holoNum(copy, "x", 0, vars),
                    WorldHologram.holoNum(copy, "y", 0, vars),
                    WorldHologram.holoNum(copy, "z", 0, vars)});
            refreshCreateBlock(element.id());
        }
        invalidateLayout(worldPage);
        worldNodes = layoutPage(worldPage, 800, 600);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§b[OpenDreamCore] §f选区缩放 ×" + Math.round(factor * 100) / 100.0
                        + "（绕包围盒中心；Ctrl+Z 撤消"
                        + (skipped > 0 ? "；跳过 " + skipped + " 锁定" : "") + "）"), false);
        return true;
    }

    /** 组/多选/单选旋转（Ctrl+Alt+滚轮）：绕选中集包围盒中心刚性旋转（位置 + yaw 同步）；返回是否执行（无选区 false）。 */
    boolean rotateWorldSelection(double angleDeg) {
        if (!WorldEditor.get().worldEditMode || worldPage == null || WorldEditor.get().worldEditSelected == null) {
            return false;
        }
        java.util.List<String> members = new java.util.ArrayList<>();
        String grp = worldGroupOf(WorldEditor.get().worldEditSelected);
        if (grp != null && worldGroupMembers(grp).size() > 1) {
            members.addAll(worldGroupMembers(grp));
        } else if (worldEditMulti.size() >= 2) {
            members.addAll(worldEditMulti);
        } else {
            members.add(WorldEditor.get().worldEditSelected);
        }
        int skipped = members.size();
        members = filterLocked(members);
        skipped -= members.size();
        if (members.isEmpty()) {
            return false;
        }
        var vars = worldPage.variables();
        // 包围盒中心（旋转基准）
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        java.util.List<Element> els = new java.util.ArrayList<>();
        for (String id : members) {
            var el = findElement(worldPage, id);
            if (el == null) {
                continue;
            }
            Object raw = el.props().get("hologram");
            if (!(raw instanceof Map<?, ?> holo)) {
                continue;
            }
            String type = String.valueOf(el.props().get("type"));
            double w = WorldHologram.holoNum(holo, "width", "text".equals(type) ? 2.0 : 1.0, vars);
            double h = WorldHologram.holoNum(holo, "height", "text".equals(type) ? 0.25 : 1.0, vars);
            double x = WorldHologram.holoNum(holo, "x", 0, vars);
            double y = WorldHologram.holoNum(holo, "y", 0, vars);
            minX = Math.min(minX, x - w / 2);
            maxX = Math.max(maxX, x + w / 2);
            minY = Math.min(minY, y - h / 2);
            maxY = Math.max(maxY, y + h / 2);
            els.add(el);
        }
        if (els.isEmpty()) {
            return false;
        }
        double ccx = (minX + maxX) / 2;
        double ccy = (minY + maxY) / 2;
        WorldEditor.get().pushWorldUndo("选区旋转", "rotate", members); // 连续滚轮合并
        double rad = Math.toRadians(angleDeg);
        double cos = Math.cos(rad), sin = Math.sin(rad);
        for (Element element : els) {
            Object raw = element.props().get("hologram");
            Map<?, ?> holo = (Map<?, ?>) raw;
            double x = WorldHologram.holoNum(holo, "x", 0, vars) - ccx;
            double y = WorldHologram.holoNum(holo, "y", 0, vars) - ccy;
            double nx = x * cos - y * sin;
            double ny = x * sin + y * cos;
            Object yawObj = holo.get("yaw");
            double nyaw = (yawObj instanceof Number n ? n.doubleValue() : 0) + angleDeg;
            Map<Object, Object> copy = new java.util.LinkedHashMap<>(holo);
            copy.put("x", Math.round((nx + ccx) * 100) / 100.0);
            copy.put("y", Math.round((ny + ccy) * 100) / 100.0);
            copy.put("yaw", Math.round(nyaw * 10) / 10.0);
            element.props().put("hologram", copy);
            WorldEditor.get().worldEditDirty.put(element.id(), new double[]{
                    WorldHologram.holoNum(copy, "x", 0, vars),
                    WorldHologram.holoNum(copy, "y", 0, vars),
                    WorldHologram.holoNum(copy, "z", 0, vars)});
            WorldEditor.get().worldEditProps.computeIfAbsent(element.id(), k -> new ConcurrentHashMap<>())
                    .put("hologram.yaw", String.valueOf(Math.round(nyaw * 10) / 10.0));
            refreshCreateBlock(element.id());
        }
        invalidateLayout(worldPage);
        worldNodes = layoutPage(worldPage, 800, 600);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§b[OpenDreamCore] §f选区旋转 " + els.size() + " 元素 "
                        + Math.round(angleDeg * 10) / 10.0 + "°（绕包围盒中心；Ctrl+Z 撤消"
                        + (skipped > 0 ? "；跳过 " + skipped + " 锁定" : "") + "）"), false);
        return true;
    }

    /** 整体旋转面板：全部元素位置绕锚点旋转 + yaw 同步（刚性旋转，锚点为原点）；一步撤消。 */
    private void rotateWorldPanel(double angleDeg) {
        java.util.Set<String> ids = new java.util.HashSet<>();
        WorldEditor.get().collectWorldIds(worldNodes, ids);
        if (ids.isEmpty()) {
            return;
        }
        List<String> all = new java.util.ArrayList<>(ids);
        int skippedRot = all.size();
        all = filterLocked(all); // 锁定元素不参与整体旋转
        skippedRot -= all.size();
        if (all.isEmpty()) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§e[OpenDreamCore] §f全部元素已锁定，无法整体旋转"), false);
            return;
        }
        WorldEditor.get().pushWorldUndo("整体旋转", "rotate", all); // 连续滚轮合并
        double rad = Math.toRadians(angleDeg);
        double cos = Math.cos(rad), sin = Math.sin(rad);
        var vars = worldPage.variables();
        for (String id : all) {
            var element = findElement(worldPage, id);
            if (element == null) {
                continue;
            }
            Object raw = element.props().get("hologram");
            if (!(raw instanceof Map<?, ?> holo)) {
                continue;
            }
            double x = WorldHologram.holoNum(holo, "x", 0, vars);
            double y = WorldHologram.holoNum(holo, "y", 0, vars);
            double nx = x * cos - y * sin;
            double ny = x * sin + y * cos;
            Object yawObj = holo.get("yaw");
            double nyaw = (yawObj instanceof Number n ? n.doubleValue() : 0) + angleDeg;
            Map<Object, Object> copy = new java.util.LinkedHashMap<>(holo);
            copy.put("x", Math.round(nx * 100) / 100.0);
            copy.put("y", Math.round(ny * 100) / 100.0);
            copy.put("yaw", Math.round(nyaw * 10) / 10.0);
            element.props().put("hologram", copy);
            WorldEditor.get().worldEditDirty.put(id, new double[]{
                    WorldHologram.holoNum(copy, "x", 0, vars),
                    WorldHologram.holoNum(copy, "y", 0, vars),
                    WorldHologram.holoNum(copy, "z", 0, vars)});
            WorldEditor.get().worldEditProps.computeIfAbsent(id, k -> new ConcurrentHashMap<>())
                    .put("hologram.yaw", String.valueOf(Math.round(nyaw * 10) / 10.0));
            refreshCreateBlock(id);
        }
        invalidateLayout(worldPage);
        worldNodes = layoutPage(worldPage, 800, 600);
        WorldEditor.get().worldPanelRotateAccum += angleDeg; // 旋转读数累计（Ctrl+Alt 按住时锚点旁显示）
        WorldEditor.get().worldPanelRotateAt = System.currentTimeMillis();
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§b[OpenDreamCore] §f整体旋转 "
                        + (angleDeg >= 0 ? "+" : "") + Math.round(angleDeg * 10) / 10.0
                        + "°（Ctrl+Alt+滚轮"
                        + (skippedRot > 0 ? "；跳过 " + skippedRot + " 锁定" : "") + "）"), false);
    }

    /** 整体缩放面板：全部元素 x/y/width/height（text 含 scale 字号）× factor，锚点为原点；一步撤消。 */
    private void scaleWorldPanel(double factor) {
        java.util.Set<String> ids = new java.util.HashSet<>();
        WorldEditor.get().collectWorldIds(worldNodes, ids);
        if (ids.isEmpty()) {
            return;
        }
        List<String> all = new java.util.ArrayList<>(ids);
        int skippedScale = all.size();
        all = filterLocked(all); // 锁定元素不参与整体缩放
        skippedScale -= all.size();
        if (all.isEmpty()) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§e[OpenDreamCore] §f全部元素已锁定，无法整体缩放"), false);
            return;
        }
        WorldEditor.get().pushWorldUndo("整体缩放", "scale", all); // 连续滚轮合并
        var vars = worldPage.variables();
        for (String id : all) {
            var element = findElement(worldPage, id);
            if (element == null) {
                continue;
            }
            Object raw = element.props().get("hologram");
            if (!(raw instanceof Map<?, ?> holo)) {
                continue;
            }
            boolean text = "text".equals(String.valueOf(element.props().get("type")));
            Map<Object, Object> copy = new java.util.LinkedHashMap<>(holo);
            copy.put("x", Math.round(WorldHologram.holoNum(holo, "x", 0, vars) * factor * 100) / 100.0);
            copy.put("y", Math.round(WorldHologram.holoNum(holo, "y", 0, vars) * factor * 100) / 100.0);
            copy.put("width", Math.round(WorldHologram.holoNum(holo, "width",
                    text ? 2.0 : 1.0, vars) * factor * 100) / 100.0);
            copy.put("height", Math.round(WorldHologram.holoNum(holo, "height",
                    text ? 0.25 : 1.0, vars) * factor * 100) / 100.0);
            if (holo.get("scale") instanceof Number sn) {
                copy.put("scale", Math.round(sn.doubleValue() * factor * 1000) / 1000.0); // 字号随缩放
            }
            element.props().put("hologram", copy);
            WorldEditor.get().worldEditDirty.put(id, new double[]{
                    WorldHologram.holoNum(copy, "x", 0, vars),
                    WorldHologram.holoNum(copy, "y", 0, vars),
                    WorldHologram.holoNum(copy, "z", 0, vars)});
            refreshCreateBlock(id);
        }
        invalidateLayout(worldPage);
        worldNodes = layoutPage(worldPage, 800, 600);
        WorldEditor.get().worldPanelScaleAccum *= factor; // 缩放读数累计（Alt 按住时锚点旁显示）
        WorldEditor.get().worldPanelScaleAt = System.currentTimeMillis();
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§b[OpenDreamCore] §f整体缩放 ×"
                        + Math.round(factor * 100) / 100.0 + "（Alt+滚轮"
                        + (skippedScale > 0 ? "；跳过 " + skippedScale + " 锁定" : "") + "）"), false);
    }

    /** 是否有未保存编辑（退出编辑时确认提示用）。 */
    public boolean hasPendingWorldEdits() {
        return WorldEditor.get().worldEditMode && worldPage != null
                && (!WorldEditor.get().worldEditDirty.isEmpty() || !WorldEditor.get().worldEditProps.isEmpty() || !WorldEditor.get().worldEditDeletes.isEmpty());
    }

    /** 未保存项数量（位置 + 属性 + 删除）。 */
    public int worldPendingEditCount() {
        return WorldEditor.get().worldEditDirty.size() + WorldEditor.get().worldEditProps.size() + WorldEditor.get().worldEditDeletes.size();
    }

    /** 退出编辑模式（释放租约 + 状态消息）。 */
    public void exitWorldEditMode() {
        if (worldPage == null) {
            return;
        }
        releaseLease(worldPage.id());
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§e[OpenDreamCore] §f已退出世界编辑模式"), false);
    }

    /** 撤消栈标签（最新在前；历史面板展示用）。 */
    public java.util.List<String> worldUndoLabels() {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (WorldEditOp op : WorldEditor.get().worldUndoStack) {
            out.add(op.label);
        }
        return out;
    }

    /** 重做栈标签（最新在前；历史面板展示用）。 */
    public java.util.List<String> worldRedoLabels() {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (WorldEditOp op : WorldEditor.get().worldRedoStack) {
            out.add(op.label);
        }
        return out;
    }

    /** 编辑历史屏（工具栏 历史▾）：撤消/重做双列列表 + 单步操作按钮。 */

    /** 退出编辑确认屏（有未保存草稿时）：保存并退出 / 放弃并退出 / 取消。 */

    /** 元素查找屏（编辑模式 F 键）：按 id 子串实时过滤，点击/Enter 定位选中。 */

    /** 批量重命名屏：前缀 + 起始序号 → 多选元素按序号改名（可撤消，保存写回）。 */

    /** 属性输入屏：极简 EditBox 对话框（Enter 提交 / ESC 取消）。 */

    /** 元素模板管理屏（对齐屏模板▽）：行点击 = 粘贴（id 冲突加后缀），Shift+点击 = 删除；＋存当前 / 翻页 / 关闭。 */

    /** 背景预设管理屏（对齐屏预设▽）：行点击 = 载入，Shift+点击 = 删除；顶部 ＋保存当前 / 翻页 / 关闭。 */

    /** 页面变量编辑屏（对齐屏变量▽）：行点击 = 改值，Shift+点击 = 删除；顶部 ＋新增 / 翻页 / 关闭。 */

    /** 编辑工具栏（HUD 阶段绘制，两行）：信息 + 步长/吸附/保存/放弃/退出；选中元素属性行。 */
    private void renderWorldEditToolbar(net.minecraft.client.gui.GuiGraphics g) {
        var mc = Minecraft.getInstance();
        int w = mc.getWindow().getGuiScaledWidth();
        int x0 = w / 2 - 280;
        int pw = 560;
        int y0 = 8, barH = 30;
        WorldEditor.get().toolbarVisible = true;
        g.fill(x0, y0, x0 + pw, y0 + barH, 0xCC10151F);
        g.fill(x0, y0, x0 + pw, y0 + 2, 0xFF42A5F5);
        // 折叠切换（▾ 收起 / ▸ 展开第二~四行）
        drawToolbarButton(g, mc, WorldEditor.get().toolbarCollapse, x0 + 2, y0,
                WorldEditor.get().worldToolbarCollapsed ? "▸ 展开" : "▾ 收起", 0xFF78909C);
        // 第一行文本：页面 + 选中元素 + 坐标 + 未保存数
        String page = worldPage.id() == null ? "?" : worldPage.id();
        String sel = WorldEditor.get().worldEditSelected == null ? "-" : WorldEditor.get().worldEditSelected;
        String pos = "";
        if (WorldEditor.get().worldEditSelected != null) {
            var el = findElement(worldPage, WorldEditor.get().worldEditSelected);
            if (el != null) {
                Object raw = el.props().get("hologram");
                Map<?, ?> holo = raw instanceof Map<?, ?> h ? h : Map.of();
                var vars = worldPage.variables();
                pos = String.format(java.util.Locale.ROOT, " %.2f, %.2f, %.2f",
                        WorldHologram.holoNum(holo, "x", 0, vars),
                        WorldHologram.holoNum(holo, "y", 0, vars),
                        WorldHologram.holoNum(holo, "z", 0, vars));
            }
        }
        String text = "世界编辑 " + page + " | " + sel + (pos.isEmpty() ? "" : " [" + pos + "]")
                + (WorldEditor.get().worldEditDirty.isEmpty() && WorldEditor.get().worldEditProps.isEmpty() && WorldEditor.get().worldEditDeletes.isEmpty()
                ? "" : " | 未保存 " + (WorldEditor.get().worldEditDirty.size() + WorldEditor.get().worldEditProps.size() + WorldEditor.get().worldEditDeletes.size()) + " 项");
        g.drawString(mc.font, text, x0 + 66, y0 + (barH - 8) / 2, 0xFFE0E0E0);
        // 第一行按钮：步长 / 吸附 / 保存 / 放弃 / 退出
        drawToolbarButton(g, mc, WorldEditor.get().toolbarStep, x0 + 280, y0, "步长" + fmtShort(WorldEditor.get().worldEditStep), 0xFF7E57C2);
        drawToolbarButton(g, mc, WorldEditor.get().toolbarSnap, x0 + 336, y0,
                WorldEditor.get().worldEditSnap > 0 ? "吸附" + fmtShort(WorldEditor.get().worldEditSnap) : "吸附关", 0xFF26A69A);
        drawToolbarButton(g, mc, WorldEditor.get().toolbarSave, x0 + 392, y0, "保存", 0xFF42A5F5);
        drawToolbarButton(g, mc, WorldEditor.get().toolbarDiscard, x0 + 448, y0, "放弃", 0xFF8D6E63);
        drawToolbarButton(g, mc, WorldEditor.get().toolbarExit, x0 + 504, y0, "退出", 0xFFE57373);
        if (WorldEditor.get().worldToolbarCollapsed) {
            return; // 折叠：只留第一行
        }
        // 第二行：新增/删除 + 选中元素属性编辑（文本/颜色/尺寸）
        int y1 = y0 + barH + 4, h2 = 26;
        g.fill(x0, y1, x0 + pw, y1 + h2, 0xCC151A24);
        drawToolbarButton(g, mc, WorldEditor.get().toolbarAdd, x0 + 10, y1, "新增", 0xFF66BB6A);
        drawToolbarButton(g, mc, WorldEditor.get().toolbarDelete, x0 + 70, y1, "删除", 0xFFEF5350);
        if (WorldEditor.get().worldEditSelected != null) {
            var el = findElement(worldPage, WorldEditor.get().worldEditSelected);
            if (el != null) {
                String type = String.valueOf(el.props().get("type"));
                drawToolbarButton(g, mc, WorldEditor.get().toolbarText, x0 + 130, y1, "文本", 0xFF5C6BC0);
                drawToolbarButton(g, mc, WorldEditor.get().toolbarColor, x0 + 190, y1, "颜色", 0xFFEC407A);
                drawToolbarButton(g, mc, WorldEditor.get().toolbarScale, x0 + 250, y1, "尺寸", 0xFFFFA726);
                drawToolbarButton(g, mc, WorldEditor.get().toolbarProps, x0 + 310, y1, "属性", 0xFFAB47BC);
                drawToolbarButton(g, mc, WorldEditor.get().toolbarAlign, x0 + 370, y1, "对齐", 0xFF26C6DA);
                // 当前值摘要
                String sum = "属性: ";
                if ("text".equals(type)) {
                    String c = elementPropValue(el, "text.content");
                    sum += "内容=" + (c == null ? "-" : shortText(c)) + "  ";
                    String col = elementPropValue(el, "text.color");
                    sum += "颜色=" + (col == null ? "-" : col) + "  ";
                }
                String s = elementPropValue(el, "hologram.scale");
                sum += "缩放=" + (s == null ? "-" : s);
                g.drawString(mc.font, sum, x0 + 444, y1 + 2, 0xFFB0BEC5);
                // 层级面包屑（父链）：▸ 根 > 父 > 当前（点击节点 = 选中该祖先；超宽从末端截断）
                WorldEditor.get().toolbarBreadcrumbRects.clear();
                WorldEditor.get().worldBreadcrumbIds.clear();
                List<String> chain = worldParentChain(WorldEditor.get().worldEditSelected);
                if (chain.size() >= 2) {
                    int bx = x0 + 444;
                    int by = y1 + 15;
                    int avail = x0 + pw - 4 - bx;
                    java.util.List<String> reversed = new java.util.ArrayList<>(chain);
                    java.util.Collections.reverse(reversed);
                    java.util.List<String> shown = new java.util.ArrayList<>();
                    int used = 0;
                    for (String id : reversed) {
                        int iw = mc.font.width(id) + (shown.isEmpty() ? 0 : mc.font.width(">") + 4);
                        if (shown.isEmpty() || used + iw <= avail) {
                            shown.add(id);
                            used += iw;
                        } else {
                            break;
                        }
                    }
                    java.util.Collections.reverse(shown);
                    if (shown.size() < chain.size()) {
                        g.drawString(mc.font, "…", bx, by, 0xFF78909C);
                        bx += mc.font.width("…") + 2;
                    }
                    for (int i = 0; i < shown.size(); i++) {
                        String id = shown.get(i);
                        boolean isSelf = i == shown.size() - 1;
                        int iw = mc.font.width(id);
                        g.drawString(mc.font, id, bx, by, isSelf ? 0xFFFFD54F : 0xFF90CAF9);
                        WorldEditor.get().toolbarBreadcrumbRects.add(new int[]{bx, by, bx + iw, by + 8});
                        WorldEditor.get().worldBreadcrumbIds.add(id);
                        bx += iw + 2;
                        if (i < shown.size() - 1) {
                            g.drawString(mc.font, ">", bx, by, 0xFF78909C);
                            bx += mc.font.width(">") + 2;
                        }
                    }
                }
            }
        } else {
            g.drawString(mc.font, "点击世界元素选中，或点 [新增] 创建元素", x0 + 140, y1 + (h2 - 8) / 2, 0xFFB0BEC5);
        }
        // 第三行：撤消 / 重做（Ctrl+Z / Ctrl+Y 的可见入口）+ 历史提示 + 修饰键速查
        int y2 = y1 + h2 + 4;
        g.fill(x0, y2, x0 + pw, y2 + barH, 0xCC10151F);
        drawToolbarButton(g, mc, WorldEditor.get().toolbarUndo, x0 + 10, y2, "撤消", 0xFF26A69A);
        drawToolbarButton(g, mc, WorldEditor.get().toolbarRedo, x0 + 70, y2, "重做", 0xFF26A69A);
        String hist = "历史 " + (WorldEditor.get().worldUndoStack.size() + WorldEditor.get().worldRedoStack.size())
                + " 步（Ctrl+Z 撤消 / Ctrl+Y 或 Ctrl+Shift+Z 重做）";
        g.drawString(mc.font, hist, x0 + 140, y2 + 4, 0xFF80CBC4);
        drawToolbarButton(g, mc, WorldEditor.get().toolbarHistory, x0 + 140, y2, "历史▾", 0xFF26A69A);
        String mods = "Ctrl=复制  Ctrl+Alt=复制整组  Z=层级  Alt=移动/滚轮缩放  M+拖=锚点  M+方向键=锚点微移  Shift=锁轴  G=网格  H=透视  U=背景  L=悬停加速  R=旋转90°  Y=旋转5°  Ctrl+[/]=尺寸±10%  O=透明度  T=文本对齐  I=干净预览  右键隐藏元素·J=显示全部  Ctrl+,/.:速度  Ctrl+Alt+,/.:宽  Shift+,/.:相位  Ctrl+Shift+,/.:间距  Alt+,/.:段长  Ctrl+Shift+Alt+,/.:副相  9/0:段数";
        g.drawString(mc.font, mods, x0 + 140, y2 + barH - 10, 0xFF90A4AE);
        // 第四行：拖入创建类型 chips（按住拖到面板释放 = 在该位置创建元素）
        int y3 = y2 + barH + 4;
        g.fill(x0, y3, x0 + pw, y3 + barH, 0xCC151A24);
        int chipW = 46, pitch = 48;
        for (int i = 0; i < WorldEditor.get().WORLD_TYPE_CHIPS.length; i++) {
            int cx = x0 + 4 + i * pitch;
            int[] rect = WorldEditor.get().toolbarTypeRects[i];
            rect[0] = cx;
            rect[1] = y3;
            rect[2] = cx + chipW;
            rect[3] = y3 + barH;
            boolean dragging = WorldEditor.get().WORLD_TYPE_CHIPS[i].equals(WorldEditor.get().worldTypeDrag);
            g.fill(cx, y3, cx + chipW, y3 + barH, dragging ? 0xFF2E5C8A : 0xFF1E2A38);
            g.fill(cx, y3 + 27, cx + chipW, y3 + barH, dragging ? 0xFF42A5F5 : 0xFF3A4A66);
            String label = WorldEditor.get().WORLD_TYPE_CHIP_LABELS[i];
            g.drawString(mc.font, label, cx + (chipW - mc.font.width(label)) / 2, y3 + (barH - 8) / 2, 0xFFFFFFFF);
        }
        g.drawString(mc.font, "按住拖到面板释放=创建", x0 + 4 + WorldEditor.get().WORLD_TYPE_CHIPS.length * pitch,
                y3 + (barH - 8) / 2, 0xFF78909C);
    }

    private static String fmtShort(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    static String shortText(String s) {
        return s.length() > 18 ? s.substring(0, 18) + "…" : s;
    }

    private void drawToolbarButton(net.minecraft.client.gui.GuiGraphics g, Minecraft mc, int[] rect,
                                   int x, int y, String label, int accent) {
        rect[0] = x;
        rect[1] = y;
        rect[2] = x + 60;
        rect[3] = y + 30;
        g.fill(x, y, x + 60, y + 30, 0xFF1E2A38);
        g.fill(x, y + 27, x + 60, y + 30, accent);
        g.drawString(mc.font, label, x + (60 - mc.font.width(label)) / 2, y + 11, 0xFFFFFFFF);
    }

    /** 右键菜单绘制（屏幕空间，锚定右键按下位置；渲染与点击共用同一构造保证索引一致）。 */
    private void renderWorldContextMenu(net.minecraft.client.gui.GuiGraphics g) {
        if (WorldEditor.get().worldCtxId == null) {
            return;
        }
        var mc = Minecraft.getInstance();
        List<String[]> items = worldContextItems();
        if (items.isEmpty()) {
            WorldEditor.get().worldCtxId = null;
            return;
        }
        int w = 150, ih = 20;
        int x = (int) WorldEditor.get().worldCtxX;
        int y = (int) WorldEditor.get().worldCtxY;
        int h = items.size() * ih + 4;
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        if (x + w > sw - 4) {
            x = sw - w - 4;
        }
        if (y + h > sh - 4) {
            y = sh - h - 4;
        }
        g.fill(x, y, x + w, y + h, 0xEE10151F);
        g.fill(x, y, x + w, y + 1, 0xFF42A5F5);
        double[] mouse = scaledMouse(mc);
        worldCtxRects.clear();
        for (int i = 0; i < items.size(); i++) {
            int iy = y + 2 + i * ih;
            boolean hover = inside((int) mouse[0], (int) mouse[1], new int[]{x, iy, x + w, iy + ih});
            boolean cursorRow = i == WorldEditor.get().worldCtxCursor;
            if (hover || cursorRow) {
                g.fill(x, iy, x + w, iy + ih, cursorRow && !hover ? 0xFF2A3A50 : 0xFF1E2A38);
            }
            String label = items.get(i)[0];
            g.drawString(mc.font, label, x + 8, iy + 6, (hover || cursorRow) ? 0xFFFFFFFF : 0xFFB0BEC5);
            worldCtxRects.add(new int[]{x, iy, x + w, iy + ih});
        }
    }

    /** 右键菜单项（[label, action]；取消 = 仅关闭）。 */
    List<String[]> worldContextItems() {
        List<String[]> items = new java.util.ArrayList<>();
        if (WorldEditor.get().worldCtxId == null) {
            return items;
        }
        var element = findElement(worldPage, WorldEditor.get().worldCtxId);
        if (element == null) {
            return items;
        }
        items.add(new String[]{"删除", "delete"});
        items.add(new String[]{"复制", "copy"});
        items.add(new String[]{"剪切", "cut"});
        items.add(new String[]{"复制JSON", "copyjson"});
        items.add(new String[]{"复制YAML", "copyyaml"});
        items.add(new String[]{"复制格式", "copyfmt"});
        items.add(new String[]{"粘贴格式", "pastefmt"});
        items.add(new String[]{"另存为模板…", "savetpl"});
        items.add(new String[]{"阵列复制…", "array"});
        if (worldEditMulti.size() >= 2 && worldEditMulti.contains(WorldEditor.get().worldCtxId)) {
            items.add(new String[]{"批量属性…", "batchprops"});
            items.add(new String[]{"批量重命名…", "batchrename"});
            items.add(new String[]{"批量居中", "centerbatch"});
            items.add(new String[]{"批量页签…", "batchtab"});
            items.add(new String[]{"网格排列", "gridarrange"});
        }
        items.add(new String[]{"属性…", "props"});
        items.add(new String[]{"重命名…", "rename"});
        items.add(new String[]{"页签归属…", "tab"});
        String ctxGroup = worldGroupOf(WorldEditor.get().worldCtxId);
        if (ctxGroup != null && worldGroupMembers(ctxGroup).size() > 1) {
            items.add(new String[]{"选中整组", "selectgroup"});
        }
        boolean hasKids = !element.children().isEmpty();
        boolean hasParent = element.props().get("parent") != null;
        if (hasKids) {
            items.add(new String[]{"选中首个子元素", "firstchild"});
        }
        if (hasParent) {
            items.add(new String[]{"选中父元素", "parent"});
        }
        items.add(new String[]{"上一个同级", "sibprev"});
        items.add(new String[]{"下一个同级", "sibnext"});
        items.add(new String[]{"对齐…", "align"});
        if ("text".equals(String.valueOf(element.props().get("type")))) {
            items.add(new String[]{"编辑文本…", "text"});
        }
        String colorPath = firstColorPropPath(element);
        if (colorPath != null) {
            items.add(new String[]{"颜色…", "color:" + colorPath});
            items.add(new String[]{"复制颜色", "copycolor"});
            items.add(new String[]{"粘贴颜色", "pastecolor"});
        }
        items.add(new String[]{"缩放…", "scale"});
        items.add(new String[]{"旋转…", "yaw"});
        items.add(new String[]{"旋转90°", "rot90"});
        items.add(new String[]{"重置变换", "resettf"});
        items.add(new String[]{"对齐网格", "snapgrid"});
        items.add(new String[]{"拍平Z", "flat"});
        items.add(new String[]{"定位…", "loc"});
        items.add(new String[]{"复制坐标", "copypos"});
        items.add(new String[]{"居中", "center"});
        boolean visible = ClientController.get().worldElementVisible(
                worldPage.id() == null ? "world" : worldPage.id(), WorldEditor.get().worldCtxId);
        items.add(new String[]{visible ? "隐藏" : "显示", "hide"});
        // 组批量隐藏/显示（会话级）
        String hideGrp = worldGroupOf(WorldEditor.get().worldCtxId);
        if (hideGrp != null && worldGroupMembers(hideGrp).size() > 1) {
            items.add(new String[]{visible ? "隐藏整组" : "显示整组", "hidegroup"});
        }
        // 持久隐藏（hologram.hidden，保存写回页面文件）
        Object hraw = element.props().get("hologram");
        boolean phidden = hraw instanceof Map<?, ?> hm
                && Boolean.parseBoolean(String.valueOf(hm.get("hidden")));
        items.add(new String[]{phidden ? "持久显示" : "持久隐藏", "phide"});
        items.add(new String[]{"动作…", "actions"});
        items.add(new String[]{worldElementLocked(WorldEditor.get().worldCtxId) ? "解锁" : "锁定", "lock"});
        items.add(new String[]{"取消", "cancel"});
        return items;
    }

    private static String firstColorPropPath(Element element) {
        for (String[] prop : elementPropPaths(element)) {
            if (prop[0].endsWith("color") || prop[0].endsWith("Color")) {
                return prop[0];
            }
        }
        return null;
    }

    /** 读取元素属性路径值（无返回 null）。 */
    private static String propPathValue(Element element, String path) {
        Object cur = element.props();
        for (String part : path.split("\\.")) {
            if (!(cur instanceof Map<?, ?> m)) {
                return null;
            }
            cur = m.get(part);
        }
        return cur == null ? null : String.valueOf(cur);
    }

    /** 复制选中元素首个颜色属性值（跨元素快速配色）。 */
    public void copyWorldElementColor() {
        if (WorldEditor.get().worldEditSelected == null || worldPage == null) {
            return;
        }
        var el = findElement(worldPage, WorldEditor.get().worldEditSelected);
        if (el == null) {
            return;
        }
        String path = firstColorPropPath(el);
        if (path == null) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§e[OpenDreamCore] §f该元素没有颜色属性"), false);
            return;
        }
        String v = propPathValue(el, path);
        if (v == null) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§e[OpenDreamCore] §f颜色属性为空"), false);
            return;
        }
        WorldEditor.get().worldCopiedColor = v;
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§a[OpenDreamCore] §f已复制颜色 " + path + " = " + v), false);
    }

    /** 粘贴颜色到选区（组/多选/单选）各自首个颜色属性；锁定跳过、可撤消。 */
    public void pasteWorldElementColor() {
        pasteWorldElementColor(null);
    }

    /** 粘贴颜色（显式成员列表；null = 从当前选区推导）。 */
    public void pasteWorldElementColor(java.util.List<String> explicitMembers) {
        if (WorldEditor.get().worldCopiedColor == null) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§e[OpenDreamCore] §f先复制颜色（右键「复制颜色」）"), false);
            return;
        }
        if (WorldEditor.get().worldEditSelected == null || worldPage == null) {
            return;
        }
        java.util.List<String> members;
        if (explicitMembers != null && !explicitMembers.isEmpty()) {
            members = new java.util.ArrayList<>(explicitMembers);
        } else {
            members = new java.util.ArrayList<>();
            String grp = worldGroupOf(WorldEditor.get().worldEditSelected);
            if (grp != null && worldGroupMembers(grp).size() > 1) {
                members.addAll(worldGroupMembers(grp));
            } else if (worldEditMulti.size() >= 2) {
                members.addAll(worldEditMulti);
            } else {
                members.add(WorldEditor.get().worldEditSelected);
            }
        }
        int skipped = members.size();
        members = filterLocked(members);
        skipped -= members.size();
        if (members.isEmpty()) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§e[OpenDreamCore] §f目标元素全部锁定，已跳过"), false);
            return;
        }
        WorldEditor.get().pushWorldUndo("粘贴颜色", "pastecolor", members);
        int changed = 0;
        for (String id : members) {
            var el = findElement(worldPage, id);
            if (el == null) {
                continue;
            }
            String path = firstColorPropPath(el);
            if (path == null) {
                continue;
            }
            setElementPropPath(el, path, WorldEditor.get().worldCopiedColor);
            WorldEditor.get().worldEditProps.computeIfAbsent(id, k -> new ConcurrentHashMap<>())
                    .put(path, WorldEditor.get().worldCopiedColor);
            refreshCreateBlock(id);
            changed++;
        }
        if (changed > 0) {
            invalidateLayout(worldPage);
            worldNodes = layoutPage(worldPage, 800, 600);
        }
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§a[OpenDreamCore] §f已粘贴颜色 " + WorldEditor.get().worldCopiedColor + " 到 "
                        + changed + " 个元素（Ctrl+Z 撤消"
                        + (skipped > 0 ? "；跳过 " + skipped + " 锁定" : "") + "）"), false);
    }

    /** 右键菜单项执行（先清多选，菜单作用于右键目标单个元素）。 */
    void runWorldContextAction(int index) {
        List<String[]> items = worldContextItems();
        if (index < 0 || index >= items.size()) {
            WorldEditor.get().worldCtxId = null;
            worldCtxRects.clear();
            return;
        }
        String action = items.get(index)[1];
        String targetId = WorldEditor.get().worldCtxId;
        WorldEditor.get().worldCtxId = null;
        worldCtxRects.clear();
        if ("cancel".equals(action) || targetId == null) {
            return;
        }
        // 批量属性：作用于整个多选集（先取快照再清多选）
        List<String> batch = worldEditMulti.size() >= 2 && worldEditMulti.contains(targetId)
                ? new java.util.ArrayList<>(worldEditMulti) : null;
        worldEditMulti.clear(); // 右键菜单 = 单元素操作（批量属性除外）
        WorldEditor.get().worldEditSelected = targetId;
        switch (action) {
            case "delete" -> WorldEditor.get().deleteWorldElement();
            case "copy" -> {
                String copyId = WorldEditor.get().createWorldElementCopy(targetId);
                if (copyId != null) {
                    Minecraft.getInstance().player.displayClientMessage(
                            Component.literal("§a[OpenDreamCore] §f已复制 " + copyId + "（可拖拽移动）"), false);
                }
            }
            case "copyjson" -> {
                var el = findElement(worldPage, targetId);
                if (el != null) {
                    Minecraft.getInstance().keyboardHandler.setClipboard(
                            toJsonValue(elementToJson(el)));
                    Minecraft.getInstance().player.displayClientMessage(
                            Component.literal("§a[OpenDreamCore] §f已复制元素 JSON（Ctrl+V 到外部工具）"), false);
                }
            }
            case "cut" -> cutWorldElement(targetId);
            case "copyyaml" -> WorldEditor.get().copyWorldElementYaml();
            case "copyfmt" -> {
                WorldEditor.get().worldEditSelected = targetId;
                WorldEditor.get().copyWorldElementFormat();
            }
            case "pastefmt" -> {
                WorldEditor.get().worldEditSelected = targetId;
                WorldEditor.get().pasteWorldElementFormat();
            }
            case "savetpl" -> {
                WorldEditor.get().worldEditSelected = targetId;
                Minecraft.getInstance().setScreen(new WorldEditPropScreen(
                        "模板名（1~32 字符 · 保存该元素为命名模板）", "",
                        v -> WorldEditor.get().saveWorldTemplate(v)));
            }
            case "array" -> Minecraft.getInstance().setScreen(new WorldEditPropScreen(
                    "阵列复制（份数:间距，如 4:1.5）", "3:1.5",
                    v -> ClientController.get().arrayDuplicateWorldSelection(v)));
            case "batchprops" -> {
                if (batch != null) {
                    openBatchPropsScreen(batch);
                }
            }
            case "batchrename" -> {
                if (batch != null) {
                    Minecraft.getInstance().setScreen(new WorldEditRenameScreen(batch));
                }
            }
            case "centerbatch" -> {
                if (batch != null) {
                    centerWorldElements(batch);
                }
            }
            case "batchtab" -> {
                if (batch != null) {
                    Minecraft.getInstance().setScreen(new WorldEditTabScreen(batch));
                }
            }
            case "gridarrange" -> {
                if (batch != null) {
                    arrangeWorldGrid(batch);
                } else {
                    arrangeWorldGrid();
                }
            }
            case "props" -> openPropsScreen();
            case "rename" -> Minecraft.getInstance().setScreen(new WorldEditPropScreen(
                    "重命名元素（新 id，字母/数字/下划线/短横线）", targetId,
                    v -> {
                        if (renameWorldElementToOne(targetId, v)) {
                            WorldEditor.get().worldEditSelected = null; // 选中同步（旧 id 失效）
                        }
                    }));
            case "selectgroup" -> selectWorldGroup(targetId);
            case "firstchild" -> selectWorldFirstChild(targetId);
            case "parent" -> selectWorldParent(targetId);
            case "sibprev" -> selectWorldSibling(targetId, -1);
            case "sibnext" -> selectWorldSibling(targetId, 1);
            case "tab" -> Minecraft.getInstance().setScreen(new WorldEditTabScreen(targetId));
            case "align" -> openAlignScreen();
            case "text" -> openPropEditor("text.content", "编辑文本内容");
            case "scale" -> openPropEditor("hologram.scale", "编辑缩放（世界单位/像素）");
            case "yaw" -> openPropEditor("hologram.yaw", "编辑旋转角（度，-180~180）");
            case "resettf" -> resetWorldElementTransform(targetId);
            case "rot90" -> WorldEditor.get().rotateWorldElement90(90);
            case "snapgrid" -> {
                if (batch != null) {
                    snapWorldSelectionToGrid(batch);
                } else {
                    snapWorldSelectionToGrid();
                }
            }
            case "loc" -> {
                var locEl = findElement(worldPage, targetId);
                if (locEl != null) {
                    Object lRaw = locEl.props().get("hologram");
                    String cur = "0,0";
                    if (lRaw instanceof Map<?, ?> lh) {
                        double lx = WorldHologram.holoNum(lh, "x", 0, worldPage.variables());
                        double ly = WorldHologram.holoNum(lh, "y", 0, worldPage.variables());
                        cur = Math.round(lx * 100) / 100.0 + "," + Math.round(ly * 100) / 100.0;
                    }
                    final String initial = cur;
                    Minecraft.getInstance().setScreen(new WorldEditPropScreen(
                            "定位（x,y，相对面板锚点）", initial,
                            v -> setWorldElementPosition(targetId, v)));
                }
            }
            case "flat" -> {
                if (batch != null) {
                    flattenWorldZMembers(batch);
                } else {
                    flattenWorldZSelection();
                }
            }
            case "copypos" -> {
                var posEl = findElement(worldPage, targetId);
                if (posEl != null) {
                    Object posRaw = posEl.props().get("hologram");
                    if (posRaw instanceof Map<?, ?> ph) {
                        double px = WorldHologram.holoNum(ph, "x", 0, worldPage.variables());
                        double py = WorldHologram.holoNum(ph, "y", 0, worldPage.variables());
                        double pz = WorldHologram.holoNum(ph, "z", 0, worldPage.variables());
                        String txt = Math.round(px * 100) / 100.0 + "," + Math.round(py * 100) / 100.0
                                + "," + Math.round(pz * 100) / 100.0;
                        Minecraft.getInstance().keyboardHandler.setClipboard(txt);
                        Minecraft.getInstance().player.displayClientMessage(
                                Component.literal("§a[OpenDreamCore] §f已复制坐标: " + txt), false);
                    }
                }
            }
            case "center" -> centerWorldElement(targetId);
            case "hide" -> {
                ClientController.get().toggleWorldElementHide(targetId);
                Minecraft.getInstance().player.displayClientMessage(
                        Component.literal("§7[OpenDreamCore] §f元素已切换隐藏/显示（会话级）"), false);
            }
            case "hidegroup" -> toggleWorldGroupHide(targetId);
            case "phide" -> toggleWorldElementHidden(targetId);
            case "actions" -> openActionScreen();
            case "lock" -> toggleWorldElementLock(targetId);
            case "copycolor" -> {
                WorldEditor.get().worldEditSelected = targetId;
                copyWorldElementColor();
            }
            case "pastecolor" -> {
                WorldEditor.get().worldEditSelected = targetId;
                if (batch != null) {
                    pasteWorldElementColor(batch);
                } else {
                    pasteWorldElementColor();
                }
            }
            default -> {
                if (action.startsWith("color:")) {
                    openPropEditor(action.substring(6), "编辑颜色（#RRGGBB）");
                }
            }
        }
    }

    /** 批量属性面板：以首个元素属性清单为模板，整组一次编辑（提交走批量管线）。 */
    private void openBatchPropsScreen(List<String> ids) {
        if (ids.isEmpty()) {
            return;
        }
        var first = findElement(worldPage, ids.get(0));
        if (first == null) {
            return;
        }
        Minecraft.getInstance().setScreen(new WorldEditBatchPropsScreen(ids, first));
    }

    /** 脚本：设置世界元素位置（hologram.x/y/z 写回 + 重建布局）。 */
    public boolean setWorldElementPos(String elementId, double x, double y, double z) {
        if (worldPage == null) {
            return false;
        }
        var element = findElement(worldPage, elementId);
        if (element == null) {
            return false;
        }
        Object raw = element.props().get("hologram");
        Map<Object, Object> holo = new java.util.LinkedHashMap<>(
                raw instanceof Map<?, ?> h ? (Map<?, ?>) h : java.util.Map.of());
        holo.put("x", x);
        holo.put("y", y);
        holo.put("z", z);
        element.props().put("hologram", holo);
        invalidateLayout(worldPage);
        worldNodes = layoutPage(worldPage, 800, 600);
        return true;
    }

    /** 脚本：获取世界元素位置（{x, y, z}；未找到返回 null）。 */
    public java.util.Map<String, Object> getWorldElementPos(String elementId) {
        if (worldPage == null) {
            return null;
        }
        var element = findElement(worldPage, elementId);
        if (element == null) {
            return null;
        }
        Object raw = element.props().get("hologram");
        Map<?, ?> holo = raw instanceof Map<?, ?> h ? h : java.util.Map.of();
        var vars = worldPage.variables();
        var out = new java.util.LinkedHashMap<String, Object>();
        out.put("x", WorldHologram.holoNum(holo, "x", 0, vars));
        out.put("y", WorldHologram.holoNum(holo, "y", 0, vars));
        out.put("z", WorldHologram.holoNum(holo, "z", 0, vars));
        return out;
    }

    public LocalPageManager localPages() {
        return localPages;
    }

    /** 本地页面 ConfigIR 缓存（import 跨页面解析用）。 */
    private final Map<String, Map<String, Object>> localIr = new ConcurrentHashMap<>();
    /** 服务端下发页面 ConfigIR 缓存（import 解析可引用已同步页面）。 */
    private final Map<String, Map<String, Object>> serverIr = new ConcurrentHashMap<>();

    /** 本地目录加载完成后注册全部 IR（先清空旧的本地项）。 */
    public void registerLocalIr(Map<String, Map<String, Object>> irst) {
        localIr.clear();
        localIr.putAll(irst);
    }

    /** 按 id 取页面 ConfigIR（本地优先，其次服务端）；import 展开用。 */
    public Map<String, Object> pageIr(String pageId) {
        Map<String, Object> ir = localIr.get(pageId);
        if (ir == null) {
            ir = serverIr.get(pageId);
        }
        return ir;
    }

    /** 按 id 找页面（本地仓库优先，其次服务端下发）。 */
    public Page pageById(String id) {
        if (id == null) {
            return null;
        }
        Page page = localPages.get(id);
        if (page == null) {
            page = serverPages.get(id);
        }
        return page;
    }

    /** 服务端下发的页面（按 id；无则 null）。/odc edit 编辑服务端页面用。 */
    public Page serverPage(String id) {
        return id == null ? null : serverPages.get(id);
    }

    /**
     * 编辑模型页面：应用删除/复制（ElementEditStore），返回过滤后的页面。
     * 删除标记的元素（含递归子元素）剔除；复制元素追加在末尾。
     */
    private Page editedPage(Page page) {
        if (page == null) {
            return null;
        }
        String pageId = page.id() == null ? "page" : page.id();
        java.util.Set<String> deleted = elementEdits.deleted(pageId);
        java.util.List<Element> copies = elementEdits.copies(pageId);
        if ((deleted == null || deleted.isEmpty()) && copies.isEmpty()
                && !elementEdits.hasHidden(pageId)) {
            return page;
        }
        java.util.List<Element> kept = new java.util.ArrayList<>();
        for (Element element : page.elements()) {
            if (deleted != null && deleted.contains(element.id())) {
                continue;
            }
            kept.add(filterChildren(element, deleted, pageId));
        }
        kept.addAll(copies);
        return new Page(page.id(), page.title(), page.match(), page.displayMode(),
                page.variables(), kept, page.functions(), page.options());
    }

    private static Element filterChildren(Element element, java.util.Set<String> deleted, String pageId) {
        java.util.List<Element> kids = new java.util.ArrayList<>();
        for (Element child : element.children()) {
            if (deleted != null && deleted.contains(child.id())) {
                continue;
            }
            kids.add(filterChildren(child, deleted, pageId));
        }
        if (ClientController.get().elementEdits().isHidden(pageId, element.id())) {
            // 运行时隐藏：visibleWhen 置 false（保留子树，显示时即刻恢复）
            return new Element(element.id(), element.type(), element.layout(), element.props(),
                    "false", element.enabledWhen(), element.actions(), kids, element.parent());
        }
        return new Element(element.id(), element.type(), element.layout(), element.props(),
                element.visibleWhen(), element.enabledWhen(), element.actions(), kids, element.parent());
    }

    /** 布局入口：应用编辑模型（删除/复制/位置覆盖）后交给 LayoutEngine。 */
    List<RenderNode> layoutPage(Page page, double w, double h) {
        String pageId = page.id() == null ? "page" : page.id();
        String key = layoutKey(page);
        // 布局缓存：变量/编辑覆盖 hash 不变时直接复用（state_patch 高频/拖拽提交提速）
        String hash = layoutHash(page, pageId);
        Object[] cached = layoutCache.get(key);
        if (cached != null && hash.equals(cached[0])) {
            return (List<RenderNode>) cached[1];
        }
        List<RenderNode> nodes = LayoutEngine.layout(editedPage(page), w, h, elementEdits.forPage(pageId));
        // 根层按 z 升序稳定排序（与 RenderNode 子节点排序同规则）：z 大画在上面、同 z 保持声明顺序。
        // 屏幕/HUD/世界全部节点列表的唯一出口 —— 绘制层迭确定，不受 YAML 书写顺序影响。
        nodes = UiRenderer.zSorted(nodes);
        layoutCache.put(key, new Object[]{hash, nodes});
        return nodes;
    }

    /** 布局缓存键（页面对象身份；重建后自然 miss）。 */
    private static String layoutKey(Page page) {
        return "p" + System.identityHashCode(page);
    }

    /** 布局版本：页面变量 + 全局变量 + 编辑覆盖（删除/复制/隐藏/位置）的字符串摘要。 */
    private String layoutHash(Page page, String pageId) {
        return String.valueOf(page.variables())
                + "|" + String.valueOf(globals)
                + "|" + String.valueOf(elementEdits.forPage(pageId))
                + "|" + String.valueOf(elementEdits.deleted(pageId))
                + "|" + String.valueOf(elementEdits.copies(pageId))
                + "|" + String.valueOf(elementEdits.hidden(pageId));
    }

    /** 显式失效（元素结构变化但 vars/edits 未变的入口：设置元素属性/拖拽提交等）。 */
    void invalidateLayout(Page page) {
        if (page != null) {
            layoutCache.remove(layoutKey(page));
        }
    }

    /** embed 嵌入页布局缓存（页面 id + 尺寸 → 节点树）。 */
    private final Map<String, List<RenderNode>> embedCache = new ConcurrentHashMap<>();
    /** 布局结果缓存（layoutKey → {版本摘要, 节点}）。 */
    private final Map<String, Object[]> layoutCache = new ConcurrentHashMap<>();

    /** 取嵌入页布局（按嵌入容器尺寸布局，结果缓存）。 */
    public List<RenderNode> embeddedNodes(Page page, int w, int h) {
        String key = (page.id() == null ? "page" : page.id()) + "@" + w + "x" + h;
        return embedCache.computeIfAbsent(key, k -> layoutPage(page, w, h));
    }

    public CloudSyncClient cloud() {
        return cloud;
    }

    public TooltipStore tooltips() {
        return tooltips;
    }

    public ElementEditStore elementEdits() {
        return elementEdits;
    }

    public String elementEditsSnapshot(String pageId) {
        return elementEdits.snapshot(pageId);
    }

    public void restoreElementEdits(String pageId, String json) {
        elementEdits.restore(pageId, json);
    }

    /** 编辑模式改位置后重建当前页面布局。 */
    public void refreshCurrent() {
        if (screen == null) {
            return;
        }
        invalidateLayout(screen.page());
        Minecraft mc = Minecraft.getInstance();
        double w = mc.getWindow().getGuiScaledWidth();
        double h = mc.getWindow().getGuiScaledHeight();
        String id = screen.page().id() == null ? "page" : screen.page().id();
        screen.refresh(layoutPage(screen.page(), w, h));
    }

    /** 服务端全局变量（{{global.xxx}} 插值）。 */
    public Map<String, Object> globals() {
        return globals;
    }

    /** 全局状态到达：更新变量并刷新当前页面。 */
    public void handleGlobalState(com.opendreamcore.protocol.message.GlobalState state) {
        globals.clear();
        globals.putAll(state.values());
        Minecraft mc = Minecraft.getInstance();
        double w = mc.getWindow().getGuiScaledWidth();
        double h = mc.getWindow().getGuiScaledHeight();
        if (screen != null) {
            screen.refresh(layoutPage(screen.page(), w, h));
        }
        if (hudPage != null) {
            hudNodes = layoutPage(hudPage, w, h);
        }
        if (worldPage != null) {
            worldNodes = layoutPage(worldPage, 800, 600);
        }
        LOGGER.info("服务端全局状态已更新 {} 项", state.values().size());
    }

    // ================= 服务端窗口标题下发（window_title）=================

    private boolean hudDiagDone;
    private volatile boolean managedPacksDone;

    static {
        // 模组加载阶段：读缓存并直接应用最近一次的标题（无缓存跳过，服务端下发后哈希去重写盘）
        try {
            new com.opendreamcore.client.controller.TitlePushService().applyLatestCachedTitle();
        } catch (Throwable ignored) {
        }
    }

    private final com.opendreamcore.client.controller.TitlePushService titlePushService =
            new com.opendreamcore.client.controller.TitlePushService();

    /** window_title 到达：委托 controller/TitlePushService（C6 抽出）。 */
    public void handleWindowTitle(com.opendreamcore.protocol.message.WindowTitlePush push) {
        titlePushService.handleWindowTitle(push);
    }

    /** 进服早期调用（JOIN 事件）：按服务器地址预载缓存标题——委托 controller/TitlePushService。 */
    public void preloadServerTitle() {
        titlePushService.preloadServerTitle();
    }

    /** 断线/退出服务器：解除覆盖，还原本地 branding 序列。 */
    public void clearServerTitle() {
        titlePushService.clearServerTitle();
    }

    /** 进服后拉取服务端 tooltip 注册表。 */
    public void requestTooltips() {
        var buf = new com.opendreamcore.protocol.OdcByteArrayBuf();
        new com.opendreamcore.protocol.message.TooltipResync().encode(buf);
        sendRaw(com.opendreamcore.protocol.Protocol.TOOLTIP_RESYNC, buf.toByteArray());
    }

    /** 页面生命周期脚本（open/close/tick/resize...），渲染线程调用。 */
    public void runLifecycle(Page page, String name) {
        String script = page.functions() == null ? null : page.functions().get(name);
        if (script == null || script.isBlank()) {
            return;
        }
        try {
            com.opendreamcore.script.Scope scope = new com.opendreamcore.script.Scope();
            page.variables().forEach(scope::assignVar);
            com.opendreamcore.script.DreamLang.execute(script, scope);
        } catch (Exception e) {
            LOGGER.warn("页面 {} 生命周期 {} 脚本出错: {}", page.id(), name, e.toString());
        }
    }

    /** 页签切换生命周期：页面 functions.onTabChange 脚本（vars.tab = 新页签，vars.prevTab = 旧页签）。 */
    void runTabChangeLifecycle(Page page, String newTab, String prevTab) {
        if (page == null) {
            return;
        }
        String script = page.functions() == null ? null : page.functions().get("onTabChange");
        if (script == null || script.isBlank()) {
            return;
        }
        try {
            com.opendreamcore.script.Scope scope = new com.opendreamcore.script.Scope();
            page.variables().forEach(scope::assignVar);
            scope.assignVar("tab", newTab);
            if (prevTab != null) {
                scope.assignVar("prevTab", prevTab);
            }
            com.opendreamcore.script.DreamLang.execute(script, scope);
        } catch (Exception e) {
            LOGGER.warn("页面 {} onTabChange 脚本出错: {}", page.id(), e.toString());
        }
    }

    private static int count(List<RenderNode> nodes) {
        int n = nodes.size();
        for (RenderNode node : nodes) {
            n += count(node.children());
        }
        return n;
    }

    // ---------- 事件发送 ----------

    /** 是否有服务端连接（多人/局域网）。 */
    public boolean isServerMode() {
        return Minecraft.getInstance().getConnection() != null;
    }

    // ---------- 脚本调度（委托 client/controller/ScriptScheduler，C6 拆分） ----------

    private final com.opendreamcore.client.controller.ScriptScheduler scriptScheduler =
            new com.opendreamcore.client.controller.ScriptScheduler(new com.opendreamcore.client.controller.ScriptScheduler.Host() {
                @Override public String currentPageId() { return ClientController.this.currentPageId(); }
                @Override public Page currentPage() { return ClientController.this.currentPage(); }
                @Override public Page pageById(String pageId) { return ClientController.this.pageById(pageId); }
                @Override public void runLocalAction(Page page, String script) { ClientController.this.runLocalAction(page, script); }
                @Override public void applyDelayedVar(String pageId, String varName, Object value) {
                    ClientController.this.applyDelayedVar(pageId, varName, value);
                }
            });

    /** 延迟变量到期应用（屏幕/HUD/世界分支；由 ScriptScheduler 回调）。 */
    void applyDelayedVar(String pageId, String varName, Object value) {
        Page page = pageById(pageId);
        if (page == null) return;
        page.variables().put(varName, value);
        Page cur = currentPage();
        if (cur != null && pageId.equals(cur.id())) {
            if (screen != null) refreshCurrent();
        } else if (hudPage != null && pageId.equals(hudPage.id())) {
            hudNodes = layoutPage(hudPage, Minecraft.getInstance().getWindow().getGuiScaledWidth(),
                    Minecraft.getInstance().getWindow().getGuiScaledHeight());
        } else if (worldPage != null && pageId.equals(worldPage.id())) {
            invalidateLayout(worldPage);
        }
    }

    /** 调度脚本执行；intervalMs = 0 一次性，>0 循环。返回任务 id。 */
    public long scheduleScript(String script, long delayMs, long intervalMs) {
        return scriptScheduler.scheduleScript(script, delayMs, intervalMs);
    }

    /** 防抖：同名键重置计时，安静 ms 毫秒后执行一次。 */
    public long debounceScript(String script, long ms, String key) {
        return scriptScheduler.debounceScript(script, ms, key);
    }

    /** 节流：周期内最多一次（合并尾调用）。 */
    public long throttleScript(String script, long ms, String key) {
        return scriptScheduler.throttleScript(script, ms, key);
    }

    /** 延迟设置页面变量（Screen.延迟设置变量）。 */
    public long delaySetPageVar(String varName, Object value, long ms) {
        return scriptScheduler.delaySetPageVar(varName, value, ms);
    }

    /** 取消页面挂起的延迟变量。 */
    public boolean cancelDelayedVar(String pageId, String varName) {
        return scriptScheduler.cancelDelayedVar(pageId, varName);
    }

    /** 延迟变量剩余毫秒（无挂起任务 -1）。 */
    public double delayedVarRemaining(String pageId, String varName) {
        return scriptScheduler.delayedVarRemaining(pageId, varName);
    }

    /** 取消脚本任务。 */
    public boolean cancelScript(long id) {
        return scriptScheduler.cancelScript(id);
    }

    /** 页面关闭清理全部任务。 */
    public void cancelScriptsForPage(String pageId) {
        scriptScheduler.cancelScriptsForPage(pageId);
    }

    private void tickScriptTasks() {
        scriptScheduler.tick();
    }


/** 当前页面 id（屏幕优先；无则为 null）。 */
    public String currentPageId() {
        Page page = currentPage();
        return page == null ? null : page.id();
    }

    /**
     * 元素事件分发：多人 → 上报服务端裁决；单机 → 本地执行元素 actions 脚本。
     */
    public void handleElementEvent(UiSession session, Page page, RenderNode node,
                                   UiEvent.Trigger trigger, String data) {
        if (isServerMode()) {
            sendEvent(session.event(node.id(), trigger, data));
            return;
        }
        // 单机模式：本地执行脚本（无服务端裁决）
        String script = null;
        if (node.source() != null) {
            script = node.source().actions().get(triggerName(trigger));
        }
        if (script != null && !script.isBlank()) {
            runLocalAction(page, script, data);
        }
    }

    /** 本地执行页面脚本（单机模式 actions / 生命周期共用）。 */
    public void runLocalAction(Page page, String script) {
        runLocalAction(page, script, null);
    }

    /** 本地执行页面脚本；data 非空时注入 vars.event / vars.input（事件数据）。 */
    public void runLocalAction(Page page, String script, String data) {
        try {
            com.opendreamcore.script.Scope scope = new com.opendreamcore.script.Scope();
            page.variables().forEach(scope::assignVar);
            globals.forEach(scope::assignGlobal);
            var player = Minecraft.getInstance().player;
            if (player != null) {
                scope.assignPlayer("name", player.getName().getString());
                scope.assignPlayer("health", (double) player.getHealth());
                scope.assignPlayer("level", (double) player.experienceLevel);
                scope.assignPlayer("x", player.getX());
                scope.assignPlayer("y", player.getY());
                scope.assignPlayer("z", player.getZ());
            }
            if (data != null) {
                scope.assignVar("event", data);
                scope.assignVar("input", data);
            }
            com.opendreamcore.script.DreamLang.execute(script, scope);
        } catch (Exception e) {
            LOGGER.warn("本地脚本执行失败: {}", e.toString());
        }
    }

    private static String triggerName(UiEvent.Trigger trigger) {
        return switch (trigger) {
            case CLICK -> "click";
            case HOVER -> "hover";
            case PRESS -> "press";
            case INPUT -> "input";
            case SCROLL -> "scroll";
            case KEY -> "key";
        };
    }

    public void sendEvent(UiEvent event) {
        if (event == null) {
            return;
        }
        var buf = new com.opendreamcore.protocol.OdcByteArrayBuf();
        event.encode(buf);
        sendRaw(com.opendreamcore.protocol.Protocol.UI_EVENT, buf.toByteArray());
    }

    // ---------- 服务端下发（page_control） ----------

    /** 握手竞态回放：密钥已到，把暂存的页面/HUD/控制指令按原序补跑一遍。 */
    private void flushPendingHandshake() {
        java.util.List<com.opendreamcore.protocol.message.PageSync> pages;
        java.util.List<com.opendreamcore.protocol.message.HudSync> huds;
        synchronized (pendingPageSyncs) {
            if (pendingPageSyncs.isEmpty()) {
                return;
            }
            pages = new java.util.ArrayList<>(pendingPageSyncs);
            pendingPageSyncs.clear();
        }
        synchronized (pendingHudSyncs) {
            huds = new java.util.ArrayList<>(pendingHudSyncs);
            pendingHudSyncs.clear();
        }
        LOGGER.info("握手回放：暂存页面 {} 条 / HUD {} 条", pages.size(), huds.size());
        for (var s : pages) {
            storeServerPage(s);
        }
        for (var h : huds) {
            handleHudSync(h);
        }
        // 控制指令最后回放：此时 serverPages 已填好
        java.util.List<com.opendreamcore.protocol.message.PageControl> controls;
        synchronized (pendingControls) {
            controls = new java.util.ArrayList<>(pendingControls);
            pendingControls.clear();
        }
        for (var c : controls) {
            try {
                handlePageControl(c);
            } catch (Exception e) {
                LOGGER.warn("回放控制指令失败 {}: {}", c.pageId(), e.toString());
            }
        }
    }

    public void handlePageControl(PageControl control) {
        switch (control.action()) {
            case OPEN, SUB_OPEN -> {
                Page page = serverPages.get(control.pageId());
                if (page == null) {
                    // 页面还没到且还有暂存的加密包（握手竞态）：控制指令也扣住回放，
                    // 否则 OPEN 先于 page_sync 解密到达，页面永远开不出来
                    boolean waiting;
                    synchronized (pendingPageSyncs) { waiting = !pendingPageSyncs.isEmpty(); }
                    if (waiting) {
                        synchronized (pendingControls) { pendingControls.add(control); }
                        LOGGER.debug("服务端控制 {} 页面未就绪，暂存待回放", control.pageId());
                    } else {
                        LOGGER.warn("服务端要求打开未知页面 {}", control.pageId());
                    }
                    return;
                }
                if (control.action() == PageControl.Action.OPEN) {
                    open(page, control.sessionId());
                } else {
                    openSubPage(page, control.sessionId());
                }
            }
            case CLOSE, SUB_CLOSE -> {
                String closingId = control.sessionId();
                close();
                // 服务端关闭世界页面（按会话匹配面板；多面板同屏时只关对应面板）
                closeWorld(closingId);
                com.opendreamcore.client.UiRenderer.clearRevealScope(closingId);
            }
            case MOVE -> {
                if (screen != null && control.sessionId() != null
                        && control.sessionId().equals(screen.session().sessionId())) {
                    double[] pos = parseMove(control.parentSessionId());
                    if (pos != null) {
                        screen.setOffset(pos[0], pos[1]);
                    }
                }
            }
            default -> LOGGER.debug("暂不处理页面动作 {}", control.action());
        }
    }

    /** MOVE 的 parentSessionId 字段复用为 "x,y" 坐标串。 */
    private static double[] parseMove(String data) {
        if (data == null) {
            return null;
        }
        String[] parts = data.split(",");
        if (parts.length != 2) {
            return null;
        }
        try {
            return new double[]{Double.parseDouble(parts[0].trim()), Double.parseDouble(parts[1].trim())};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 拖拽结束：记忆页面位置（下次打开恢复）。 */
    public void rememberPosition(Page page, double x, double y) {
        String id = page.id() == null ? "page" : page.id();
        rememberedPositions.put(id, new double[]{x, y});
        savePositions();
    }

    private void savePositions() {
        try {
            var file = Minecraft.getInstance().gameDirectory.toPath()
                    .resolve("OpenDreamCore").resolve("positions.json");
            var map = new java.util.LinkedHashMap<String, java.util.List<Double>>();
            rememberedPositions.forEach((id, pos) -> map.put(id, java.util.List.of(pos[0], pos[1])));
            java.nio.file.Files.writeString(file,
                    new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(map));
        } catch (Exception e) {
            LOGGER.debug("位置记忆保存失败: {}", e.toString());
        }
    }

    /** 拖拽位置记忆加载（进服时调用）。 */
    public void loadPositions() {
        try {
            var file = Minecraft.getInstance().gameDirectory.toPath()
                    .resolve("OpenDreamCore").resolve("positions.json");
            if (!java.nio.file.Files.isRegularFile(file)) {
                return;
            }
            String json = java.nio.file.Files.readString(file);
            com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            root.entrySet().forEach(entry -> {
                var arr = entry.getValue().getAsJsonArray();
                rememberedPositions.put(entry.getKey(),
                        new double[]{arr.get(0).getAsDouble(), arr.get(1).getAsDouble()});
            });
        } catch (Exception e) {
            LOGGER.debug("位置记忆加载失败: {}", e.toString());
        }
    }

    /** 服务端页面 id 清单（/odc list 用，排序输出）。 */
    public java.util.List<String> serverPageIds() {
        return serverPages.keySet().stream().sorted().toList();
    }

    public void storeServerPage(Page page) {
        serverPages.put(page.id() == null ? "page" : page.id(), page);
    }

    /** 服务端下发的页面 YAML 入库（page_sync 消息：加密包先解密，再展开 import 构建）。 */
    public void storeServerPage(com.opendreamcore.protocol.message.PageSync sync) {
        try {
            Page page = buildServerPage(sync.pageId(), sync.content(), sync.encrypted());
            if (page == null) {
                // 密钥未到（握手竞态）：先扣住，ready_ack 后回放
                if (sync.encrypted()) {
                    synchronized (pendingPageSyncs) { pendingPageSyncs.add(sync); }
                    LOGGER.debug("服务端页面 {} 密钥未到，暂存待回放", sync.pageId());
                } else {
                    LOGGER.warn("服务端页面解析失败 {}", sync.pageId());
                }
                return;
            }
            String pid = page.id() == null ? sync.pageId() : page.id();
            serverPages.put(pid, page);
            LOGGER.info("收到服务端页面 {}（{}，解析出 {} 元素）", pid,
                    sync.encrypted() ? "加密" : "明文", page.elements() == null ? -1 : page.elements().size());
            // 自动挂载：display:hud → openHud；display:world → openWorld。
            // 同 id 已挂载时跳过，避免与服务端 PAGE_CONTROL OPEN 重复触发两遍脚本/提示
            if (page.displayMode() == com.opendreamcore.page.DisplayMode.HUD) {
                Minecraft.getInstance().execute(() -> {
                    if (hudPage == null || !pid.equals(hudPage.id())) {
                        openHud(page);
                    }
                });
            }
            if (page.displayMode() == com.opendreamcore.page.DisplayMode.WORLD) {
                Minecraft.getInstance().execute(() -> {
                    if (findWorldPanel(pid) == null) {
                        openWorld(page, null);
                    }
                });
            }
        } catch (Exception e) {
            LOGGER.warn("服务端页面解析失败 {}: {}", sync.pageId(), e.toString());
        }
    }

    /** 服务端页面字节 → Page（解密 + import 展开 + schema 构建）。密钥未到返回 null（调用方决定暂存）。 */
    private Page buildServerPage(String pageId, byte[] content, boolean encrypted) {
        byte[] plain = content;
        if (encrypted) {
            byte[] key = cloud.sessionKey();
            if (key.length == 0) {
                return null;
            }
            plain = com.opendreamcore.protocol.Crypto.decrypt(key, content);
        }
        Map<String, Object> ir = new com.opendreamcore.config.YamlParser().parse(
                new String(plain, java.nio.charset.StandardCharsets.UTF_8));
        serverIr.put(pageId, ir);
        Map<String, Object> expanded = com.opendreamcore.page.PageImporter.expand(ir, this::pageIr);
        return com.opendreamcore.config.PageSchema.build(pageId, expanded);
    }

    /** 服务端 HUD 同步（hud_sync）：挂载/卸载常驻 HUD（个人/全局/静态三型）。 */
    public void handleHudSync(com.opendreamcore.protocol.message.HudSync sync) {
        if (sync.remove()) {
            closeHud();
            return;
        }
        try {
            Page page = buildServerPage(sync.pageId(), sync.content(), sync.encrypted());
            if (page == null && sync.encrypted()) {
                // 密钥未到：暂存待 ready_ack 回放
                synchronized (pendingHudSyncs) { pendingHudSyncs.add(sync); }
                LOGGER.debug("服务端 HUD {} 密钥未到，暂存待回放", sync.pageId());
                return;
            }
            if (page != null) {
                openHud(page, sync.sessionId().isEmpty() ? null : sync.sessionId());
                LOGGER.info("服务端 HUD 挂载 {}（{}）", sync.pageId(), sync.mode());
            }
        } catch (Exception e) {
            LOGGER.warn("服务端 HUD 解析失败 {}: {}", sync.pageId(), e.toString());
        }
    }

    // ---------- 背景音乐（页面 music 选项 + 服务端 MusicSync） ----------

    private volatile boolean musicConfigured;

    /** 页面音乐配置（music: 文件 或 {file, volume, loop}），打开页面时调用。 */
    public void applyMusic(Map<String, Object> options) {
        musicConfigured = false;
        if (options == null) {
            return;
        }
        Object music = options.get("music");
        if (music == null) {
            return;
        }
        String file;
        double vol = 0.8;
        boolean loop = true;
        if (music instanceof Map<?, ?> m) {
            Object rawFile = m.get("file");
            file = rawFile == null ? null : String.valueOf(rawFile);
            vol = m.get("volume") instanceof Number n ? n.doubleValue() : 0.8;
            loop = !(m.get("loop") instanceof Boolean b && !b);
        } else {
            file = String.valueOf(music);
        }
        if (file != null && !file.isBlank()) {
            musicConfigured = true;
            MusicPlayer.get().play(file, vol, loop);
        }
    }

    /** 页面音乐随页面关闭停止（仅停止本页配置的音乐）。 */
    public void stopPageMusic() {
        if (musicConfigured) {
            MusicPlayer.get().stop();
            musicConfigured = false;
        }
    }

    /** 服务端背景音乐指令（music 通道）。 */
    public void handleMusicSync(com.opendreamcore.protocol.message.MusicSync sync) {
        switch (sync.action()) {
            case PLAY -> MusicPlayer.get().play(sync.file(), sync.volume(), sync.loop());
            case STOP -> MusicPlayer.get().stop();
            case VOLUME -> MusicPlayer.get().volume(sync.volume());
        }
    }

    /** 服务端动画触发（ui_animation）：播放/停止/暂停/恢复命名动画。 */
    public void handleUiAnimation(com.opendreamcore.protocol.message.UiAnimation sync) {
        var engine = AnimationEngine.get();
        switch (sync.action()) {
            case PLAY -> {
                if (sync.names().size() > 1) {
                    engine.playSequence(sync.names().toArray(new String[0]));
                } else if (!sync.names().isEmpty()) {
                    engine.play(sync.names().get(0));
                }
            }
            case STOP -> sync.names().forEach(engine::stop);
            case PAUSE -> sync.names().forEach(engine::pause);
            case RESUME -> sync.names().forEach(engine::resume);
        }
        LOGGER.info("服务端动画触发 {}", sync.action());
    }

    /** 服务端世界页签同步（Screen.设置世界页签 / 广播世界页签）：强制切换激活页签。 */
    public void handleWorldTab(com.opendreamcore.protocol.message.WorldTabSync sync) {
        String pageId = sync.pageId();
        String tab = sync.tab();
        if (pageId == null || tab == null || tab.isEmpty()) {
            return;
        }
        // 页面打开时校验页签存在于 tabs 选项（防止错别字页签把整页元素藏掉）
        if (worldPage != null && pageId.equals(worldPage.id())) {
            RenderNode tabs = findTabsNode(worldNodes);
            if (tabs != null) {
                Map<?, ?> spec = UiRenderer.propsMap(tabs, "tabs");
                List<?> options = spec.get("options") instanceof List<?> l ? l : List.of();
                boolean valid = false;
                for (Object option : options) {
                    if (String.valueOf(option).equals(tab)) {
                        valid = true;
                        break;
                    }
                }
                if (!valid) {
                    Minecraft.getInstance().player.displayClientMessage(
                            Component.literal("§c[OpenDreamCore] §f页签不存在: " + tab + "（忽略）"), false);
                    return;
                }
            }
        }
        String prev = worldTab.get(pageId);
        worldTab.put(pageId, tab);
        WorldPanel panel = findWorldPanel(pageId);
        if (panel != null) {
            panel.tabSwitchAt = System.currentTimeMillis(); // 服务端切页签同样触发淡入过渡（按面板）
        }
        runTabChangeLifecycle(panel == null ? worldPage : panel.page, tab, prev); // 页签切换生命周期
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§a[OpenDreamCore] §f世界页签已切换: " + tab), false);
        LOGGER.info("服务端世界页签同步 {} -> {}", pageId, tab);
    }

    /** 服务端配置下发（config_push）：合并写入 odc.properties 并即时生效。 */
    public void handleConfigPush(com.opendreamcore.protocol.message.ConfigPush push) {        String text = push.properties();
        if (text == null || text.isBlank()) {
            return;
        }
        try {
            Path file = Minecraft.getInstance().gameDirectory.toPath()
                    .resolve("config").resolve("opendreamcore").resolve("odc.properties");
            Files.createDirectories(file.getParent());
            Map<String, String> merged = new java.util.LinkedHashMap<>();
            if (Files.isRegularFile(file)) {
                for (String line : Files.readAllLines(file)) {
                    int eq = line.indexOf('=');
                    if (eq > 0) {
                        merged.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
                    }
                }
            }
            for (String line : text.split("\n")) {
                if (line.isBlank()) {
                    continue;
                }
                int eq = line.indexOf('=');
                if (eq > 0) {
                    merged.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
                }
            }
            StringBuilder sb = new StringBuilder();
            merged.forEach((k, v) -> sb.append(k).append('=').append(v).append('\n'));
            Files.writeString(file, sb.toString());
            LOGGER.info("客户端配置已更新（{} 项，写入 {}）", merged.size(), file.getFileName());
        } catch (Exception e) {
            LOGGER.warn("配置写入失败: {}", e.toString());
        }
    }

    // ---------- 握手与版本检查 ----------

    /** 进服时发送 ready。 */
    public void sendReady() {
        Ready ready = new Ready(com.opendreamcore.protocol.Protocol.VERSION, clientVersion(),
                com.opendreamcore.protocol.Protocol.CAPABILITY_LOCAL_UI | com.opendreamcore.protocol.Protocol.CAPABILITY_CLOUD);
        // 先声明下行通道（minecraft:register）：必须早于 READY——服务端收到 READY 即刻下发
        // PAGE_SYNC，晚于声明会被 Paper 静默丢弃（首包竞态，1.20.1 实机实证）。
        sendRaw("minecraft:register", com.opendreamcore.protocol.Protocol.clientboundRegisterPayload());
        var buf = new com.opendreamcore.protocol.OdcByteArrayBuf();
        ready.encode(buf);
        sendRaw(com.opendreamcore.protocol.Protocol.READY, buf.toByteArray());
    }

    /**
     * 版本检查：进服回执到达时对比版本（渲染线程调用）。
     * 协议+mod 全部一致 → 绿；协议一致但 mod 不同 → 黄提醒；协议不一致 → 红，enforce 时断开。
     */
    public void handleReadyAck(ReadyAck ack) {
        serverVersion = ack.modVersion();
        serverProtocol = ack.protocolVersion();
        cloud.onReadyAck(ack.resourceKey());
        flushPendingHandshake();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        // 根据服务端能力决定是否加载本地 UI（allow-local-ui 配置控制）
        if ((ack.capabilities() & com.opendreamcore.protocol.Protocol.CAPABILITY_LOCAL_UI) != 0) {
            Path uiDir = mc.gameDirectory.toPath().resolve("OpenDreamCore").resolve("UI");
            localPages().load(uiDir);
            autoMountHud();
            LOGGER.info("服务端允许本地 UI，已加载本地页面");
        } else {
            LOGGER.info("服务端不允许本地 UI，跳过本地页面加载");
        }
        boolean protoOk = ack.protocolVersion() == com.opendreamcore.protocol.Protocol.VERSION;
        boolean modOk = ack.modVersion().equals(clientVersion());
        boolean enforce = isEnforce();
        if (protoOk && modOk) {
            // 全部一致：绿色
            mc.player.displayClientMessage(Component.literal(
                    "§a[OpenDreamCore] §fv" + CLIENT_VERSION + " §a-> §av" + ack.modVersion()
                            + " §7(协议 §av" + ack.protocolVersion() + "§7)"), false);
            return;
        }
        if (protoOk) {
            // 协议一致但 mod 版本不同（黄色提醒，不断开）
            mc.player.displayClientMessage(Component.literal(
                    "§e[OpenDreamCore] §ev" + CLIENT_VERSION + " §e-> §av" + ack.modVersion()
                            + " §7(协议 §av" + ack.protocolVersion() + "§7)"), false);
            mc.player.displayClientMessage(Component.literal(
                    "§e[OpenDreamCore] §e建议更新客户端模组至 v" + ack.modVersion()), false);
            return;
        }
        // 协议不一致：红色
        mc.player.displayClientMessage(Component.literal(
                "§c[OpenDreamCore] §cv" + CLIENT_VERSION + " §e-> §av" + ack.modVersion()
                        + " §7(协议 §cv" + com.opendreamcore.protocol.Protocol.VERSION + "§e->§av" + ack.protocolVersion() + "§7)"), false);
        if (enforce) {
            mc.player.displayClientMessage(Component.literal("§c[OpenDreamCore] §c版本不匹配，已断开连接"), false);
            mc.player.connection.getConnection()
                    .disconnect(Component.literal("OpenDreamCore 版本不匹配：请更新客户端"));
        }
    }

    /** versionCheck.enforce 配置（config/opendreamcore/odc.properties，默认 false 只提示）。 */
    private static boolean isEnforce() {
        try {
            var file = Minecraft.getInstance().gameDirectory.toPath()
                    .resolve("config").resolve("opendreamcore").resolve("odc.properties");
            if (java.nio.file.Files.isRegularFile(file)) {
                for (String line : java.nio.file.Files.readAllLines(file)) {
                    if (line.startsWith("versionCheck.enforce=")) {
                        return Boolean.parseBoolean(line.substring("versionCheck.enforce=".length()).trim());
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /** 服务端状态补丁：更新页面变量并重算布局（screen/hud/world 都支持）。 */
    public void handleStatePatch(com.opendreamcore.protocol.message.StatePatch patch) {
        // 找目标页面（screen 优先，其次 hud/world）
        Page page = null;
        if (screen != null && screen.session() != null && screen.session().sessionId().equals(patch.sessionId())) {
            page = screen.page();
        } else if (hudPage != null && hudSession != null && hudSession.sessionId().equals(patch.sessionId())) {
            page = hudPage;
        } else if (worldPage != null) {
            page = worldPage;
        }
        if (page == null) {
            return;
        }
        for (Map.Entry<String, Object> entry : patch.values().entrySet()) {
            applyPatch(page.variables(), entry.getKey(), entry.getValue());
        }
        Minecraft mc = Minecraft.getInstance();
        double w = mc.getWindow().getGuiScaledWidth();
        double h = mc.getWindow().getGuiScaledHeight();
        if (screen != null && page == screen.page()) {
            screen.refresh(layoutPage(page, w, h));
        } else if (page == hudPage) {
            hudNodes = layoutPage(page, w, h);
        } else if (page == worldPage) {
            worldNodes = layoutPage(page, 800, 600);
        }
    }

    /** 点路径赋值：a.b.c → 中间 map 自动创建；"@元素id.路径" → 改元素属性。 */
    private static void applyPatch(Map<String, Object> vars, String path, Object value) {
        if (path.startsWith("@")) {
            // 元素属性补丁：@buy_sword.text.content
            String rest = path.substring(1);
            int dot = rest.indexOf('.');
            String elementId = dot < 0 ? rest : rest.substring(0, dot);
            String propPath = dot < 0 ? "value" : rest.substring(dot + 1);
            Page page = null;
            if (INSTANCE.screen != null && INSTANCE.screen.page() != null) {
                page = INSTANCE.screen.page();
            } else if (INSTANCE.hudPage != null) {
                page = INSTANCE.hudPage;
            }
            if (page != null) {
                INSTANCE.setElementProp(page, elementId, propPath, value);
            }
            return;
        }
        String[] parts = path.split("\\.");
        if (parts.length == 1) {
            vars.put(parts[0], value);
            return;
        }
        Object cur = vars;
        for (int i = 0; i < parts.length - 1; i++) {
            Object next = cur instanceof Map<?, ?> m ? m.get(parts[i]) : null;
            if (!(next instanceof Map<?, ?>)) {
                Map<String, Object> fresh = new java.util.LinkedHashMap<>();
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) cur;
                m.put(parts[i], fresh);
                next = fresh;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) next;
            cur = m;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> last = (Map<String, Object>) cur;
        last.put(parts[parts.length - 1], value);
    }

    /** 编辑模式开关（当前打开页面）。 */
    public void toggleEdit(boolean on) {
        if (screen != null) {
            screen.setEditMode(on);
        }
    }

    /** 当前打开页面的 Page（无则 null）。 */
    public Page currentPage() {
        return screen == null ? null : screen.page();
    }

    /** 本地脚本改页面变量并刷新（单机动态演示；服务端场景用 Screen.更新状态）。 */
    public void setPageVar(String name, Object value) {
        if (screen != null) {
            screen.page().variables().put(name, value);
            refreshCurrent();
        }
    }

    /** 任意形态当前页（屏幕 → HUD → 世界聚焦面板；无返回 null）。 */
    public Page anyCurrentPage() {
        if (screen != null) {
            return screen.page();
        }
        if (hudPage != null) {
            return hudPage;
        }
        return worldPage;
    }

    /** 任意形态页面设置变量并刷新对应展示形态（Var.设置 用）。 */
    public boolean setPageVarAny(String name, Object value) {
        Page page = anyCurrentPage();
        if (page == null || name == null) {
            return false;
        }
        page.variables().put(name, value);
        if (screen != null && screen.page() == page) {
            refreshCurrent();
        } else if (hudPage == page) {
            hudNodes = layoutPage(hudPage, Minecraft.getInstance().getWindow().getGuiScaledWidth(),
                    Minecraft.getInstance().getWindow().getGuiScaledHeight());
        } else if (worldPage == page) {
            invalidateLayout(worldPage);
        }
        return true;
    }

    // ---------- 动画变量（委托 client/controller/AnimateVarService，C6 拆分） ----------

    private final com.opendreamcore.client.controller.AnimateVarService animateVarService =
            new com.opendreamcore.client.controller.AnimateVarService(new com.opendreamcore.client.controller.AnimateVarService.Host() {
                @Override public Page anyCurrentPage() { return ClientController.this.anyCurrentPage(); }
                @Override public Page pageById(String pageId) { return ClientController.this.pageById(pageId); }
                @Override public void setPageVarAny(String name, Object value) { ClientController.this.setPageVarAny(name, value); }
                @Override public void refreshScreenIfPage(Page page) {
                    if (screen != null && screen.page() == page) refreshCurrent();
                }
            });

    /** 设置动画变量（立即写入页面变量并清除补间；Var.设置动画值）。 */
    public boolean setAnimateValue(String name, Object value) {
        return animateVarService.setAnimateValue(name, value);
    }

    /** 动画到：durationMs 内从当前值缓动到目标值。 */
    public boolean animateValueTo(String name, double to, long durationMs,
                                  com.opendreamcore.script.Easing.Type easing) {
        return animateVarService.animateValueTo(name, to, durationMs, easing);
    }

    /** 动画变量当前值（活动补间取插值；否则读页面变量）。 */
    public double getAnimateValue(String name) {
        return animateVarService.getAnimateValue(name);
    }

    private void tickAnimateValues() {
        animateVarService.tick();
    }
    // ---------- 组件方法（动态改元素属性） ----------

    /** 按路径设置元素属性（"text.content"/"button.label"/"color" 等），返回是否找到元素。 */
    public boolean setElementProp(Page page, String elementId, String path, Object value) {
        com.opendreamcore.page.Element element = findElement(page, elementId);
        if (element == null) {
            return false;
        }
        String[] parts = path.split("\\.");
        if (parts.length == 0) {
            return false;
        }
        Map<String, Object> cur = element.props();
        for (int i = 0; i < parts.length - 1; i++) {
            Object next = cur.get(parts[i]);
            if (!(next instanceof Map<?, ?>)) {
                Map<String, Object> fresh = new java.util.LinkedHashMap<>();
                cur.put(parts[i], fresh);
                next = fresh;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) next;
            cur = m;
        }
        cur.put(parts[parts.length - 1], value);
        return true;
    }

    /** 读取元素属性路径的值（无返回 null）。 */
    public Object getElementProp(Page page, String elementId, String path) {
        com.opendreamcore.page.Element element = findElement(page, elementId);
        if (element == null) {
            return null;
        }
        Object cur = element.props();
        for (String part : path.split("\\.")) {
            if (!(cur instanceof Map<?, ?> m)) {
                return null;
            }
            cur = m.get(part);
        }
        return cur;
    }

    // ---------- 组件动态操作（显隐/存在/悬停/创建） ----------

    /** 运行时隐藏元素（会话级；立即刷新布局）。 */
    public boolean hideElement(String elementId) {
        Page page = currentPage();
        if (page == null || findElement(page, elementId) == null) {
            return false;
        }
        String pageId = page.id() == null ? "page" : page.id();
        elementEdits.markHidden(pageId, elementId);
        refreshCurrent();
        return true;
    }

    /** 运行时显示元素（取消隐藏标记）。 */
    public boolean showElement(String elementId) {
        Page page = currentPage();
        if (page == null) {
            return false;
        }
        String pageId = page.id() == null ? "page" : page.id();
        elementEdits.unmarkHidden(pageId, elementId);
        refreshCurrent();
        return true;
    }

    /** 元素是否存在（原始页面 + 会话复制元素）。 */
    public boolean elementExists(String elementId) {
        Page page = currentPage();
        if (page == null) {
            return false;
        }
        if (findElement(page, elementId) != null) {
            return true;
        }
        String pageId = page.id() == null ? "page" : page.id();
        for (Element copy : elementEdits.copies(pageId)) {
            if (findElement(copy, elementId) != null) {
                return true;
            }
        }
        return false;
    }

    /** 当前悬停元素 id（无则 null）。 */
    public String hoveredElement() {
        return screen == null ? null : screen.hoverId();
    }

    /**
     * 创建元素并挂到当前页面末尾（会话级；布局立即生效）。
     * 参数：id、type、x、y、width、height；type 支持 text/button/image/panel 等。
     */
    public boolean createElement(String elementId, String type, double x, double y,
                                 double width, double height) {
        Page page = currentPage();
        if (page == null || elementId == null || elementId.isBlank() || type == null || type.isBlank()) {
            return false;
        }
        if (findElement(page, elementId) != null || elementExists(elementId)) {
            return false; // id 冲突
        }
        com.opendreamcore.page.Layout layout = new com.opendreamcore.page.Layout(
                String.valueOf(x), String.valueOf(y),
                Double.isNaN(width) ? null : String.valueOf(width),
                Double.isNaN(height) ? null : String.valueOf(height));
        java.util.Map<String, Object> props = new java.util.LinkedHashMap<>();
        props.put("created", true);
        Element element = new Element(elementId, type, layout, props,
                null, null, java.util.Map.of(), java.util.List.of(), null);
        String pageId = page.id() == null ? "page" : page.id();
        elementEdits.addCopy(pageId, element);
        refreshCurrent();
        return true;
    }

    /** 递归找元素（按 id）。 */
    public static com.opendreamcore.page.Element findElement(Page page, String elementId) {
        for (com.opendreamcore.page.Element element : page.elements()) {
            com.opendreamcore.page.Element found = findElement(element, elementId);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** 元素查找：按 id 子串过滤（空查询 = 全部；`type:xxx` 前缀 = 按类型过滤），返回 [id, type, 位置]；最多 limit 条。 */
    public java.util.List<String[]> findWorldElements(String query) {
        java.util.List<String[]> out = new java.util.ArrayList<>();
        if (worldPage == null || worldNodes == null) {
            return out;
        }
        String q = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        String typeFilter = null;
        String idFilter = q;
        if (q.startsWith("type:")) {
            typeFilter = q.substring(5).trim();
            idFilter = "";
        }
        collectFindMatches(worldNodes, idFilter, typeFilter, worldPage.variables(), out, 12, 0);
        return out;
    }

    private static void collectFindMatches(List<RenderNode> nodes, String idFilter, String typeFilter,
                                           java.util.Map<String, Object> vars,
                                           java.util.List<String[]> out, int limit, int depth) {
        if (nodes == null || out.size() >= limit) {
            return;
        }
        for (RenderNode node : nodes) {
            if (out.size() >= limit) {
                return;
            }
            boolean matchId = idFilter.isEmpty()
                    || node.id().toLowerCase(java.util.Locale.ROOT).contains(idFilter);
            boolean matchType = typeFilter == null
                    || String.valueOf(node.props().get("type")).toLowerCase(java.util.Locale.ROOT)
                    .contains(typeFilter);
            if (matchId && matchType) {
                Object raw = node.props().get("hologram");
                Map<?, ?> holo = raw instanceof Map<?, ?> h ? h : Map.of();
                String pos = String.format(java.util.Locale.ROOT, "%.2f, %.2f, %.2f",
                        WorldHologram.holoNum(holo, "x", 0, vars),
                        WorldHologram.holoNum(holo, "y", 0, vars),
                        WorldHologram.holoNum(holo, "z", 0, vars));
                out.add(new String[]{node.id(), String.valueOf(node.props().get("type")), pos,
                        String.valueOf(depth)});
            }
            collectFindMatches(node.children(), idFilter, typeFilter, vars, out, limit, depth + 1);
        }
    }

    /** 定位选中元素（查找屏点击/Enter；清多选，世界选中框高亮）。 */
    public void selectWorldElement(String elementId) {
        if (worldPage == null || elementId == null || findElement(worldPage, elementId) == null) {
            return;
        }
        // 定位即显示（隐藏元素经查找屏选中后恢复可见，便于操作）
        String pid = worldPage.id() == null ? "world" : worldPage.id();
        String k = wkey(pid, elementId);
        Boolean[] st = WorldEditor.get().worldElementStates.get(k);
        if (st != null && st[0] != null && !st[0]) {
            st[0] = true;
            WorldEditor.get().worldElementStates.put(k, st);
        }
        worldEditMulti.clear();
        WorldEditor.get().worldEditSelected = elementId;
        WorldEditor.get().worldPanelSelections.put(pid, elementId); // 记录面板选择（跨面板对齐参考）
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§a[OpenDreamCore] §f已定位 " + elementId), false);
    }

    /** 显示全部运行时隐藏元素（会话级；J 键 / 恢复入口）。 */
    public void showAllWorldElements() {
        if (worldPage == null || WorldEditor.get().worldElementStates.isEmpty()) {
            return;
        }
        String pid = worldPage.id() == null ? "world" : worldPage.id();
        String prefix = pid + "/";
        int n = 0;
        java.util.List<String> gone = new java.util.ArrayList<>();
        for (java.util.Map.Entry<String, Boolean[]> e : WorldEditor.get().worldElementStates.entrySet()) {
            if (!e.getKey().startsWith(prefix)) {
                continue;
            }
            Boolean[] st = e.getValue();
            if (st[0] != null && !st[0]) {
                st[0] = true;
                WorldEditor.get().worldElementStates.put(e.getKey(), st);
                n++;
            }
            if (e.getKey().equals(prefix)) {
                continue;
            }
        }
        if (n > 0) {
            invalidateLayout(worldPage);
            worldNodes = layoutPage(worldPage, 800, 600);
        }
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§7[OpenDreamCore] §f已恢复 " + n + " 个隐藏元素"), false);
    }

    /** 查找定位 + 相机对准：选中元素并把玩家视线转向元素中心（不移动玩家）。 */
    public void focusWorldElement(String elementId) {
        if (worldPage == null || elementId == null || findElement(worldPage, elementId) == null) {
            return;
        }
        selectWorldElement(elementId);
        var mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        var node = findWorldNode(elementId);
        if (node == null) {
            return;
        }
        net.minecraft.world.phys.Vec3 center = worldElementCenter(node, mc.gameRenderer.getMainCamera());
        mc.player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, center);
        mc.player.yRotO = mc.player.getYRot();
        mc.player.xRotO = mc.player.getXRot();
    }

    /** 单元素重命名（右键「重命名…」）：newName 直接作新 id（校验合法/唯一）；可撤消。 */
    public boolean renameWorldElementToOne(String elementId, String newName) {
        if (worldPage == null || elementId == null) {
            return false;
        }
        String name = newName == null ? "" : newName.trim();
        if (name.isEmpty() || !name.matches("[A-Za-z0-9_\\-]+")) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§c[OpenDreamCore] §f名称需为字母/数字/下划线/短横线"), false);
            return false;
        }
        if (name.equals(elementId)) {
            return false;
        }
        if (findElement(worldPage, name) != null) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§c[OpenDreamCore] §f已存在同名元素 " + name), false);
            return false;
        }
        WorldEditor.get().pushWorldUndo("重命名 " + elementId, null, List.of(elementId));
        renameWorldElementInner(elementId, name);
        invalidateLayout(worldPage);
        worldNodes = layoutPage(worldPage, 800, 600);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§a[OpenDreamCore] §f已重命名 " + elementId + " → " + name
                        + "（Ctrl+Z 撤消）"), false);
        return true;
    }

    /** 批量重命名（按序号 prefix_1、prefix_2…；id 唯一化；整树重建 + 编辑状态迁移；可撤消）。 */
    public void renameWorldElements(List<String> oldIds, String prefix, int start) {
        if (worldPage == null || oldIds == null || oldIds.isEmpty()) {
            return;
        }
        String pre = prefix == null ? "" : prefix.trim();
        if (pre.isEmpty() || !pre.matches("[A-Za-z0-9_\\-]+")) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§c[OpenDreamCore] §f前缀需为字母/数字/下划线/短横线"), false);
            return;
        }
        java.util.List<String> oldList = new java.util.ArrayList<>();
        for (String id : oldIds) {
            if (!oldList.contains(id) && findElement(worldPage, id) != null) {
                oldList.add(id);
            }
        }
        if (oldList.isEmpty()) {
            return;
        }
        oldList.sort(String::compareTo);
        // 生成新 id（跳过已存在与本次冲突）
        java.util.Set<String> existing = new java.util.HashSet<>();
        WorldEditor.get().collectWorldIds(worldNodes, existing);
        java.util.List<String> newIds = new java.util.ArrayList<>();
        int idx = start;
        for (String ignored : oldList) {
            String cand;
            do {
                cand = pre + idx++;
            } while (existing.contains(cand) || oldList.contains(cand) || newIds.contains(cand));
            newIds.add(cand);
        }
        // 快照：旧元素暂存（撤消恢复用）+ 一步撤消覆盖全部旧/新 id
        java.util.List<String> undoIds = new java.util.ArrayList<>(oldList);
        undoIds.addAll(newIds);
        WorldEditor.get().pushWorldUndo("批量重命名", null, undoIds);
        for (String id : oldList) {
            var el = findElement(worldPage, id);
            if (el != null) {
                WorldEditor.get().worldEditUndoElements.put(id, el);
            }
        }
        for (int i = 0; i < oldList.size(); i++) {
            renameWorldElementInner(oldList.get(i), newIds.get(i));
        }
        WorldEditor.get().worldEditSelected = newIds.get(newIds.size() - 1);
        worldEditMulti.clear();
        worldEditMulti.addAll(newIds);
        invalidateLayout(worldPage);
        worldNodes = layoutPage(worldPage, 800, 600);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§a[OpenDreamCore] §f已批量重命名 " + oldList.size()
                        + " 个元素（保存后写回页面文件）"), false);
    }

    /** 单元素重命名内核（无快照，调用方负责 undo 栈与旧元素暂存）。 */
    private void renameWorldElementInner(String oldId, String newId) {
        if (findElement(worldPage, oldId) == null) {
            return;
        }
        java.util.List<Element> newRoots = rebuildPageForest(worldPage.elements(), oldId, newId);
        worldPage.elements().clear();
        worldPage.elements().addAll(newRoots);
        migrateWorldEditKeys(oldId, newId);
    }

    /** 重建子列表：子元素 parent==oldId → newId，深层引用递归修复。 */
    private static java.util.List<Element> rebuildChildrenRefs(java.util.List<Element> children,
                                                               String oldId, String newId) {
        java.util.List<Element> out = new java.util.ArrayList<>();
        for (Element child : children) {
            out.add(rebuildElementRefs(child, oldId, newId));
        }
        return out;
    }

    /** 重建元素引用：命中目标 id → 换新 id（parent 重映射、子引用递归修复）；否则 parent/子引用逐层修复。 */
    private static Element rebuildElementRefs(Element e, String oldId, String newId) {
        if (e.id().equals(oldId)) {
            return new Element(newId, e.type(), e.layout(), e.props(), e.visibleWhen(), e.enabledWhen(),
                    e.actions(), rebuildChildrenRefs(e.children(), oldId, newId),
                    oldId.equals(e.parent()) ? newId : e.parent());
        }
        java.util.List<Element> kids = e.children();
        java.util.List<Element> newKids = null;
        for (int i = 0; i < kids.size(); i++) {
            Element rebuilt = rebuildElementRefs(kids.get(i), oldId, newId);
            if (rebuilt != kids.get(i)) {
                if (newKids == null) {
                    newKids = new java.util.ArrayList<>(kids);
                }
                newKids.set(i, rebuilt);
            }
        }
        boolean parentChanged = oldId.equals(e.parent());
        if (!parentChanged && newKids == null) {
            return e;
        }
        return new Element(e.id(), e.type(), e.layout(), e.props(), e.visibleWhen(), e.enabledWhen(),
                e.actions(), newKids == null ? e.children() : newKids,
                parentChanged ? newId : e.parent());
    }

    /** 重建页面根列表：整树引用修复（含目标替换）。 */
    private static java.util.List<Element> rebuildPageForest(java.util.List<Element> roots,
                                                             String oldId, String newId) {
        java.util.List<Element> out = new java.util.ArrayList<>();
        for (Element e : roots) {
            out.add(rebuildElementRefs(e, oldId, newId));
        }
        return out;
    }

    /** 编辑状态键迁移（oldId → newId）：选中/多选/预览/高亮/dirty/props/删除/暂存/原始快照。 */
    private void migrateWorldEditKeys(String oldId, String newId) {
        if (WorldEditor.get().worldEditSelected != null && WorldEditor.get().worldEditSelected.equals(oldId)) {
            WorldEditor.get().worldEditSelected = newId;
        }
        if (worldEditMulti.remove(oldId)) {
            worldEditMulti.add(newId);
        }
        if (worldMarqueePreview.remove(oldId)) {
            worldMarqueePreview.add(newId);
        }
        if (worldEditHighlight.remove(oldId)) {
            worldEditHighlight.add(newId);
        }
        if (WorldEditor.get().worldEditDirty.containsKey(oldId)) {
            WorldEditor.get().worldEditDirty.put(newId, WorldEditor.get().worldEditDirty.remove(oldId));
        }
        if (WorldEditor.get().worldEditProps.containsKey(oldId)) {
            WorldEditor.get().worldEditProps.put(newId, WorldEditor.get().worldEditProps.remove(oldId));
        }
        if (WorldEditor.get().worldEditDeletes.remove(oldId)) {
            WorldEditor.get().worldEditDeletes.add(newId);
        }
        if (WorldEditor.get().worldEditOriginal.containsKey(oldId)) {
            WorldEditor.get().worldEditOriginal.put(newId, WorldEditor.get().worldEditOriginal.remove(oldId));
        }
        if (WorldEditor.get().worldEditOriginalProps.containsKey(oldId)) {
            WorldEditor.get().worldEditOriginalProps.put(newId, WorldEditor.get().worldEditOriginalProps.remove(oldId));
        }
        if (worldZScrubBase.containsKey(oldId)) {
            worldZScrubBase.put(newId, worldZScrubBase.remove(oldId));
        }
        if (WorldEditor.get().worldEditDeletedElements.containsKey(oldId)) {
            WorldEditor.get().worldEditDeletedElements.put(newId, WorldEditor.get().worldEditDeletedElements.remove(oldId));
        }
    }

    /** 单元素一键居中：hologram.x/y = 0（面板锚点即中心），保持 z；可撤消。 */
    public void centerWorldElement(String elementId) {
        if (elementId != null) {
            centerWorldElements(List.of(elementId));
        }
    }

    /** 批量居中：多选整组 x/y = 0 对齐锚点（各保持 z；一步撤消）。 */
    public void centerWorldElements(List<String> elementIds) {
        if (worldPage == null || elementIds == null || elementIds.isEmpty()) {
            return;
        }
        java.util.List<String> alive = new java.util.ArrayList<>();
        for (String id : elementIds) {
            if (findElement(worldPage, id) != null) {
                alive.add(id);
            }
        }
        if (alive.isEmpty()) {
            return;
        }
        WorldEditor.get().pushWorldUndo("居中", null, alive);
        var vars = worldPage.variables();
        for (String id : alive) {
            var element = findElement(worldPage, id);
            if (element == null) {
                continue;
            }
            Object raw = element.props().get("hologram");
            Map<Object, Object> holo = new java.util.LinkedHashMap<>(
                    raw instanceof Map<?, ?> h ? (Map<?, ?>) h : Map.of());
            double z = WorldHologram.holoNum(holo, "z", 0, vars);
            holo.put("x", 0.0);
            holo.put("y", 0.0);
            element.props().put("hologram", holo);
            WorldEditor.get().worldEditDirty.put(id, new double[]{0, 0, z});
            refreshCreateBlock(id);
        }
        invalidateLayout(worldPage);
        worldNodes = layoutPage(worldPage, 800, 600);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§a[OpenDreamCore] §f已居中 " + alive.size()
                        + " 个元素（保存后写回页面文件）"), false);
    }

    private static com.opendreamcore.page.Element findElement(com.opendreamcore.page.Element element, String elementId) {
        if (element.id().equals(elementId)) {
            return element;
        }
        for (com.opendreamcore.page.Element child : element.children()) {
            com.opendreamcore.page.Element found = findElement(child, elementId);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** 保存元素位置编辑（写 edits.json；服务端页面同时回传布局）。 */
    public void saveEdits() {
        elementEdits.save();
        if (screen != null) {
            // 服务端页面：把布局补丁发回服务端（需要租约）
            String pageId = screen.page().id() == null ? "page" : screen.page().id();
            if (serverPages.containsKey(pageId)) {
                if (!leaseHeld) {
                    Minecraft.getInstance().player.displayClientMessage(
                            Component.literal("§c[OpenDreamCore] §f没有编辑租约，先执行 /odc edit lease " + pageId), false);
                    return;
                }
                sendLayout(pageId);
            }
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§a[OpenDreamCore] §f元素位置已保存"), false);
        }
    }

    /** 请求服务端页面编辑租约（/odc edit lease）。 */
    public void requestLease(String pageId) {
        var buf = new com.opendreamcore.protocol.OdcByteArrayBuf();
        new com.opendreamcore.protocol.message.EditorLease(
                com.opendreamcore.protocol.message.EditorLease.Action.REQUEST, pageId, null).encode(buf);
        sendRaw(com.opendreamcore.protocol.Protocol.EDITOR_LEASE, buf.toByteArray());
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§e[OpenDreamCore] §f已请求编辑租约: " + pageId), false);
    }

    /** 释放编辑租约（/odc edit release）。 */
    public void releaseLease(String pageId) {
        var buf = new com.opendreamcore.protocol.OdcByteArrayBuf();
        new com.opendreamcore.protocol.message.EditorLease(
                com.opendreamcore.protocol.message.EditorLease.Action.RELEASE, pageId, null).encode(buf);
        sendRaw(com.opendreamcore.protocol.Protocol.EDITOR_LEASE, buf.toByteArray());
        leaseHeld = false;
        if (java.util.Objects.equals(leasePageId, pageId)) {
            leasePageId = null;
        }
        WorldEditor.get().worldEditDirty.clear();
        WorldEditor.get().worldEditProps.clear();
        WorldEditor.get().worldEditDeletes.clear();
        WorldEditor.get().worldEditDeletedElements.clear();
        updateWorldEditMode();
    }

    /** 服务端租约回执：GRANT 后可保存，DENY 提示持有者。 */
    public void handleLease(com.opendreamcore.protocol.message.EditorLease lease) {
        if (lease.action() == com.opendreamcore.protocol.message.EditorLease.Action.GRANT) {
            leaseHeld = true;
            leasePageId = lease.pageId();
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§a[OpenDreamCore] §f已获得编辑权: " + lease.pageId()), false);
        } else if (lease.action() == com.opendreamcore.protocol.message.EditorLease.Action.DENY) {
            leaseHeld = false;
            leasePageId = null;
            WorldEditor.get().worldEditDirty.clear();
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§c[OpenDreamCore] §f编辑被拒（" + lease.holder() + " 正在编辑 " + lease.pageId() + "）"), false);
        }
        updateWorldEditMode();
    }

    /** 发送页面布局补丁（C→S，服务端页面编辑保存）。 */
    public void sendLayout(String pageId) {
        Map<String, double[]> edits = elementEdits.forPage(pageId);
        if (edits == null || edits.isEmpty()) {
            return;
        }
        List<com.opendreamcore.protocol.message.PageLayout.Entry> entries = new java.util.ArrayList<>();
        edits.forEach((elementId, pos) -> entries.add(
                new com.opendreamcore.protocol.message.PageLayout.Entry(elementId, pos[0], pos[1])));
        var buf = new com.opendreamcore.protocol.OdcByteArrayBuf();
        new com.opendreamcore.protocol.message.PageLayout(pageId, entries).encode(buf);
        sendRaw(com.opendreamcore.protocol.Protocol.PAGE_LAYOUT, buf.toByteArray());
    }

    /** 服务端布局广播/附带下发：合并进编辑记忆并刷新。 */
    public void handlePageLayout(com.opendreamcore.protocol.message.PageLayout layout) {
        for (com.opendreamcore.protocol.message.PageLayout.Entry entry : layout.entries()) {
            elementEdits.set(layout.pageId(), entry.elementId(), entry.x(), entry.y());
        }
        elementEdits.save();
        if (screen != null) {
            String pageId = screen.page().id() == null ? "page" : screen.page().id();
            if (pageId.equals(layout.pageId())) {
                refreshCurrent();
            }
        }
        LOGGER.info("页面布局已应用 {} ({} 项)", layout.pageId(), layout.entries().size());
    }

    public String serverVersion() {
        return serverVersion;
    }

    /** 进服时长（秒，脚本 Player.在线时长 用）。 */
    public double onlineSeconds() {
        return loginTime == 0 ? 0 : (System.currentTimeMillis() - loginTime) / 1000.0;
    }

    public void markLogin() {
        loginTime = System.currentTimeMillis();
    }
}
