package com.opendreamcore.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.opendreamcore.client.spi.GlStateBridge;

/**
 * 1.20.1 / 1.21.1 / 1.21.4 三代共用的全局状态桥实现。
 *
 * <p>这三代的 {@code com.mojang.blaze3d.systems.RenderSystem} 状态 API <b>逐字相同</b>
 * （javap 逐条核对过），所以一份源码服务 7 个目标（fabric/neoforge × 三代 + forge-1.20.1）。
 *
 * <p><b>为什么必须是编译期直调：</b>见 {@link GlStateBridge} —— 在 Fabric 生产环境按形状反射
 * 会让 enableBlend() 在 44~53 个 0 参方法里抽签、setShaderTexture(int,int) 命中
 * blendFunc/polygonMode 并真的执行。这里的直接调用会被 loom 重映射到 intermediary，
 * 因此生产环境同样有效，且完全没有抽签空间。
 */
public final class GlStateBridgeImpl implements GlStateBridge {

    @Override
    public boolean hasGlobalState() {
        return true;
    }

    @Override
    public void enableBlend() {
        RenderSystem.enableBlend();
    }

    @Override
    public void disableBlend() {
        RenderSystem.disableBlend();
    }

    @Override
    public void enableCull() {
        RenderSystem.enableCull();
    }

    @Override
    public void disableCull() {
        RenderSystem.disableCull();
    }

    @Override
    public void enableDepthTest() {
        RenderSystem.enableDepthTest();
    }

    @Override
    public void disableDepthTest() {
        RenderSystem.disableDepthTest();
    }

    @Override
    public void depthMask(boolean value) {
        RenderSystem.depthMask(value);
    }

    @Override
    public void defaultBlendFunc() {
        RenderSystem.defaultBlendFunc();
    }

    @Override
    public void blendFuncSeparate(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
    }

    @Override
    public float[] getShaderColor() {
        float[] c = RenderSystem.getShaderColor();
        if (c == null || c.length < 4) {
            return null;
        }
        // 复制一份：调用方要拿它当快照长期持有，不能把 RenderSystem 内部数组的引用交出去。
        return new float[]{c[0], c[1], c[2], c[3]};
    }

    @Override
    public void setShaderColor(float r, float g, float b, float a) {
        RenderSystem.setShaderColor(r, g, b, a);
    }

    @Override
    public Integer getShaderTexture(int sampler) {
        return RenderSystem.getShaderTexture(sampler);
    }

    @Override
    public boolean setShaderTexture(int sampler, int textureId) {
        RenderSystem.setShaderTexture(sampler, textureId);
        return true;
    }
}
