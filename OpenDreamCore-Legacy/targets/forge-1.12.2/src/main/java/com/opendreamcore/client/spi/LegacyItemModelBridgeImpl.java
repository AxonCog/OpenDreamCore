package com.opendreamcore.client.spi;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

/**
 * 1212 的物品桥：老 RenderItem 没有公开的 3D 静态口子，走 2D 图标 + GL 旋转矩阵。
 */
public final class LegacyItemModelBridgeImpl implements LegacyItemRenderBridge {

    @Override
    public void drawItemModel(int cx, int cy, float scale, float yaw, float pitch, String itemId) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.world == null) {
            return;
        }
        ItemStack stack = resolveItem(itemId);
        if (stack == null || stack.isEmpty()) {
            return;
        }
        GlStateManager.pushMatrix();
        GlStateManager.translate(cx, cy, 100.0);
        float s = scale <= 0 ? 16.0f : 16.0f * scale;
        GlStateManager.scale(s, s, 1.0);
        GlStateManager.rotate((float) Math.toDegrees(pitch), 1.0F, 0.0F, 0.0F);
        GlStateManager.rotate((float) Math.toDegrees(yaw), 0.0F, 1.0F, 0.0F);
        mc.getRenderItem().renderItemAndEffectIntoGUI(stack, -8, -8);
        GlStateManager.popMatrix();
    }

    private static ItemStack resolveItem(String id) {
        ResourceLocation rl = new ResourceLocation(id);
        if (rl == null) {
            return ItemStack.EMPTY;
        }
        Item item = ForgeRegistries.ITEMS.getValue(rl);
        return item == null || item == net.minecraft.init.Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }
}