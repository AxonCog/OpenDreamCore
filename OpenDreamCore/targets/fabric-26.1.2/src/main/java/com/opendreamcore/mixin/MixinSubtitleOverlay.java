package com.opendreamcore.mixin;

import com.opendreamcore.client.ClientController;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.SubtitleOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * hideVanilla 逐层：subtitle_overlay（字幕）是独立组件 SubtitleOverlay，在入口处直接取消。
 * 入口名跟版本走：1.21.x 叫 render，26.x 改成 extractRenderState(GuiGraphicsExtractor)，别照抄老版本的。
 */
@Mixin(SubtitleOverlay.class)
public abstract class MixinSubtitleOverlay {

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void odc$hideSubtitle(net.minecraft.client.gui.GuiGraphicsExtractor g, CallbackInfo ci) {
        if (ClientController.get().isVanillaLayerHidden("minecraft:subtitle_overlay")) {
            ci.cancel();
        }
    }
}
