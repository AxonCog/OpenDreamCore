package com.opendreamcore.client.elements;

import net.minecraft.resources.ResourceLocation;

import com.opendreamcore.client.GifPlayer;

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
 * C2 拆分自 ScreenElements（WorldMiscDraws 组件族）。方法体逐字搬运，可见性放宽为 public。
 */
public final class WorldMiscDraws {
    private WorldMiscDraws() {}

    /** 指南针：随玩家朝向滚动的东西南北刻度条 + 中央指针 + 可选路标（waypoints）。 */
    public static void drawCompass(GuiGraphics g, Font font, RenderNode node,
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
    public static void drawDirection(GuiGraphics g, Font font, RenderNode node) {
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
    public static char facingArrow(double relDeg) {
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
    public static void drawCanvas(GuiGraphics g, Font font, RenderNode node, java.util.Map<String, Object> pageVars) {
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

    public static void drawCanvasCircle(GuiGraphics g, RenderNode node, Map<?, ?> m) {
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

    public static void drawCanvasLine(GuiGraphics g, RenderNode node, Map<?, ?> m) {
        double x1 = node.x() + UiRenderer.num(m.get("x1"), 0);
        double y1 = node.y() + UiRenderer.num(m.get("y1"), 0);
        double x2 = node.x() + UiRenderer.num(m.get("x2"), 10);
        double y2 = node.y() + UiRenderer.num(m.get("y2"), 10);
        int color = UiStyle.color(m.get("color"), 0xFFFFFFFF);
        int width = (int) Math.max(1, UiRenderer.num(m.get("width"), 1));
        ChartDraws.drawLinePx(g, x1, y1, x2, y2, color, width);
    }

    public static void drawCanvasGradient(GuiGraphics g, RenderNode node, Map<?, ?> m) {
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
    public static void drawCanvasTriangle(GuiGraphics g, RenderNode node, Map<?, ?> m) {
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

    public static void drawCanvasImage(GuiGraphics g, RenderNode node, Map<?, ?> m) {
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
        CompatRender.blit(g, texture, (int) x, (int) y, (int) w, (int) h, 0.0F, 0.0F, (int) w, (int) h, (int) w, (int) h);
    }

    /** 顶部 Boss 条：暗屏 + 分段血条 + 居中文字（P3 由服务端控制进度）。 */
    public static void drawBossBar(GuiGraphics g, Font font, RenderNode node) {
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
    public static void drawEmbed(GuiGraphics g, Font font, RenderNode node,
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
        CompatRender.posePush(g.pose());
        CompatRender.poseTranslate(g.pose(), node.x(), node.y());
        UiRenderer.EMBED_DEPTH.set(depth + 1);
        try {
            UiRenderer.draw(g, font, embedded, mouseX - (int) node.x(), mouseY - (int) node.y(), null, target.variables());
        } finally {
            UiRenderer.EMBED_DEPTH.set(depth);
        }
        CompatRender.posePop(g.pose());
        g.disableScissor();
    }

    public static void drawPlaceholder(GuiGraphics g, Font font, RenderNode node) {
        UiRenderer.fillRect(g, node, UiRenderer.alphaColor(0x40FF9800));
        g.drawString(font, "[" + node.type() + "]", (int) node.x() + 2, (int) node.y() + 2, UiRenderer.alphaColor(0xFFFFD54F));
    }

    public static void drawScreenTabs(GuiGraphics g, Font font, RenderNode node, UiRenderer.State state, java.util.Map<String, Object> pageVars) {
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

    public static void drawTable(GuiGraphics g, Font font, RenderNode node, java.util.Map<String, Object> pageVars) {
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
