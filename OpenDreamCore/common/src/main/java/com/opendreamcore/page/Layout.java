package com.opendreamcore.page;

/**
 * 元素布局：x/y/width/height，数字或表达式字符串（"parent.width - 30"）。
 */
public final class Layout {

    private final String x;
    private final String y;
    private final String width;
    private final String height;

    public Layout(String x, String y, String width, String height) {
        this.x = normalize(x);
        this.y = normalize(y);
        this.width = normalize(width);
        this.height = normalize(height);
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
