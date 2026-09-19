package com.opendreamcore.ui;

/**
 * 布局锚点（坐标系热拔插的又一个插头）——"元素挂在哪"由谁说了算。
 *
 * 内置九宫格（LayoutAnchors 里注册）：center / top_* / bottom_* / center_*。
 * 附属想发明新锚法（黄金分割、靠某元素边、屏幕某百分比……）？实现本接口注册，
 * 页面 anchor:你的名字 就能用——布局引擎一个字符不用改。
 *
 * point() 返回 {x, y} = 相对容器的基准点（容器左上 0,0，右下 W,H）；
 * 元素最终位置 = 基准点 + 自己的偏移。偏移方向两套默认实现：
 *   rx==1（右系锚点）水平偏移入向取反（向容器内为正）；ry==1（底系锚点）垂直同理；
 *   其余照常向右/向下为正——和九宫格的旧符号完全一致。
 */
public interface LayoutAnchor {

    /** 锚点名（anchor 属性的值；同名注册覆盖旧的）。 */
    String name();

    /** 容器尺寸 + 元素尺寸 → 锚点基准 {x, y}（相对容器左上）。 */
    double[] point(double containerW, double containerH, double elementW, double elementH);

    /** 水平偏移量（inward=true 且右系锚点 → 入向取反）。 */
    default double offsetX(double v, boolean inward) {
        return v;
    }

    /** 垂直偏移量（inward=true 且底系锚点 → 入向取反）。 */
    default double offsetY(double v, boolean inward) {
        return v;
    }
}