package com.opendreamcore.ui;

/**
 * 视口策略（坐标系热拔插的插头）——"design 画布怎么映射到屏幕"由谁说了算。
 *
 * 页面声明 design:{width,height,fit:名字} 时，按 fit 名查注册表拿到策略：
 *   letterbox            等比缩放 + 居中留边（全屏页面默认）
 *   anchor_top_left      等比缩放 + 贴左上（HUD 面板：低比例窗口裁右下）
 *   anchor_top_right     等比缩放 + 贴右上（裁左下）
 *   anchor_bottom_left   等比缩放 + 贴左下（裁右上）
 *   anchor_bottom_right  等比缩放 + 贴右下（裁左上）
 *   anchor_top_center    等比缩放 + 贴顶边居中（裁底部）
 *   anchor_bottom_center 等比缩放 + 贴底边居中（裁顶部）
 *   anchor_center        同 letterbox
 *
 * 想自定义坐标系（拉伸填满、保宽等比、基准偏移……）？实现本接口
 * register 进 {@link ViewportStrategies}，页面写 fit:你的名字 就行，
 * 核心渲染/命中/动画层一个字符都不用动。
 */
public interface ViewportStrategy {

    /** 策略名（design.fit 的值；同名注册覆盖旧的）。 */
    String name();

    /** 由设计尺寸 + 当前画布尺寸造视口；参数非法时返回 {@link Viewport#IDENTITY}（绝不拦渲染）。 */
    Viewport build(double designWidth, double designHeight, double screenW, double screenH);
}