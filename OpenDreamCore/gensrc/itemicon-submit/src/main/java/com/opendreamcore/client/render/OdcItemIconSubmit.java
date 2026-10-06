package com.opendreamcore.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.opendreamcore.client.resources.LooseResourceLoader;
import com.opendreamcore.client.visual.VisualItemSkins;
import com.opendreamcore.mixin.WorldRenderTypeMixin;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ItemIcon 在 submit 管线里的实现（1.21.11 与 26.1.2 共用一份）。
 *
 * <p>这两代没有「拿得到 ItemStack 的即时绘制入口」了：物品先进渲染状态再统一提交。
 * 所以拦在持有 ItemStack 的提交点（手持层 / 手持渲染器），命中规则就改成提交
 * 我们自己的一张贴图四边形并 cancel 掉原版提交；没命中原版一行不改。
 *
 * <p>渲染类型照抄 worldrender-1.21.5+ 那套已验证的做法：装配描述只设贴图，
 * 其余语义（深度测试、面剔除、混合、顶点格式）全部来自自建管线；渲染类型一旦建成
 * 采样器就钉死在那张贴图上，所以按贴图缓存。
 */
public final class OdcItemIconSubmit {

    /** 卡片边长（世界单位），与原版物品 1×1 对齐。 */
    private static final float SIZE = 0.5F;

    private static final Map<Identifier, RenderType> CACHE = new ConcurrentHashMap<>();

    private OdcItemIconSubmit() {
    }

    /** 命中规则就提交自定义几何并返回 true（调用方 cancel）；没命中返回 false。 */
    public static boolean submit(ItemStack stack, PoseStack pose, SubmitNodeCollector collector) {
        if (stack == null || stack.isEmpty() || pose == null || collector == null) {
            return false;
        }
        String id;
        try {
            id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        } catch (Throwable t) {
            return false;
        }
        String tex = VisualItemSkins.textureFor(id, stack.getHoverName().getString());
        if (tex == null) {
            return false;
        }
        LooseResourceLoader.SheetInfo sheet = LooseResourceLoader.sheetOf(tex);
        if (sheet == null) {
            return false;
        }
        try {
            RenderType rt = renderTypeFor(sheet.rl());
            if (rt == null) {
                return false;
            }
            float frames = Math.max(1.0F, sheet.frames());
            final float u0 = (sheet.frame() * sheet.frameW()) / (float) (sheet.frameW() * frames);
            final float u1 = u0 + 1.0F / frames;
            collector.submitCustomGeometry(pose, rt, (poseM, consumer) -> {
                consumer.addVertex(poseM, -SIZE, -SIZE, 0).setUv(u0, 0).setColor(1.0F, 1.0F, 1.0F, 1.0F);
                consumer.addVertex(poseM, SIZE, -SIZE, 0).setUv(u1, 0).setColor(1.0F, 1.0F, 1.0F, 1.0F);
                consumer.addVertex(poseM, SIZE, SIZE, 0).setUv(u1, 1).setColor(1.0F, 1.0F, 1.0F, 1.0F);
                consumer.addVertex(poseM, -SIZE, SIZE, 0).setUv(u0, 1).setColor(1.0F, 1.0F, 1.0F, 1.0F);
            });
            return true;
        } catch (Throwable t) {
            // 提交失败就放行原版：物品渲染断掉比没替换严重得多
            return false;
        }
    }

    /** 按贴图缓存渲染类型（渲染类型建成后采样器钉死，不能改绑）。 */
    private static RenderType renderTypeFor(Identifier tex) {
        RenderType cached = CACHE.get(tex);
        if (cached != null) {
            return cached;
        }
        // 直接用世界贴图管线：它声明的顶点格式（带UV带色）、混合、深度测试与面剔除
        // 对物品卡片同样成立——物品卡片就是要「贴在原版变换里的一张带贴图四边形」。
        RenderSetup setup = RenderSetup.builder(OdcWorldRenderPipelines.WORLD_TEXTURED)
                .withTexture("Sampler0", tex)
                .createRenderSetup();
        RenderType rt = WorldRenderTypeMixin.odc$create("opendreamcore_item_"
                + tex.getNamespace() + "_" + tex.getPath().replace('/', '_').replace('.', '_'), setup);
        if (CACHE.size() >= 128) {
            CACHE.clear();
        }
        CACHE.put(tex, rt);
        return rt;
    }
}