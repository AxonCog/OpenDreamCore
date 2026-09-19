package com.opendreamcore.mixin;

import com.opendreamcore.client.resources.LooseResourceLoader;
import com.opendreamcore.client.visual.VisualCursor;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 光标形态（1.20.1-1.21.4）：Screen.render 收尾墨迹按 VisualCursor 画
 * 自定义光标贴图（16x16）。crosshair 不碰，让原版继续。
 * Screen 的鼠标坐标直接用 render 参数传进来的那份（1.21.8 起字段已删，
 * 这里统一不碰 @Shadow，跨版本行为一致）。
 */
@Mixin(Screen.class)
public abstract class MixinCursor {

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
            at = @At("RETURN"))
    private void opendreamcore$cursor(GuiGraphics gui, int mx, int my, float partialTicks,
                                      CallbackInfo ci) {
        if (!VisualCursor.custom()) {
            return;
        }
        ResourceLocation rl = LooseResourceLoader.lookup(VisualCursor.current());
        if (rl == null) {
            return;
        }
        gui.blit(rl, mx, my, 16, 16, 0, 0);
    }
}