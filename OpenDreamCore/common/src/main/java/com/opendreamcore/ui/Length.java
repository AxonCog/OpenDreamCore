package com.opendreamcore.ui;

/**
 * 布局长度：数值 + 单位属性。
 *
 * 写法只有两种合法形态（正则 ^(-?\d+(\.\d+)?)(px)?$，允许首尾空白）：
 *   无单位：页面坐标系里的长度。声明了 design 的包按设计单位走（每帧乘缩放 s），
 *       没声明 design 的老包 s 恒为 1，于是数值就等于屏幕像素——同一个值在两条路上
 *       含义自洽，老包不会因为新管线而挪一个像素。
 *   带 px：画布像素，免缩放但不免锚点（挂在 bottom_right 上照样挂，只是尺寸不吃 s）。
 *
 * 其余写法（parent.width、vars.coin、窗口函数……）不是长度，是表达式，
 * 本类不吞：isExpression() 为真，交由布局引擎走表达式求值老路。
 * 值在语法上根本不像长度（y 写了个表、写了 true）才是解析失败，
 * 失败必须带 YAML 行列号抛出去，不许当 0 画到屏幕左上角去。
 */
public final class Length {

    /** 长度字面量：捕获组 1 = 数值，组 3 = px 后缀（组 2 是小数部分，不参与判定）。 */
    private static final java.util.regex.Pattern PATTERN =
            java.util.regex.Pattern.compile("^(-?\\d+(\\.\\d+)?)(px)?$");

    /** 解析失败（值不是长度也不是表达式），带 YAML 行列号。 */
    public static final class ParseException extends RuntimeException {
        private final int line;
        private final int column;

        ParseException(String message, int line, int column) {
            super(line > 0 ? message + "（第 " + line + " 行 第 " + column + " 列）" : message);
            this.line = line;
            this.column = column;
        }

        public int line() {
            return line;
        }

        public int column() {
            return column;
        }
    }

    public static final Length ZERO = new Length(0, false, false);

    private final float v;
    /** true = 写了 px 后缀：不吃视口缩放。 */
    private final boolean abs;
    /** true = 这不是长度字面量，得按表达式求值。 */
    private final boolean expression;

    private Length(double v, boolean abs, boolean expression) {
        this.v = (float) v;
        this.abs = abs;
        this.expression = expression;
    }

    /** 由已求值的表达式结果造长度（表达式结果一律按设计单位走，与无单位字面量同路）。 */
    public static Length ofExpressionResult(double value) {
        return new Length(value, false, false);
    }

    /** 画布像素长度（渲染期内部构造用）。 */
    public static Length ofPixels(double value) {
        return new Length(value, true, false);
    }

    /**
     * 解析布局值。
     *
     * @param raw    YAML 里的原始值（可为 Number，老包 x: 100 这种写法）
     * @param line   出错时上报的 YAML 行号（1 起，未知传 -1）
     * @param column 列号（1 起，未知传 -1）
     * @param key    键名，只为报错可读
     */
    public static Length parse(Object raw, int line, int column, String key) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number n) {
            return new Length(n.doubleValue(), false, false);
        }
        String text = String.valueOf(raw).trim();
        if (text.isEmpty()) {
            return null;
        }
        java.util.regex.Matcher m = PATTERN.matcher(text);
        if (m.matches()) {
            return new Length(Double.parseDouble(m.group(1)), m.group(3) != null, false);
        }
        if (looksLikeLength(text)) {
            throw new ParseException("布局属性 " + key + " 的值不是合法长度: \"" + text
                    + "\"（合法写法：数字、数字px，或布局表达式）", line, column);
        }
        return new Length(0, false, true);
    }

    /**
     * 像长度却没过正则的形态（+12、.5、12.、1e3、带空格的 12 px、全角数字）——
     * 这类是手滑，必须报错；带字母或点的（parent.width）才是表达式，放行给求值。
     */
    private static boolean looksLikeLength(String text) {
        char first = text.charAt(0);
        if (!(Character.isDigit(first) || first == '-' || first == '+' || first == '.')) {
            return false;
        }
        if (text.indexOf(' ') >= 0) {
            // 数字形态里夹空格的两种可能：
            //  "12 px"（数字 + 空格 + 单位）= 手滑，报错；
            //  "2 * entity.health_ratio" = 数字开头其实是算术表达式，放行给表达式求值。
            // 判定：剥掉首尾空白后恰好两个 token 且第二个是 px 才是手滑。
            String[] parts = text.trim().split("\\s+");
            return parts.length == 2
                    && parts[1].equalsIgnoreCase("px")
                    && parts[0].matches("-?\\d+(\\.\\d+)?");
        }
        for (int i = 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '.' && text.indexOf('.') == i) {
                continue; // 单个小数点仍是数字形态
            }
            if (i >= text.length() - 2 && (c == 'p' || c == 'P')
                    && text.regionMatches(i, "px", 0, 2)) {
                return true;
            }
            if (!(Character.isDigit(c) || c == 'p' || c == 'P')) {
                return false;
            }
        }
        return true;
    }

    public float v() {
        return v;
    }

    public boolean abs() {
        return abs;
    }

    public boolean isExpression() {
        return expression;
    }

    /** 折算成画布像素（abs 不乘 s，表达式结果乘 s）。 */
    public double toPixels(Viewport vp) {
        return abs ? v : v * vp.scale();
    }

    @Override
    public String toString() {
        return expression ? "expr" : v + (abs ? "px" : "");
    }
}
