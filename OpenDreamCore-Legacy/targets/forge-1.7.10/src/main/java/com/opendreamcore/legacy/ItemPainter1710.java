package com.opendreamcore.legacy;

import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import com.opendreamcore.client.spi.ItemPainter;

/**
 * 物品真身画笔（1.7.10）：物品规格 "minecraft:stone x3"。
 * 这代 RenderItem 在 entity 包里、没有单例，自己持一个；
 * GUI 方法要显式递 FontRenderer 和 TextureManager。
 * 物品名查询走 GameRegistry（modid:name），查不到再试老数字 id。
 */
public final class ItemPainter1710 implements ItemPainter {

    private final net.minecraft.client.renderer.entity.RenderItem renderer =
            new net.minecraft.client.renderer.entity.RenderItem();

    @Override
    public boolean render(String itemSpec, double x, double y, double size, double alpha) {
        ItemStack stack = build(itemSpec);
        if (stack == null) {
            return false;
        }
        draw(Minecraft.getMinecraft(), stack, stack.stackSize, x, y, size);
        return true;
    }

    @Override
    public boolean renderHotbar(int slotIndex, double x, double y, double size, double alpha) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || slotIndex < 0 || slotIndex > 8) {
            return false;
        }
        ItemStack stack = mc.thePlayer.inventory.mainInventory[slotIndex];
        if (stack == null) {
            return false;
        }
        draw(mc, stack, stack.stackSize, x, y, size);
        return true;
    }

    private void draw(Minecraft mc, ItemStack stack, int count, double x, double y, double size) {
        int px = (int) Math.round(x + (size - 16) / 2.0);
        int py = (int) Math.round(y + (size - 16) / 2.0);
        org.lwjgl.opengl.GL11.glPushMatrix();
        try {
            if (Math.abs(size - 16.0) > 0.5) {
                org.lwjgl.opengl.GL11.glTranslatef((float) (x + size / 2), (float) (y + size / 2), 0);
                float s = (float) (size / 16.0);
                org.lwjgl.opengl.GL11.glScalef(s, s, 1);
                org.lwjgl.opengl.GL11.glTranslatef(-8, -8, 0);
            }
            net.minecraft.client.renderer.RenderHelper.enableGUIStandardItemLighting();
            renderer.renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(), stack, px, py);
            if (count > 1) {
                renderer.renderItemOverlayIntoGUI(mc.fontRenderer, mc.getTextureManager(), stack, px, py);
            }
            net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();
        } finally {
            org.lwjgl.opengl.GL11.glPopMatrix();
        }
        org.lwjgl.opengl.GL11.glColor4f(1, 1, 1, 1);
    }

    private static ItemStack build(String itemSpec) {
        String s = itemSpec == null ? "" : itemSpec.trim();
        int sp = s.indexOf(' ');
        String id = (sp > 0 ? s.substring(0, sp) : s).toLowerCase(java.util.Locale.ROOT);
        int count = 1;
        if (sp > 0) {
            String tail = s.substring(sp + 1).trim();
            if (tail.toLowerCase(java.util.Locale.ROOT).startsWith("x")) {
                try {
                    count = Math.max(1, Integer.parseInt(tail.substring(1).trim()));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        Item item = (Item) Item.itemRegistry.getObject(new ResourceLocation(id));
        if (item == null) {
            return null;
        }
        return new ItemStack(item, count);
    }
}
