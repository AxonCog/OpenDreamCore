package com.opendreamcore.client.entity;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 物品 3D 模型渲染桥（SPI）。item_model 组件在各版本的物品渲染管线上差异大
 * （renderStatic / render / 26.1.2 的 item()），差异全收敛在这一个实现里。
 */
public interface ItemModelRenderBridge {

    /**
     * GUI 内渲染物品 3D 模型。
     *
     * @param g       绘制上下文（各版本实参类型不同：1.20/1.21 是 GuiGraphics，
     *                26.1.2 是 GuiGraphicsExtractor。接口故意用 Object——
     *                26.1.2 把 GuiGraphics 类整个删了，接口要是钉死它，
     *                那边编译共享层直接炸。桥实现自己 cast 到本版本类型）
     * @param cx      元素中心 X
     * @param cy      元素中心 Y
     * @param scale   缩放倍率（1.0 = 默认尺寸）
     * @param yaw     朝向角度（度，含 followMouse 与 rotateY 叠加）
     * @param pitch   俯仰角度（度，含 rotateX）
     * @param itemId  物品 id（minecraft:diamond_sword 这种）
     * @param alpha   透明度 0-255
     */
    void drawItemModel(Object g, int cx, int cy, float scale, float yaw, float pitch,
                       String itemId, int alpha);
}