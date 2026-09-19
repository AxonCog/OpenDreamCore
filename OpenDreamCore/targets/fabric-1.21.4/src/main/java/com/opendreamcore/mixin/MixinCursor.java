package com.opendreamcore.mixin;

import com.opendreamcore.client.visual.VisualCursor;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 光标形态（1.21.4）：这版 GuiGraphics 的 blit 全删了（只剩 blitSprite），
 * Screen 也没有可 @Shadow 的鼠标字段，直接用 render 参数 + fill 示意。
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
        gui.fill(mx, my, mx + 12, my + 12, 0xCCFFE066);
    }
}