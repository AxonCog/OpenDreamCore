package com.opendreamcore.legacy;

import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.opendreamcore.client.spi.ItemPainter;

/**
 * 物品真身画笔（1.6.4）：物品规格 "minecraft:stone x3"。
 * 这代 RenderItem 没有全局单例，自己持一个；物品名走 GameRegistry
 * 的名字注册表（modid:name 拆开查）；物品名 + 数量可以直接用
 * findItemStack 一步到位。GUI 物品方法要显式递 FontRenderer 和
 * TextureManager，都是 Minecraft 的现成字段。
 */
public final class ItemPainter164 implements ItemPainter {

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
            renderer.renderItemAndEffectIntoGUI(mc.fontRenderer, mc.renderEngine, stack, px, py);
            if (count > 1) {
                renderer.renderItemOverlayIntoGUI(mc.fontRenderer, mc.renderEngine, stack, px, py);
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
        int colon = id.indexOf(':');
        String modid = colon > 0 ? id.substring(0, colon) : "minecraft";
        String name = colon > 0 ? id.substring(colon + 1) : id;
        // 名字注册表查不到就再试数字 id（1.6.4 双轨制的老物件）
        Item item = cpw.mods.fml.common.registry.GameRegistry.findItem(modid, name);
        if (item == null) {
            try {
                int numId = Integer.parseInt(name);
                if (numId >= 0 && numId < Item.itemsList.length) {
                    item = Item.itemsList[numId];
                }
            } catch (NumberFormatException ignored) {
            }
        }
        if (item == null) {
            return null;
        }
        return new ItemStack(item, count);
    }
}
