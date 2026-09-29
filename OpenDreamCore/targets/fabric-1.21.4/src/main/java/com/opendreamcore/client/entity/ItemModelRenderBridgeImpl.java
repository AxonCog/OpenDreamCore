package com.opendreamcore.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;

/**
 * 1.21.4/1.21.8 物品 3D 展示桥：renderStatic(FIXED) + PoseStack 旋转。
 * 注册表取物品走反射（1.21.2+ ITEM.get 返回 Optional），缓冲提交 flush/endBatch 都试。
 */
public final class ItemModelRenderBridgeImpl implements ItemModelRenderBridge {

    @Override
    public void drawItemModel(Object g0, int cx, int cy, float scale, float yaw, float pitch,
                              String itemId, int alpha) {
        GuiGraphics g = (GuiGraphics) g0;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) {
            return;
        }
        ItemRenderer ir = mc.getItemRenderer();
        ItemStack stack = resolveItem(itemId);
        if (stack == null || stack.isEmpty()) {
            return;
        }
        PoseStack pose = new PoseStack();
        pose.translate(cx, cy, 100.0F);
        pose.scale(scale, scale, scale);
        pose.mulPose(new Quaternionf().rotationYXZ(
                (float) Math.toRadians(yaw), (float) Math.toRadians(pitch), 0.0F));
        pose.translate(-0.5F, -0.5F, 0.0F);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        // 独立批次：见 1.21.4/1.21.8 变体处的说明（共享源被原版占用，不能替它收尾）。
        com.opendreamcore.client.CompatRender.WorldBatch worldBatch =
                com.opendreamcore.client.CompatRender.beginWorldBatch();
        MultiBufferSource source = worldBatch.source() != null
                ? (MultiBufferSource) worldBatch.source()
                : buffers;
        try {
            ir.renderStatic(stack, ItemDisplayContext.FIXED, 0xF000F0, 0, pose, source, mc.level, 0);
        } finally {
            worldBatch.close();
        }
    }

    /** 取物品：直调注册表（getValue 找不到返回 null），不再按名反射。 */
    private static ItemStack resolveItem(String id) {
        net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(id);
        if (rl == null) {
            return ItemStack.EMPTY;
        }
        net.minecraft.world.item.Item item =
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(rl);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    @Override
    public java.util.List<Object> loreLines(Object stack) {
        net.minecraft.world.item.component.ItemLore lore =
                ((net.minecraft.world.item.ItemStack) stack).getComponents()
                        .get(net.minecraft.core.component.DataComponents.LORE);
        return lore == null ? java.util.List.of() : new java.util.ArrayList<Object>(lore.lines());
    }

    @Override
    public java.util.List<Object> tooltipLines(Object stack, Object level, Object player, Object flag) {
        return new java.util.ArrayList<Object>(((net.minecraft.world.item.ItemStack) stack).getTooltipLines(
                net.minecraft.world.item.Item.TooltipContext.of((net.minecraft.world.level.Level) level),
                (net.minecraft.world.entity.player.Player) player,
                (net.minecraft.world.item.TooltipFlag) flag));
    }

    @Override
    public int packFormat() {
        return net.minecraft.SharedConstants.getCurrentVersion()
                .getPackVersion(net.minecraft.server.packs.PackType.CLIENT_RESOURCES);
    }

}