package com.opendreamcore.ui.theme;

import com.opendreamcore.util.J8;

import java.util.Locale;

/**
 * 响应式条件：主题里的 "@media"，但量的是窗口宽高像素，
 * 条件是窗口宽高（像素），布局每次重算时现判现用，窗口拉伸拖动即时响应。
 *
 * 支持四种原子条件，可任意组合（全部满足才算命中）：
 * <pre>
 *   "@max-width 854"                              简写形式
 *   "@media (min-width: 400) and (max-height: 600)" CSS 风格形式
 * </pre>
 */
public record MediaQuery(double minWidth, double maxWidth,
                         double minHeight, double maxHeight) {

    private static final double UNSET = Double.NaN;

    /** 全空条件：永真。 */
    public static final MediaQuery ALWAYS = new MediaQuery(UNSET, UNSET, UNSET, UNSET);

    public boolean matches(double windowWidth, double windowHeight) {
        if (!Double.isNaN(minWidth) && windowWidth < minWidth) {
            return false;
        }
        if (!Double.isNaN(maxWidth) && windowWidth > maxWidth) {
            return false;
        }
        if (!Double.isNaN(minHeight) && windowHeight < minHeight) {
            return false;
        }
        if (!Double.isNaN(maxHeight) && windowHeight > maxHeight) {
            return false;
        }
        return true;
    }

    public boolean isEmpty() {
        return Double.isNaN(minWidth) && Double.isNaN(maxWidth)
                && Double.isNaN(minHeight) && Double.isNaN(maxHeight);
    }

    /**
     * 解析媒体条件；无法解析返回 null（调用方 warn 跳过）。
     * 两种形态都吃：
     *   "@max-width 854"
     *   "@media (min-width: 400) and (max-height: 600)"
     */
    static MediaQuery parse(String raw) {
        if (raw == null || J8.isBlank(raw)) {
            return null;
        }
        String t = raw.trim();
        double minW = UNSET, maxW = UNSET, minH = UNSET, maxH = UNSET;

        // 提取所有 (维度 比较符 数值) 片段——两种写法统一走这一条路
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "\\(?\\s*(min-width|max-width|min-height|max-height)\\s*:?\\s*([0-9.]+)\\s*\\)?",
                java.util.regex.Pattern.CASE_INSENSITIVE).matcher(t);
        while (m.find()) {
            String dim = m.group(1).toLowerCase(Locale.ROOT);
            double v = Double.parseDouble(m.group(2));
            switch (dim) {
                case "min-width" -> minW = v;
                case "max-width" -> maxW = v;
                case "min-height" -> minH = v;
                case "max-height" -> maxH = v;
                default -> { }
            }
        }
        MediaQuery q = new MediaQuery(minW, maxW, minH, maxH);
        return q.isEmpty() ? null : q;
    }

    /** 两个条件取交集（各自维度收紧）；任一为 null 返回另一个。 */
    public static MediaQuery merge(MediaQuery a, MediaQuery b) {
        if (a == null || a.isEmpty()) return b;
        if (b == null || b.isEmpty()) return a;
        return new MediaQuery(
                max(a.minWidth(), b.minWidth()),
                min(a.maxWidth(), b.maxWidth()),
                max(a.minHeight(), b.minHeight()),
                min(a.maxHeight(), b.maxHeight()));
    }

    private static double max(double x, double y) {
        if (Double.isNaN(x)) return y;
        if (Double.isNaN(y)) return x;
        return Math.max(x, y);
    }

    private static double min(double x, double y) {
        if (Double.isNaN(x)) return y;
        if (Double.isNaN(y)) return x;
        return Math.min(x, y);
    }

    /** 人话描述（日志与调试用）。 */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("@");
        boolean any = false;
        if (!Double.isNaN(minWidth)) { sb.append("min-width ").append((int) minWidth); any = true; }
        if (!Double.isNaN(maxWidth)) { if (any) sb.append(" & "); sb.append("max-width ").append((int) maxWidth); any = true; }
        if (!Double.isNaN(minHeight)) { if (any) sb.append(" & "); sb.append("min-height ").append((int) minHeight); any = true; }
        if (!Double.isNaN(maxHeight)) { if (any) sb.append(" & "); sb.append("max-height ").append((int) maxHeight); }
        return sb.toString();
    }
}
