package com.opendreamcore.legacy;

import com.opendreamcore.client.render.LegacyRenderBridge;
import com.opendreamcore.client.render.RenderStateSnapshot;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/**
 * 1.6.4 的渲染接入口：状态直接写 GL11（与 1.7.10 同代做法）。
 *
 * <p>这代没有 GlStateManager 缓存层，GL11 就是状态真相，直接写不会和缓存对不上。
 * 批次方面 Tessellator 单例、draw 即提交，没有攒着的批次要收。
 */
public final class RenderBridge164 implements LegacyRenderBridge {

    private static final RenderStateSnapshot STATE = new RenderStateSnapshot() {
        @Override
        protected void applyBlend(boolean on, int srcFactor, int dstFactor) {
            if (on) {
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(srcFactor, dstFactor);
            } else {
                GL11.glDisable(GL11.GL_BLEND);
            }
        }

        @Override
        protected void applyBlendFactors(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
            GL11.glEnable(GL11.GL_BLEND);
            // 分离式混合因子是 GL1.4 的入口，LWJGL2 把它放在 GL14 类里（GL11 没有这个方法）
            GL14.glBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
        }

        @Override
        protected void applyCull(boolean on) {
            if (on) {
                GL11.glEnable(GL11.GL_CULL_FACE);
            } else {
                GL11.glDisable(GL11.GL_CULL_FACE);
            }
        }

        @Override
        protected void applyDepthMask(boolean on) {
            GL11.glDepthMask(on);
        }

        @Override
        protected void applyLighting(boolean on) {
            if (on) {
                GL11.glEnable(GL11.GL_LIGHTING);
            } else {
                GL11.glDisable(GL11.GL_LIGHTING);
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
