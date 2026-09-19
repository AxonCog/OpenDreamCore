package com.opendreamcore.ui;

import java.util.logging.Logger;

/**
 * 视口：设计画布 → 屏幕的每帧变换。
 *
 * 规则只有一条：包/页面声明了 design:{width,height,fit?} 才启用；没声明就是 IDENTITY
 * （s=1、ox=oy=0），老包一帧都不会挪。
 *
 *   s  = min(屏幕宽/设计宽, 屏幕高/设计高)
 *   ox/oy = 按 fit 策略定（letterbox 居中留边 / anchor_* 贴角边）
 *
 * 整数倍闸门：design 高度必须能被 180 整除（360/540/720/900/1080/1440…），
 * 保证所有常见逻辑分辨率下 s 是整数倍（文字/贴图吃整数倍红利）；不合规 warn + 退回 IDENTITY。
 *
 * 布局引擎全程在设计坐标系里算（window.width 就是设计宽），最后一步用
 * roundEdge 把「边」取整（x0/x1 各自 round 后相减得宽高）——字段不 round，
 * 相邻元素共用同一条边，取整后必然无缝，混用 px 与无单位也不漏缝。
 *
 * 反向：鼠标屏幕坐标用 unlayout/pxToDesign 换回设计坐标（编辑器拖拽、命中回写用）。
 */
public final class Viewport {

    private static final Logger LOGGER = Logger.getLogger(Viewport.class.getName());

    /** 未声明 design 的老包口径：逐帧等价于旧版 1:1。 */
    public static final Viewport IDENTITY = new Viewport(0, 0, 1, 0, 0, "identity");

    private final double designWidth;
    private final double designHeight;
    private final double scale;
    private final double offsetX;
    private final double offsetY;
    private final String fit;

    /** 最近一次页面布局使用的视口（渲染层取来做文本缩放与 debug 打印）。 */
    private static volatile Viewport active = IDENTITY;

    /** debug 模式：布局后打印 cw/ch/s/ox/oy 与每元素整数 rect（客户端开关注入）。 */
    private static volatile boolean debug;

    private Viewport(double dw, double dh, double s, double ox, double oy, String fit) {
        this.designWidth = dw;
        this.designHeight = dh;
        this.scale = s;
        this.offsetX = ox;
        this.offsetY = oy;
        this.fit = fit == null ? "letterbox" : fit;
    }

    /** 由声明尺寸与当前屏幕尺寸构建；参数非法（<=0）或基准不合规退回 IDENTITY，绝不影响渲染。 */
    public static Viewport of(double designWidth, double designHeight, double screenW, double screenH) {
        if (!valid(designWidth, designHeight) || screenW <= 0 || screenH <= 0) {
            return IDENTITY;
        }
        double s = Math.min(screenW / designWidth, screenH / designHeight);
        return new Viewport(designWidth, designHeight, s,
                (screenW - designWidth * s) / 2, (screenH - designHeight * s) / 2, "letterbox");
    }

    /** 任意变换直接构造（ViewportStrategies 内置 anchor 策略用）；同样走整数倍闸门。 */
    public static Viewport ofTransform(double dw, double dh, double s, double ox, double oy, String fit) {
        if (!valid(dw, dh) || s <= 0 || Double.isNaN(ox) || Double.isNaN(oy)) {
            return IDENTITY;
        }
        return new Viewport(dw, dh, s, ox, oy, fit);
    }

    /** 整数倍闸门：高度必须能被 180 整除，保证常见逻辑分辨率下 s 是整数倍。 */
    private static boolean valid(double dw, double dh) {
        if (dw <= 0 || dh <= 0) {
            return false;
        }
        if (dh % 180 != 0) {
            LOGGER.warning(() -> "[OpenDreamCore][viewport] 基准高度 " + (int) dh
                    + " 不是 180 的整数倍，退回 IDENTITY——design 高度用 360/540/720/1080/1440 这些"
                    + "（保证缩放系数是整数倍，文字贴图才锐利）");
            return false;
        }
        return true;
    }

    public static Viewport active() {
        return active;
    }

    public static void setActive(Viewport vp) {
        active = vp == null ? IDENTITY : vp;
    }

    public static void setDebug(boolean on) {
        debug = on;
    }

    public static boolean debug() {
        return debug;
    }

    public boolean isIdentity() {
        return scale == 1.0 && offsetX == 0.0 && offsetY == 0.0;
    }

    public double designWidth() {
        return designWidth;
    }

    public double designHeight() {
        return designHeight;
    }

    public double scale() {
        return scale;
    }

    public double offsetX() {
        return offsetX;
    }

    public double offsetY() {
        return offsetY;
    }

    /** fit 策略名（letterbox / anchor_* / 自定义；toString 与调试用）。 */
    public String fit() {
        return fit;
    }

    /** 画布像素 → 设计坐标 X（px 位置用，贴真屏幕左边）。 */
    public double pxToDesignX(double screenV) {
        return isIdentity() ? screenV : (screenV - offsetX) / scale;
    }

    /** 画布像素 → 设计坐标 Y（px 位置用，贴真屏幕顶边）。 */
    public double pxToDesignY(double screenV) {
        return isIdentity() ? screenV : (screenV - offsetY) / scale;
    }

    /**
     * 长度字面量 → 设计坐标数值。
     * abs（px）尺寸：一画布像素 = 1/s 个设计单位，缩放后恰好还原成 v 个像素；
     * 位置用的 px 不走这里，走 pxToDesignX/Y（否则被居中度挤离屏幕边）。
     * IDENTITY 下 s=1，无单位 ≡ px，与老包口径重合。
     */
    public double layoutLength(Length len) {
        if (len == null) {
            return 0;
        }
        return len.abs() ? len.v() / scale : len.v();
    }

    /** 设计坐标 → 屏幕像素 X（边取整）。 */
    public double edgeX(double designX) {
        return isIdentity() ? designX : Math.round(offsetX + designX * scale);
    }

    public double edgeY(double designY) {
        return isIdentity() ? designY : Math.round(offsetY + designY * scale);
    }

    /** 宽高由两条边相减得出（零缝保证）；起点 NaN/尺寸 NaN 原样透传。 */
    public double spanW(double designX, double designW) {
        if (isIdentity() || Double.isNaN(designX) || Double.isNaN(designW)) {
            return designW;
        }
        return Math.round(offsetX + (designX + designW) * scale) - Math.round(offsetX + designX * scale);
    }

    public double spanH(double designY, double designH) {
        if (isIdentity() || Double.isNaN(designY) || Double.isNaN(designH)) {
            return designH;
        }
        return Math.round(offsetY + (designY + designH) * scale) - Math.round(offsetY + designY * scale);
    }

    /** 逆变换：鼠标屏幕坐标 → 设计坐标（编辑器拖拽/命中回写用）。 */
    public double unlayoutX(double screenX) {
        return isIdentity() ? screenX : (screenX - offsetX) / scale;
    }

    public double unlayoutY(double screenY) {
        return isIdentity() ? screenY : (screenY - offsetY) / scale;
    }

    @Override
    public String toString() {
        return isIdentity() ? "Viewport[legacy]"
                : "Viewport[design=" + (int) designWidth + "x" + (int) designHeight
                        + " s=" + scale + " ox=" + offsetX + " oy=" + offsetY + "]";
    }
}
