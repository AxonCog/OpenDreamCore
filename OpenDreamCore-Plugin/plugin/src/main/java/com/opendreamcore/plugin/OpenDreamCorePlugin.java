package com.opendreamcore.plugin;

import com.opendreamcore.plugin.command.OdcCommand;
import com.opendreamcore.plugin.network.ProtocolHandler;
import com.opendreamcore.plugin.network.ProtocolListener;
import com.opendreamcore.plugin.page.ServerPageManager;
import com.opendreamcore.plugin.server.MatchListener;
import com.opendreamcore.plugin.server.ServerMethods;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

/**
 * 服务端插件入口：协议通道 + 页面仓库 + 命令 + match 触发。
 * 作者：梦幻 QQ:2496599413
 * GitHub: AxonCog/OpenDreamCore
 */
public class OpenDreamCorePlugin extends JavaPlugin {

    private static OpenDreamCorePlugin instance;

    private ProtocolListener network;
    private ServerPageManager pages;
    private com.opendreamcore.plugin.server.EditorManager editors;
    private com.opendreamcore.plugin.container.ContainerRegistry containers;
    private com.opendreamcore.plugin.hud.HudRegistry hud;
    private com.opendreamcore.plugin.server.TooltipManager tooltips;
    private com.opendreamcore.plugin.server.UiWatcher watcher;

    /** 供脚本方法等静态入口取网络层。 */
    public static OpenDreamCorePlugin get() {
        return instance;
    }

    /** 容器注册表（容器 UI 会话 ↔ 真实容器绑定）。 */
    public com.opendreamcore.plugin.container.ContainerRegistry containerRegistry() {
        return containers;
    }

    /** HUD 三型注册表（个人/全局常驻/静态广播）。 */
    public com.opendreamcore.plugin.hud.HudRegistry hudRegistry() {
        return hud;
    }

    /** 页面仓库（脚本方法取编译后 YAML 用）。 */
    public ServerPageManager pageManager() {
        return pages;
    }

    /** tooltips 管理器（文件监听自动重载用）。 */
    public com.opendreamcore.plugin.server.TooltipManager tooltipManager() {
        return tooltips;
    }

    /** 网络层（脚本方法 Container.刷新 等重发快照用）。 */
    public ProtocolListener networkLayer() {
        return network;
    }

    /** 给指定玩家下发状态补丁（脚本 Screen.更新状态 用）。 */
    public void sendStatePatch(org.bukkit.entity.Player player, java.util.Map<String, Object> values) {
        if (network != null) {
            network.sendStatePatch(player, values);
        }
    }

    public void closePage(org.bukkit.entity.Player player) {
        if (network != null) {
            network.closePage(player);
        }
    }

    /** 下发并打开页面（脚本 Screen.打开页面 用；扁平语法按玩家编译 + 加密下发）。 */
    public void openPage(org.bukkit.entity.Player player, String pageId) {
        if (network == null) {
            return;
        }
        String yaml = pages.compiledYaml(pageId, player);
        if (yaml != null) {
            network.openPage(player, pageId, yaml);
        }
    }

    /** 打开子页（脚本 Screen.打开子页 用）。 */
    public void openSubPage(org.bukkit.entity.Player player, String pageId) {
        if (network == null) {
            return;
        }
        String yaml = pages.compiledYaml(pageId, player);
        if (yaml != null) {
            network.openSubPage(player, pageId, yaml);
        }
    }

    /** /odc edit list。 */
    public String editorOverview() {
        return editors == null ? "编辑器未就绪" : editors.overview();
    }

    /** /odc stats：页面/事件/脚本/容器/HUD 统计。 */
    public String statsOverview() {
        int pageCount = pages.ids().size();
        int elementCount = 0;
        for (String id : pages.ids()) {
            var page = pages.get(id);
            if (page != null) {
                elementCount += countElements(page.elements());
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("§aOpenDreamCore §fv").append(getDescription().getVersion())
                .append(" §7(协议 v").append(com.opendreamcore.protocol.Protocol.VERSION).append(")\n");
        sb.append("§7页面 ").append(pageCount).append(" 个（元素 ").append(elementCount).append("）\n");
        sb.append("§7在线 ").append(getServer().getOnlinePlayers().size()).append(" 人\n");
        sb.append("§7").append(network.handlerStats()).append('\n');
        sb.append("§7容器绑定 ").append(containers.size()).append(" | HUD 注册 ").append(hud.size());
        return sb.toString();
    }

    private static int countElements(java.util.List<com.opendreamcore.page.Element> elements) {
        int count = elements.size();
        for (com.opendreamcore.page.Element element : elements) {
            count += countElements(element.children());
        }
        return count;
    }

    /** /odc edit grant：给指定玩家授予页面编辑权（无玩家参数用执行者）。 */
    public String editorGrant(String pageId, String playerName) {
        if (editors == null) {
            return "编辑器未就绪";
        }
        if (!pages.ids().contains(pageId)) {
            return "没有这个页面: " + pageId;
        }
        org.bukkit.entity.Player player = getServer().getPlayerExact(playerName);
        if (player == null) {
            return "玩家不在线: " + playerName;
        }
        var result = editors.request(player, pageId);
        if (result.action() == com.opendreamcore.protocol.message.EditorLease.Action.GRANT && network != null) {
            // 同步客户端租约状态（客户端据此进入世界编辑模式）
            network.sendEditorLease(player, result);
        }
        return result.action() == com.opendreamcore.protocol.message.EditorLease.Action.GRANT
                ? "已授予 " + playerName + " 编辑 " + pageId
                : "授予失败（" + result.holder() + " 正在编辑）";
    }

    /** /odc edit revoke：释放页面编辑权（仅持有者/管理员）。 */
    public String editorRevoke(String pageId, String playerName) {
        if (editors == null) {
            return "编辑器未就绪";
        }
        org.bukkit.entity.Player player = getServer().getPlayerExact(playerName);
        if (player == null) {
            return "玩家不在线: " + playerName;
        }
        editors.release(player, pageId);
        return "已释放 " + pageId + " 的编辑权";
    }

    @Override
    public void onEnable() {
        instance = this;
        ServerMethods.registerAll();
        com.opendreamcore.plugin.server.ServerPlaceholders.registerAll();
        saveDefaultConfig(); // config.yml（文件监听开关 + client 配置段下发）

        pages = new ServerPageManager(this);
        pages.load();

        containers = new com.opendreamcore.plugin.container.ContainerRegistry();
        hud = new com.opendreamcore.plugin.hud.HudRegistry();

        var cloud = new com.opendreamcore.plugin.cloud.CloudResourceManager(this);
        tooltips = new com.opendreamcore.plugin.server.TooltipManager(this);
        tooltips.load();
        loadTypeAliases();
        editors = new com.opendreamcore.plugin.server.EditorManager(this, pages);
        network = new ProtocolListener(this, new ProtocolHandler(this, pages), cloud, tooltips, editors, pages);
        cloud.attach(network);
        network.registerChannels();

        getServer().getPluginManager().registerEvents(new MatchListener(this, pages, network), this);

        // Functions 触发器（join/quit/chat/death/respawn/tick）
        var triggers = new com.opendreamcore.plugin.server.FunctionTriggers(this, pages);
        getServer().getPluginManager().registerEvents(triggers, this);
        triggers.startTick();

        OdcCommand command = new OdcCommand(this, pages, network);
        getCommand("odc").setExecutor(command);
        getCommand("odc").setTabCompleter(command);

        // 文件监听自动热重载（UI / resources / tooltips 目录）
        if (getConfig().getBoolean("file-watcher.enabled", true)) {
            watcher = new com.opendreamcore.plugin.server.UiWatcher(this,
                    getConfig().getLong("file-watcher.debounce-ms", 300));
            watcher.watch(getDataFolder().toPath().resolve("UI"));
            watcher.watch(getDataFolder().toPath().resolve("resources"));
            watcher.watch(getDataFolder().toPath().resolve("tooltip"));
            watcher.watch(getDataFolder().toPath());
            watcher.start();
        }

        // 全局状态周期刷新（在线人数等，5 秒一次）
        getServer().getScheduler().runTaskTimer(this, network::broadcastGlobalState, 100L, 100L);

        // 启动 Logo
        String[] logo = {
                "§d  ____                   ____                     _   ",
                "§d / __ \\                 / ___|                   | |  ",
                "§d| |  | |_   _ _ __ ___  | (___   ___  ___ _ __ ___| |_ ",
                "§d| |  | | | | | '_ ` _ \\  \\___ \\ / _ \\/ __| '__/ _ \\ __|",
                "§d| |__| | |_| | | | | | | ____) | (_) \\__ \\ | |  __/ |_ ",
                "§d \\____/ \\__,_|_| |_| |_| |____/ \\___/|___/_|  \\___|\\__|",
                "§f v" + getDescription().getVersion()
                        + " §7| 协议 v" + com.opendreamcore.protocol.Protocol.VERSION
                        + " §7| 梦幻 QQ:2496599413",
                "§7  https://github.com/AxonCog/OpenDreamCore"
        };
        for (String line : logo) {
            getLogger().info(line);
        }

        getLogger().info("OpenDreamCore 服务端核心已启用（页面 " + pages.ids().size() + " 个）");
    }

    /** 从 type-aliases.yml 加载自定义 type 别名（热加载用） */
    public void loadTypeAliases() {
        File file = new File(getDataFolder(), "type-aliases.yml");
        if (!file.isFile()) {
            return;
        }
        try {
            org.bukkit.configuration.file.YamlConfiguration config =
                    org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
            java.util.Map<String, String> aliases = new java.util.LinkedHashMap<>();
            for (String key : config.getKeys(false)) {
                Object val = config.get(key);
                if (val != null) {
                    aliases.put(key, String.valueOf(val));
                }
            }
            com.opendreamcore.config.TypeInferrer.loadAliases(aliases);
            getLogger().info("type 别名已加载 " + aliases.size() + " 条（type-aliases.yml）");
        } catch (Exception e) {
            getLogger().warning("type-aliases.yml 加载失败: " + e);
        }
    }

    @Override
    public void onDisable() {
        if (watcher != null) {
            watcher.stop();
        }
        if (network != null) {
            network.unregisterChannels();
        }
        getLogger().info("OpenDreamCore 服务端核心已停用");
    }
}
