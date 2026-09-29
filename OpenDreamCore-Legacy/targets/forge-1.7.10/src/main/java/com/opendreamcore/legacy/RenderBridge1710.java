package com.opendreamcore.legacy;

import com.opendreamcore.client.render.LegacyRenderBridge;
import com.opendreamcore.client.render.RenderStateSnapshot;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/**
 * 1.7.10 的渲染接入口：状态直接写 GL11。
 *
 * <p>这代还没有 GlStateManager 那层缓存（它是 1.8 才引入的），GL11 就是唯一的状态真相，
 * 所以直接写裸 GL 不会和任何缓存对不上。
 *
 * <p>批次收尾：这代 Tessellator 是单例、draw 即提交，没有攒着的批次，空实现。
 */
public final class RenderBridge1710 implements LegacyRenderBridge {

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
