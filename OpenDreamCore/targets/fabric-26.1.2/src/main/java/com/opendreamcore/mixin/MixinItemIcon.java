package com.opendreamcore.mixin;

import com.opendreamcore.client.resources.LooseResourceLoader;
import com.opendreamcore.client.visual.VisualItemSkins;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 物品图标覆写（26.1.2 专用）：GUI 物品全走 GuiGraphicsExtractor.item，
 * 覆写图用 9 参 blit 直接绑 Identifier 画（uv 比例）。
 */
@Mixin(GuiGraphicsExtractor.class)
public abstract class MixinItemIcon {

    @Inject(method = "item(Lnet/minecraft/world/item/ItemStack;II)V",
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
        GuiGraphicsExtractor g = (GuiGraphicsExtractor) (Object) this;
        g.blit(rl, x, y, 16, 16, 0.0F, 0.0F, 1.0F, 1.0F);
        ci.cancel();
    }
}