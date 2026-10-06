package com.opendreamcore.client.camera;

import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * M1.9 L3：2D 曲线面板（编辑会话激活时用）——26.1.2 壳。
 *
 * <p>26.1.2 起屏幕改走渲染状态抽取：{@code Renderable.extractRenderState(GuiGraphicsExtractor,...)}
 * 取代 {@code render(GuiGraphics,...)}；绘制原语 fill/outline/text(Font,Component,x,y,color) 语义对齐。
 * 鼠标同 1.21.11：MouseButtonEvent 记录参数。逻辑与另外两份壳一致，刻意不用原版 widget。
 */
public final class CameraCurveScreen extends Screen {
    private static final int BTN_H = 14;

    private int px;
    private int py;
    private int pw;
    private int ph;
    private int axisX0;
    private int axisX1;
    private int axisY;
    private int dragDot = -1;

    public CameraCurveScreen() {
        super(Component.literal("运镜曲线"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        CameraEditSession s = CameraEditSession.get();
        pw = Math.min(460, this.width - 30);
        ph = 172;
        px = (this.width - pw) / 2;
        py = Math.max(10, (this.height - ph) / 2 - 10);
        axisX0 = px + 16;
        axisX1 = px + pw - 16;
        axisY = py + 46;
    }

    // ---------------------------------------------------------------- 时间轴坐标

    private double tMax() {
        CameraEditSession s = CameraEditSession.get();
        double m = s.durationValue() > 0 ? s.durationValue() : 1;
        if (s.durationValue() <= 0) {
            m = Math.max(m, s.pointsCount() - 1);
        }
        for (Double t : s.timesView()) {
            if (t != null && t > m) {
                m = t;
            }
        }
        return m;
    }

    private double xToT(double x) {
        return (x - axisX0) / Math.max(1, axisX1 - axisX0) * tMax();
    }

    private int tToX(double t) {
        return (int) Math.round(axisX0 + (axisX1 - axisX0) * (t / tMax()));
    }

    /** 第 i 点的显示时间（显式值，未设则自动均分）。 */
    private double tOf(CameraEditSession s, int i) {
        Double t = i < s.timesView().size() ? s.timesView().get(i) : null;
        if (t != null) {
            return t;
        }
        int n = s.pointsCount();
        return n <= 1 ? 0 : (s.durationValue() > 0 ? s.durationValue() * i / (n - 1) : i);
    }

    // ---------------------------------------------------------------- 渲染

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        CameraEditSession s = CameraEditSession.get();
        g.fill(px, py, px + pw, py + ph, 0xE0101018);
        g.outline(px, py, px + pw, py + ph, 0xFF3A6EA5);
        text(g, "运镜曲线：" + s.editedId(), px + 10, py + 8, 0xFFFFFFD0);
        text(g, "左键拖点改时间 · 右键改缓动 · Esc 关闭", px + 10, py + 22, 0xFF8A8A9A);

        // 时间轴
        g.fill(axisX0, axisY, axisX1, axisY + 1, 0xFF555566);
        int n = s.pointsCount();
        String hover = null;
        for (int i = 0; i < n; i++) {
            int x = tToX(tOf(s, i));
            boolean hot = Math.abs(mx - x) <= 6 && Math.abs(my - axisY) <= 9;
            int color = i == dragDot ? 0xFFFFF060 : hot ? 0xFFFFFFFF : 0xFF7FD0FF;
            g.fill(x - 3, axisY - 3, x + 3, axisY + 4, color);
            String ease = i < s.easesView().size() ? s.easesView().get(i) : null;
            String tag = ease == null ? "" : ease.equals("held") ? "定" : ease.equals("linear") ? "直" : "滑";
            if (hot || i == dragDot) {
                hover = "点 " + (i + 1) + " · t=" + trim(tOf(s, i)) + "s · 缓动=" + (ease == null ? "跟随" : ease);
            }
            if (n <= 14 || hot || i == dragDot) {
                text(g, (i + 1) + tag, x - 8, axisY + 10, hot ? 0xFFFFFFFF : 0xFF9A9AAA);
            }
        }
        if (hover != null) {
            text(g, hover, px + 10, py + ph - 12, 0xFFFFF0A0);
        }

        int y1 = py + 78;
        button(g, "时长 -0.5s", px + 10, y1, 78, mx, my);
        text(g, trim(s.durationValue()) + "s" + (s.durationValue() <= 0 ? "(自动)" : ""),
                px + 96, y1 + 3, 0xFFB0E0B0);
        button(g, "时长 +0.5s", px + 160, y1, 78, mx, my);
        button(g, "循环: " + (s.loopEnabled() ? "开" : "关"), px + 250, y1, 78, mx, my);
        button(g, "插值: " + (s.smoothInterp() ? "平滑" : "直线"), px + 340, y1, 78, mx, my);

        int y2 = py + 100;
        button(g, s.previewing() ? "预览:开" : "预览", px + 10, y2, 52, mx, my);
        button(g, "导出", px + 70, y2, 52, mx, my);
        button(g, "导入", px + 130, y2, 52, mx, my);
        button(g, "保存", px + 190, y2, 52, mx, my);
        button(g, "关闭", px + 250, y2, 52, mx, my);
    }

    private boolean button(GuiGraphicsExtractor g, String label, int x, int y, int w, int mx, int my) {
        boolean hot = mx >= x && mx <= x + w && my >= y && my <= y + BTN_H;
        g.fill(x, y, x + w, y + BTN_H, hot ? 0xFF2E4A6B : 0xFF22222E);
        g.outline(x, y, x + w, y + BTN_H, hot ? 0xFF7FB4E8 : 0xFF444455);
        text(g, label, x + 5, y + 3, hot ? 0xFFFFFFFF : 0xFFC8C8D8);
        return hot;
    }

    private void text(GuiGraphicsExtractor g, String s, int x, int y, int color) {
        g.text(this.font, Component.literal(s), x, y, color);
    }

    private static String trim(double v) {
        double r = Math.round(v * 10.0) / 10.0;
        return r == Math.floor(r) ? String.valueOf((long) r) : String.valueOf(r);
    }

    // ---------------------------------------------------------------- 交互

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent e, boolean dbl) {
        return pressAt(e.x(), e.y(), e.button()) || super.mouseClicked(e, dbl);
    }

    /** 按下：时间轴圆点（左键拖拽/右键缓动）→ 按钮 → 父类。 */
    private boolean pressAt(double mx, double my, int button) {
        CameraEditSession s = CameraEditSession.get();
        if (Math.abs(my - axisY) <= 9) {
            int dot = dotAt(s, mx);
            if (dot >= 0) {
                if (button == 1) {
                    s.cycleEase(dot);
                    s.rearmPreview();
                } else if (button == 0) {
                    s.pushUndo();
                    dragDot = dot;
                }
                return true;
            }
        }
        return clickButtons(s, mx, my);
    }

    /** 时间轴上离鼠标最近的圆点（半径 7px 内）；无命中返回 -1。 */
    private int dotAt(CameraEditSession s, double mx) {
        int best = -1;
        double bestD = 7;
        for (int i = 0; i < s.pointsCount(); i++) {
            double d = Math.abs(mx - tToX(tOf(s, i)));
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    /** 按钮两行（时长/循环/插值 + 预览/导出/导入/保存/关闭）；命中即处理并返回 true。 */
    private boolean clickButtons(CameraEditSession s, double mx, double my) {
        if (click(mx, my, px + 10, py + 78, 78)) {
            s.setDuration(s.durationValue() - 0.5);
            s.rearmPreview();
            return true;
        }
        if (click(mx, my, px + 160, py + 78, 78)) {
            s.setDuration(s.durationValue() + 0.5);
            s.rearmPreview();
            return true;
        }
        if (click(mx, my, px + 250, py + 78, 78)) {
            s.setLoop(!s.loopEnabled());
            s.rearmPreview();
            return true;
        }
        if (click(mx, my, px + 340, py + 78, 78)) {
            s.setSmooth(!s.smoothInterp());
            s.rearmPreview();
            return true;
        }
        if (click(mx, my, px + 10, py + 100, 52)) {
            s.togglePreview();
            return true;
        }
        if (click(mx, my, px + 70, py + 100, 52)) {
            String yaml = s.exportYaml();
            if (yaml != null && this.minecraft != null) {
                this.minecraft.keyboardHandler.setClipboard(yaml);
            }
            return true;
        }
        if (click(mx, my, px + 130, py + 100, 52)) {
            if (this.minecraft != null) {
                s.importYaml(this.minecraft.keyboardHandler.getClipboard());
            }
            return true;
        }
        if (click(mx, my, px + 190, py + 100, 52)) {
            s.save();
            return true;
        }
        if (click(mx, my, px + 250, py + 100, 52)) {
            onClose();
            return true;
        }
        return false;
    }

    private boolean click(double mx, double my, int x, int y, int w) {
        return mx >= x && mx <= x + w && my >= y && my <= y + BTN_H;
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent e, double dx, double dy) {
        if (dragDot >= 0) {
            CameraEditSession s = CameraEditSession.get();
            s.dragKeyframeTime(dragDot, xToT(e.x()));
            s.rearmPreview();
            return true;
        }
        return super.mouseDragged(e, dx, dy);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent e) {
        dragDot = -1;
        return super.mouseReleased(e);
    }
}
