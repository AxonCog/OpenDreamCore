package com.opendreamcore.client.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import com.opendreamcore.client.CompatRender;
import com.opendreamcore.client.resources.LooseResourceLoader;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

/**
 * ItemIcon 的世界侧替换：第一人称手持、第三人称手臂、掉落物实体、投掷物。
 *
 * <p>GUI 侧（{@code MixinItemIcon}）走的是 blit 图标路径；世界侧没有 blit 可用，这里直接
 * 在原版物品渲染已经摆好的变换里画一张贴图卡片并取消原版绘制——尺寸取原版物品的 1×1，
 * 位置与朝向完全跟随原版变换（手持就是贴在手上、掉落物就是躺在地上）。
 *
 * <p>规则命中才画；没命中返回 false，原版一模一样地跑（缺席即安全，不会误伤任何物品）。
 */
public final class ItemIconWorld {

    /** 世界侧卡片边长（原版物品模型也是 1×1 世界单位，直接对齐）。 */
    private static final float SIZE = 0.5F;

    private ItemIconWorld() {
    }

    /**
     * 命中规则就画贴图卡片并返回 true（调用方取消原版绘制）；没命中返回 false。
     */
    public static boolean tryDraw(ItemStack stack, PoseStack pose, MultiBufferSource buffers,
                                  int light, int overlay) {
        if (stack == null || stack.isEmpty() || pose == null || buffers == null) {
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
        if (MATCHED.add(tex)) {
            com.opendreamcore.client.OdcDebug.log("[ODC-icon] 规则命中: " + id + " → " + tex);
        }
        LooseResourceLoader.SheetInfo sheet = LooseResourceLoader.sheetOf(tex);
        if (sheet == null) {
            // 规则命中了但贴图找不到：只提示一次，否则「配置写了没反应」完全无从查起。
            // 贴图放 resourcepacks/OpenDreamCore/ 下即可，名字可带不带目录。
            if (MISSING.add(tex)) {
                com.opendreamcore.client.OdcDebug.log("[ODC-icon] 贴图缺失，规则命中但不替换：" + tex
                        + "（物品 " + id + "；放到 resourcepacks/OpenDreamCore/" + tex + "）");
            }
            return false;
        }
        return draw(pose, sheet, buffers, light, overlay);
    }

    private static final java.util.Set<String> MATCHED =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static final java.util.Set<String> MISSING =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** 批次转发失败原因只告警一次：否则「规则命中了还是原版材质」完全无从查起。 */
    private static volatile boolean batchFailLogged;

    /** 写成功也只告警一次：区分「没写进批次」和「写了但不可见」两种完全不同的病因。 */
    private static volatile boolean batchOkLogged;

    /** 画一张贴图卡片（gif 按帧表切当前帧）。返回 false 表示这帧没能画出来。
     * 直接用原版变换矩阵：手持/掉落物 pose 已把物品摆好，卡片与原版物品模型
     * 同空间同尺寸。不能叠世界空间广告牌——手持坐标是相对相机的视图空间，
     * 叠了会位置乱飞。 */

    public static boolean draw(PoseStack pose, LooseResourceLoader.SheetInfo sheet,
                               MultiBufferSource buffers, int light, int overlay) {
        try {
            Object consumer = com.opendreamcore.client.render.CompatBatch.bufferedConsumer(buffers, sheet.rl());
            if (consumer == null) {
                if (!batchFailLogged) {
                    batchFailLogged = true;
                    com.opendreamcore.client.OdcDebug.log("[ODC-icon] 批次转发失败（bufferedConsumer=null，原版批次入口解析不了），回退原版绘制");
                }
                return false;
            }
            float frames = Math.max(1.0F, sheet.frames());
            float u0 = (sheet.frame() * sheet.frameW()) / (float) (sheet.frameW() * frames);
            float u1 = u0 + 1.0F / frames;
            float half = SIZE;
            org.joml.Matrix4f mat = pose.last().pose();
            // text 批次（固定缓冲）格式无 Normal：元素数>=5（entity 系）才传法线
            Object normalMat = com.opendreamcore.client.render.CompatBatch.lastFormatElements >= 5
                    ? pose.last().normal() : null;
            boolean ok = com.opendreamcore.client.render.CompatBatch.quadInto(consumer, mat,
                    normalMat,
                    -half, half, -half, half, u0, u1, 0.0F, 1.0F, light, overlay);
            if (!ok) {
                if (!batchFailLogged) {
                    batchFailLogged = true;
                    com.opendreamcore.client.OdcDebug.log("[ODC-icon] 批次写顶点失败（quadInto=false，链式 API 形状解析不了），回退原版绘制");
                }
                return false;
            }
            if (!batchOkLogged) {
                batchOkLogged = true;
                com.opendreamcore.client.OdcDebug.log("[ODC-icon] 世界侧卡片已写入批次（此后不再重复打印）");
            }
            // 写完立即 flush：text 固定缓冲若等到帧末统一 endBatch，实测会整卡不可见
            // （实机 14:15「批次已写入」但空白）；定点 flush 后批次不再挂起，后续
            // 请求其它批次也不会把它当 fallback 吞掉。
            com.opendreamcore.client.render.CompatBatch.flushBatch(buffers, sheet.rl());
            return true;
        } catch (Throwable t) {
            if (!batchFailLogged) {
                batchFailLogged = true;
                com.opendreamcore.client.OdcDebug.log("[ODC-icon] 世界侧卡片绘制异常: " + t);
            }
            // 画不出来就放行原版：宁可显示原版物品，也别让整个世界渲染断掉
            return false;
        }
    }
}