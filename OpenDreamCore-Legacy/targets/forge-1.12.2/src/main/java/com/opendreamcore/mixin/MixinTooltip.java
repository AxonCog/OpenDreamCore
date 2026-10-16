package com.opendreamcore.mixin;

import java.util.ArrayList;
import java.util.List;
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
 * 原版 tooltip 替换（1.12.2）：容器里悬停物品时按物品 id 命中服务端注册
 * 的 tooltip/ 条目，自绘顶掉原版。hoveredSlot 是 private，@Shadow 拿。
 */
@Mixin(GuiScreen.class)
public abstract class MixinTooltip {

    @Shadow
    private Slot hoveredSlot;

    @Inject(method = "renderToolTip(Ljava/util/List;II)V",
            at = @At("HEAD"), cancellable = true)
    private void opendreamcore$replaceTooltip(java.util.List<String> lines, int x, int y,
                                              CallbackInfo ci) {
        Object self = this;
        if (!(self instanceof GuiContainer)) {
            return;
        }
        Slot slot = this.hoveredSlot;
        if (slot == null || !slot.getHasStack()) {
            return;
        }
        String id = slot.getStack().getItem().getRegistryName() == null
                ? "" : slot.getStack().getItem().getRegistryName().toString();
        LegacyTooltipStore.Entry entry = LegacyTooltipStore.get(id);
        if (entry == null) {
            return;
        }
        drawTooltip(entry, x + 12, y - 8);
        ci.cancel();
    }

    /** 自绘提示框：背景条 + 边框 + 多行文字（1.12.2 用 drawRect + fontRenderer.drawString）。 */
    private static void drawTooltip(LegacyTooltipStore.Entry e, int x, int y) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
        net.minecraft.client.gui.FontRenderer font = mc.fontRenderer;
        String[] lines = wrapLines(font, String.valueOf(e.text), tooltipWidth(e));
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

    private static int tooltipWidth(LegacyTooltipStore.Entry e) {
        double width = e.width;
        return width > 0 && Double.isFinite(width) && width <= Integer.MAX_VALUE ? (int) Math.ceil(width) : Integer.MAX_VALUE;
    }

    private static String[] wrapLines(net.minecraft.client.gui.FontRenderer font, String text, int maxWidth) {
        List<String> out = new ArrayList<>();
        for (String raw : text.split("\\n", -1)) {
            StringBuilder line = new StringBuilder();
            for (int i = 0; i < raw.length(); i++) {
                String ch = raw.substring(i, i + 1);
                if (line.length() > 0 && (maxWidth != Integer.MAX_VALUE && font.getStringWidth(line + ch) > maxWidth)) { out.add(line.toString()); line.setLength(0); }
                line.append(ch);
            }
            out.add(line.toString());
        }
        return out.toArray(new String[0]);
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