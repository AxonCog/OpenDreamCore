package com.opendreamcore.client.entity;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 实体 GUI 渲染桥（SPI）。差异全收敛在这一个实现里：
 * 解析实体引用、造展示实体、GUI 内画实体——各版本 Minecraft 渲染 API 不同，
 * 每个 target 提供一份实现注册进 EntityViews。共享层不碰版本细节。
 *
 * 接口里只出现全版本都有的类型（GuiGraphics / Object 兜底实体），
 * 任何版本特有类（EntityRenderDispatcher 之类）不许漏进共享层。
 */
public interface EntityRenderBridge {

    /**
     * 解析实体引用 → 实体实例（渲染端转成 LivingEntity 用）。
     * ref 语义：owner（自己）/ 玩家名 / 实体UUID / 空串（同 owner）。
     * 找不到返回 null（组件按"没有实体"跳过绘制）。
     */
    Object resolveEntity(String ref);

    /**
     * 按模型类型造一个展示用临时实体（type: model 组件）。
     * model：player / armor_stand / 原版生物类型（zombie、pig…）；
     * 不认识的类型回退 armor_stand（总有东西可看，不静默空白）。
     */
    Object dummyFor(String model);

    /**
     * GUI 内渲染实体。
     *
     * @param g        绘制上下文
     * @param cx       元素中心 X（屏幕坐标）
     * @param cy       元素中心 Y
     * @param scale    缩放倍率（1.0 = 默认 30px 高模型；供姿态微调）
     * @param yaw      朝向角度（度；已含 followMouse 与 rotateY 叠加，180=正对屏幕）
     * @param pitch    俯仰角度（度；已含 rotateX）
     * @param entity   实体实例（resolveEntity / dummyFor 产物）
     * @param alpha    透明度 0-255（255 不透明；<255 尽力整体淡出，实现不了就忽略）
     * @param hideName 是否隐藏名牌
     */
    void drawEntity(GuiGraphics g, int cx, int cy, float scale, float yaw, float pitch,
                    Object entity, int alpha, boolean hideName);
}