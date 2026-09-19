package com.opendreamcore.client;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * "codc" 客户端本地诊断的共享逻辑。
 *
 * 命令命名空间彻底分开：模组注册 codc（纯客户端执行，任何版本
 * 都不发往服务器），服务器插件独占 odc。客户端诊断不该依赖服务器
 * 在线，也不该借服务器命令通道。
 *
 * codc 只做本地能回答的事：会话/页面清单、页面原始 YAML 落盘。
 * open/close/reload 这类服务器职责仍然归 /odc，两边互不借道。
 */
public final class Diagnostics {
    private Diagnostics() {
    }

    /** codc state：会话/页面/可见性一览，全部来自本地运行时，不发任何包。 */
    public static List<String> state(MessageDispatcher dispatcher, String activePageId) {
        List<String> out = new ArrayList<>();
        out.add("§6[ODC]§r codc 客户端本地诊断（不经过服务器；服务器命令是 /odc）");
        out.add("§7serverVersion:§r " + dispatcher.serverVersion());
        String session = activePageId == null || activePageId.isEmpty()
                ? null : MessageDispatcher.sessionOf(activePageId);
        out.add("§7activePage:§r " + (activePageId == null || activePageId.isEmpty()
                ? "§8(无)" : activePageId + " §7(session=" + (session == null ? "-" : session) + ")"));
        List<String> ids = dispatcher.pageIds();
        out.add("§7pages(" + ids.size() + "):§r "
                + (ids.isEmpty() ? "§8(无)" : String.join(", ", ids)));
        List<String> hud = new ArrayList<>(dispatcher.hudPageIds());
        out.add("§7hudPages(" + hud.size() + "):§r "
                + (hud.isEmpty() ? "§8(无)" : String.join(", ", hud)));
        out.add("§7visualPages:§r " + dispatcher.visualPageIds());
        out.add("§7screenPage:§r " + (LocalOdc.screenPageId.get().isEmpty()
                ? "§8(未打开)" : LocalOdc.screenPageId.get()));
        return out;
    }

    /** codc dump <页面id>：page_sync 原始 YAML 落盘 odc-dump/，便于离线比对。 */
    public static String dump(MessageDispatcher dispatcher, String pageId) {
        String raw = dispatcher.rawPage(pageId);
        if (raw == null) {
            return "§c[ODC]§r 页面不存在（或为视觉规则生成的页面，无原始 YAML）: " + pageId;
        }
        try {
            Path dir = Paths.get("odc-dump");
            Files.createDirectories(dir);
            String safe = pageId.replaceAll("[^A-Za-z0-9._-]", "_");
            Path file = dir.resolve(safe + ".yaml");
            Files.write(file, raw.getBytes(StandardCharsets.UTF_8));
            return "§a[ODC]§r 已写入 " + file.toAbsolutePath();
        } catch (Exception e) {
            return "§c[ODC]§r 写入失败: " + e;
        }
    }
}
