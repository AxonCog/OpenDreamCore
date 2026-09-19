package com.opendreamcore.client.api;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 核心自带的那个源：扫游戏目录 OpenDreamCore/UI/ 下的 YAML。
 *
 * 递归子目录，页面 id = 相对 UI 目录的路径去掉后缀（斜杠统一 /）：
 *   UI/shop.yaml → "shop"；UI/hud/help.yaml → "hud/help"
 * 目录不存在就顺手建一个空目录，不算错误。
 */
public final class YamlDirPageSource implements PageSource {

    /** 核心源的固定名字，附属模组别占。 */
    public static final String NAME = "yaml-dir";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Map<String, String> scan(Path uiDir) {
        Map<String, String> out = new LinkedHashMap<>();
        if (uiDir == null) {
            return out;
        }
        if (!Files.isDirectory(uiDir)) {
            try {
                Files.createDirectories(uiDir);
            } catch (IOException ignored) {
                // 目录建不出来就算了，反正没有页面
            }
            return out;
        }
        List<Path> files = new ArrayList<>();
        try (var stream = Files.walk(uiDir)) {
            stream.filter(p -> {
                String name = p.getFileName().toString();
                return name.endsWith(".yaml") || name.endsWith(".yml");
            }).forEach(files::add);
        } catch (IOException e) {
            return out;
        }
        for (Path file : files) {
            try {
                String id = uiDir.relativize(file).toString().replace("\\", "/")
                        .replaceFirst("\\.(ya?ml)$", "");
                out.put(id, Files.readString(file, StandardCharsets.UTF_8));
            } catch (Exception e) {
                // 单页读失败不拦别人，日志由调用方统一记
            }
        }
        return out;
    }
}
