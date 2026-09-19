package com.opendreamcore.bridge;

import com.opendreamcore.client.bridge.TargetBridges;
import com.opendreamcore.client.entity.EntityRenderBridge;
import com.opendreamcore.client.entity.ItemModelRenderBridge;
import com.opendreamcore.client.spi.ResourcePackInjector;

/**
 * fabric-26.1.2 的桥总实现：TargetBridges 契约缺一不行——接口加方法这文件就得跟着
 * 加，漏了直接编译红，全版本对齐从结构上兜底。
 */
public final class Fabric2612Bridges implements TargetBridges {

    private static final EntityRenderBridge ENTITY = new com.opendreamcore.client.entity.EntityRenderBridgeImpl();
    private static final ItemModelRenderBridge ITEM = new com.opendreamcore.client.entity.ItemModelRenderBridgeImpl();
    private static final ResourcePackInjector INJECTOR = new com.opendreamcore.client.FabricPackInjector();

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
}
