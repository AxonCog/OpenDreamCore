package com.opendreamcore.client;

import com.mojang.logging.LogUtils;
import com.opendreamcore.page.Element;
import com.opendreamcore.page.Page;
import com.opendreamcore.protocol.message.PageControl;
import com.opendreamcore.protocol.message.Ready;
import com.opendreamcore.protocol.message.ReadyAck;
import com.opendreamcore.protocol.message.UiEvent;
import com.opendreamcore.ui.LayoutEngine;
import com.opendreamcore.ui.RenderNode;
import com.opendreamcore.ui.UiSession;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 世界面板背景子系统。
 * 调色板 / 渐变 / 背景预设 / 随机背景 / 背景键值编辑。
 * 仅同包可见；世界页面与编辑状态经 {@link ClientController} 单例访问。
 */
final class WorldBackgroundEditor {
    private static final WorldBackgroundEditor INSTANCE = new WorldBackgroundEditor();

    /** 子系统入口（单例）。 */
    static WorldBackgroundEditor get() {
        return INSTANCE;
    }

    /** ClientController 单例（世界页面/编辑状态/undo 栈入口）。 */
    private final ClientController cc = ClientController.get();

    public void setWorldPanelBackground(String colorOrNull) {        if (cc.worldPage == null) {
            return;
        }
        cc.pushWorldBackgroundUndo("背景: 颜色", "bg:color");
        Map<String, Object> options = cc.worldPage.options();
        Map<String, Object> world = new java.util.LinkedHashMap<String, Object>();
        if (options.get("world") instanceof Map<?, ?> w) {
            w.forEach((k, v) -> world.put(String.valueOf(k), v));
        }
        if (colorOrNull == null) {
            world.remove("background");
        } else {
            world.put("background", colorOrNull);
            cc.worldRecentBg.remove(colorOrNull);
            cc.worldRecentBg.add(0, colorOrNull);
            while (cc.worldRecentBg.size() > 2) {
                cc.worldRecentBg.remove(cc.worldRecentBg.size() - 1);
            }
        }
        options.put("world", world);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§b[OpenDreamCore] §f面板背景: "
                        + (colorOrNull == null ? "无（移除）" : colorOrNull)
                        + "（运行时生效；页面 world 段持久化需在 YAML 写回）"), false);
    }

    private static java.nio.file.Path bgPaletteFile() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("OpenDreamCore").resolve("UI").resolve("_bg_palette.json");
    }

    public java.util.List<String> worldPaletteColors() {
        java.util.List<String> out = new java.util.ArrayList<>();
        try {
            java.nio.file.Path f = bgPaletteFile();
            if (java.nio.file.Files.exists(f)) {
                String body = java.nio.file.Files.readString(f).trim();
                if (body.startsWith("[") && body.endsWith("]")) {
                    String inner = body.substring(1, body.length() - 1);
                    for (String part : inner.split(",")) {
                        String t = part.trim().replace("\"", "");
                        if (t.matches("#[0-9a-fA-F]{6,8}")) {
                            out.add(t.toUpperCase(java.util.Locale.ROOT));
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private void saveBgPalette(java.util.List<String> colors) {
        try {
            java.nio.file.Path f = bgPaletteFile();
            java.nio.file.Files.createDirectories(f.getParent());
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < colors.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append('"').append(colors.get(i)).append('"');
            }
            sb.append(']');
            java.nio.file.Files.writeString(f, sb.toString());
        } catch (Exception e) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§c[OpenDreamCore] §f色板保存失败: " + e.getMessage()), false);
        }
    }

    public void pinWorldPaletteColor(String hex) {
        if (hex == null) {
            return;
        }
        String h = hex.toUpperCase(java.util.Locale.ROOT);
        java.util.List<String> colors = worldPaletteColors();
        if (colors.contains(h)) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§e[OpenDreamCore] §f" + h + " 已在收藏色板"), false);
            return;
        }
        colors.add(0, h);
        while (colors.size() > 16) {
            colors.remove(colors.size() - 1);
        }
        saveBgPalette(colors);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§a[OpenDreamCore] §f已收藏 " + h
                        + "（自定义色板 " + colors.size() + "/16）"), false);
    }

    public void removeWorldPaletteColor(String hex) {
        if (hex == null) {
            return;
        }
        java.util.List<String> colors = worldPaletteColors();
        if (colors.remove(hex.toUpperCase(java.util.Locale.ROOT))) {
            saveBgPalette(colors);
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§e[OpenDreamCore] §f已移除收藏 " + hex), false);
        }
    }

    public void exportWorldPalette() {
        java.util.List<String> colors = worldPaletteColors();
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < colors.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(colors.get(i)).append('"');
        }
        sb.append(']');
        Minecraft.getInstance().keyboardHandler.setClipboard(sb.toString());
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§a[OpenDreamCore] §f收藏色板已导出（" + colors.size()
                        + " 色）到剪贴板"), false);
    }

    public void importWorldPalette() {
        String text = Minecraft.getInstance().keyboardHandler.getClipboard();
        if (text == null || text.isBlank()) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§c[OpenDreamCore] §f剪贴板为空"), false);
            return;
        }
        try {
            Object parsed = new org.yaml.snakeyaml.Yaml().load(text);
            if (!(parsed instanceof List<?> l)) {
                Minecraft.getInstance().player.displayClientMessage(
                        Component.literal("§c[OpenDreamCore] §f剪贴板不是色板数组（[\"#RRGGBB\",...]）"), false);
                return;
            }
            java.util.List<String> colors = worldPaletteColors();
            int added = 0;
            for (Object o : l) {
                String t = String.valueOf(o).trim().toUpperCase(java.util.Locale.ROOT);
                if (t.matches("#[0-9A-F]{6,8}") && !colors.contains(t)) {
                    colors.add(t);
                    added++;
                }
            }
            while (colors.size() > 16) {
                colors.remove(colors.size() - 1);
            }
            saveBgPalette(colors);
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§a[OpenDreamCore] §f已导入 " + added
                            + " 个收藏色（共 " + colors.size() + "/16）"), false);
        } catch (Exception e) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§c[OpenDreamCore] §f色板导入失败: " + e.getMessage()), false);
        }
    }

    public void cycleWorldBackgroundAlpha() {        if (cc.worldPage == null) {
            return;
        }
        Map<String, Object> options = cc.worldPage.options();
        Map<String, Object> world = new java.util.LinkedHashMap<String, Object>();
        if (options.get("world") instanceof Map<?, ?> w) {
            w.forEach((k, v) -> world.put(String.valueOf(k), v));
        }
        Object bgObj = world.get("background");
        Map<String, Object> bg;
        boolean plain = false;
        if (bgObj instanceof Map<?, ?> bm) {
            bg = new java.util.LinkedHashMap<String, Object>();
            bm.forEach((k, v) -> bg.put(String.valueOf(k), v));
        } else if (bgObj != null) {
            bg = new java.util.LinkedHashMap<String, Object>();
            bg.put("color", String.valueOf(bgObj));
            plain = true;
        } else {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§c[OpenDreamCore] §f面板无背景色，无法调透明度（先设背景色）"), false);
            return;
        }
        Object colorObj = bg.get("color");
        String hex = colorObj == null ? "" : String.valueOf(colorObj).trim();
        if (!hex.matches("#[0-9a-fA-F]{6,8}")) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§c[OpenDreamCore] §f背景色格式需 #RRGGBB 或 #AARRGGBB"), false);
            return;
        }
        String body = hex.substring(1);
        String alphaPart = body.length() == 8 ? body.substring(0, 2) : "FF";
        String rgb = body.length() == 8 ? body.substring(2) : body;
        String next = switch (alphaPart.toUpperCase(java.util.Locale.ROOT)) {
            case "FF" -> "CC";
            case "CC" -> "99";
            case "99" -> "66";
            case "66" -> "33";
            default -> "FF";
        };
        String nextHex = "#" + next + rgb;
        cc.pushWorldBackgroundUndo("背景: 色透明度", "bg:bgalpha");
        bg.put("color", nextHex);
        world.put("background", bg);
        options.put("world", world);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§b[OpenDreamCore] §f背景色透明度: " + next
                        + " → " + nextHex + "（AARRGGBB；可 Ctrl+Z 撤）"), false);
    }

    public boolean worldPanelGradientHorizontal() {
        if (cc.worldPage == null) {
            return false;
        }
        Object worldObj = cc.worldPage.options().get("world");
        if (!(worldObj instanceof Map<?, ?> w)) {
            return false;
        }
        Object bg = w.get("background");
        return bg instanceof Map<?, ?> bm
                && "horizontal".equals(String.valueOf(bm.get("gradientDir")));
    }

    public void cycleWorldPanelGradientDir() {
        if (cc.worldPage == null) {
            return;
        }
        Map<String, Object> options = cc.worldPage.options();
        Map<String, Object> world = new java.util.LinkedHashMap<String, Object>();
        if (options.get("world") instanceof Map<?, ?> w) {
            w.forEach((k, v) -> world.put(String.valueOf(k), v));
        }
        Object bgObj = world.get("background");
        Map<String, Object> bg;
        if (bgObj instanceof Map<?, ?> bm) {
            bg = new java.util.LinkedHashMap<String, Object>();
            bm.forEach((k, v) -> bg.put(String.valueOf(k), v));
        } else if (bgObj != null) {
            bg = new java.util.LinkedHashMap<String, Object>();
            bg.put("color", String.valueOf(bgObj));
        } else {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§c[OpenDreamCore] §f面板无背景，无法设渐变方向（先设背景+渐变）"), false);
            return;
        }
        cc.pushWorldBackgroundUndo("背景: 渐变方向", "bg:gradientdir");
        boolean horizontal = worldPanelGradientHorizontal();
        if (horizontal) {
            bg.remove("gradientDir");
        } else {
            bg.put("gradientDir", "horizontal");
        }
        world.put("background", bg);
        options.put("world", world);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§b[OpenDreamCore] §f面板背景渐变方向: "
                        + (horizontal ? "上下" : "左右（左 color → 右 gradient）")
                        + "（运行时生效；页面 world 段持久化需在 YAML 写回）"), false);
    }

    public void swapWorldPanelGradientColors() {
        if (cc.worldPage == null) {
            return;
        }
        Map<String, Object> options = cc.worldPage.options();
        Map<String, Object> world = new java.util.LinkedHashMap<String, Object>();
        if (options.get("world") instanceof Map<?, ?> w) {
            w.forEach((k, v) -> world.put(String.valueOf(k), v));
        }
        Object bgObj = world.get("background");
        if (!(bgObj instanceof Map<?, ?> bm)) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§c[OpenDreamCore] §f面板无背景配置，无法互换渐变双色"), false);
            return;
        }
        Map<String, Object> bg = new java.util.LinkedHashMap<String, Object>();
        bm.forEach((k, v) -> bg.put(String.valueOf(k), v));
        if (bg.get("color") == null || bg.get("gradient") == null) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§c[OpenDreamCore] §f需同时有 color 与 gradient 才能互换"), false);
            return;
        }
        cc.pushWorldBackgroundUndo("背景: 渐变互换", "bg:swap");
        Object tmp = bg.get("color");
        bg.put("color", bg.get("gradient"));
        bg.put("gradient", tmp);
        world.put("background", bg);
        options.put("world", world);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§b[OpenDreamCore] §f渐变双色已互换: "
                        + bg.get("color") + " ⇄ " + bg.get("gradient")
                        + "（运行时生效；页面 world 段持久化需在 YAML 写回）"), false);
    }

    public String worldPanelGradientMid() {
        if (cc.worldPage == null) {
            return null;
        }
        Object worldObj = cc.worldPage.options().get("world");
        if (!(worldObj instanceof Map<?, ?> w)) {
            return null;
        }
        Object bg = w.get("background");
        if (bg instanceof Map<?, ?> bm && bm.get("gradientMid") != null) {
            return String.valueOf(bm.get("gradientMid"));
        }
        return null;
    }

    public void cycleWorldPanelGradientMid() {
        if (cc.worldPage == null) {
            return;
        }
        Map<String, Object> options = cc.worldPage.options();
        Map<String, Object> world = new java.util.LinkedHashMap<String, Object>();
        if (options.get("world") instanceof Map<?, ?> w) {
            w.forEach((k, v) -> world.put(String.valueOf(k), v));
        }
        Object bgObj = world.get("background");
        Map<String, Object> bg;
        if (bgObj instanceof Map<?, ?> bm) {
            bg = new java.util.LinkedHashMap<String, Object>();
            bm.forEach((k, v) -> bg.put(String.valueOf(k), v));
        } else if (bgObj != null) {
            bg = new java.util.LinkedHashMap<String, Object>();
            bg.put("color", String.valueOf(bgObj));
        } else {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§c[OpenDreamCore] §f面板无背景，无法设渐变中段色（先设背景+渐变）"), false);
            return;
        }
        cc.pushWorldBackgroundUndo("背景: 渐变中段色", "bg:mid");
        Object cur = bg.get("gradientMid");
        String next;
        if (cur == null) {
            next = "#0D1B2A";
        } else if ("#0D1B2A".equalsIgnoreCase(String.valueOf(cur))) {
            next = "#3A4A66";
        } else {
            next = null;
        }
        if (next == null) {
            bg.remove("gradientMid");
        } else {
            bg.put("gradientMid", next);
        }
        world.put("background", bg);
        options.put("world", world);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§b[OpenDreamCore] §f渐变中段色: "
                        + (next == null ? "无（双色渐变）" : next)
                        + "（运行时生效；页面 world 段持久化需在 YAML 写回）"), false);
    }

    public void cycleWorldPanelGradientMidPos() {
        if (cc.worldPage == null) {
            return;
        }
        Map<String, Object> options = cc.worldPage.options();
        Map<String, Object> world = new java.util.LinkedHashMap<String, Object>();
        if (options.get("world") instanceof Map<?, ?> w) {
            w.forEach((k, v) -> world.put(String.valueOf(k), v));
        }
        Object bgObj = world.get("background");
        Map<String, Object> bg;
        if (bgObj instanceof Map<?, ?> bm) {
            bg = new java.util.LinkedHashMap<String, Object>();
            bm.forEach((k, v) -> bg.put(String.valueOf(k), v));
        } else if (bgObj != null) {
            bg = new java.util.LinkedHashMap<String, Object>();
            bg.put("color", String.valueOf(bgObj));
        } else {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§c[OpenDreamCore] §f面板无背景，无法设渐变中段位置（先设背景+中段色）"), false);
            return;
        }
        if (bg.get("gradientMid") == null) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§c[OpenDreamCore] §f需先设渐变中段色（Ctrl+点击 向 按钮）"), false);
            return;
        }
        cc.pushWorldBackgroundUndo("背景: 渐变中段位置", "bg:midpos");
        double cur = bg.get("gradientMidPos") instanceof Number n ? n.doubleValue() : 0.5;
        double next = cur < 0.4 ? 0.5 : cur < 0.6 ? 0.7 : 0.3;
        bg.put("gradientMidPos", Math.round(next * 10) / 10.0);
        world.put("background", bg);
        options.put("world", world);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§b[OpenDreamCore] §f渐变中段位置: " + next
                        + "（运行时生效；页面 world 段持久化需在 YAML 写回）"), false);
    }

    public void cycleWorldPanelGradientPreset() {
        if (cc.worldPage == null) {
            return;
        }
        String[][] presets = {
                {"深蓝", "#2A3A52", "#0D1B2A"},
                {"暗金", "#3E2723", "#FF8F00"},
                {"赛博青", "#004D40", "#00BCD4"},
                {"暖橙", "#4E342E", "#FF7043"}};
        Map<String, Object> options = cc.worldPage.options();
        Map<String, Object> world = new java.util.LinkedHashMap<String, Object>();
        if (options.get("world") instanceof Map<?, ?> w) {
            w.forEach((k, v) -> world.put(String.valueOf(k), v));
        }
        Object bgObj = world.get("background");
        Map<String, Object> bg;
        if (bgObj instanceof Map<?, ?> bm) {
            bg = new java.util.LinkedHashMap<String, Object>();
            bm.forEach((k, v) -> bg.put(String.valueOf(k), v));
        } else {
            bg = new java.util.LinkedHashMap<String, Object>();
        }
        String curColor = bg.get("color") == null ? "" : String.valueOf(bg.get("color")).toUpperCase(java.util.Locale.ROOT);
        String curGrad = bg.get("gradient") == null ? "" : String.valueOf(bg.get("gradient")).toUpperCase(java.util.Locale.ROOT);
        int idx = 0;
        for (int i = 0; i < presets.length; i++) {
            if (curColor.equals(presets[i][1].toUpperCase(java.util.Locale.ROOT))
                    && curGrad.equals(presets[i][2].toUpperCase(java.util.Locale.ROOT))) {
                idx = (i + 1) % presets.length;
                break;
            }
        }
        cc.pushWorldBackgroundUndo("背景: 渐变预设", "bg:preset");
        bg.put("color", presets[idx][1]);
        bg.put("gradient", presets[idx][2]);
        bg.remove("gradientMid");
        bg.remove("gradientMidPos");
        world.put("background", bg);
        options.put("world", world);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§b[OpenDreamCore] §f渐变预设: " + presets[idx][0]
                        + "（" + presets[idx][1] + " → " + presets[idx][2] + "）"), false);
    }

    public String worldPanelBackgroundColor() {
        if (cc.worldPage == null) {
            return null;
        }
        Object worldObj = cc.worldPage.options().get("world");
        if (!(worldObj instanceof Map<?, ?> w)) {
            return null;
        }
        Object bg = w.get("background");
        if (bg == null) {
            return null;
        }
        Object color = bg instanceof Map<?, ?> bm ? bm.get("color") : bg;
        if (color == null) {
            return null;
        }
        String hex = String.valueOf(color).trim().toUpperCase(java.util.Locale.ROOT);
        if (hex.length() == 9) {
            hex = hex.substring(0, 7); // 去 alpha
        }
        return hex.matches("#[0-9A-F]{6}") ? hex : null;
    }

    public String worldPanelGradientSummary() {
        if (cc.worldPage == null) {
            return null;
        }
        Object worldObj = cc.worldPage.options().get("world");
        if (!(worldObj instanceof Map<?, ?> w)) {
            return null;
        }
        Object bg = w.get("background");
        if (!(bg instanceof Map<?, ?> bm)) {
            return null;
        }
        Object color = bm.get("color");
        Object grad = bm.get("gradient");
        if (color == null || grad == null) {
            return null;
        }
        return String.valueOf(color) + "→" + grad;
    }

    public String worldBackgroundYaml() {
        if (cc.worldPage == null) {
            return null;
        }
        Object worldObj = cc.worldPage.options().get("world");
        if (!(worldObj instanceof Map<?, ?> w)) {
            return null;
        }
        Object bg = w.get("background");
        if (bg == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder("background:\n");
        if (bg instanceof Map<?, ?> bm) {
            for (java.util.Map.Entry<?, ?> e : bm.entrySet()) {
                Object v = e.getValue();
                String vs;
                if (v instanceof Number) {
                    vs = String.valueOf(v);
                } else if (v instanceof Boolean) {
                    vs = String.valueOf(v);
                } else {
                    vs = "'" + String.valueOf(v) + "'";
                }
                sb.append("  ").append(e.getKey()).append(": ").append(vs).append('\n');
            }
        } else {
            sb.append("  color: '").append(bg).append("'\n");
        }
        return sb.toString();
    }

    public void pasteWorldBackgroundFromClipboard() {
        if (cc.worldPage == null) {
            return;
        }
        String text = Minecraft.getInstance().keyboardHandler.getClipboard();
        if (text == null || text.isBlank()) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§c[OpenDreamCore] §f剪贴板为空"), false);
            return;
        }
        Map<String, Object> bg = new java.util.LinkedHashMap<>();
        boolean any = false;
        String[] lines = text.split("\\R");
        // 无 background: 头时视为整段即背景块（Ctrl+C 复制的是 background: 开头，两种都支持）
        int start = 0;
        if (lines.length > 0 && lines[0].trim().startsWith("background:")) {
            start = 1;
        }
        for (int i = start; i < lines.length; i++) {
            String t = lines[i].trim();
            if (t.isEmpty() || t.startsWith("#") || t.startsWith("background:")) {
                continue;
            }
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("^([\\w-]+):\\s*(.*)$").matcher(t);
            if (m.matches()) {
                bg.put(m.group(1), parseYamlValue(m.group(2)));
                any = true;
            }
        }
        if (!any) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§c[OpenDreamCore] §f剪贴板中没有 background 键值（用 Ctrl+C 复制）"), false);
            return;
        }
        cc.pushWorldBackgroundUndo("背景: 粘贴", "bg:paste");
        Map<String, Object> options = cc.worldPage.options();
        Map<String, Object> world = new java.util.LinkedHashMap<String, Object>();
        if (options.get("world") instanceof Map<?, ?> w) {
            w.forEach((k, v) -> world.put(String.valueOf(k), v));
        }
        world.put("background", bg);
        options.put("world", world);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§b[OpenDreamCore] §f背景配置已应用: " + bg.size()
                        + " 键（" + bg.keySet() + "；页面 world 段持久化需在 YAML 写回）"), false);
    }

    static Object parseYamlValue(String raw) {
        String v = raw.trim();
        if (v.length() >= 2 && ((v.startsWith("'") && v.endsWith("'"))
                || (v.startsWith("\"") && v.endsWith("\"")))) {
            return v.substring(1, v.length() - 1);
        }
        if ("true".equalsIgnoreCase(v)) {
            return true;
        }
        if ("false".equalsIgnoreCase(v)) {
            return false;
        }
        if (v.matches("-?\\d+(\\.\\d+)?")) {
            return v.contains(".") ? Double.parseDouble(v) : (long) Long.parseLong(v);
        }
        return v;
    }

    public void randomWorldBackground() {
        if (cc.worldPage == null) {
            return;
        }
        double hue = Math.random();
        double sat = 0.35 + Math.random() * 0.35;
        double lum = 0.18 + Math.random() * 0.12;
        int c1 = hslToRgb(hue, sat, lum);
        int c2 = hslToRgb((hue + 0.08) % 1.0, Math.min(1, sat + 0.15), Math.max(0.05, lum - 0.08));
        String h1 = String.format(java.util.Locale.ROOT, "#%02X%02X%02X",
                (c1 >> 16) & 0xFF, (c1 >> 8) & 0xFF, c1 & 0xFF);
        String h2 = String.format(java.util.Locale.ROOT, "#%02X%02X%02X",
                (c2 >> 16) & 0xFF, (c2 >> 8) & 0xFF, c2 & 0xFF);
        cc.pushWorldBackgroundUndo("背景: 随机配色", "bg:random");
        Map<String, Object> options = cc.worldPage.options();
        Map<String, Object> world = new java.util.LinkedHashMap<String, Object>();
        if (options.get("world") instanceof Map<?, ?> w) {
            w.forEach((k, v) -> world.put(String.valueOf(k), v));
        }
        Map<String, Object> bg = new java.util.LinkedHashMap<String, Object>();
        if (world.get("background") instanceof Map<?, ?> bm) {
            bm.forEach((k, v) -> bg.put(String.valueOf(k), v));
        }
        bg.put("color", h1);
        bg.put("gradient", h2);
        bg.remove("gradientMid");
        bg.remove("gradientMidPos");
        world.put("background", bg);
        options.put("world", world);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§b[OpenDreamCore] §f随机配色: " + h1 + " → " + h2
                        + "（可再双击刷新）"), false);
    }

    private static int hslToRgb(double h, double s, double l) {
        double c = (1 - Math.abs(2 * l - 1)) * s;
        double hp = h * 6;
        double x = c * (1 - Math.abs(hp % 2 - 1));
        double r0, g0, b0;
        if (hp < 1) {
            r0 = c; g0 = x; b0 = 0;
        } else if (hp < 2) {
            r0 = x; g0 = c; b0 = 0;
        } else if (hp < 3) {
            r0 = 0; g0 = c; b0 = x;
        } else if (hp < 4) {
            r0 = 0; g0 = x; b0 = c;
        } else if (hp < 5) {
            r0 = x; g0 = 0; b0 = c;
        } else {
            r0 = c; g0 = 0; b0 = x;
        }
        double m = l - c / 2;
        return ((int) Math.round((r0 + m) * 255) << 16)
                | ((int) Math.round((g0 + m) * 255) << 8)
                | (int) Math.round((b0 + m) * 255);
    }

    public String worldBackgroundJson() {
        if (cc.worldPage == null) {
            return null;
        }
        Object worldObj = cc.worldPage.options().get("world");
        if (!(worldObj instanceof Map<?, ?> w)) {
            return null;
        }
        Object bg = w.get("background");
        if (bg == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        if (bg instanceof Map<?, ?> bm) {
            for (java.util.Map.Entry<?, ?> e : bm.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append('"').append(e.getKey()).append("\":");
                Object v = e.getValue();
                if (v instanceof Number || v instanceof Boolean) {
                    sb.append(v);
                } else {
                    sb.append('"').append(String.valueOf(v)).append('"');
                }
            }
        } else {
            sb.append("\"color\":\"").append(bg).append('"');
        }
        sb.append('}');
        return sb.toString();
    }

    public void saveWorldBackgroundPreset() {
        String json = worldBackgroundJson();
        if (json == null) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§c[OpenDreamCore] §f面板无背景配置"), false);
            return;
        }
        try {
            java.nio.file.Path f = ClientController.bgPresetFile();
            java.nio.file.Files.createDirectories(f.getParent());
            String existing = java.nio.file.Files.exists(f)
                    ? java.nio.file.Files.readString(f) : "";
            String body = existing.trim();
            // 自动时间戳名（name 字段）
            String stamp = new java.text.SimpleDateFormat("MM-dd HH:mm")
                    .format(new java.util.Date());
            java.util.Map<String, Object> bg = ClientController.parseBgJsonObject(json);
            bg.put("name", stamp);
            String entry = ClientController.bgMapToJson(bg);
            String out;
            if (body.startsWith("[")) {
                out = body.substring(0, body.length() - 1).trim()
                        + (body.length() > 2 ? "," : "") + entry + "]";
            } else {
                out = "[" + entry + "]";
            }
            java.nio.file.Files.writeString(f, out);
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§a[OpenDreamCore] §f背景预设已保存: " + stamp), false);
        } catch (Exception e) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§c[OpenDreamCore] §f预设保存失败: " + e.getMessage()), false);
        }
    }

    public void loadWorldBackgroundPreset() {
        if (cc.worldPage == null) {
            return;
        }
        try {
            java.nio.file.Path f = ClientController.bgPresetFile();
            if (!java.nio.file.Files.exists(f)) {
                Minecraft.getInstance().player.displayClientMessage(
                        Component.literal("§c[OpenDreamCore] §f无预设文件（先 Alt+Ctrl+S 保存）"), false);
                return;
            }
            String body = java.nio.file.Files.readString(f).trim();
            if (!body.startsWith("[") || !body.endsWith("]")) {
                Minecraft.getInstance().player.displayClientMessage(
                        Component.literal("§c[OpenDreamCore] §f预设文件格式异常"), false);
                return;
            }
            java.util.List<java.util.Map<String, Object>> presets = new java.util.ArrayList<>();
            String inner = body.substring(1, body.length() - 1).trim();
            int idx = 0;
            while (idx < inner.length()) {
                int open = inner.indexOf('{', idx);
                if (open < 0) {
                    break;
                }
                int close = inner.indexOf('}', open);
                if (close < 0) {
                    break;
                }
                java.util.Map<String, Object> bg = ClientController.parseBgJsonObject(inner.substring(open, close + 1));
                if (!bg.isEmpty()) {
                    presets.add(bg);
                }
                idx = close + 1;
            }
            if (presets.isEmpty()) {
                Minecraft.getInstance().player.displayClientMessage(
                        Component.literal("§c[OpenDreamCore] §f预设列表为空"), false);
                return;
            }
            cc.pushWorldBackgroundUndo("背景: 载入预设", "bg:load");
            java.util.Map<String, Object> bg = presets.get(WorldEditor.get().worldBgPresetIdx % presets.size());
            WorldEditor.get().worldBgPresetIdx = (WorldEditor.get().worldBgPresetIdx + 1) % presets.size();
            Map<String, Object> options = cc.worldPage.options();
            Map<String, Object> world = new java.util.LinkedHashMap<String, Object>();
            if (options.get("world") instanceof Map<?, ?> w) {
                w.forEach((k, v) -> world.put(String.valueOf(k), v));
            }
            world.put("background", bg);
            options.put("world", world);
            Object name = bg.get("name");
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§a[OpenDreamCore] §f背景预设 "
                            + (name == null ? "#" + ((WorldEditor.get().worldBgPresetIdx - 1 + presets.size()) % presets.size() + 1)
                            : "\"" + name + "\"")
                            + " " + ((WorldEditor.get().worldBgPresetIdx - 1 + presets.size()) % presets.size() + 1)
                            + "/" + presets.size() + "（继续 Alt+Ctrl+L 循环）"), false);
        } catch (Exception e) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§c[OpenDreamCore] §f预设载入失败: " + e.getMessage()), false);
        }
    }

    public void renameWorldBackgroundPreset(String newName) {
        if (newName == null || newName.isBlank()) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§e[OpenDreamCore] §f预设名不能为空"), false);
            return;
        }
        try {
            java.nio.file.Path f = ClientController.bgPresetFile();
            if (!java.nio.file.Files.exists(f)) {
                Minecraft.getInstance().player.displayClientMessage(
                        Component.literal("§c[OpenDreamCore] §f无预设文件"), false);
                return;
            }
            String body = java.nio.file.Files.readString(f).trim();
            if (!body.startsWith("[") || !body.endsWith("]")) {
                Minecraft.getInstance().player.displayClientMessage(
                        Component.literal("§c[OpenDreamCore] §f预设文件格式异常"), false);
                return;
            }
            java.util.List<java.util.Map<String, Object>> presets = new java.util.ArrayList<>();
            java.util.List<String> raw = new java.util.ArrayList<>();
            String inner = body.substring(1, body.length() - 1).trim();
            int idx = 0;
            while (idx < inner.length()) {
                int open = inner.indexOf('{', idx);
                if (open < 0) {
                    break;
                }
                int close = inner.indexOf('}', open);
                if (close < 0) {
                    break;
                }
                String seg = inner.substring(open, close + 1);
                presets.add(ClientController.parseBgJsonObject(seg));
                raw.add(seg);
                idx = close + 1;
            }
            if (presets.isEmpty()) {
                Minecraft.getInstance().player.displayClientMessage(
                        Component.literal("§c[OpenDreamCore] §f预设列表为空"), false);
                return;
            }
            int target = ((WorldEditor.get().worldBgPresetIdx - 1) % presets.size() + presets.size()) % presets.size();
            presets.get(target).put("name", newName);
            raw.set(target, ClientController.bgMapToJson(presets.get(target)));
            java.nio.file.Files.writeString(f, "[" + String.join(",", raw) + "]");
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§a[OpenDreamCore] §f预设 #" + (target + 1)
                            + " 已命名: " + newName), false);
        } catch (Exception e) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§c[OpenDreamCore] §f预设重命名失败: " + e.getMessage()), false);
        }
    }

    public void deleteWorldBackgroundPreset() {
        try {
            java.nio.file.Path f = ClientController.bgPresetFile();
            if (!java.nio.file.Files.exists(f)) {
                Minecraft.getInstance().player.displayClientMessage(
                        Component.literal("§c[OpenDreamCore] §f无预设文件"), false);
                return;
            }
            String body = java.nio.file.Files.readString(f).trim();
            if (!body.startsWith("[") || !body.endsWith("]")) {
                Minecraft.getInstance().player.displayClientMessage(
                        Component.literal("§c[OpenDreamCore] §f预设文件格式异常"), false);
                return;
            }
            java.util.List<String> items = new java.util.ArrayList<>();
            String inner = body.substring(1, body.length() - 1).trim();
            int idx = 0;
            while (idx < inner.length()) {
                int open = inner.indexOf('{', idx);
                if (open < 0) {
                    break;
                }
                int close = inner.indexOf('}', open);
                if (close < 0) {
                    break;
                }
                items.add(inner.substring(open, close + 1));
                idx = close + 1;
            }
            if (items.isEmpty()) {
                Minecraft.getInstance().player.displayClientMessage(
                        Component.literal("§c[OpenDreamCore] §f预设列表为空"), false);
                return;
            }
            int del = ((WorldEditor.get().worldBgPresetIdx - 1) % items.size() + items.size()) % items.size();
            items.remove(del);
            if (WorldEditor.get().worldBgPresetIdx > 0) {
                WorldEditor.get().worldBgPresetIdx--;
            }
            String out = "[" + String.join(",", items) + "]";
            java.nio.file.Files.writeString(f, out);
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§a[OpenDreamCore] §f已删除预设 #" + (del + 1)
                            + "（剩余 " + items.size() + " 条）"), false);
        } catch (Exception e) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§c[OpenDreamCore] §f预设删除失败: " + e.getMessage()), false);
        }
    }

    public void setWorldBackgroundKeyValue(String key, String hex) {
        if (cc.worldPage == null || key == null || hex == null) {
            return;
        }
        cc.pushWorldBackgroundUndo("背景: " + key, "bg:key");
        Map<String, Object> options = cc.worldPage.options();
        Map<String, Object> world = new java.util.LinkedHashMap<String, Object>();
        if (options.get("world") instanceof Map<?, ?> w) {
            w.forEach((k, v) -> world.put(String.valueOf(k), v));
        }
        Map<String, Object> bg = new java.util.LinkedHashMap<String, Object>();
        if (world.get("background") instanceof Map<?, ?> bm) {
            bm.forEach((k, v) -> bg.put(String.valueOf(k), v));
        }
        bg.put(key, hex);
        world.put("background", bg);
        options.put("world", world);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§b[OpenDreamCore] §f背景 " + key + ": " + hex
                        + "（运行时生效；页面 world 段持久化需在 YAML 写回）"), false);
    }

    public void randomWorldBackgroundKey(String key) {
        double hue = Math.random();
        double sat = 0.4 + Math.random() * 0.3;
        double lum = 0.25 + Math.random() * 0.2;
        int c = hslToRgb(hue, sat, lum);
        String hex = String.format(java.util.Locale.ROOT, "#%02X%02X%02X",
                (c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF);
        setWorldBackgroundKeyValue(key, hex);
    }

    public void nudgeWorldBackgroundKey(String key, boolean brighter) {
        if (cc.worldPage == null || key == null) {
            return;
        }
        Map<String, Object> options = cc.worldPage.options();
        Map<String, Object> world = new java.util.LinkedHashMap<String, Object>();
        if (options.get("world") instanceof Map<?, ?> w) {
            w.forEach((k, v) -> world.put(String.valueOf(k), v));
        }
        Object bgObj = world.get("background");
        Map<String, Object> bg;
        if (bgObj instanceof Map<?, ?> bm) {
            bg = new java.util.LinkedHashMap<String, Object>();
            bm.forEach((k, v) -> bg.put(String.valueOf(k), v));
        } else if (bgObj != null && "color".equals(key)) {
            bg = new java.util.LinkedHashMap<String, Object>();
            bg.put("color", String.valueOf(bgObj));
        } else {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§c[OpenDreamCore] §f该背景键未设置，无法调明暗"), false);
            return;
        }
        Object colorObj = bg.get(key);
        String hex = colorObj == null ? "" : String.valueOf(colorObj).trim();
        if (!hex.matches("#[0-9a-fA-F]{6,8}")) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§c[OpenDreamCore] §f" + key + " 格式需 #RRGGBB 或 #AARRGGBB"), false);
            return;
        }
        cc.pushWorldBackgroundUndo("背景: " + key + " 明暗", "bg:keynudge");
        double f = brighter ? 1.1 : 1 / 1.1;
        String body = hex.substring(1);
        boolean alpha8 = body.length() == 8;
        String rgbPart = alpha8 ? body.substring(2) : body;
        int r = (int) Math.min(255, Math.max(0, Math.round(Integer.parseInt(rgbPart.substring(0, 2), 16) * f)));
        int g = (int) Math.min(255, Math.max(0, Math.round(Integer.parseInt(rgbPart.substring(2, 4), 16) * f)));
        int b = (int) Math.min(255, Math.max(0, Math.round(Integer.parseInt(rgbPart.substring(4, 6), 16) * f)));
        String next = String.format(java.util.Locale.ROOT, "#%02X%02X%02X", r, g, b);
        if (alpha8) {
            next = hex.substring(0, 3) + next.substring(1);
        }
        bg.put(key, next);
        world.put("background", bg);
        options.put("world", world);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§b[OpenDreamCore] §f" + key + " 明暗: " + (brighter ? "+10%" : "-10%")
                        + " → " + next + "（运行时生效；页面 world 段持久化需在 YAML 写回）"), false);
    }

    public void swapWorldBackgroundKeyWithColor(String key) {
        if (cc.worldPage == null || key == null || "color".equals(key)) {
            return;
        }
        Map<String, Object> options = cc.worldPage.options();
        Map<String, Object> world = new java.util.LinkedHashMap<String, Object>();
        if (options.get("world") instanceof Map<?, ?> w) {
            w.forEach((k, v) -> world.put(String.valueOf(k), v));
        }
        Object bgObj = world.get("background");
        Map<String, Object> bg;
        if (bgObj instanceof Map<?, ?> bm) {
            bg = new java.util.LinkedHashMap<String, Object>();
            bm.forEach((k, v) -> bg.put(String.valueOf(k), v));
        } else {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§c[OpenDreamCore] §f面板无背景配置"), false);
            return;
        }
        Object color = bg.get("color");
        Object keyVal = bg.get(key);
        if (color == null || keyVal == null) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§c[OpenDreamCore] §f需同时有 color 与 " + key), false);
            return;
        }
        cc.pushWorldBackgroundUndo("背景: 互换 " + key, "bg:keyswap");
        bg.put("color", keyVal);
        bg.put(key, color);
        world.put("background", bg);
        options.put("world", world);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§b[OpenDreamCore] §f已互换 color ↔ " + key + ": "
                        + keyVal + " / " + color), false);
    }

    public void saveBackgroundJsonPreset(String json) {
        if (json == null) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§c[OpenDreamCore] §f无背景配置可保存"), false);
            return;
        }
        try {
            java.nio.file.Path f = ClientController.bgPresetFile();
            java.nio.file.Files.createDirectories(f.getParent());
            String existing = java.nio.file.Files.exists(f)
                    ? java.nio.file.Files.readString(f) : "";
            String body = existing.trim();
            String stamp = new java.text.SimpleDateFormat("MM-dd HH:mm")
                    .format(new java.util.Date());
            java.util.Map<String, Object> bg = ClientController.parseBgJsonObject(json);
            bg.put("name", stamp);
            String entry = ClientController.bgMapToJson(bg);
            String out;
            if (body.startsWith("[")) {
                out = body.substring(0, body.length() - 1).trim()
                        + (body.length() > 2 ? "," : "") + entry + "]";
            } else {
                out = "[" + entry + "]";
            }
            java.nio.file.Files.writeString(f, out);
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§a[OpenDreamCore] §f快照已存为预设: " + stamp), false);
        } catch (Exception e) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§c[OpenDreamCore] §f预设保存失败: " + e.getMessage()), false);
        }
    }

    public boolean hasWorldPanelGradient() {
        if (cc.worldPage == null) {
            return false;
        }
        Object worldObj = cc.worldPage.options().get("world");
        if (!(worldObj instanceof Map<?, ?> w)) {
            return false;
        }
        Object bg = w.get("background");
        return bg instanceof Map<?, ?> bm && bm.get("gradient") != null;
    }

    public void toggleWorldPanelGradient() {
        if (cc.worldPage == null) {
            return;
        }
        Map<String, Object> options = cc.worldPage.options();
        Map<String, Object> world = new java.util.LinkedHashMap<String, Object>();
        if (options.get("world") instanceof Map<?, ?> w) {
            w.forEach((k, v) -> world.put(String.valueOf(k), v));
        }
        Object bgObj = world.get("background");
        Map<String, Object> bg;
        if (bgObj instanceof Map<?, ?> bm) {
            bg = new java.util.LinkedHashMap<String, Object>();
            bm.forEach((k, v) -> bg.put(String.valueOf(k), v));
        } else if (bgObj != null) {
            bg = new java.util.LinkedHashMap<String, Object>();
            bg.put("color", String.valueOf(bgObj));
        } else {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§c[OpenDreamCore] §f面板无背景，无法开关渐变（先设背景色）"), false);
            return;
        }
        cc.pushWorldBackgroundUndo("背景: 渐变开关", "bg:gradient");
        boolean on = bg.get("gradient") == null;
        if (on) {
            bg.put("gradient", "#1A2332");
        } else {
            bg.remove("gradient");
        }
        world.put("background", bg);
        options.put("world", world);
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("§b[OpenDreamCore] §f面板背景渐变: " + (on ? "开" : "关")
                        + "（运行时生效；页面 world 段持久化需在 YAML 写回）"), false);
    }

    public void loadWorldBackgroundPresetAt(int index) {
        if (cc.worldPage == null) {
            return;
        }
        try {
            java.nio.file.Path f = ClientController.bgPresetFile();
            if (!java.nio.file.Files.exists(f)) {
                Minecraft.getInstance().player.displayClientMessage(
                        Component.literal("§c[OpenDreamCore] §f无预设文件（先 Alt+Ctrl+S 保存）"), false);
                return;
            }
            String body = java.nio.file.Files.readString(f).trim();
            if (!body.startsWith("[") || !body.endsWith("]")) {
                Minecraft.getInstance().player.displayClientMessage(
                        Component.literal("§c[OpenDreamCore] §f预设文件格式异常"), false);
                return;
            }
            java.util.List<java.util.Map<String, Object>> presets = new java.util.ArrayList<>();
            String inner = body.substring(1, body.length() - 1);
            int idx = 0;
            while (idx < inner.length()) {
                int open = inner.indexOf('{', idx);
                if (open < 0) {
                    break;
                }
                int close = inner.indexOf('}', open);
                if (close < 0) {
                    break;
                }
                java.util.Map<String, Object> bg = ClientController.parseBgJsonObject(inner.substring(open, close + 1));
                if (!bg.isEmpty()) {
                    presets.add(bg);
                }
                idx = close + 1;
            }
            if (index < 0 || index >= presets.size()) {
                Minecraft.getInstance().player.displayClientMessage(
                        Component.literal("§c[OpenDreamCore] §f预设序号越界"), false);
                return;
            }
            java.util.Map<String, Object> bg = presets.get(index);
            cc.pushWorldBackgroundUndo("背景: 载入预设", "bg:load");
            Map<String, Object> options = cc.worldPage.options();
            Map<String, Object> world = new java.util.LinkedHashMap<String, Object>();
            if (options.get("world") instanceof Map<?, ?> w) {
                w.forEach((k, v) -> world.put(String.valueOf(k), v));
            }
            world.put("background", bg);
            options.put("world", world);
            Object name = bg.get("name");
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§a[OpenDreamCore] §f已载入预设 #" + (index + 1)
                            + (name == null ? "" : " \"" + name + "\"") + "（可 Ctrl+Z 撤）"), false);
        } catch (Exception e) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§c[OpenDreamCore] §f预设载入失败: " + e.getMessage()), false);
        }
    }

    public void deleteWorldBackgroundPresetAt(int index) {
        try {
            java.nio.file.Path f = ClientController.bgPresetFile();
            if (!java.nio.file.Files.exists(f)) {
                Minecraft.getInstance().player.displayClientMessage(
                        Component.literal("§c[OpenDreamCore] §f无预设文件"), false);
                return;
            }
            String body = java.nio.file.Files.readString(f).trim();
            if (!body.startsWith("[") || !body.endsWith("]")) {
                Minecraft.getInstance().player.displayClientMessage(
                        Component.literal("§c[OpenDreamCore] §f预设文件格式异常"), false);
                return;
            }
            java.util.List<String> items = new java.util.ArrayList<>();
            String inner = body.substring(1, body.length() - 1);
            int idx = 0;
            while (idx < inner.length()) {
                int open = inner.indexOf('{', idx);
                if (open < 0) {
                    break;
                }
                int close = inner.indexOf('}', open);
                if (close < 0) {
                    break;
                }
                items.add(inner.substring(open, close + 1));
                idx = close + 1;
            }
            if (index < 0 || index >= items.size()) {
                return;
            }
            items.remove(index);
            java.nio.file.Files.writeString(f, "[" + String.join(",", items) + "]");
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§a[OpenDreamCore] §f已删除预设 #" + (index + 1)
                            + "（剩余 " + items.size() + " 条）"), false);
        } catch (Exception e) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§c[OpenDreamCore] §f预设删除失败: " + e.getMessage()), false);
        }
    }

}
