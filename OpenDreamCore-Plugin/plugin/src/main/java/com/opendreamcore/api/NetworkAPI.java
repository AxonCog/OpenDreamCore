package com.opendreamcore.api;

import com.opendreamcore.plugin.OpenDreamCorePlugin;
import com.opendreamcore.plugin.network.CustomPacketRegistry;
import org.bukkit.entity.Player;

import java.util.function.BiConsumer;

/**
 * 自定义双向通道 API：附属插件和装了模组的客户端之间直接传话。
 *
 * 走的是协议里的 custom_packet（一条通用的字符串通道），两端都不用自己碰网络层：
 * <pre>
 * // 下行：推给某个玩家（null = 全服广播）
 * OpenDreamCoreAPI.network().sendCustom(player, "dreamcore:shop", "{&quot;item&quot;:&quot;剑&quot;}");
 * OpenDreamCoreAPI.network().sendCustom("dreamcore:notice", "整点开工");
 *
 * // 上行：客户端用脚本 Network.发送(通道, 内容) 发上来，这边接收
 * OpenDreamCoreAPI.network().onCustom("dreamcore:report", (player, payload) -> { ... });
 * </pre>
 * 客户端侧对应 Network.订阅(通道, lambda)；Java 附属模组走 api 包的 BridgeEvents
 * （话题名 custom:通道）。两边通道名保持一致即可，推荐 dreamcore:xxx 前缀。
 *
 * 收消息在 Bukkit 主线程回调，直接改世界/发库存都安全。
 */
public final class NetworkAPI {

    static final NetworkAPI INSTANCE = new NetworkAPI();

    private NetworkAPI() { }

    /** 下行给指定玩家。插件没加载或参数为空返回 false。 */
    public boolean sendCustom(Player player, String channel, String payload) {
        OpenDreamCorePlugin plugin = OpenDreamCoreAPI.plugin();
        if (plugin == null || player == null || channel == null || channel.trim().isEmpty()) {
            return false;
        }
        CustomPacketRegistry.send(plugin, player, channel, payload == null ? "" : payload);
        return true;
    }

    /** 下行广播给所有在线玩家，返回发出的条数。 */
    public int broadcastCustom(String channel, String payload) {
        OpenDreamCorePlugin plugin = OpenDreamCoreAPI.plugin();
        if (plugin == null || channel == null || channel.trim().isEmpty()) {
            return 0;
        }
        int n = 0;
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            CustomPacketRegistry.send(plugin, p, channel, payload == null ? "" : payload);
            n++;
        }
        return n;
    }

    /**
     * 接收客户端上行（同名覆盖，传 null 移除）。
     * 回调参数：发消息的那个玩家 + 内容字符串。
     */
    public void onCustom(String channel, BiConsumer<Player, String> handler) {
        CustomPacketRegistry.registerHandler(channel, handler);
    }

    /** 取消接收，返回是否真删了。 */
    public boolean offCustom(String channel) {
        return CustomPacketRegistry.unregisterHandler(channel);
    }
}
