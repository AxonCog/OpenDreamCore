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
 * 整图直出零切割：uv 全幅（0-1）就是整张贴图；gif 用帧表当前帧切片
 * （tickAll 推进，自动播动画）；规则按显示名匹配（textureFor 带 hoverName）。
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
        String tex = VisualItemSkins.textureFor(id, stack.getHoverName().getString());
        if (tex == null) {
            return;
        }
        var si = LooseResourceLoader.sheetOf(tex);
        if (si == null) {
            return;
        }
        float u0 = 0.0F;
        float u1 = 1.0F;
        if (si.frames() > 1) {
            float step = 1.0F / si.frames();
            u0 = si.frame() * step;
            u1 = u0 + step;
        }
        Identifier rl = si.rl();
        GuiGraphicsExtractor g = (GuiGraphicsExtractor) (Object) this;
        // 整图直出：uv 全幅；gif 切当前帧区间
        g.blit(rl, x, y, 16, 16, u0, 0.0F, u1, 1.0F);
        ci.cancel();
    }
}
