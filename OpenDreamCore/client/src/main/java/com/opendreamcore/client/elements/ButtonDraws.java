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
 * C2 拆分自 ScreenElements（ButtonDraws 组件族）。方法体逐字搬运，可见性放宽为 public。
 */
public final class ButtonDraws {
    private ButtonDraws() {}

    public static void drawButton(GuiGraphics g, Font font, RenderNode node, int mouseX, int mouseY,
                                   java.util.Map<String, Object> pageVars) {
        Map<?, ?> spec = UiRenderer.propsMap(node, "button");
        boolean hover = node.enabled() && node.contains(mouseX, mouseY);
        boolean pressed = hover && net.minecraft.client.Minecraft.getInstance().mouseHandler.isLeftPressed();
        boolean checked = UiRenderer.bool(spec.get("checked"), false);
        // 背景色：disabled 灰 / hover 优先 hoverColor / 否则 background（color 为 background 别名）
        int bg;
        if (!node.enabled()) {
            bg = 0xFF20242C;
        } else if (hover) {
            bg = UiStyle.color(spec.get("hoverColor"),
                    UiStyle.color(spec.get("background"), UiStyle.color(spec.get("color"), 0xFF3A3F4A)));
        } else {
            bg = UiStyle.color(spec.get("background"), UiStyle.color(spec.get("color"), 0xFF2A2F3A));
        }
        // 五态贴图：disabled / checked / pressed / hover / normal（优先级从高到低；
        // 每态支持 字符串=整图 或 {path,x,y,width,height}=源区域；缺图回退纯色）
        Object texRaw;
        if (!node.enabled() && spec.get("disabled") != null) {
            texRaw = spec.get("disabled");
        } else if (checked && spec.get("checked") != null) {
            texRaw = spec.get("checked");
        } else if (pressed && spec.get("pressed") != null) {
            texRaw = spec.get("pressed");
        } else if (hover && spec.get("hover") != null) {
            texRaw = spec.get("hover");
        } else {
            texRaw = spec.get("normal");
        }
        if (MediaItemDraws.drawStateTexture(g, texRaw, node)) {
            // 贴图已绘制
        } else {
            // 圆角/描边回退（button.radius / button.border / button.borderWidth；border 支持 {color,width,flow,flowColor}）
            double radius = UiRenderer.num(spec.get("radius"), UiRenderer.num(node.props().get("radius"), 0));
            UiRenderer.BorderSpec bs = UiRenderer.parseBorder(spec.get("border"), (int) UiRenderer.num(spec.get("borderWidth"), 1));
            boolean flow = bs.flow() || UiRenderer.bool(spec.get("flow"), false);
            int flowColor = UiStyle.color(spec.get("flowColor"), bs.flowColor());
            if (radius > 0 || bs.color() != 0) {
                if (flow) {
                    UiRenderer.drawRoundedRectFlow(g, node, radius, UiRenderer.alphaColor(bg), bs.color(), bs.width(), flowColor,
                            hover);
                } else {
                    UiRenderer.drawRoundedRect(g, node, radius, UiRenderer.alphaColor(bg), bs.color(), bs.width());
                }
            } else {
                UiRenderer.fillRect(g, node, UiRenderer.alphaColor(bg));
                g.fill((int) node.x(), (int) node.y(), (int) (node.x() + node.width()), (int) (node.y() + 1), 0xFF505868);
                g.fill((int) node.x(), (int) (node.y() + node.height() - 1), (int) (node.x() + node.width()), (int) (node.y() + node.height()), 0xFF20242C);
            }
        }
        String label = UiRenderer.interpolate(node, UiRenderer.str(spec.get("label")), pageVars);
        if (label != null && !label.isEmpty()) {
            // 逐字揭示（button.reveal 或 button.revealIntervalMs，两种写法兼容）
            Object rev = spec.get("reveal");
            Object rim = spec.get("revealIntervalMs");
            if (rev == null && rim != null) {
                double ms = UiRenderer.num(rim, 0);
                if (ms > 0) rev = Map.of("speed", ms);
            }
            if (rev != null && !Boolean.FALSE.equals(rev)) {
                Map<Object, Object> rspec = new java.util.LinkedHashMap<>();
                rspec.put("reveal", rev);
                label = TextElements.applyReveal(node, rspec, label, null);
            }
            int labelColor = UiStyle.color(spec.get("textColor"), node.enabled() ? 0xFFFFFFFF : 0xFF808080);
            TtfRenderer custom = CustomFonts.get(UiRenderer.str(node.props().get("font")));
            if (custom != null) {
                double lx = node.x() + (node.width() - custom.measure(label, 1.0)) / 2;
                double ly = node.y() + (node.height() - 8) / 2;
                custom.draw(g, label, lx, ly, labelColor, 1.0, false);
            } else {
                int lx = (int) (node.x() + (node.width() - font.width(label)) / 2);
                int ly = (int) (node.y() + (node.height() - 8) / 2);
                g.drawString(font, label, lx, ly, UiRenderer.alphaColor(labelColor));
            }
        }
        double cd = UiRenderer.num(spec.get("cooldown"), UiRenderer.num(spec.get("coolDown"), 0));
        if (cd > 0 && cd < 1) {
            int cover = UiStyle.color(spec.get("cooldownColor"), 0xAA000000);
            g.fill((int) node.x(), (int) node.y(), (int) (node.x() + node.width() * cd), (int) (node.y() + node.height()), cover);
        }
    }

    /** 复选框：方框 + 勾号 + 标签；点击切换（CLICK 事件带 true/false）。 */
    public static void drawCheckbox(GuiGraphics g, Font font, RenderNode node, UiRenderer.State state) {
        Map<?, ?> spec = UiRenderer.propsMap(node, "checkbox");
        Boolean local = state == null ? null : state.toggleValue(node.id());
        boolean checked = local != null ? local : UiRenderer.bool(spec.get("value"), false);
        String label = UiRenderer.interpolate(node, UiRenderer.str(spec.get("label")), null);
        int box = (int) Math.min(node.height(), 14);
        int bx = (int) node.x();
        int by = (int) (node.y() + (node.height() - box) / 2);
        int accent = UiStyle.color(spec.get("color"), 0xFF7A8BFF);
        g.fill(bx, by, bx + box, by + box, UiRenderer.alphaColor(checked ? 0xFF2A3355 : 0xFF20242C));
        g.fill(bx, by, bx + box, by + 1, accent);
        g.fill(bx, by + box - 1, bx + box, by + box, accent);
        g.fill(bx, by, bx + 1, by + box, accent);
        g.fill(bx + box - 1, by, bx + box, by + box, accent);
        if (checked) {
            // 勾号：左下→右上两段线
            g.fill(bx + 3, by + box - 4, bx + 5, by + box - 2, accent);
            g.fill(bx + 5, by + box - 6, bx + 6, by + box - 4, accent);
            g.fill(bx + 6, by + box - 7, bx + 7, by + box - 6, accent);
            g.fill(bx + 7, by + box - 8, bx + 9, by + box - 6, accent);
            g.fill(bx + 9, by + box - 11, bx + 10, by + box - 8, accent);
            g.fill(bx + 10, by + box - 13, bx + 11, by + box - 11, accent);
            g.fill(bx + 11, by + box - 14, bx + 12, by + box - 13, accent);
        }
        if (label != null && !label.isEmpty()) {
            g.drawString(font, label, bx + box + 4, (int) (node.y() + (node.height() - 8) / 2),
                    UiRenderer.alphaColor(0xFFFFFFFF));
        }
    }

    public static void drawToggle(GuiGraphics g, Font font, RenderNode node, UiRenderer.State state) {
        Map<?, ?> spec = UiRenderer.propsMap(node, "toggle");
        Boolean local = state == null ? null : state.toggleValue(node.id());
        boolean on = local != null ? local : UiRenderer.bool(spec.get("value"), false);
        String label = UiRenderer.interpolate(node, UiRenderer.str(spec.get("label")), null);
        int h = (int) node.height();
        // 单选组（toggle.radio: 组名）：圆圈 + 选中圆点外观；同组互斥由 OdcScreen.applyRadioGroup 保证
        Object radioRaw = spec.get("radio");
        boolean radio = radioRaw != null && !String.valueOf(radioRaw).isBlank()
                && !"null".equalsIgnoreCase(String.valueOf(radioRaw));
        if (radio) {
            int r = (int) Math.min(h * 0.45, 9);
            int cx = (int) (node.x() + node.width() - r - 3);
            int cy = (int) (node.y() + h / 2.0);
            UiRenderer.fillRounded(g, cx - r, cy - r, r * 2, r * 2, r, on ? 0xFF4CAF50 : 0xFF303540);
            UiRenderer.fillRounded(g, cx - r + 2, cy - r + 2, r * 2 - 4, r * 2 - 4, Math.max(1, r - 2), 0xFF10141A);
            if (on) {
                int dot = Math.max(2, r - 3);
                UiRenderer.fillRounded(g, cx - dot / 2.0, cy - dot / 2.0, dot, dot, dot / 2.0, 0xFF4CAF50);
            }
        } else {
            int trackW = (int) Math.min(node.width(), h * 2);
            int trackX = (int) (node.x() + node.width() - trackW);
            // 轨道
            g.fill(trackX, (int) node.y() + 1, trackX + trackW, (int) (node.y() + h - 1), on ? 0xFF4CAF50 : 0xFF303540);
            // 滑块
            int knobX = on ? trackX + trackW - h + 2 : trackX + 2;
            g.fill(knobX, (int) node.y() + 2, knobX + h - 4, (int) (node.y() + h - 2), 0xFFFFFFFF);
        }
        if (label != null && !label.isEmpty()) {
            g.drawString(font, label, (int) node.x(), (int) (node.y() + (h - 8) / 2), UiRenderer.alphaColor(0xFFFFFFFF));
        }
    }
}
