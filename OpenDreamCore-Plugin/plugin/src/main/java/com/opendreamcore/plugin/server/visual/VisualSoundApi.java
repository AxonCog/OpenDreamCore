package com.opendreamcore.plugin.server.visual;

import com.opendreamcore.protocol.message.CustomPacket;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * SoundAPI（服务端喇叭）：按视觉规则键喊客户端放音效。
 *
 * 用法（脚本/别的插件都能吆喝）：
 *   SoundAPI.播放(玩家, "充值成功")          —— 声音规则库里的音效键
 *   SoundAPI.播放(玩家, "充值成功", 1.0, 1.2) —— 顺手把音量/音调也盖了
 *   SoundAPI.停止(玩家, "背景音乐")          —— 掐掉循环中的那个
 *
 * 和 Sound.播放(内置音效) 的分工：Sound 走服务端原生 playSound（只有注册表里
 * 的元老跟原版行为）；SoundAPI 走视觉规则库（散装音频、循环控制、跟着九系统
 * 一起热重载）。规则里没这键？那就不理你，丢一条 debug 完事。
 *
 * 通道复用 custom_packet：视觉系统是 ODC 自家能力，为一句单向指令开新协议
 * 常量不值当（协议 v1 纹丝不动）；客户端在 handleCustomPacket 里认领
 * "sound" / "sound_stop" 两个保留通道名（常量定义在 Protocol，两边一伙的）。
 */
public final class VisualSoundApi {

    /** custom_packet 里归视觉音效用的保留通道（常量定义在 Protocol，两端共用）。 */
    public static final String CHANNEL_PLAY = com.opendreamcore.protocol.Protocol.CUSTOM_SOUND;
    public static final String CHANNEL_STOP = com.opendreamcore.protocol.Protocol.CUSTOM_SOUND_STOP;

    private VisualSoundApi() {
    }

    /** 触发客户端播放：音效键按 Sounds 规则库解析（规则默认音量/音调）。 */
    public static void play(Player player, String key) {
        play(player, key, null, null);
    }

    /** 触发客户端播放：覆盖音量/音调（null = 用规则默认值）。 */
    public static void play(Player player, String key, Double volume, Double pitch) {
        if (player == null || key == null || key.trim().isEmpty()) {
            return;
        }
        StringBuilder payload = new StringBuilder(key);
        if (volume != null) {
            payload.append('|').append(volume);
        }
        if (pitch != null) {
            payload.append('|').append(pitch);
        }
        send(player, CHANNEL_PLAY, payload.toString());
    }

    /** 停止客户端循环音效（Sounds 规则里配了 loop: true 的键）。 */
    public static void stop(Player player, String key) {
        if (player == null || key == null || key.trim().isEmpty()) {
            return;
        }
        send(player, CHANNEL_STOP, key);
    }

    /** 全服广播播放（音效键相同，按各自客户端规则执行）。 */
    public static void broadcast(String key) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            play(p, key);
        }
    }

    /** 规则库是否有这个音效键（第三方插件预检用）。 */
    public static boolean hasKey(String key) {
        VisualRuleManager m = manager();
        return m != null && m.rulesOf("Sounds").containsKey(key);
    }

    /** 当前规则库的音效键集合（tab 补全/调试用）。 */
    public static java.util.Set<String> keys() {
        VisualRuleManager m = manager();
        return m == null ? java.util.Collections.emptySet() : m.rulesOf("Sounds").keySet();
    }

    // 内部

    private static VisualRuleManager manager() {
        try {
            org.bukkit.plugin.Plugin p = Bukkit.getPluginManager().getPlugin("OpenDreamCore");
            if (p instanceof com.opendreamcore.plugin.OpenDreamCorePlugin plugin) {
                return plugin.visualRules();
            }
        } catch (Exception ignored) {
            // 插件没启用/还没装载——查库失败按无规则处理
        }
        return null;
    }

    private static void send(Player player, String channel, String payload) {
        try {
            org.bukkit.plugin.Plugin p = Bukkit.getPluginManager().getPlugin("OpenDreamCore");
            if (!(p instanceof com.opendreamcore.plugin.OpenDreamCorePlugin plugin)) {
                return;
            }
            // 只有握手完成的客户端才收——裸原版客户端没有消费器，发了也是死信
            if (!plugin.networkLayer().isReady(player)) {
                return;
            }
            plugin.networkLayer().send(player,
                    com.opendreamcore.protocol.Protocol.CUSTOM_PACKET,
                    new CustomPacket(channel, payload));
        } catch (Exception e) {
            Bukkit.getLogger().warning("[OpenDreamCore][soundapi] 播放指令下发失败 " + key(player) + ": " + e);
        }
    }

    private static String key(Player p) {
        return p == null ? "?" : p.getName();
    }
}