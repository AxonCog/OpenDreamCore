package com.opendreamcore.mixin;

import com.opendreamcore.client.ClientController;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.SubtitleOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * hideVanilla 逐层：subtitle_overlay（字幕）在 1.21.1 是独立组件 SubtitleOverlay（Gui 内无渲染方法），
 * 在此取消其 render。
 */
@Mixin(SubtitleOverlay.class)
public abstract class MixinSubtitleOverlay {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void odc$hideSubtitle(GuiGraphics g, CallbackInfo ci) {
        if (ClientController.get().isVanillaLayerHidden("minecraft:subtitle_overlay")) {
            ci.cancel();
        }
    }
}
