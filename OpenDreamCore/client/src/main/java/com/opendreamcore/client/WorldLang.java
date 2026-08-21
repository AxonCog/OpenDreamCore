package com.opendreamcore.client;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 世界面板多语言：`{lang.键名}` 占位符 → 客户端语言文件。
 * 查找顺序：游戏目录 OpenDreamCore/lang/&lt;locale&gt;.properties →
 * 模组资源 assets/opendreamcore/lang/&lt;locale&gt;.properties →
 * en_us 回退 → 找不到保留原文。
 */
public final class WorldLang {

    private static final ConcurrentHashMap<String, String> CACHE = new ConcurrentHashMap<>();
    private static String loadedLocale = "";
    private static boolean triedDefault;

    private WorldLang() {
    }

    /** 替换文本中的 {lang.xxx} 占位符。 */
    public static String resolve(String text) {
        if (text == null || !text.contains("{lang.")) {
            return text;
        }
        ensureLoaded();
        var matcher = java.util.regex.Pattern.compile("\\{lang\\.([\\w.\\-]+)\\}").matcher(text);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String value = CACHE.get(matcher.group(1));
            matcher.appendReplacement(sb,
                    java.util.regex.Matcher.quoteReplacement(value == null ? matcher.group() : value));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static void ensureLoaded() {
        String locale = currentLocale();
        if (loadedLocale.equals(locale) && triedDefault) {
            return;
        }
        loadedLocale = locale;
        triedDefault = true;
        CACHE.clear();
        load(locale);
        if (!"en_us".equals(locale)) {
            load("en_us"); // 回退
        }
    }

    private static String currentLocale() {
        try {
            var mc = net.minecraft.client.Minecraft.getInstance();
            var manager = mc.getLanguageManager();
            return manager == null ? "en_us" : manager.getSelected();
        } catch (Exception e) {
            return "en_us";
        }
    }

    private static void load(String locale) {
        Properties props = new Properties();
        boolean any = false;
        // 1) 游戏目录 OpenDreamCore/lang/<locale>.properties（作者自定义覆盖）
        try {
            Path file = net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath()
                    .resolve("OpenDreamCore").resolve("lang").resolve(locale + ".properties");
            if (Files.isRegularFile(file)) {
                try (InputStream in = Files.newInputStream(file)) {
                    props.load(in);
                    any = true;
                }
            }
        } catch (Exception ignored) {
        }
        // 2) 模组资源 assets/opendreamcore/lang/<locale>.properties
        if (!any) {
            try (InputStream in = WorldLang.class.getResourceAsStream(
                    "/assets/opendreamcore/lang/" + locale + ".properties")) {
                if (in != null) {
                    props.load(in);
                    any = true;
                }
            } catch (Exception ignored) {
            }
        }
        if (any) {
            props.forEach((k, v) -> CACHE.put(String.valueOf(k), String.valueOf(v)));
        }
    }
}
