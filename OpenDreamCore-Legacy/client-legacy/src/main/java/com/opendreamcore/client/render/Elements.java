package com.opendreamcore.client.render;

import com.opendreamcore.client.spi.PlayerInfoSource;
import com.opendreamcore.page.Page;

import java.util.List;
import java.util.Map;

/**
 * 通用装饰与批量元素画笔。
 *
 * 分两半：上半是"每个元素都要过一遍"的通用件（背景/阴影/发光/角标/状态图标/占位），
 * 语义和高版本元素管线一致——先垫阴影发光，再画底，内容最后落笔，角标收口；
 * 下半是屏幕页/世界页缺的元素类型（按钮/进度条/复选框/线条/圆/仪表之类），
 * 全部用 LegacyRenderer 那套原语凑，四版通用。
 *
 * 属性读取规矩沿用 Painters：元素级键优先、类型子段兜底（firstOf），
 * 颜色走 argb，缺省值都往高版本观感靠。
 */
final class Elements {

    private Elements() { }

    //     // 通用装饰
    // 
    /**
     * 元素底：background 段或元素级 color 都认。
     * 支持 圆角(radius)/渐变(gradient 两段或 {from,to})/边框(border+borderColor/borderWidth)，
     * hover/pressed 态换色——hoverColor 缺省按主色提亮一档。
     */
    static void background(LegacyRenderer r, Map<String, Object> p, double x, double y,
                           double w, double h, double alpha, boolean hovered, boolean pressed) {
        Map<String, Object> bg = Painters.mapOf(p.get("background"));
        Object colorRaw = Painters.firstOf(p, bg, "color");
        if (colorRaw == null) {
            colorRaw = p.get("fillColor");
        }
        if (colorRaw == null && bg.isEmpty() && !p.containsKey("radius") && !p.containsKey("border")) {
            return; // 压根没声明底色需求，不硬画
        }
        int color = Painters.argb(colorRaw, 0xFFFFFFFF);
        if (hovered) {
            color = Painters.argb(p.get("hoverColor"), lighten(color));
        }
        if (pressed) {
            color = Painters.mulAlpha(color, 0.75);
        }
        double radius = Painters.num(Painters.firstOf(p, bg, "radius"));
        Object gradRaw = Painters.firstOf(p, bg, "gradient");
        int gradTo = 0;
        if (gradRaw instanceof List && ((List<?>) gradRaw).size() >= 2) {
            gradTo = Painters.argb(((List<?>) gradRaw).get(1), 0);
            color = Painters.argb(((List<?>) gradRaw).get(0), color);
        } else if (gradRaw instanceof Map && !((Map<?, ?>) gradRaw).isEmpty()) {
            Map<?, ?> g = (Map<?, ?>) gradRaw;
            gradTo = Painters.argb(g.get("to"), 0);
            color = Painters.argb(g.get("from"), color);
        }
        int argb = Painters.mulAlpha(color, alpha);
        boolean withBorder = Painters.bool(Painters.firstOf(p, bg, "border")) || p.containsKey("borderColor");
        int borderColor = withBorder ? Painters.argb(p.get("borderColor"), darken(argb)) : 0;
        double bw = withBorder ? Math.max(1, Painters.num(p.get("borderWidth"), 1)) : 0;
        int borderArgb = withBorder ? Painters.mulAlpha(borderColor, alpha) : 0;
        // 现代端 drawRoundedRect 同款：border 色先铺圆角底，再内缩画填充——
        // 圆角描边自然成型（原先圆角底衬配 outlineRect 直角描边，实机残爪）
        if (withBorder && (borderArgb >>> 24) != 0) {
            if (radius > 0) {
                r.fillRounded(x, y, w, h, radius, borderArgb);
            } else {
                r.fillRect(x, y, w, h, borderArgb);
            }
        }
        if (bw * 2 >= w || bw * 2 >= h) {
            return;
        }
        double ix = x + bw;
        double iy = y + bw;
        double iw = w - bw * 2;
        double ih = h - bw * 2;
        double ir = Math.max(0, radius - bw);
        if (radius > 0 && gradTo != 0) {
            r.fillRoundedGradient(ix, iy, iw, ih, ir, argb, Painters.mulAlpha(gradTo, alpha));
        } else if (radius > 0) {
            r.fillRounded(ix, iy, iw, ih, ir, argb);
        } else if (gradTo != 0) {
            r.fillRect(ix, iy, iw, ih / 2, argb);
            r.fillRect(ix, iy + ih / 2, iw, ih - ih / 2, Painters.mulAlpha(gradTo, alpha));
        } else {
            r.fillRect(ix, iy, iw, ih, argb);
        }
    }

    /** 阴影：三层向下偏移的半透明暗影，垫在底前面（高版本同语义）。 */
    static void shadow(LegacyRenderer r, Map<String, Object> p, double x, double y,
                       double w, double h, double alpha) {
        Map<String, Object> sh = Painters.mapOf(p.get("shadow"));
        if (sh.isEmpty() && p.get("shadow") == null) {
            return;
        }
        if (p.get("shadow") instanceof String || p.get("shadow") instanceof List) {
            sh = Painters.mapOf(java.util.Collections.singletonMap("color", p.get("shadow")));
        }
        int color = Painters.argb(sh.get("color"), 0x40000000);
        double offset = Painters.num(sh.get("offset"), Math.max(1, h * 0.06));
        double size = Painters.num(sh.get("size"), 2) + 1;
        double radius = Painters.num(p.get("radius"));
        for (int i = 1; i <= 3; i++) {
            double o = offset * i * 0.6;
            int a = Painters.mulAlpha(color, alpha * (1.0 - i * 0.28));
            if (a >>> 24 == 0) {
                continue;
            }
            if (radius > 0) {
                r.fillRounded(x - size + o * 0.2, y + o, w + size * 2, h, radius, a);
            } else {
                r.fillRect(x - size, y + o, w + size * 2, h, a);
            }
        }
    }

    /** 发光：同心扩散的半透明描边圈。 */
    static void glow(LegacyRenderer r, Map<String, Object> p, double x, double y,
                     double w, double h, double alpha) {
        Map<String, Object> g = Painters.mapOf(p.get("glow"));
        if (g.isEmpty() && !(p.get("glow") instanceof String)) {
            return;
        }
        int color = Painters.argb(p.get("glow") instanceof Map ? g.get("color") : p.get("glow"), 0x33FFD700);
        double size = Painters.num(g.get("size"), 3);
        for (int i = 1; i <= 4; i++) {
            double o = size * i * 0.5;
            int a = Painters.mulAlpha(color, alpha * (1.0 - i * 0.2));
            if (a >>> 24 == 0) {
                continue;
            }
            r.outlineRect(x - o, y - o, w + o * 2, h + o * 2, a);
        }
    }

    /** 右上角标：true 红点 / 数字 / {count, color}。 */
    static void badge(LegacyRenderer r, Object prop, double x, double y,
                      double w, double h, double alpha) {
        if (prop == null) {
            return;
        }
        Map<String, Object> m = Painters.mapOf(prop);
        int color = Painters.argb(m.get("color"), 0xFFE53935);
        String text = "";
        if (prop instanceof Number || prop instanceof String) {
            String s = String.valueOf(prop);
            if (!s.equals("true")) {
                text = s;
            }
        }
        double n = Painters.num(m.get("count"), 0);
        if (n > 0) {
            text = String.valueOf((int) n);
        }
        double cx = x + w;
        double cy = y;
        double rad = text.isEmpty() ? Math.max(2, h * 0.12) : Math.max(4, h * 0.18);
        r.fillCircle(cx, cy, rad, Painters.mulAlpha(color, alpha));
        if (!text.isEmpty()) {
            double tw = r.textWidth(text);
            r.pushPose();
            r.translate(cx - tw / 2, cy - 3, 0);
            r.scale(Math.max(0.5, rad / 5));
            r.drawText(text, 0, 0, Painters.mulAlpha(0xFFFFFFFF, alpha), false);
            r.popPose();
        }
    }

    /** 左上角状态图标：一段文本 + 可选色。 */
    static void statusIcon(LegacyRenderer r, Object prop, double x, double y, double alpha) {
        if (prop == null) {
            return;
        }
        Map<String, Object> m = Painters.mapOf(prop);
        String text = Painters.str(m.get("icon"));
        if (text.isEmpty() && !(prop instanceof Map)) {
            text = String.valueOf(prop);
        }
        if (text.isEmpty()) {
            return;
        }
        int color = Painters.argb(m.get("color"), 0xFF9CCC65);
        r.drawText(text, x, y, Painters.mulAlpha(color, alpha), false);
    }

    /**
     * 未知/画不了的元素：半透明底 + 描边 + 类型名，问题摆在明面上，
     * 不再静默丢——丢了用户只知道"乱"，占位能直接对上是哪个元素哪页缺画笔。
     */
    static void placeholder(LegacyRenderer r, double x, double y, double w, double h,
                            double alpha, String type, String pageId, String id) {
        r.fillRect(x, y, w, h, Painters.mulAlpha(0x33222B3A, alpha));
        r.outlineRect(x, y, w, h, Painters.mulAlpha(0xFF4A6680, alpha));
        String label = type + "?" + (id == null || id.isEmpty() ? "" : ":" + id);
        double tw = r.textWidth(label);
        if (tw > 0 && tw <= Math.max(w, 40) * 2) {
            r.drawText(label, x + 2, y + h / 2 - 4, Painters.mulAlpha(0xFF8FA3BF, alpha), false);
        }
    }

    //     // 批量元素
    // 
    /** 按钮：底（含 hover/press 态）+ 居中标签。 */
    static void button(LegacyRenderer r, Map<String, Object> p, Page page, double x, double y,
                       double w, double h, double alpha, boolean hovered, boolean pressed) {
        Map<String, Object> btn = Painters.mapOf(p.get("button"));
        background(r, mergeColor(p, btn), x, y, w, h, alpha, hovered, pressed);
        String label = Painters.textOf(p);
        if (label.isEmpty()) {
            label = Placeholders.apply(Painters.str(firstOf3(p, btn, "label", "content", "text")), page);
        }
        if (!label.isEmpty()) {
            int color = Painters.argb(Painters.firstOf(p, btn, "textColor"), 0xFFFFFFFF);
            labelCentered(r, label, x, y, w, h, Painters.mulAlpha(color, alpha));
        }
    }

    /** 进度条：底槽 + 按 value/max 填充 + 居中百分比或自定义文本。 */
    static void progress(LegacyRenderer r, Map<String, Object> p, Page page, double x, double y,
                         double w, double h, double alpha) {
        Map<String, Object> pg = Painters.mapOf(p.get("progress"));
        double min = Painters.num(pg.get("min"), 0);
        double max = Math.max(1, Painters.num(pg.get("max"), 100));
        double value = Painters.num(pg.get("value"), 0);
        double ratio = max > min ? clamp01((value - min) / (max - min)) : 0;
        int track = Painters.argb(pg.get("trackColor"), 0xFF22304A);
        int fill = Painters.argb(pg.get("color") != null ? pg.get("color") : pg.get("fillColor"), 0xFFFFD54F);
        double radius = Painters.num(pg.get("radius"), h / 2);
        r.fillRounded(x, y, w, h, radius, Painters.mulAlpha(track, alpha));
        if (ratio > 0) {
            r.fillRounded(x, y, Math.max(h, w * ratio), h, radius, Painters.mulAlpha(fill, alpha));
        }
        String text = Placeholders.apply(Painters.str(pg.get("text")), page);
        String shown = text.isEmpty() ? (int) (ratio * 100) + "%" : text;
        int textColor = Painters.argb(pg.get("textColor"), 0xFFFFFFFF);
        labelCentered(r, shown, x, y, w, h, Painters.mulAlpha(textColor, alpha));
    }

    /** 通用横条（血条/蓝条这类）：track + fill 比例。 */
    static void bar(LegacyRenderer r, Map<String, Object> p, Page page, double x, double y,
                    double w, double h, double alpha) {
        Map<String, Object> b = Painters.mapOf(p.get("bar"));
        double ratio = clamp01(Painters.num(b.get("ratio"),
                Painters.num(b.get("value"), 1) / Math.max(1, Painters.num(b.get("max"), 1))));
        int track = Painters.argb(b.get("trackColor"), 0xB3141B29);
        int fill = Painters.argb(b.get("color") != null ? b.get("color") : b.get("fillColor"), 0xFF66BB6A);
        r.fillRect(x, y, w, h, Painters.mulAlpha(track, alpha));
        r.fillRect(x, y, w * ratio, h, Painters.mulAlpha(fill, alpha));
        r.outlineRect(x, y, w, h, Painters.mulAlpha(0xFF3A4A66, alpha));
    }

    /** 复选框：方框 + 勾（画不了真勾就用内嵌实心方块顶），右侧标签。 */
    static void checkbox(LegacyRenderer r, Map<String, Object> p, Page page, double x, double y,
                         double w, double h, double alpha, boolean hovered) {
        Map<String, Object> cb = Painters.mapOf(p.get("checkbox"));
        boolean checked = Painters.bool(cb.get("value")) || Painters.bool(cb.get("checked"));
        double box = h * 0.85;
        double by = y + (h - box) / 2;
        int border = Painters.argb(cb.get("borderColor"), 0xFF8FA3BF);
        int fill = Painters.argb(cb.get("color"), 0xFFFFD54F);
        r.fillRect(x, by, box, box, Painters.mulAlpha(0xFF141B29, alpha));
        r.outlineRect(x, by, box, box, Painters.mulAlpha(hovered ? lighten(border) : border, alpha));
        if (checked) {
            double inset = box * 0.25;
            r.fillRect(x + inset, by + inset, box - inset * 2, box - inset * 2,
                    Painters.mulAlpha(fill, alpha));
        }
        String label = Placeholders.apply(Painters.str(cb.get("label")), page);
        if (!label.isEmpty()) {
            r.drawText(label, x + box + 5, y + (h - 8) / 2, Painters.mulAlpha(0xFFD7E2F0, alpha), false);
        }
    }

    /** 线段：from/to 两点或 x/y 起 + width 长 height 粗的水平条，两种写法都收。 */
    static void line(LegacyRenderer r, Map<String, Object> p, double x, double y,
                     double w, double h, double alpha) {
        Map<String, Object> ln = Painters.mapOf(p.get("line"));
        int color = Painters.argb(Painters.firstOf(p, ln, "color"), 0xFFFFD54F);
        double width = Math.max(1, Painters.num(ln.get("thickness") != null ? ln.get("thickness") : ln.get("strokeWidth"), 1));
        Map<String, Object> from = Painters.mapOf(ln.get("from"));
        Map<String, Object> to = Painters.mapOf(ln.get("to"));
        double x1 = x + Painters.num(from.get("x"), 0);
        double y1 = y + Painters.num(from.get("y"), 0);
        double x2 = x + Painters.num(to.get("x"), w);
        double y2 = y + Painters.num(to.get("y"), h);
        r.drawLine(x1, y1, x2, y2, width, Painters.mulAlpha(color, alpha));
    }

    /** 圆/环：直径取 w/h 小者；stroke>0 画环不填心。 */
    static void circle(LegacyRenderer r, Map<String, Object> p, double x, double y,
                       double w, double h, double alpha, boolean hovered) {
        Map<String, Object> c = Painters.mapOf(p.get("circle"));
        int color = Painters.argb(Painters.firstOf(p, c, "color"), 0xFFFFD54F);
        double d = Math.min(w > 0 ? w : h, h > 0 ? h : w);
        if (d <= 0) {
            d = 16;
        }
        double cx = x + (w > 0 ? w : d) / 2;
        double cy = y + (h > 0 ? h : d) / 2;
        double stroke = Painters.num(c.get("strokeWidth") != null ? c.get("strokeWidth") : c.get("stroke"), 0);
        if (stroke > 0) {
            double rad = d / 2;
            // 环 = 一圈短线堆出来（原语里没有圆环笔）
            int steps = Math.max(16, (int) (rad * 2));
            for (int i = 0; i < steps; i++) {
                double a0 = Math.PI * 2 * i / steps;
                double a1 = Math.PI * 2 * (i + 1) / steps;
                r.drawLine(cx + Math.cos(a0) * rad, cy + Math.sin(a0) * rad,
                        cx + Math.cos(a1) * rad, cy + Math.sin(a1) * rad,
                        stroke, Painters.mulAlpha(color, alpha));
            }
        } else {
            r.fillCircle(cx, cy, d / 2,
                    Painters.mulAlpha(hovered ? lighten(color) : color, alpha));
        }
    }

    /** 元素级渐变填充（type 就叫 gradient 的那种）。 */
    static void gradient(LegacyRenderer r, Map<String, Object> p, double x, double y,
                         double w, double h, double alpha) {
        Map<String, Object> g = Painters.mapOf(p.get("gradient"));
        int from = Painters.argb(g.get("from"), Painters.argb(g.get("color"), 0xFF42A5F5));
        int to = Painters.argb(g.get("to"), 0xFF1A237E);
        boolean horizontal = String.valueOf(g.get("direction")).contains("left")
                || String.valueOf(g.get("direction")).contains("right");
        if (horizontal) {
            int bands = 24;
            for (int i = 0; i < bands; i++) {
                r.fillRect(x + w * i / bands, y, w / bands + 1, h,
                        Painters.mulAlpha(lerpColor(from, to, i / (double) (bands - 1)), alpha));
            }
        } else {
            r.fillRoundedGradient(x, y, w, h, Painters.num(g.get("radius")), 
                    Painters.mulAlpha(from, alpha), Painters.mulAlpha(to, alpha));
        }
    }

    /**
     * 仪表盘：半圆弧刻度 + 指针。
     * 弧用一圈短线堆（和圆环一个路子），指针按 value 摆角度。
     */
    static void gauge(LegacyRenderer r, Map<String, Object> p, Page page, double x, double y,
                      double w, double h, double alpha) {
        Map<String, Object> g = Painters.mapOf(p.get("gauge"));
        double value = Painters.num(g.get("value"), 0);
        double max = Math.max(1, Painters.num(g.get("max"), 100));
        double rad = Math.min(w, h * 1.6) / 2 * 0.9;
        double cx = x + w / 2;
        double cy = y + h * 0.95;
        int track = Painters.argb(g.get("trackColor"), 0xFF22304A);
        int fill = Painters.argb(g.get("color"), 0xFFFFD54F);
        int steps = 36;
        double sweep = Math.PI; // 半圆
        for (int i = 0; i < steps; i++) {
            double t = i / (double) steps;
            double a0 = Math.PI + sweep * t;
            double a1 = Math.PI + sweep * (i + 1) / steps;
            int col = t <= value / max ? fill : track;
            r.drawLine(cx + Math.cos(a0) * rad, cy + Math.sin(a0) * rad,
                    cx + Math.cos(a1) * rad, cy + Math.sin(a1) * rad,
                    Math.max(1.5, h * 0.08), Painters.mulAlpha(col, alpha));
        }
        double na = Math.PI + sweep * clamp01(value / max);
        r.drawLine(cx, cy, cx + Math.cos(na) * rad * 0.8, cy + Math.sin(na) * rad * 0.8,
                Math.max(1, h * 0.05), Painters.mulAlpha(0xFFF2F5FA, alpha));
        String text = Placeholders.apply(Painters.str(g.get("text")), page);
        if (text.isEmpty()) {
            text = String.valueOf((long) value);
        }
        labelCentered(r, text, x, y + h * 0.35, w, h * 0.3, Painters.mulAlpha(0xFFFFFFFF, alpha));
    }

    /** 下拉框（收拢态外观）：底 + 当前值 + 右侧小三角。 */
    static void dropdown(LegacyRenderer r, Map<String, Object> p, Page page, double x, double y,
                         double w, double h, double alpha, boolean hovered) {
        Map<String, Object> d = Painters.mapOf(p.get("dropdown"));
        background(r, mergeColor(p, d), x, y, w, h, alpha, hovered, false);
        Object opts = d.get("options");
        String current = Painters.str(d.get("value"));
        if (current.isEmpty() && opts instanceof List && !((List<?>) opts).isEmpty()) {
            current = String.valueOf(((List<?>) opts).get(0));
        }
        r.drawText(current, x + 4, y + (h - 8) / 2,
                Painters.mulAlpha(Painters.argb(d.get("textColor"), 0xFFD7E2F0), alpha), false);
        double tx = x + w - 10;
        double ty = y + h / 2;
        r.fillTriangle(tx - 3, ty - 1.5, tx + 3, ty - 1.5, tx, ty + 2.5,
                Painters.mulAlpha(0xFF8FA3BF, alpha));
    }

    /** 输入框（静态外观 + 光标闪烁）：真实键入在批2接键盘钩子。 */
    static void input(LegacyRenderer r, Map<String, Object> p, Page page, double x, double y,
                      double w, double h, double alpha, boolean hovered, boolean focused) {
        Map<String, Object> in = Painters.mapOf(p.get("input"));
        background(r, mergeColor(p, in), x, y, w, h, alpha, hovered, false);
        String text = Placeholders.apply(Painters.str(firstOf3(p, in, "value", "text", "placeholder")), page);
        int color = Painters.argb(in.get("textColor"), text.equals(Painters.str(in.get("placeholder")))
                && !Painters.bool(in.get("filled")) ? 0xFF6B7C93 : 0xFFD7E2F0);
        r.drawText(text, x + 4, y + (h - 8) / 2, Painters.mulAlpha(color, alpha), false);
        if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
            r.fillRect(x + 4 + r.textWidth(text), y + 3, 1, h - 6,
                    Painters.mulAlpha(0xFFFFD54F, alpha));
        }
    }

    /** 聊天框：最近 N 行文本，新的在下。 */
    static void chat(LegacyRenderer r, Map<String, Object> p, Page page, double x, double y,
                     double w, double h, double alpha) {
        Map<String, Object> c = Painters.mapOf(p.get("chat_display"));
        r.fillRect(x, y, w, h, Painters.mulAlpha(0x66141B29, alpha));
        Object lines = c.get("lines");
        if (!(lines instanceof List)) {
            lines = c.get("content");
        }
        int color = Painters.argb(c.get("textColor"), 0xFFD7E2F0);
        double lh = Math.max(8, Painters.num(c.get("lineHeight"), 10));
        if (lines instanceof List) {
            List<?> ls = (List<?>) lines;
            int maxLines = (int) Math.max(1, h / lh);
            int start = Math.max(0, ls.size() - maxLines);
            double ty = y + h - (ls.size() - start) * lh;
            for (int i = start; i < ls.size(); i++) {
                if (ty < y) {
                    break;
                }
                r.drawText(Placeholders.apply(String.valueOf(ls.get(i)), page), x + 3, ty,
                        Painters.mulAlpha(color, alpha), false);
                ty += lh;
            }
        }
        r.outlineRect(x, y, w, h, Painters.mulAlpha(0xFF3A4A66, alpha));
    }

    /** Boss 血条：名 + 槽 + 填充。 */
    static void bossBar(LegacyRenderer r, Map<String, Object> p, Page page, double x, double y,
                        double w, double h, double alpha) {
        Map<String, Object> b = Painters.mapOf(p.get("boss_bar"));
        String name = Placeholders.apply(Painters.str(b.get("name")), page);
        if (!name.isEmpty()) {
            labelCentered(r, name, x, y - 12, w, 10, Painters.mulAlpha(0xFFFFFFFF, alpha));
        }
        double ratio = clamp01(Painters.num(b.get("value"), 1) / Math.max(1, Painters.num(b.get("max"), 1)));
        int fill = Painters.argb(b.get("color"), 0xFFAB47BC);
        r.fillRect(x, y, w, h, Painters.mulAlpha(0xB3141B29, alpha));
        r.fillRect(x, y, w * ratio, h, Painters.mulAlpha(fill, alpha));
        r.outlineRect(x, y, w, h, Painters.mulAlpha(0xFFE0B0FF, alpha));
    }

    /** 罗盘：盘面 + 正北标 + 随视角摆的指针（数据走 PlayerInfoSource 口子）。 */
    static void compass(LegacyRenderer r, Map<String, Object> p, double x, double y,
                        double w, double h, double alpha) {
        Map<String, Object> c = Painters.mapOf(p.get("compass"));
        double d = Math.min(w, h);
        double cx = x + w / 2;
        double cy = y + h / 2;
        r.fillCircle(cx, cy, d / 2, Painters.mulAlpha(Painters.argb(c.get("color"), 0xCC141B29), alpha));
        int steps = Math.max(24, (int) d);
        for (int i = 0; i < steps; i++) {
            double a0 = Math.PI * 2 * i / steps;
            double a1 = Math.PI * 2 * (i + 1) / steps;
            r.drawLine(cx + Math.cos(a0) * d / 2, cy + Math.sin(a0) * d / 2,
                    cx + Math.cos(a1) * d / 2, cy + Math.sin(a1) * d / 2,
                    1, Painters.mulAlpha(0xFF8FA3BF, alpha));
        }
        double yaw = Math.toRadians(PlayerInfoSource.Host.current().yaw());
        double nx = cx + Math.sin(-yaw) * d * 0.36;
        double ny = cy - Math.cos(-yaw) * d * 0.36;
        r.drawLine(cx, cy, nx, ny, Math.max(1.5, d * 0.05), Painters.mulAlpha(0xFFE53935, alpha));
        r.drawText("N", cx - 2, y + 1, Painters.mulAlpha(0xFFFFFFFF, alpha), false);
    }

    /** 方位字：按玩家朝向换算 东南西北。 */
    static void direction(LegacyRenderer r, Map<String, Object> p, double x, double y,
                          double w, double h, double alpha) {
        Map<String, Object> d = Painters.mapOf(p.get("direction"));
        double yaw = normalizeYaw(PlayerInfoSource.Host.current().yaw());
        String[] names = {"南", "西", "北", "东"};
        String shown = names[(int) Math.floor(((yaw + 45) % 360) / 90)];
        if (Painters.bool(d.get("degrees"))) {
            shown = (int) yaw + "° " + shown;
        }
        int color = Painters.argb(d.get("color"), 0xFFFFFFFF);
        labelCentered(r, shown, x, y, w, h, Painters.mulAlpha(color, alpha));
    }

    /**
     * 画布笔刷：canvas 段的 brushes 指令序列（line/circle/triangle/gradient/text/image）。
     * 坐标是画布本地系（外层已 translate 到元素原点），x/y/cx/cy/x1.. 直接当本地坐标用。
     */
    static void drawBrushes(LegacyRenderer r, Map<String, Object> p, Page page,
                            double w, double h, double alpha) {
        Map<String, Object> spec = Painters.mapOf(p.get("canvas"));
        Object bgRaw = spec.get("background");
        if (bgRaw != null) {
            int bg = Painters.argb(bgRaw, 0);
            if ((bg >>> 24) != 0) {
                r.fillRect(0, 0, w, h, Painters.mulAlpha(bg, alpha));
            }
        }
        Object brushes = spec.get("brushes");
        if (!(brushes instanceof List)) {
            return;
        }
        for (Object o : (List<?>) brushes) {
            if (!(o instanceof Map)) {
                continue;
            }
            Map<String, Object> m = Painters.mapOf(o);
            String bt = Painters.str(m.get("type"));
            int color = Painters.argb(m.get("color"), 0xFFFFFFFF);
            if ("line".equals(bt)) {
                r.drawLine(Painters.num(m.get("x1")), Painters.num(m.get("y1")),
                        Painters.num(m.get("x2")), Painters.num(m.get("y2")),
                        Math.max(1, Painters.num(m.get("width"), 1)),
                        Painters.mulAlpha(color, alpha));
            } else if ("circle".equals(bt)) {
                double rad = Painters.num(m.get("radius"), 10);
                if (Painters.bool(m.get("fill"))) {
                    r.fillCircle(Painters.num(m.get("cx")), Painters.num(m.get("cy")), rad,
                            Painters.mulAlpha(color, alpha));
                } else {
                    ring(r, Painters.num(m.get("cx")), Painters.num(m.get("cy")), rad,
                            Math.max(1, Painters.num(m.get("width"), 1)),
                            Painters.mulAlpha(color, alpha));
                }
            } else if ("triangle".equals(bt)) {
                r.fillTriangle(Painters.num(m.get("x1")), Painters.num(m.get("y1")),
                        Painters.num(m.get("x2")), Painters.num(m.get("y2")),
                        Painters.num(m.get("x3")), Painters.num(m.get("y3")),
                        Painters.mulAlpha(color, alpha));
            } else if ("gradient".equals(bt)) {
                int from = Painters.argb(m.get("from"), 0xFFFFFFFF);
                int to = Painters.argb(m.get("to"), 0xFF000000);
                boolean vertical = !"false".equals(String.valueOf(m.get("vertical")));
                double bx = Painters.num(m.get("x"));
                double by = Painters.num(m.get("y"));
                double bw = Painters.num(m.get("width"), 10);
                double bh = Painters.num(m.get("height"), 10);
                int bands = (int) Math.max(2, vertical ? bh : bw);
                for (int i = 0; i < bands; i++) {
                    double t01 = i / (double) (bands - 1);
                    int c = lerpColor(from, to, t01);
                    if (vertical) {
                        r.fillRect(bx, by + i, bw, 1, Painters.mulAlpha(c, alpha));
                    } else {
                        r.fillRect(bx + i, by, 1, bh, Painters.mulAlpha(c, alpha));
                    }
                }
            } else if ("text".equals(bt)) {
                String content = Placeholders.apply(Painters.str(m.get("content")), page);
                if (!content.isEmpty()) {
                    r.drawText(content, Painters.num(m.get("x")), Painters.num(m.get("y")),
                            Painters.mulAlpha(color, alpha), Painters.bool(m.get("shadow")));
                }
            } else if ("rect".equals(bt) || "rectbrush".equals(bt)) {
                r.fillRect(Painters.num(m.get("x")), Painters.num(m.get("y")),
                        Painters.num(m.get("width"), 10), Painters.num(m.get("height"), 10),
                        Painters.mulAlpha(color, alpha));
            }
        }
    }

    /** 细线圆环：短线段拼一圈（无扇形描边笔时的通用解）。 */
    static void ring(LegacyRenderer r, double cx, double cy, double rad, double width, int argb) {
        int steps = Math.max(16, (int) (rad * 2));
        for (int i = 0; i < steps; i++) {
            double a0 = Math.PI * 2 * i / steps;
            double a1 = Math.PI * 2 * (i + 1) / steps;
            r.drawLine(cx + Math.cos(a0) * rad, cy + Math.sin(a0) * rad,
                    cx + Math.cos(a1) * rad, cy + Math.sin(a1) * rad, width, argb);
        }
    }

    /** 文本标签：水平/垂直居中，字号走 scale 段（drawText 前乘变换）。 */
    static void labelCentered(LegacyRenderer r, String text, double x, double y,
                              double w, double h, int argb) {
        if (text == null || text.isEmpty()) {
            return;
        }
        double tw = r.textWidth(text);
        r.drawText(text, x + (w - tw) / 2, y + (h - 8) / 2, argb, false);
    }

    //     // 小工具
    // 
    private static Object firstOf3(Map<String, Object> top, Map<String, Object> sub,
                                   String a, String b, String c) {
        Object v = Painters.firstOf(top, sub, a);
        if (v == null) {
            v = Painters.firstOf(top, sub, b);
        }
        if (v == null) {
            v = Painters.firstOf(top, sub, c);
        }
        return v;
    }

    /** 类型子段的 color/background 顶到元素级一份，background() 两套写法都能吃到。 */
    private static Map<String, Object> mergeColor(Map<String, Object> p, Map<String, Object> sub) {
        if (p.containsKey("color") || p.containsKey("background")) {
            return p;
        }
        Object c = sub.get("color");
        if (c == null) {
            // button:{background:"#3A7D44"} 这套写法：子段里的 background 就是底色字符串
            Object bg = sub.get("background");
            if (bg instanceof String || bg instanceof Number) {
                c = bg;
            }
        }
        if (c == null) {
            return p;
        }
        java.util.Map<String, Object> copy = new java.util.LinkedHashMap<>(p);
        copy.put("color", c);
        Object radius = sub.get("radius");
        if (radius != null) {
            copy.put("radius", radius);
        }
        Object hover = sub.get("hoverColor");
        if (hover != null) {
            copy.put("hoverColor", hover);
        }
        return copy;
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    private static double normalizeYaw(double yaw) {
        double y = yaw % 360;
        return y < 0 ? y + 360 : y;
    }

    private static int lighten(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r8 = Math.min(255, ((argb >> 16) & 0xFF) + 32);
        int g8 = Math.min(255, ((argb >> 8) & 0xFF) + 32);
        int b8 = Math.min(255, (argb & 0xFF) + 32);
        return a << 24 | r8 << 16 | g8 << 8 | b8;
    }

    private static int darken(int argb) {
        int a = (argb >>> 24) & 0xFF;
        return a << 24 | (((argb >> 16) & 0xFF) * 3 / 4) << 16
                | (((argb >> 8) & 0xFF) * 3 / 4) << 8 | ((argb & 0xFF) * 3 / 4);
    }

    /** 通道级线性插值（渐变分段用）。 */
    private static int lerpColor(int from, int to, double t) {
        int a = (int) (((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * t);
        int rr = (int) (((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int g = (int) (((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (a & 0xFF) << 24 | (rr & 0xFF) << 16 | (g & 0xFF) << 8 | (b & 0xFF);
    }
}
