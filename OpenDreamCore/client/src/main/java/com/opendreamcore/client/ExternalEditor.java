package com.opendreamcore.client;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 外置编辑器：用系统默认程序打开 YAML 文件，保存后 UiFileWatcher 自动热重载。
 *
 * 支持两种模式：
 * <ul>
 *   <li>系统默认编辑器（Desktop.edit）— 适合 .yaml 关联了 VSCode/Notepad++ 等</li>
 *   <li>指定编辑器命令（options 编辑器路径）— 适合指定 IDE 打开</li>
 * </ul>
 *
 * 工作流：
 * <ol>
 *   <li>/odc edit shop → 游戏 UI 高亮元素 + 文件已就绪</li>
 *   <li>/odc edit external shop → 系统编辑器打开 shop.yaml</li>
 *   <li>在外置编辑器中修改并保存</li>
 *   <li>UiFileWatcher 监听到文件变化 → 自动热重载</li>
 *   <li>游戏内页面实时刷新</li>
 * </ol>
 */
public final class ExternalEditor {

    public static final Logger LOGGER = LogUtils.getLogger();

    /** YAML 模板（新建页面时用）。 */
    private static final String TEMPLATE = """
            # OpenDreamCore 页面 - 由外置编辑器创建
            # 文档: https://github.com/opendreamcore
            # 保存后游戏自动热重载（UiFileWatcher）

            title: "新页面"
            display: screen
            background: "0xA0000000"

            # 变量区
            # vars:
            #   count: 0

            # 元素区
            # hello:
            #   type: text
            #   x: "window.width / 2 - 50"
            #   y: "window.height / 2 - 10"
            #   text: "Hello World"
            #   fontSize: 16
            #   color: "#FFFFFF"
            """;

    private ExternalEditor() {
    }

    /**
     * 确保 YAML 文件存在：存在则返回路径，不存在则用模板创建。
     * 支持子路径：pageId="hud/help" → OpenDreamCore/UI/hud/help.yaml
     *
     * @param pageId 页面 id（不含扩展名，支持斜杠子路径）
     * @return YAML 文件路径（已存在或已创建）
     */
    public static Path ensureFile(String pageId) {
        Path file = findFile(pageId);
        if (!Files.exists(file)) {
            try {
                Files.createDirectories(file.getParent());
                Files.writeString(file, TEMPLATE, StandardCharsets.UTF_8);
                LOGGER.info("已创建页面文件: {}", file);
            } catch (IOException e) {
                LOGGER.error("创建页面文件失败: {}", e.toString());
            }
        }
        return file;
    }

    /**
     * 用系统默认程序打开 YAML 文件（外置编辑器）。
     *
     * @param pageId 页面 id
     * @return true = 打开成功
     */
    public static boolean open(String pageId) {
        Path file = ensureFile(pageId);
        if (!Files.exists(file)) {
            return false;
        }
        return openFile(file);
    }

    /**
     * 用指定编辑器命令打开文件（如 VSCode: code, Notepad++: notepad++）。
     *
     * @param editorCmd 编辑器命令（如 "code", "notepad++", "subl"）
     * @param pageId    页面 id
     * @return true = 打开成功
     */
    public static boolean openWith(String editorCmd, String pageId) {
        Path file = ensureFile(pageId);
        if (!Files.exists(file)) {
            return false;
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(editorCmd, file.toString());
            pb.directory(file.getParent().toFile());
            pb.inheritIO();
            Process p = pb.start();
            // 不等待：编辑器应该保持打开
            return true;
        } catch (IOException e) {
            LOGGER.warn("外置编辑器启动失败 ({} {}): {}", editorCmd, file, e.toString());
            return false;
        }
    }

    /**
     * 用 Desktop API 打开文件（跨平台系统默认程序）。
     */
    private static boolean openFile(Path file) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();
                if (desktop.isSupported(Desktop.Action.OPEN)) {
                    desktop.open(file.toFile());
                    return true;
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Desktop.open 失败: {}", e.toString());
        }
        // Fallback: 尝试平台特定命令
        String os = System.getProperty("os.name", "").toLowerCase();
        try {
            ProcessBuilder pb;
            if (os.contains("win")) {
                // Windows: 用 rundll32 shell32.dll OpenAs
                pb = new ProcessBuilder("rundll32.exe", "shell32.dll,OpenAs_RunDLL",
                        file.toString());
            } else if (os.contains("mac")) {
                pb = new ProcessBuilder("open", file.toString());
            } else {
                pb = new ProcessBuilder("xdg-open", file.toString());
            }
            pb.start();
            return true;
        } catch (IOException e) {
            LOGGER.error("无法打开外置编辑器: {}", e.toString());
            return false;
        }
    }

    /**
     * 在游戏目录下查找页面文件路径（支持 .yaml 和 .yml，支持子路径）。
     * pageId="hud/help" → OpenDreamCore/UI/hud/help.yaml
     *
     * @param pageId 页面 id（支持斜杠子路径）
     * @return 文件路径（优先 .yaml，其次 .yml；都不存在返回 .yaml 路径供创建）
     */
    public static Path findFile(String pageId) {
        Path uiDir = Minecraft.getInstance().gameDirectory.toPath()
                .resolve("OpenDreamCore").resolve("UI");
        // 支持斜杠分隔子路径（hud/help → hud/help.yaml）
        String subPath = pageId.replace("\\", "/");
        Path yaml = uiDir.resolve(subPath + ".yaml");
        if (Files.exists(yaml)) return yaml;
        Path yml = uiDir.resolve(subPath + ".yml");
        if (Files.exists(yml)) return yml;
        return yaml; // 返回 .yaml 路径供创建
    }

    /**
     * 获取 UI 目录路径。
     */
    public static Path uiDir() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("OpenDreamCore").resolve("UI");
    }

    /**
     * 从文件路径提取页面 id（相对 UI 目录，去掉 .yaml/.yml 后缀）。
     */
    public static String pageIdFromFile(Path file) {
        Path uiDir = uiDir();
        if (!file.startsWith(uiDir)) return null;
        String relative = uiDir.relativize(file).toString().replace("\\", "/");
        return relative.replaceFirst("\\.(ya?ml)$", "");
    }
}
