package com.opendreamcore.legacy;

import com.opendreamcore.client.render.LegacyRenderBridge;
import com.opendreamcore.client.render.RenderStateSnapshot;
import net.minecraft.client.renderer.GlStateManager;

/**
 * 1.12.2 的渲染接入口：状态写回走 GlStateManager。
 *
 * <p>这代的 GlStateManager 自己带一层状态缓存，绕过它写裸 GL 会让缓存和实际值不一致——之后原版
 * 调 enableBlend() 会因为"缓存说已经开了"而什么都不做。所以写回全程走 GlStateManager。
 * 混合因子用 tryBlendFuncSeparate：这代的项目代码已经在用它，能一次把 RGB 与 Alpha 四项都写准。
 *
 * <p>批次收尾：这代原版的 Tessellator 是单例、绘制即上传（tess.draw() 就是一次完整提交），
 * 不存在"攒着的批次"，所以这里是空实现。
 */
public final class RenderBridge1212 implements LegacyRenderBridge {

    private static final RenderStateSnapshot STATE = new RenderStateSnapshot() {
        @Override
        protected void applyBlend(boolean on, int srcFactor, int dstFactor) {
            if (on) {
                GlStateManager.enableBlend();
                GlStateManager.tryBlendFuncSeparate(srcFactor, dstFactor, srcFactor, dstFactor);
            } else {
                GlStateManager.disableBlend();
            }
        }

        @Override
        protected void applyBlendFactors(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
        }

        @Override
        protected void applyCull(boolean on) {
            if (on) {
                GlStateManager.enableCull();
            } else {
                GlStateManager.disableCull();
            }
        }

        @Override
        protected void applyDepthMask(boolean on) {
            GlStateManager.depthMask(on);
        }

        @Override
        protected void applyLighting(boolean on) {
            if (on) {
                GlStateManager.enableLighting();
            } else {
                GlStateManager.disableLighting();
            }
        }
    };

    @Override
    public RenderStateSnapshot state() {
        return STATE;
    }

    @Override
    public void flushPendingBatch() {
        // 这代 Tessellator 单例、draw 即提交，没有跨调用攒着的批次要收
    }
}
