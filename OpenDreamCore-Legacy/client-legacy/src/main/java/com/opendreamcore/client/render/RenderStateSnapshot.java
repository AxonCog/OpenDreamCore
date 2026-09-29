package com.opendreamcore.client.render;

import org.lwjgl.opengl.GL11;

/**
 * 世界画布的 GL 状态快照：进画布前把当前值读下来，退出时按读到的原值写回。
 *
 * <p>为什么需要它：世界里的面板要"混合开、背面剔除关、只测深度不写深度"才画得对，而这段绘制
 * 是插在原版世界渲染流程中间跑的。以前的做法是进入时改状态、退出时写死一组固定值
 * （深度写开、恢复面剔除、关混合），写死值多数情况下恰好与原版一致，可一旦进入前原版本来就处于
 * 别的状态（典型是半透明阶段原版自己把深度写关了），退出时我们就把深度写入永久打开，后续原版
 * 半透明几何和深度缓冲对不上，表现出来就是物品、生物"部分透明"。所以改成读原值、写原值。
 *
 * <p>读原值这条路四代是同一条：都是 LWJGL 的 GL11，方法名与常量在 LWJGL2（1.12.2/1.7.10/1.6.4）
 * 与 LWJGL3（1.16.5）里一致，所以快照与还原的算法、以及"读"的部分放在共享层只有一份。
 * 写回则必须交给各代自己的写入层：1.16.5 走 RenderSystem、1.12.2 走 GlStateManager，
 * 这两代的状态对象各自带缓存，绕过它们直接写裸 GL 会让缓存与实际值不一致——
 * 之后原版调 enableBlend() 会因为"缓存说已经开了"而什么都不做，状态就再也修不回来。
 * 1.7.10/1.6.4 没有这层缓存，直接写 GL11。
 *
 * <p>各 target 派生一个子类，只实现那几个"写"的方法；快照、比较、还原顺序都在这里，
 * 不会出现四份各自漂移的实现。
 */
public abstract class RenderStateSnapshot {

    /** GL_BLEND。 */
    private static final int GL_BLEND = 0x0BE2;
    /** GL_DEPTH_WRITEMASK：只能用 glGetBoolean 读。 */
    private static final int GL_DEPTH_WRITEMASK = 0x0B72;
    /** GL_CULL_FACE。 */
    private static final int GL_CULL_FACE = 0x0B44;
    /** GL_LIGHTING：老三代要还原，1.16.5 起没有这项。 */
    private static final int GL_LIGHTING = 0x0B50;
    /** 混合因子四项（GL1.4 的 RGB/Alpha 分离形式）。 */
    private static final int GL_BLEND_SRC_RGB = 0x80C9;
    private static final int GL_BLEND_DST_RGB = 0x80C8;
    private static final int GL_BLEND_SRC_ALPHA = 0x80CB;
    private static final int GL_BLEND_DST_ALPHA = 0x80CA;

    /** GL_SRC_ALPHA / GL_ONE_MINUS_SRC_ALPHA：画布用的标准透明混合。 */
    private static final int GL_SRC_ALPHA = 0x0302;
    private static final int GL_ONE_MINUS_SRC_ALPHA = 0x0303;

    private boolean captured;
    private boolean blend;
    private boolean depthMask;
    private boolean cull;
    private boolean lighting;
    private int srcRgb;
    private int dstRgb;
    private int srcAlpha;
    private int dstAlpha;

    /**
     * 本代是否要管光照开关。1.16.5 起原版去掉了 GL_LIGHTING（核心管线自己处理光照），
     * 读那个常量会拿到无意义的 0 并可能刷 GL 错误，所以由子类声明跳过。
     */
    protected boolean managesLighting() {
        return true;
    }

    /** 写：混合开关 + 四个因子。 */
    protected abstract void applyBlend(boolean on, int srcFactor, int dstFactor);

    /** 写：背面剔除开关。 */
    protected abstract void applyCull(boolean on);

    /** 写：深度写入开关。 */
    protected abstract void applyDepthMask(boolean on);

    /** 写：光照开关（{@link #managesLighting()} 为 false 时不会被调用）。 */
    protected abstract void applyLighting(boolean on);

    /** 进入画布前调用：把要动的那几项原值读下来。读失败就放弃还原（宁可不碰，也不写错值）。 */
    public final void capture() {
        try {
            blend = GL11.glGetBoolean(GL_BLEND);
            depthMask = GL11.glGetBoolean(GL_DEPTH_WRITEMASK);
            cull = GL11.glGetBoolean(GL_CULL_FACE);
            srcRgb = GL11.glGetInteger(GL_BLEND_SRC_RGB);
            dstRgb = GL11.glGetInteger(GL_BLEND_DST_RGB);
            srcAlpha = GL11.glGetInteger(GL_BLEND_SRC_ALPHA);
            dstAlpha = GL11.glGetInteger(GL_BLEND_DST_ALPHA);
            if (managesLighting()) {
                lighting = GL11.glGetBoolean(GL_LIGHTING);
            }
            captured = true;
        } catch (Throwable t) {
            captured = false;
        }
    }

    /**
     * 进入画布：透明混合 + 双面 + 只测深度不写深度（面板会被墙挡，但不写深度去挡后面画的东西）。
     * 深度测试本身不动——原版留给它的值就是我们要的。
     */
    public final void enterHolo() {
        applyBlend(true, GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        applyCull(false);
        applyDepthMask(false);
        if (managesLighting()) {
            applyLighting(false);
        }
    }

    /**
     * 退出画布：按进入前读到的原值逐项写回。{@link #capture()} 没成功就什么都不做
     * （读不到原值就不该猜，让状态维持现状比写错值安全）。
     * 深度测试从头到尾没动过，所以这里也不碰它。
     */
    public final void restore() {
        if (!captured) {
            return;
        }
        if (blend) {
            // RGB 与 Alpha 的因子在 GL1.4 之后是分开的，逐项写回才是准确还原
            applyBlendFactors(srcRgb, dstRgb, srcAlpha, dstAlpha);
        } else {
            applyBlend(false, srcRgb, dstRgb);
        }
        applyCull(cull);
        applyDepthMask(depthMask);
        if (managesLighting()) {
            applyLighting(lighting);
        }
    }

    /**
     * 写：分离式混合因子。默认退回统一因子（拿 RGB 那对）；
     * 有分离式入口的代覆盖本方法，还原才是逐项准确的。
     */
    protected void applyBlendFactors(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        applyBlend(true, srcRgb, dstRgb);
    }
}
