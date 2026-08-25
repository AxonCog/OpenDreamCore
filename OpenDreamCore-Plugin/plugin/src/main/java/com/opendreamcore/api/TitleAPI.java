package com.opendreamcore.api;

import com.opendreamcore.plugin.OpenDreamCorePlugin;
import com.opendreamcore.protocol.Protocol;
import com.opendreamcore.protocol.message.WindowTitlePush;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * 窗口标题 API：按玩家下发客户端窗口标题（打字机/轮播/随机，DreamCore ClientTitleManager 语义），
 * 或解除覆盖还原本地 branding（title.txt/title.json/icon.png）。
 *
 * <pre>
 * OpenDreamCoreAPI.title().push(player, "梦幻小屋");            // 单文本直设
 * OpenDreamCoreAPI.title().push(player, "", List.of("甲","乙"), true, false, 120, 3000, -1, true); // 打字机轮播
 * OpenDreamCoreAPI.title().reset(player);                      // 还原本地
 * </pre>
 * 配置驱动的进服自动下发见 config.yml 的 client-title 段（无需任何指令/调用）。
 * 未安装模组的原版客户端会忽略本通道，零副作用。
 */
public final class TitleAPI {

    static final TitleAPI INSTANCE = new TitleAPI();

    private TitleAPI() {
    }

    /** 单文本直设（对应 DC 运行时 WindowTitle 通道）。 */
    public boolean push(Player player, String text) {
        return send(player, WindowTitlePush.statik(text));
    }

    /**
     * 完整配置推送。
     *
     * @param text       兜底文本（titles 为空时显示）
     * @param titles     轮播序列
     * @param typewriter 打字机逐字显现
     * @param random     随机轮换（多句时随机选句；优先于打字机效果）
     * @param speed      打字机每字符毫秒
     * @param interval   每句展示时长基准毫秒
     * @param holdMs     打完停留毫秒（-1 = 取 interval）
     * @param loop       是否循环
     */
    public boolean push(Player player, String text, List<String> titles,
                        boolean typewriter, boolean random, int speed, int interval,
                        int holdMs, boolean loop) {
        if ((titles == null || titles.isEmpty()) && (text == null || text.isEmpty())) {
            return false;
        }
        return send(player, WindowTitlePush.config(text, titles, typewriter, random, speed, interval, holdMs, loop));
    }

    /** 解除覆盖：客户端还原本地 branding。 */
    public boolean reset(Player player) {
        return send(player, WindowTitlePush.reset());
    }

    /** 全体广播单文本（逐发，等价循环 push）。 */
    public void pushAll(String text) {
        for (Player p : players()) {
            push(p, text);
        }
    }

    /** 全体解除覆盖。 */
    public void resetAll() {
        for (Player p : players()) {
            reset(p);
        }
    }

    private Iterable<? extends Player> players() {
        OpenDreamCorePlugin plugin = OpenDreamCoreAPI.plugin();
        return plugin == null ? List.of() : plugin.getServer().getOnlinePlayers();
    }

    private boolean send(Player player, WindowTitlePush push) {
        OpenDreamCorePlugin plugin = OpenDreamCoreAPI.plugin();
        if (plugin == null || player == null || push == null) {
            return false;
        }
        plugin.networkLayer().send(player, Protocol.WINDOW_TITLE, push);
        return true;
    }
}
