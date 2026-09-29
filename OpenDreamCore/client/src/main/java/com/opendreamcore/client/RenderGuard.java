package com.opendreamcore.client;

import com.opendreamcore.client.spi.GlState;
import com.opendreamcore.client.spi.GlStateBridge;
import org.lwjgl.opengl.GL11;

/**
 * 世界渲染状态守卫：进入时真快照，退出时真还原。
 *
 * <p>为什么需要它：世界里的全息面板要"混合开启 + 双面 + 只测深度不写深度"才能正确显示，
 * 但这段绘制是插在原版世界渲染流程中间跑的。以前的做法是进入时改状态、退出时写死一组固定值
 * （深度写开、恢复面剔除、关混合）。写死值在多数情况下恰好与原版一致，可一旦进入前原版本来就
 * 处于别的状态（典型是半透明阶段原版自己把深度写关了），退出时我们就把深度写永久打开，
 * 后续原版半透明几何与深度缓冲不一致，表现出来就是物品、生物"部分透明"、时隐时现。
 *
 * <p>所以这里改成：进入前把每一项原值读出来存下，退出时按存下的值逐项写回。异常路径也必须还原
 * （见 {@link #close()}），否则一次绘制异常就会让 GL 状态永久跑偏。
 *
 * <p>另一个关键点：1.21.6 起 Mojang 把混合、深度、面剔除这些状态从 RenderSystem 上撤掉了，
 * 改为由每个渲染管线自己声明。这些方法在那之后不存在，反射调用会静默失败——也就是说在老版本上
 * "把状态改对"这件事本身在高版本是做不到的（也不该做：管线会在每次绘制时自己设）。
 * 因此本守卫按"状态 API 是否存在"分两条路：存在则真快照真还原；不存在则不动 GL 状态，
 * 但仍还原那些跨版本依然是全局的东西（着色器染色、模型视图栈深度），避免留下假的"已还原"印象。
 *
 * <p>用法固定成 try-with-resources，保证异常也走还原：
 * <pre>
 *   try (RenderGuard guard = RenderGuard.enterWorldHolo()) {
 *       ... 世界绘制 ...
 *   }
 * </pre>
 */
public final class RenderGuard implements AutoCloseable {

    private static final org.slf4j.Logger LOGGER =
            org.slf4j.LoggerFactory.getLogger("OpenDreamCore");

    /** GL_BLEND。 */
    private static final int GL_BLEND = 0x0BE2;
    /** GL_CULL_FACE。 */
    private static final int GL_CULL_FACE = 0x0B44;
    /** GL_DEPTH_TEST。 */
    private static final int GL_DEPTH_TEST = 0x0B71;
    /** GL_DEPTH_WRITEMASK（glGetBoolean 专用 pname，不能用 glGetIntegerv 读）。 */
    private static final int GL_DEPTH_WRITEMASK = 0x0B72;
    private static final int GL_BLEND_SRC_RGB = 0x80C9;
    private static final int GL_BLEND_DST_RGB = 0x80C8;
    private static final int GL_BLEND_SRC_ALPHA = 0x80CB;
    private static final int GL_BLEND_DST_ALPHA = 0x80CA;

    /** GL_SRC_ALPHA_SATURATE：只允许当源因子，出现在 dst 位就是 GL_INVALID_ENUM。 */
    private static final int GL_SRC_ALPHA_SATURATE = 0x0308;

    /** 合法的混合因子枚举（GL 规定，含仅源合法的 SATURATE 与 GL_CONSTANT_*）。 */
    private static final java.util.Set<Integer> VALID_BLEND_FACTORS = java.util.Set.of(
            0x0000, // GL_ZERO
            0x0001, // GL_ONE
            0x0300, // GL_SRC_COLOR
            0x0301, // GL_ONE_MINUS_SRC_COLOR
            0x0302, // GL_SRC_ALPHA
            0x0303, // GL_ONE_MINUS_SRC_ALPHA
            0x0304, // GL_DST_ALPHA
            0x0305, // GL_ONE_MINUS_DST_ALPHA
            0x0306, // GL_DST_COLOR
            0x0307, // GL_ONE_MINUS_DST_COLOR
            0x0308, // GL_SRC_ALPHA_SATURATE
            0x8001, // GL_CONSTANT_COLOR
            0x8002, // GL_ONE_MINUS_CONSTANT_COLOR
            0x8003, // GL_CONSTANT_ALPHA
            0x8004  // GL_ONE_MINUS_CONSTANT_ALPHA
    );

    /** 非法混合因子的告警只打一次（否则每帧刷屏）。 */
    private static final java.util.concurrent.atomic.AtomicBoolean BLEND_SKIP_LOGGED
            = new java.util.concurrent.atomic.AtomicBoolean(false);

    /**
     * 状态 API 一律通过 {@link GlStateBridge} 走编译期直调，本类不再持有任何反射 Method。
     *
     * <p>曾经的写法：用 CompatRender.resolveMethod 反射解析 depthMask / enableBlend / … 共 12 个方法，
     * 并用 {@code DEPTH_MASK != null} 当「本代有没有全局状态 API」的分界标。这在 Fabric 生产环境里是错的——
     * 那里的 MC 方法名是 intermediary，按名必失，兜底退化成在同形方法里按声明顺序抽签：
     * 该类的 0 参方法在 1.20.1/1.21.1/1.21.4 上各有 53/47/44 个、(int,int) 方法有 9 个，
     * 于是 {@code setShaderTexture(0, 纹理id)} 真的被当成 blendFunc(src,dst) / polygonMode(face,mode) 执行，
     * 逐帧刷 GL_INVALID_ENUM（实测单次会话 540 / 2420 条）。
     *
     * <p>现在：{@link GlStateBridge#hasGlobalState()} 是各 target 用编译期常量/直调给出的确定答案，
     * 其余每个状态动作也都是直调，没有抽签空间。
     */

    /** 本世代状态是否由我们负责还原（false = ≥1.21.6，管线声明，勿动）。 */
    private final boolean globalState;
    /** 进入前的模型视图栈深度（所有世代都是全局的，必须还原）。 */
    private final int savedStackDepth;
    /** 进入前的着色器染色（null = 该世代读不到，退出时不还原）。 */
    private final float[] savedColor;
    /** 进入前绑定在 0 号采样器上的贴图（null = 读不到）。 */
    private final Object savedTexture;

    private final boolean savedBlend;
    private final boolean savedCull;
    private final boolean savedDepthTest;
    private final boolean savedDepthMask;
    private final int[] savedBlendFactors;

    private boolean closed;

    private RenderGuard(boolean globalState, int savedStackDepth, float[] savedColor, Object savedTexture,
                        boolean savedBlend, boolean savedCull, boolean savedDepthTest, boolean savedDepthMask,
                        int[] savedBlendFactors) {
        this.globalState = globalState;
        this.savedStackDepth = savedStackDepth;
        this.savedColor = savedColor;
        this.savedTexture = savedTexture;
        this.savedBlend = savedBlend;
        this.savedCull = savedCull;
        this.savedDepthTest = savedDepthTest;
        this.savedDepthMask = savedDepthMask;
        this.savedBlendFactors = savedBlendFactors;
    }

    /**
     * 进入"世界全息"渲染状态：开混合、用原版默认混合函数、关面剔除、只测深度不写深度。
     * 返回的守卫在 close 时把进入前的原值逐项写回。
     */
    public static RenderGuard enterWorldHolo() {
        GlStateBridge gl = GlState.get();
        boolean globalState = gl.hasGlobalState();

        // 先快照，再改状态——顺序不能反，否则存下来的是我们自己刚设的值，还原就成了"还原成自己"。
        int stackDepth = CompatRender.modelViewStackDepth();
        float[] color = gl.getShaderColor();
        Object texture = gl.getShaderTexture(0);
        boolean blend = readBool(GL_BLEND, true);
        boolean cull = readBool(GL_CULL_FACE, true);
        boolean depthTest = readBool(GL_DEPTH_TEST, true);
        boolean depthMask = readBool(GL_DEPTH_WRITEMASK, true);
        int[] factors = readBlendFactors();

        if (globalState) {
            gl.enableBlend();
            gl.defaultBlendFunc();
            gl.disableCull();
            gl.depthMask(false);
        }
        // globalState=false 时刻意什么都不设：状态由渲染管线声明，这里改了也不会生效，
        // 改了反而制造"已经设对了"的错觉。
        //
        // 正因如此，高版本（≥1.21.6，此时 globalState=false）光靠这里改状态是不够的：
        // 世界几何得改走渲染类型管线，才能在光影模组眼里被正确归类。这里顺手打开世界绘制
        // 窗口，窗口内建立的一切顶点批次都会挂上世界语义渲染类型（不写深度/双面/透明）。
        // 挂在守卫里而不是各绘制入口，是因为世界里一共三处入口，而它们全部成对进出本守卫——
        // 一处开关就全覆盖，也不会因为以后新增入口而漏掉。
        com.opendreamcore.client.render.WorldRenderTypeDispatch.enterWorldDraw();

        return new RenderGuard(globalState, stackDepth, color, texture,
                blend, cull, depthTest, depthMask, factors);
    }

    /**
     * 按进入前的原值逐项还原。可以重复调用（第二次起为空操作）。
     * 每一项都单独兜异常：一项还原失败不能让后面的项也不还原，更不能把异常抛回渲染帧。
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;

        if (globalState) {
            GlStateBridge gl = GlState.get();
            if (savedBlend) {
                gl.enableBlend();
            } else {
                gl.disableBlend();
            }
            // 混合因子必须在混合开关之后写回：GL 对已关闭的混合仍保留因子，
            // 先写因子再开混合会让这一帧的这一段用错因子。
            if (savedBlendFactors != null && blendFactorsWritable(savedBlendFactors)) {
                gl.blendFuncSeparate(savedBlendFactors[0], savedBlendFactors[1],
                        savedBlendFactors[2], savedBlendFactors[3]);
            }
            if (savedDepthTest) {
                gl.enableDepthTest();
            } else {
                gl.disableDepthTest();
            }
            gl.depthMask(savedDepthMask);
            if (savedCull) {
                gl.enableCull();
            } else {
                gl.disableCull();
            }
        }

        if (savedColor != null) {
            // 值是我们自己存下来的原值（不是编造的），直接写回。
            GlState.get().setShaderColor(savedColor[0], savedColor[1], savedColor[2], savedColor[3]);
        }
        restoreShaderTexture(savedTexture);

        // 模型视图栈：把我们压进去的层全部弹掉。这是最要紧的一项——多压一层，
        // 后面所有原版几何都会跟着位移/旋转，观感上就是整个世界错位。
        CompatRender.modelViewRestoreTo(savedStackDepth);

        // 退出世界绘制窗口必须放在最后：本方法整体承诺「可重复调用」，而窗口标记是线程局部的，
        // 漏清会让窗口外的 UI/字体绘制也被当成世界几何，那批顶点格式与类型对不上就会画不出来。
        // 放在所有还原动作之后，异常路径也一定走到这里。
        com.opendreamcore.client.render.WorldRenderTypeDispatch.exitWorldDraw();
    }

    // ── 读取原值：只信 GL 的实际状态，不猜 ─────────────────────────────────────

    private static boolean readBool(int pname, boolean fallback) {
        try {
            return GL11.glGetBoolean(pname);
        } catch (Throwable t) {
            return fallback;
        }
    }

    private static int[] readBlendFactors() {
        try {
            return new int[]{
                    GL11.glGetInteger(GL_BLEND_SRC_RGB),
                    GL11.glGetInteger(GL_BLEND_DST_RGB),
                    GL11.glGetInteger(GL_BLEND_SRC_ALPHA),
                    GL11.glGetInteger(GL_BLEND_DST_ALPHA),
            };
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 写回混合因子前的合法性校验。
     *
     * <p>为什么必须校验：这四个值是 {@code glGetIntegerv} 读回来的「当时状态」，而写回走的是
     * {@code glBlendFuncSeparate}。GL 对目标因子有一处特殊规定——{@code GL_SRC_ALPHA_SATURATE}
     * 只允许当<b>源</b>因子，一旦它出现在 dst 位就是 GL_INVALID_ENUM，驱动日志里逐帧刷
     * 「Invalid destination blending factor」。而读回来的值未必可写回：上一手可能是光影模组 /
     * 资源包着色器留下的，某些驱动也会把没设过的状态回读成脏值。与其写一个非法值把 GL 错误
     * 注入渲染帧（错误本身又会干扰后续状态判断），不如放弃这一项还原：混合因子本就是原版
     * 每次绘制自己会设的状态，不还原的代价远小于注入错误。校验不过只记一次日志，不刷屏。
     */
    private static boolean blendFactorsWritable(int[] factors) {
        for (int i = 0; i < 4; i++) {
            int v = factors[i];
            boolean dstSlot = (i == 1 || i == 3);
            if (!VALID_BLEND_FACTORS.contains(v) || (dstSlot && v == GL_SRC_ALPHA_SATURATE)) {
                if (BLEND_SKIP_LOGGED.compareAndSet(false, true)) {
                    StringBuilder all = new StringBuilder();
                    for (int x : factors) {
                        all.append("0x").append(Integer.toHexString(x)).append(' ');
                    }
                    LOGGER.warn("[ODC-render] 混合因子原值不可写回（0x{} 在 {} 位），已跳过该项还原"
                            + "以免注入 GL_INVALID_ENUM: {}", Integer.toHexString(v),
                            dstSlot ? "dst" : "src", all.toString().trim());
                }
                return false;
            }
        }
        return true;
    }

    /**
     * 还原 0 号采样器上的贴图。
     *
     * <p><b>这里曾经是全仓最贵的一个 bug：</b>老实现用
     * {@code CompatRender.resolveMethod(RenderSystem.class, "setShaderTexture", int.class, int.class)}，
     * 而 Fabric 生产环境方法名是 intermediary —— 按名必失后兜底按声明顺序挑同形方法。
     * 1.21.1 的 RenderSystem 上共有 <b>9</b> 个 (int,int) 方法：
     * {@code blendFunc}(第 57 行) → {@code polygonMode}(第 63 行) → … →
     * {@code setShaderTexture(int,int)}（第 149 行，几乎垫底），于是我们传的 (采样器 0, 纹理 id)
     * 被当成 (src,dst) 混合因子或 (face,mode) 多边形模式被<b>真的执行</b>：
     * <pre>
     *   GL_INVALID_ENUM … Invalid destination blending factor.   （命中 blendFunc 时）
     *   GL_INVALID_ENUM … &lt;mode&gt; is not a valid polygon mode.      （命中 polygonMode 时）
     * </pre>
     * 每帧刷屏（实测单次会话 540 条 / 2420 条两种形态，就是同一处调用点的两种 JVM 方法顺序）。
     *
     * <p>现在走 {@link GlStateBridge#setShaderTexture}：编译期直调，无抽签空间；
     * 该代没有全局贴图 API（1.21.11 起）时桥返回 false，我们跳过还原——原版每个渲染类型每次绘制
     * 都会自己重新绑定贴图，不还原没有代价。
     */
    private void restoreShaderTexture(Object texture) {
        if (!(texture instanceof Integer)) {
            return; // 没快照到（该代读不到）：不动，避免写一个编造的值
        }
        GlState.get().setShaderTexture(0, (Integer) texture);
    }
}
