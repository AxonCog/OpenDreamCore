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
 * 组件绘制器：OdcScreen（页面）与 HUD 常驻渲染共用。
 * 组件类型：layout/rect/text/button/input/slider/progress/image/video/item_slot/item_display/
 * toggle/dropdown/hot_slot/chat_input/chat_display/scroll/area_input/suggestion/card/flip_card/
 * chart/compass/direction/canvas/boss_bar/embed，其余类型画占位框。
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

        /** 滚动容器当前偏移（0 = 顶部）。 */
        default double scrollY(String id) {
            return 0;
        }

        /** 多行输入框文本（area_input）。 */
        default String areaText(String id) {
            return "";
        }

        /** 输入建议框文本（suggestion）。 */
        default String suggestionText(String id) {
            return "";
        }

        /** 输入建议下拉是否展开。 */
        default boolean suggestionOpen(String id) {
            return false;
        }

        /** 翻牌动画进度 0..1（flip_card：0 = 正面，1 = 背面）。 */
        default double flipProgress(String id) {
            return 0;
        }

        /** 元素是否持有焦点（输入类组件高亮）。 */
        default boolean focused(String id) {
            return false;
        }

        /** 下拉框键盘光标（-1 = 无；展开时高亮该选项）。 */
        default int dropdownCursor(String id) {
            return -1;
        }

        /** 输入建议键盘光标（-1 = 无；展开时高亮该建议项）。 */
        default int suggestionCursor(String id) {
            return -1;
        }
    }

    private static final Pattern TEMPLATE = Pattern.compile("\\{\\{\\s*([^}]+?)\\s*}}");
    /** 单花括号页面变量：{vars.xxx} / {global.xxx}（与 {{vars.xxx}} 等价，双写法都支持）。 */
    private static final Pattern VARS_BRACE = Pattern.compile("\\{(vars|global)\\.[^}]+}");

    private UiRenderer() {
    }

    public static void draw(GuiGraphics g, Font font, List<RenderNode> nodes,
                            int mouseX, int mouseY, State state, java.util.Map<String, Object> pageVars) {
        draw(g, font, nodes, mouseX, mouseY, state, pageVars, null);
    }

    /** scope = 页面 id：动画按页面作用域隔离（屏幕/HUD/世界同帧共存不串扰）。 */
    public static void draw(GuiGraphics g, Font font, List<RenderNode> nodes,
                            int mouseX, int mouseY, State state, java.util.Map<String, Object> pageVars,
                            String scope) {
        for (RenderNode node : nodes) {
            drawNode(g, font, node, mouseX, mouseY, state, pageVars, scope);
        }
    }

    /** 当前绘制透明度（动画 opacity 用，渲染单线程）。 */
    private static float currentAlpha = 1.0F;

    public static void drawNode(GuiGraphics g, Font font, RenderNode node,
                                int mouseX, int mouseY, State state, java.util.Map<String, Object> pageVars) {
        drawNode(g, font, node, mouseX, mouseY, state, pageVars, null);
    }

    public static void drawNode(GuiGraphics g, Font font, RenderNode node,
                                int mouseX, int mouseY, State state, java.util.Map<String, Object> pageVars,
                                String scope) {
        if (!node.visible()) {
            return;
        }
        // 元素动画偏移（页面 animations 定义）：{dx, dy, scale, alpha, rotation}
        double[] anim = AnimationEngine.get().offset(node.id(), scope);
        float prevAlpha = currentAlpha;
        // 静态 opacity/scale/rotation（props，支持表达式）与动画叠加
        double scale = node.scale();
        double alpha = node.opacity();
        double rotation = node.rotation();
        if (anim != null) {
            scale *= anim[2];
            alpha *= anim[3];
            rotation += anim[4];
        }
        boolean usePose = anim != null || scale != 1.0 || rotation != 0.0;
        boolean useAlpha = alpha < 1.0;
        if (usePose || useAlpha) {
            currentAlpha = (float) alpha;
            if (usePose) {
                g.pose().pushPose();
                if (anim != null) {
                    g.pose().translate(anim[0], anim[1], 0);
                }
                // 缩放/旋转绕元素中心
                double cx = node.x() + Math.max(node.width(), 0) / 2.0;
                double cy = node.y() + Math.max(node.height(), 0) / 2.0;
                if (scale != 1.0 || rotation != 0.0) {
                    g.pose().translate(cx, cy, 0);
                    if (rotation != 0.0) {
                        g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees((float) rotation));
                    }
                    g.pose().scale((float) scale, (float) scale, 1.0F);
                    g.pose().translate(-cx, -cy, 0);
                }
            }
        }
        // 元素阴影（shadow: "#33000000" 或 {color, offset, size}）：三层向下偏移半透明暗影，垫底绘制
        // （与世界面板 hologram.shadow 同语义；容器类型不画）
        Object shadowProp = node.props().get("shadow");
        if (shadowProp != null && !"layout".equals(node.type()) && !"scroll".equals(node.type())
                && !"container".equals(node.type()) && !"foreach".equals(node.type())) {
            drawShadow(g, node, shadowProp);
        }
        // 元素发光（glow: "#33FFD700" 或 {color, size}）：四层同心扩散半透明辉光，垫底绘制
        // （与世界面板 hologram.glow 同语义；容器类型不发光）
        Object glowProp = node.props().get("glow");
        if (glowProp != null && !"layout".equals(node.type()) && !"scroll".equals(node.type())
                && !"container".equals(node.type()) && !"foreach".equals(node.type())) {
            drawGlow(g, node, glowProp);
        }
        switch (node.type()) {
            case "layout" -> drawLayout(g, font, node);
            case "container", "foreach" -> drawLayout(g, font, node);
            case "rect" -> drawRect(g, font, node, node.contains(mouseX, mouseY));
            case "text" -> ScreenElements.drawText(g, font, node, pageVars, scope);
            case "button" -> ScreenElements.drawButton(g, font, node, mouseX, mouseY, pageVars);
            case "input" -> ScreenElements.drawInput(g, font, node, state);
            case "slider" -> ScreenElements.drawSlider(g, font, node, state);
            case "progress" -> ScreenElements.drawProgress(g, font, node);
            case "gauge" -> ScreenElements.drawGauge(g, font, node);
            case "arc_slider" -> ScreenElements.drawArcSlider(g, font, node, state);
            case "image" -> ScreenElements.drawImage(g, font, node);
            case "video" -> ScreenElements.drawVideo(g, font, node);
            case "item_slot" -> ScreenElements.drawItemSlot(g, font, node, pageVars);
            case "item_display" -> ScreenElements.drawItemDisplay(g, font, node, pageVars);
            case "chest_slot" -> ScreenElements.drawChestSlot(g, font, node);
            case "hot_slot" -> ScreenElements.drawHotSlot(g, font, node);
            case "chat_input" -> ScreenElements.drawChatInput(g, font, node, state);
            case "chat_display" -> ScreenElements.drawChatDisplay(g, font, node);
            case "toggle" -> ScreenElements.drawToggle(g, font, node, state);
            case "checkbox" -> ScreenElements.drawCheckbox(g, font, node, state);
            case "dropdown" -> ScreenElements.drawDropdown(g, font, node, state, mouseX, mouseY);
            case "scroll" -> drawScroll(g, font, node, state, pageVars, mouseX, mouseY, scope);
            case "area_input" -> ScreenElements.drawAreaInput(g, font, node, state);
            case "suggestion" -> ScreenElements.drawSuggestion(g, font, node, state, mouseX, mouseY);
            case "card" -> ScreenElements.drawCard(g, font, node, pageVars);
            case "flip_card" -> ScreenElements.drawFlipCard(g, font, node, state, pageVars);
            case "chart" -> ScreenElements.drawChart(g, font, node);
            case "compass" -> ScreenElements.drawCompass(g, font, node, pageVars);
            case "direction" -> ScreenElements.drawDirection(g, font, node);
            case "canvas" -> ScreenElements.drawCanvas(g, font, node, pageVars);
            case "boss_bar" -> ScreenElements.drawBossBar(g, font, node);
            case "embed" -> ScreenElements.drawEmbed(g, font, node, pageVars, mouseX, mouseY);
            case "tabs" -> ScreenElements.drawScreenTabs(g, font, node, state, pageVars);
            case "table" -> ScreenElements.drawTable(g, font, node, pageVars);
            default -> ScreenElements.drawPlaceholder(g, font, node);
        }
        // 元素角标（badge: true 红点 / 数字数量 / {count, color}，右上角）——与世界面板 hologram.badge 同语义
        Object badgeProp = node.props().get("badge");
        if (badgeProp != null && !"layout".equals(node.type()) && !"scroll".equals(node.type())
                && !"container".equals(node.type()) && !"foreach".equals(node.type())) {
            ScreenElements.drawScreenBadge(g, font, node, badgeProp);
        }
        // 元素状态图标（statusIcon: 文本或 {icon, color}，左上角）——与世界面板同语义
        Object statusProp = node.props().get("statusIcon");
        if (statusProp != null && !"layout".equals(node.type()) && !"scroll".equals(node.type())
                && !"container".equals(node.type()) && !"foreach".equals(node.type())) {
            ScreenElements.drawScreenStatusIcon(g, font, node, statusProp);
        }
        for (RenderNode child : node.children()) {
            if (!"scroll".equals(node.type())) {
                drawNode(g, font, child, mouseX, mouseY, state, pageVars, scope);
            }
        }
        if (usePose || useAlpha) {
            currentAlpha = prevAlpha;
            if (usePose) {
                g.pose().popPose();
            }
        }
    }

    /** 滚动容器：裁剪内容 + 滚动偏移 + 右侧滚动条。 */
    private static void drawScroll(GuiGraphics g, Font font, RenderNode node, State state,
                                   java.util.Map<String, Object> pageVars, int mouseX, int mouseY,
                                   String scope) {
        fillRect(g, node, alphaColor(UiStyle.color(node.props().get("background"), 0xFF15181E)));
        double offset = state == null ? 0 : Math.max(0, state.scrollY(node.id()));
        // 内容高度 = 子元素最大底部
        double contentH = 0;
        for (RenderNode child : node.children()) {
            contentH = Math.max(contentH, child.y() - node.y() + Math.max(child.height(), 0));
        }
        double viewH = Math.max(node.height(), 0);
        double maxOffset = Math.max(0, contentH - viewH);
        offset = Math.min(offset, maxOffset);
        // 裁剪 + 平移内容
        int[] r = rect(node);
        g.enableScissor(r[0], r[1], r[2], r[3]);
        g.pose().pushPose();
        g.pose().translate(0, -offset, 0);
        for (RenderNode child : node.children()) {
            drawNode(g, font, child, mouseX, mouseY, state, pageVars, scope);
        }
        g.pose().popPose();
        g.disableScissor();
        // 滚动条（内容超出才显示）
        if (maxOffset > 0) {
            int barW = 3;
            int trackH = (int) Math.max(10, viewH - 4);
            double thumbH = Math.max(10, trackH * (viewH / Math.max(contentH, 1)));
            double thumbY = 2 + (trackH - thumbH) * (offset / maxOffset);
            g.fill(r[2] - barW - 1, r[1] + 2, r[2] - 1, r[1] + 2 + trackH, 0x50304050);
            g.fill(r[2] - barW - 1, (int) (r[1] + 2 + thumbY), r[2] - 1, (int) (r[1] + 2 + thumbY + thumbH), 0xB07A8BFF);
        }
    }

    /** 颜色乘当前透明度（元素 opacity 动画）。 */
    static int alphaColor(int color) {
        if (currentAlpha >= 1.0F) {
            return color;
        }
        int a = (int) (((color >>> 24) & 0xFF) * Math.max(0, Math.min(1, currentAlpha)));
        return (a << 24) | (color & 0xFFFFFF);
    }

    /** 元素阴影：三层向下偏移半透明暗影（与世界面板 hologram.shadow 同语义）。 */
    private static void drawShadow(GuiGraphics g, RenderNode node, Object shadowProp) {
        int color = 0x33000000;
        double offset = 1.0;
        double size = 1.0;
        if (shadowProp instanceof Map<?, ?> m) {
            color = UiStyle.color(m.get("color"), color);
            offset = num(m.get("offset"), 1.0);
            size = num(m.get("size"), 1.0);
        } else {
            color = UiStyle.color(shadowProp, color);
        }
        int a0 = (color >>> 24) & 0xFF;
        if (a0 <= 0) {
            return;
        }
        double h = Math.max(node.height(), 0);
        double w = Math.max(node.width(), 0);
        double[] offs = {0.05, 0.11, 0.19};   // 偏移 = 高度 × 比例 × offset
        double[] expands = {1.02, 1.05, 1.08}; // 微扩 = 原尺寸 ×（1 + 扩量 × size）
        double[] alphas = {0.5, 0.3, 0.15};
        double radius = num(node.props().get("radius"), 0);
        int r = (color >> 16) & 0xFF;
        int gg = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        for (int i = 0; i < offs.length; i++) {
            double dy = h * offs[i] * offset;
            double ex = 1 + (expands[i] - 1) * size;
            double x = node.x() - (w * ex - w) / 2;
            double y = node.y() - (h * ex - h) / 2 + dy;
            double ew = w * ex;
            double eh = h * ex;
            int a = (int) (a0 * alphas[i]);
            if (a <= 0 || ew <= 0 || eh <= 0) {
                continue;
            }
            int layerColor = alphaColor((a << 24) | (r << 16) | (gg << 8) | b);
            if (radius > 0) {
                fillRounded(g, x, y, ew, eh, Math.min(radius, Math.min(ew, eh) / 2), layerColor);
            } else {
                g.fill((int) x, (int) y, (int) (x + ew), (int) (y + eh), layerColor);
            }
        }
    }

    /** 元素发光：四层同心扩散半透明矩形（1.25x/1.6x/2.1x/2.8x 透明度递减，与世界面板同参数）。 */
    private static void drawGlow(GuiGraphics g, RenderNode node, Object glowProp) {
        int color = 0x33FFD700;
        double size = 1.0;
        if (glowProp instanceof Map<?, ?> m) {
            color = UiStyle.color(m.get("color"), color);
            size = num(m.get("size"), 1.0);
        } else {
            color = UiStyle.color(glowProp, color);
        }
        int a0 = (color >>> 24) & 0xFF;
        if (a0 <= 0) {
            return;
        }
        double cx = node.x() + Math.max(node.width(), 0) / 2.0;
        double cy = node.y() + Math.max(node.height(), 0) / 2.0;
        double[] expands = {1.25, 1.6, 2.1, 2.8};
        double[] alphas = {0.5, 0.32, 0.18, 0.08};
        int r = (color >> 16) & 0xFF;
        int gg = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        for (int i = 0; i < expands.length; i++) {
            double e = 1 + (expands[i] - 1) * size;
            int w = (int) Math.round(Math.max(node.width(), 0) * e);
            int h = (int) Math.round(Math.max(node.height(), 0) * e);
            int a = (int) (a0 * alphas[i]);
            if (a <= 0 || w <= 0 || h <= 0) {
                continue;
            }
            g.fill((int) (cx - w / 2.0), (int) (cy - h / 2.0), (int) (cx + w / 2.0), (int) (cy + h / 2.0),
                    alphaColor((a << 24) | (r << 16) | (gg << 8) | b));
        }
    }

    private static void drawLayout(GuiGraphics g, Font font, RenderNode node) {
        int color = UiStyle.color(node.props().get("background"), 0);
        if (color != 0) {
            fillRect(g, node, alphaColor(color));
        }
    }

    private static void drawRect(GuiGraphics g, Font font, RenderNode node, boolean hovered) {
        int fill = alphaColor(UiStyle.color(node.props().get("color"), UiStyle.color(node.props().get("background"), 0xFFFFFFFF)));
        int gradient = UiStyle.color(node.props().get("gradient"), 0);
        double radius = num(node.props().get("radius"), 0);
        String gDir = str(node.props().get("gradientDir"));
        boolean vertical = gDir == null || !"horizontal".equalsIgnoreCase(gDir);
        BorderSpec bs = parseBorder(node.props().get("border"),
                (int) num(node.props().get("borderWidth"), 1));
        if (bs.flow() || bool(node.props().get("flow"), false)) {
            int flowColor = UiStyle.color(node.props().get("flowColor"), bs.flowColor());
            if (gradient != 0) {
                drawGradientRectDir(g, node, radius, fill, gradient, vertical, bs.color(), bs.width());
                drawBorderFlow(g, node, radius, bs.color(), bs.width(), flowColor, hovered);
            } else {
                drawRoundedRectFlow(g, node, radius, fill, bs.color(), bs.width(), flowColor, hovered);
            }
        } else if (gradient != 0) {
            drawGradientRectDir(g, node, radius, fill, gradient, vertical, bs.color(), bs.width());
        } else if (radius > 0 || bs.color() != 0) {
            String dashRaw = str(node.props().get("dash"));
            String doubleRaw = str(node.props().get("double"));
            if ((dashRaw != null && !dashRaw.isBlank()) || "true".equalsIgnoreCase(doubleRaw)) {
                drawDashedBorder(g, node, radius, bs.color(), bs.width(),
                        dashRaw != null ? dashRaw : "solid", doubleRaw);
            } else {
                drawRoundedRect(g, node, radius, fill, bs.color(), bs.width());
            }
        } else {
            fillRect(g, node, fill);
        }
    }

    /** 描边解析：颜色字符串 或 {color, width, flow, flowColor} 对象。 */
    record BorderSpec(int color, int width, boolean flow, int flowColor) {
    }

    static BorderSpec parseBorder(Object raw, int widthDefault) {
        if (raw instanceof Map<?, ?> bm) {
            return new BorderSpec(UiStyle.color(bm.get("color"), 0),
                    (int) num(bm.get("width"), widthDefault),
                    Boolean.parseBoolean(String.valueOf(bm.get("flow"))),
                    UiStyle.color(bm.get("flowColor"), 0xFFFFFFFF));
        }
        return new BorderSpec(UiStyle.color(raw, 0), widthDefault, false, 0xFFFFFFFF);
    }

    // ---------- 圆角/描边矩形（三角剖分） ----------

    /** 圆角矩形：外圈 border 层 + 内缩 fill 层；radius=0 且 border=0 走普通 fillRect。 */
    static void drawRoundedRect(GuiGraphics g, RenderNode node, double radius,
                                        int fill, int border, int borderW) {
        double x = node.x();
        double y = node.y();
        double w = node.width();
        double h = node.height();
        double r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
        int alpha = (border >>> 24) & 0xFF;
        if (borderW > 0 && alpha > 0) {
            fillRounded(g, x, y, w, h, r, border);
        }
        double inset = borderW > 0 && ((border >>> 24) & 0xFF) > 0 ? borderW : 0;
        if (inset * 2 >= w || inset * 2 >= h) {
            return;
        }
        fillRounded(g, x + inset, y + inset, w - inset * 2, h - inset * 2,
                Math.max(0, r - inset), fill);
    }

    private static void drawGradientRect(GuiGraphics g, RenderNode node, double radius,
                                         int fill, int gradient, int border, int borderW) {
        drawGradientRectDir(g, node, radius, fill, gradient, true, border, borderW);
    }

    private static void drawGradientRectDir(GuiGraphics g, RenderNode node, double radius,
                                            int fill, int gradient, boolean vertical, int border, int borderW) {
        double x = node.x();
        double y = node.y();
        double w = node.width();
        double h = node.height();
        double r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
        int alpha = (border >>> 24) & 0xFF;
        if (borderW > 0 && alpha > 0) {
            fillRounded(g, x, y, w, h, r, border);
        }
        double inset = borderW > 0 && alpha > 0 ? borderW : 0;
        if (inset * 2 >= w || inset * 2 >= h) {
            return;
        }
        if (vertical) {
            fillRoundedGrad(g, x + inset, y + inset, w - inset * 2, h - inset * 2,
                    Math.max(0, r - inset), fill, gradient);
        } else {
            fillRoundedGradDir(g, x + inset, y + inset, w - inset * 2, h - inset * 2,
                    Math.max(0, r - inset), fill, gradient, false);
        }
    }

    private static void drawDashedBorder(GuiGraphics g, RenderNode node, double radius,
                                         int border, int borderW, String dash, String dbl) {
        boolean isDouble = "true".equalsIgnoreCase(dbl);
        double x = node.x();
        double y = node.y();
        double w = node.width();
        double h = node.height();
        double r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
        int fill = alphaColor(UiStyle.color(node.props().get("color"), 0xFFFFFFFF));
        int gradient = UiStyle.color(node.props().get("gradient"), 0);
        if (gradient != 0) {
            String gDir = str(node.props().get("gradientDir"));
            boolean vert = gDir == null || !"horizontal".equalsIgnoreCase(gDir);
            if (vert) fillRoundedGrad(g, x + borderW, y + borderW, w - borderW * 2, h - borderW * 2,
                    Math.max(0, r - borderW), fill, gradient);
            else fillRoundedGradDir(g, x + borderW, y + borderW, w - borderW * 2, h - borderW * 2,
                    Math.max(0, r - borderW), fill, gradient, false);
        } else {
            fillRounded(g, x + borderW, y + borderW, w - borderW * 2, h - borderW * 2,
                    Math.max(0, r - borderW), fill);
        }
        double dashLen = "dashed".equalsIgnoreCase(dash) ? 6 : "dotted".equalsIgnoreCase(dash) ? 2 : 0;
        double gap = dashLen > 0 ? dashLen : 0;
        if (isDouble) {
            for (int k = 0; k < 2; k++) {
                double off = k == 0 ? 0 : borderW / 2 + 1;
                double ww = w - off * 2;
                double hh = h - off * 2;
                double rr = Math.max(0, r - off);
                if (dashLen > 0) drawDashedOutline(g, x + off, y + off, ww, hh, rr, border, 1, dashLen, gap);
                else fillRounded(g, x + off, y + off, ww, hh, rr, border);
            }
        } else if (dashLen > 0) {
            drawDashedOutline(g, x, y, w, h, r, border, borderW, dashLen, gap);
        }
    }

    private static void drawDashedOutline(GuiGraphics g, double x, double y, double w, double h,
                                          double r, int color, int bw, double dashLen, double gap) {
        double perim = 2 * Math.max(0, w - 2 * r) + 2 * Math.max(0, h - 2 * r) + 2 * Math.PI * r;
        if (perim <= 0) return;
        double step = dashLen + gap;
        int segs = (int) Math.ceil(perim / step);
        float cr = ((color >> 16) & 0xFF) / 255F;
        float cg = ((color >> 8) & 0xFF) / 255F;
        float cb = (color & 0xFF) / 255F;
        float ca = ((color >>> 24) & 0xFF) / 255F;
        if (ca <= 0) return;
        var mat = g.pose().last().pose();
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
        com.mojang.blaze3d.systems.RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionColorShader);
        var b = com.mojang.blaze3d.vertex.Tesselator.getInstance()
                .begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR);
        double lEdge = Math.max(0, w - 2 * r);
        double lArc = Math.PI * r / 2;
        for (int i = 0; i < segs; i++) {
            double s0 = i * step;
            double s1 = Math.min(s0 + dashLen, perim);
            double[] p0 = roundedOutlinePoint(x, y, w, h, r, lEdge, lArc, s0);
            double[] p1 = roundedOutlinePoint(x, y, w, h, r, lEdge, lArc, s1);
            double dx = p1[0] - p0[0];
            double dy = p1[1] - p0[1];
            double len = Math.sqrt(dx * dx + dy * dy);
            if (len < 0.01) continue;
            double nx = -dy / len * bw / 2;
            double ny = dx / len * bw / 2;
            quadV(b, mat, p0[0] + nx, p0[1] + ny, p1[0] + nx, p1[1] + ny, p1[0] - nx, p1[1] - ny, p0[0] - nx, p0[1] - ny, cr, cg, cb, ca);
        }
        com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(b.buildOrThrow());
        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
    }

    /** 圆角描边流光：border 外圈 + 亮色段沿周长匀速流动（1.2s 一圈，段长 15% 周长）+ 内缩 fill。 */
    static void drawRoundedRectFlow(GuiGraphics g, RenderNode node, double radius,
                                            int fill, int border, int borderW, int flowColor,
                                            boolean hovered) {
        double x = node.x();
        double y = node.y();
        double w = node.width();
        double h = node.height();
        double r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
        int alpha = (border >>> 24) & 0xFF;
        if (borderW > 0 && alpha > 0) {
            fillRounded(g, x, y, w, h, r, border);
            drawBorderFlow(g, node, radius, border, borderW, flowColor, hovered);
        }
        double inset = borderW > 0 && alpha > 0 ? borderW : 0;
        if (inset * 2 >= w || inset * 2 >= h) {
            return;
        }
        fillRounded(g, x + inset, y + inset, w - inset * 2, h - inset * 2,
                Math.max(0, r - inset), fill);
    }

    /** 描边流光段：沿圆角矩形周长匀速流动（1.2s 一圈，段长 15% 周长），贴 border 外缘绘制；
     *  hover 加速提亮（450ms 一圈、段长 22%、亮度 ×1.6，与世界面板同步）。 */
    private static void drawBorderFlow(GuiGraphics g, RenderNode node, double radius,
                                       int border, int borderW, int flowColor, boolean hovered) {
        double x = node.x();
        double y = node.y();
        double w = node.width();
        double h = node.height();
        double r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
        double fw = Math.max(1, borderW);
        // 周长：四直边 + 四圆弧
        double lEdge = Math.max(0, w - 2 * r);
        double lArc = Math.PI * r / 2;
        double perimeter = 4 * lEdge + 4 * lArc;
        if (perimeter <= 0.01) {
            return;
        }
        long cycleMs = hovered ? 450 : 1200;
        double segLen = perimeter * (hovered ? 0.22 : 0.15);
        double cycle = (System.currentTimeMillis() % cycleMs) / (double) cycleMs * perimeter;
        int samples = Math.max(4, (int) Math.ceil(segLen / 3.0));
        int ac = (flowColor >>> 24) & 0xFF;
        if (ac <= 0) {
            return;
        }
        float fr = ((flowColor >> 16) & 0xFF) / 255.0F;
        float fg = ((flowColor >> 8) & 0xFF) / 255.0F;
        float fb = (flowColor & 0xFF) / 255.0F;
        float fa = Math.min(1.0F, ac / 255.0F * (hovered ? 1.6F : 1.0F));
        var matrix = g.pose().last().pose();
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
        com.mojang.blaze3d.systems.RenderSystem.setShader(
                net.minecraft.client.renderer.GameRenderer::getPositionColorShader);
        var builder = com.mojang.blaze3d.vertex.Tesselator.getInstance()
                .begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.TRIANGLES,
                        com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR);
        double[] prev = null;
        for (int i = 0; i <= samples; i++) {
            double s = cycle + segLen * i / samples;
            double[] p = roundedOutlinePoint(x, y, w, h, r, lEdge, lArc, s % perimeter);
            if (prev == null) {
                prev = p;
                continue;
            }
            // 路径点连线 → 宽度 fw 的四边形（沿路径方向）
            double dx = p[0] - prev[0];
            double dy = p[1] - prev[1];
            double len = Math.sqrt(dx * dx + dy * dy);
            if (len < 0.01) {
                prev = p;
                continue;
            }
            double nx = -dy / len * fw / 2;
            double ny = dx / len * fw / 2;
            quadV(builder, matrix, prev[0] + nx, prev[1] + ny, p[0] + nx, p[1] + ny,
                    p[0] - nx, p[1] - ny, prev[0] - nx, prev[1] - ny, fr, fg, fb, fa);
            prev = p;
        }
        com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(builder.buildOrThrow());
        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
    }

    /** 圆角矩形周长参数 s(0..perimeter) → 轮廓点（起点：左下角直边左端，顺时针）。 */
    private static double[] roundedOutlinePoint(double x, double y, double w, double h, double r,
                                                double lEdge, double lArc, double s) {
        double segBottom = lEdge;
        double segArcBR = segBottom + lArc;
        double segRight = segArcBR + Math.max(0, h - 2 * r);
        double segArcTR = segRight + lArc;
        double segTop = segArcTR + lEdge;
        double segArcTL = segTop + lArc;
        double segLeft = segArcTL + Math.max(0, h - 2 * r);
        double segArcBL = segLeft + lArc;
        if (s < segBottom) {
            return new double[]{x + r + s, y + h};
        }
        if (s < segArcBR) {
            double a = (s - segBottom) / lArc * (Math.PI / 2);
            return new double[]{x + w - r + r * Math.cos(a), y + h - r + r * Math.sin(a)};
        }
        if (s < segRight) {
            return new double[]{x + w, y + h - r - (s - segArcBR)};
        }
        if (s < segArcTR) {
            double a = -Math.PI / 2 + (s - segRight) / lArc * (Math.PI / 2);
            return new double[]{x + w - r + r * Math.cos(a), y + r + r * Math.sin(a)};
        }
        if (s < segTop) {
            return new double[]{x + w - r - (s - segArcTR), y};
        }
        if (s < segArcTL) {
            double a = -Math.PI + (s - segTop) / lArc * (Math.PI / 2);
            return new double[]{x + r + r * Math.cos(a), y + r + r * Math.sin(a)};
        }
        if (s < segLeft) {
            return new double[]{x, y + r + (s - segArcTL)};
        }
        // 左下圆弧（回到起点）
        double a = Math.PI / 2 + (s - segLeft) / lArc * (Math.PI / 2);
        return new double[]{x + r + r * Math.cos(a), y + h - r + r * Math.sin(a)};
    }

    /** 四边形（4 点，2 三角形）。 */
    private static void quadV(com.mojang.blaze3d.vertex.VertexConsumer builder, org.joml.Matrix4f matrix,
                              double x0, double y0, double x1, double y1, double x2, double y2,
                              double x3, double y3, float r, float g, float b, float a) {
        builder.addVertex(matrix, (float) x0, (float) y0, 0).setColor(r, g, b, a);
        builder.addVertex(matrix, (float) x1, (float) y1, 0).setColor(r, g, b, a);
        builder.addVertex(matrix, (float) x2, (float) y2, 0).setColor(r, g, b, a);
        builder.addVertex(matrix, (float) x0, (float) y0, 0).setColor(r, g, b, a);
        builder.addVertex(matrix, (float) x2, (float) y2, 0).setColor(r, g, b, a);
        builder.addVertex(matrix, (float) x3, (float) y3, 0).setColor(r, g, b, a);
    }

    /** 屏幕空间圆角矩形填充（TRIANGLES：中矩形 + 四角扇形）。 */
    static void fillRounded(GuiGraphics g, double x, double y, double w, double h,
                                    double r, int color) {
        if (w <= 0 || h <= 0) {
            return;
        }
        float red = ((color >> 16) & 0xFF) / 255.0F;
        float green = ((color >> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        float alpha = ((color >>> 24) & 0xFF) / 255.0F;
        if (alpha <= 0) {
            return;
        }
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        var matrix = g.pose().last().pose();
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
        com.mojang.blaze3d.systems.RenderSystem.setShader(
                net.minecraft.client.renderer.GameRenderer::getPositionColorShader);
        var builder = com.mojang.blaze3d.vertex.Tesselator.getInstance()
                .begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.TRIANGLES,
                        com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR);
        // 中间矩形（2 三角形）
        double cx0 = x + r;
        double cy0 = y + r;
        double cx1 = x + w - r;
        double cy1 = y + h - r;
        quad(builder, matrix, cx0, cy0, cx1, cy1, red, green, blue, alpha);
        if (r > 0) {
            int segments = 8;
            // 四角扇形：左上 π→3π/2、右上 3π/2→2π、右下 0→π/2、左下 π/2→π
            cornerFan(builder, matrix, cx0, cy0, r, Math.PI, Math.PI * 1.5, segments, red, green, blue, alpha);
            cornerFan(builder, matrix, cx1, cy0, r, Math.PI * 1.5, Math.PI * 2, segments, red, green, blue, alpha);
            cornerFan(builder, matrix, cx1, cy1, r, 0, Math.PI / 2, segments, red, green, blue, alpha);
            cornerFan(builder, matrix, cx0, cy1, r, Math.PI / 2, Math.PI, segments, red, green, blue, alpha);
        }
        com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(builder.buildOrThrow());
        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
    }

    private static void quad(com.mojang.blaze3d.vertex.VertexConsumer builder, org.joml.Matrix4f matrix,
                             double x0, double y0, double x1, double y1,
                             float r, float g, float b, float a) {
        builder.addVertex(matrix, (float) x0, (float) y0, 0).setColor(r, g, b, a);
        builder.addVertex(matrix, (float) x1, (float) y0, 0).setColor(r, g, b, a);
        builder.addVertex(matrix, (float) x1, (float) y1, 0).setColor(r, g, b, a);
        builder.addVertex(matrix, (float) x0, (float) y0, 0).setColor(r, g, b, a);
        builder.addVertex(matrix, (float) x1, (float) y1, 0).setColor(r, g, b, a);
        builder.addVertex(matrix, (float) x0, (float) y1, 0).setColor(r, g, b, a);
    }

    /** 屏幕空间圆角矩形渐变填充（方向由 gradientDir 决定，默认垂直）。 */
    private static void fillRoundedGrad(GuiGraphics g, double x, double y, double w, double h,
                                        double r, int top, int bottom) {
        fillRoundedGradDir(g, x, y, w, h, r, top, bottom, true);
    }

    private static void fillRoundedGradDir(GuiGraphics g, double x, double y, double w, double h,
                                           double r, int top, int bottom, boolean vertical) {
        if (w <= 0 || h <= 0) {
            return;
        }
        int at = (top >>> 24) & 0xFF;
        int ab = (bottom >>> 24) & 0xFF;
        if (at <= 0 && ab <= 0) {
            return;
        }
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        var matrix = g.pose().last().pose();
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
        com.mojang.blaze3d.systems.RenderSystem.setShader(
                net.minecraft.client.renderer.GameRenderer::getPositionColorShader);
        var builder = com.mojang.blaze3d.vertex.Tesselator.getInstance()
                .begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.TRIANGLES,
                        com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR);
        double cx0 = x + r;
        double cy0 = y + r;
        double cx1 = x + w - r;
        double cy1 = y + h - r;
        quadGrad(builder, matrix, cx0, cy0, cx1, cy1, top, bottom, y, h);
        if (r > 0) {
            int segments = 8;
            cornerFanGrad(builder, matrix, cx0, cy0, r, Math.PI, Math.PI * 1.5, segments, top, bottom, y, h);
            cornerFanGrad(builder, matrix, cx1, cy0, r, Math.PI * 1.5, Math.PI * 2, segments, top, bottom, y, h);
            cornerFanGrad(builder, matrix, cx1, cy1, r, 0, Math.PI / 2, segments, top, bottom, y, h);
            cornerFanGrad(builder, matrix, cx0, cy1, r, Math.PI / 2, Math.PI, segments, top, bottom, y, h);
        }
        com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(builder.buildOrThrow());
        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
    }

    /** 渐变插值色：top → bottom 按 t(0~1)。 */
    private static int gradColor(int top, int bottom, double t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) ((((top >> 16) & 0xFF) + (((bottom >> 16) & 0xFF) - ((top >> 16) & 0xFF)) * t));
        int g = (int) ((((top >> 8) & 0xFF) + (((bottom >> 8) & 0xFF) - ((top >> 8) & 0xFF)) * t));
        int b = (int) (((top & 0xFF) + ((bottom & 0xFF) - (top & 0xFF)) * t));
        int a = (int) ((((top >>> 24) & 0xFF) + (((bottom >>> 24) & 0xFF) - ((top >>> 24) & 0xFF)) * t));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static void addV(com.mojang.blaze3d.vertex.VertexConsumer builder, org.joml.Matrix4f matrix,
                             double x, double y, int color) {
        builder.addVertex(matrix, (float) x, (float) y, 0)
                .setColor(((color >> 16) & 0xFF) / 255.0F, ((color >> 8) & 0xFF) / 255.0F,
                        (color & 0xFF) / 255.0F, ((color >>> 24) & 0xFF) / 255.0F);
    }

    /** 渐变矩形（2 三角形，顶点色按 y 插值）。 */
    private static void quadGrad(com.mojang.blaze3d.vertex.VertexConsumer builder, org.joml.Matrix4f matrix,
                                 double x0, double y0, double x1, double y1,
                                 int top, int bottom, double baseY, double h) {
        int c00 = gradColor(top, bottom, (y0 - baseY) / h);
        int c10 = gradColor(top, bottom, (y0 - baseY) / h);
        int c01 = gradColor(top, bottom, (y1 - baseY) / h);
        int c11 = gradColor(top, bottom, (y1 - baseY) / h);
        addV(builder, matrix, x0, y0, c00);
        addV(builder, matrix, x1, y0, c10);
        addV(builder, matrix, x1, y1, c11);
        addV(builder, matrix, x0, y0, c00);
        addV(builder, matrix, x1, y1, c11);
        addV(builder, matrix, x0, y1, c01);
    }

    /** 渐变角扇形：顶点色按 y 插值。 */
    private static void cornerFanGrad(com.mojang.blaze3d.vertex.VertexConsumer builder, org.joml.Matrix4f matrix,
                                      double cx, double cy, double r, double from, double to, int segments,
                                      int top, int bottom, double baseY, double h) {
        for (int i = 0; i < segments; i++) {
            double a0 = from + (to - from) * i / segments;
            double a1 = from + (to - from) * (i + 1) / segments;
            addV(builder, matrix, cx, cy, gradColor(top, bottom, (cy - baseY) / h));
            double px0 = cx + r * Math.cos(a0);
            double py0 = cy + r * Math.sin(a0);
            addV(builder, matrix, px0, py0, gradColor(top, bottom, (py0 - baseY) / h));
            double px1 = cx + r * Math.cos(a1);
            double py1 = cy + r * Math.sin(a1);
            addV(builder, matrix, px1, py1, gradColor(top, bottom, (py1 - baseY) / h));
        }
    }

    /** 角落扇形（圆心 + 弧段，TRIANGLES 逐段）。 */
    private static void cornerFan(com.mojang.blaze3d.vertex.VertexConsumer builder, org.joml.Matrix4f matrix,
                                  double cx, double cy, double r, double from, double to, int segments,
                                  float red, float green, float blue, float alpha) {
        for (int i = 0; i < segments; i++) {
            double a0 = from + (to - from) * i / segments;
            double a1 = from + (to - from) * (i + 1) / segments;
            builder.addVertex(matrix, (float) cx, (float) cy, 0).setColor(red, green, blue, alpha);
            builder.addVertex(matrix, (float) (cx + r * Math.cos(a0)), (float) (cy + r * Math.sin(a0)), 0)
                    .setColor(red, green, blue, alpha);
            builder.addVertex(matrix, (float) (cx + r * Math.cos(a1)), (float) (cy + r * Math.sin(a1)), 0)
                    .setColor(red, green, blue, alpha);
        }
    }

    // ---------- 逐字揭示（text.reveal） ----------
    /** 逐字动画状态（scope+元素 id → 首次渲染毫秒 + 内容快照，内容变化重触发）。 */
    static final java.util.Map<String, ScreenElements.RevealState> textRevealState = new java.util.concurrent.ConcurrentHashMap<>();
    static final int REVEAL_PRUNE_THRESHOLD = 800;
    public static void clearRevealScope(String scope) {
        if (scope == null) { textRevealState.clear(); return; }
        String prefix = scope + "\u0001";
        textRevealState.keySet().removeIf(k -> k.startsWith(prefix));
    }

    // ---------- 角标 / 状态图标（badge / statusIcon） ----------

    // ---------- 仪表盘 / 环形滑块（弧形渲染） ----------

    /** 槽位里的物品图标（item: "minecraft:diamond_sword"，可带数量 "id x64"；支持 {{vars.xxx}} 插值）。 */
    static void drawItemIcon(GuiGraphics g, Font font, RenderNode node, Object raw, boolean big,
                                     java.util.Map<String, Object> pageVars) {
        double size = big ? Math.max(16, node.width() * 0.7) : Math.min(16, Math.min(node.width(), node.height()));
        double ix = node.x() + (node.width() - size) / 2;
        double iy = node.y() + (node.height() - size) / 2;
        drawItemAt(g, font, ix, iy, size, raw, pageVars);
    }

    /** 在任意坐标画物品图标（card 图标、容器光标等用）。 */
    public static void drawItemAt(GuiGraphics g, Font font, double x, double y, double size, Object raw,
                                  java.util.Map<String, Object> pageVars) {
        String id = raw == null ? null : String.valueOf(raw).trim();
        if (id == null || id.isEmpty()) {
            return;
        }
        id = interpolate(null, id, pageVars); // 动态物品：服务端 state_patch 改 vars 后重渲染
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
        int icon = (int) Math.max(8, size);
        int ix = (int) (x + (size - icon) / 2);
        int iy = (int) (y + (size - icon) / 2);
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

    static void drawItemAtRot(GuiGraphics g, Font font, double x, double y, double size, Object raw,
                                      java.util.Map<String, Object> pageVars, Map<?, ?> rotSpec) {
        String id = raw == null ? null : String.valueOf(raw).trim();
        if (id == null || id.isEmpty()) return;
        id = interpolate(null, id, pageVars);
        if (id == null || id.isEmpty()) return;
        int count = 1;
        String[] parts = id.split("\\s+");
        if (parts.length >= 3 && "x".equalsIgnoreCase(parts[1])) {
            id = parts[0];
            try { count = Integer.parseInt(parts[2]); } catch (NumberFormatException ignored) {}
        }
        ItemStack stack = parseItem(id);
        if (stack.isEmpty()) return;
        stack.setCount(count);
        int icon = (int) Math.max(8, size);
        int ix = (int) (x + (size - icon) / 2);
        int iy = (int) (y + (size - icon) / 2);
        var pose = g.pose();
        pose.pushPose();
        pose.translate(ix + icon / 2.0, iy + icon / 2.0, 0);
        if (rotSpec != null) {
            double rx = num(rotSpec.get("x"), num(rotSpec.get("rx"), 0));
            double ry = num(rotSpec.get("y"), num(rotSpec.get("ry"), 0));
            double rz = num(rotSpec.get("z"), num(rotSpec.get("rz"), 0));
            if (rx != 0) pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees((float) rx));
            if (ry != 0) pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees((float) ry));
            if (rz != 0) pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees((float) rz));
        }
        float scale = icon / 16.0F;
        pose.scale(scale, scale, 1.0F);
        pose.translate(-icon / 2.0, -icon / 2.0, 0);
        g.renderItem(stack, 0, 0);
        if (count > 1) g.renderItemDecorations(font, stack, 0, 0);
        pose.popPose();
    }

    // ========== 输入类：area_input / suggestion ==========

    // ========== 展示类：card / flip_card / chart ==========

    // ========== 方位类：compass / direction ==========

    // ========== 画布：canvas ==========

    public static int lerpColor(int from, int to, double t) {
        int a = (int) (((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * t);
        int r = (int) (((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int gr = (int) (((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (a << 24) | (r << 16) | (gr << 8) | b;
    }

    // ========== 顶部 Boss 条：boss_bar ==========

    // ========== 运行时嵌入：embed ==========

    /** 嵌入深度防护（嵌入页再嵌入 → 死循环）。 */
    static final ThreadLocal<Integer> EMBED_DEPTH = ThreadLocal.withInitial(() -> 0);

    public static boolean bool(Object v, boolean fallback) {
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

    /** 解析物品 id → ItemStack（世界 UI/物品提示等共用）。 */
    public static ItemStack parseItemStatic(String id) {
        return parseItem(id);
    }

    /** {{vars.coin}} / {{global.xxx}} 插值 + {player.name} 等占位符；解析失败原样保留。 */
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
        // 单花括号页面变量：{vars.xxx} / {global.xxx}
        Matcher vb = VARS_BRACE.matcher(sb);
        StringBuilder out = new StringBuilder();
        while (vb.find()) {
            String path = vb.group().substring(1, vb.group().length() - 1).trim();
            Object value = resolvePath(pageVars, path);
            vb.appendReplacement(out, Matcher.quoteReplacement(value == null ? vb.group() : String.valueOf(value)));
        }
        vb.appendTail(out);
        // 多语言占位符：{lang.键名} → 客户端语言文件（游戏目录覆盖 / 模组资源 / en_us 回退）
        return WorldLang.resolve(com.opendreamcore.script.PlaceholderRegistry.resolve(out.toString()));
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

    // ---- 公共 API 转发（实现移至 ScreenElements，round 6）----
    public static String suggestionValue(Object s) {
        return ScreenElements.suggestionValue(s);
    }

    public static java.util.List<Object> filterSuggestions(java.util.Map<?, ?> spec, String text) {
        return ScreenElements.filterSuggestions(spec, text);
    }

    public static String[] wrapLinesFlat(net.minecraft.client.gui.Font font, String text, int maxWidth) {
        return ScreenElements.wrapLinesFlat(font, text, maxWidth);
    }
}
