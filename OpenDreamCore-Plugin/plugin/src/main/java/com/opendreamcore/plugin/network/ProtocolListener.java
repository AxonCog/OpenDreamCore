package com.opendreamcore.plugin.network;

import com.opendreamcore.plugin.OpenDreamCorePlugin;
import com.opendreamcore.plugin.cloud.CloudResourceManager;
import com.opendreamcore.protocol.Protocol;
import com.opendreamcore.protocol.message.CloudDiff;
import com.opendreamcore.protocol.message.CloudManifest;
import com.opendreamcore.protocol.message.PageControl;
import com.opendreamcore.protocol.message.PageSync;
import com.opendreamcore.protocol.message.ReadyAck;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 协议通道监听：收 ready / ui_event / cloud_diff，发 ready_ack / page_sync / page_control / cloud_*。
 * 通道名 opendreamcore:*，包体是协议消息完整二进制（OdcByteArrayBuf 编解码）。
 * 客户端 NeoForge mod 发 vanilla custom payload，Paper 把未注册通道转给 Bukkit messenger。
 */
public final class ProtocolListener implements PluginMessageListener {

    private final OpenDreamCorePlugin plugin;
    private final ProtocolHandler handler;
    private final CloudResourceManager cloud;
    private final com.opendreamcore.plugin.server.TooltipManager tooltips;
    private final com.opendreamcore.plugin.server.EditorManager editors;
    private final com.opendreamcore.plugin.page.ServerPageManager pages;
    private final Map<Player, Long> readyPlayers = new ConcurrentHashMap<>();
    /** 玩家客户端版本信息（ready 时记录，/odc version 用）。 */
    private final Map<Player, ClientVersionInfo> clientVersions = new ConcurrentHashMap<>();

    /** 玩家客户端版本信息。 */
    public record ClientVersionInfo(String modVersion, int protocolVersion, long readyAt) {}

    public ProtocolListener(OpenDreamCorePlugin plugin, ProtocolHandler handler, CloudResourceManager cloud,
                            com.opendreamcore.plugin.server.TooltipManager tooltips,
                            com.opendreamcore.plugin.server.EditorManager editors,
                            com.opendreamcore.plugin.page.ServerPageManager pages) {
        this.plugin = plugin;
        this.handler = handler;
        this.cloud = cloud;
        this.tooltips = tooltips;
        this.editors = editors;
        this.pages = pages;
    }

    /** 注册收/发通道。 */
    public void registerChannels() {
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, channel(Protocol.READY), this);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, channel(Protocol.UI_EVENT), this);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, channel(Protocol.CLOUD_DIFF), this);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, channel(Protocol.TOOLTIP_RESYNC), this);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, channel(Protocol.EDITOR_LEASE), this);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, channel(Protocol.EDITOR_SAVE), this);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, channel(Protocol.EDITOR_WORLD), this);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, channel(Protocol.EDITOR_WORLD_ACK));
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, channel(Protocol.PAGE_LAYOUT), this);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, channel(Protocol.PAGE_CLOSE), this);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, channel(Protocol.CUSTOM_PACKET), this);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, channel(Protocol.READY_ACK));
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, channel(Protocol.PAGE_SYNC));
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, channel(Protocol.PAGE_CONTROL));
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, channel(Protocol.CLOUD_MANIFEST));
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, channel(Protocol.CLOUD_FILE));
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, channel(Protocol.CLOUD_DELETE));
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, channel(Protocol.CLOUD_DONE));
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, channel(Protocol.STATE_PATCH));
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, channel(Protocol.TOOLTIP_REGISTRY));
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, channel(Protocol.GLOBAL_STATE));
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, channel(Protocol.EDITOR_LEASE));
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, channel(Protocol.PAGE_LAYOUT));
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, channel(Protocol.CONTAINER_SYNC));
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, channel(Protocol.CHAT_MESSAGE));
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, channel(Protocol.UI_EFFECT));
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, channel(Protocol.BOSS_BAR));
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, channel(Protocol.NAME_TAG));
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, channel(Protocol.ITEM_TIP));
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, channel(Protocol.HUD_SYNC));
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, channel(Protocol.MUSIC));
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, channel(Protocol.CONFIG_PUSH));
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, channel(Protocol.WINDOW_TITLE));
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, channel(Protocol.UI_ANIMATION));
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, channel(Protocol.WORLD_TAB));
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, channel(Protocol.WORLD_ELEMENT_STATE));
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, channel(Protocol.CUSTOM_PACKET));
    }

    public void unregisterChannels() {
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin);
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin);
    }

    public static String channel(String path) {
        return Protocol.NAMESPACE + ":" + path;
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, @NotNull byte[] bytes) {
        if (channel.equals(channel(Protocol.READY))) {
            handleReady(player, bytes);
        } else if (channel.equals(channel(Protocol.UI_EVENT))) {
            handler.onUiEvent(player, bytes);
        } else if (channel.equals(channel(Protocol.CLOUD_DIFF))) {
            handleCloudDiff(player, bytes);
        } else if (channel.equals(channel(Protocol.TOOLTIP_RESYNC))) {
            send(player, Protocol.TOOLTIP_REGISTRY, tooltips.buildRegistry(player));
        } else if (channel.equals(channel(Protocol.EDITOR_LEASE))) {
            handleEditorLease(player, bytes);
        } else if (channel.equals(channel(Protocol.EDITOR_SAVE))) {
            handleEditorSave(player, bytes);
        } else if (channel.equals(channel(Protocol.EDITOR_WORLD))) {
            handleEditorWorld(player, bytes);
        } else if (channel.equals(channel(Protocol.PAGE_LAYOUT))) {
            handlePageLayout(player, bytes);
        } else if (channel.equals(channel(Protocol.PAGE_CLOSE))) {
            handlePageClose(player, bytes);
        } else if (channel.equals(channel(Protocol.CUSTOM_PACKET))) {
            handleCustomPacket(player, bytes);
        }
    }

    /** 自定义双向通道上行（custom_packet）：分发注册表处理器 + EventBus 脚本订阅。 */
    private void handleCustomPacket(Player player, byte[] bytes) {
        try {
            var packet = com.opendreamcore.protocol.message.CustomPacket.decode(
                    new com.opendreamcore.protocol.OdcByteArrayBuf(bytes));
            CustomPacketRegistry.dispatch(plugin, player, packet.channel(), packet.payload());
        } catch (Exception e) {
            plugin.getLogger().warning("custom_packet 处理失败 (" + player.getName() + "): " + e);
        }
    }

    /** 客户端关页通知：清理会话与容器绑定，广播 CloseEvent。 */
    private void handlePageClose(Player player, byte[] bytes) {
        try {
            var close = com.opendreamcore.protocol.message.PageClose.decode(
                    new com.opendreamcore.protocol.OdcByteArrayBuf(bytes));
            var session = handler.sessionInfo(close.sessionId());
            handler.closeSession(close.sessionId());
            plugin.containerRegistry().unbind(close.sessionId());
            plugin.getLogger().info("页面关闭 " + player.getName() + " 会话 " + close.sessionId());
            // 第三方插件监听（onPluginMessageReceived 已在主线程，直接触发）
            safeCallEvent(com.opendreamcore.plugin.event.OdcEvents.close(player,
                    session == null ? null : session.pageId(), close.sessionId()));
        } catch (Exception e) {
            plugin.getLogger().warning("page_close 处理失败 (" + player.getName() + "): " + e);
        }
    }

    /** 客户端布局保存：校验租约 → 落盘 → 广播给所有玩家（并触发 LayoutEvent）。 */
    private void handlePageLayout(Player player, byte[] bytes) {
        try {
            var layout = com.opendreamcore.protocol.message.PageLayout.decode(
                    new com.opendreamcore.protocol.OdcByteArrayBuf(bytes));
            if (editors.saveLayout(player, layout.pageId(), layout.entries())) {
                safeCallEvent(com.opendreamcore.plugin.event.OdcEvents.layout(player, layout.pageId(), layout.entries()));
                for (Player online : plugin.getServer().getOnlinePlayers()) {
                    if (isReady(online)) {
                        send(online, Protocol.PAGE_LAYOUT, layout);
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("page_layout 处理失败 (" + player.getName() + "): " + e);
        }
    }

    /** 世界 WYSIWYG 保存：校验租约 → 写回页面 YAML（元素 + 页面级选项）→ 回执确认 → 同页玩家重发（编辑者收到后本地编辑态清空）。 */
    private void handleEditorWorld(Player player, byte[] bytes) {
        try {
            var layout = com.opendreamcore.protocol.message.WorldLayout.decode(
                    new com.opendreamcore.protocol.OdcByteArrayBuf(bytes));
            int baked = editors.saveWorldLayout(player, layout.pageId(), layout.entries(),
                    layout.optionsProps(), layout.pageTitle(), layout.variablesProps());
            if (baked > 0) {
                player.sendMessage("§a[OpenDreamCore] §f世界面板已写入 " + layout.pageId()
                        + "（" + baked + " 项/键，位置/属性/选项烘焙进页面文件）");
                send(player, Protocol.EDITOR_WORLD_ACK,
                        new com.opendreamcore.protocol.message.WorldSaveAck(layout.pageId(), baked,
                                "已写入 " + baked + " 项/键"));
                for (Player online : plugin.getServer().getOnlinePlayers()) {
                    if (online.isOnline() && handler.openPageIds(online).contains(layout.pageId())) {
                        plugin.openPage(online, layout.pageId());
                    }
                }
            } else {
                player.sendMessage("§c[OpenDreamCore] §f世界面板保存失败（无租约或文件不可写）");
                send(player, Protocol.EDITOR_WORLD_ACK,
                        new com.opendreamcore.protocol.message.WorldSaveAck(layout.pageId(), 0,
                                "无租约或文件不可写"));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("editor_world 处理失败 (" + player.getName() + "): " + e);
        }
    }

    private void handleEditorLease(Player player, byte[] bytes) {
        try {
            var lease = com.opendreamcore.protocol.message.EditorLease.decode(
                    new com.opendreamcore.protocol.OdcByteArrayBuf(bytes));
            var result = switch (lease.action()) {
                case REQUEST -> editors.request(player, lease.pageId());
                case RELEASE -> {
                    editors.release(player, lease.pageId());
                    yield null;
                }
                default -> null;
            };
            if (result != null) {
                send(player, Protocol.EDITOR_LEASE, result);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("editor_lease 处理失败 (" + player.getName() + "): " + e);
        }
    }

    private void handleEditorSave(Player player, byte[] bytes) {
        try {
            var save = com.opendreamcore.protocol.message.EditorSave.decode(
                    new com.opendreamcore.protocol.OdcByteArrayBuf(bytes));
            if (editors.save(player, save.pageId(), save.yaml())) {
                // 保存成功：把新页面推给所有玩家（其他人也能看到更新）
                String yaml = pages.yamlOf(save.pageId());
                if (yaml != null) {
                    for (Player online : plugin.getServer().getOnlinePlayers()) {
                        if (isReady(online)) {
                            send(online, Protocol.PAGE_SYNC,
                                    new com.opendreamcore.protocol.message.PageSync(save.pageId(), yaml));
                        }
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("editor_save 处理失败 (" + player.getName() + "): " + e);
        }
    }

    private void handleReady(Player player, byte[] bytes) {
        try {
            var ready = com.opendreamcore.protocol.message.Ready.decode(new com.opendreamcore.protocol.OdcByteArrayBuf(bytes));
            String serverVersion = plugin.getDescription().getVersion();
            plugin.getLogger().info("客户端就绪 " + player.getName() + ": 协议 v" + ready.protocolVersion()
                    + "，模组 " + ready.modVersion() + "，能力 " + ready.capabilities());
            readyPlayers.put(player, System.currentTimeMillis());
            clientVersions.put(player, new ClientVersionInfo(ready.modVersion(), ready.protocolVersion(), System.currentTimeMillis()));

            boolean protoOk = ready.protocolVersion() == Protocol.VERSION;
            boolean modOk = ready.modVersion().equals(serverVersion);
            // 会话 key：云资源加密用（每个玩家独立）
            byte[] key = cloud.newSessionKey(player);
            // 能力：根据 allow-local-ui 配置决定是否允许客户端加载本地 UI
            int capabilities = Protocol.CAPABILITY_CLOUD;
            if (plugin.getConfig().getBoolean("allow-local-ui", false)) {
                capabilities |= Protocol.CAPABILITY_LOCAL_UI;
            }
            ReadyAck ack = new ReadyAck(Protocol.VERSION, serverVersion, capabilities, key);
            send(player, Protocol.READY_ACK, ack);

            // 版本提示由客户端在收到 ready_ack 后自行显示（避免重复消息）
            // 服务端控制台记录版本对比
            if (protoOk && modOk) {
                plugin.getLogger().info("客户端版本匹配: " + player.getName()
                        + " v" + ready.modVersion() + " (协议 v" + ready.protocolVersion() + ")");
            } else if (!protoOk) {
                plugin.getLogger().warning("协议版本不匹配: " + player.getName() + " 客户端 v"
                        + ready.protocolVersion() + " 服务端 v" + Protocol.VERSION);
            } else {
                plugin.getLogger().info("模组版本不同: " + player.getName() + " 客户端 v"
                        + ready.modVersion() + " 服务端 v" + serverVersion);
            }

            if (!protoOk) {
                return;
            }
            // 云资源清单下发（能力含 CLOUD 才发）
            if ((ready.capabilities() & Protocol.CAPABILITY_CLOUD) != 0) {
                send(player, Protocol.CLOUD_MANIFEST, cloud.buildManifest());
            }
            // 服务端全局变量（{{global.xxx}} 插值，含 tps/ping）
            send(player, Protocol.GLOBAL_STATE, buildGlobalState(player));
            // 客户端配置下发（config.yml client 段 → odc.properties）
            send(player, Protocol.CONFIG_PUSH, buildClientConfig());
            // 客户端窗口标题（config.yml client-title 段，进服即生效，无需指令；未配置则不下发）
            var titlePush = buildClientTitlePush();
            if (titlePush != null) {
                send(player, Protocol.WINDOW_TITLE, titlePush);
                plugin.getLogger().info("已向 " + player.getName() + " 下发窗口标题（"
                        + (titlePush.text() == null ? 0 : titlePush.text().length()) + " 字，typewriter="
                        + titlePush.typewriter() + "）");
            } else {
                plugin.getLogger().info("窗口标题未下发：client-title 未启用或文本为空");
            }

            // 页面下发：push-pages=all 时全量推送所有页面；否则只推已开会话的
            // 会话可能在通道注册前开启（join 自动开服），首发 PAGE_SYNC 会被 Paper 丢弃；
            // ready 时通道已注册，此处补发保证可达
            java.util.Set<String> toPush = new java.util.LinkedHashSet<>(handler.openPageIds(player));
            if (plugin.getConfig().getString("push-pages", "all").equalsIgnoreCase("all")) {
                for (com.opendreamcore.page.Page pg : pages.allPages()) {
                    if (pg.id() != null) {
                        toPush.add(pg.id());
                    }
                }
            }
            for (String pid : toPush) {
                // 必须用编译后的 YAML：原文含 DreamLang 函数块，客户端解析会丢元素
                String yaml = pages.compiledYaml(pid, player);
                if (yaml != null) {
                    send(player, Protocol.PAGE_SYNC, buildPageSync(player, pid, yaml));
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("ready 解析失败 (" + player.getName() + "): " + e);
        }
    }

    /** 构建客户端配置（config.yml 的 client 段 → key=value 行）。 */
    private com.opendreamcore.protocol.message.ConfigPush buildClientConfig() {
        var section = plugin.getConfig().getConfigurationSection("client");
        StringBuilder sb = new StringBuilder();
        if (section != null) {
            for (String key : section.getKeys(false)) {
                Object value = section.get(key);
                sb.append(key).append('=').append(value).append('\n');
            }
        }
        return new com.opendreamcore.protocol.message.ConfigPush(sb.toString());
    }

    /**
     * 读取 config.yml 的 client-title 段为窗口标题下发消息。
     * enabled=false 或未配置（text/titles 全空）返回 null 不下发。
     */
    com.opendreamcore.protocol.message.WindowTitlePush buildClientTitlePush() {
        var section = plugin.getConfig().getConfigurationSection("client-title");
        if (section == null || !section.getBoolean("enabled", false)) {
            return null;
        }
        String text = section.getString("text", "");
        java.util.List<String> titles = section.getStringList("titles");
        boolean typewriter = section.getBoolean("typewriter", false);
        boolean random = section.getBoolean("random", false);
        int speed = section.getInt("speed", 120);
        int interval = section.getInt("interval", 3000);
        int holdMs = section.contains("hold-ms") ? section.getInt("hold-ms") : -1;
        boolean loop = section.getBoolean("loop", true);
        if ((titles == null || titles.isEmpty()) && (text == null || text.isEmpty())) {
            return null;
        }
        return com.opendreamcore.protocol.message.WindowTitlePush.config(
                text, titles, typewriter, random, speed, interval, holdMs, loop);
    }

    /** 下发窗口标题指令（SET/RESET，TitleAPI 与配置下发共用）。 */
    public void sendWindowTitle(Player player, com.opendreamcore.protocol.message.WindowTitlePush push) {
        if (isReady(player)) {
            send(player, Protocol.WINDOW_TITLE, push);
        }
    }

    /** 广播全部页面给所有已握手玩家（文件热重载后同步新内容）。 */
    public void broadcastPages() {
        for (String pageId : pages.ids()) {
            String yaml = pages.yamlOf(pageId);
            if (yaml == null) {
                continue;
            }
            for (Player online : plugin.getServer().getOnlinePlayers()) {
                if (isReady(online)) {
                    send(online, Protocol.PAGE_SYNC, buildPageSync(online, pageId, yaml));
                }
            }
        }
    }

    /** 构建个人全局状态（含 TPS/Ping）：按玩家注入 ping。 */
    private com.opendreamcore.protocol.message.GlobalState buildGlobalState(Player player) {
        var values = new java.util.LinkedHashMap<String, Object>();
        values.put("online", (long) plugin.getServer().getOnlinePlayers().size());
        values.put("max_players", (long) plugin.getServer().getMaxPlayers());
        values.put("server_name", plugin.getServer().getName());
        values.put("tps", serverTps());
        try {
            values.put("ping", (long) player.getPing());
        } catch (Throwable ignored) {
            values.put("ping", 0L);
        }
        return new com.opendreamcore.protocol.message.GlobalState(values);
    }

    /** 服务端 TPS：Paper API getTPS → PAPI %server_tps% → 默认 20.0。 */
    private double serverTps() {
        try {
            double[] tps = plugin.getServer().getTPS();
            if (tps != null && tps.length > 0 && tps[0] > 0) {
                return Math.min(20.0, tps[0]);
            }
        } catch (Throwable ignored) {
            // 非 Paper 服务端没有 getTPS
        }
        // 尝试 PAPI（如 servertools 等插件提供 %server_tps%）
        try {
            if (plugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                Class<?> api = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                Object result = api.getMethod("setPlaceholders", org.bukkit.entity.Player.class, String.class)
                        .invoke(null, null, "%server_tps%");
                if (result != null && !String.valueOf(result).contains("%")) {
                    return Double.parseDouble(String.valueOf(result).trim());
                }
            }
        } catch (Throwable ignored) {
        }
        return 20.0;
    }

    /** 广播全局状态给所有已握手玩家（周期任务调用，刷新在线人数/TPS/Ping 等）。 */
    public void broadcastGlobalState() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (isReady(player)) {
                send(player, Protocol.GLOBAL_STATE, buildGlobalState(player));
            }
        }
    }

    private void handleCloudDiff(Player player, byte[] bytes) {
        try {
            CloudDiff diff = CloudDiff.decode(new com.opendreamcore.protocol.OdcByteArrayBuf(bytes));
            cloud.sendDiff(player, diff.paths());
        } catch (Exception e) {
            plugin.getLogger().warning("cloud_diff 解析失败 (" + player.getName() + "): " + e);
        }
    }

    /** 向单个玩家发送协议消息。 */
    public void send(Player player, String path, com.opendreamcore.protocol.message.Message message) {
        var buf = new com.opendreamcore.protocol.OdcByteArrayBuf();
        message.encode(buf);
        player.sendPluginMessage(plugin, channel(path), buf.toByteArray());
    }

    /** 同一连接内已执行过 open 脚本的页面（player → pageId 集合），防 join 自动开 + 触发器双开。 */
    private final java.util.Map<java.util.UUID, java.util.Set<String>> openedPages = new java.util.concurrent.ConcurrentHashMap<>();

    /** 下发页面：先同步 YAML（会话 key 存在时加密）+ 布局覆盖，再发打开指令（会话由服务端分配）。返回会话 id。
     *  force=true 时忽略同页去重（/odc open 显式指定时允许重开）。 */
    public String openPage(Player player, String pageId, String yaml) {
        return openPage(player, pageId, yaml, false);
    }

    public String openPage(Player player, String pageId, String yaml, boolean force) {
        var key = player.getUniqueId();
        var set = openedPages.computeIfAbsent(key, k -> java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>()));
        if (!force && !set.add(pageId)) {
            // 本连接已开过同一页：跳过重复脚本/PAGE_CONTROL，只回现有会话
            return handler.sessionOf(player);
        }
        send(player, Protocol.PAGE_SYNC, buildPageSync(player, pageId, yaml));
        var layout = editors.loadLayout(pageId);
        if (!layout.isEmpty()) {
            send(player, Protocol.PAGE_LAYOUT, new com.opendreamcore.protocol.message.PageLayout(pageId, layout));
        }
        String sessionId = handler.openSession(player, pageId);
        send(player, Protocol.PAGE_CONTROL, new PageControl(PageControl.Action.OPEN, pageId, sessionId, null));
        // 第三方插件监听（页面打开事件；Arclight 可能拒绝调度器内 callEvent，安全包裹）
        safeCallEvent(com.opendreamcore.plugin.event.OdcEvents.open(player, pageId, sessionId));
        return sessionId;
    }

    /** 页面同步包：有会话 key 就 AES-GCM 加密（加密下发）。 */
    private com.opendreamcore.protocol.message.PageSync buildPageSync(Player player, String pageId, String yaml) {
        byte[] key = cloud.keyOf(player);
        if (key != null && yaml != null) {
            return new com.opendreamcore.protocol.message.PageSync(pageId,
                    com.opendreamcore.protocol.Crypto.encrypt(key, yaml.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                    true);
        }
        return new com.opendreamcore.protocol.message.PageSync(pageId, yaml);
    }

    /** 下发容器内容快照（chest_slot/container 组件数据）。 */
    public void sendContainerSync(Player player, com.opendreamcore.protocol.message.ContainerSync sync) {
        if (sync != null && isReady(player)) {
            send(player, Protocol.CONTAINER_SYNC, sync);
        }
    }

    /** 下发聊天通道消息给单个玩家。 */
    public void sendChatMessage(Player player, com.opendreamcore.protocol.message.ChatMessage message) {
        if (isReady(player)) {
            send(player, Protocol.CHAT_MESSAGE, message);
        }
    }

    /** 下发屏幕特效（震动/闪屏/过渡，服务端脚本远程触发）。 */
    public void sendUiEffect(Player player, com.opendreamcore.protocol.message.UiEffect effect) {
        if (isReady(player)) {
            send(player, Protocol.UI_EFFECT, effect);
        }
    }

    /** 下发 Boss 条（target 为空 = 广播全体）。 */
    public void sendBossBar(com.opendreamcore.protocol.message.BossBarSync sync, Player target) {
        if (target != null) {
            send(target, Protocol.BOSS_BAR, sync);
            return;
        }
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            if (isReady(online)) {
                send(online, Protocol.BOSS_BAR, sync);
            }
        }
    }

    /** 广播名牌（全体）。 */
    public void sendNameTag(com.opendreamcore.protocol.message.NameTagSync sync) {
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            if (isReady(online)) {
                send(online, Protocol.NAME_TAG, sync);
            }
        }
    }

    /** 下发物品提示。 */
    public void sendItemTip(Player player, com.opendreamcore.protocol.message.ItemTipSync sync) {
        if (isReady(player)) {
            send(player, Protocol.ITEM_TIP, sync);
        }
    }

    /** 下发背景音乐指令（播放/停止/音量）。 */
    public void sendMusic(Player player, com.opendreamcore.protocol.message.MusicSync sync) {
        if (isReady(player)) {
            send(player, Protocol.MUSIC, sync);
        }
    }

    /** 挂载服务端 HUD（个人/全局/静态三型；按玩家编译 + 加密下发）。 */
    public void openHud(Player player, String pageId, String yaml,
                        com.opendreamcore.protocol.message.HudSync.Mode mode) {
        byte[] key = cloud.keyOf(player);
        byte[] content;
        boolean encrypted;
        if (key != null && yaml != null) {
            content = com.opendreamcore.protocol.Crypto.encrypt(key,
                    yaml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            encrypted = true;
        } else {
            content = yaml == null ? new byte[0] : yaml.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            encrypted = false;
        }
        String sessionId = handler.openHudSession(player, pageId);
        send(player, Protocol.HUD_SYNC,
                new com.opendreamcore.protocol.message.HudSync(pageId, mode, content, encrypted, sessionId));
    }

    /** 卸载玩家 HUD。 */
    public void closeHud(Player player) {
        if (isReady(player)) {
            send(player, Protocol.HUD_SYNC, new com.opendreamcore.protocol.message.HudSync(
                    com.opendreamcore.protocol.message.HudSync.Mode.HUD, ""));
        }
    }

    /** 广播 HUD 给全部已握手玩家（GHUD/静态）。 */
    public void broadcastHud(String pageId, String yaml, com.opendreamcore.protocol.message.HudSync.Mode mode) {
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            if (isReady(online)) {
                openHud(online, pageId, yaml, mode);
            }
        }
    }

    /** 广播聊天通道消息给全部已握手玩家（target 非空时只发给该玩家）。 */
    public void broadcastChatMessage(com.opendreamcore.protocol.message.ChatMessage message, Player target) {
        if (target != null) {
            sendChatMessage(target, message);
            return;
        }
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            sendChatMessage(online, message);
        }
    }

    /** 关闭玩家当前页面。 */
    public void closePage(Player player) {
        send(player, Protocol.PAGE_CONTROL, new PageControl(PageControl.Action.CLOSE, "current", null, null));
    }

    /** 打开子页（挂在当前会话下）。 */
    public void openSubPage(Player player, String pageId, String yaml) {
        send(player, Protocol.PAGE_SYNC, buildPageSync(player, pageId, yaml));
        String sessionId = handler.openSession(player, pageId);
        send(player, Protocol.PAGE_CONTROL,
                new PageControl(PageControl.Action.SUB_OPEN, pageId, sessionId, handler.sessionOf(player)));
    }

    /** 关闭子页。 */
    public void closeSubPage(Player player) {
        send(player, Protocol.PAGE_CONTROL, new PageControl(PageControl.Action.SUB_CLOSE, "current", null, null));
    }

    /** 移动页面（MOVE）：parentSessionId 字段复用为 "x,y" 坐标串。 */
    public void movePage(Player player, double x, double y) {
        String sessionId = handler.sessionOf(player);
        if (sessionId == null) {
            return;
        }
        send(player, Protocol.PAGE_CONTROL,
                new PageControl(PageControl.Action.MOVE, "current", sessionId, x + "," + y));
    }

    /** 状态补丁：更新玩家当前页面的变量（金币/进度等实时刷新）。 */
    public void sendStatePatch(Player player, java.util.Map<String, Object> values) {
        String sessionId = handler.sessionOf(player);
        if (sessionId == null) {
            return;
        }
        send(player, Protocol.STATE_PATCH, new com.opendreamcore.protocol.message.StatePatch(sessionId, values));
    }

    public boolean isReady(Player player) {
        return readyPlayers.containsKey(player);
    }

    /** 玩家客户端版本信息（/odc version <玩家> 用）。 */
    public ClientVersionInfo clientVersionOf(Player player) {
        return clientVersions.get(player);
    }

    /** 全部在线玩家版本概览（/odc version 用）。 */
    public String versionOverview() {
        String serverVersion = plugin.getDescription().getVersion();
        StringBuilder sb = new StringBuilder();
        sb.append("§a[OpenDreamCore] §f版本信息\n");
        sb.append("§7插件版本: §a").append(serverVersion).append(" §7(协议 v").append(Protocol.VERSION).append(")\n");
        var online = plugin.getServer().getOnlinePlayers();
        if (online.isEmpty()) {
            sb.append("§7无在线玩家");
        } else {
            sb.append("§7在线玩家版本:");
            for (Player p : online) {
                ClientVersionInfo info = clientVersions.get(p);
                sb.append("\n");
                if (info == null) {
                    sb.append("  §7").append(p.getName()).append(" §7— §8未握手");
                } else {
                    boolean protoOk = info.protocolVersion() == Protocol.VERSION;
                    boolean modOk = info.modVersion().equals(serverVersion);
                    if (protoOk && modOk) {
                        sb.append("  §a").append(p.getName()).append(" §7— §a").append(info.modVersion())
                                .append(" §7(协议 v").append(info.protocolVersion()).append(") §a✓");
                    } else if (!protoOk) {
                        sb.append("  §c").append(p.getName()).append(" §7— §c").append(info.modVersion())
                                .append(" §7(协议 §c").append(info.protocolVersion()).append("§7) §c✗");
                    } else {
                        sb.append("  §e").append(p.getName()).append(" §7— §c").append(info.modVersion())
                                .append(" §7(协议 v").append(info.protocolVersion()).append(") §e⚠");
                    }
                }
            }
        }
        return sb.toString();
    }

    /** 玩家离线：清除握手和版本记录。 */
    public void removeSession(Player player) {
        readyPlayers.remove(player);
        clientVersions.remove(player);
        openedPages.remove(player.getUniqueId());
    }

    /** 会话/事件/脚本统计（/odc stats）。 */
    public String handlerStats() {
        return handler.stats();
    }

    /** 下发编辑器租约回执（服务端 /odc edit grant/world 授予后同步客户端租约状态）。 */
    public void sendEditorLease(Player player, com.opendreamcore.protocol.message.EditorLease lease) {
        if (isReady(player)) {
            send(player, Protocol.EDITOR_LEASE, lease);
        }
    }

    /** 玩家当前是否有打开的页面。 */
    public boolean hasOpenPage(Player player) {
        return handler.sessionOf(player) != null;
    }

    /** 玩家当前打开的页面 id（无则 null；/odc world reset 重发用）。 */
    public String openPageId(Player player) {
        String sessionId = handler.sessionOf(player);
        if (sessionId == null) {
            return null;
        }
        var info = handler.sessionInfo(sessionId);
        return info == null ? null : info.pageId();
    }

    /** 下发动画触发（播放/停止/暂停/恢复，服务端远程控制页面动画）。 */
    public void sendUiAnimation(Player player, com.opendreamcore.protocol.message.UiAnimation sync) {
        if (isReady(player)) {
            send(player, Protocol.UI_ANIMATION, sync);
        }
    }

    /** 下发世界页签切换（Screen.设置世界页签：强制某玩家切页签）。 */
    public void sendWorldTab(Player player, String pageId, String tab) {
        if (isReady(player)) {
            send(player, Protocol.WORLD_TAB, new com.opendreamcore.protocol.message.WorldTabSync(pageId, tab));
        }
    }

    /** 广播世界页签切换（Screen.广播世界页签：给所有正在看该页面的玩家，多面板同屏全命中）。 */
    public void broadcastWorldTab(String pageId, String tab) {
        var sync = new com.opendreamcore.protocol.message.WorldTabSync(pageId, tab);
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            if (isReady(online) && handler.openPageIds(online).contains(pageId)) {
                send(online, Protocol.WORLD_TAB, sync);
            }
        }
    }

    /** 下发世界元素状态（Screen.设置元素可见/可用：强制某玩家）。 */
    public void sendWorldElementState(Player player, String pageId, String elementId, int mode, boolean value) {
        if (isReady(player)) {
            send(player, Protocol.WORLD_ELEMENT_STATE,
                    new com.opendreamcore.protocol.message.WorldElementState(pageId, elementId, mode, value));
        }
    }

    /** 广播世界元素状态（给所有正在看该页面的玩家，多面板同屏全命中）。 */
    public void broadcastWorldElementState(String pageId, String elementId, int mode, boolean value) {
        var state = new com.opendreamcore.protocol.message.WorldElementState(pageId, elementId, mode, value);
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            if (isReady(online) && handler.openPageIds(online).contains(pageId)) {
                send(online, Protocol.WORLD_ELEMENT_STATE, state);
            }
        }
    }

    /** 广播 tooltip 注册表给所有已握手玩家（TooltipAPI 运行时注册后调用；按玩家过滤权限）。 */
    public void broadcastTooltips() {
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            if (isReady(online)) {
                send(online, Protocol.TOOLTIP_REGISTRY, tooltips.buildRegistry(online));
            }
        }
    }

    /** 安全触发 Bukkit 事件（Arclight 在调度器任务中调 callEvent 会报异常，try-catch 跳过）。 */
    private void safeCallEvent(org.bukkit.event.Event event) {
        try {
            plugin.getServer().getPluginManager().callEvent(event);
        } catch (IllegalStateException e) {
            // Arclight: cannot be triggered asynchronously from primary server thread
            // 第三方插件事件不影响核心流程，跳过
            plugin.getLogger().fine("事件触发跳过（Arclight 限制）: " + event.getEventName());
        }
    }
}
