package com.opendreamcore.client.glyph;

/**
 * 替换字形的几何策略（全代际共用，零 MC 依赖）。
 *
 * 只回答三个问题，全部是纯算术：
 *   1. 一条贴图规则该从纹理的哪个像素矩形取样（{@link Slice}）；
 *   2. 字形框占多大、落在基线的什么位置、排版推进多宽（{@link #bitmap}）；
 *   3. 像素坐标怎么换算成归一化 UV（{@link #norm}）。
 *
 * 三种配置语义与几何一一对应：
 *   - 写了 height：沿用原版字符框（顶边 -1、底边 height-1），行高不因替换而改变；
 *   - 未写尺寸：按贴图原生像素绘制，底边贴基线向上生长（表情大图语义）；
 *   - 只写 fontWidth：绘制尺寸不变，仅改排版步进。
 *
 * 所有方法都不抛异常，任何缺数据都退到「一个字符大」这一最保守取值，保证渲染管线
 * 永远拿到一份合法几何；贴图未就绪由调用方在取样前拦掉（返回原版字形）。
 */
public final class OdcGlyphGeometry {

    /** 显式尺寸模式的顶边（对齐原版字符框语义）。 */
    private static final float EXPLICIT_UP = -1.0F;
    /** 原生尺寸模式的底边（贴基线）。 */
    private static final float NATIVE_DOWN = 7.0F;
    /** 尺寸未知时的兜底字符宽。 */
    private static final float FALLBACK = 9.0F;

    /** 排版步进兜底值（贴图尚未就绪时用，一个标准字符宽）。 */
    public static final float FALLBACK_ADVANCE = 9.0F;

    private final float left;
    private final float right;
    private final float up;
    private final float down;
    private final float advance;

    private OdcGlyphGeometry(float left, float right, float up, float down, float advance) {
        this.left = left;
        this.right = right;
        this.up = up;
        this.down = down;
        this.advance = advance;
    }

    public float left() {
        return left;
    }

    public float right() {
        return right;
    }

    public float up() {
        return up;
    }

    public float down() {
        return down;
    }

    public float advance() {
        return advance;
    }

    /** 贴图取样矩形（像素坐标，左上原点）。 */
    public static final class Slice {
        private final int x;
        private final int y;
        private final int w;
        private final int h;

        Slice(int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        public int x() {
            return x;
        }

        public int y() {
            return y;
        }

        public int w() {
            return w;
        }

        public int h() {
            return h;
        }
    }

    /**
     * 决定从纹理上取哪一块像素，三种来源按优先级判定：
     *   1. 多帧动画（frames &gt; 1）：取当前帧在横向帧表里的那一格，纵向占满；
     *   2. 区间/图集（给了 u/v 与单帧尺寸）：按给定像素矩形取；
     *   3. 单张整图：整张纹理都是这个字的。
     * 越界矩形一律夹回纹理范围内，绝不产生非法 UV。
     */
    public static Slice sliceOf(int u, int v, int sliceW, int sliceH,
                                int frame, int frames, int texW, int texH) {
        int x;
        int y;
        int w;
        int h;
        if (frames > 1 && sliceW > 0 && sliceH > 0) {
            int f = Math.max(0, Math.min(frame, frames - 1));
            x = f * sliceW;
            y = 0;
            w = sliceW;
            h = sliceH;
        } else if (sliceW > 0 && sliceH > 0 && (u > 0 || v > 0)) {
            x = u;
            y = v;
            w = sliceW;
            h = sliceH;
        } else {
            x = 0;
            y = 0;
            w = texW;
            h = texH;
        }
        if (texW > 0) {
            x = Math.max(0, Math.min(x, texW - 1));
            w = Math.max(1, Math.min(w, texW - x));
        }
        if (texH > 0) {
            y = Math.max(0, Math.min(y, texH - 1));
            h = Math.max(1, Math.min(h, texH - y));
        }
        return new Slice(x, y, w, h);
    }

    /**
     * 位图 / GIF 字形几何。
     *
     * @param width     配置里写的绘制宽（0/负 = 未写）
     * @param height    配置里写的绘制高（0/负 = 未写，走原生尺寸语义）
     * @param fontWidth 配置里写的排版步进（0/负 = 用绘制宽）
     * @param slice     取样矩形（原生尺寸模式下由它决定绘制大小）
     */
    public static OdcGlyphGeometry bitmap(int width, int height, int fontWidth, Slice slice) {
        float w;
        float h;
        float up;
        float down;
        if (height > 0) {
            // 显式尺寸：老语义（顶边 -1、底边 height-1），行高保持原版
            w = Math.max(1.0F, width > 0 ? width : FALLBACK);
            h = Math.max(1.0F, height);
            up = EXPLICIT_UP;
            down = h - 1.0F;
        } else {
            // 原生尺寸：真实像素宽高，底边贴基线向上生长
            w = Math.max(1.0F, slice.w());
            h = Math.max(1.0F, slice.h());
            down = NATIVE_DOWN;
            up = down - h;
        }
        float advance = fontWidth > 0 ? fontWidth : w;
        return new OdcGlyphGeometry(0.0F, w, up, down, Math.max(1.0F, advance));
    }

    /**
     * 全局 TTF 字形几何：度量取自字体光栅结果，底边自然伸到基线下方；
     * 推进宽度用字体自身 advance（不是位图宽，避免字距忽宽忽窄）。
     */
    public static OdcGlyphGeometry ttf(float pixelWidth, float pixelHeight, float top, float advance) {
        float width = Math.max(1.0F, pixelWidth);
        float height = Math.max(1.0F, pixelHeight);
        return new OdcGlyphGeometry(0.0F, width, top, top + height, Math.max(1.0F, advance));
    }

    /** 像素坐标 → 归一化坐标；非法或越界都夹回 [0,1]。 */
    public static float norm(int pixel, int total) {
        if (total <= 0 || pixel <= 0) {
            return 0.0F;
        }
        float v = (float) pixel / total;
        return v > 1.0F ? 1.0F : v;
    }
}
