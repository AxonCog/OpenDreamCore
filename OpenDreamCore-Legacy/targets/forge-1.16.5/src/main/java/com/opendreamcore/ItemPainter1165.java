package com.opendreamcore;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import com.opendreamcore.client.spi.ItemPainter;

/**
 * 物品真身画笔（1.16.5）：物品规格 "minecraft:stone x3"。
 * 这代物品渲染进 Instantiated buffer，走 ItemRenderer 的 GUI 两步：
 * renderItemAndEffectIntoGUI + renderGuiItemOverlay（数量角标）。
 */
public final class ItemPainter1165 implements ItemPainter {

    @Override
    public boolean render(String itemSpec, double x, double y, double size, double alpha) {
        net.minecraft.item.ItemStack stack = build(itemSpec);
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        draw(mc, stack, stack.getCount(), x, y, size);
        return true;
    }

    @Override
    public boolean renderHotbar(int slotIndex, double x, double y, double size, double alpha) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null || slotIndex < 0 || slotIndex > 8) {
            return false;
        }
        net.minecraft.item.ItemStack stack = mc.player.inventory.items.get(slotIndex);
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        draw(mc, stack, stack.getCount(), x, y, size);
        return true;
    }

    private static void draw(net.minecraft.client.Minecraft mc, net.minecraft.item.ItemStack stack,
                             int count, double x, double y, double size) {
        int px = (int) Math.round(x + (size - 16) / 2.0);
        int py = (int) Math.round(y + (size - 16) / 2.0);
        com.mojang.blaze3d.matrix.MatrixStack pose = new com.mojang.blaze3d.matrix.MatrixStack();
        if (Math.abs(size - 16.0) > 0.5) {
            pose.translate(x + size / 2, y + size / 2, 100.0D);
            pose.scale((float) (size / 16.0), (float) (size / 16.0), 1.0F);
            pose.translate(-8.0D, -8.0D, 0.0D);
        } else {
            pose.translate(px, py, 100.0D);
        }
        net.minecraft.client.renderer.ItemRenderer ir = mc.getItemRenderer();
        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
        ir.renderGuiItem(stack, px, py);
        if (count > 1) {
            ir.renderGuiItemDecorations(mc.font, stack, px, py);
        }
    }

    private static net.minecraft.item.ItemStack build(String itemSpec) {
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
        net.minecraft.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS
                .getValue(new ResourceLocation(id));
        if (item == null || item == net.minecraft.item.Items.AIR) {
            return null;
        }
        return new net.minecraft.item.ItemStack(item, count);
    }
}
