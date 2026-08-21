package com.opendreamcore.plugin.network;

import com.opendreamcore.plugin.OpenDreamCorePlugin;
import com.opendreamcore.protocol.OdcByteArrayBuf;
import com.opendreamcore.protocol.Protocol;
import com.opendreamcore.protocol.message.CustomPacket;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自定义双向通道（custom_packet）服务端注册表：
 * - 第三方插件：registerHandler(通道, (player, payload) -> ...) 接收客户端上行；
 *   send(plugin, player, 通道, 内容) 下行推送。
 * - 服务端脚本：Network.发送 / Network.广播 下行；上行分发同时发布到
 *   EventBus "custom:&lt;通道&gt;"（参数 = 玩家名, 内容），脚本用 Event.订阅 接收。
 * 分发在 Bukkit 主线程执行。
 */
public final class CustomPacketRegistry {

    private static final Map<String, java.util.function.BiConsumer<Player, String>> HANDLERS =
            new ConcurrentHashMap<>();

    private CustomPacketRegistry() {
    }

    /** 注册通道处理器（同名覆盖；null 移除）。 */
    public static void registerHandler(String channel, java.util.function.BiConsumer<Player, String> handler) {
        if (channel == null || channel.isBlank()) {
            return;
        }
        if (handler == null) {
            HANDLERS.remove(channel);
        } else {
            HANDLERS.put(channel, handler);
        }
    }

    /** 移除通道处理器。 */
    public static boolean unregisterHandler(String channel) {
        return HANDLERS.remove(channel) != null;
    }

    /** 服务端 → 客户端下行（指定玩家）。 */
    public static void send(OpenDreamCorePlugin plugin, Player player, String channel, String payload) {
        if (plugin == null || player == null || channel == null || channel.isBlank()) {
            return;
        }
        try {
            var buf = new OdcByteArrayBuf();
            new CustomPacket(channel, payload).encode(buf);
            player.sendPluginMessage(plugin, Protocol.NAMESPACE + ":" + Protocol.CUSTOM_PACKET,
                    buf.toByteArray());
        } catch (Exception e) {
            plugin.getLogger().warning("custom_packet 下行失败 (" + player.getName() + "): " + e);
        }
    }

    /** 服务端 → 客户端下行（全部在线玩家）。 */
    public static void broadcast(OpenDreamCorePlugin plugin, String channel, String payload) {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            send(plugin, p, channel, payload);
        }
    }

    /** 客户端上行分发：注册表处理器 + EventBus 脚本订阅，均切主线程执行。 */
    static void dispatch(OpenDreamCorePlugin plugin, Player player, String channel, String payload) {
        java.util.function.BiConsumer<Player, String> handler = HANDLERS.get(channel);
        if (handler == null && com.opendreamcore.script.EventBus.handlerCount("custom:" + channel) == 0) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                if (handler != null) {
                    handler.accept(player, payload);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("custom_packet 处理器异常 (" + channel + ", "
                        + player.getName() + "): " + e);
            }
            try {
                com.opendreamcore.script.EventBus.publish("custom:" + channel,
                        player.getName(), payload);
            } catch (Exception ignored) {
                // 单个订阅出错不影响其它
            }
        });
    }
}
