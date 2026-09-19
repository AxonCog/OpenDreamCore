package com.opendreamcore.mixin;

import com.opendreamcore.client.LegacyTooltipStore;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 原版 tooltip 替换（1.6.4）：容器里悬停物品按物品 id 命中服务端注册
 * 的 tooltip/ 条目，自绘顶掉原版。1.6.4 没有 registryName，id 拿
 * unlocalizedName 尾段匹配（跟 GlobalRenderItem 一个套路）。
 */
@Mixin(GuiContainer.class)
public abstract class MixinTooltip {

    @Shadow
    protected abstract Slot getSlotAtPosition(int x, int y);

    @Inject(method = "renderToolTip(Ljava/util/List;II)V",
            at = @At("HEAD"), cancellable = true)
    private void opendreamcore$replaceTooltip(java.util.List lines, int x, int y,
                                              CallbackInfo ci) {
        Slot slot = this.getSlotAtPosition(x, y);
        if (slot == null || !slot.getHasStack()) {
            return;
        }
        String un = slot.getStack().getItem().getUnlocalizedName();
        if (un != null && un.startsWith("item.")) {
            un = un.substring(5);
        } else if (un != null && un.startsWith("tile.")) {
            un = un.substring(5);
        }
        String id = un == null ? "" : un;
        LegacyTooltipStore.Entry entry = LegacyTooltipStore.get(id);
        if (entry == null) {
            return;
        }
        drawTooltip(entry, x + 12, y - 8);
        ci.cancel();
    }

    /** 自绘提示框：背景条 + 边框 + 多行文字（1.6.4 用 drawRect + fontRenderer.drawString）。 */
    private static void drawTooltip(LegacyTooltipStore.Entry e, int x, int y) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
        net.minecraft.client.gui.FontRenderer font = mc.fontRenderer;
        String[] lines = String.valueOf(e.text).split("\n", -1);
        int w = 0;
        for (String l : lines) {
            w = Math.max(w, font.getStringWidth(l));
        }
        int pad = 4;
        int h = lines.length * (font.FONT_HEIGHT + 1) + pad * 2;
        int bg = parseColor(e.background, 0xCC101018);
        int border = parseColor(e.border, 0xFF2F2F4F);
        int fg = parseColor(e.color, 0xFFFFFFFF);
        GuiScreen.drawRect(x, y, x + w + pad * 2, y + h, bg);
        GuiScreen.drawRect(x, y, x + w + pad * 2, y + 1, border);
        GuiScreen.drawRect(x, y + h - 1, x + w + pad * 2, y + h, border);
        GuiScreen.drawRect(x, y, x + 1, y + h, border);
        GuiScreen.drawRect(x + w + pad * 2 - 1, y, x + w + pad * 2, y + h, border);
        for (int i = 0; i < lines.length; i++) {
            font.drawString(lines[i], x + pad, y + pad + i * (font.FONT_HEIGHT + 1), fg);
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