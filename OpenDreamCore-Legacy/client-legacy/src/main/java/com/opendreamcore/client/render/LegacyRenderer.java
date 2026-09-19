package com.opendreamcore.client.render;

/**
 * 渲染 SPI：client 层的页面绘制只认这套接口，
 * 各 target 用自家时代的 GL API 实现（1.16.5 BufferBuilder / 1.12.2 GlStateManager / 1.7.10 GL11）。
 * 全部在渲染线程调用；矩阵由实现方自管（pushPose/popPose 配对）。
 */
public interface LegacyRenderer {

    /** 屏幕宽高（GUI 缩放后的逻辑像素）。 */
    int screenWidth();

    int screenHeight();

    void pushPose();

    void popPose();

    /** 平移（逻辑像素）。 */
    void translate(double x, double y, double z);

    /** 绕 Z 轴旋转（度）。 */
    void rotateZ(double degrees);

    /** 等比缩放（动画的 scale 段用；坐标/尺寸同乘）。 */
    default void scale(double factor) {
        // 画笔只有平移/旋转的版本可以空实现：动画缩放退化成无缩放，
        // 面板照常画，只是呼吸效果少一层
    }

    /**
     * 实心矩形。color 为 ARGB。
     */
    void fillRect(double x, double y, double w, double h, int argb);

    /**
     * 描边矩形（四边各 1px，同色）。
     */
    void outlineRect(double x, double y, double w, double h, int argb);

    /**
     * 圆角实心矩形（radius 为像素，超过短边一半自动收敛）。
     * 高版本背景底衬同款：中央 + 四边 + 四角四分之一圆扇形。
     * 实现不了的版本退化为直角矩形（default 兑底，观感差一点但链路不断）。
     */
    default void fillRounded(double x, double y, double w, double h, double radius, int argb) {
        fillRect(x, y, w, h, argb);
    }

    /**
     * 圆角渐变实心矩形：顶色 topArgb 线性过渡到底色 bottomArgb，
     * 圆角扇形内同样按 y 插值（和高版本 renderBackground 同一视觉）。
     */
    default void fillRoundedGradient(double x, double y, double w, double h, double radius,
                                     int topArgb, int bottomArgb) {
        fillRounded(x, y, w, h, radius, topArgb);
    }

    /**
     * 直线：从 (x1,y1) 到 (x2,y2)，线宽像素。
     * 默认实现退化：水平/垂直直接垫矩形，斜线用逐格堆点凑合；
     * 各家 target 用自家 GL 线段重写。
     */
    default void drawLine(double x1, double y1, double x2, double y2, double width, int argb) {
        if (Math.abs(x1 - x2) < 0.001) {
            double cx = (y1 < y2) ? y1 : y2;
            fillRect(x1 - width / 2, cx, width, Math.abs(y1 - y2) + width, argb);
        } else if (Math.abs(y1 - y2) < 0.001) {
            double cx = (x1 < x2) ? x1 : x2;
            fillRect(cx, y1 - width / 2, Math.abs(x1 - x2) + width, width, argb);
        } else {
            // 斜线堆点：步长半个线宽，粗看是条线，细看是串骰子——聊胜于无
            double dx = x2 - x1;
            double dy = y2 - y1;
            double steps = Math.max(Math.abs(dx), Math.abs(dy)) / Math.max(width * 0.5, 1.0);
            for (int i = 0; i <= steps; i++) {
                double t = steps == 0 ? 0 : i / steps;
                fillRect(x1 + dx * t - width / 2, y1 + dy * t - width / 2, width, width, argb);
            }
        }
    }

    /**
     * 实心圆：圆心 (cx,cy) 半径 radius，扇形细分（同圆角底衬那套算法）。
     * 默认退化用圆角矩形凑，接近但不是真圆；各家 target 用三角扇重写。
     */
    default void fillCircle(double cx, double cy, double radius, int argb) {
        fillRounded(cx - radius, cy - radius, radius * 2, radius * 2, radius * 0.85, argb);
    }

    /**
     * 实心三角形。默认退化：画个包围盒占位，至少位置形状对得上账；
     * 各家 target 用自家 GL 重写。
     */
    default void fillTriangle(double x1, double y1, double x2, double y2,
                              double x3, double y3, int argb) {
        double minX = Math.min(x1, Math.min(x2, x3));
        double maxX = Math.max(x1, Math.max(x2, x3));
        double minY = Math.min(y1, Math.min(y2, y3));
        double maxY = Math.max(y1, Math.max(y2, y3));
        int dim = (argb & 0x00FFFFFF) | ((argb >>> 2) & 0x3F000000);
        fillRect(minX, minY, maxX - minX, maxY - minY, dim);
    }

    /**
     * 文本绘制。返回文本实际宽度（供布局后续计算）。
     */
    double drawText(String text, double x, double y, int argb, boolean shadow);

    /** 文本宽度（不落笔）。 */
    double textWidth(String text);

    /**
     * 贴图：texture 为任意稳定 id（target 自行映射到本版本 ResourceLocation/域名）。
     * uv 区域按纹理实际尺寸的比例给。
     */
    void drawImage(String texture, double x, double y, double w, double h,
                   double u0, double v0, double u1, double v1, int tintArgb);

    /**
     * 世界相位画布：在世界坐标 anchor 处开一块正对镜头的 billboard 平面。
     * begin 之后，上面那套 2D 画法（fillRect/drawText/drawImage）照常工作，
     * 但坐标不再是屏幕像素，而是面板平面像素——1px = 0.025 格
     * （现代端 HoloTextRender 同尺度），面板是真实方块尺寸，远了小近了大。
     *
     * 返回 false 表示这版没实现（世界页该帧跳过）；实现方负责：
     * push 矩阵 → 平移到锚点（已含 -相机位置）→ 朝向镜头旋转 → 缩放，
     * 以及混合开/光照关/背面剔除关/深度写入关（面板会被墙挡但不挡后画的）。
     */
    default boolean beginWorld(double camX, double camY, double camZ,
                               double anchorX, double anchorY, double anchorZ) {
        return false;
    }

    /** 收起世界画布：与 beginWorld 严格配对，恢复状态弹矩阵。 */
    default void endWorld() { }

    /**
     * 头顶页用的活体名单：半径内的生物快照（坐标/身高/血量/名字/类型）。
     * 实现不了的版本退化为空名单（头顶页该帧静默，不出错）。
     */
    default java.util.List<com.opendreamcore.client.spi.EntitySource.Snapshot> nearbyLiving(
            double camX, double camY, double camZ, double maxDist) {
        return java.util.Collections.emptyList();
    }
}
