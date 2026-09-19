package com.opendreamcore.mixin;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.opendreamcore.client.LegacyTooltipStore;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 原版 tooltip 替换（1.16.5）：服务端用物品 id 注册的 tooltip/ 条目，
 * 悬停原版物品时按 id 命中就自绘顶掉原版。
 */
@Mixin(Screen.class)
public abstract class MixinTooltip {

    @Inject(method = "renderTooltip(Lcom/mojang/blaze3d/matrix/MatrixStack;"
            + "Lnet/minecraft/item/ItemStack;II)V",
            at = @At("HEAD"), cancellable = true)
    private void opendreamcore$replaceTooltip(MatrixStack ms, ItemStack stack, int x, int y,
                                              CallbackInfo ci) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        String id = ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
        LegacyTooltipStore.Entry entry = LegacyTooltipStore.get(id);
        if (entry == null) {
            return;
        }
        drawTooltip(ms, entry, x + 12, y - 8);
        ci.cancel();
    }

    /** 自绘提示框：背景条 + 边框 + 多行文字（1.16.5 用 AbstractGui.fill + font.drawString）。 */
    private static void drawTooltip(MatrixStack ms, LegacyTooltipStore.Entry e, int x, int y) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        FontRenderer font = mc.font;
        String[] lines = String.valueOf(e.text).split("\n", -1);
        int w = 0;
        for (String l : lines) {
            w = Math.max(w, font.width(l));
        }
        int pad = 4;
        int h = lines.length * (font.lineHeight + 1) + pad * 2;
        int bg = parseColor(e.background, 0xCC101018);
        int border = parseColor(e.border, 0xFF2F2F4F);
        int fg = parseColor(e.color, 0xFFFFFFFF);
        AbstractGui.fill(ms, x, y, x + w + pad * 2, y + h, bg);
        AbstractGui.fill(ms, x, y, x + w + pad * 2, y + 1, border);
        AbstractGui.fill(ms, x, y + h - 1, x + w + pad * 2, y + h, border);
        AbstractGui.fill(ms, x, y, x + 1, y + h, border);
        AbstractGui.fill(ms, x + w + pad * 2 - 1, y, x + w + pad * 2, y + h, border);
        for (int i = 0; i < lines.length; i++) {
            font.draw(ms, lines[i], (float) (x + pad),
                    (float) (y + pad + i * (font.lineHeight + 1)), fg);
        }
    }

    private static int parseColor(String s, int fallback) {
        if (s == null || s.trim().isEmpty()) {
            return fallback;
        }
        try {
            String hex = s.trim();
            if (hex.startsWith("#")) {
                hex = hex.substring(1);
            } else {
                hex = hex.replace("0x", "");
            }
            long v = Long.parseLong(hex, 16);
            if (hex.length() <= 6) {
                v = v | 0xFF000000L;
            }
            return (int) v;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}