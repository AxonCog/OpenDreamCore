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
 * C2 拆分自 ScreenElements（TextElements 组件族）。方法体逐字搬运，可见性放宽为 public。
 */
public final class TextElements {
    private TextElements() {}

    public static void drawText(GuiGraphics g, Font font, RenderNode node, java.util.Map<String, Object> pageVars, String scope) {
        Map<?, ?> spec = UiRenderer.propsMap(node, "text");
        String content = UiRenderer.interpolate(node, UiRenderer.str(spec.get("content")), pageVars);
        if (content == null || content.isEmpty()) {
            return;
        }
        content = applyReveal(node, spec, content, scope); // text.reveal 逐字揭示（scope 隔离，内容变化重触发）
        if (content.isEmpty()) {
            return;
        }
        int color = UiStyle.color(spec.get("color"), 0xFFFFFFFF);
        boolean shadow = UiRenderer.bool(spec.get("shadow"), false);
        int strokeColor = UiStyle.color(spec.get("strokeColor"), 0);
        double strokeWidth = UiRenderer.num(spec.get("strokeWidth"), spec.get("strokeColor") != null ? 1 : 0);
        String align = UiRenderer.str(spec.get("align"));
        double scale = UiRenderer.num(spec.get("scale"), 1.0);
        double lineHeight = UiRenderer.num(spec.get("lineHeight"), 9);
        // 折行：text.wrap（px 宽）或 autoHeight（自动高度 → 按元素宽度折行，与布局测量一致）
        String[] lines;
        double wrapPx = UiRenderer.num(spec.get("wrap"), 0);
        boolean autoH = UiRenderer.bool(spec.get("autoHeight"), false);
        if (wrapPx > 0) {
            lines = ElementTextUtil.wrapLinesFlat(font, content, (int) Math.max(8, wrapPx));
        } else if (autoH) {
            lines = ElementTextUtil.wrapLinesFlat(font, content, (int) Math.max(8, node.width()));
        } else {
            lines = content.split("\n", -1);
        }
        TtfRenderer custom = CustomFonts.get(UiRenderer.str(node.props().get("font")));
        double x = node.x();
        double y = node.y();
        if (custom != null) {
            // 自定义字体：按最长行做对齐，逐行绘制
            double maxW = 0;
            for (String line : lines) {
                maxW = Math.max(maxW, custom.measure(line, scale));
            }
            if ("center".equals(align)) {
                x += (node.width() - maxW) / 2;
            } else if ("right".equals(align)) {
                x += node.width() - maxW;
            }
            for (int i = 0; i < lines.length; i++) {
                custom.draw(g, lines[i], x, y + i * lineHeight, color, scale, shadow);
            }
            return;
        }
        if (lines.length == 1) {
            if ("center".equals(align)) {
                x += (int) ((node.width() - font.width(content)) / 2);
            } else if ("right".equals(align)) {
                x += (int) (node.width() - font.width(content));
            }
            drawTextStroke(g, font, content, (int) x, (int) y, strokeColor, strokeWidth);
            if (shadow) {
                g.drawString(font, content, (int) x, (int) y, UiRenderer.alphaColor(color), true);
            } else {
                g.drawString(font, content, (int) x, (int) y, UiRenderer.alphaColor(color));
            }
            return;
        }
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.isEmpty()) {
                continue;
            }
            double lx = node.x();
            if ("center".equals(align)) {
                lx += (int) ((node.width() - font.width(line)) / 2);
            } else if ("right".equals(align)) {
                lx += (int) (node.width() - font.width(line));
            }
            double ly = y + i * lineHeight;
            drawTextStroke(g, font, line, (int) lx, (int) ly, strokeColor, strokeWidth);
            if (shadow) {
                g.drawString(font, line, (int) lx, (int) ly, UiRenderer.alphaColor(color), true);
            } else {
                g.drawString(font, line, (int) lx, (int) ly, UiRenderer.alphaColor(color));
            }
        }
    }

    public static void drawTextStroke(GuiGraphics g, Font font, String text, int x, int y,
                                       int strokeColor, double strokeWidth) {
        if (strokeWidth <= 0 || (strokeColor >>> 24) == 0) return;
        int sw = Math.max(1, (int) Math.round(strokeWidth));
        int sc = UiRenderer.alphaColor(strokeColor);
        for (int dx = -sw; dx <= sw; dx++) {
            for (int dy = -sw; dy <= sw; dy++) {
                if (dx == 0 && dy == 0) continue;
                if (sw > 1 && Math.abs(dx) + Math.abs(dy) > sw) continue;
                g.drawString(font, text, x + dx, y + dy, sc);
            }
        }
    }

    public record RevealState(long startMs, String content) {}

    public static String revealKey(RenderNode node, String scope) {
        return scope == null || scope.isEmpty() ? node.id() : scope + "\u0001" + node.id();
    }

    public static void pruneRevealIfNeeded() {
        if (UiRenderer.textRevealState.size() > UiRenderer.REVEAL_PRUNE_THRESHOLD) {
            var it = UiRenderer.textRevealState.keySet().iterator();
            int toRemove = UiRenderer.REVEAL_PRUNE_THRESHOLD / 2;
            for (int i = 0; i < toRemove && it.hasNext(); i++) { it.next(); it.remove(); }
        }
    }

    /** 可见字符数（忽略 § 颜色码）。 */
    public static int visibleCharCount(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '§' && i + 1 < s.length()) {
                i++;
                continue;
            }
            n++;
        }
        return n;
    }

    /** 取前 n 个可见字符（保留 § 颜色码；颜色码不计入可见数）。 */
    public static String sliceVisible(String s, int n) {
        if (n <= 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int seen = 0;
        for (int i = 0; i < s.length() && seen < n; i++) {
            char c = s.charAt(i);
            if (c == '§' && i + 1 < s.length()) {
                sb.append(c).append(s.charAt(i + 1));
                i++;
                continue;
            }
            sb.append(c);
            seen++;
        }
        return sb.toString();
    }

    /**
     * text.reveal 逐字揭示：true = 25ms/字；数字 = 毫秒/字；{speed, delay, loop}。
     * 按 scope+元素首次渲染起算，逐字浮现（§ 颜色码保留）；内容变化重触发；loop = 循环重放。
     */
    public static String applyReveal(RenderNode node, Map<?, ?> spec, String content, String scope) {
        Object rev = spec.get("reveal");
        if (rev == null || Boolean.FALSE.equals(rev)) {
            return content;
        }
        double speed = 25;
        double delay = 0;
        boolean loop = false;
        if (rev instanceof Map<?, ?> rm) {
            speed = UiRenderer.num(rm.get("speed"), speed);
            delay = UiRenderer.num(rm.get("delay"), 0);
            loop = Boolean.parseBoolean(String.valueOf(rm.get("loop")));
        } else if (rev instanceof Number n) {
            speed = n.doubleValue();
        }
        if (speed <= 0) {
            return content;
        }
        int total = visibleCharCount(content);
        if (total <= 0) {
            return content;
        }
        pruneRevealIfNeeded();
        String key = revealKey(node, scope);
        RevealState state = UiRenderer.textRevealState.get(key);
        if (state == null || !content.equals(state.content())) {
            state = new RevealState(System.currentTimeMillis(), content);
            UiRenderer.textRevealState.put(key, state);
        }
        long start = state.startMs();
        double p = (System.currentTimeMillis() - start - delay) / (total * speed);
        if (loop) {
            p = p % 1.0;
            if (p < 0) {
                p += 1.0;
            }
        }
        p = Math.max(0, Math.min(1, p));
        return sliceVisible(content, (int) Math.floor(p * total));
    }
}
