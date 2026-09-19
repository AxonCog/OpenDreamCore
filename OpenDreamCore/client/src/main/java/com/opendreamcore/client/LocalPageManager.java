package com.opendreamcore.client;

import com.opendreamcore.config.PageSchema;
import com.opendreamcore.config.YamlParser;
import com.opendreamcore.page.DisplayMode;
import com.opendreamcore.page.Match;
import com.opendreamcore.page.Page;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本地页面仓库：页面从 PageSourceRegistry 里登记的各个源收（核心自带的是扫
 * 游戏目录 OpenDreamCore/UI/ 的 YAML 源，递归子目录）。页面 id 就是相对 UI
 * 目录的路径（不含 .yaml 后缀）：
 *   UI/shop.yaml → "shop"
 *   UI/hud/help.yaml → "hud/help"
 * 单机模式（无服务端下发）时用；多人模式下服务端页面优先。
 */
public final class LocalPageManager {

    private final Map<String, Page> pages = new ConcurrentHashMap<>();

    /** 扫描目录（不存在就创建空目录，不报错）。 */
    public void load(Path uiDir) {
        pages.clear();
        CustomFonts.loadAll(); // 顺带重扫字体目录（/odc reload 时新字体生效）
        // 先把 themes/ 目录灌进主题库，页面构建才查得到；热重载也走这里
        try {
            com.opendreamcore.ui.theme.ThemeLibrary.get().clear();
            com.opendreamcore.ui.theme.ThemeLibrary.get()
                    .loadFromDir(uiDir.getParent() == null ? null : uiDir.getParent().resolve("themes"));
        } catch (Exception e) {
            ClientController.LOGGER.warn("主题目录加载失败: {}", e.toString());
        }
        if (!Files.isDirectory(uiDir)) {
            try {
                Files.createDirectories(uiDir);
            } catch (IOException ignored) {
                // 目录建不出来就算了，反正没有页面
            }
        }
        // 页面出处走源注册表：核心自带的 YAML 目录源排第一，附属注册的源往后追加
        // （id 撞车时后写的覆盖先写的）。某个源整体挂了不牵连别人，吞掉记日志继续。
        if (com.opendreamcore.client.api.PageSourceRegistry.sources().isEmpty()) {
            com.opendreamcore.client.api.PageSourceRegistry.register(
                    new com.opendreamcore.client.api.YamlDirPageSource());
        }
        // 两阶段：先收齐原文解析成 IR（import 模板要跨页查），再逐个展开构建
        Map<String, Map<String, Object>> irst = new java.util.LinkedHashMap<>();
        Map<String, String> raws = new java.util.LinkedHashMap<>();
        for (var source : com.opendreamcore.client.api.PageSourceRegistry.sources()) {
            Map<String, String> scanned;
            try {
                scanned = source.scan(uiDir);
            } catch (Exception ex) {
                ClientController.LOGGER.warn("页面源 {} 扫描失败: {}", source.name(), ex.toString());
                continue;
            }
            if (scanned == null) {
                continue;
            }
            for (Map.Entry<String, String> entry : scanned.entrySet()) {
                String id = entry.getKey();
                if (id == null || entry.getValue() == null) {
                    continue;
                }
                try {
                    irst.put(id, parseAuto(entry.getValue()));
                    raws.put(id, entry.getValue()); // 原文留着，布局报错要指行
                } catch (Exception ex) {
                    ClientController.LOGGER.warn("本地页面解析失败 {}: {}", id, ex.toString());
                    ClientController.chatWarnOnce("local-page:" + id,
                            "§c[OpenDreamCore] §f本地页面 " + id + " 解析失败: "
                                    + ClientController.shortReason(ex));
                }
            }
        }
        ClientController.get().registerLocalIr(irst);
        for (Map.Entry<String, Map<String, Object>> entry : irst.entrySet()) {
            try {
                Map<String, Object> ir = com.opendreamcore.page.PageImporter.expand(entry.getValue(),
                        ClientController.get()::pageIr);
                Page page = PageSchema.build(entry.getKey(), ir,
                        com.opendreamcore.config.LocationIndex.of(raws.get(entry.getKey())));
                pages.put(page.id() == null ? entry.getKey() : page.id(), page);
            } catch (Exception e) {
                ClientController.LOGGER.warn("本地页面加载失败 {}: {}", entry.getKey(), e.toString());
            }
        }
    }



    /**
     * 自动检测格式：统一经 AdapterRegistry.detect 路由（v2 规划 E4，检测单一来源）。
     * 命中 DreamCoreParser 时附带启用 方法.* 脚本桥 + 客户端运行时宿主（均幂等）。
     */
    private static Map<String, Object> parseAuto(String yaml) {
        var parser = com.opendreamcore.adapter.AdapterRegistry.detect(yaml);
        if (parser instanceof com.opendreamcore.adapter.dreamcore.DreamCoreParser) {
            // 旧格式：启用 方法.* 脚本桥 + 客户端运行时宿主（均幂等）
            com.opendreamcore.adapter.dreamcore.LegacyMethods.ensureRegistered();
            LegacyClientHost.install();
        }
        return parser != null ? parser.parse(yaml) : new YamlParser().parse(yaml);
    }

    public Page get(String pageId) {
        return pages.get(pageId);
    }

    /** 运行时注册页面（面板克隆用；不入盘，重启后由目录扫描重建）。 */
    public void add(Page page) {
        if (page != null && page.id() != null) {
            pages.put(page.id(), page);
        }
    }

    /** 按 match 找第一个命中的页面（优先级降序）。 */
    public Page match(String target, String title, DisplayMode mode) {
        java.util.List<Page> all = matchAll(target, title, mode);
        return all.isEmpty() ? null : all.get(0);
    }

    /** 按 match 全部命中（优先级降序）。HUD 多页同挂/多世界面板等"不止一张"的场景用。 */
    public java.util.List<Page> matchAll(String target, String title, DisplayMode mode) {
        java.util.List<Page> out = new java.util.ArrayList<>();
        for (Page p : pages.values()) {
            if (p.match() != null && matches(p.match(), target, title, mode)) {
                out.add(p);
            }
        }
        out.sort((a, b) -> Integer.compare(
                b.match().priority(), a.match().priority()));
        return out;
    }

    private static boolean matches(Match match, String target, String title, DisplayMode mode) {
        if (match.when() != null && !match.when().isBlank()) {
            // 表达式条件：DreamLang 求值（player.xxx + 页面变量）
            try {
                com.opendreamcore.script.Scope scope = new com.opendreamcore.script.Scope();
                var player = net.minecraft.client.Minecraft.getInstance().player;
                if (player != null) {
                    scope.assignPlayer("name", player.getName().getString());
                    scope.assignPlayer("health", (double) player.getHealth());
                    scope.assignPlayer("level", (double) player.experienceLevel);
                }
                Object result = com.opendreamcore.script.DreamLang.evaluate(match.when(), scope);
                if (!(result instanceof Boolean b) || !b) {
                    return false;
                }
            } catch (Exception e) {
                return false; // 表达式出错不匹配
            }
        }
        String m = match.target();
        if (m == null) {
            return false;
        }
        if (m.equalsIgnoreCase("hud")) {
            return mode == DisplayMode.HUD;
        }
        if (m.equalsIgnoreCase("world")) {
            return mode == DisplayMode.WORLD;
        }
        if (m.equalsIgnoreCase("screen")) {
            return mode == DisplayMode.SCREEN;
        }
        if (m.equalsIgnoreCase("inventory") || m.equalsIgnoreCase("player")) {
            return mode == DisplayMode.CONTAINER || mode == DisplayMode.SCREEN;
        }
        if (m.contains(":")) {
            return target != null && m.equalsIgnoreCase(target);
        }
        return title != null && m.equalsIgnoreCase(title);
    }

    public List<String> ids() {
        List<String> ids = new ArrayList<>(pages.keySet());
        ids.sort(String::compareTo);
        return ids;
    }
}
