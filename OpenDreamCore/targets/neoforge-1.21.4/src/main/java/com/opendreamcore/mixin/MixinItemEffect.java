package com.opendreamcore.mixin;

import com.opendreamcore.client.visual.VisualItemEffects;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 物品特效：renderItem 画完图标后再叠一层特效（发光=半透明扩散层，描边=轮廓）。
 * 不认识的 effect 值啥也不画，别把渲染炸了。
 */
@Mixin(GuiGraphics.class)
public abstract class MixinItemEffect {

    @Inject(method = "renderItem(Lnet/minecraft/world/item/ItemStack;II)V",
            at = @At("RETURN"))
    private void opendreamcore$itemEffect(ItemStack stack, int x, int y, CallbackInfo ci) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        VisualItemEffects.EffectEntry fx = VisualItemEffects.effectFor(id);
        if (fx == null) {
            return;
        }
        GuiGraphics g = (GuiGraphics) (Object) this;
        int rgb = fx.color() & 0xFFFFFF;
        switch (fx.effect()) {
            case "glow" -> {
                g.fill(x - 3, y - 3, x + 19, y + 19, rgb | 0x15000000);
                g.fill(x - 2, y - 2, x + 18, y + 18, rgb | 0x22000000);
                g.fill(x - 1, y - 1, x + 17, y + 17, rgb | 0x30000000);
            }
            case "outline" -> {
                g.fill(x - 1, y - 1, x + 17, y, rgb | 0xFF000000);
                g.fill(x - 1, y + 16, x + 17, y + 17, rgb | 0xFF000000);
                g.fill(x - 1, y, x, y + 16, rgb | 0xFF000000);
                g.fill(x + 16, y, x + 17, y + 16, rgb | 0xFF000000);
            }
            default -> {
                // 不认识的形态不画
            }
        }
    }
}