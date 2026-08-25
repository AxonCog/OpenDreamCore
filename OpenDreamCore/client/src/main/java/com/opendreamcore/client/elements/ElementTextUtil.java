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
 * C2 拆分自 ScreenElements（ElementTextUtil 组件族）。方法体逐字搬运，可见性放宽为 public。
 */
public final class ElementTextUtil {
    private ElementTextUtil() {}

    /** 文本按宽度折行（\n 强制换行 + 自动折行）。 */
    public static List<FormattedCharSequence> wrapLines(Font font, String text, int maxWidth) {
        List<FormattedCharSequence> out = new ArrayList<>();
        for (String line : text.split("\n", -1)) {
            out.addAll(font.split(Component.literal(line), Math.max(8, maxWidth)));
        }
        return out;
    }

    /** 文本按宽度折行 → 字符串行数组（逐字符折行，与布局测量一致；供 drawText/自动高度共用）。 */
    public static String[] wrapLinesFlat(Font font, String text, int maxWidth) {
        List<String> out = new ArrayList<>();
        int maxPx = Math.max(8, maxWidth);
        for (String rawLine : text.split("\n", -1)) {
            if (rawLine.isEmpty()) {
                out.add("");
                continue;
            }
            StringBuilder cur = new StringBuilder();
            for (int i = 0; i < rawLine.length(); i++) {
                String ch = String.valueOf(rawLine.charAt(i));
                if (font.width(cur + ch) > maxPx && cur.length() > 0) {
                    out.add(cur.toString());
                    cur = new StringBuilder(ch);
                } else {
                    cur.append(ch);
                }
            }
            out.add(cur.toString());
        }
        return out.toArray(new String[0]);
    }
}
