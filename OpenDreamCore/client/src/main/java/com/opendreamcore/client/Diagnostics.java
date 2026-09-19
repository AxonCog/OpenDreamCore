package com.opendreamcore.client;

import com.opendreamcore.page.Page;
import net.minecraft.client.Minecraft;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * "codc" 本地诊断的共享实现。
 *
 * 开 codc 这个名字的缘由：客户端以前把本地敲的 /odc 拦下来转给服务器执行，
 * 结果跟服务器插件的同名命令抢 TAB 补全（玩家敲一次弹两遍），
 * 被取消的本地消息还不进聊天历史。归属本来就该分开——/odc 是服务器插件的，
 * 客户端只留 codc 这个名字，回答点自己知道的事，一个包都不发。
 *
 * 措辞别写得太客气：这种坑不写明来龙去脉，下个人又会顺手加转发。
 */
public final class Diagnostics {

    private Diagnostics() {
    }

    /** codc state：会话/页面/版本一览，全从本地运行时拿，不发任何包。 */
    public static List<String> state() {
        var cc = ClientController.get();
        var out = new java.util.ArrayList<String>();
        out.add("§6[ODC]§r codc 客户端本地诊断（不经过服务器；服务器命令是 /odc）");
        out.add("  会话: " + (cc.currentSessionId() == null
                ? "§7无（当前是本地页面）§r"
                : "§a" + cc.currentSessionId() + " §7来自服务器会话§r"));
        out.add("  页面: " + (cc.isHudOpen()
                ? "§aHUD 常驻 " + cc.hudPageId()
                : (cc.isOpen() ? "已开 " + cc.currentPageId() : "§7无")));
        out.add("  本地页面: " + cc.localPages().ids().size() + " 个");
        out.add("  §7想操作服务器页面就找服务器插件的 /odc，两边互不借道§r");
        return out;
    }

    /** codc dump <页面id>：把手上的页面 YAML 落盘 odc-dump/，便于离线比对。 */
    public static String dump(String pageId) {
        // 本地页面直接读源文件；服务端下发的没有原文，只能从 UI 目录找同名文件
        Page page = ClientController.get().pageById(pageId);
        if (page == null) {
            return "§c本地没有页面 " + pageId + "（服务端下发的页面暂无反序列化原文）";
        }
        Path uiDir = Minecraft.getInstance().gameDirectory.toPath()
                .resolve("OpenDreamCore").resolve("UI");
        for (String ext : new String[]{".yaml", ".yml"}) {
            Path f = uiDir.resolve(pageId + ext);
            if (Files.isRegularFile(f)) {
                try {
                    Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve("odc-dump");
                    Files.createDirectories(dir);
                    Path out = dir.resolve(pageId.replaceAll("[^A-Za-z0-9_.-]", "_") + ".yaml");
                    Files.write(out, Files.readAllBytes(f));
                    return "§a已转存 §f" + out.toAbsolutePath();
                } catch (Exception e) {
                    return "§c转存失败: " + e.getMessage();
                }
            }
        }
        return "§c页面 " + pageId + " 在 UI 目录找不到同名 YAML 文件（服务端下发的页面没有本地原文）";
    }
}
