package com.opendreamcore.mixin;

import com.opendreamcore.client.resources.LooseResourceLoader;
import com.opendreamcore.client.visual.VisualItemSkins;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 物品图标覆写：ItemIcon 规则命中就把原版图标换成自定义贴图。
 * 在 renderItem 最前面拦，画完 cancel 掉，原版那套 2D 图标不再跑。
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
        ResourceLocation rl = LooseResourceLoader.lookup(tex);
        if (rl == null) {
            return;
        }
        GuiGraphics g = (GuiGraphics) (Object) this;
        // 图标固定 16x16，跟原版一格对齐
        g.blit(rl, x, y, 16, 16, 0, 0);
        ci.cancel();
    }
}