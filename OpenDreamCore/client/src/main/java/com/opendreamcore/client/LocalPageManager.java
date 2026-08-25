package com.opendreamcore.client;

import com.opendreamcore.config.PageSchema;
import com.opendreamcore.config.YamlParser;
import com.opendreamcore.page.DisplayMode;
import com.opendreamcore.page.Match;
import com.opendreamcore.page.Page;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本地页面仓库：加载游戏目录 OpenDreamCore/UI/ 下的 YAML 页面（递归子目录）。
 * 页面 id = 相对 UI 目录的路径（不含 .yaml 后缀）：
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
        if (!Files.isDirectory(uiDir)) {
            try {
                Files.createDirectories(uiDir);
            } catch (IOException ignored) {
                // 目录建不出来就算了，反正没有页面
            }
            return;
        }
        // 递归扫描子目录：支持 UI/hud/help.yaml → id="hud/help"
        List<Path> files = new ArrayList<>();
        try (var stream = Files.walk(uiDir)) {
            stream.filter(p -> {
                String name = p.getFileName().toString();
                return name.endsWith(".yaml") || name.endsWith(".yml");
            }).forEach(files::add);
        } catch (IOException e) {
            return;
        }
        // C7：移除原"files 为空时二次 walk"死代码（重复扫描且可能清空结果，无任何效果）
        // 两阶段：先解析全部 IR（import 模板跨页面解析需要全量），再逐个展开构建
        // 页面 id = 相对 UI 目录的路径（去掉 .yaml/.yml 后缀；斜杠统一为 /）
        Map<String, Map<String, Object>> irst = new java.util.LinkedHashMap<>();
        for (Path file : files) {
            try {
                String yaml = Files.readString(file, StandardCharsets.UTF_8);
                String id = uiDir.relativize(file).toString().replace("\\", "/")
                        .replaceFirst("\\.(ya?ml)$", "");
                irst.put(id, parseAuto(yaml));
            } catch (Exception e) {
                ClientController.LOGGER.warn("本地页面解析失败 {}: {}", file.getFileName(), e.toString());
            }
        }
        ClientController.get().registerLocalIr(irst);
        for (Map.Entry<String, Map<String, Object>> entry : irst.entrySet()) {
            try {
                Map<String, Object> ir = com.opendreamcore.page.PageImporter.expand(entry.getValue(),
                        ClientController.get()::pageIr);
                Page page = PageSchema.build(entry.getKey(), ir);
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
        return pages.values().stream()
                .filter(p -> p.match() != null && matches(p.match(), target, title, mode))
                .sorted((a, b) -> Integer.compare(
                        b.match().priority(), a.match().priority()))
                .findFirst()
                .orElse(null);
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
