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
 * C2 拆分自 ScreenElements（ChartDraws 组件族）。方法体逐字搬运，可见性放宽为 public。
 */
public final class ChartDraws {
    private ChartDraws() {}

    /** 图表：bar（柱状）/ line（折线）/ pie（饼图）。 */
    public static void drawChart(GuiGraphics g, Font font, RenderNode node) {
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
            WorldMiscDraws.drawPlaceholder(g, font, node);
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

    public static void drawBarChart(GuiGraphics g, Font font, RenderNode node, List<Double> data,
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

    public static void drawLineChart(GuiGraphics g, Font font, RenderNode node, List<Double> data,
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
    public static void drawPieChart(GuiGraphics g, Font font, RenderNode node, List<Double> data,
                                     int baseColor, List<?> rawColors, boolean showLabels, Map<?, ?> spec) {
        double total = 0;
        for (double v : data) {
            total += v;
        }
        if (total <= 0) {
            WorldMiscDraws.drawPlaceholder(g, font, node);
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
    public static void fillPieRow(GuiGraphics g, int py, double cx, double cy, double dy, double dx,
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

    public static double clampX(double x, double min, double max) {
        return Math.max(min, Math.min(max, x));
    }

    public static double normAngle(double a) {
        a = a % (Math.PI * 2);
        return a < 0 ? a + Math.PI * 2 : a;
    }

    /** 角度落在哪个切片（cum 为边界数组，含 0 与 2π）。 */
    public static int sliceAt(double[] cum, double angle) {
        for (int i = 0; i < cum.length - 1; i++) {
            if (angle >= cum[i] && angle < cum[i + 1]) {
                return i;
            }
        }
        return cum.length - 2;
    }

    public static int colorAt(List<?> colors, int index, int fallback) {
        if (colors != null && index < colors.size()) {
            return UiStyle.color(colors.get(index), fallback);
        }
        return fallback;
    }

    public static double maxOf(List<Double> data) {
        double max = 0;
        for (double v : data) {
            max = Math.max(max, v);
        }
        return max;
    }

    public static int brighten(int color) {
        int r = Math.min(255, ((color >> 16) & 0xFF) + 40);
        int gr = Math.min(255, ((color >> 8) & 0xFF) + 40);
        int b = Math.min(255, (color & 0xFF) + 40);
        return (color & 0xFF000000) | (r << 16) | (gr << 8) | b;
    }

    /** 像素级直线（DDA）。 */
    public static void drawLinePx(GuiGraphics g, double x1, double y1, double x2, double y2, int color, int width) {
        int steps = (int) Math.max(1, Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1)));
        int half = Math.max(0, width - 1);
        for (int i = 0; i <= steps; i++) {
            double t = steps == 0 ? 0 : i / (double) steps;
            int px = (int) (x1 + (x2 - x1) * t);
            int py = (int) (y1 + (y2 - y1) * t);
            g.fill(px - half, py - half, px + half + 1, py + half + 1, UiRenderer.alphaColor(color));
        }
    }
}
