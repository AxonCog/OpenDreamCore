package com.opendreamcore.client.spi;

/**
 * 物品真身画笔：item_slot / hot_slot 元素里的物品图标。
 *
 * 规格契约与现代端一致："minecraft:stone x3"——命名空间 id 加空格 x 数量，
 * 不带数量就是 1。老版本没有现代端那套 ItemStack 管线，各版自己建栈、
 * 自己调本时代的物品渲染器；画不了（id 不认识/没进世界）返回 false，
 * 调用方退回格底占位，链路不断。
 *
 * 尺寸合同：x/y 是格子左上角（逻辑像素），size 是格边长；物品贴图按
 * 16px 基准画在格子中央，数量角标由实现方按现代端样式补。
 */
public interface ItemPainter {

    /** 画一个物品图标（带数量角标）。返回 false = 画不了，调用方上占位。 */
    boolean render(String itemSpec, double x, double y, double size, double alpha);

    /** 画玩家快捷栏第 slot 格（0 起）的实况物品；没进世界返回 false。 */
    boolean renderHotbar(int slotIndex, double x, double y, double size, double alpha);

    /** 注册与兜底，跟 EntityPainter 一个套路。 */
    final class Host {
        private static volatile ItemPainter current;

        public static void register(ItemPainter painter) {
            if (painter != null) {
                current = painter;
            }
        }

        public static ItemPainter current() {
            return current != null ? current : FALLBACK;
        }

        /** 没人注册时：画不了，调用方画格底占位。 */
        private static final ItemPainter FALLBACK = new ItemPainter() {
            @Override
            public boolean render(String itemSpec, double x, double y, double size, double alpha) {
                return false;
            }

            @Override
            public boolean renderHotbar(int slotIndex, double x, double y, double size, double alpha) {
                return false;
            }
        };
    }
}
