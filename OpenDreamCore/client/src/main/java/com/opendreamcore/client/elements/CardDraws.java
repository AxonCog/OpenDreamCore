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
 * C2 拆分自 ScreenElements（CardDraws 组件族）。方法体逐字搬运，可见性放宽为 public。
 */
public final class CardDraws {
    private CardDraws() {}

    public static void drawCard(GuiGraphics g, Font font, RenderNode node, java.util.Map<String, Object> pageVars) {
        Map<?, ?> spec = UiRenderer.propsMap(node, "card");
        double timeout = UiRenderer.num(spec.get("timeout"), UiRenderer.num(spec.get("autoClose"), 0));
        if (timeout > 0 && node.props().get("_cardShowAt") == null) {
            node.props().put("_cardShowAt", String.valueOf(System.currentTimeMillis()));
        }
        if (timeout > 0) {
            Object at = node.props().get("_cardShowAt");
            long showAt = 0;
            try { showAt = Long.parseLong(String.valueOf(at)); } catch (Exception ignored) {}
            if (showAt > 0 && System.currentTimeMillis() - showAt >= (long) timeout) {
                node.props().put("_cardExpired", "true");
                return;
            }
        }
        if ("true".equals(String.valueOf(node.props().get("_cardExpired")))) return;
        int bg = UiStyle.color(spec.get("background"), 0xFF1E222B);
        UiRenderer.fillRect(g, node, UiRenderer.alphaColor(bg));
        drawCardFrame(g, node, UiStyle.color(spec.get("border"), 0xFF3A4254));
        double pad = 8;
        double tx = node.x() + pad;
        double ty = node.y() + pad;
        Object icon = spec.get("icon");
        if (icon != null && !String.valueOf(icon).isBlank()) {
            UiRenderer.drawItemAt(g, font, tx, ty, 16, icon, pageVars);
            tx += 20;
        }
        String title = UiRenderer.interpolate(node, UiRenderer.str(spec.get("title")), pageVars);
        if (title != null && !title.isEmpty()) {
            g.drawString(font, title, (int) tx, (int) ty, UiRenderer.alphaColor(0xFFFFFFFF), true);
            ty += 11;
        }
        String subtitle = UiRenderer.interpolate(node, UiRenderer.str(spec.get("subtitle")), pageVars);
        if (subtitle != null && !subtitle.isEmpty()) {
            g.drawString(font, subtitle, (int) tx, (int) ty, UiRenderer.alphaColor(0xFF9AA3B2));
            ty += 10;
        }
        String content = UiRenderer.interpolate(node, UiRenderer.str(spec.get("content")), pageVars);
        if (content != null && !content.isEmpty()) {
            double contentW = node.width() - pad * 2;
            List<FormattedCharSequence> lines = font.split(Component.literal(content), (int) Math.max(8, contentW));
            for (FormattedCharSequence line : lines) {
                if (ty > node.y() + node.height() - 12) {
                    break;
                }
                g.drawString(font, line, (int) tx, (int) ty, UiRenderer.alphaColor(0xFFC8CFDA));
                ty += 9;
            }
        }
        Object actionsRaw = spec.get("actions");
        if (actionsRaw instanceof List<?> actions && !actions.isEmpty()) {
            double btnY = node.y() + node.height() - 18;
            double bx = node.x() + node.width() - pad;
            for (int i = actions.size() - 1; i >= 0; i--) {
                Object a = actions.get(i);
                Map<?, ?> am = a instanceof Map<?, ?> m ? m : Map.of("label", String.valueOf(a));
                String label = UiRenderer.interpolate(node, UiRenderer.str(am.get("label")), pageVars);
                if (label == null || label.isEmpty()) continue;
                int lw = font.width(label) + 10;
                bx -= lw + 4;
                g.fill((int) bx, (int) btnY, (int) (bx + lw), (int) (btnY + 12), 0xFF3A4254);
                g.drawString(font, label, (int) (bx + 5), (int) (btnY + 2), UiRenderer.alphaColor(0xFFFFFFFF));
            }
        }
        String footer = UiRenderer.interpolate(node, UiRenderer.str(spec.get("footer")), pageVars);
        if (footer != null && !footer.isEmpty()) {
            g.drawString(font, footer, (int) (node.x() + pad), (int) (node.y() + node.height() - 10), UiRenderer.alphaColor(0xFF6B7280));
        }
    }

    /** 卡片外框：1px 描边。 */
    public static void drawCardFrame(GuiGraphics g, RenderNode node, int border) {
        int x1 = (int) node.x();
        int y1 = (int) node.y();
        int x2 = (int) (node.x() + node.width());
        int y2 = (int) (node.y() + node.height());
        g.fill(x1, y1, x2, y1 + 1, border);
        g.fill(x1, y2 - 1, x2, y2, border);
        g.fill(x1, y1, x1 + 1, y2, border);
        g.fill(x2 - 1, y1, x2, y2, border);
    }

    /** 翻牌卡片：点击翻转，支持 X/Y 轴 + scaleX/Y 兼容。 */
    public static void drawFlipCard(GuiGraphics g, Font font, RenderNode node, UiRenderer.State state,
                                     java.util.Map<String, Object> pageVars) {
        Map<?, ?> spec = UiRenderer.propsMap(node, "flip_card");
        Map<?, ?> front = spec.get("front") instanceof Map<?, ?> m ? m : Map.of();
        Map<?, ?> back = spec.get("back") instanceof Map<?, ?> m ? m : Map.of();
        double p = state == null ? 0 : state.flipProgress(node.id());
        String axis = UiRenderer.str(spec.get("axis"));
        if (axis == null) axis = UiRenderer.str(spec.get("direction"));
        boolean vertical = "y".equalsIgnoreCase(axis) || "vertical".equalsIgnoreCase(axis);
        double scale;
        Map<?, ?> face;
        if (p < 0.5) {
            scale = 1 - p * 2;
            face = front;
        } else {
            scale = (p - 0.5) * 2;
            face = back;
        }
        scale = Math.max(0.02, scale);
        var pose = g.pose();
        CompatRender.posePush(pose);
        double cx = node.x() + node.width() / 2;
        double cy = node.y() + node.height() / 2;
        CompatRender.poseTranslate(pose, cx, cy);
        if (vertical) CompatRender.poseScale(pose, 1.0, scale);
        else CompatRender.poseScale(pose, scale, 1.0);
        CompatRender.poseTranslate(pose, -cx, -cy);
        drawCardFace(g, font, node, face, pageVars);
        CompatRender.posePop(pose);
    }

    public static void drawCardFace(GuiGraphics g, Font font, RenderNode node, Map<?, ?> face,
                                     java.util.Map<String, Object> pageVars) {
        int bg = UiStyle.color(face.get("background"), 0xFF1E222B);
        UiRenderer.fillRect(g, node, UiRenderer.alphaColor(bg));
        drawCardFrame(g, node, UiStyle.color(face.get("border"), 0xFF3A4254));
        String title = UiRenderer.interpolate(node, UiRenderer.str(face.get("title")), pageVars);
        String content = UiRenderer.interpolate(node, UiRenderer.str(face.get("content")), pageVars);
        double cx = node.x() + node.width() / 2;
        if (title != null && !title.isEmpty()) {
            g.drawString(font, title, (int) (cx - font.width(title) / 2.0), (int) (node.y() + node.height() * 0.3), UiRenderer.alphaColor(0xFFFFFFFF), true);
        }
        if (content != null && !content.isEmpty()) {
            List<FormattedCharSequence> lines = font.split(Component.literal(content), (int) Math.max(8, node.width() - 16));
            double y = node.y() + node.height() * 0.48;
            for (FormattedCharSequence line : lines) {
                g.drawString(font, line, (int) (cx - font.width(line) / 2.0), (int) y, UiRenderer.alphaColor(0xFFC8CFDA));
                y += 9;
            }
        }
    }
}
