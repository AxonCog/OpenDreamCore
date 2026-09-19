package com.opendreamcore.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 26.1.2 的 GuiGraphicsExtractor 把 guiRenderState 捂得死死的，物品桥要在
 * submit 式管线里加渲染节点必须经过它——一个 accessor 的事，别整反射。
 */
@Mixin(GuiGraphicsExtractor.class)
public interface MixinGuiGraphicsExtractor {

    @Accessor("guiRenderState")
    GuiRenderState opendreamcore$getGuiRenderState();
}