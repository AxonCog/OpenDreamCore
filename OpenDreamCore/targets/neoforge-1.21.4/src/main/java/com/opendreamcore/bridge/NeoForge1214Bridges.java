package com.opendreamcore.bridge;

import com.opendreamcore.client.bridge.TargetBridges;
import com.opendreamcore.client.entity.EntityRenderBridge;
import com.opendreamcore.client.entity.ItemModelRenderBridge;
import com.opendreamcore.client.spi.GlStateBridge;
import com.opendreamcore.client.spi.ResourcePackInjector;

/**
 * neoforge-1.21.4 的 TargetBridges 实现；接口新增方法时需同步实现，确保该目标的桥契约完整。
 */
public final class NeoForge1214Bridges implements TargetBridges {
    /** 全局 GL / 着色器状态桥；调用点统一使用该桥，避免按签名反射。 */
    private static final GlStateBridge GL_STATE = new com.opendreamcore.client.GlStateBridgeImpl();

    private static final EntityRenderBridge ENTITY = new com.opendreamcore.client.entity.EntityRenderBridgeImpl();
    private static final ItemModelRenderBridge ITEM = new com.opendreamcore.client.entity.ItemModelRenderBridgeImpl();
    private static final ResourcePackInjector INJECTOR = new com.opendreamcore.client.NeoForgePackInjector();

    @Override
    public EntityRenderBridge entityBridge() {
        return ENTITY;
    }

    @Override
    public ItemModelRenderBridge itemModelBridge() {
        return ITEM;
    }

    @Override
    public ResourcePackInjector packInjector() {
        return INJECTOR;
    }

    @Override
    public GlStateBridge glStateBridge() {
        return GL_STATE;
    }
}
