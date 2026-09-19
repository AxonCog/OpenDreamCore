package com.opendreamcore.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Quaternionf;

/**
 * 1.20.1 Forge 物品 3D 展示桥：ItemRenderer.render(FIXED) + PoseStack 旋转。
 * 注册表走 ForgeRegistries.ITEMS（与 forge 侧实体桥同源）。
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
        var model = ir.getModel(stack, mc.level, mc.player, 0);
        PoseStack pose = new PoseStack();
        pose.translate(cx, cy, 100.0F);
        pose.scale(scale, scale, scale);
        pose.mulPose(new Quaternionf().rotationYXZ(
                (float) Math.toRadians(yaw), (float) Math.toRadians(pitch), 0.0F));
        pose.translate(-0.5F, -0.5F, 0.0F);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        ir.render(stack, ItemDisplayContext.FIXED, false, pose, buffers, 0xF000F0, 0, model);
        buffers.endBatch();
    }

    private static ItemStack resolveItem(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) {
            return ItemStack.EMPTY;
        }
        var item = ForgeRegistries.ITEMS.getValue(rl);
        return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }
}