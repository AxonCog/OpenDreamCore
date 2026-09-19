package com.opendreamcore.mixin;

import com.opendreamcore.client.resources.LooseResourceLoader;
import com.opendreamcore.client.visual.VisualItemSkins;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 物品图标覆写（1.21.8 专用）：这版 GuiGraphics 只剩 9 参 blit（uv 比例），
 * 覆写图整张画就是 uv 0-1。
 */
@Mixin(GuiGraphics.class)
public abstract class MixinItemIcon {

    @Inject(method = "renderItem(Lnet/minecraft/world/item/ItemStack;II)V",
            at = @At("HEAD"), cancellable = true)
    private void opendreamcore$overrideIcon(ItemStack stack, int x, int y, CallbackInfo ci) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        String tex = VisualItemSkins.textureFor(id);
        if (tex == null) {
            return;
        }
        Identifier rl = LooseResourceLoader.lookup(tex);
        if (rl == null) {
            return;
        }
        GuiGraphics g = (GuiGraphics) (Object) this;
        g.blit(rl, x, y, 16, 16, 0.0F, 0.0F, 1.0F, 1.0F);
        ci.cancel();
    }
}