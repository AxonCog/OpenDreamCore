package com.opendreamcore.client.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.opendreamcore.mixin.WorldRenderPipelinesMixin;
import net.minecraft.resources.Identifier;

/**
 * 自建的世界语义渲染管线（26.1.2 专用；1.21.11 用 worldrender-pipe-12111 那一份）。
 *
 * <p>从 1.21.6 起，「这块世界几何算哪一类内容」不再由绘制前临时设的 GL 状态决定，而由绘制时用的
 * 渲染管线声明。光影模组以管线为分类单位重绘世界，所以只要面板还走立即模式加裸着色器，它在光影眼里
 * 就没有类别，只能按默认处理——落进半透明阶段就会把深度缓冲写乱，表现成物品、生物部分透明。
 *
 * <p>这条路自己声明了一条管线，四个语义逐项写死、不依赖任何原版上下文：
 * <ul>
 *   <li><b>不写深度</b>：billboard 不该在深度缓冲留痕，否则会把之后的水体、粒子、实体挡成
 *       半透明——这正是要修的症状本身。这一代把它表达在深度模板状态的第二个参数上。</li>
 *   <li><b>双面可见</b>（{@code withCull(false)}）：面板背对相机时不能整片消失。</li>
 *   <li><b>透明混合</b>：色彩目标状态里给 TRANSLUCENT，渐变底与半透明图标才能正确叠加。</li>
 *   <li><b>不写叠加层</b>：这里没有用 {@code useOverlay()}，面板因而不会吃到受伤/药水的叠加染色
 *       （面板是信息层，不是实体）。</li>
 * </ul>
 *
 * <p>深度测试保留且用「小于等于」比较，这是最容易搞错的一处：整条管线如果连深度测试一起关掉
 * （原版 GUI 系管线就是这么做的），面板会穿墙显示；我们要的是「照常被测深度挡住，但不留下自己的
 * 深度」。
 *
 * <p>这一代不再有 withBlend / withDepthWrite 这类单个开关，混合与深度写入被合并进两个状态记录：
 * 色彩目标状态（混合 + 写入哪些色彩通道）与深度模板状态（比较函数 + 是否写深度 + 深度偏移）。
 * 所以这里换用 withColorTargetState / withDepthStencilState 表达同样的语义，语义本身与 1.21.11
 * 那份逐一对应，只是写法跟着这一代的建模走。
 *
 * <p>着色器复用原版 core 里已有的 position_tex_color：它做的就是「贴图 × 顶点色」，正好等于
 * 面板需要的效果，不必自带着色器（少一份着色器就少一处要跟着版本维护的东西）。顶点格式用
 * position_tex_color 而非 position_tex，是为了把透明度放到顶点上——顶点色是随几何走的，
 * 不像全局着色器染色那样需要额外复位，异常中断也不会泄漏到后续几何上。
 *
 * <p>两个 uniform 块是这个着色器的输入契约（{@code DynamicTransforms} 与 {@code Projection}，
 * 由渲染系统按帧上传），必须如实声明，否则着色器拿不到矩阵画不到屏幕上。
 */
public final class OdcWorldRenderPipelines {

    /** 世界贴图面板：位置 + UV + 顶点色。 */
    /** 世界贴图面板：位置 + UV + 顶点色（照常测深度）。 */
    public static final RenderPipeline WORLD_TEXTURED = buildWorldTextured(false);

    /**
     * 同一管线的穿透变体：把深度测试整条关掉。
     *
     * <p>用于 {@code depthMode: always} 与 transparent 模式的第二遍。这条路原先刻意不走渲染类型，
     * 理由是「渲染管线的构建器只能设写不写深度，不提供深度测试函数」——实测不成立：1.21.8 / 1.21.11 的
     * {@code DepthTestFunction.NO_DEPTH_TEST}、26.1.2 的 {@code CompareOp.ALWAYS_PASS} 都在，
     * 本类本来就一直在调 {@code withDepthTestFunction}。改由管线表达之后穿透趟才真的生效：那些版本的
     * {@code disableDepthTest()} 已经是空操作（窗口期 1.21.6 起状态 API 撤走），继续走老路等于没做。
     */
    public static final RenderPipeline WORLD_TEXTURED_NO_DEPTH = buildWorldTextured(true);

    private OdcWorldRenderPipelines() {
    }

    private static RenderPipeline buildWorldTextured(boolean noDepth) {
        // 透明混合 + 写全部色彩通道（单参构造即「写全部」，与原版其它管线的取值一致）
        ColorTargetState colorTarget = new ColorTargetState(BlendFunction.TRANSLUCENT);
        // 「小于等于」比较保留深度遮挡，但不写深度（第二个参数 false）
        // 常规趟：LESS_THAN_OR_EQUAL 保留遮挡，但不写深度（第二个参数 false）；
        // 穿透趟：ALWAYS_PASS 等于整趟不做深度测试，面板重新穿墙可见。
        DepthStencilState depthStencil = new DepthStencilState(
                noDepth ? CompareOp.ALWAYS_PASS : CompareOp.LESS_THAN_OR_EQUAL, false);

        return RenderPipeline.builder()
                .withLocation(Identifier.fromNamespaceAndPath("opendreamcore",
                        noDepth ? "world_textured_no_depth" : "world_textured"))
                .withVertexShader("core/position_tex_color")
                .withFragmentShader("core/position_tex_color")
                .withSampler("Sampler0")
                // 着色器声明的两个 uniform 块，名字与着色器里一致
                .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                .withColorTargetState(colorTarget)
                .withDepthStencilState(depthStencil)
                .withCull(false)
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
                .build();
    }

    /**
     * 登记自建管线（Fabric 侧路径：由 {@code RenderPipelines} 静态初始化末尾的注入调用）。
     *
     * <p>登记不是可选项：原版那张静态管线表同时充当「启动时编译哪些着色器」的清单，不登记就等于
     * 声明了一条没人给它编译程序的管线，绘制时拿到的是空管线。
     *
     * <p>NeoForge 侧不走这里——那边有官方的管线注册事件（见目标内的 {@code ClientEvents}），
     * 由事件把同一条管线登记进去，比注入原版静态初始化更稳。
     */
    public static void registerAll() {
        WorldRenderPipelinesMixin.odc$register(WORLD_TEXTURED);
        WorldRenderPipelinesMixin.odc$register(WORLD_TEXTURED_NO_DEPTH);
    }
}
