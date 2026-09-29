package com.opendreamcore.mixin;

import com.opendreamcore.client.resources.LooseResourceLoader;
import com.opendreamcore.client.visual.VisualItemSkins;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 物品图标覆写（1.21.4 专用）：这版旧式 blit 删光只剩 blitSprite，自定义纹理
 * 画不了 sprite 那套，直接 RenderType.text + buffer quad。buffer 是私有字段，
 * @Shadow 拿。
 * 整图直出零切割：UV 全幅（0-1），gif 用帧表当前帧切片（tickAll 推进，自动播动画）；
 * 规则按显示名匹配（textureFor 带 hoverName）。
 */
@Mixin(GuiGraphics.class)
public abstract class MixinItemIcon {

    @Shadow
    @Final
    private MultiBufferSource.BufferSource bufferSource;

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
        float u0 = 0.0F;
        float u1 = 1.0F;
        if (si.frames() > 1) {
            float step = 1.0F / si.frames();
            u0 = si.frame() * step;
            u1 = u0 + step;
        }
        ResourceLocation rl = si.rl();
        GuiGraphics g = (GuiGraphics) (Object) this;
        var consumer = this.bufferSource.getBuffer(RenderType.text(rl));
        var pose = g.pose().last().pose();
        float w = 16.0F;
        float h = 16.0F;
        float z = 0.0F;
        consumer.addVertex(pose, x, y, z).setUv(u0, 0.0F).setColor(255, 255, 255, 255).setLight(0xF000F0);
        consumer.addVertex(pose, x, y + h, z).setUv(u0, 1.0F).setColor(255, 255, 255, 255).setLight(0xF000F0);
        consumer.addVertex(pose, x + w, y + h, z).setUv(u1, 1.0F).setColor(255, 255, 255, 255).setLight(0xF000F0);
        consumer.addVertex(pose, x + w, y, z).setUv(u1, 0.0F).setColor(255, 255, 255, 255).setLight(0xF000F0);
        ci.cancel();
    }
}
