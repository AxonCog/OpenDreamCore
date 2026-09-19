package com.opendreamcore.client;

import com.opendreamcore.protocol.OdcByteArrayBuf;
import com.opendreamcore.protocol.message.GlobalState;
import com.opendreamcore.protocol.message.HudSync;
import com.opendreamcore.protocol.message.PageControl;
import com.opendreamcore.protocol.message.PageSync;
import com.opendreamcore.protocol.message.ReadyAck;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 服务端消息分发表：路径 → 解码 → 落地动作。
 * 与现代版 ClientController 的 handle* 一一同名，方便逐个搬实现体；
 * 这里只管解码与页面入库，挂载/渲染由各版本渲染层接手。
 */
public final class MessageDispatcher {
    private final Logger logger;
    private final Map<String, PageSync> pages = new LinkedHashMap<>();
    private String serverVersion = "";
    private int serverProtocol = -1;
    /** 服务端全局变量（global_state 推来的 {{global.xxx}} 取值源）。 */
    private static final Map<String, Object> GLOBALS = new java.util.concurrent.ConcurrentHashMap<>();

    /** 渲染层取全局变量用的口子；查不到返回 null。 */
    public static Object globalValue(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        return GLOBALS.get(name);
    }

    /** 全局变量只读快照（表达式环境平铺用）。 */
    public static Map<String, Object> globalsView() {
        return java.util.Collections.unmodifiableMap(GLOBALS);
    }

    public MessageDispatcher(Logger logger) {
        this.logger = logger;
    }

    /** 入口：RawBytes 收到的服务端消息从这里走。返回 true 表示已识别。 */
    public boolean dispatch(String path, byte[] data) {
        switch (path) {
            case "ready_ack":
                handleReadyAck(ReadyAck.decode(buf(data)));
                return true;
            case "page_sync":
                handlePageSync(PageSync.decode(buf(data)));
                return true;
            case "page_control":
                handlePageControl(PageControl.decode(buf(data)));
                return true;
            case "global_state":
                GlobalState gs = GlobalState.decode(buf(data));
                GLOBALS.clear();
                if (gs.values() != null) {
                    GLOBALS.putAll(gs.values());
                }
                logger.info("[ODC] 全局状态 {} 项", gs.values() == null ? 0 : gs.values().size());
                return true;
            case "config_push":
                com.opendreamcore.protocol.message.ConfigPush cp =
                        com.opendreamcore.protocol.message.ConfigPush.decode(buf(data));
                logger.info("[ODC] 客户端配置下发 {} 字节", cp.properties() == null ? 0 : cp.properties().length());
                return true;
            case "hud_sync":
                HudSync hud = HudSync.decode(buf(data));
                if (hud.remove()) {
                    hudPageIds.remove(hud.pageId());
                    logger.info("[ODC] HUD 卸载 {}", hud.pageId());
                } else {
                    handlePageSync(new PageSync(hud.pageId(), hud.content(), hud.encrypted()));
                    if (!hudPageIds.contains(hud.pageId())) {
                        hudPageIds.add(hud.pageId());
                        logger.info("[ODC] HUD 挂载 {}（{}）", hud.pageId(), hud.mode());
                    }
                }
                if (hudListener != null) {
                    hudListener.accept(hud.pageId());
                }
                return true;
            case "pack":
                // 服务端让客户端装资源包：拿注册进来的注入器真实执行
                String packUrl = new String(data, java.nio.charset.StandardCharsets.UTF_8).trim();
                logger.info("[ODC] 服务端下发资源包 {}", packUrl);
                if (!packUrl.isEmpty()) {
                    com.opendreamcore.client.spi.ResourcePackInjector.Host.current().inject(packUrl);
                }
                return true;
            case "tooltip_registry":
                com.opendreamcore.client.LegacyTooltipStore.handle(
                        com.opendreamcore.protocol.message.TooltipRegistry.decode(buf(data)));
                return true;
            case "window_title":
                com.opendreamcore.protocol.message.WindowTitlePush wt =
                        com.opendreamcore.protocol.message.WindowTitlePush.decode(buf(data));
                logger.info("[ODC] 窗口标题推送 op={} 文本 {} 字",
                        wt.op(), wt.text() == null ? 0 : wt.text().length());
                if (wt.op() == com.opendreamcore.protocol.message.WindowTitlePush.Op.RESET) {
                    com.opendreamcore.client.WindowBranding.resetAndClear();
                    return true;
                }
                // SET_CONFIG 带打字机/轮播才建序列器，纯静态走原文
                if (wt.op() == com.opendreamcore.protocol.message.WindowTitlePush.Op.SET_CONFIG
                        && (wt.typewriter() || (wt.titles() != null && wt.titles().size() > 1))) {
                    com.opendreamcore.branding.TitleConfig cfg =
                            new com.opendreamcore.branding.TitleConfig();
                    cfg.text = wt.text() == null ? "" : wt.text();
                    cfg.titles = wt.titles();
                    cfg.typewriter = wt.typewriter();
                    cfg.random = wt.random();
                    cfg.speed = wt.speed();
                    cfg.interval = wt.interval();
                    cfg.holdMs = wt.holdMs();
                    cfg.loop = wt.loop();
                    // 服务器把内容全空着发过来就当 RESET 处理：序列器拿着空串
                    // 每 tick 刷标题，窗口名直接变空，这就是“默认空标题”的来源
                    if (cfg.text.isEmpty() && (cfg.titles == null || cfg.titles.isEmpty())) {
                        com.opendreamcore.client.WindowBranding.resetAndClear();
                        return true;
                    }
                    com.opendreamcore.client.WindowBranding.onSequence(
                            new com.opendreamcore.branding.TypewriterSequencer(cfg));
                    // 序列源句落缓存：重启后先亮第一句，别闪回原版
                    if (!cfg.text.isEmpty()) {
                        com.opendreamcore.client.WindowBranding.cacheTitle(cfg.text);
                    } else if (cfg.titles != null && !cfg.titles.isEmpty()) {
                        com.opendreamcore.client.WindowBranding.cacheTitle(cfg.titles.get(0));
                    }
                    return true;
                }
                // 静态文本：text 空着就取轮播第一句（服务器配置里 titles 有货
                // text 留空是常态），两边都空等于没配内容，还原原版别硬写
                String staticTitle = (wt.text() != null && !wt.text().isEmpty()) ? wt.text()
                        : (wt.titles() != null && !wt.titles().isEmpty() ? wt.titles().get(0) : null);
                if (staticTitle == null) {
                    com.opendreamcore.client.WindowBranding.resetAndClear();
                    return true;
                }
                if (titleListener != null) {
                    titleListener.accept(staticTitle);
                }
                // 静态标题也得记进 WindowBranding：tick() 每 tick 重写靠的就是
                // serverTitle，绕过它的话下一 tick 什么都不写，原版随手一改
                // 标题就没了——上一版标题闪一下就消失就是这条路径漏了记账
                com.opendreamcore.client.WindowBranding.onWindowTitle(staticTitle);
                return true;
            case "state_patch":
                // 服务器脚本推的会话变量（tps/ping/自定义 vars）：进变量表，
                // 字符替换的 {vars.xxx} 就是从这取的。按会话 id 分键，
                // 同名变量后到覆盖先到
                com.opendreamcore.protocol.message.StatePatch sp =
                        com.opendreamcore.protocol.message.StatePatch.decode(buf(data));
                if (sp.values() != null) {
                    for (Map.Entry<String, Object> en : sp.values().entrySet()) {
                        com.opendreamcore.client.PageVariables.set(sp.sessionId(), en.getKey(), en.getValue());
                    }
                }
                return true;
            case "visual_rules":
                com.opendreamcore.protocol.message.VisualRulesSync vr =
                        com.opendreamcore.protocol.message.VisualRulesSync.decode(buf(data));
                // 九系统规则整包入库：ItemIcon/KeyConfig/世界面板页立即消费，
                // 其余系统现代端也只是摆着待用，这里同款
                com.opendreamcore.client.visual.LegacyVisualSkins.apply(vr, this);
                logger.info("[ODC] 视觉规则入库 {} 个系统 / {} 条",
                        vr.toBundles().size(), vr.entries().size());
                return true;
            case "cloud_manifest":
                // 云资源清单：对比本地缓存，缺的回 cloud_diff 拉过来
                CloudCache.handleManifest(
                        com.opendreamcore.protocol.message.CloudManifest.decode(buf(data)));
                return true;
            case "cloud_file":
                CloudCache.handleFile(
                        com.opendreamcore.protocol.message.CloudFile.decode(buf(data)));
                return true;
            case "cloud_delete":
                CloudCache.handleDelete(
                        com.opendreamcore.protocol.message.CloudDelete.decode(buf(data)));
                return true;
            case "cloud_done":
                CloudCache.handleDone(
                        com.opendreamcore.protocol.message.CloudDone.decode(buf(data)));
                return true;
            case "custom_packet": {
                // 服务端 → 客户端自定义通道：解码后交给桥事件总线（附属 Java 订阅、
                // 脚本侧 Network.订阅 两边同一个总线，现代端同口径）
                com.opendreamcore.protocol.message.CustomPacket custom =
                        com.opendreamcore.protocol.message.CustomPacket.decode(buf(data));
                com.opendreamcore.client.api.BridgeEvents.post(
                        "custom:" + custom.channel(), custom.payload());
                return true;
            }
            default:
                return false;
        }
    }

    private OdcByteArrayBuf buf(byte[] data) {
        return new OdcByteArrayBuf(data);
    }

    private byte[] sessionKey = new byte[0];

    private void handleReadyAck(ReadyAck ack) {
        serverVersion = ack.modVersion();
        serverProtocol = ack.protocolVersion();
        byte[] key = ack.resourceKey();
        boolean keyChanged = key != null && key.length > 0
                && !java.util.Arrays.equals(key, sessionKey);
        sessionKey = key == null ? new byte[0] : key;
        // 云缓存同把 key：哈希命名定位、内存解密都用它，两边必须一致
        CloudCache.onReadyAck(key);
        logger.info("[ODC] 握手完成：服务端 v{}（协议 v{}）", serverVersion, serverProtocol);
        if (keyChanged) {
            retryEncryptedPages();
        }
    }

    /** 密钥未到时扣住的加密页；key 一到立即重解。 */
    private final java.util.List<PageSync> pendingEncrypted = new java.util.ArrayList<>();

    private void retryEncryptedPages() {
        if (pendingEncrypted.isEmpty()) {
            return;
        }
        java.util.List<PageSync> retry = new java.util.ArrayList<>(pendingEncrypted);
        pendingEncrypted.clear();
        for (PageSync ps : retry) {
            handlePageSync(ps);
        }
    }

    private void handlePageSync(PageSync sync) {
        if (sync.encrypted()) {
            if (sessionKey.length == 0) {
                pendingEncrypted.add(sync);
                logger.info("[ODC] 加密页面 {} 密钥未到，暂存", sync.pageId());
                return;
            }
            try {
                byte[] plain = com.opendreamcore.protocol.Crypto.decrypt(sessionKey, sync.content());
                sync = new PageSync(sync.pageId(), plain, false);
            } catch (Exception e) {
                logger.warn("[ODC] 页面 {} 解密失败: {}", sync.pageId(), e.toString());
                com.opendreamcore.client.spi.ChatNotifier.Host.warnOnce(
                        "page-decrypt:" + sync.pageId(),
                        "§c[OpenDreamCore] §f页面 " + sync.pageId() + " 解密失败: "
                                + shortReason(e));
                return;
            }
        }
        pages.put(sync.pageId(), sync);
        // 解析验证：YAML → IR → Page（schema 构建走 common 全管线）
        int elements = -1;
        try {
            Map<String, Object> ir = new com.opendreamcore.config.YamlParser().parse(
                    new String(sync.content(), java.nio.charset.StandardCharsets.UTF_8));
            com.opendreamcore.page.Page parsed =
                    com.opendreamcore.config.PageSchema.build(sync.pageId(), ir);
            elements = parsed.elements() == null ? 0 : parsed.elements().size();
        } catch (Exception e) {
            logger.warn("[ODC] 页面 {} 解析失败: {}", sync.pageId(), e.toString());
            // 解析失败不能哑巴：聊天栏吱一声，服务端不至于对着黑屏猜
            com.opendreamcore.client.spi.ChatNotifier.Host.warnOnce(
                    "page-parse:" + sync.pageId(),
                    "§c[OpenDreamCore] §f页面 " + sync.pageId() + " 解析失败: "
                            + shortReason(e));
        }
        logger.info("[ODC] 页面入库 {}（{}，{} 字节，{} 元素）",
                sync.pageId(), sync.encrypted() ? "加密" : "明文", sync.content().length,
                elements < 0 ? "解析失败" : String.valueOf(elements));
        // 页面到了就补跑之前扣住的控制指令（OPEN 先于 page_sync 的竞态）
        if (!pendingControls.isEmpty()) {
            java.util.List<Runnable> replay = new java.util.ArrayList<>(pendingControls);
            pendingControls.clear();
            for (Runnable r : replay) {
                r.run();
            }
        }
    }

    private static final java.util.Map<String, String> PAGE_SESSIONS =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** 异常原因缩略：聊天栏一句话说清，别贴全文堆栈。 */
    private static String shortReason(Throwable t) {
        if (t == null) {
            return "未知原因";
        }
        String s = String.valueOf(t);
        return s.length() > 120 ? s.substring(0, 120) + "…" : s;
    }

    /** 给定页面的最近一次 OPEN 会话 id；没记录给空串（单人/重进时正常发生）。 */
    public static String sessionOf(String pageId) {
        String s = pageId == null ? null : PAGE_SESSIONS.get(pageId);
        return s == null ? "" : s;
    }

    private java.util.function.Consumer<String> closeListener;

    /** 渲染层注册：CLOSE/SUB_CLOSE 时回调页面 id（清理 activePageId 与互斥状态用）。 */
    public void onClose(java.util.function.Consumer<String> listener) {
        this.closeListener = listener;
    }

    /** 控制指令先于页面到达时扣住，页面入库后回放（同 modern 已验证策略）。 */
    private final java.util.List<Runnable> pendingControls = new java.util.ArrayList<>();

    private void handlePageControl(PageControl control) {
        PageSync page = pages.get(control.pageId());
        if (page == null && !pages.isEmpty()) {
            // 有库存但没这条 → 真未知；库存为空 → 可能还在路上，扣住
            if (pages.isEmpty()) {
                pendingControls.add(() -> dispatch("page_control", encode(control)));
                logger.info("[ODC] 控制指令 {} 暂存待回放", control.pageId());
                return;
            }
            logger.warn("[ODC] 控制指令引用未知页面 {}", control.pageId());
            return;
        }
        if (page == null) {
            pendingControls.add(() -> dispatch("page_control", encode(control)));
            return;
        }
        logger.info("[ODC] 页面控制 {} action={}", control.pageId(), control.action());
        if (control.action() == PageControl.Action.OPEN
                || control.action() == PageControl.Action.SUB_OPEN) {
            PAGE_SESSIONS.put(control.pageId(), control.sessionId());
        }
        if (control.action() == PageControl.Action.CLOSE
                || control.action() == PageControl.Action.SUB_CLOSE) {
            // 关页时把动画时钟/页签状态一并抹掉，重开重放、页签回默认
            com.opendreamcore.client.render.PageAnimations.dropPage(control.pageId());
            com.opendreamcore.client.render.WorldPanels.dropPage(control.pageId());
        }
        if (openListener != null && (
                control.action() == PageControl.Action.OPEN
                        || control.action() == PageControl.Action.SUB_OPEN)) {
            openListener.accept(control.pageId());
        }
    }

    private byte[] encode(PageControl control) {
        com.opendreamcore.protocol.OdcByteArrayBuf buf = new com.opendreamcore.protocol.OdcByteArrayBuf();
        control.encode(buf);
        return buf.toByteArray();
    }

    private java.util.function.Consumer<String> openListener;
    private java.util.function.Consumer<String> titleListener;
    private final java.util.Set<String> hudPageIds = new java.util.LinkedHashSet<>();
    private java.util.function.Consumer<String> hudListener;

    /** 常驻 HUD 页面 id 集合（渲染层每帧遍历绘制）。 */
    public java.util.Set<String> hudPageIds() {
        return new java.util.LinkedHashSet<>(hudPageIds);
    }

    /** codc hud 本地卸载：和服务器 hud_sync remove 同一效果，只是不发东西。 */
    public void unmountHud(String pageId) {
        if (hudPageIds.remove(pageId)) {
            com.opendreamcore.client.render.PageAnimations.dropPage(pageId);
            com.opendreamcore.client.render.WorldPanels.dropPage(pageId);
            if (hudListener != null) {
                hudListener.accept(pageId);
            }
        }
    }

    public void onHud(java.util.function.Consumer<String> listener) {
        this.hudListener = listener;
    }

    /** 渲染层注册：OPEN/SUB_OPEN 时回调页面 id（用于切换当前显示页）。 */
    public void onOpen(java.util.function.Consumer<String> listener) {
        this.openListener = listener;
    }

    /** 窗口标题回调：target 注册后 window_title 推送直达平台 API。 */
    public void onWindowTitle(java.util.function.Consumer<String> listener) {
        this.titleListener = listener;
    }

    public String serverVersion() {
        return serverVersion;
    }

    /** 渲染层取页面模型：视觉规则页 > 服务端下发 > 本地页。 */
    public com.opendreamcore.page.Page page(String id) {
        com.opendreamcore.page.Page visual = visualPages.get(id);
        if (visual != null) {
            return visual;
        }
        PageSync sync = pages.get(id);
        if (sync == null) {
            return localPages.get(id);
        }
        try {
            Map<String, Object> ir = new com.opendreamcore.config.YamlParser().parse(
                    new String(sync.content(), java.nio.charset.StandardCharsets.UTF_8));
            return com.opendreamcore.config.PageSchema.build(id, ir);
        } catch (Exception e) {
            logger.warn("[ODC] 页面 {} 读取失败: {}", id, e.toString());
            return null;
        }
    }

    /** codc dump 用：页面原始 YAML（视觉规则页和本地页没走 page_sync，本地页另有原文表）。 */
    public String rawPage(String id) {
        PageSync sync = pages.get(id);
        if (sync != null) {
            return new String(sync.content(), java.nio.charset.StandardCharsets.UTF_8);
        }
        return localSources.get(id);
    }

    /** 视觉规则生成的世界面板页（WorldTexture/HeadTag），随规则整包重建。 */
    private final java.util.Map<String, com.opendreamcore.page.Page> visualPages =
            new java.util.LinkedHashMap<>();

    /** 视觉规则页注册：同名覆盖，页 id 带系统前缀不会和 page_sync 冲突。 */
    public void registerVisualPage(com.opendreamcore.page.Page page) {
        visualPages.put(page.id(), page);
    }

    /** 视觉规则页 id 集合（渲染层每帧遍历画）。 */
    public java.util.List<String> visualPageIds() {
        return new java.util.ArrayList<>(visualPages.keySet());
    }

    /** 任一可用会话 id（按键组合上报要用）；一个都没有给 null。 */
    public static String anySessionId() {
        for (String s : PAGE_SESSIONS.values()) {
            if (s != null && !s.isEmpty()) {
                return s;
            }
        }
        return null;
    }

    public java.util.List<String> pageIds() {
        java.util.List<String> out = new java.util.ArrayList<>(visualPages.keySet());
        out.addAll(pages.keySet());
        out.addAll(localPages.keySet());
        return out;
    }

    // 本地页面包（附属页面源供的，现代端 LocalPageManager 同款职责）

    private final java.util.Map<String, String> localSources =
            new java.util.concurrent.ConcurrentHashMap<String, String>();
    private final java.util.Map<String, com.opendreamcore.page.Page> localPages =
            new java.util.concurrent.ConcurrentHashMap<String, com.opendreamcore.page.Page>();

    /** 本地 UI 目录（各版壳开局注入，拿不到就不装本地页）。 */
    private static volatile java.util.function.Supplier<java.nio.file.Path> uiDirSupplier;

    /** 各版 ClientHooks 开局调一次。 */
    public static void setUiDir(java.util.function.Supplier<java.nio.file.Path> dir) {
        uiDirSupplier = dir;
    }

    /**
     * 重扫本地页面：把 PageSourceRegistry 里登记的源全跑一遍（核心自带 YAML 目录源
     * 排第一），逐个入库。跑在客户端线程上，一个源挂了不牵连别人。
     */
    public int loadLocalPages() {
        java.util.function.Supplier<java.nio.file.Path> s = uiDirSupplier;
        if (s == null) {
            return 0;
        }
        java.nio.file.Path uiDir = null;
        try {
            uiDir = s.get();
        } catch (Exception ignored) {
        }
        if (com.opendreamcore.client.api.PageSourceRegistry.sources().isEmpty()) {
            com.opendreamcore.client.api.PageSourceRegistry.register(
                    new com.opendreamcore.client.api.YamlDirPageSource());
        }
        int n = 0;
        for (com.opendreamcore.client.api.PageSource source
                : com.opendreamcore.client.api.PageSourceRegistry.sources()) {
            java.util.Map<String, String> raws;
            try {
                raws = source.scan(uiDir);
            } catch (Exception ex) {
                logger.warn("[ODC] 页面源 {} 扇描失败: {}", source.name(), ex.toString());
                continue;
            }
            if (raws == null) {
                continue;
            }
            for (java.util.Map.Entry<String, String> entry : raws.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                if (putLocalPage(entry.getKey(), entry.getValue())) {
                    n++;
                }
            }
        }
        if (n > 0) {
            logger.info("[ODC] 本地页面入库 {} 页（源：{}）", n,
                    com.opendreamcore.client.api.PageSourceRegistry.names());
        }
        return n;
    }

    /** 单页入库：走跟服务端页同一条解析管线（YAML → IR → Page）。 */
    private boolean putLocalPage(String id, String yaml) {
        try {
            Map<String, Object> ir = new com.opendreamcore.config.YamlParser().parse(yaml);
            com.opendreamcore.page.Page built =
                    com.opendreamcore.config.PageSchema.build(id, ir);
            localPages.put(id, built);
            localSources.put(id, yaml);
            return true;
        } catch (Exception e) {
            logger.warn("[ODC] 本地页面 {} 解析失败: {}", id, e.toString());
            com.opendreamcore.client.spi.ChatNotifier.Host.warnOnce(
                    "local-page:" + id,
                    "§c[OpenDreamCore] §f本地页面 " + id + " 解析失败: " + shortReason(e));
            return false;
        }
    }

    /** 运行时塞一页（面板克隆、附属直接注册用）。 */
    public void registerLocalPage(com.opendreamcore.page.Page page) {
        if (page != null && page.id() != null) {
            localPages.put(page.id(), page);
        }
    }

    /** 本地页 id 清单。 */
    public java.util.List<String> localPageIds() {
        java.util.List<String> out = new java.util.ArrayList<>(localPages.keySet());
        java.util.Collections.sort(out);
        return out;
    }
}
