package com.opendreamcore.client.render;

/**
 * 世界语义渲染类型提供者：由各目标按自己那一代的 API 造出「世界几何该用哪条渲染类型」。
 *
 * <p>为什么需要它：世界里的全息面板与名牌是插在原版世界渲染流程中间画的。渲染管线重构之后，
 * 「这块几何算哪一类世界内容」不再取决于调用方临时设的 GL 状态，而取决于绘制时用的渲染类型
 * 及其绑定的管线。此前我们用立即模式 + 裸着色器直接送顶点，光影模组（Iris / Oculus）拿不到
 * 可归类的渲染类型，只能按默认类别处理；一旦落进半透明阶段，就与深度缓冲不一致，
 * 表现出来就是物品、生物部分透明、时隐时现。
 *
 * <p>要的语义有四条，缺一不可：<b>不写深度</b>（billboard 不该在深度缓冲留痕，否则会把
 * 水体/粒子/实体挡成半透明）、<b>双面可见</b>（背对相机不能消失）、<b>透明混合</b>
 * （渐变与半透明底要正确叠加）、<b>不吃光照也不吃叠加层</b>（面板不是实体，不该被药水/
 * 受伤叠加染色）。此外顶点格式要分两种：纯色几何用 position_color，贴图几何用 position_tex
 * （position_color 的着色器不采样贴图，硬套贴图格式会因顶点元素不匹配而画不出东西）。
 *
 * <p>三种世代的构造方式完全不同（1.21.8 的工厂是包内可见、1.21.11/26.1.2 是公开的且改叫
 * RenderSetup），所以共享层只认这个接口，具体构造留在各自目标。没有注册实现时共享层回退到
 * 原版世界里本就存在的那条等价类型（盔甲半透明），再不行就完全走原有绘制路径。
 */
public interface WorldRenderTypeProvider {

    /**
     * 取一条世界语义渲染类型。
     *
     * @param textured  顶点是否带 UV（纯色几何 false / 贴图几何 true）
     * @param seeThrough 是否连深度测试也关掉（穿透趟；常规趟 false）
     * @param mode      顶点格式的模式（TRIANGLES / QUADS，直接透传给管线）
     * @param texture   贴图对象（textured=true 时非 null；绑定给管线的采样器）
     * @return 可直接绘制的渲染类型；造不出来返回 null（调用方回退）
     */
    Object resolve(boolean textured, boolean seeThrough, Object mode, Object texture);
}
