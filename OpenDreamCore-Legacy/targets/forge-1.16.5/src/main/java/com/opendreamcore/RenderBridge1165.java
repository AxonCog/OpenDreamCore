package com.opendreamcore;

import com.opendreamcore.client.render.LegacyRenderBridge;
import com.opendreamcore.client.render.RenderStateSnapshot;
import com.mojang.blaze3d.systems.RenderSystem;

/**
 * 1.16.5 的渲染接入口：状态写回走 RenderSystem，批次收尾走原版的 BufferSource。
 *
 * <p>写状态为什么不直接写裸 GL：RenderSystem 自己带一层状态缓存，绕过它写 GL 会让缓存和实际
 * 值不一致，之后原版调 enableBlend() 会因为"缓存说已经开了"而什么都不做，状态就修不回来了。
 * 所以这里全程走 RenderSystem。
 *
 * <p>批次收尾：1.16.5 起原版把顶点攒在 BufferSource 的批次里、由渲染流程统一下载。我们的世界
 * 相位挂在帧末回调，可能撞上原版还没收的批次，先请它收掉再用同一个批次画自己的。
 */
public final class RenderBridge1165 implements LegacyRenderBridge {

    /** 这代没有 GL_LIGHTING（光照由核心管线处理），所以快照跳过光照项。 */
    private static final RenderStateSnapshot STATE = new RenderStateSnapshot() {
        @Override
        protected boolean managesLighting() {
            return false;
        }

        @Override
        protected void applyBlend(boolean on, int srcFactor, int dstFactor) {
            if (on) {
                RenderSystem.enableBlend();
                RenderSystem.blendFuncSeparate(srcFactor, dstFactor, srcFactor, dstFactor);
            } else {
                RenderSystem.disableBlend();
            }
        }

        @Override
        protected void applyBlendFactors(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
        }

        @Override
        protected void applyCull(boolean on) {
            if (on) {
                RenderSystem.enableCull();
            } else {
                RenderSystem.disableCull();
            }
        }

        @Override
        protected void applyDepthMask(boolean on) {
            RenderSystem.depthMask(on);
        }

        @Override
        protected void applyLighting(boolean on) {
            // 这代没有这一项，见 managesLighting()
        }
    };

    @Override
    public RenderStateSnapshot state() {
        return STATE;
    }

    @Override
    public void flushPendingBatch() {
        try {
            net.minecraft.client.Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
        } catch (Throwable ignored) {
            // 原版没有待收批次（或这版没有 BufferSource）时不做任何事，绝不让收尾影响渲染
        }
    }
}
