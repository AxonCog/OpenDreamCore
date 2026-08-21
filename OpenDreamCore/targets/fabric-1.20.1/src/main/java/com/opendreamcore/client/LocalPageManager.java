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
 * 本地页面仓库：加载游戏目录 OpenDreamCore/UI/*.yaml。
 * 单机模式（无服务端下发）时用；多人模式下服务端页面优先。
 */
public final class LocalPageManager {

    private final Map<String, Page> pages = new ConcurrentHashMap<>();

    /** 扫描目录（不存在就创建空目录，不报错）。 */
    public void load(Path uiDir) {
        pages.clear();
        if (!Files.isDirectory(uiDir)) {
            try {
                Files.createDirectories(uiDir);
            } catch (IOException ignored) {
                // 目录建不出来就算了，反正没有页面
            }
            return;
        }
        List<Path> files;
        try (var stream = Files.list(uiDir)) {
            files = stream.filter(p -> p.getFileName().toString().endsWith(".yaml")).toList();
        } catch (IOException e) {
            return;
        }
        if (files.isEmpty()) {
            copyDefaults(uiDir); // 首次使用：把内置示例页面放进去
            try (var stream = Files.list(uiDir)) {
                files = stream.filter(p -> p.getFileName().toString().endsWith(".yaml")).toList();
            } catch (IOException ignored) {
                files = List.of();
            }
        }
        for (Path file : files) {
            try {
                String yaml = Files.readString(file, StandardCharsets.UTF_8);
                Map<String, Object> ir = new YamlParser().parse(yaml);
                String id = file.getFileName().toString().replaceFirst("\\.yaml$", "");
                Page page = PageSchema.build(id, ir);
                pages.put(page.id() == null ? id : page.id(), page);
            } catch (Exception e) {
                ClientController.LOGGER.warn("本地页面加载失败 {}: {}", file.getFileName(), e.toString());
            }
        }
    }

    /** 内置默认页面（mod 资源包 assets/opendreamcore/default_ui/）。 */
    private static void copyDefaults(Path uiDir) {
        String[] defaults = {"welcome.yaml", "hud.yaml", "container.yaml", "hologram.yaml"};
        for (String name : defaults) {
            try (var in = LocalPageManager.class.getResourceAsStream(
                    "/assets/opendreamcore/default_ui/" + name)) {
                if (in == null) {
                    continue;
                }
                Files.copy(in, uiDir.resolve(name));
            } catch (IOException e) {
                ClientController.LOGGER.warn("默认页面复制失败 {}: {}", name, e.toString());
            }
        }
    }

    public Page get(String pageId) {
        return pages.get(pageId);
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
