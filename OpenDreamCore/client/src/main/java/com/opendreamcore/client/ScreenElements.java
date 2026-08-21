package com.opendreamcore.client;

import com.opendreamcore.ui.RenderNode;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 屏幕元素渲染器集。
 * 文本/按钮/输入/滑块/图片/视频/槽位/卡片/图表/罗盘/画布/Boss条/表格等逐元素绘制。
 * 绘制原语与共享工具仍在 UiRenderer（同包直接调用）。
 */
final class ScreenElements {

    private ScreenElements() {
    }

    static void drawText(GuiGraphics g, Font font, RenderNode node, java.util.Map<String, Object> pageVars, String scope) {
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
            lines = wrapLinesFlat(font, content, (int) Math.max(8, wrapPx));
        } else if (autoH) {
            lines = wrapLinesFlat(font, content, (int) Math.max(8, node.width()));
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

    static void drawTextStroke(GuiGraphics g, Font font, String text, int x, int y,
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

    /**
     * 状态贴图绘制（button 四态 / input.normal+focus 通用）：
     * 字符串 = 整图路径；对象 = {path, x, y, width, height(或 sourceHeight), textureWidth, textureHeight} 源区域。
     * 源区域会拉伸铺满元素；返回 false 表示未提供贴图（调用方回退纯色绘制）。
     */
    static boolean drawStateTexture(GuiGraphics g, Object raw, RenderNode node) {
        if (raw == null) {
            return false;
        }
        net.minecraft.resources.ResourceLocation tex;
        int sx = 0;
        int sy = 0;
        int sw = 0;
        int sh = 0;
        int tw = 256;
        int th = 256;
        if (raw instanceof String s) {
            tex = UiStyle.texture(UiRenderer.str(s));
            if (tex == null) {
                return false;
            }
        } else if (raw instanceof java.util.Map<?, ?> tm) {
            tex = UiStyle.texture(UiRenderer.str(tm.get("path")));
            if (tex == null) {
                return false;
            }
            sx = (int) UiRenderer.num(tm.get("x"), 0);
            sy = (int) UiRenderer.num(tm.get("y"), 0);
            sw = (int) UiRenderer.num(tm.get("width"), 0);
            sh = (int) UiRenderer.num(tm.get("height"), UiRenderer.num(tm.get("sourceHeight"), 0));
            tw = (int) UiRenderer.num(tm.get("textureWidth"), 256);
            th = (int) UiRenderer.num(tm.get("textureHeight"), 256);
        } else {
            return false;
        }
        if (sw > 0 || sh > 0) {
            if (sw <= 0) {
                sw = Math.max(sh, 1);
            }
            if (sh <= 0) {
                sh = Math.max(sw, 1);
            }
            g.blit(tex, (int) node.x(), (int) node.y(), (int) node.width(), (int) node.height(),
                    sx, sy, sw, sh, tw, th);
        } else {
            g.blit(tex, (int) node.x(), (int) node.y(), (int) node.width(), (int) node.height(),
                    0.0F, 0.0F, (int) node.width(), (int) node.height(),
                    (int) node.width(), (int) node.height());
        }
        return true;
    }

    static void drawButton(GuiGraphics g, Font font, RenderNode node, int mouseX, int mouseY,
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
        if (drawStateTexture(g, texRaw, node)) {
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
                label = applyReveal(node, rspec, label, null);
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
    static void drawCheckbox(GuiGraphics g, Font font, RenderNode node, UiRenderer.State state) {
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

    static record RevealState(long startMs, String content) {}

    static String revealKey(RenderNode node, String scope) {
        return scope == null || scope.isEmpty() ? node.id() : scope + "\u0001" + node.id();
    }

    static void pruneRevealIfNeeded() {
        if (UiRenderer.textRevealState.size() > UiRenderer.REVEAL_PRUNE_THRESHOLD) {
            var it = UiRenderer.textRevealState.keySet().iterator();
            int toRemove = UiRenderer.REVEAL_PRUNE_THRESHOLD / 2;
            for (int i = 0; i < toRemove && it.hasNext(); i++) { it.next(); it.remove(); }
        }
    }

    /** 可见字符数（忽略 § 颜色码）。 */
    static int visibleCharCount(String s) {
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
    static String sliceVisible(String s, int n) {
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
    static String applyReveal(RenderNode node, Map<?, ?> spec, String content, String scope) {
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

    /** 屏幕角标（badge）：true = 红点；数字 = 数量徽标；{count, color} = 计数 + 自定义色。右上角，随元素变换。 */
    static void drawScreenBadge(GuiGraphics g, Font font, RenderNode node, Object badgeProp) {
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
    static void drawScreenStatusIcon(GuiGraphics g, Font font, RenderNode node, Object statusProp) {
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

    static void drawInput(GuiGraphics g, Font font, RenderNode node, UiRenderer.State state) {
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
    static void drawInputBox(GuiGraphics g, RenderNode node, Map<?, ?> spec, boolean focused) {
        // 状态贴图（normal/focus，支持源区域对象形式）；提供贴图时优先绘制，回退纯色路径
        Object texRaw = focused && spec.get("focus") != null ? spec.get("focus")
                : !focused && spec.get("normal") != null ? spec.get("normal") : null;
        if (drawStateTexture(g, texRaw, node)) {
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

    static void drawSlider(GuiGraphics g, Font font, RenderNode node, UiRenderer.State state) {
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

    static void drawProgress(GuiGraphics g, Font font, RenderNode node) {
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
    static void drawGauge(GuiGraphics g, Font font, RenderNode node) {
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
    static void drawArcSlider(GuiGraphics g, Font font, RenderNode node, UiRenderer.State state) {
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
    static void fillArc(GuiGraphics g, double cx, double cy, double radius, double thickness,
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
        var matrix = g.pose().last().pose();
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
        com.mojang.blaze3d.systems.RenderSystem.setShader(
                net.minecraft.client.renderer.GameRenderer::getPositionColorShader);
        var builder = com.mojang.blaze3d.vertex.Tesselator.getInstance()
                .begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.TRIANGLES,
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
        com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(builder.buildOrThrow());
        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
    }

    /**
     * 视频组件：src 指向真视频文件（mp4/webm/mov/mkv...，需 JavaCV 丢进 mods）或帧序列目录
     * （frame_0000.png...，无 JavaCV 回退）。fps 帧序列速度；loop 循环；fit: contain 按原比例居中。
     */
    static void drawVideo(GuiGraphics g, Font font, RenderNode node) {
        Map<?, ?> spec = UiRenderer.propsMap(node, "video");
        String src = UiRenderer.str(spec.get("src"));
        double fps = UiRenderer.num(spec.get("fps"), 24);
        Object loopRaw = spec.get("loop");
        boolean loop = loopRaw == null || Boolean.parseBoolean(String.valueOf(loopRaw));
        String fit = UiRenderer.str(spec.get("fit"));
        ResourceLocation texture = null;
        int x = (int) node.x();
        int y = (int) node.y();
        int w = (int) node.width();
        int h = (int) node.height();
        boolean videoFile = false;
        if (src != null) {
            String lower = src.toLowerCase(java.util.Locale.ROOT);
            boolean remote = lower.startsWith("https://") || lower.startsWith("http://");
            videoFile = remote
                    || lower.endsWith(".mp4") || lower.endsWith(".webm") || lower.endsWith(".mov")
                    || lower.endsWith(".mkv") || lower.endsWith(".avi") || lower.endsWith(".flv");
            if (videoFile && FfmpegVideoPlayer.available()) {
                FfmpegVideoPlayer video = FfmpegVideoPlayer.of(src, loop, fit);
                if (video != null) {
                    FfmpegVideoPlayer.register(node.id(), video); // 脚本按元素 id 控制
                    texture = video.currentTexture();
                    int[] r = video.drawRect(node.x(), node.y(), node.width(), node.height());
                    x = r[0];
                    y = r[1];
                    w = r[2];
                    h = r[3];
                }
            }
            if (texture == null) {
                // 回退帧序列（帧目录）
                VideoPlayer video = VideoPlayer.of(src);
                if (video != null) {
                    texture = video.currentTexture(fps);
                }
            }
        }
        if (texture == null) {
            UiRenderer.fillRect(g, node, UiRenderer.alphaColor(0xFF101318));
            // 播放器已就绪但首帧未出 → 加载中；否则占位
            FfmpegVideoPlayer vp = videoFile && FfmpegVideoPlayer.available()
                    ? FfmpegVideoPlayer.byElement(node.id()) : null;
            if (vp != null && !vp.hasFrame() && !vp.isFailed()) {
                g.drawString(font, "加载中…", (int) node.x() + 2, (int) node.y() + 2, UiRenderer.alphaColor(0xFFFFD54F));
            } else {
                g.drawString(font, "[video]", (int) node.x() + 2, (int) node.y() + 2, UiRenderer.alphaColor(0xFFFFD54F));
            }
            return;
        }
        g.blit(texture, x, y, w, h, 0.0F, 0.0F, w, h, w, h);
        // seek 条（video.seekable: true 且播放器有时间轴）：底部进度条，点击跳转
        if (UiRenderer.bool(spec.get("seekable"), false) && videoFile && FfmpegVideoPlayer.available()) {
            FfmpegVideoPlayer vp = FfmpegVideoPlayer.byElement(node.id());
            if (vp != null) {
                double cur = vp.currentSeconds();
                double dur = vp.durationSeconds();
                if (dur > 0 && cur >= 0) {
                    int barH = 4;
                    int bx = x;
                    int by = y + h - barH;
                    int bw = w;
                    g.fill(bx, by, bx + bw, by + barH, 0x90000000);
                    int fillW = (int) Math.max(0, Math.min(bw, bw * (cur / dur)));
                    g.fill(bx, by, bx + fillW, by + barH, 0xFF7A8BFF);
                    // 记录条区域（OdcScreen 点击跳转用）
                    VIDEO_SEEK_BARS.put(node.id(), new int[]{bx, by, bx + bw, by + barH,
                            (int) Math.round(dur * 100)});
                }
            }
        }
    }

    /** video seek 条区域表：元素 id → {x0, y0, x1, y1, 时长×100}（渲染线程写，点击线程读）。 */
    public static final java.util.Map<String, int[]> VIDEO_SEEK_BARS = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * video 元素点击：落在 seek 条上 → 跳转播放位置并返回 true（已消费）。
     */
    public static boolean handleVideoSeekClick(String elementId, double mouseX, double mouseY) {
        int[] bar = VIDEO_SEEK_BARS.get(elementId);
        if (bar == null) {
            return false;
        }
        if (mouseX < bar[0] || mouseX > bar[2] || mouseY < bar[1] - 2 || mouseY > bar[3] + 2) {
            return false;
        }
        FfmpegVideoPlayer vp = FfmpegVideoPlayer.byElement(elementId);
        if (vp == null) {
            return false;
        }
        double ratio = (mouseX - bar[0]) / Math.max(1, bar[2] - bar[0]);
        ratio = Math.max(0, Math.min(1, ratio));
        vp.seek(ratio * (bar[4] / 100.0));
        return true;
    }

    static void drawImage(GuiGraphics g, Font font, RenderNode node) {
        Map<?, ?> spec = UiRenderer.propsMap(node, "image");
        String src = UiRenderer.str(spec.get("src"));
        if (src == null && spec.get("images") instanceof List<?> imgs && !imgs.isEmpty()) {
            long interval = (long) UiRenderer.num(spec.get("interval"), 1200);
            if (interval <= 0) interval = 1200;
            int idx = (int) ((System.currentTimeMillis() / interval) % imgs.size());
            Object pick = imgs.get(idx);
            src = pick == null ? null : String.valueOf(pick);
        }
        ResourceLocation texture;
        if (src != null && src.toLowerCase(java.util.Locale.ROOT).endsWith(".gif")) {
            GifPlayer gif = GifPlayer.of(src);
            texture = gif == null ? null : gif.currentTexture();
        } else {
            texture = UiStyle.texture(src);
        }
        if (texture == null) {
            UiRenderer.fillRect(g, node, UiRenderer.alphaColor(0xFF20242C));
            return;
        }
        int w = (int) node.width();
        int h = (int) node.height();
        Map<?, ?> srcR = spec.get("source") instanceof Map<?, ?> sm ? sm : null;
        int su = srcR == null ? 0 : (int) UiRenderer.num(srcR.get("x"), 0);
        int sv = srcR == null ? 0 : (int) UiRenderer.num(srcR.get("y"), 0);
        int sw = srcR == null ? w : (int) UiRenderer.num(srcR.get("w"), w);
        int sh = srcR == null ? h : (int) UiRenderer.num(srcR.get("h"), h);
        Map<?, ?> sheet = spec.get("sheet") instanceof Map<?, ?> sm2 ? sm2 : null;
        int texW = sheet == null ? 256 : (int) UiRenderer.num(sheet.get("w"), 256);
        int texH = sheet == null ? 256 : (int) UiRenderer.num(sheet.get("h"), 256);
        double rot = UiRenderer.num(spec.get("rotation"), 0);
        Object mirror = spec.get("mirror");
        boolean mx = Boolean.TRUE.equals(mirror)
                || mirror instanceof Map<?, ?> mm && UiRenderer.bool(mm.get("x"), false);
        boolean my = mirror instanceof Map<?, ?> mm2 && UiRenderer.bool(mm2.get("y"), false);
        Map<?, ?> ns = spec.get("nineSlice") instanceof Map<?, ?> nm ? nm : null;
        boolean tile = UiRenderer.bool(spec.get("tile"), UiRenderer.bool(spec.get("repeat"), false));
        var pose = g.pose();
        pose.pushPose();
        if (mx || my || rot != 0) {
            double cx = node.x() + w / 2.0;
            double cy = node.y() + h / 2.0;
            pose.translate(cx, cy, 0);
            if (mx) pose.scale(-1.0F, 1.0F, 1.0F);
            if (my) pose.scale(1.0F, -1.0F, 1.0F);
            if (rot != 0) pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees((float) rot));
            pose.translate(-cx, -cy, 0);
        }
        if (tile) {
            int tw = Math.max(1, sw);
            int th = Math.max(1, sh);
            for (int yy = 0; yy < h; yy += th) {
                for (int xx = 0; xx < w; xx += tw) {
                    int cw = Math.min(tw, w - xx);
                    int ch = Math.min(th, h - yy);
                    g.blit(texture, (int) (node.x() + xx), (int) (node.y() + yy), cw, ch, su, sv, Math.min(tw, cw), Math.min(th, ch), texW, texH);
                }
            }
        } else if (ns != null) {
            int left = (int) UiRenderer.num(ns.get("left"), 0);
            int right = (int) UiRenderer.num(ns.get("right"), 0);
            int top = (int) UiRenderer.num(ns.get("top"), 0);
            int bottom = (int) UiRenderer.num(ns.get("bottom"), 0);
            int midW = Math.max(0, sw - left - right);
            int midH = Math.max(0, sh - top - bottom);
            if (midW > 0 && midH > 0) {
                g.blit(texture, (int) (node.x() + left), (int) (node.y() + top),
                        (int) (w - left - right), (int) (h - top - bottom),
                        su + left, sv + top, midW, midH, texW, texH);
            }
            g.blit(texture, (int) node.x(), (int) node.y(), left, top, su, sv, left, top, texW, texH);
            g.blit(texture, (int) (node.x() + w - right), (int) node.y(), right, top, su + sw - right, sv, right, top, texW, texH);
            g.blit(texture, (int) node.x(), (int) (node.y() + h - bottom), left, bottom, su, sv + sh - bottom, left, bottom, texW, texH);
            g.blit(texture, (int) (node.x() + w - right), (int) (node.y() + h - bottom), right, bottom, su + sw - right, sv + sh - bottom, right, bottom, texW, texH);
            if (midW > 0) {
                g.blit(texture, (int) (node.x() + left), (int) node.y(), (int) (w - left - right), top, su + left, sv, midW, top, texW, texH);
                g.blit(texture, (int) (node.x() + left), (int) (node.y() + h - bottom), (int) (w - left - right), bottom, su + left, sv + sh - bottom, midW, bottom, texW, texH);
            }
            if (midH > 0) {
                g.blit(texture, (int) node.x(), (int) (node.y() + top), left, (int) (h - top - bottom), su, sv + top, left, midH, texW, texH);
                g.blit(texture, (int) (node.x() + w - right), (int) (node.y() + top), right, (int) (h - top - bottom), su + sw - right, sv + top, right, midH, texW, texH);
            }
        } else {
            g.blit(texture, (int) node.x(), (int) node.y(), w, h, su, sv, sw, sh, texW, texH);
        }
        pose.popPose();
    }

    /** 快捷栏：渲染玩家 9 格物品 + 当前选中高亮，点击切换（样式可配）。 */
    static void drawHotSlot(GuiGraphics g, Font font, RenderNode node) {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        Map<?, ?> spec = UiRenderer.propsMap(node, "hot_slot");
        int slots = Math.max(1, (int) UiRenderer.num(spec.get("slots"), 9));
        int selectedColor = UiStyle.color(spec.get("selectedColor"), 0xFF3A4254);
        int slotColor = UiStyle.color(spec.get("slotColor"), 0xFF181C24);
        int accent = UiStyle.color(spec.get("accent"), 0xFF7A8BFF);
        int topBorder = UiStyle.color(spec.get("borderColor"), 0xFF505868);
        int bottomDim = UiStyle.color(spec.get("borderColor"), 0xFF20242C);
        double cellRadius = UiRenderer.num(spec.get("radius"), 0);
        double cellW = node.width() / slots;
        int selected = player.getInventory().selected;
        var inventory = player.getInventory().items;
        for (int i = 0; i < slots; i++) {
            int x = (int) (node.x() + i * cellW);
            int y = (int) node.y();
            int w = (int) cellW;
            int h = (int) node.height();
            boolean sel = i == selected;
            int bg = sel ? selectedColor : slotColor;
            if (cellRadius > 0) {
                double r = Math.min(cellRadius, Math.min(w, h) / 2);
                if (sel) {
                    UiRenderer.fillRounded(g, x, y, w, h, r, accent); // 选中：accent 外圈
                    UiRenderer.fillRounded(g, x + 2, y + 2, w - 4, h - 4, Math.max(0, r - 2), bg);
                } else {
                    UiRenderer.fillRounded(g, x, y, w, h, r, bg);
                }
            } else {
                g.fill(x, y, x + w, y + h, bg);
                g.fill(x, y, x + w, y + 1, sel ? accent : topBorder);
                g.fill(x, y + h - 1, x + w, y + h, sel ? accent : bottomDim);
            }
            if (i < inventory.size()) {
                var stack = inventory.get(i);
                if (!stack.isEmpty()) {
                    int icon = Math.min(16, (int) Math.min(cellW - 4, h - 4));
                    int ix = x + (w - icon) / 2;
                    int iy = y + (h - icon) / 2;
                    var pose = g.pose();
                    pose.pushPose();
                    pose.translate(ix, iy, 0);
                    pose.scale(icon / 16.0F, icon / 16.0F, 1.0F);
                    g.renderItem(stack, 0, 0);
                    if (stack.getCount() > 1) {
                        g.renderItemDecorations(font, stack, 0, 0);
                    }
                    pose.popPose();
                }
            }
        }
    }

    /** 聊天输入框：同 input（placeholder/圆角/描边/光标），回车发送聊天。 */
    static void drawChatInput(GuiGraphics g, Font font, RenderNode node, UiRenderer.State state) {
        Map<?, ?> spec = UiRenderer.propsMap(node, "chat_input");
        String text = state == null ? "" : state.inputText(node.id());
        if (text == null) {
            text = "";
        }
        boolean focused = state != null && state.focused(node.id());
        drawInputBox(g, node, spec, focused);
        String prefix = UiRenderer.str(spec.get("prefix"));
        String shown = (prefix == null ? "" : prefix) + text;
        int textColor = UiStyle.color(spec.get("textColor"), 0xFFFFFFFF);
        if (shown.isEmpty()) {
            String ph = UiRenderer.interpolate(node, UiRenderer.str(spec.get("placeholder")), null);
            if (ph != null && !ph.isEmpty()) {
                g.drawString(font, ph, (int) node.x() + 4, (int) (node.y() + (node.height() - 8) / 2),
                        UiRenderer.alphaColor(0xFF707880));
            }
        } else {
            g.drawString(font, shown, (int) node.x() + 4, (int) (node.y() + (node.height() - 8) / 2),
                    UiRenderer.alphaColor(textColor));
        }
        if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
            int cx = (int) node.x() + 4 + font.width(shown);
            g.fill(cx, (int) node.y() + 4, cx + 1, (int) (node.y() + node.height() - 4),
                    UiRenderer.alphaColor(textColor));
        }
    }

    /** 聊天显示区：底部对齐显示最近聊天（RichText 渲染，支持行内多色；channel 过滤通道消息）。 */
    static void drawChatDisplay(GuiGraphics g, Font font, RenderNode node) {
        Map<?, ?> spec = UiRenderer.propsMap(node, "chat_display");
        // 背景（可选）
        Object bg = spec.get("background");
        if (bg != null) {
            int color = UiStyle.color(bg, 0);
            if (color != 0) {
                UiRenderer.fillRect(g, node, UiRenderer.alphaColor(color));
            }
        }
        String channel = UiRenderer.str(spec.get("channel"));
        java.util.List<String> messages;
        if (channel != null && !channel.isBlank() && !"all".equalsIgnoreCase(channel)) {
            messages = ClientController.get().chatStore().messages(channel); // 服务端通道
        } else {
            messages = ClientController.get().latestChat(200); // 全局聊天缓存
        }
        if (messages.isEmpty()) {
            return;
        }
        double lineH = UiRenderer.num(spec.get("lineHeight"), 9);
        int maxLines = Math.max(1, (int) (node.height() / lineH));
        int start = Math.max(0, messages.size() - maxLines);
        double y = node.y() + node.height() - lineH;
        for (int i = messages.size() - 1; i >= start; i--) {
            // 占位符插值（{player.*}/{system.*}/{color.*} 等按接收者解析）后画富文本
            String line = UiRenderer.interpolate(node, messages.get(i), null);
            drawRichLine(g, font, line, (int) node.x() + 2, (int) y);
            y -= lineH;
        }
    }

    /** 画一行富文本（按 RichText 片段累进 x，行内多色）。 */
    static void drawRichLine(GuiGraphics g, Font font, String legacy, int x, int y) {
        int cx = x;
        for (com.opendreamcore.script.RichText.Segment seg : com.opendreamcore.script.RichText.parse(legacy)) {
            if (seg.text().isEmpty()) {
                continue;
            }
            g.drawString(font, seg.text(), cx, y, UiRenderer.alphaColor(0xFF000000 | seg.color()));
            cx += font.width(seg.text());
        }
    }

    static void drawItemSlot(GuiGraphics g, Font font, RenderNode node, java.util.Map<String, Object> pageVars) {
        UiRenderer.fillRect(g, node, UiRenderer.alphaColor(0xFF101318));
        g.fill((int) node.x(), (int) node.y(), (int) (node.x() + node.width()), (int) (node.y() + 1), 0xFF505868);
        g.fill((int) node.x(), (int) (node.y() + node.height() - 1), (int) (node.x() + node.width()), (int) (node.y() + node.height()), 0xFF20242C);
        UiRenderer.drawItemIcon(g, font, node, node.props().get("item"), false, pageVars);
    }

    static void drawItemDisplay(GuiGraphics g, Font font, RenderNode node, java.util.Map<String, Object> pageVars) {
        Map<?, ?> spec = UiRenderer.propsMap(node, "item_display");
        Map<?, ?> rot = spec.get("rotation") instanceof Map<?, ?> rm ? rm
                : spec.get("rotate") instanceof Map<?, ?> rm2 ? rm2 : null;
        if (rot != null) {
            double size = Math.max(16, node.width() * 0.7);
            double ix = node.x() + (node.width() - size) / 2;
            double iy = node.y() + (node.height() - size) / 2;
            UiRenderer.drawItemAtRot(g, font, ix, iy, size, spec.get("item"), pageVars, rot);
        } else {
            UiRenderer.drawItemIcon(g, font, node, spec.get("item"), true, pageVars);
        }
    }

    /** 容器槽位：显示服务端 container_sync 推送的真实容器物品；点击事件带槽位号。 */
    static void drawChestSlot(GuiGraphics g, Font font, RenderNode node) {
        UiRenderer.fillRect(g, node, UiRenderer.alphaColor(0xFF101318));
        g.fill((int) node.x(), (int) node.y(), (int) (node.x() + node.width()), (int) (node.y() + 1), 0xFF505868);
        g.fill((int) node.x(), (int) (node.y() + node.height() - 1), (int) (node.x() + node.width()), (int) (node.y() + node.height()), 0xFF20242C);
        Map<?, ?> spec = UiRenderer.propsMap(node, "chest_slot");
        int slot = spec.get("slot") instanceof Number n ? n.intValue() : 0;
        ContainerStore.ContainerData data = ClientController.get().containerStore()
                .get(ClientController.get().currentSessionId());
        if (data != null) {
            ContainerStore.SlotData slotData = data.slot(slot);
            if (slotData != null && slotData.itemId() != null && !slotData.itemId().isEmpty()) {
                double size = Math.min(16, Math.min(node.width(), node.height()));
                double ix = node.x() + (node.width() - size) / 2;
                double iy = node.y() + (node.height() - size) / 2;
                UiRenderer.drawItemAt(g, font, ix, iy, size, slotData.itemId() + " x" + slotData.count(), null);
            }
        }
        if (UiRenderer.bool(spec.get("showSlot"), false)) {
            g.drawString(font, String.valueOf(slot), (int) node.x() + 2, (int) node.y() + 1, UiRenderer.alphaColor(0xFF6B7280));
        }
    }

    static void drawToggle(GuiGraphics g, Font font, RenderNode node, UiRenderer.State state) {
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

    static void drawDropdown(GuiGraphics g, Font font, RenderNode node, UiRenderer.State state, int mouseX, int mouseY) {
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
    static void drawAreaInput(GuiGraphics g, Font font, RenderNode node, UiRenderer.State state) {
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
        List<FormattedCharSequence> lines = wrapLines(font, text, (int) node.width() - 8);
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
    static void drawSuggestion(GuiGraphics g, Font font, RenderNode node, UiRenderer.State state, int mouseX, int mouseY) {
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

    static String suggestionLabel(Object s) {
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

    /** 文本按宽度折行（\n 强制换行 + 自动折行）。 */
    static List<FormattedCharSequence> wrapLines(Font font, String text, int maxWidth) {
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

    static void drawCard(GuiGraphics g, Font font, RenderNode node, java.util.Map<String, Object> pageVars) {
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
    static void drawCardFrame(GuiGraphics g, RenderNode node, int border) {
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
    static void drawFlipCard(GuiGraphics g, Font font, RenderNode node, UiRenderer.State state,
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
        pose.pushPose();
        double cx = node.x() + node.width() / 2;
        double cy = node.y() + node.height() / 2;
        pose.translate(cx, cy, 0);
        if (vertical) pose.scale(1.0F, (float) scale, 1.0F);
        else pose.scale((float) scale, 1.0F, 1.0F);
        pose.translate(-cx, -cy, 0);
        drawCardFace(g, font, node, face, pageVars);
        pose.popPose();
    }

    static void drawCardFace(GuiGraphics g, Font font, RenderNode node, Map<?, ?> face,
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

    /** 图表：bar（柱状）/ line（折线）/ pie（饼图）。 */
    static void drawChart(GuiGraphics g, Font font, RenderNode node) {
        Map<?, ?> spec = UiRenderer.propsMap(node, "chart");
        String type = UiRenderer.str(spec.get("type"));
        if (type == null) {
            type = "bar";
        }
        List<Double> data = new ArrayList<>();
        Object rawData = spec.get("data");
        if (rawData instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Number n) {
                    data.add(n.doubleValue());
                } else if (o != null) {
                    try {
                        data.add(Double.parseDouble(String.valueOf(o)));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        if (data.isEmpty()) {
            drawPlaceholder(g, font, node);
            return;
        }
        int baseColor = UiStyle.color(spec.get("color"), 0xFF42A5F5);
        List<?> rawColors = spec.get("colors") instanceof List<?> l ? l : List.of();
        boolean showLabels = UiRenderer.bool(spec.get("showLabels"), false);
        switch (type) {
            case "pie" -> drawPieChart(g, font, node, data, baseColor, rawColors, showLabels, spec);
            case "line" -> drawLineChart(g, font, node, data, baseColor, rawColors, showLabels, spec);
            default -> drawBarChart(g, font, node, data, baseColor, rawColors, showLabels, spec);
        }
    }

    static void drawBarChart(GuiGraphics g, Font font, RenderNode node, List<Double> data,
                                     int baseColor, List<?> rawColors, boolean showLabels, Map<?, ?> spec) {
        List<?> labels = spec.get("labels") instanceof List<?> l ? l : List.of();
        double min = UiRenderer.num(spec.get("min"), 0);
        double max = UiRenderer.num(spec.get("max"), maxOf(data));
        int n = data.size();
        double plotH = node.height() - (showLabels ? 10 : 0);
        double baseline = node.y() + plotH;
        double slotW = node.width() / n;
        double barW = Math.max(1, slotW * 0.6);
        for (int i = 0; i < n; i++) {
            double v = data.get(i);
            double ratio = max > min ? (v - min) / (max - min) : 0;
            ratio = Math.max(0, Math.min(1, ratio));
            double h = ratio * (plotH - 2);
            double bx = node.x() + i * slotW + (slotW - barW) / 2;
            int color = colorAt(rawColors, i, baseColor);
            g.fill((int) bx, (int) (baseline - h), (int) (bx + barW), (int) baseline, UiRenderer.alphaColor(color));
            g.fill((int) bx, (int) (baseline - h), (int) (bx + barW), (int) (baseline - h + 1), UiRenderer.alphaColor(brighten(color)));
            if (showLabels && i < labels.size()) {
                String label = String.valueOf(labels.get(i));
                g.drawString(font, label, (int) (bx + (barW - font.width(label)) / 2), (int) (baseline + 1), UiRenderer.alphaColor(0xFF9AA3B2));
            }
        }
        g.fill((int) node.x(), (int) baseline, (int) (node.x() + node.width()), (int) baseline + 1, 0xFF505868);
    }

    static void drawLineChart(GuiGraphics g, Font font, RenderNode node, List<Double> data,
                                      int baseColor, List<?> rawColors, boolean showLabels, Map<?, ?> spec) {
        List<?> labels = spec.get("labels") instanceof List<?> l ? l : List.of();
        double min = UiRenderer.num(spec.get("min"), 0);
        double max = UiRenderer.num(spec.get("max"), maxOf(data));
        int n = data.size();
        double plotH = node.height() - (showLabels ? 10 : 0);
        double baseline = node.y() + plotH;
        double slotW = node.width() / n;
        double[] xs = new double[n];
        double[] ys = new double[n];
        for (int i = 0; i < n; i++) {
            double ratio = max > min ? (data.get(i) - min) / (max - min) : 0;
            ratio = Math.max(0, Math.min(1, ratio));
            xs[i] = node.x() + i * slotW + slotW / 2;
            ys[i] = baseline - 2 - ratio * (plotH - 4);
        }
        int color = colorAt(rawColors, 0, baseColor);
        for (int i = 0; i < n - 1; i++) {
            drawLinePx(g, xs[i], ys[i], xs[i + 1], ys[i + 1], color, 1);
        }
        for (int i = 0; i < n; i++) {
            g.fill((int) xs[i] - 1, (int) ys[i] - 1, (int) xs[i] + 2, (int) ys[i] + 2, UiRenderer.alphaColor(brighten(color)));
            if (showLabels && i < labels.size()) {
                String label = String.valueOf(labels.get(i));
                g.drawString(font, label, (int) (xs[i] - font.width(label) / 2.0), (int) (baseline + 1), UiRenderer.alphaColor(0xFF9AA3B2));
            }
        }
        g.fill((int) node.x(), (int) baseline, (int) (node.x() + node.width()), (int) baseline + 1, 0xFF505868);
    }

    /** 饼图：逐行扫描，按角度区间填色（近似圆）。 */
    static void drawPieChart(GuiGraphics g, Font font, RenderNode node, List<Double> data,
                                     int baseColor, List<?> rawColors, boolean showLabels, Map<?, ?> spec) {
        double total = 0;
        for (double v : data) {
            total += v;
        }
        if (total <= 0) {
            drawPlaceholder(g, font, node);
            return;
        }
        double cx = node.x() + node.width() / 2;
        double cy = node.y() + node.height() / 2;
        double r = Math.max(2, Math.min(node.width(), node.height()) / 2 - 2);
        double[] cum = new double[data.size() + 1];
        double acc = 0;
        for (int i = 0; i < data.size(); i++) {
            cum[i] = acc;
            acc += data.get(i) / total * Math.PI * 2;
        }
        cum[data.size()] = Math.PI * 2;
        int y0 = (int) Math.ceil(cy - r);
        int y1 = (int) Math.floor(cy + r);
        for (int py = y0; py <= y1; py++) {
            double dy = cy - py; // 行相对圆心的垂直偏移（上正下负）
            double dx = Math.sqrt(Math.max(0, r * r - dy * dy));
            if (dy == 0) {
                // 中线行：左右两半各归一个角度
                int left = sliceAt(cum, 3 * Math.PI / 2);
                int right = sliceAt(cum, Math.PI / 2);
                g.fill((int) (cx - dx), py, (int) cx, py + 1, UiRenderer.alphaColor(colorAt(rawColors, left, baseColor)));
                g.fill((int) cx, py, (int) (cx + dx), py + 1, UiRenderer.alphaColor(colorAt(rawColors, right, baseColor)));
                continue;
            }
            double lo = normAngle(Math.atan2(-dx, -dy));
            double hi = normAngle(Math.atan2(dx, -dy));
            // 行角度范围跨 0/2π 缝时拆两段
            if (lo <= hi) {
                fillPieRow(g, py, cx, cy, dy, dx, lo, hi, cum, rawColors, baseColor);
            } else {
                fillPieRow(g, py, cx, cy, dy, dx, lo, Math.PI * 2, cum, rawColors, baseColor);
                fillPieRow(g, py, cx, cy, dy, dx, 0, hi, cum, rawColors, baseColor);
            }
        }
        if (showLabels && data.size() <= 8) {
            for (int i = 0; i < data.size(); i++) {
                double mid = (cum[i] + cum[i + 1]) / 2;
                double lx = cx + Math.sin(mid) * r * 0.6;
                double ly = cy - Math.cos(mid) * r * 0.6;
                String label = String.valueOf(data.get(i));
                g.drawString(font, label, (int) (lx - font.width(label) / 2.0), (int) (ly - 4), UiRenderer.alphaColor(0xFFFFFFFF), true);
            }
        }
    }

    /** 填一行饼图：把 [lo, hi) 内按切片边界切成段。 */
    static void fillPieRow(GuiGraphics g, int py, double cx, double cy, double dy, double dx,
                                   double lo, double hi, double[] cum, List<?> rawColors, int baseColor) {
        List<Double> bounds = new ArrayList<>();
        bounds.add(lo);
        for (int i = 1; i < cum.length - 1; i++) {
            if (cum[i] > lo && cum[i] < hi) {
                bounds.add(cum[i]);
            }
        }
        bounds.add(hi);
        for (int i = 0; i < bounds.size() - 1; i++) {
            double a = bounds.get(i);
            double b = bounds.get(i + 1);
            double mid = (a + b) / 2;
            int slice = sliceAt(cum, mid);
            double x0 = clampX(cx + dy * Math.tan(a), cx - dx, cx + dx);
            double x1 = clampX(cx + dy * Math.tan(b), cx - dx, cx + dx);
            if (x1 > x0) {
                g.fill((int) x0, py, (int) x1, py + 1, UiRenderer.alphaColor(colorAt(rawColors, slice, baseColor)));
            }
        }
    }

    static double clampX(double x, double min, double max) {
        return Math.max(min, Math.min(max, x));
    }

    static double normAngle(double a) {
        a = a % (Math.PI * 2);
        return a < 0 ? a + Math.PI * 2 : a;
    }

    /** 角度落在哪个切片（cum 为边界数组，含 0 与 2π）。 */
    static int sliceAt(double[] cum, double angle) {
        for (int i = 0; i < cum.length - 1; i++) {
            if (angle >= cum[i] && angle < cum[i + 1]) {
                return i;
            }
        }
        return cum.length - 2;
    }

    static int colorAt(List<?> colors, int index, int fallback) {
        if (colors != null && index < colors.size()) {
            return UiStyle.color(colors.get(index), fallback);
        }
        return fallback;
    }

    static double maxOf(List<Double> data) {
        double max = 0;
        for (double v : data) {
            max = Math.max(max, v);
        }
        return max;
    }

    static int brighten(int color) {
        int r = Math.min(255, ((color >> 16) & 0xFF) + 40);
        int gr = Math.min(255, ((color >> 8) & 0xFF) + 40);
        int b = Math.min(255, (color & 0xFF) + 40);
        return (color & 0xFF000000) | (r << 16) | (gr << 8) | b;
    }

    /** 像素级直线（DDA）。 */
    static void drawLinePx(GuiGraphics g, double x1, double y1, double x2, double y2, int color, int width) {
        int steps = (int) Math.max(1, Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1)));
        int half = Math.max(0, width - 1);
        for (int i = 0; i <= steps; i++) {
            double t = steps == 0 ? 0 : i / (double) steps;
            int px = (int) (x1 + (x2 - x1) * t);
            int py = (int) (y1 + (y2 - y1) * t);
            g.fill(px - half, py - half, px + half + 1, py + half + 1, UiRenderer.alphaColor(color));
        }
    }

    /** 指南针：随玩家朝向滚动的东西南北刻度条 + 中央指针 + 可选路标（waypoints）。 */
    static void drawCompass(GuiGraphics g, Font font, RenderNode node,
                                    java.util.Map<String, Object> pageVars) {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        Map<?, ?> spec = UiRenderer.propsMap(node, "compass");
        double ppd = UiRenderer.num(spec.get("pixelsPerDegree"), 1.0);
        if (ppd <= 0) {
            ppd = 1;
        }
        double yaw = player.getYRot();
        double facing = ((yaw + 180) % 360 + 360) % 360; // 0 = 北
        double cx = node.x() + node.width() / 2;
        double cy = node.y() + node.height() / 2;
        UiRenderer.fillRect(g, node, UiRenderer.alphaColor(0x66101418));
        // 15° 小刻度（45° 的加高）
        for (int deg = 0; deg < 360; deg += 15) {
            double x = cx + (facing - deg) * ppd;
            if (x < node.x() - 2 || x > node.x() + node.width() + 2) {
                continue;
            }
            boolean big = deg % 45 == 0;
            int tickH = big ? 6 : 3;
            g.fill((int) x, (int) (cy - tickH / 2), (int) x + 1, (int) (cy + tickH / 2 + 1), UiRenderer.alphaColor(big ? 0xFFB0BEC5 : 0xFF506070));
        }
        // 方位字母（45° 一格）
        String[] dirs = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        for (int i = 0; i < 8; i++) {
            int deg = i * 45;
            double x = cx + (facing - deg) * ppd;
            if (x < node.x() || x > node.x() + node.width() - 8) {
                continue;
            }
            int color = deg % 90 == 0 ? UiStyle.color(spec.get("color"), 0xFFFFFFFF) : 0xFF808890;
            g.drawString(font, dirs[i], (int) x, (int) (cy - 4), UiRenderer.alphaColor(color));
        }
        // 路标（compass.waypoints: [{x, z, label?, color?}]，x/z 支持 ${vars} 表达式）：
        // 按目标相对方位投影到刻度条，顶部旗标 + 标签
        Object waypoints = spec.get("waypoints");
        if (waypoints instanceof List<?> list) {
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> w)) {
                    continue;
                }
                double wx = UiRenderer.num(UiRenderer.interpolate(node, String.valueOf(w.get("x")), pageVars), 0);
                double wz = UiRenderer.num(UiRenderer.interpolate(node, String.valueOf(w.get("z")), pageVars), 0);
                double dx = wx - player.getX();
                double dz = wz - player.getZ();
                double toTarget = Math.toDegrees(Math.atan2(-dx, dz)); // 目标相对方位（yaw 语义）
                double rel = (((toTarget - yaw) % 360) + 360) % 360;
                if (rel > 180) {
                    rel -= 360;
                }
                double mx = cx + rel * ppd;
                int wc = UiStyle.color(w.get("color"), 0xFFFFB300);
                String wl = UiRenderer.interpolate(node, String.valueOf(w.get("label")), pageVars);
                if ("null".equals(wl)) {
                    wl = null;
                }
                if (mx < node.x() - 8 || mx > node.x() + node.width() + 8) {
                    continue;
                }
                // 旗杆 + 三角旗（贴顶）
                g.fill((int) mx, (int) (node.y() + 1), (int) mx + 1, (int) (node.y() + 6), UiRenderer.alphaColor(wc));
                g.fill((int) mx + 1, (int) (node.y() + 1), (int) mx + 5, (int) (node.y() + 4), UiRenderer.alphaColor(wc));
                if (wl != null && !wl.isEmpty() && !"null".equalsIgnoreCase(wl)) {
                    g.drawString(font, wl, (int) (mx - font.width(wl) / 2.0), (int) (node.y() + 8),
                            UiRenderer.alphaColor(wc));
                }
            }
        }
        // 中央指针
        g.fill((int) cx, (int) node.y() + 1, (int) cx + 1, (int) (node.y() + node.height() - 1), 0xFFFF5252);
        g.fill((int) cx - 2, (int) node.y() + 1, (int) cx + 3, (int) node.y() + 3, 0xFFFF5252);
        g.fill((int) cx - 1, (int) node.y() + 3, (int) cx + 2, (int) node.y() + 5, 0xFFFF5252);
        g.fill((int) cx, (int) node.y() + 5, (int) cx + 1, (int) node.y() + 7, 0xFFFF5252);
        if (UiRenderer.bool(spec.get("showDegrees"), false)) {
            g.drawString(font, String.valueOf((int) Math.round(facing)) + "°",
                    (int) (cx + node.width() / 2 - 30), (int) (cy - 4), UiRenderer.alphaColor(0xFF9AA3B2));
        }
    }

    /** 方向指示：当前朝向文字（北/东北...）+ 可选指向目标坐标的箭头。 */
    static void drawDirection(GuiGraphics g, Font font, RenderNode node) {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        Map<?, ?> spec = UiRenderer.propsMap(node, "direction");
        double yaw = player.getYRot();
        int facing = (int) Math.round(((((yaw + 180) % 360) + 360) % 360) / 45.0) % 8;
        String[] names = {"北 N", "东北 NE", "东 E", "东南 SE", "南 S", "西南 SW", "西 W", "西北 NW"};
        String[] cn = {"北", "东北", "东", "东南", "南", "西南", "西", "西北"};
        String format = UiRenderer.str(spec.get("format"));
        String text;
        if ("cn".equalsIgnoreCase(format)) {
            text = cn[facing];
        } else if ("en".equalsIgnoreCase(format)) {
            text = names[facing].substring(names[facing].indexOf(' ') + 1);
        } else {
            text = names[facing];
        }
        int color = UiStyle.color(spec.get("color"), 0xFFFFFFFF);
        Object target = spec.get("target");
        boolean arrow = UiRenderer.bool(spec.get("showArrow"), target != null);
        double cx = node.x() + node.width() / 2;
        double cy = node.y() + node.height() / 2;
        if (target instanceof List<?> t && t.size() >= 2) {
            double tx = UiRenderer.num(t.get(0), 0);
            double tz = UiRenderer.num(t.get(1), 0);
            double dx = tx - player.getX();
            double dz = tz - player.getZ();
            double toTarget = Math.toDegrees(Math.atan2(-dx, dz)); // 目标相对方位（yaw 语义）
            double rel = (((toTarget - yaw) % 360) + 360) % 360;
            if (rel > 180) {
                rel -= 360;
            }
            if (arrow) {
                g.drawString(font, String.valueOf(facingArrow(rel)), (int) (cx - font.width(String.valueOf(facingArrow(rel))) - 8), (int) (cy - 4), UiRenderer.alphaColor(color));
            }
        } else if (arrow) {
            char ch = facingArrow(facing * 45.0);
            g.drawString(font, String.valueOf(ch), (int) (cx - font.width(String.valueOf(ch)) - 8), (int) (cy - 4), UiRenderer.alphaColor(color));
        }
        g.drawString(font, text, (int) (cx - font.width(text) / 2.0), (int) (cy - 4), UiRenderer.alphaColor(color), true);
    }

    /** 相对角度 → 四向箭头字符。 */
    static char facingArrow(double relDeg) {
        double rel = ((relDeg % 360) + 360) % 360;
        if (rel > 180) {
            rel -= 360;
        }
        if (rel >= -45 && rel < 45) {
            return '▲';
        }
        if (rel >= 45 && rel < 135) {
            return '▶';
        }
        if (rel >= 135 || rel < -135) {
            return '▼';
        }
        return '◀';
    }

    /** 画布：按 brushes 指令序列绘制（UiRenderer.rect/circle/line/gradient/triangle/text/image）。 */
    static void drawCanvas(GuiGraphics g, Font font, RenderNode node, java.util.Map<String, Object> pageVars) {
        Map<?, ?> spec = UiRenderer.propsMap(node, "canvas");
        Object bgRaw = spec.get("background");
        if (bgRaw != null) {
            int bg = UiStyle.color(bgRaw, 0);
            if (bg != 0) {
                UiRenderer.fillRect(g, node, UiRenderer.alphaColor(bg));
            }
        }
        Object brushesRaw = spec.get("brushes");
        if (!(brushesRaw instanceof List<?> brushes)) {
            return;
        }
        for (Object brushObj : brushes) {
            if (!(brushObj instanceof Map<?, ?> m)) {
                continue;
            }
            String type = UiRenderer.str(m.get("type"));
            if (type == null) {
                continue;
            }
            switch (type) {
                case "UiRenderer.rect" -> {
                    int c = UiStyle.color(m.get("color"), 0xFFFFFFFF);
                    int bx = (int) (node.x() + UiRenderer.num(m.get("x"), 0));
                    int by = (int) (node.y() + UiRenderer.num(m.get("y"), 0));
                    int bw = (int) UiRenderer.num(m.get("width"), 10);
                    int bh = (int) UiRenderer.num(m.get("height"), 10);
                    g.fill(bx, by, bx + bw, by + bh, UiRenderer.alphaColor(c));
                }
                case "circle" -> drawCanvasCircle(g, node, m);
                case "line" -> drawCanvasLine(g, node, m);
                case "gradient" -> drawCanvasGradient(g, node, m);
                case "triangle" -> drawCanvasTriangle(g, node, m);
                case "text" -> {
                    String content = UiRenderer.interpolate(node, UiRenderer.str(m.get("content")), pageVars);
                    if (content == null || content.isEmpty()) {
                        break;
                    }
                    int c = UiStyle.color(m.get("color"), 0xFFFFFFFF);
                    int bx = (int) (node.x() + UiRenderer.num(m.get("x"), 0));
                    int by = (int) (node.y() + UiRenderer.num(m.get("y"), 0));
                    if (UiRenderer.bool(m.get("shadow"), false)) {
                        g.drawString(font, content, bx, by, UiRenderer.alphaColor(c), true);
                    } else {
                        g.drawString(font, content, bx, by, UiRenderer.alphaColor(c));
                    }
                }
                case "image" -> drawCanvasImage(g, node, m);
                default -> {
                    // 未知笔刷忽略
                }
            }
        }
    }

    static void drawCanvasCircle(GuiGraphics g, RenderNode node, Map<?, ?> m) {
        double cx = node.x() + UiRenderer.num(m.get("cx"), 0);
        double cy = node.y() + UiRenderer.num(m.get("cy"), 0);
        double r = UiRenderer.num(m.get("radius"), 10);
        int color = UiStyle.color(m.get("color"), 0xFFFFFFFF);
        boolean fill = UiRenderer.bool(m.get("fill"), true);
        for (double py = -r; py <= r; py += 1) {
            double dx = Math.sqrt(Math.max(0, r * r - py * py));
            if (fill) {
                g.fill((int) (cx - dx), (int) (cy + py), (int) (cx + dx + 1), (int) (cy + py + 1), UiRenderer.alphaColor(color));
            } else {
                g.fill((int) (cx - dx), (int) (cy + py), (int) (cx - dx + 1), (int) (cy + py + 1), UiRenderer.alphaColor(color));
                g.fill((int) (cx + dx), (int) (cy + py), (int) (cx + dx + 1), (int) (cy + py + 1), UiRenderer.alphaColor(color));
            }
        }
    }

    static void drawCanvasLine(GuiGraphics g, RenderNode node, Map<?, ?> m) {
        double x1 = node.x() + UiRenderer.num(m.get("x1"), 0);
        double y1 = node.y() + UiRenderer.num(m.get("y1"), 0);
        double x2 = node.x() + UiRenderer.num(m.get("x2"), 10);
        double y2 = node.y() + UiRenderer.num(m.get("y2"), 10);
        int color = UiStyle.color(m.get("color"), 0xFFFFFFFF);
        int width = (int) Math.max(1, UiRenderer.num(m.get("width"), 1));
        drawLinePx(g, x1, y1, x2, y2, color, width);
    }

    static void drawCanvasGradient(GuiGraphics g, RenderNode node, Map<?, ?> m) {
        double x = node.x() + UiRenderer.num(m.get("x"), 0);
        double y = node.y() + UiRenderer.num(m.get("y"), 0);
        double w = UiRenderer.num(m.get("width"), 10);
        double h = UiRenderer.num(m.get("height"), 10);
        int from = UiStyle.color(m.get("from"), 0xFFFFFFFF);
        int to = UiStyle.color(m.get("to"), 0xFF000000);
        boolean vertical = UiRenderer.bool(m.get("vertical"), true);
        int steps = (int) (vertical ? h : w);
        for (int i = 0; i < steps; i++) {
            double t = steps > 1 ? i / (double) (steps - 1) : 1;
            int c = UiRenderer.lerpColor(from, to, t);
            if (vertical) {
                g.fill((int) x, (int) (y + i), (int) (x + w), (int) (y + i + 1), UiRenderer.alphaColor(c));
            } else {
                g.fill((int) (x + i), (int) y, (int) (x + i + 1), (int) (y + h), UiRenderer.alphaColor(c));
            }
        }
    }

    /** 三角形：逐行扫描线与三边求交。 */
    static void drawCanvasTriangle(GuiGraphics g, RenderNode node, Map<?, ?> m) {
        double[] xs = {node.x() + UiRenderer.num(m.get("x1"), 0), node.x() + UiRenderer.num(m.get("x2"), 10), node.x() + UiRenderer.num(m.get("x3"), 0)};
        double[] ys = {node.y() + UiRenderer.num(m.get("y1"), 0), node.y() + UiRenderer.num(m.get("y2"), 0), node.y() + UiRenderer.num(m.get("y3"), 10)};
        int color = UiStyle.color(m.get("color"), 0xFFFFFFFF);
        int minY = (int) Math.min(ys[0], Math.min(ys[1], ys[2]));
        int maxY = (int) Math.max(ys[0], Math.max(ys[1], ys[2]));
        for (int py = minY; py <= maxY; py++) {
            double y = py + 0.5;
            double xMin = Double.MAX_VALUE;
            double xMax = -Double.MAX_VALUE;
            for (int e = 0; e < 3; e++) {
                int e2 = (e + 1) % 3;
                double yA = ys[e];
                double yB = ys[e2];
                if ((y >= yA && y < yB) || (y >= yB && y < yA)) {
                    double t = (y - yA) / (yB - yA);
                    double x = xs[e] + (xs[e2] - xs[e]) * t;
                    xMin = Math.min(xMin, x);
                    xMax = Math.max(xMax, x);
                }
            }
            if (xMax >= xMin) {
                g.fill((int) Math.ceil(xMin), py, (int) Math.floor(xMax) + 1, py + 1, UiRenderer.alphaColor(color));
            }
        }
    }

    static void drawCanvasImage(GuiGraphics g, RenderNode node, Map<?, ?> m) {
        String src = UiRenderer.str(m.get("src"));
        ResourceLocation texture;
        if (src != null && src.toLowerCase(java.util.Locale.ROOT).endsWith(".gif")) {
            GifPlayer gif = GifPlayer.of(src);
            texture = gif == null ? null : gif.currentTexture();
        } else {
            texture = UiStyle.texture(src);
        }
        if (texture == null) {
            return;
        }
        double x = node.x() + UiRenderer.num(m.get("x"), 0);
        double y = node.y() + UiRenderer.num(m.get("y"), 0);
        double w = UiRenderer.num(m.get("width"), 16);
        double h = UiRenderer.num(m.get("height"), 16);
        g.blit(texture, (int) x, (int) y, (int) w, (int) h, 0.0F, 0.0F, (int) w, (int) h, (int) w, (int) h);
    }

    /** 顶部 Boss 条：暗屏 + 分段血条 + 居中文字（P3 由服务端控制进度）。 */
    static void drawBossBar(GuiGraphics g, Font font, RenderNode node) {
        Map<?, ?> spec = UiRenderer.propsMap(node, "boss_bar");
        double progress = UiRenderer.num(spec.get("progress"), 1.0);
        if (progress > 1 && progress <= 100) {
            progress /= 100; // 兼容 0-100 写法
        }
        progress = Math.max(0, Math.min(1, progress));
        int color = UiStyle.color(spec.get("color"), 0xFFFF0000);
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (UiRenderer.bool(spec.get("darkenScreen"), false)) {
            g.fill(0, 0, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), 0x99000000);
        }
        double w = node.width() > 0 ? node.width() : 182;
        double h = node.height() > 0 ? node.height() : 10;
        double x = node.width() > 0 ? node.x() : (mc.getWindow().getGuiScaledWidth() - w) / 2;
        double y = node.y();
        g.fill((int) x, (int) y, (int) (x + w), (int) (y + h), 0xFF000000);
        g.fill((int) x + 1, (int) y + 1, (int) (x + w - 1), (int) (y + h - 1), 0xFF330000);
        double fillW = Math.max(0, (w - 2) * progress);
        g.fill((int) x + 1, (int) y + 1, (int) (x + 1 + fillW), (int) (y + h - 1), UiRenderer.alphaColor(color));
        for (double sx = x + 12; sx < x + 1 + fillW; sx += 11) {
            g.fill((int) sx, (int) y + 1, (int) sx + 1, (int) (y + h - 1), 0xFF000000);
        }
        // 覆盖层（overlay: {color, progress}）
        Object overlayRaw = spec.get("overlay");
        if (overlayRaw instanceof Map<?, ?> overlay) {
            double op = Math.max(0, Math.min(1, UiRenderer.num(overlay.get("progress"), 0)));
            int oc = UiStyle.color(overlay.get("color"), 0xFF7A8BFF);
            double ow = Math.max(0, (w - 2) * op);
            g.fill((int) x + 1, (int) y + 1, (int) (x + 1 + ow), (int) (y + h - 1), UiRenderer.alphaColor(oc));
            for (double sx = x + 12; sx < x + 1 + ow; sx += 11) {
                g.fill((int) sx, (int) y + 1, (int) sx + 1, (int) (y + h - 1), 0xFF000000);
            }
        }
        String text = UiRenderer.str(spec.get("text"));
        if (text != null && !text.isEmpty()) {
            int tx = (int) (x + (w - font.width(text)) / 2);
            int ty = (int) (y - 11);
            g.drawString(font, text, tx, ty, UiRenderer.alphaColor(0xFFFFFFFF), true);
        }
    }

    /** 运行时嵌入页面：把另一个页面的布局画进本容器（裁剪 + 相对平移，仅展示）。 */
    static void drawEmbed(GuiGraphics g, Font font, RenderNode node,
                                  java.util.Map<String, Object> pageVars, int mouseX, int mouseY) {
        Map<?, ?> spec = UiRenderer.propsMap(node, "embed");
        String pageId = UiRenderer.str(spec.get("page"));
        if (pageId == null || pageId.isBlank()) {
            drawPlaceholder(g, font, node);
            return;
        }
        com.opendreamcore.page.Page target = ClientController.get().pageById(pageId);
        if (target == null) {
            g.fill((int) node.x(), (int) node.y(), (int) (node.x() + node.width()), (int) (node.y() + node.height()), UiRenderer.alphaColor(0x33101418));
            g.drawString(font, "[embed: " + pageId + "]", (int) node.x() + 2, (int) node.y() + 2, UiRenderer.alphaColor(0xFFFFD54F));
            return;
        }
        int depth = UiRenderer.EMBED_DEPTH.get();
        if (depth > 4) {
            drawPlaceholder(g, font, node);
            return;
        }
        int w = Math.max(1, (int) node.width());
        int h = Math.max(1, (int) node.height());
        List<RenderNode> embedded = ClientController.get().embeddedNodes(target, w, h);
        int[] r = UiRenderer.rect(node);
        g.enableScissor(r[0], r[1], r[2], r[3]);
        g.pose().pushPose();
        g.pose().translate(node.x(), node.y(), 0);
        UiRenderer.EMBED_DEPTH.set(depth + 1);
        try {
            UiRenderer.draw(g, font, embedded, mouseX - (int) node.x(), mouseY - (int) node.y(), null, target.variables());
        } finally {
            UiRenderer.EMBED_DEPTH.set(depth);
        }
        g.pose().popPose();
        g.disableScissor();
    }

    static void drawPlaceholder(GuiGraphics g, Font font, RenderNode node) {
        UiRenderer.fillRect(g, node, UiRenderer.alphaColor(0x40FF9800));
        g.drawString(font, "[" + node.type() + "]", (int) node.x() + 2, (int) node.y() + 2, UiRenderer.alphaColor(0xFFFFD54F));
    }

    static void drawScreenTabs(GuiGraphics g, Font font, RenderNode node, UiRenderer.State state, java.util.Map<String, Object> pageVars) {
        Map<?, ?> spec = UiRenderer.propsMap(node, "tabs");
        List<String> options = new ArrayList<>();
        Object raw = spec.get("options");
        if (raw instanceof List<?> l) {
            for (Object o : l) if (o != null) options.add(UiRenderer.interpolate(node, String.valueOf(o), pageVars));
        }
        if (options.isEmpty()) return;
        String active = state == null ? null : state.dropdownValue(node.id());
        if (active == null) active = UiRenderer.interpolate(node, UiRenderer.str(spec.get("active")), pageVars);
        if (active == null) active = options.get(0);
        int count = options.size();
        double w = node.width() / count;
        double h = node.height();
        int bg = UiStyle.color(spec.get("color"), 0xFF2A3A52);
        int activeBg = UiStyle.color(spec.get("activeColor"), 0xFF42A5F5);
        int textColor = UiStyle.color(spec.get("textColor"), 0xFFE0E0E0);
        int textActive = UiStyle.color(spec.get("textActiveColor"), 0xFFFFFFFF);
        UiRenderer.fillRect(g, node, UiRenderer.alphaColor(bg));
        for (int i = 0; i < count; i++) {
            String opt = options.get(i);
            boolean sel = opt.equals(active);
            if (sel) {
                g.fill((int) (node.x() + i * w), (int) node.y(), (int) (node.x() + (i + 1) * w), (int) (node.y() + h), activeBg);
                g.fill((int) (node.x() + i * w), (int) (node.y() + h - 2), (int) (node.x() + (i + 1) * w), (int) (node.y() + h), 0xFF7A8BFF);
            }
            g.drawString(font, opt, (int) (node.x() + i * w + w / 2 - font.width(opt) / 2), (int) (node.y() + h / 2 - 4), UiRenderer.alphaColor(sel ? textActive : textColor));
        }
    }

    static void drawTable(GuiGraphics g, Font font, RenderNode node, java.util.Map<String, Object> pageVars) {
        Map<?, ?> spec = UiRenderer.propsMap(node, "table");
        List<String> headers = new ArrayList<>();
        Object hr = spec.get("headers");
        if (hr instanceof List<?> hl) for (Object o : hl) if (o != null) headers.add(UiRenderer.interpolate(node, String.valueOf(o), pageVars));
        List<List<String>> rows = new ArrayList<>();
        Object rr = spec.get("rows");
        if (rr instanceof List<?> rl) {
            for (Object row : rl) {
                List<String> out = new ArrayList<>();
                if (row instanceof List<?> cols) for (Object c : cols) out.add(c == null ? "" : UiRenderer.interpolate(node, String.valueOf(c), pageVars));
                else if (row != null) out.add(UiRenderer.interpolate(node, String.valueOf(row), pageVars));
                rows.add(out);
            }
        }
        int cols = Math.max(headers.size(), rows.stream().mapToInt(List::size).max().orElse(0));
        if (cols == 0) return;
        double colW = node.width() / cols;
        double rowH = UiRenderer.num(spec.get("rowHeight"), 12);
        int headerBg = UiStyle.color(spec.get("headerColor"), 0xFF2A3A52);
        int cellBg = UiStyle.color(spec.get("cellColor"), 0xFF1A2332);
        int headerText = UiStyle.color(spec.get("headerTextColor"), 0xFFFFFFFF);
        int cellText = UiStyle.color(spec.get("cellTextColor"), 0xFFC8CFDA);
        UiRenderer.fillRect(g, node, UiRenderer.alphaColor(cellBg));
        if (!headers.isEmpty()) {
            g.fill((int) node.x(), (int) node.y(), (int) (node.x() + node.width()), (int) (node.y() + rowH), headerBg);
            for (int c = 0; c < headers.size(); c++) {
                String t = headers.get(c);
                if (t != null) g.drawString(font, t, (int) (node.x() + c * colW + 4), (int) (node.y() + 2), UiRenderer.alphaColor(headerText));
            }
            g.fill((int) node.x(), (int) (node.y() + rowH), (int) (node.x() + node.width()), (int) (node.y() + rowH + 1), headerBg);
        }
        double baseY = headers.isEmpty() ? node.y() : node.y() + rowH + 1;
        for (int r = 0; r < rows.size(); r++) {
            double y = baseY + r * rowH;
            if (y + rowH > node.y() + node.height()) break;
            List<String> row = rows.get(r);
            for (int c = 0; c < row.size(); c++) {
                String cell = row.get(c);
                if (cell != null && !cell.isEmpty()) g.drawString(font, cell, (int) (node.x() + c * colW + 4), (int) (y + 2), UiRenderer.alphaColor(cellText));
            }
        }
        int borderColor = UiStyle.color(spec.get("border"), 0xFF3A4254);
        g.fill((int) node.x(), (int) node.y(), (int) (node.x() + node.width()), (int) node.y() + 1, borderColor);
        g.fill((int) node.x(), (int) (node.y() + node.height() - 1), (int) (node.x() + node.width()), (int) (node.y() + node.height()), borderColor);
        g.fill((int) node.x(), (int) node.y(), (int) node.x() + 1, (int) (node.y() + node.height()), borderColor);
        g.fill((int) (node.x() + node.width() - 1), (int) node.y(), (int) (node.x() + node.width()), (int) (node.y() + node.height()), borderColor);
    }

}
