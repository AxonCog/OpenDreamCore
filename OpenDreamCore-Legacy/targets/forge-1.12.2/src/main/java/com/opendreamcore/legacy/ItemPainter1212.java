package com.opendreamcore.legacy;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.opendreamcore.client.spi.ItemPainter;

/**
 * 物品真身画笔：item_slot / hot_slot 元素里的物品图标。
 * 物品规格跟现代端同一套写法——"minecraft:stone x3"，空格 x 数量。
 * 渲染走本时代的 RenderItem，16px 基准，格边长变了就用矩阵等比缩。
 */
final class ItemPainter1212 implements ItemPainter {

    @Override
    public boolean render(String itemSpec, double x, double y, double size, double alpha) {
        ItemStack stack = build(itemSpec);
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        Minecraft mc = Minecraft.getMinecraft();
        RenderItem ri = mc.getRenderItem();
        int px = (int) Math.round(x + (size - 16) / 2.0);
        int py = (int) Math.round(y + (size - 16) / 2.0);
        GlStateManager.pushMatrix();
        if (Math.abs(size - 16.0) > 0.5) {
            // 格子不是标准 16px：原点挪到格心再等比缩，物品始终居中
            GlStateManager.translate(x + size / 2, y + size / 2, 0);
            GlStateManager.scale(size / 16.0, size / 16.0, 1.0);
            GlStateManager.translate(-8, -8, 0);
        }
        GlStateManager.enableAlpha();
        RenderHelper.enableGUIStandardItemLighting();
        try {
            ri.renderItemAndEffectIntoGUI(stack, px, py);
            if (countOf(itemSpec) > 1) {
                ri.renderItemOverlayIntoGUI(mc.fontRenderer, stack, px, py, null);
            }
        } finally {
            RenderHelper.disableStandardItemLighting();
            GlStateManager.popMatrix();
        }
        GlStateManager.color(1F, 1F, 1F, 1F);
        return true;
    }

    @Override
    public boolean renderHotbar(int slotIndex, double x, double y, double size, double alpha) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || slotIndex < 0 || slotIndex > 8) {
            return false;
        }
        ItemStack stack = mc.player.inventory.getStackInSlot(slotIndex);
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        RenderItem ri = mc.getRenderItem();
        int px = (int) Math.round(x + (size - 16) / 2.0);
        int py = (int) Math.round(y + (size - 16) / 2.0);
        GlStateManager.pushMatrix();
        if (Math.abs(size - 16.0) > 0.5) {
            GlStateManager.translate(x + size / 2, y + size / 2, 0);
            GlStateManager.scale(size / 16.0, size / 16.0, 1.0);
            GlStateManager.translate(-8, -8, 0);
        }
        GlStateManager.enableAlpha();
        RenderHelper.enableGUIStandardItemLighting();
        try {
            ri.renderItemAndEffectIntoGUI(stack, px, py);
            if (stack.getCount() > 1) {
                ri.renderItemOverlayIntoGUI(mc.fontRenderer, stack, px, py, null);
            }
        } finally {
            RenderHelper.disableStandardItemLighting();
            GlStateManager.popMatrix();
        }
        GlStateManager.color(1F, 1F, 1F, 1F);
        return true;
    }

    /** "minecraft:stone x3" → 栈；id 不认识给 null。 */
    private static ItemStack build(String itemSpec) {
        String id = idOf(itemSpec);
        if (id.isEmpty()) {
            return null;
        }
        Item item = Item.getByNameOrId(id);
        if (item == null) {
            return null;
        }
        return new ItemStack(item, Math.max(1, countOf(itemSpec)));
    }

    static String idOf(String itemSpec) {
        String s = itemSpec == null ? "" : itemSpec.trim();
        int sp = s.indexOf(' ');
        return (sp > 0 ? s.substring(0, sp) : s).toLowerCase(java.util.Locale.ROOT);
    }

    static int countOf(String itemSpec) {
        String s = itemSpec == null ? "" : itemSpec.trim();
        int sp = s.indexOf(' ');
        if (sp > 0 && sp + 1 < s.length()) {
            String tail = s.substring(sp + 1).trim();
            if (tail.toLowerCase(java.util.Locale.ROOT).startsWith("x")) {
                try {
                    return Math.max(1, Integer.parseInt(tail.substring(1).trim()));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return 1;
    }
}
