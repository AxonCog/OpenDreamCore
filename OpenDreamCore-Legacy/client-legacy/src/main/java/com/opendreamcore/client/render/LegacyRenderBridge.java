package com.opendreamcore.client.render;

/**
 * 渲染接入口：把"改 GL 状态"和"收尾原版批次"这两件必须用本代 API 才能做的事，
 * 交给各 target 注册实现。
 *
 * <p>为什么要有这层：client-legacy 是四代共用的共享树（1.16.5 / 1.12.2 / 1.7.10 / 1.6.4 各自
 * 把它当源码目录编进自己的 jar），共享树编译时看不到任何一代的渲染类——1.16.5 的 RenderSystem、
 * 1.12.2 的 GlStateManager 都不在两千年老版本的 classpath 上。所以共享层只能放"读"的公共算法
 * （{@link RenderStateSnapshot}）与调用时机，真正落笔写状态、以及请求原版先收批次，都由各 target
 * 注册进来。
 *
 * <p>与项目里 ItemPainter/EntityPainter 一个套路：Host 持当前实现，没人注册时退化实现不做事。
 */
public interface LegacyRenderBridge {

    /** 本代的状态快照器（每帧可复用一个实例，capture 会重置）。 */
    RenderStateSnapshot state();

    /**
     * 世界相位开始前，请本代把原版正在攒的批次先收掉。
     *
     * <p>原因：世界全息挂在帧末的世界渲染回调里，此时原版可能已经往共享的顶点批次里塞了几何等着
     * 统一上传（1.16.5 起是 BufferSource 那套）。我们不打招呼就往同一个批次里 begin/end，会把原版
     * 正在攒的内容一起冲掉，表现为实体/粒子偶发缺一块。各代在这里用自己的入口把原版的批次先结束掉，
     * 之后我们再用这个已经空了的批次自己收尾，井水不犯河水。
     */
    void flushPendingBatch();

    /** 注册与兜底。 */
    final class Host {
        private static volatile LegacyRenderBridge current;

        public static void register(LegacyRenderBridge bridge) {
            if (bridge != null) {
                current = bridge;
            }
        }

        public static LegacyRenderBridge current() {
            return current != null ? current : FALLBACK;
        }

        /**
         * 没人注册时：状态一点不碰、批次也不动。
         * 这会让世界面板少一层"混合开、双面、不写深度"的保证，画面可能不对——但比乱写状态好：
         * 错的 GL 状态会污染后续原版几何（正是要修的那个"部分透明"病根），而不碰只会让面板自己不好看。
         */
        private static final LegacyRenderBridge FALLBACK = new LegacyRenderBridge() {
            private final RenderStateSnapshot noop = new RenderStateSnapshot() {
                @Override
                protected void applyBlend(boolean on, int srcFactor, int dstFactor) {
                }

                @Override
                protected void applyCull(boolean on) {
                }

                @Override
                protected void applyDepthMask(boolean on) {
                }

                @Override
                protected void applyLighting(boolean on) {
                }
            };

            @Override
            public RenderStateSnapshot state() {
                return noop;
            }

            @Override
            public void flushPendingBatch() {
            }
        };
    }
}
