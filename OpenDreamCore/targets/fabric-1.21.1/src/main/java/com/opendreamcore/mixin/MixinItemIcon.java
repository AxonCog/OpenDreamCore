package com.opendreamcore.mixin;

import com.opendreamcore.client.CompatRender;
import com.opendreamcore.client.resources.LooseResourceLoader;
import com.opendreamcore.client.visual.VisualItemSkins;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 物品图标覆写：ItemIcon 规则命中就把原版图标换成自定义贴图。
 * 在 renderItem 最前面拦，画完 cancel 掉，原版那套 2D 图标不再跑。
 * 整图直出零切割：本版 GuiGraphics 只有 256 图集语义的旧式 blit，
 * 以前 g.blit(rl,x,y,16,16,0,0) 只采样贴图左上 1/16 区域（大图被切割成角料）；
 * 改走 CompatRender.blit（11 参版本垫片）按真实像素尺寸全幅 UV，
 * gif 用帧表当前帧切片（tickAll 推进，自动播动画）。
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
        String tex = VisualItemSkins.textureFor(id, stack.getHoverName().getString());
        if (tex == null) {
            return;
        }
        var si = LooseResourceLoader.sheetOf(tex);
        if (si == null) {
            return;
        }
        GuiGraphics g = (GuiGraphics) (Object) this;
        // 图标固定 16x16，跟原版一格对齐；UV 全幅（gif 切当前帧）
        CompatRender.blit(g, si.rl(), x, y, 16, 16,
                si.frame() * si.frameW(), 0, si.frameW(), si.frameH(),
                si.frameW() * si.frames(), si.frameH());
        ci.cancel();
    }
}
