package com.opendreamcore.client.render;

import com.opendreamcore.page.Page;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 两路渲染（屏幕页/世界页）共用的属性解析和小画笔。
 * 全是静态工具，不含状态；画笔实例从参数传进来。
 */
final class Painters {

    private Painters() { }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> mapOf(Object v) {
        return v instanceof Map ? (Map<String, Object>) v : java.util.Collections.emptyMap();
    }

    /** 元素级和子段里同名的键，元素级优先（rect: {color} 和顶层 color 都认）。 */
    static Object firstOf(Map<String, Object> top, Map<String, Object> sub, String key) {
        Object v = top.get(key);
        return v != null ? v : sub.get(key);
    }

    static String textOf(Map<String, Object> p) {
        Map<String, Object> text = mapOf(p.get("text"));
        Object content = text.containsKey("content") ? text.get("content") : p.get("content");
        return content == null ? "" : String.valueOf(content);
    }

    static boolean visible(Map<String, Object> p) {
        Object visible = p.get("visible");
        return visible == null || Boolean.parseBoolean(String.valueOf(visible));
    }

    static String normType(String type) {
        return type == null ? "" : type.toLowerCase(Locale.ROOT).replace("_", "");
    }

    public static double num(Object v) {
        return v instanceof Number ? ((Number) v).doubleValue() : 0;
    }

    public static double num(Object v, double fallback) {
        if (v instanceof Number) {
            return ((Number) v).doubleValue();
        }
        if (v != null) {
            try {
                return Double.parseDouble(String.valueOf(v));
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    public static String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    /** 宽松布尔：true/是/on/1 都算开，其他全关。 */
    public static boolean bool(Object v) {
        if (v == null) {
            return false;
        }
        String s = String.valueOf(v).trim().toLowerCase(java.util.Locale.ROOT);
        return s.equals("true") || s.equals("1") || s.equals("yes") || s.equals("on");
    }

    /**
     * 颜色解析：#RRGGBB(AA) / 0xAARRGGBB / RRGGBB / solid:R,G,B,A；
     * 缺省不透明。
     */
    static int argb(Object v, int fallback) {
        if (v == null) {
            return fallback;
        }
        String s = String.valueOf(v).trim();
        if (s.startsWith("solid:")) {
            String[] parts = s.substring(6).split(",");
            if (parts.length >= 3) {
                int rr = (int) Double.parseDouble(parts[0].trim());
                int gg = (int) Double.parseDouble(parts[1].trim());
                int bb = (int) Double.parseDouble(parts[2].trim());
                int aa = parts.length >= 4 ? (int) Double.parseDouble(parts[3].trim()) : 255;
                return clampByte(aa) << 24 | clampByte(rr) << 16 | clampByte(gg) << 8 | clampByte(bb);
            }
            return fallback;
        }
        try {
            if (s.startsWith("#")) {
                s = s.substring(1);
            } else if (s.toLowerCase(Locale.ROOT).startsWith("0x")) {
                s = s.substring(2);
            }
            long parsed = Long.parseLong(s, 16);
            if (s.length() == 6) {
                parsed |= 0xFF000000L;
            }
            return (int) parsed;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int clampByte(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    /** 动画/淡入统一走 alpha 乘算：ARGB 高 8 位按倍率缩，颜色不动。 */
    static int mulAlpha(int argb, double mul) {
        int a = (int) (((argb >>> 24) & 0xFF) * mul);
        if (a > 255) {
            a = 255;
        }
        if (a < 0) {
            a = 0;
        }
        return (a << 24) | (argb & 0xFFFFFF);
    }

    //     // 元素形状（屏幕/世界两路共用，坐标系由外层变换决定）
    // 
    static void rect(LegacyRenderer r, Map<String, Object> p, double x, double y, double w, double h, double alpha) {
        Map<String, Object> rect = mapOf(p.get("rect"));
        int color = argb(firstOf(p, rect, "color"), 0xFFFFFFFF);
        int gradient = argb(rect.get("gradient"), 0);
        double radius = Math.max(0, num(p.get("radius"), 0));
        boolean border = Boolean.parseBoolean(String.valueOf(p.getOrDefault("border", "false")));
        double bw = border ? 1 : 0;
        // 现代端同款 border 铺底内缩（带 radius 时描边也是圆的）
        int borderArgb = mulAlpha(color, alpha);
        if (border) {
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
        if (gradient != 0) {
            if (radius > 0) {
                r.fillRoundedGradient(ix, iy, iw, ih, ir(radius, bw), mulAlpha(color, alpha), mulAlpha(gradient, alpha));
            } else {
                double half = ih / 2.0;
                r.fillRect(ix, iy, iw, half, mulAlpha(color, alpha));
                r.fillRect(ix, iy + half, iw, ih - half, mulAlpha(gradient, alpha));
            }
        } else if (radius > 0) {
            r.fillRounded(ix, iy, iw, ih, ir(radius, bw), mulAlpha(color, alpha));
        } else {
            r.fillRect(ix, iy, iw, ih, mulAlpha(color, alpha));
        }
    }

    /** 内缩后的圆角半径：外半径减描边宽，不为负。 */
    private static double ir(double radius, double bw) {
        return Math.max(0, radius - bw);
    }

    /** 页签条：等分格子，激活格高亮。页签切换的点击交互后续接鼠标事件再补。 */
    static void tabs(LegacyRenderer r, Map<String, Object> p, double x, double y, double w, double h,
                     double alpha, double fontUnit, Page page) {
        Map<String, Object> tabs = mapOf(p.get("tabs"));
        Object optionsRaw = tabs.get("options");
        if (!(optionsRaw instanceof List<?>) || ((List<?>) optionsRaw).isEmpty()) {
            return;
        }
        List<String> options = new ArrayList<>();
        for (Object o : (List<?>) optionsRaw) {
            options.add(Placeholders.apply(String.valueOf(o), page));
        }
        String active = WorldPanels.activeTabOf(options, tabs.get("active"));
        int idle = argb(tabs.get("color"), 0xFF22304A);
        int activeBg = argb(tabs.get("activeColor"), 0xFFFFD54F);
        int textIdle = argb(tabs.get("textColor"), 0xFF8FA3BF);
        int textActive = argb(tabs.get("textActiveColor"), 0xFF1A1206);
        int gap = (int) Math.max(1, h * 0.15);
        double cellW = (w - gap * (options.size() - 1)) / options.size();
        for (int i = 0; i < options.size(); i++) {
            double cx = x + i * (cellW + gap);
            boolean isActive = options.get(i).equals(active);
            r.fillRect(cx, y, cellW, h, mulAlpha(isActive ? activeBg : idle, alpha));
            String label = options.get(i);
            double lw = r.textWidth(label) * fontUnit;
            if (lw > 0 && lw < cellW) {
                r.pushPose();
                r.translate(cx + cellW / 2, y + h / 2, 0);
                r.scale(fontUnit);
                r.drawText(label, -r.textWidth(label) / 2.0, -4.0,
                        mulAlpha(isActive ? textActive : textIdle, alpha), false);
                r.popPose();
            }
        }
    }

    static void toggle(LegacyRenderer r, Map<String, Object> p, double x, double y, double w, double h,
                       double alpha, double fontUnit) {
        Map<String, Object> toggle = mapOf(p.get("toggle"));
        boolean value = Boolean.parseBoolean(String.valueOf(toggle.get("value")));
        int color = argb(toggle.get("color"), 0xFFFFD54F);
        // 轨道占整格高的六成，开关圆头是个小方块（画笔只有矩形，圆用方凑合）
        double trackH = h * 0.6;
        double trackY = y + (h - trackH) / 2;
        r.fillRect(x, trackY, w, trackH, mulAlpha(value ? color : 0xFF39435A, alpha));
        double knob = trackH;
        double knobX = value ? x + w - knob : x;
        r.fillRect(knobX, trackY, knob, trackH, mulAlpha(0xFFF2F5FA, alpha));
        String label = Placeholders.apply(str(toggle.get("label")), null);
        if (!label.isEmpty() && fontUnit > 0) {
            r.pushPose();
            r.translate(x, y + h + h * 0.4, 0);
            r.scale(fontUnit);
            r.drawText(label, 0, 0, mulAlpha(0xFFB8C4D8, alpha), false);
            r.popPose();
        }
    }

    static void slider(LegacyRenderer r, Map<String, Object> p, double x, double y, double w, double h,
                       double alpha, double fontUnit) {
        Map<String, Object> slider = mapOf(p.get("slider"));
        double min = num(slider.get("min"), 0);
        double max = num(slider.get("max"), 100);
        double value = num(slider.get("value"), min);
        int color = argb(slider.get("color"), 0xFF4FC3F7);
        double ratio = max > min ? Math.min(1, Math.max(0, (value - min) / (max - min))) : 0;
        double trackH = h * 0.5;
        double trackY = y + (h - trackH) / 2;
        r.fillRect(x, trackY, w, trackH, mulAlpha(0xFF2A3448, alpha));
        r.fillRect(x, trackY, w * ratio, trackH, mulAlpha(color, alpha));
        double knob = trackH * 1.4;
        double knobX = x + w * ratio - knob / 2;
        r.fillRect(knobX, trackY - (knob - trackH) / 2, knob, knob, mulAlpha(0xFFF2F5FA, alpha));
    }

    static void itemSlot(LegacyRenderer r, Map<String, Object> p, double x, double y, double w, double h, double alpha) {
        Map<String, Object> slot = mapOf(p.get("item_slot"));
        if (slot.isEmpty()) {
            slot = mapOf(p.get("hot_slot"));
        }
        String item = str(slot.get("item"));
        int count = (int) num(slot.get("count"), 1);
        // 槽底 + 描边：物品真身交给 ItemPainter，画不了再回退到名字标注
        r.fillRect(x, y, w, h, mulAlpha(0xB3141B29, alpha));
        r.outlineRect(x, y, w, h, mulAlpha(0xFF3A4A66, alpha));
        if (!item.isEmpty()
                && com.opendreamcore.client.spi.ItemPainter.Host.current().render(
                        item, x, y, Math.min(w, h), alpha)) {
            // ItemIcon 规则命中的物品：原版图标之上再盖一层自定义皮肤
            paintSkin(r, item, x, y, Math.min(w, h), alpha);
            return; // 真身上屏（含数量角标），名字标注不再叠
        }
        if (count > 1) {
            String tag = "x" + count;
            double tagW = r.textWidth(tag) * 1.4;
            r.pushPose();
            r.translate(x + w - tagW - 2, y + h - 12, 0);
            r.scale(1.4);
            r.drawText(tag, 0, 0, mulAlpha(0xFFFFE082, alpha), true);
            r.popPose();
        }
        if (!item.isEmpty()) {
            String shortName = item.contains(":") ? item.substring(item.indexOf(':') + 1) : item;
            r.pushPose();
            r.translate(x + 2, y + h + 2, 0);
            r.scale(0.9);
            r.drawText(shortName, 0, 0, mulAlpha(0xFF8FA3BF, alpha), false);
            r.popPose();
        }
    }

    /**
     * ItemIcon 皮肤覆盖：规则命中的物品在图标上再画一层自定义贴图。
     * 贴图解析交给各版渲染器的 drawImage（托管目录直读链路已接好），
     * 规则没命中/画不了就无声退出——皮肤是锦上添花，不是必需品。
     */
    private static void paintSkin(LegacyRenderer r, String item, double x, double y,
                                  double size, double alpha) {
        com.opendreamcore.client.visual.LegacyVisualSkins.SkinEntry e =
                com.opendreamcore.client.visual.LegacyVisualSkins.skinForId(item);
        if (e == null) {
            return;
        }
        double pad = size * (1.0 - Math.min(1.0, Math.max(0.2, e.scale)) * 0.8);
        r.drawImage(e.texture, x + pad / 2, y + pad / 2, size - pad, size - pad,
                0, 0, 1, 1, mulAlpha(0xFFFFFFFF, alpha));
    }
}