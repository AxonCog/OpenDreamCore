package com.opendreamcore.client.spi;

/**
 * GUI 内实体渲染桥（老壳线）。
 *
 * type: entity / model 组件在远古四版的渲染入口：老版本没有统一的 GuiGraphics 上下文，
 * 渲染走各自版本的 GL/屏幕 API（1.16.5 的 GuiInventory、1.12.2 的 drawEntityOnScreen、
 * 1.7.10/1.6.4 的 RenderManager），所以接口只谈坐标和实体对象，版本差异全在实现里。
 */
public interface LegacyEntityRenderBridge {

    /** 解析实体引用（owner / 玩家名 / UUID）→ 实体对象；找不到 null。 */
    Object resolveEntity(String ref);

    /** 按模型类型造展示实体（player / armor_stand / 原版生物）；识别不了回退盔甲架。 */
    Object dummyFor(String model);

    /** GUI 内渲染实体：cx/cy 中心、scale 缩放、yaw/pitch 朝向（度）。 */
    void drawEntity(int cx, int cy, float scale, float yaw, float pitch, Object entity);

    /** 注册与兜底。 */
    final class Host {
        private static volatile LegacyEntityRenderBridge bridge;

        private Host() {
        }

        public static void register(LegacyEntityRenderBridge b) {
            if (b != null) {
                bridge = b;
            }
        }

        public static LegacyEntityRenderBridge current() {
            return bridge;
        }
    }
}