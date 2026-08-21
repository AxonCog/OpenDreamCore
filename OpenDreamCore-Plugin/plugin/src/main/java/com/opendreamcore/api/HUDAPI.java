package com.opendreamcore.api;

import com.opendreamcore.plugin.OpenDreamCorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * HUD / 世界 UI API：HUD 三型挂载、Boss 条、头顶名牌、物品提示、背景音乐、屏幕特效。
 * target 参数为 null 时表示全体（Boss 条/音乐）；名牌按目标玩家名解析实体。
 */
public final class HUDAPI {

    static final HUDAPI INSTANCE = new HUDAPI();

    private HUDAPI() {
    }

    // ---- HUD 三型 ----

    /** 挂载个人 HUD（按玩家编译，进服自动重挂）。 */
    public boolean mountHud(Player player, String pageId) {
        OpenDreamCorePlugin plugin = OpenDreamCoreAPI.plugin();
        if (plugin == null) {
            return false;
        }
        plugin.hudRegistry().mountPlayer(player, pageId);
        String yaml = plugin.pageManager().compiledYaml(pageId, player);
        if (yaml == null) {
            return false;
        }
        plugin.networkLayer().openHud(player, pageId, yaml,
                com.opendreamcore.protocol.message.HudSync.Mode.HUD);
        return true;
    }

    /** 卸载个人 HUD。 */
    public boolean unmountHud(Player player) {
        OpenDreamCorePlugin plugin = OpenDreamCoreAPI.plugin();
        if (plugin == null) {
            return false;
        }
        plugin.hudRegistry().unmountPlayer(player);
        plugin.networkLayer().closeHud(player);
        return true;
    }

    /** 挂载全局 HUD（GHUD：全体广播 + 新进服自动挂）。 */
    public boolean mountGlobalHud(String pageId) {
        OpenDreamCorePlugin plugin = OpenDreamCoreAPI.plugin();
        if (plugin == null) {
            return false;
        }
        plugin.hudRegistry().setGlobal(com.opendreamcore.protocol.message.HudSync.Mode.GHUD, pageId);
        broadcastHud(pageId, com.opendreamcore.protocol.message.HudSync.Mode.GHUD);
        return true;
    }

    /** 挂载静态 HUD（HUDStatic：全体广播，内容为纯公告）。 */
    public boolean mountStaticHud(String pageId) {
        OpenDreamCorePlugin plugin = OpenDreamCoreAPI.plugin();
        if (plugin == null) {
            return false;
        }
        plugin.hudRegistry().setGlobal(com.opendreamcore.protocol.message.HudSync.Mode.STATIC, pageId);
        broadcastHud(pageId, com.opendreamcore.protocol.message.HudSync.Mode.STATIC);
        return true;
    }

    /** 卸载全局/静态 HUD（全体）。 */
    public boolean unmountGlobalHud() {
        OpenDreamCorePlugin plugin = OpenDreamCoreAPI.plugin();
        if (plugin == null) {
            return false;
        }
        plugin.hudRegistry().clearGlobal(com.opendreamcore.protocol.message.HudSync.Mode.GHUD);
        plugin.hudRegistry().clearGlobal(com.opendreamcore.protocol.message.HudSync.Mode.STATIC);
        for (Player online : Bukkit.getOnlinePlayers()) {
            plugin.networkLayer().closeHud(online);
        }
        return true;
    }

    private void broadcastHud(String pageId, com.opendreamcore.protocol.message.HudSync.Mode mode) {
        OpenDreamCorePlugin plugin = OpenDreamCoreAPI.plugin();
        for (Player online : Bukkit.getOnlinePlayers()) {
            String yaml = plugin.pageManager().compiledYaml(pageId, online);
            if (yaml != null) {
                plugin.networkLayer().openHud(online, pageId, yaml, mode);
            }
        }
    }

    // ---- Boss 条 ----

    /** 显示 Boss 条（target null = 全体）。 */
    public boolean showBossBar(Player target, String id, String text, double progress, String color) {
        OpenDreamCorePlugin plugin = OpenDreamCoreAPI.plugin();
        if (plugin == null || id == null) {
            return false;
        }
        plugin.networkLayer().sendBossBar(new com.opendreamcore.protocol.message.BossBarSync(id,
                com.opendreamcore.protocol.message.BossBarSync.Action.ADD, text == null ? "" : text,
                progress, color == null ? "#E53935" : color), target);
        return true;
    }

    /** 更新 Boss 条（进度/文本/颜色整体替换）。 */
    public boolean updateBossBar(Player target, String id, String text, double progress, String color) {
        OpenDreamCorePlugin plugin = OpenDreamCoreAPI.plugin();
        if (plugin == null || id == null) {
            return false;
        }
        plugin.networkLayer().sendBossBar(new com.opendreamcore.protocol.message.BossBarSync(id,
                com.opendreamcore.protocol.message.BossBarSync.Action.UPDATE, text == null ? "" : text,
                progress, color == null ? "#E53935" : color), target);
        return true;
    }

    /** 移除 Boss 条（target null = 全体）。 */
    public boolean removeBossBar(Player target, String id) {
        OpenDreamCorePlugin plugin = OpenDreamCoreAPI.plugin();
        if (plugin == null || id == null) {
            return false;
        }
        plugin.networkLayer().sendBossBar(new com.opendreamcore.protocol.message.BossBarSync(id,
                com.opendreamcore.protocol.message.BossBarSync.Action.REMOVE, "", 0, ""), target);
        return true;
    }

    // ---- 头顶名牌 ----

    /** 给目标玩家设置头顶名牌（全体可见；文字支持颜色码）。 */
    public boolean setNameTag(String playerName, String text, String color) {
        OpenDreamCorePlugin plugin = OpenDreamCoreAPI.plugin();
        Player target = playerName == null ? null : Bukkit.getPlayerExact(playerName);
        if (plugin == null || target == null) {
            return false;
        }
        plugin.networkLayer().sendNameTag(new com.opendreamcore.protocol.message.NameTagSync(
                target.getEntityId(), text == null ? "" : text, color == null ? "#FFFFFF" : color));
        return true;
    }

    /** 移除目标玩家的名牌。 */
    public boolean removeNameTag(String playerName) {
        return setNameTag(playerName, "", "#FFFFFF");
    }

    // ---- 物品提示 ----

    /** 屏幕中央物品浮窗（图标 + 名字，尾段淡出）。 */
    public boolean showItemTip(Player player, String itemId, int count, int durationMs) {
        OpenDreamCorePlugin plugin = OpenDreamCoreAPI.plugin();
        if (plugin == null || player == null || itemId == null) {
            return false;
        }
        plugin.networkLayer().sendItemTip(player, new com.opendreamcore.protocol.message.ItemTipSync(
                itemId, Math.max(1, count), Math.max(200, durationMs)));
        return true;
    }

    // ---- 背景音乐 ----

    /** 播放背景音乐（文件在客户端 OpenDreamCore/music/ 或云端 music/；target null = 全体）。 */
    public boolean playMusic(Player target, String file, double volume, boolean loop) {
        OpenDreamCorePlugin plugin = OpenDreamCoreAPI.plugin();
        if (plugin == null || file == null) {
            return false;
        }
        if (target != null) {
            plugin.networkLayer().sendMusic(target, new com.opendreamcore.protocol.message.MusicSync(
                    com.opendreamcore.protocol.message.MusicSync.Action.PLAY, file, volume, loop));
        } else {
            for (Player online : Bukkit.getOnlinePlayers()) {
                plugin.networkLayer().sendMusic(online, new com.opendreamcore.protocol.message.MusicSync(
                        com.opendreamcore.protocol.message.MusicSync.Action.PLAY, file, volume, loop));
            }
        }
        return true;
    }

    /** 停止背景音乐（target null = 全体）。 */
    public boolean stopMusic(Player target) {
        OpenDreamCorePlugin plugin = OpenDreamCoreAPI.plugin();
        if (plugin == null) {
            return false;
        }
        if (target != null) {
            plugin.networkLayer().sendMusic(target, new com.opendreamcore.protocol.message.MusicSync(
                    com.opendreamcore.protocol.message.MusicSync.Action.STOP, "", 0, false));
        } else {
            for (Player online : Bukkit.getOnlinePlayers()) {
                plugin.networkLayer().sendMusic(online, new com.opendreamcore.protocol.message.MusicSync(
                        com.opendreamcore.protocol.message.MusicSync.Action.STOP, "", 0, false));
            }
        }
        return true;
    }

    // ---- 屏幕特效 ----

    /** 屏幕震动（强度, 时长 ms）。 */
    public boolean shake(Player player, double strength, int durationMs) {
        return effect(player, com.opendreamcore.protocol.message.UiEffect.Kind.SHAKE,
                strength, durationMs, "");
    }

    /** 闪屏（颜色, 时长 ms）。 */
    public boolean flash(Player player, String color, int durationMs) {
        return effect(player, com.opendreamcore.protocol.message.UiEffect.Kind.FLASH,
                durationMs, 0, color == null ? "#FFFFFF" : color);
    }

    /** 全屏过渡（颜色, 时长 ms）。 */
    public boolean transition(Player player, String color, int durationMs) {
        return effect(player, com.opendreamcore.protocol.message.UiEffect.Kind.TRANSITION,
                durationMs, 0, color == null ? "#000000" : color);
    }

    private boolean effect(Player player, com.opendreamcore.protocol.message.UiEffect.Kind kind,
                           double arg1, double arg2, String color) {
        OpenDreamCorePlugin plugin = OpenDreamCoreAPI.plugin();
        if (plugin == null || player == null) {
            return false;
        }
        plugin.networkLayer().sendUiEffect(player,
                new com.opendreamcore.protocol.message.UiEffect(kind, arg1, arg2, color));
        return true;
    }
}
