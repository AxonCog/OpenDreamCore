package com.opendreamcore.mixin;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.opendreamcore.client.visual.LegacyVisualCursor;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 光标形态（1.16.5）：crosshair 走原版，命中自定义形态画一块黄色示意
 * 方块（GL 贴图管线太版本化，先保证规则有可见反馈）。
 *
 * 注入串写 srg 名：这代没有运行时反混淆，注解里的名字不会被重映射。
 */
@Mixin(Screen.class)
public abstract class MixinCursor {

    @Inject(method = "func_230430_a_(Lcom/mojang/blaze3d/matrix/MatrixStack;IIF)V",
            at = @At("RETURN"))
    private void opendreamcore$cursor(MatrixStack ms, int mx, int my, float pt, CallbackInfo ci) {
        if (!LegacyVisualCursor.custom()) {
            return;
        }
        AbstractGui.fill(ms, mx, my, mx + 12, my + 12, 0xCCFFE066);
    }
}