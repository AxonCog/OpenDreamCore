package com.opendreamcore.client.elements;

import com.opendreamcore.client.AnimationEngine;
import com.opendreamcore.client.ClientController;
import com.opendreamcore.client.CompatRender;
import com.opendreamcore.client.FfmpegVideoPlayer;
import com.opendreamcore.client.CustomFonts;
import com.opendreamcore.client.SoundStore;
import com.opendreamcore.client.TtfRenderer;
import com.opendreamcore.client.UiRenderer;
import com.opendreamcore.client.UiStyle;
import com.opendreamcore.page.Element;
import com.opendreamcore.script.RichText;
import com.opendreamcore.ui.LayoutEngine;
import com.opendreamcore.ui.RenderNode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * C2 拆分自 ScreenElements（InputDraws 组件族）。方法体逐字搬运，可见性放宽为 public。
 */
public final class InputDraws {
    private InputDraws() {}

    public static void drawInput(GuiGraphics g, Font font, RenderNode node, UiRenderer.State state) {
        Map<?, ?> spec = UiRenderer.propsMap(node, "input");
        String text = state == null ? "" : state.inputText(node.id());
        if (text == null) {
            text = "";
        }
        boolean focused = state != null && state.focused(node.id());
        drawInputBox(g, node, spec, focused);
        int textColor = UiStyle.color(spec.get("textColor"), 0xFFFFFFFF);
        if (text.isEmpty()) {
            // 占位符（空文本灰字提示）
            String ph = UiRenderer.interpolate(node, UiRenderer.str(spec.get("placeholder")), null);
            if (ph != null && !ph.isEmpty()) {
                g.drawString(font, ph, (int) node.x() + 4, (int) (node.y() + (node.height() - 8) / 2),
                        UiRenderer.alphaColor(0xFF707880));
            }
        } else {
            g.drawString(font, text, (int) node.x() + 4, (int) (node.y() + (node.height() - 8) / 2),
                    UiRenderer.alphaColor(textColor));
        }
        // 光标：聚焦时文本末尾闪烁竖线（500ms 周期）
        if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
            int cx = (int) node.x() + 4 + font.width(text);
            g.fill(cx, (int) node.y() + 4, cx + 1, (int) (node.y() + node.height() - 4),
                    UiRenderer.alphaColor(textColor));
        }
    }

    /** 输入框底框：底色/圆角/描边/焦点高亮（input / chat_input / area_input 共用）。 */
    public static void drawInputBox(GuiGraphics g, RenderNode node, Map<?, ?> spec, boolean focused) {
        // 状态贴图（normal/focus，支持源区域对象形式）；提供贴图时优先绘制，回退纯色路径
        Object texRaw = focused && spec.get("focus") != null ? spec.get("focus")
                : !focused && spec.get("normal") != null ? spec.get("normal") : null;
        if (MediaItemDraws.drawStateTexture(g, texRaw, node)) {
            return;
        }
        int bg = UiStyle.color(spec.get("background"), 0xFF20242C);
        int accent = UiStyle.color(spec.get("accent"), focused ? 0xFF7A8BFF : 0xFF505868);
        double radius = UiRenderer.num(spec.get("radius"), 0);
        int border = UiStyle.color(spec.get("border"), accent);
        int borderW = (int) UiRenderer.num(spec.get("borderWidth"), 1);
        if (radius > 0 || border != 0) {
            UiRenderer.drawRoundedRect(g, node, radius, UiRenderer.alphaColor(bg), border, borderW);
            if (focused && border == 0) {
                // 焦点无描边时补下边框高亮
                g.fill((int) node.x(), (int) (node.y() + node.height() - 1), (int) (node.x() + node.width()), (int) (node.y() + node.height()), accent);
            }
        } else {
            UiRenderer.fillRect(g, node, UiRenderer.alphaColor(bg));
            g.fill((int) node.x(), (int) node.y(), (int) (node.x() + node.width()), (int) (node.y() + 1), accent);
            if (focused) {
                g.fill((int) node.x(), (int) (node.y() + node.height() - 1), (int) (node.x() + node.width()), (int) (node.y() + node.height()), accent);
            }
        }
    }

    public static void drawSlider(GuiGraphics g, Font font, RenderNode node, UiRenderer.State state) {
        Map<?, ?> spec = UiRenderer.propsMap(node, "slider");
        double min = UiRenderer.num(spec.get("min"), 0);
        double max = UiRenderer.num(spec.get("max"), 100);
        Double local = state == null ? null : state.sliderValue(node.id());
        double value = local != null ? local : UiRenderer.num(spec.get("value"), min);
        double ratio = max > min ? (value - min) / (max - min) : 0;
        ratio = Math.max(0, Math.min(1, ratio));
        boolean vertical = UiRenderer.bool(spec.get("vertical"), false);
        // 手柄尺寸与内边距可配（thumbWidth/thumbHeight/paddingLeft/Right/Top/Bottom）
        double thumbW = UiRenderer.num(spec.get("thumbWidth"), 8);
        double thumbH = UiRenderer.num(spec.get("thumbHeight"), 8);
        double padL = UiRenderer.num(spec.get("paddingLeft"), 0);
        double padR = UiRenderer.num(spec.get("paddingRight"), 0);
        double padT = UiRenderer.num(spec.get("paddingTop"), 0);
        double padB = UiRenderer.num(spec.get("paddingBottom"), 0);
        if (vertical) {
            // 竖向滑块：竖轨 + 自下而上填充 + 手柄
            double trackHalf = Math.max(1, thumbW / 4);
            int cx0 = (int) (node.x() + node.width() / 2 - trackHalf);
            int cx1 = (int) (node.x() + node.width() / 2 + trackHalf);
            int top = (int) (node.y() + padT);
            int bottom = (int) (node.y() + node.height() - padB);
            g.fill(cx0, top, cx1, bottom, 0xFF303540);
            g.fill(cx0, (int) (bottom - (bottom - top) * ratio), cx1, bottom, 0xFF7A8BFF);
            int knobY = (int) (bottom - (bottom - top) * ratio);
            g.fill((int) (node.x() + padL), (int) (knobY - thumbH / 2),
                    (int) (node.x() + node.width() - padR), (int) (knobY + thumbH / 2), 0xFFB0C0FF);
        } else {
            double trackHalf = Math.max(1, thumbH / 4);
            int left = (int) (node.x() + padL);
            int right = (int) (node.x() + node.width() - padR);
            int cy = (int) (node.y() + node.height() / 2);
            g.fill(left, cy - (int) trackHalf, right, cy + (int) trackHalf, 0xFF303540);
            g.fill(left, cy - (int) trackHalf, (int) (left + (right - left) * ratio), cy + (int) trackHalf, 0xFF7A8BFF);
            int knobX = (int) (left + (right - left) * ratio);
            g.fill((int) (knobX - thumbW / 2), (int) (node.y() + padT),
                    (int) (knobX + thumbW / 2), (int) (node.y() + node.height() - padB), 0xFFB0C0FF);
        }
    }

    public static void drawProgress(GuiGraphics g, Font font, RenderNode node) {
        Map<?, ?> spec = UiRenderer.propsMap(node, "progress");
        double min = UiRenderer.num(spec.get("min"), 0);
        double max = UiRenderer.num(spec.get("max"), 100);
        double value = UiRenderer.num(spec.get("value"), min);
        double ratio = max > min ? (value - min) / (max - min) : 0;
        ratio = Math.max(0, Math.min(1, ratio));
        String shape = UiRenderer.str(spec.get("shape"));
        if ("arc".equalsIgnoreCase(shape) || "circle".equalsIgnoreCase(shape)) {
            double cx = node.x() + node.width() / 2;
            double cy = node.y() + node.height() / 2;
            double radius = UiRenderer.num(spec.get("radius"), Math.min(node.width(), node.height()) / 2 - 2);
            double thickness = UiRenderer.num(spec.get("thickness"), 6);
            double start = UiRenderer.num(spec.get("startAngle"), -90);
            double sweep = "circle".equalsIgnoreCase(shape) ? 360 : UiRenderer.num(spec.get("sweepAngle"), 270);
            int track = UiStyle.color(spec.get("trackColor"), 0xFF303540);
            int color = UiStyle.color(spec.get("color"), 0xFF4CAF50);
            fillArc(g, cx, cy, radius, thickness, start, sweep, track);
            if (ratio > 0) {
                fillArc(g, cx, cy, radius, thickness, start, start + sweep * ratio - start, color);
            }
            if (UiRenderer.bool(spec.get("showValue"), false)) {
                String text = String.valueOf(Math.round(ratio * 100));
                g.drawString(font, text + "%", (int) (cx - font.width(text + "%") / 2.0F), (int) (cy - 4),
                        UiRenderer.alphaColor(0xFFFFFFFF));
            }
            return;
        }
        UiRenderer.fillRect(g, node, UiRenderer.alphaColor(UiStyle.color(spec.get("background"), 0xFF303540)));
        g.fill((int) node.x(), (int) node.y(), (int) (node.x() + node.width() * ratio), (int) (node.y() + node.height()),
                UiStyle.color(spec.get("color"), 0xFF4CAF50));
    }

    /** 仪表盘（显示型）：轨道弧 + 数值弧 + 数值文本。 */
    public static void drawGauge(GuiGraphics g, Font font, RenderNode node) {
        Map<?, ?> spec = UiRenderer.propsMap(node, "gauge");
        double min = UiRenderer.num(spec.get("min"), 0);
        double max = UiRenderer.num(spec.get("max"), 100);
        double value = UiRenderer.num(spec.get("value"), min);
        double ratio = max > min ? (value - min) / (max - min) : 0;
        ratio = Math.max(0, Math.min(1, ratio));
        double cx = node.x() + node.width() / 2;
        double cy = node.y() + node.height() / 2;
        double radius = UiRenderer.num(spec.get("radius"), Math.min(node.width(), node.height()) / 2 - 2);
        double thickness = UiRenderer.num(spec.get("thickness"), 4);
        double start = UiRenderer.num(spec.get("startAngle"), -90);
        double sweep = UiRenderer.num(spec.get("sweepAngle"), 360);
        int track = UiStyle.color(spec.get("trackColor"), 0xFF303540);
        int color = UiStyle.color(spec.get("color"), 0xFF4CAF50);
        fillArc(g, cx, cy, radius, thickness, start, sweep, track);
        if (ratio > 0) {
            fillArc(g, cx, cy, radius, thickness, start, start + sweep * ratio, color);
        }
        boolean showValue = UiRenderer.bool(spec.get("showValue"), true);
        if (showValue) {
            String text = String.valueOf(Math.round(value * 100) / 100.0);
            g.drawString(font, text, (int) (cx - font.width(text) / 2.0F), (int) (cy - 4),
                    UiRenderer.alphaColor(0xFFFFFFFF));
        }
    }

    /** 环形滑块（交互型）：轨道弧 + 值弧 + 拖拽手柄；拖动触发 INPUT 事件。 */
    public static void drawArcSlider(GuiGraphics g, Font font, RenderNode node, UiRenderer.State state) {
        Map<?, ?> spec = UiRenderer.propsMap(node, "arc_slider");
        double min = UiRenderer.num(spec.get("min"), 0);
        double max = UiRenderer.num(spec.get("max"), 100);
        Double local = state == null ? null : state.sliderValue(node.id());
        double value = local != null ? local : UiRenderer.num(spec.get("value"), min);
        double ratio = max > min ? (value - min) / (max - min) : 0;
        ratio = Math.max(0, Math.min(1, ratio));
        double cx = node.x() + node.width() / 2;
        double cy = node.y() + node.height() / 2;
        double radius = UiRenderer.num(spec.get("radius"), Math.min(node.width(), node.height()) / 2 - 2);
        double thickness = UiRenderer.num(spec.get("thickness"), 4);
        double start = UiRenderer.num(spec.get("startAngle"), -90);
        double sweep = UiRenderer.num(spec.get("sweepAngle"), 360);
        int track = UiStyle.color(spec.get("trackColor"), 0xFF303540);
        int color = UiStyle.color(spec.get("color"), 0xFF7A8BFF);
        fillArc(g, cx, cy, radius, thickness, start, sweep, track);
        if (ratio > 0) {
            fillArc(g, cx, cy, radius, thickness, start, start + sweep * ratio, color);
        }
        // 手柄：值位置的小圆点
        double angle = Math.toRadians(start + sweep * ratio);
        double hx = cx + radius * Math.cos(angle);
        double hy = cy + radius * Math.sin(angle);
        g.fill((int) hx - 3, (int) hy - 3, (int) hx + 3, (int) hy + 3, 0xFFE8EDFF);
    }

    /** 弧形填充（TRIANGLES 扇环：外弧点 + 内弧点逐段）。GUI 坐标，y 向下。 */
    public static void fillArc(GuiGraphics g, double cx, double cy, double radius, double thickness,
                                double startDeg, double sweepDeg, int color) {
        if (sweepDeg == 0 || radius <= 0 || thickness <= 0) {
            return;
        }
        float red = ((color >> 16) & 0xFF) / 255.0F;
        float green = ((color >> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        float alpha = ((color >>> 24) & 0xFF) / 255.0F;
        if (alpha <= 0) {
            return;
        }
        double rOut = radius;
        double rIn = Math.max(0, radius - thickness);
        int segments = Math.max(4, (int) Math.ceil(Math.abs(sweepDeg) / 8.0));
        var matrix = CompatRender.guiMatrix(g);
        CompatRender.enableBlend();
        CompatRender.defaultBlendFunc();
        CompatRender.disableDepthTest();
        CompatRender.setColorShader();
        var builder = CompatRender.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.TRIANGLES,
                        com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < segments; i++) {
            double a0 = Math.toRadians(startDeg + sweepDeg * i / segments);
            double a1 = Math.toRadians(startDeg + sweepDeg * (i + 1) / segments);
            double ox0 = cx + rOut * Math.cos(a0);
            double oy0 = cy + rOut * Math.sin(a0);
            double ox1 = cx + rOut * Math.cos(a1);
            double oy1 = cy + rOut * Math.sin(a1);
            double ix0 = cx + rIn * Math.cos(a0);
            double iy0 = cy + rIn * Math.sin(a0);
            double ix1 = cx + rIn * Math.cos(a1);
            double iy1 = cy + rIn * Math.sin(a1);
            // 四边形 = 2 三角形（外0, 外1, 内0 / 外1, 内1, 内0）
            builder.addVertex(matrix, (float) ox0, (float) oy0, 0).setColor(red, green, blue, alpha);
            builder.addVertex(matrix, (float) ox1, (float) oy1, 0).setColor(red, green, blue, alpha);
            builder.addVertex(matrix, (float) ix0, (float) iy0, 0).setColor(red, green, blue, alpha);
            builder.addVertex(matrix, (float) ox1, (float) oy1, 0).setColor(red, green, blue, alpha);
            builder.addVertex(matrix, (float) ix1, (float) iy1, 0).setColor(red, green, blue, alpha);
            builder.addVertex(matrix, (float) ix0, (float) iy0, 0).setColor(red, green, blue, alpha);
        }
        builder.buildAndDraw();
        CompatRender.enableDepthTest();
        CompatRender.disableBlend();
    }

    public static void drawDropdown(GuiGraphics g, Font font, RenderNode node, UiRenderer.State state, int mouseX, int mouseY) {
        Map<?, ?> spec = UiRenderer.propsMap(node, "dropdown");
        List<?> options = spec.get("options") instanceof List<?> list ? list : List.of();
        String value = state == null ? null : state.dropdownValue(node.id());
        if (value == null) {
            value = UiRenderer.str(spec.get("value"));
        }
        if (value == null && !options.isEmpty()) {
            value = String.valueOf(options.get(0));
        }
        boolean open = state != null && state.dropdownOpen(node.id());
        // 主框
        UiRenderer.fillRect(g, node, UiRenderer.alphaColor(open ? 0xFF2E3340 : 0xFF20242C));
        g.fill((int) node.x(), (int) node.y(), (int) (node.x() + node.width()), (int) (node.y() + 1), 0xFF505868);
        String label = UiRenderer.interpolate(node, value, null);
        if (label != null) {
            g.drawString(font, label, (int) node.x() + 4, (int) (node.y() + (node.height() - 8) / 2), UiRenderer.alphaColor(0xFFFFFFFF));
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
        // 展开选项（maxVisibleOptions 限制可见行数，默认全量）
        int cursor = state == null ? -1 : state.dropdownCursor(node.id());
        int itemH = 14;
        int maxVisible = (int) UiRenderer.num(spec.get("maxVisibleOptions"), options.size());
        int y = (int) (node.y() + node.height());
        for (int i = 0; i < options.size() && i < maxVisible; i++) {
            String option = String.valueOf(options.get(i));
            boolean hover = mouseX >= node.x() && mouseX <= node.x() + node.width()
                    && mouseY >= y && mouseY <= y + itemH;
            boolean active = hover || i == cursor; // 键盘光标优先于鼠标悬停
            g.fill((int) node.x(), y, (int) (node.x() + node.width()), y + itemH,
                    active ? 0xFF3A3F4A : 0xFF181C24);
            g.drawString(font, option, (int) node.x() + 4, y + 3, UiRenderer.alphaColor(0xFFFFFFFF));
            y += itemH;
        }
    }

    /** 多行输入框：\n 换行 + 自动折行 + 纵向滚动（复用 scrollY 状态）。 */
    public static void drawAreaInput(GuiGraphics g, Font font, RenderNode node, UiRenderer.State state) {
        Map<?, ?> spec = UiRenderer.propsMap(node, "area_input");
        String text = state == null ? "" : state.areaText(node.id());
        if (text == null) {
            text = "";
        }
        boolean focused = state != null && state.focused(node.id());
        drawInputBox(g, node, spec, focused);
        if (text.isEmpty()) {
            String hint = UiRenderer.str(spec.get("placeholder"));
            if (hint != null && !hint.isEmpty()) {
                g.drawString(font, hint, (int) node.x() + 4, (int) (node.y() + 3), UiRenderer.alphaColor(0xFF707880));
            }
            return;
        }
        List<FormattedCharSequence> lines = ElementTextUtil.wrapLines(font, text, (int) node.width() - 8);
        double lineH = 9;
        int maxLines = Math.max(1, (int) ((node.height() - 4) / lineH));
        double offset = state == null ? 0 : Math.max(0, state.scrollY(node.id()));
        double maxOffset = Math.max(0, lines.size() - maxLines);
        offset = Math.min(offset, maxOffset);
        int start = (int) offset;
        for (int i = start; i < Math.min(lines.size(), start + maxLines); i++) {
            g.drawString(font, lines.get(i), (int) node.x() + 4, (int) (node.y() + 3 + (i - start) * lineH), UiRenderer.alphaColor(0xFFFFFFFF));
        }
        if (maxOffset > 0) {
            int[] r = UiRenderer.rect(node);
            int trackH = (int) Math.max(10, node.height() - 4);
            double thumbH = Math.max(8, trackH * (maxLines / Math.max(lines.size(), 1)));
            double thumbY = 2 + (trackH - thumbH) * (offset / maxOffset);
            g.fill(r[2] - 3, r[1] + 2, r[2] - 1, r[1] + 2 + trackH, 0x50304050);
            g.fill(r[2] - 3, (int) (r[1] + 2 + thumbY), r[2] - 1, (int) (r[1] + 2 + thumbY + thumbH), 0xB07A8BFF);
        }
    }

    /** 输入建议框：输入框 + 匹配建议下拉（点选后回填并触发 input 事件）。 */
    public static void drawSuggestion(GuiGraphics g, Font font, RenderNode node, UiRenderer.State state, int mouseX, int mouseY) {
        Map<?, ?> spec = UiRenderer.propsMap(node, "suggestion");
        List<?> suggestions = spec.get("suggestions") instanceof List<?> l ? l : List.of();
        String text = state == null ? "" : state.suggestionText(node.id());
        if (text == null) {
            text = "";
        }
        boolean focused = state != null && state.focused(node.id());
        UiRenderer.fillRect(g, node, UiRenderer.alphaColor(0xFF20242C));
        g.fill((int) node.x(), (int) node.y(), (int) (node.x() + node.width()), (int) (node.y() + 1), focused ? 0xFF7A8BFF : 0xFF505868);
        if (focused) {
            g.fill((int) node.x(), (int) (node.y() + node.height() - 1), (int) (node.x() + node.width()), (int) (node.y() + node.height()), 0xFF7A8BFF);
        }
        g.drawString(font, text, (int) node.x() + 4, (int) (node.y() + (node.height() - 8) / 2), UiRenderer.alphaColor(0xFFFFFFFF));
        boolean open = state != null && state.suggestionOpen(node.id());
        if (!open || suggestions.isEmpty()) {
            return;
        }
        List<Object> filtered = filterSuggestions(spec, text);
        int max = (int) Math.max(1, UiRenderer.num(spec.get("max"), 6));
        int itemH = 14;
        int shown = Math.min(filtered.size(), max);
        int cursor = state == null ? -1 : state.suggestionCursor(node.id());
        int y = (int) (node.y() + node.height());
        g.fill((int) node.x(), y, (int) (node.x() + node.width()), y + shown * itemH, 0xE0181C24);
        g.fill((int) node.x(), y, (int) (node.x() + node.width()), y + 1, 0xFF505868);
        for (int i = 0; i < shown; i++) {
            String label = suggestionLabel(filtered.get(i));
            boolean hover = mouseX >= node.x() && mouseX <= node.x() + node.width()
                    && mouseY >= y && mouseY < y + itemH;
            if (i == cursor) {
                g.fill((int) node.x(), y, (int) (node.x() + node.width()), y + itemH, 0xFF2F4A66); // 键盘光标
            } else if (hover) {
                g.fill((int) node.x(), y, (int) (node.x() + node.width()), y + itemH, 0xFF3A3F4A);
            }
            g.drawString(font, label, (int) node.x() + 4, y + 3, UiRenderer.alphaColor(0xFFFFFFFF));
            y += itemH;
        }
    }

    public static String suggestionLabel(Object s) {
        if (s instanceof Map<?, ?> m) {
            Object label = m.get("label");
            if (label != null) {
                return String.valueOf(label);
            }
            Object value = m.get("value");
            if (value != null) {
                return String.valueOf(value);
            }
        }
        return String.valueOf(s);
    }

    /** 建议项的值（选中后回填输入框的内容）。 */
    public static String suggestionValue(Object s) {
        if (s instanceof Map<?, ?> m) {
            Object value = m.get("value");
            if (value != null) {
                return String.valueOf(value);
            }
            Object label = m.get("label");
            if (label != null) {
                return String.valueOf(label);
            }
        }
        return String.valueOf(s);
    }

    /** 按当前输入过滤建议列表（前缀匹配，大小写不敏感）；交互层与绘制层共用。 */
    public static List<Object> filterSuggestions(Map<?, ?> spec, String text) {
        List<?> suggestions = spec.get("suggestions") instanceof List<?> l ? l : List.of();
        List<Object> filtered = new ArrayList<>();
        for (Object s : suggestions) {
            String label = suggestionLabel(s);
            if (text == null || text.isEmpty()
                    || label.toLowerCase(java.util.Locale.ROOT).startsWith(text.toLowerCase(java.util.Locale.ROOT))) {
                filtered.add(s);
            }
        }
        return filtered;
    }
}
