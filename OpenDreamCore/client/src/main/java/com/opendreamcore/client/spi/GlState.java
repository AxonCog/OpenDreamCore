package com.opendreamcore.client.spi;

/**
 * {@link GlStateBridge} 的全局注册口与兜底实现。
 *
 * <p>兜底（未注册时）一律「什么都不做」：宁可不动 GL 状态，也不要在拿不准的情况下乱写——
 * 这正是本桥要消灭的那类问题。未注册只可能出现在 legacy（非 TargetBridges）目标上。
 */
public final class GlState {

    /** 未注册时的兜底：全部空操作，读操作返回不可用。 */
    private static final GlStateBridge NOOP = new GlStateBridge() {
        @Override
        public boolean hasGlobalState() {
            return false;
        }

        @Override
        public void enableBlend() {
        }

        @Override
        public void disableBlend() {
        }

        @Override
        public void enableCull() {
        }

        @Override
        public void disableCull() {
        }

        @Override
        public void enableDepthTest() {
        }

        @Override
        public void disableDepthTest() {
        }

        @Override
        public void depthMask(boolean value) {
        }

        @Override
        public void defaultBlendFunc() {
        }

        @Override
        public void blendFuncSeparate(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        }

        @Override
        public float[] getShaderColor() {
            return null;
        }

        @Override
        public void setShaderColor(float r, float g, float b, float a) {
        }

        @Override
        public Integer getShaderTexture(int sampler) {
            return null;
        }

        @Override
        public boolean setShaderTexture(int sampler, int textureId) {
            return false;
        }
    };

    private static volatile GlStateBridge impl = NOOP;

    private GlState() {
    }

    /** 由 BridgeBootstrap 在 target 客户端入口调用一次。 */
    public static void register(GlStateBridge bridge) {
        if (bridge != null) {
            impl = bridge;
        }
    }

    /** 取当前实现（永不返回 null）。 */
    public static GlStateBridge get() {
        return impl;
    }
}
