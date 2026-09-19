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
 * 光标形态（1.21.8/1.21.11）：Screen.render 收尾按 VisualCursor 画光标
 * 贴图。这版 GuiGraphics 的 blit 用浮点 uv 比例（9 参那套）。
 * 1.21.8 的 Screen 不再有 mouseX/mouseY 字段，直接用 render 参数。
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
        gui.blit(rl, mx, my, 16, 16, 0.0F, 0.0F, 1.0F, 1.0F);
    }
}