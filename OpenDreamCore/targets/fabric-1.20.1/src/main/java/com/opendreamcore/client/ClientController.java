package com.opendreamcore.client;

import com.mojang.logging.LogUtils;
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
    private static final String CLIENT_VERSION = "0.1.0";

    private volatile UiSender sender;

    private final LocalPageManager localPages = new LocalPageManager();
    private final Map<String, Page> serverPages = new ConcurrentHashMap<>();
    private final CloudSyncClient cloud = new CloudSyncClient();
    private final TooltipStore tooltips = new TooltipStore();
    private final ElementEditStore elementEdits = new ElementEditStore();
    private final Map<String, Object> globals = new ConcurrentHashMap<>();
    private volatile boolean leaseHeld;

    private UiSession session;
    private OdcScreen screen;
    private String serverVersion;
    private int serverProtocol;

    // HUD 常驻页面（display: hud 或 match: hud）
    private Page hudPage;
    private List<RenderNode> hudNodes;
    private UiSession hudSession;

    // 世界全息页面（display: world 或 match: world）
    private Page worldPage;
    private List<RenderNode> worldNodes;

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
    }

    /** 发送协议消息（target 网络层转发到对应通道）。 */
    public void sendRaw(String channelPath, byte[] bytes) {
        UiSender s = sender;
        if (s != null) {
            s.send(channelPath, bytes);
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
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) {
            return;
        }
        double w = mc.getWindow().getGuiScaledWidth();
        double h = mc.getWindow().getGuiScaledHeight();
        String id = page.id() == null ? "page" : page.id();
        UiSession newSession = serverSessionId == null
                ? new UiSession(id) : new UiSession(id, serverSessionId);
        List<RenderNode> nodes = LayoutEngine.layout(page, w, h, elementEdits.forPage(id));
        OdcScreen newScreen = new OdcScreen(page, nodes, newSession);
        // 拖拽位置记忆恢复
        double[] pos = rememberedPositions.get(id);
        if (pos != null) {
            newScreen.setOffset(pos[0], pos[1]);
        }
        this.session = newSession;
        this.screen = newScreen;
        screenStack.push(newScreen);
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
        OdcScreen sub = new OdcScreen(page, LayoutEngine.layout(page, w, h), newSession);
        this.session = newSession;
        this.screen = sub;
        screenStack.push(sub);
        mc.setScreen(sub);
        runLifecycle(page, "open");
    }

    /** 关闭当前页面（发 close 生命周期脚本）。 */
    public void close() {
        if (screen != null) {
            runLifecycle(screen.page(), "close");
        }
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
        if (page == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        double w = mc.getWindow().getGuiScaledWidth();
        double h = mc.getWindow().getGuiScaledHeight();
        hudPage = page;
        hudNodes = LayoutEngine.layout(page, w, h);
        hudSession = new UiSession(page.id() == null ? "hud" : page.id());
        runLifecycle(page, "open");
        LOGGER.info("HUD 页面已挂载 {}", page.id());
    }

    public void closeHud() {
        if (hudPage != null) {
            runLifecycle(hudPage, "close");
        }
        hudPage = null;
        hudNodes = null;
        hudSession = null;
    }

    public boolean isHudOpen() {
        return hudPage != null;
    }

    /** HUD 渲染回调（RenderGuiEvent 里调用）。 */
    public void renderHud(net.minecraft.client.gui.GuiGraphics g) {
        if (hudNodes == null || hudPage == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        double scale = mc.getWindow().getGuiScaledWidth() / (double) mc.getWindow().getScreenWidth();
        int mouseX = (int) (mc.mouseHandler.xpos() * scale);
        int mouseY = (int) (mc.mouseHandler.ypos() * scale);
        UiRenderer.draw(g, mc.font, hudNodes, mouseX, mouseY, null, hudPage.variables());
    }

    /** 进服时按 match 自动挂载 HUD 页面（本地仓库）。 */
    public void autoMountHud() {
        Page hud = localPages.match("hud", null, com.opendreamcore.page.DisplayMode.HUD);
        if (hud != null) {
            openHud(hud);
        }
        Page world = localPages.match("world", null, com.opendreamcore.page.DisplayMode.WORLD);
        if (world != null) {
            openWorld(world);
        }
    }

    // ---------- 世界全息 ----------

    /** 打开世界全息页面（覆盖旧的）。 */
    public void openWorld(Page page) {
        if (page == null) {
            return;
        }
        worldPage = page;
        worldNodes = LayoutEngine.layout(page, 800, 600);
        runLifecycle(page, "open");
        LOGGER.info("世界全息已挂载 {}", page.id());
    }

    public void closeWorld() {
        if (worldPage != null) {
            runLifecycle(worldPage, "close");
        }
        worldPage = null;
        worldNodes = null;
    }

    public boolean isWorldOpen() {
        return worldPage != null;
    }

    /** 世界渲染回调（RenderLevelStageEvent 里调用）。 */
    public void renderWorld(net.minecraft.client.Camera camera, float partialTick) {
        if (worldNodes != null && worldPage != null) {
            WorldHologram.render(worldNodes, worldPage.options(), camera, partialTick);
        }
    }

    public LocalPageManager localPages() {
        return localPages;
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

    /** 编辑模式改位置后重建当前页面布局。 */
    public void refreshCurrent() {
        if (screen == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        double w = mc.getWindow().getGuiScaledWidth();
        double h = mc.getWindow().getGuiScaledHeight();
        String id = screen.page().id() == null ? "page" : screen.page().id();
        screen.refresh(LayoutEngine.layout(screen.page(), w, h, elementEdits.forPage(id)));
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
            screen.refresh(LayoutEngine.layout(screen.page(), w, h));
        }
        if (hudPage != null) {
            hudNodes = LayoutEngine.layout(hudPage, w, h);
        }
        if (worldPage != null) {
            worldNodes = LayoutEngine.layout(worldPage, 800, 600);
        }
        LOGGER.info("服务端全局状态已更新 {} 项", state.values().size());
    }

    /** 进服后拉取服务端 tooltip 注册表。 */
    public void requestTooltips() {
        var buf = new com.opendreamcore.protocol.OdcByteArrayBuf();
        new com.opendreamcore.protocol.message.TooltipResync().encode(buf);
        sendRaw(com.opendreamcore.protocol.Protocol.TOOLTIP_RESYNC, buf.toByteArray());
    }

    private void runLifecycle(Page page, String name) {
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

    private static int count(List<RenderNode> nodes) {
        int n = nodes.size();
        for (RenderNode node : nodes) {
            n += count(node.children());
        }
        return n;
    }

    // ---------- 事件发送 ----------

    public void sendEvent(UiEvent event) {
        if (event == null) {
            return;
        }
        var buf = new com.opendreamcore.protocol.OdcByteArrayBuf();
        event.encode(buf);
        sendRaw(com.opendreamcore.protocol.Protocol.UI_EVENT, buf.toByteArray());
    }

    // ---------- 服务端下发（page_control） ----------

    public void handlePageControl(PageControl control) {
        switch (control.action()) {
            case OPEN -> {
                Page page = serverPages.get(control.pageId());
                if (page == null) {
                    LOGGER.warn("服务端要求打开未知页面 {}", control.pageId());
                    return;
                }
                open(page, control.sessionId());
            }
            case SUB_OPEN -> {
                Page page = serverPages.get(control.pageId());
                if (page == null) {
                    LOGGER.warn("服务端要求打开未知子页 {}", control.pageId());
                    return;
                }
                openSubPage(page, control.sessionId());
            }
            case CLOSE, SUB_CLOSE -> close();
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

    public void storeServerPage(Page page) {
        serverPages.put(page.id() == null ? "page" : page.id(), page);
    }

    /** 服务端下发的页面 YAML 入库（page_sync 消息）。 */
    public void storeServerPage(com.opendreamcore.protocol.message.PageSync sync) {
        try {
            Map<String, Object> ir = new com.opendreamcore.config.YamlParser().parse(sync.yaml());
            Page page = com.opendreamcore.config.PageSchema.build(sync.pageId(), ir);
            serverPages.put(page.id() == null ? sync.pageId() : page.id(), page);
            LOGGER.info("收到服务端页面 {}", sync.pageId());
        } catch (Exception e) {
            LOGGER.warn("服务端页面解析失败 {}: {}", sync.pageId(), e.toString());
        }
    }

    // ---------- 握手与版本检查 ----------

    /** 进服时发送 ready。 */
    public void sendReady() {
        Ready ready = new Ready(com.opendreamcore.protocol.Protocol.VERSION, CLIENT_VERSION,
                com.opendreamcore.protocol.Protocol.CAPABILITY_LOCAL_UI | com.opendreamcore.protocol.Protocol.CAPABILITY_CLOUD);
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
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        boolean protoOk = ack.protocolVersion() == com.opendreamcore.protocol.Protocol.VERSION;
        boolean modOk = ack.modVersion().equals(CLIENT_VERSION);
        boolean enforce = isEnforce();
        if (protoOk && modOk) {
            mc.player.displayClientMessage(Component.literal(
                    "§a[OpenDreamCore] §f版本匹配 §a" + CLIENT_VERSION
                            + " §7(协议 §av" + ack.protocolVersion() + "§7)"), false);
            return;
        }
        if (protoOk) {
            mc.player.displayClientMessage(Component.literal(
                    "§e[OpenDreamCore] §e版本不同 §c" + CLIENT_VERSION + " §e-> §a" + ack.modVersion()
                            + " §7(协议 §av" + ack.protocolVersion() + "§7)"), false);
            mc.player.displayClientMessage(Component.literal(
                    "§e[OpenDreamCore] §e建议更新客户端模组至 v" + ack.modVersion()), false);
            return;
        }
        mc.player.displayClientMessage(Component.literal(
                "§c[OpenDreamCore] §c" + CLIENT_VERSION + " §e-> §a" + ack.modVersion()
                        + " §7(协议 §c" + com.opendreamcore.protocol.Protocol.VERSION + "§e->§a" + ack.protocolVersion() + "§7)"), false);
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
            screen.refresh(LayoutEngine.layout(page, w, h));
        } else if (page == hudPage) {
            hudNodes = LayoutEngine.layout(page, w, h);
        } else if (page == worldPage) {
            worldNodes = LayoutEngine.layout(page, 800, 600);
        }
    }

    /** 点路径赋值：a.b.c → 中间 map 自动创建。 */
    private static void applyPatch(Map<String, Object> vars, String path, Object value) {
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
    }

    /** 服务端租约回执：GRANT 后可保存，DENY 提示持有者。 */
    public void handleLease(com.opendreamcore.protocol.message.EditorLease lease) {
        if (lease.action() == com.opendreamcore.protocol.message.EditorLease.Action.GRANT) {
            leaseHeld = true;
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§a[OpenDreamCore] §f已获得编辑权: " + lease.pageId()), false);
        } else if (lease.action() == com.opendreamcore.protocol.message.EditorLease.Action.DENY) {
            leaseHeld = false;
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§c[OpenDreamCore] §f编辑被拒（" + lease.holder() + " 正在编辑 " + lease.pageId() + "）"), false);
        }
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
