package com.opendreamcore.client.bridge;

import com.opendreamcore.client.entity.EntityRenderBridge;
import com.opendreamcore.client.entity.ItemModelRenderBridge;
import com.opendreamcore.client.spi.ResourcePackInjector;

/**
 * 每个现代 target 必须实现的「桥总口」——全版本同步的契约层。
 *
 * 新增任何版本差异能力，就在这里加一个 getter：
 *   - 所有 target 的实现类会在同一轮编译里全部红，漏哪一个立刻看得出来；
 *   - common 的 SyncMatrixTest 还会在构建后反射核对每个 target 的实现类方法齐全，
 *     双保险堵死「有人只给几个版本加了实现」。
 *
 * 实现注意：getter 应返回「已造好」的桥实例（或默认单例），BridgeBootstrap 统一注册。
 */
public interface TargetBridges {

    /** 实体渲染桥（entity/model 组件 GUI 渲染）。 */
    EntityRenderBridge entityBridge();

    /** 物品 3D 展示桥（item_model 组件）。 */
    ItemModelRenderBridge itemModelBridge();

    /** 材质包注入器（托管目录 zip/文件夹/散图装载）。 */
    ResourcePackInjector packInjector();
}