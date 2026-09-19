package com.opendreamcore.client.spi;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * 164 的物品桥：老 RenderItem 没有 3D 静态口子，2D 图标 + GL 旋转矩阵。
 */
public final class LegacyItemModelBridgeImpl implements LegacyItemRenderBridge {

    private final RenderItem renderer = new RenderItem();

    @Override
    public void drawItemModel(int cx, int cy, float scale, float yaw, float pitch, String itemId) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.theWorld == null) {
            return;
        }
        ItemStack stack = resolveItem(itemId);
        if (stack == null || stack.stackSize == 0) {
            return;
        }
        GL11.glPushMatrix();
        GL11.glTranslatef(cx, cy, 100.0F);
        float s = scale <= 0 ? 16.0f : 16.0f * scale;
        GL11.glScalef(s, s, 1.0F);
        GL11.glRotatef((float) Math.toDegrees(pitch), 1.0F, 0.0F, 0.0F);
        GL11.glRotatef((float) Math.toDegrees(yaw), 0.0F, 1.0F, 0.0F);
        renderer.renderItemAndEffectIntoGUI(mc.fontRenderer, mc.renderEngine, stack, -8, -8);
        GL11.glPopMatrix();
    }

    private static ItemStack resolveItem(String id) {
        ResourceLocation rl = new ResourceLocation(id);
        if (rl == null) {
            return null;
        }
        Item item = cpw.mods.fml.common.registry.GameRegistry.findItem(rl.getResourceDomain(), rl.getResourcePath());
        return item == null ? null : new ItemStack(item);
    }
}