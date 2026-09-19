package com.opendreamcore.client.api;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 核心自带的那个源：扫 gameDir/OpenDreamCore/UI/ 下的 YAML（老壳四版）。
 *
 * 递归子目录，页面 id = 相对 UI 目录的路径去掉后缀（斜杠统一 /）：
 *   UI/shop.yaml → "shop"；UI/hud/help.yaml → "hud/help"
 * 目录不存在就顺手建个空的，不算错误。
 */
public final class YamlDirPageSource implements PageSource {

    /** 核心源的固定名字，附属模组别占。 */
    public static final String NAME = "yaml-dir";

    private static final Charset UTF8 = Charset.forName("UTF-8");

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Map<String, String> scan(Path uiDir) {
        Map<String, String> out = new LinkedHashMap<String, String>();
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
        collect(uiDir, uiDir, out);
        return out;
    }

    private void collect(Path root, Path dir, Map<String, String> out) {
        List<Path> dirs = new ArrayList<Path>();
        DirectoryStream<Path> stream = null;
        try {
            stream = Files.newDirectoryStream(dir);
            for (Path p : stream) {
                String name = p.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                if (Files.isDirectory(p)) {
                    dirs.add(p);
                } else if (name.endsWith(".yaml") || name.endsWith(".yml")) {
                    read(root, p, out);
                }
            }
        } catch (IOException ignored) {
            // 这个目录读不了就罢了，下面还能接着走
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException ignored) {
                }
            }
        }
        for (Path sub : dirs) {
            collect(root, sub, out);
        }
    }

    private void read(Path root, Path file, Map<String, String> out) {
        try {
            String id = root.relativize(file).toString().replace('\\', '/')
                    .replaceFirst("\\.(ya?ml)$", "");
            byte[] bytes = Files.readAllBytes(file);
            out.put(id, new String(bytes, UTF8));
        } catch (Exception e) {
            // 单页读失败不拦别人，日志由调用方统一记
        }
    }
}
