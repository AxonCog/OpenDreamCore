package com.opendreamcore.client.bridge;

import com.opendreamcore.client.spi.GlStateBridge;
import com.opendreamcore.client.entity.EntityRenderBridge;
import com.opendreamcore.client.entity.ItemModelRenderBridge;
import com.opendreamcore.client.spi.ResourcePackInjector;

/**
 * 每个现代 target 必须实现的「桥总口」——全版本同步的契约层。
 *
 * 新增任何版本差异能力，就在这里加一个 getter：
 *   - 所有 target 的实现类会在同一轮编译里全部红，漏哪一个立刻看得出来；
 *   - common 的 SyncMatrixTest 还会在构建后反射核对每个 target 的实现类方法齐全，
 *     双保险堵死「有人只给几个版本加了实现」。
 *
 * 实现注意：getter 应返回「已造好」的桥实例（或默认单例），BridgeBootstrap 统一注册。
 */
public interface TargetBridges {

    /** 实体渲染桥（entity/model 组件 GUI 渲染）。 */
    EntityRenderBridge entityBridge();

    /** 物品 3D 展示桥（item_model 组件）。 */
    ItemModelRenderBridge itemModelBridge();

    /** 材质包注入器（托管目录 zip/文件夹/散图装载）。 */
    ResourcePackInjector packInjector();

    /**
     * 全局 GL / 着色器状态桥。
     *
     * <p><b>为什么这一族必须进契约：</b>enableBlend / disableBlend / enableCull / disableCull /
     * enableDepthTest / disableDepthTest / depthMask / defaultBlendFunc / setShaderColor /
     * getShaderColor / setShaderTexture / getShaderTexture 以前全部走 CompatRender.resolveMethod
     * 的「按签名兜底」。而 Fabric 生产环境方法名是 intermediary，按名字必失，兜底就变成按声明顺序
     * 抽签：1.20.1 / 1.21.1 / 1.21.4 的 com.mojang.blaze3d.systems.RenderSystem 上 0 参方法分别有
     * <b>53 / 47 / 44</b> 个、(int,int) 方法有 <b>9</b> 个。实机后果：setShaderTexture(0, 纹理id)
     * 真的调成了 blendFunc(int,int) / polygonMode(int,int)，逐帧刷
     * 「Invalid destination blending factor」/「&lt;mode&gt; is not a valid polygon mode」（单会话 540 / 2420 条）。
     * 改走本桥后由各 target 编译期直调（loom 会把引用重映射成 intermediary，生产环境同样有效），
     * 抽签空间从结构上消失。
     */
    GlStateBridge glStateBridge();
}