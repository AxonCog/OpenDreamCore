package com.opendreamcore.mixin;

import com.opendreamcore.client.ClientController;
import com.opendreamcore.protocol.message.TooltipRegistry;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 原版 tooltip 替换：服务端 tooltip/ 目录用"物品 id"注册的条目，悬停原版
 * 物品时按物品 id 命中，画自定义提示框顶掉原版；没注册的原版照旧。
 */
@Mixin(GuiGraphics.class)
public abstract class MixinTooltip {

    @Inject(method = "renderTooltip(Lnet/minecraft/client/gui/Font;"
            + "Lnet/minecraft/world/item/ItemStack;II)V",
            at = @At("HEAD"), cancellable = true)
    private void opendreamcore$replaceTooltip(Font font, ItemStack stack, int x, int y,
                                              CallbackInfo ci) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        TooltipRegistry.Entry entry = ClientController.get().tooltips().get(id);
        if (entry == null) {
            return;
        }
        GuiGraphics g = (GuiGraphics) (Object) this;
        drawTooltip(g, font, entry, x + 12, y - 8);
        ci.cancel();
    }

    /** 自绘提示框：背景条 + 边框 + 多行文字（样式取服务端条目，缺省给默认）。 */
    private static void drawTooltip(GuiGraphics g, Font font, TooltipRegistry.Entry e,
                                    int x, int y) {
        String[] lines = String.valueOf(e.text()).split("\n", -1);
        int w = 0;
        for (String l : lines) {
            w = Math.max(w, font.width(l));
        }
        int pad = 4;
        int h = lines.length * (font.lineHeight + 1) + pad * 2;
        int bg = parseColor(e.background(), 0xCC101018);
        int border = parseColor(e.border(), 0xFF2F2F4F);
        int fg = parseColor(e.color(), 0xFFFFFFFF);
        g.fill(x, y, x + w + pad * 2, y + h, bg);
        g.fill(x, y, x + w + pad * 2, y + 1, border);
        g.fill(x, y + h - 1, x + w + pad * 2, y + h, border);
        g.fill(x, y, x + 1, y + h, border);
        g.fill(x + w + pad * 2 - 1, y, x + w + pad * 2, y + h, border);
        for (int i = 0; i < lines.length; i++) {
            g.drawString(font, lines[i], x + pad, y + pad + i * (font.lineHeight + 1), fg);
        }
    }

    private static int parseColor(String s, int fallback) {
        if (s == null || s.isBlank()) {
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