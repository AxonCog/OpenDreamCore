package com.opendreamcore.plugin.api;

import com.opendreamcore.plugin.OpenDreamCorePlugin;
import com.opendreamcore.plugin.network.CustomPacketRegistry;
import org.bukkit.entity.Player;

/**
 * D3 服务端下发链路：向客户端推送自定义材质包安装指令。
 *
 * 客户端经保留通道 odc/pack 接收后，在后台完成下载/解压（支持密码），
 * 注入回主线程执行，结果反馈至聊天栏。
 *
 * 用法（附属插件）：
 * <pre>
 *     PackAPI.push(player, "https://example.com/pack.zip", null, true);
 *     PackAPI.pushAll(Bukkit.getOnlinePlayers(), "plugins/MyPlugin/packs/vip.zip", "123456", true);
 * </pre>
 */
public final class PackAPI {

    /** 保留通道名（与客户端 ClientController 拦截点约定一致）。 */
    public static final String CHANNEL = "odc/pack";

    private PackAPI() {
    }

    /**
     * 向单个玩家推送材质包安装指令。
     *
     * @param player   目标玩家（需已装 OpenDreamCore 客户端）
     * @param spec     https url 或服务端本地路径（路径仅对装在同机的玩家有意义；
     *                 远程分发一律用 https url）
     * @param password 加密 zip 密码（可空）
     * @param top      置顶覆盖
     */
    public static boolean push(Player player, String spec, String password, boolean top) {
        try {
            CustomPacketRegistry.send(com.opendreamcore.plugin.OpenDreamCorePlugin.get(),
                    player, CHANNEL, toJson(spec, password, top));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 广播给所有在线玩家。
     *
     * @see #push
     */
    public static int pushAll(Iterable<Player> players, String spec, String password, boolean top) {
        int n = 0;
        for (Player p : players) {
            if (push(p, spec, password, top)) {
                n++;
            }
        }
        return n;
    }

    private static String toJson(String spec, String password, boolean top) {
        return "{\"spec\":" + quote(spec)
                + ",\"password\":" + (password == null ? "null" : quote(password))
                + ",\"top\":" + top + "}";
    }

    private static String quote(String s) {
        String escaped = s.replace("\\", "\\\\").replace("\"", "\\\"");
        return "\"" + escaped + "\"";
    }
}
