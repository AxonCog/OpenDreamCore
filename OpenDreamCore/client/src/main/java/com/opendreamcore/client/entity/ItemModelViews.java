package com.opendreamcore.client.entity;

/**
 * 物品模型渲染桥注册表。target 入口注册自家实现；没注册时 item_model 组件静默不画。
 */
public final class ItemModelViews {

    private static volatile ItemModelRenderBridge bridge;

    private ItemModelViews() {
    }

    public static void register(ItemModelRenderBridge b) {
        if (b != null) {
            bridge = b;
        }
    }

    public static ItemModelRenderBridge bridge() {
        return bridge;
    }
}