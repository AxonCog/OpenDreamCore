package com.opendreamcore.mixin;

import com.opendreamcore.client.visual.VisualCursor;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 光标形态（26.1.2）：GUI 渲染走 extractRenderState，收尾按 VisualCursor
 * 画示意光标。自定义形态非 crosshair 时画半透明黄块（管线内 2D fill）。
 */
@Mixin(Screen.class)
public abstract class MixinCursor {

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
            at = @At("RETURN"))
    private void opendreamcore$cursor(GuiGraphicsExtractor gui, int mx, int my, float partialTicks,
                                      CallbackInfo ci) {
        if (!VisualCursor.custom()) {
            return;
        }
        gui.fill(mx, my, mx + 12, my + 12, 0xCCFFE066);
    }
}