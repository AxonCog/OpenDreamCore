package com.opendreamcore.mixin;

import com.opendreamcore.client.visual.LegacyVisualCursor;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 光标形态（1.7.10）：drawScreen 收尾按 LegacyVisualCursor 画黄色示意方块。
 */
@Mixin(GuiScreen.class)
public abstract class MixinCursor {

    @Inject(method = "drawScreen(IIF)V", at = @At("RETURN"))
    private void opendreamcore$cursor(int mx, int my, float pt, CallbackInfo ci) {
        if (!LegacyVisualCursor.custom()) {
            return;
        }
        Gui.drawRect(mx, my, mx + 12, my + 12, 0xCCFFE066);
    }
}