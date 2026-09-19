package com.opendreamcore.client.spi;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.RenderTypeBuffers;
import net.minecraft.client.renderer.model.ItemCameraTransforms;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 1165 的物品 3D 桥：ItemRenderer.renderStatic(FIXED) + MatrixStack 旋转。
 * 类名走 mojmap（ItemRenderer 在 client.renderer 包），跟 ItemPainter1165 一致。
 */
public final class LegacyItemModelBridgeImpl implements LegacyItemRenderBridge {

    @Override
    public void drawItemModel(int cx, int cy, float scale, float yaw, float pitch, String itemId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) {
            return;
        }
        ItemRenderer ir = mc.getItemRenderer();
        ItemStack stack = resolveItem(itemId);
        if (stack == null || stack.isEmpty()) {
            return;
        }
        RenderTypeBuffers buffers = mc.renderBuffers();
        IRenderTypeBuffer.Impl source = buffers.bufferSource();
        MatrixStack ms = new MatrixStack();
        ms.translate(cx, cy, 100.0);
        float s = scale <= 0 ? 16.0f : 16.0f * scale;
        ms.scale(s, s, 1.0f);
        ms.mulPose(new net.minecraft.util.math.vector.Quaternion((float) Math.toRadians(pitch), 0.0F, 0.0F, false));
        ms.mulPose(new net.minecraft.util.math.vector.Quaternion(0.0F, (float) Math.toRadians(yaw), 0.0F, false));
        ms.translate(-0.5, -0.5, 0.0);
        ir.renderStatic(stack, ItemCameraTransforms.TransformType.FIXED, 0xF000F0, 0, ms, source);
        source.endBatch();
    }

    private static ItemStack resolveItem(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) {
            return ItemStack.EMPTY;
        }
        Item item = ForgeRegistries.ITEMS.getValue(rl);
        return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }
}