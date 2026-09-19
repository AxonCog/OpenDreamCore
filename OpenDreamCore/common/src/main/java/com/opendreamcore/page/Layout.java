package com.opendreamcore.page;

import com.opendreamcore.ui.Length;

/**
 * 元素布局：x/y/width/height，数字（含 px 后缀长度）、或表达式字符串（"parent.width - 30"）。
 *
 * 字面量在构造期先过 {@link Length} 识别：纯数字/数字px 记成长度（带单位属性），
 * 其余保持表达式文本交给布局引擎求值——表达式路径与旧版逐字一致。
 * 识别失败（"12 px"、"1e3" 这类手滑写法）带 YAML 行列号抛出，不许静默当 0。
 * 位置未知的构造（引擎内部合成 Layout）传 -1，异常信息里就不带行列号。
 */
public final class Layout {

    public static final int X = 0;
    public static final int Y = 1;
    public static final int WIDTH = 2;
    public static final int HEIGHT = 3;

    /** 一个布局键在 YAML 原文里的位置（行列 1 起，未知传 -1）。 */
    public static final class Pos {
        public static final Pos UNKNOWN = new Pos(-1, -1);
        public final int line;
        public final int column;

        public Pos(int line, int column) {
            this.line = line;
            this.column = column;
        }
    }

    private final String x;
    private final String y;
    private final String width;
    private final String height;
    private final Length xLength;
    private final Length yLength;
    private final Length widthLength;
    private final Length heightLength;

    public Layout(String x, String y, String width, String height) {
        this(x, y, width, height, -1, -1);
    }

    /** 带 YAML 行列号的构造（页面 schema 解析用，报错要能指到行）。 */
    public Layout(String x, String y, String width, String height, int line, int column) {
        this(x, y, width, height,
                new Pos(line, column), new Pos(line, column), new Pos(line, column), new Pos(line, column));
    }

    /** 每键独立行列号（LocationIndex 查出来的真实位置，优先用这个）。 */
    public Layout(String x, String y, String width, String height,
                  Pos xPos, Pos yPos, Pos widthPos, Pos heightPos) {
        this.x = normalize(x);
        this.y = normalize(y);
        this.width = normalize(width);
        this.height = normalize(height);
        this.xLength = parse(this.x, "x", xPos);
        this.yLength = parse(this.y, "y", yPos);
        this.widthLength = parse(this.width, "width", widthPos);
        this.heightLength = parse(this.height, "height", heightPos);
    }

    private static Length parse(String raw, String key, Pos pos) {
        if (raw == null) {
            return null;
        }
        Length len = Length.parse(raw, pos.line, pos.column, key);
        // 表达式（isExpression）返回 null：求值交给引擎，长度只覆盖字面量
        return len == null || len.isExpression() ? null : len;
    }

    public Length length(int which) {
        switch (which) {
            case X: return xLength;
            case Y: return yLength;
            case WIDTH: return widthLength;
            default: return heightLength;
        }
    }

    private static String normalize(String v) {
        return v == null ? null : v.trim();
    }

    public String x() {
        return x;
    }

    public String y() {
        return y;
    }

    public String width() {
        return width;
    }

    public String height() {
        return height;
    }
}
