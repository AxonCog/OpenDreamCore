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
 * C2 拆分自 ScreenElements（BadgeIconDraws 组件族）。方法体逐字搬运，可见性放宽为 public。
 */
public final class BadgeIconDraws {
    private BadgeIconDraws() {}

    /** 屏幕角标（badge）：true = 红点；数字 = 数量徽标；{count, color} = 计数 + 自定义色。右上角，随元素变换。 */
    public static void drawScreenBadge(GuiGraphics g, Font font, RenderNode node, Object badgeProp) {
        if (Boolean.FALSE.equals(badgeProp)) {
            return;
        }
        int color = 0xFFE53935;
        String text = null;
        boolean dot = false;
        if (badgeProp instanceof Map<?, ?> m) {
            color = UiStyle.color(m.get("color"), color);
            Object cv = m.get("count");
            if (cv == null) {
                dot = true;
            } else if (cv instanceof Boolean b) {
                dot = b;
            } else if (cv instanceof Number n) {
                text = String.valueOf(n.intValue());
            } else {
                text = String.valueOf(cv);
            }
        } else if (badgeProp instanceof Boolean b) {
            dot = b;
        } else if (badgeProp instanceof Number n) {
            text = String.valueOf(n.intValue());
        } else {
            text = String.valueOf(badgeProp);
        }
        if (!dot && text == null) {
            return;
        }
        int cx = (int) (node.x() + node.width() - 2);
        int cy = (int) node.y() + 1;
        if (dot) {
            g.fill(cx - 4, cy, cx, cy + 4, UiRenderer.alphaColor(color));
            return;
        }
        int w = font.width(text) + 4;
        g.fill(cx - w, cy, cx, cy + 8, UiRenderer.alphaColor(color));
        g.drawString(font, text, cx - w + 2, cy + 1, UiRenderer.alphaColor(0xFFFFFFFF));
    }

    /** 屏幕状态图标（statusIcon）：文本或 {icon, color}。左上角，随元素变换。 */
    public static void drawScreenStatusIcon(GuiGraphics g, Font font, RenderNode node, Object statusProp) {
        String icon;
        int color = 0xFF4FC3F7;
        if (statusProp instanceof Map<?, ?> m) {
            icon = UiRenderer.str(m.get("icon"));
            color = UiStyle.color(m.get("color"), color);
        } else {
            icon = UiRenderer.str(statusProp);
        }
        if (icon == null || icon.isEmpty()) {
            return;
        }
        g.drawString(font, icon, (int) node.x() + 1, (int) node.y() + 1, UiRenderer.alphaColor(color));
    }
}
