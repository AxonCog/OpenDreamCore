package com.opendreamcore.client.entity;

/**
 * 实体渲染桥注册表。target 入口（FabricEvents / ClientEvents）在初始化时
 * register 自家实现；共享层 EntityViewComponent 只跟这个注册表说话。
 * 没注册时实体组件静默不画（不炸渲染）。
 */
public final class EntityViews {

    private static volatile EntityRenderBridge bridge;

    private EntityViews() {
    }

    public static void register(EntityRenderBridge b) {
        if (b != null) {
            bridge = b;
        }
    }

    /** 当前桥；没注册返回 null。 */
    public static EntityRenderBridge bridge() {
        return bridge;
    }
}