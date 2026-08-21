package com.opendreamcore.client;

import com.opendreamcore.ui.RenderNode;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 组件绘制器：OdcScreen（页面）与 HUD 常驻渲染共用。
 * 组件类型：layout/rect/text/button/input/slider/progress/image/item_slot，
 * 其余类型画占位框（video/entity 等后续补）。
 */
public final class UiRenderer {

    /** 绘制时的运行时状态（输入框文本/滑块值由交互层提供）。 */
    public interface State {
        String inputText(String id);

        Double sliderValue(String id);

        /** 开关当前值；null 表示用配置默认。 */
        Boolean toggleValue(String id);

        /** 下拉框展开状态。 */
        boolean dropdownOpen(String id);

        /** 下拉框当前选中项；null 用配置默认。 */
        String dropdownValue(String id);
    }

    private static final Pattern TEMPLATE = Pattern.compile("\\{\\{\\s*([^}]+?)\\s*}}");

    private UiRenderer() {
    }

    public static void draw(GuiGraphics g, Font font, List<RenderNode> nodes,
                            int mouseX, int mouseY, State state, java.util.Map<String, Object> pageVars) {
        for (RenderNode node : nodes) {
            drawNode(g, font, node, mouseX, mouseY, state, pageVars);
        }
    }

    public static void drawNode(GuiGraphics g, Font font, RenderNode node,
                                int mouseX, int mouseY, State state, java.util.Map<String, Object> pageVars) {
        if (!node.visible()) {
            return;
        }
        switch (node.type()) {
            case "layout" -> drawLayout(g, font, node);
            case "rect" -> drawRect(g, font, node);
            case "text" -> drawText(g, font, node, pageVars);
            case "button" -> drawButton(g, font, node, mouseX, mouseY, pageVars);
            case "input" -> drawInput(g, font, node, state);
            case "slider" -> drawSlider(g, font, node, state);
            case "progress" -> drawProgress(g, font, node);
            case "image" -> drawImage(g, font, node);
            case "video" -> drawVideo(g, font, node);
            case "item_slot" -> drawItemSlot(g, font, node);
            case "item_display" -> drawItemDisplay(g, font, node);
            case "toggle" -> drawToggle(g, font, node, state);
            case "dropdown" -> drawDropdown(g, font, node, state, mouseX, mouseY);
            default -> drawPlaceholder(g, font, node);
        }
        for (RenderNode child : node.children()) {
            drawNode(g, font, child, mouseX, mouseY, state, pageVars);
        }
    }

    private static void drawLayout(GuiGraphics g, Font font, RenderNode node) {
        int color = UiStyle.color(node.props().get("background"), 0);
        if (color != 0) {
            fillRect(g, node, color);
        }
    }

    private static void drawRect(GuiGraphics g, Font font, RenderNode node) {
        fillRect(g, node, UiStyle.color(node.props().get("color"), 0xFFFFFFFF));
    }

    private static void drawText(GuiGraphics g, Font font, RenderNode node, java.util.Map<String, Object> pageVars) {
        Map<?, ?> spec = propsMap(node, "text");
        String content = interpolate(node, str(spec.get("content")), pageVars);
        if (content == null || content.isEmpty()) {
            return;
        }
        int color = UiStyle.color(spec.get("color"), 0xFFFFFFFF);
        int x = (int) node.x();
        int y = (int) node.y();
        String align = str(spec.get("align"));
        if ("center".equals(align)) {
            x += (int) ((node.width() - font.width(content)) / 2);
        } else if ("right".equals(align)) {
            x += (int) (node.width() - font.width(content));
        }
        g.drawString(font, content, x, y, color);
    }

    private static void drawButton(GuiGraphics g, Font font, RenderNode node, int mouseX, int mouseY,
                                   java.util.Map<String, Object> pageVars) {
        Map<?, ?> spec = propsMap(node, "button");
        boolean hover = node.enabled() && node.contains(mouseX, mouseY);
        int bg = UiStyle.color(spec.get("background"),
                node.enabled() ? (hover ? 0xFF3A3F4A : 0xFF2A2F3A) : 0xFF20242C);
        fillRect(g, node, bg);
        g.fill((int) node.x(), (int) node.y(), (int) (node.x() + node.width()), (int) (node.y() + 1), 0xFF505868);
        g.fill((int) node.x(), (int) (node.y() + node.height() - 1), (int) (node.x() + node.width()), (int) (node.y() + node.height()), 0xFF20242C);
        String label = interpolate(node, str(spec.get("label")), pageVars);
        if (label != null && !label.isEmpty()) {
            int lx = (int) (node.x() + (node.width() - font.width(label)) / 2);
            int ly = (int) (node.y() + (node.height() - 8) / 2);
            g.drawString(font, label, lx, ly, node.enabled() ? 0xFFFFFFFF : 0xFF808080);
        }
    }

    private static void drawInput(GuiGraphics g, Font font, RenderNode node, State state) {
        Map<?, ?> spec = propsMap(node, "input");
        String text = state == null ? "" : state.inputText(node.id());
        if (text == null) {
            text = "";
        }
        fillRect(g, node, 0xFF20242C);
        g.fill((int) node.x(), (int) node.y(), (int) (node.x() + node.width()), (int) (node.y() + 1), 0xFF505868);
        g.drawString(font, text, (int) node.x() + 4, (int) (node.y() + (node.height() - 8) / 2), 0xFFFFFFFF);
    }

    private static void drawSlider(GuiGraphics g, Font font, RenderNode node, State state) {
        Map<?, ?> spec = propsMap(node, "slider");
        double min = num(spec.get("min"), 0);
        double max = num(spec.get("max"), 100);
        Double local = state == null ? null : state.sliderValue(node.id());
        double value = local != null ? local : num(spec.get("value"), min);
        double ratio = max > min ? (value - min) / (max - min) : 0;
        ratio = Math.max(0, Math.min(1, ratio));
        g.fill((int) node.x(), (int) (node.y() + node.height() / 2 - 2), (int) (node.x() + node.width()), (int) (node.y() + node.height() / 2 + 2), 0xFF303540);
        g.fill((int) node.x(), (int) (node.y() + node.height() / 2 - 2), (int) (node.x() + node.width() * ratio), (int) (node.y() + node.height() / 2 + 2), 0xFF7A8BFF);
        int knob = (int) (node.x() + node.width() * ratio);
        g.fill(knob - 4, (int) node.y(), knob + 4, (int) (node.y() + node.height()), 0xFFB0C0FF);
    }

    private static void drawProgress(GuiGraphics g, Font font, RenderNode node) {
        Map<?, ?> spec = propsMap(node, "progress");
        double min = num(spec.get("min"), 0);
        double max = num(spec.get("max"), 100);
        double value = num(spec.get("value"), min);
        double ratio = max > min ? (value - min) / (max - min) : 0;
        ratio = Math.max(0, Math.min(1, ratio));
        fillRect(g, node, 0xFF303540);
        g.fill((int) node.x(), (int) node.y(), (int) (node.x() + node.width() * ratio), (int) (node.y() + node.height()),
                UiStyle.color(spec.get("color"), 0xFF4CAF50));
    }

    /** 视频组件：src 指向帧序列目录（frame_0000.png...），fps 控制播放速度。 */
    private static void drawVideo(GuiGraphics g, Font font, RenderNode node) {
        Map<?, ?> spec = propsMap(node, "video");
        String src = str(spec.get("src"));
        double fps = num(spec.get("fps"), 24);
        ResourceLocation texture = null;
        if (src != null) {
            VideoPlayer video = VideoPlayer.of(src);
            if (video != null) {
                texture = video.currentTexture(fps);
            }
        }
        if (texture == null) {
            fillRect(g, node, 0xFF101318);
            g.drawString(font, "[video]", (int) node.x() + 2, (int) node.y() + 2, 0xFFFFD54F);
            return;
        }
        int w = (int) node.width();
        int h = (int) node.height();
        g.blit(texture, (int) node.x(), (int) node.y(), w, h, 0.0F, 0.0F, w, h, w, h);
    }

    private static void drawImage(GuiGraphics g, Font font, RenderNode node) {
        Map<?, ?> spec = propsMap(node, "image");
        String src = str(spec.get("src"));
        ResourceLocation texture;
        if (src != null && src.toLowerCase(java.util.Locale.ROOT).endsWith(".gif")) {
            GifPlayer gif = GifPlayer.of(src);
            texture = gif == null ? null : gif.currentTexture();
        } else {
            texture = UiStyle.texture(src);
        }
        if (texture == null) {
            fillRect(g, node, 0xFF20242C);
            return;
        }
        int w = (int) node.width();
        int h = (int) node.height();
        g.blit(texture, (int) node.x(), (int) node.y(), w, h, 0.0F, 0.0F, w, h, w, h);
    }

    private static void drawItemSlot(GuiGraphics g, Font font, RenderNode node) {
        fillRect(g, node, 0xFF101318);
        g.fill((int) node.x(), (int) node.y(), (int) (node.x() + node.width()), (int) (node.y() + 1), 0xFF505868);
        g.fill((int) node.x(), (int) (node.y() + node.height() - 1), (int) (node.x() + node.width()), (int) (node.y() + node.height()), 0xFF20242C);
        drawItemIcon(g, font, node, node.props().get("item"), false);
    }

    private static void drawItemDisplay(GuiGraphics g, Font font, RenderNode node) {
        Map<?, ?> spec = propsMap(node, "item_display");
        drawItemIcon(g, font, node, spec.get("item"), true);
    }

    /** 槽位里的物品图标（item: "minecraft:diamond_sword"，可带数量 "id x64" 或 props count）。 */
    private static void drawItemIcon(GuiGraphics g, Font font, RenderNode node, Object raw, boolean big) {
        String id = raw == null ? null : String.valueOf(raw).trim();
        if (id == null || id.isEmpty()) {
            return;
        }
        int count = 1;
        String[] parts = id.split("\\s+");
        if (parts.length >= 3 && "x".equalsIgnoreCase(parts[1])) {
            id = parts[0];
            try {
                count = Integer.parseInt(parts[2]);
            } catch (NumberFormatException ignored) {
            }
        }
        ItemStack stack = parseItem(id);
        if (stack.isEmpty()) {
            return;
        }
        stack.setCount(count);
        int icon = big ? (int) Math.max(16, node.width() * 0.7) : Math.min(16, (int) Math.min(node.width(), node.height()));
        int ix = (int) (node.x() + (node.width() - icon) / 2);
        int iy = (int) (node.y() + (node.height() - icon) / 2);
        var pose = g.pose();
        pose.pushPose();
        pose.translate(ix, iy, 0);
        float scale = icon / 16.0F;
        pose.scale(scale, scale, 1.0F);
        g.renderItem(stack, 0, 0);
        if (count > 1) {
            g.renderItemDecorations(font, stack, 0, 0);
        }
        pose.popPose();
    }

    private static void drawToggle(GuiGraphics g, Font font, RenderNode node, State state) {
        Map<?, ?> spec = propsMap(node, "toggle");
        Boolean local = state == null ? null : state.toggleValue(node.id());
        boolean on = local != null ? local : bool(spec.get("value"), false);
        String label = interpolate(node, str(spec.get("label")), null);
        int h = (int) node.height();
        int trackW = (int) Math.min(node.width(), h * 2);
        int trackX = (int) (node.x() + node.width() - trackW);
        // 轨道
        g.fill(trackX, (int) node.y() + 1, trackX + trackW, (int) (node.y() + h - 1), on ? 0xFF4CAF50 : 0xFF303540);
        // 滑块
        int knobX = on ? trackX + trackW - h + 2 : trackX + 2;
        g.fill(knobX, (int) node.y() + 2, knobX + h - 4, (int) (node.y() + h - 2), 0xFFFFFFFF);
        if (label != null && !label.isEmpty()) {
            g.drawString(font, label, (int) node.x(), (int) (node.y() + (h - 8) / 2), 0xFFFFFFFF);
        }
    }

    private static void drawDropdown(GuiGraphics g, Font font, RenderNode node, State state, int mouseX, int mouseY) {
        Map<?, ?> spec = propsMap(node, "dropdown");
        List<?> options = spec.get("options") instanceof List<?> list ? list : List.of();
        String value = state == null ? null : state.dropdownValue(node.id());
        if (value == null) {
            value = str(spec.get("value"));
        }
        if (value == null && !options.isEmpty()) {
            value = String.valueOf(options.get(0));
        }
        boolean open = state != null && state.dropdownOpen(node.id());
        // 主框
        fillRect(g, node, open ? 0xFF2E3340 : 0xFF20242C);
        g.fill((int) node.x(), (int) node.y(), (int) (node.x() + node.width()), (int) (node.y() + 1), 0xFF505868);
        String label = interpolate(node, value, null);
        if (label != null) {
            g.drawString(font, label, (int) node.x() + 4, (int) (node.y() + (node.height() - 8) / 2), 0xFFFFFFFF);
        }
        // 箭头
        int ax = (int) (node.x() + node.width() - 10);
        int ay = (int) (node.y() + node.height() / 2);
        g.fill(ax - 3, ay - 2, ax + 3, ay - 1, 0xFFB0BEC5);
        g.fill(ax - 3, ay + 1, ax + 3, ay + 2, 0xFFB0BEC5);
        g.fill(ax - 4, ay - 1, ax - 3, ay + 1, 0xFFB0BEC5);
        g.fill(ax + 3, ay - 1, ax + 4, ay + 1, 0xFFB0BEC5);
        if (!open) {
            return;
        }
        // 展开选项
        int itemH = 14;
        int y = (int) (node.y() + node.height());
        for (int i = 0; i < options.size(); i++) {
            String option = String.valueOf(options.get(i));
            boolean hover = mouseX >= node.x() && mouseX <= node.x() + node.width()
                    && mouseY >= y && mouseY <= y + itemH;
            g.fill((int) node.x(), y, (int) (node.x() + node.width()), y + itemH,
                    hover ? 0xFF3A3F4A : 0xFF181C24);
            g.drawString(font, option, (int) node.x() + 4, y + 3, 0xFFFFFFFF);
            y += itemH;
        }
    }

    private static boolean bool(Object v, boolean fallback) {
        if (v instanceof Boolean b) {
            return b;
        }
        if (v != null) {
            return Boolean.parseBoolean(String.valueOf(v));
        }
        return fallback;
    }

    private static ItemStack parseItem(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.get(rl);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    private static void drawPlaceholder(GuiGraphics g, Font font, RenderNode node) {
        // 未实现的组件画个半透明框 + 类型名，避免页面静默缺东西
        fillRect(g, node, 0x40FF9800);
        g.drawString(font, "[" + node.type() + "]", (int) node.x() + 2, (int) node.y() + 2, 0xFFFFD54F);
    }

    /** {{vars.coin}} / {{global.xxx}} 插值；解析失败原样保留。 */
    public static String interpolate(RenderNode node, String content, java.util.Map<String, Object> pageVars) {
        if (content == null) {
            return null;
        }
        Matcher m = TEMPLATE.matcher(content);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String path = m.group(1).trim();
            Object value = resolvePath(pageVars, path);
            m.appendReplacement(sb, Matcher.quoteReplacement(value == null ? m.group(0) : String.valueOf(value)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static Object resolvePath(java.util.Map<String, Object> pageVars, String path) {
        com.opendreamcore.script.Scope scope = new com.opendreamcore.script.Scope();
        if (pageVars != null) {
            pageVars.forEach(scope::assignVar);
        }
        // 服务端全局变量（{{global.xxx}}）
        ClientController.get().globals().forEach(scope::assignGlobal);
        String[] parts = path.split("\\.");
        if (parts.length == 1) {
            return scope.resolve(parts[0]);
        }
        return scope.resolve(parts);
    }

    public static Map<?, ?> propsMap(RenderNode node, String key) {
        Object raw = node.props().get(key);
        return raw instanceof Map<?, ?> map ? map : Map.of();
    }

    public static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    public static double num(Object v, double fallback) {
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        if (v != null) {
            try {
                return Double.parseDouble(String.valueOf(v));
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    public static int[] rect(RenderNode node) {
        return new int[]{(int) node.x(), (int) node.y(),
                (int) (node.x() + Math.max(node.width(), 0)), (int) (node.y() + Math.max(node.height(), 0))};
    }

    public static void fillRect(GuiGraphics g, RenderNode node, int color) {
        int[] r = rect(node);
        g.fill(r[0], r[1], r[2], r[3], color);
    }
}
