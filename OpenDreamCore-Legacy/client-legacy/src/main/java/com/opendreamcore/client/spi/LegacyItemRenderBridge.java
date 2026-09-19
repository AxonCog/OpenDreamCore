package com.opendreamcore.client.spi;

/**
 * GUI 内物品 3D 模型渲染桥（老壳线）。item_model 组件的远古版入口，
 * 版本差异（RenderItem / RenderItemModel / 1.16.5 的 ItemRenderer）全在实现里。
 */
public interface LegacyItemRenderBridge {

    /** GUI 内渲染物品 3D 模型：cx/cy 中心、scale 缩放、yaw/pitch 朝向（度）、itemId 物品 id。 */
    void drawItemModel(int cx, int cy, float scale, float yaw, float pitch, String itemId);

    /** 注册与兜底。 */
    final class Host {
        private static volatile LegacyItemRenderBridge bridge;

        private Host() {
        }

        public static void register(LegacyItemRenderBridge b) {
            if (b != null) {
                bridge = b;
            }
        }

        public static LegacyItemRenderBridge current() {
            return bridge;
        }
    }
}