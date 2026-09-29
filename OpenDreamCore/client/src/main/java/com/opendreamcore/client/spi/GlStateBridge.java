package com.opendreamcore.client.spi;

/**
 * 全局 GL / 着色器状态桥 —— 存在的唯一理由：把这些调用从「按签名反射」里救出来。
 *
 * <p><b>实机定根（本接口的由来）：</b>{@code com.mojang.blaze3d.systems.RenderSystem} 上
 * 1.20.1 / 1.21.1 / 1.21.4 各自有 <b>53 / 47 / 44</b> 个 0 参方法、<b>9</b> 个 (int,int) 方法。
 * Fabric 生产环境里 MC 方法名是 intermediary（{@code method_xxxx}），按名字反射必失；
 * 一旦退化成 CompatRender.resolveMethod 的「按参数形状兜底」，就变成按声明顺序抽签：
 * <pre>
 *   enableBlend()           → 在 44~53 个 0 参方法里抽一个（可能抽到 clear 之类改状态的）
 *   setShaderTexture(0,id)  → 命中 blendFunc(int,int) 或 polygonMode(int,int)，
 *                             我们传进去的 (采样器, 纹理id) 被当成 (src,dst) / (face,mode) 真的执行
 *                             → GL_INVALID_ENUM "Invalid destination blending factor"
 *                               / "&lt;mode&gt; is not a valid polygon mode"（单次会话 540 / 2420 条刷屏）
 * </pre>
 *
 * <p>因此这一族调用一律走本桥：实现类放在各 target 里，用<b>编译期直调</b>写（loom 会把引用
 * 重映射成 intermediary，所以生产环境同样有效），彻底不依赖反射。
 *
 * <p>每个现代 target 都必须通过 {@code TargetBridges.glStateBridge()} 提供实现（接口加方法
 * 全部 target 一起编译红，common 的 SyncMatrixTest 还会在构建后反射核对），保证全版本对齐。
 */
public interface GlStateBridge {

    /**
     * 该代是否存在「全局 GL 状态 API」。
     *
     * <p>{@code true}（1.20.1 / 1.21.1 / 1.21.4）：状态是全局的，改了就生效，也必须由我们还原。
     * <p>{@code false}（1.21.8 起）：混合/深度/面剔除改由渲染管线自己声明，这里改了不会生效，
     * 也不该改——改了只会制造「已经设对了」的错觉。
     */
    boolean hasGlobalState();

    /** 开启混合。 */
    void enableBlend();

    /** 关闭混合。 */
    void disableBlend();

    /** 开启面剔除。 */
    void enableCull();

    /** 关闭面剔除。 */
    void disableCull();

    /** 开启深度测试。 */
    void enableDepthTest();

    /** 关闭深度测试。 */
    void disableDepthTest();

    /** 设置深度写入。 */
    void depthMask(boolean value);

    /** 使用原版默认混合函数（SRC_ALPHA, ONE_MINUS_SRC_ALPHA, ONE, ONE_MINUS_SRC_ALPHA）。 */
    void defaultBlendFunc();

    /** 分别设置 RGB 与 Alpha 的混合因子。 */
    void blendFuncSeparate(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha);

    /** 读全局着色器染色；读不到（该代无此 API）返回 null，调用方据此跳过还原。 */
    float[] getShaderColor();

    /** 写全局着色器染色。 */
    void setShaderColor(float r, float g, float b, float a);

    /** 读指定采样器上的贴图 id；读不到返回 null。 */
    Integer getShaderTexture(int sampler);

    /** 绑定贴图到指定采样器；该代无此 API 返回 false。 */
    boolean setShaderTexture(int sampler, int textureId);
}
