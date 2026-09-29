package com.opendreamcore.client;

import com.opendreamcore.client.spi.GlStateBridge;

/**
 * 1.21.8 / 1.21.11 / 26.1.2 的全局状态桥实现：<b>全部空实现</b>。
 *
 * <p>这三代里 Mojang 把 enableBlend / disableBlend / enableCull / disableCull /
 * enableDepthTest / disableDepthTest / depthMask / defaultBlendFunc / setShaderColor /
 * getShaderColor 从 RenderSystem 上撤掉了（javap 核对：命中 0），状态改由每个渲染管线自己声明。
 * 也就是说：<b>这些状态在高版本是「不该手动改」的</b>——改了既不生效，还会制造「已经设对了」的错觉。
 * 所以这里的正确实现就是什么都不做。
 *
 * <p>刻意不引用任何版本专属 API（连 1.21.8 尚存的 getShaderTexture/setShaderTexture 也不碰），
 * 这样同一份源码能在三代上编译通过、行为一致。
 */
public final class GlStateBridgeImpl implements GlStateBridge {

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
}
