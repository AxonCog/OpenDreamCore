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
        // 文本随设计画布整体缩放：design 页（s≠1）下包一层 pose scale，
        // 几何按 1/s 转回设计系绘制（字号不动、折行/测量同口径，整数倍 s 下最锐）。
        // IDENTITY（s=1）直接直通，老包一帧不挪、零开销。
        double s = com.opendreamcore.ui.Viewport.active().scale();
        if (s != 1.0 && s > 0) {
            var pose = g.pose();
            CompatRender.posePush(pose);
            CompatRender.poseTranslate(pose, node.x(), node.y());
            CompatRender.poseScale(pose, (float) s, (float) s);
            RenderNode designNode = new RenderNode(node.id(), node.type(), node.source(),
                    node.x() / s, node.y() / s, node.width() / s, node.height() / s,
                    node.visible(), node.enabled(), node.children(), node.props());
            drawTextInner(g, font, designNode, pageVars, scope);
            CompatRender.posePop(pose);
            return;
        }
        drawTextInner(g, font, node, pageVars, scope);
    }

    /** 文本绘制主体（坐标/尺寸按传入节点所在坐标系，默认屏幕系；design 页由外层缩放包装喂设计系）。 */
    private static void drawTextInner(GuiGraphics g, Font font, RenderNode node, java.util.Map<String, Object> pageVars, String scope) {
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
        String fontName = UiRenderer.str(node.props().get("font"));
        TtfRenderer custom;
        if (fontName == null || fontName.isBlank()) {
            // FontConfig 全局字体（ttf: 键 = 用户指定的路径）回退；getByPath 按路径解析
            String defTtf = com.opendreamcore.client.visual.VisualFontReplace.defaultTtf();
            custom = defTtf == null || defTtf.isBlank() ? null : CustomFonts.getByPath(defTtf);
        } else {
            custom = CustomFonts.get(fontName);
        }
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
            drawWithGlyphOverlay(g, font, content, (int) x, (int) y, color, shadow);
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
            drawWithGlyphOverlay(g, font, line, (int) lx, (int) ly, color, shadow);
        }
    }

    /**
     * FontConfig 挂点：文本绘制口。
     * 委托 GlyphRenderer 拆段绘制（普通段批画、命中字符贴图顶替；贴图缺失回退原版），
     * 没规则/无命中直接直通 drawString（零开销快路径）。
     * 画完查字形叠加（VisualFontOverride）贴右侧装饰。
     */
    private static void drawWithGlyphOverlay(GuiGraphics g, Font font, String text,
                                             int x, int y, int color, boolean shadow) {
        if (!com.opendreamcore.client.visual.GlyphRenderer.renderGui(g, font, text, x, y, color, shadow)) {
            if (shadow) {
                g.drawString(font, text, x, y, UiRenderer.alphaColor(color), true);
            } else {
                g.drawString(font, text, x, y, UiRenderer.alphaColor(color));
            }
        }
        var glyph = com.opendreamcore.client.visual.VisualFontOverride.overlayFor(text);
        if (glyph == null) {
            return;
        }
        // 颜色：规则没声明就跟随正文色；声明了用规则色（parse 时已转 ARGB）
        int gc = glyph.color() < 0 ? color : glyph.color();
        g.drawString(font, glyph.overlay(), x + font.width(text), y,
                UiRenderer.alphaColor(gc), shadow);
    }

    private static void drawStringPlain(GuiGraphics g, Font font, String seg,
                                        int x, int y, int color, boolean shadow) {
        if (shadow) {
            g.drawString(font, seg, x, y, UiRenderer.alphaColor(color), true);
        } else {
            g.drawString(font, seg, x, y, UiRenderer.alphaColor(color));
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
