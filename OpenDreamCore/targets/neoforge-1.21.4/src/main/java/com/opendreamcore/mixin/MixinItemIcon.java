package com.opendreamcore.mixin;

import com.opendreamcore.client.model.GenItemModelRenderer;
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
        VisualItemSkins.Selection selected = VisualItemSkins.select(id, stack);
        if (selected == null) {
            if (GenItemModelRenderer.tryGui((Object) this, stack, x, y)) {
                ci.cancel();
            }
            return;
        }
        String tex = selected.texture();
        var si = LooseResourceLoader.sheetOf(tex);
        if (si == null) {
            if (GenItemModelRenderer.tryGui((Object) this, stack, x, y)) {
                ci.cancel();
            }
            return;
        }
        int size = VisualItemSkins.guiSize(selected);
        int dx = x + (16 - size) / 2;
        int dy = y + (16 - size) / 2;
        int frame = VisualItemSkins.frame(selected, si);
        float step = 1.0F / si.frames();
        float u0 = frame * step;
        float u1 = u0 + step;
        ResourceLocation rl = si.rl();
        GuiGraphics g = (GuiGraphics) (Object) this;
        try {
            var consumer = this.bufferSource.getBuffer(RenderType.text(rl));
            var pose = g.pose().last().pose();
            float w = size;
            float h = size;
            float z = 0.0F;
            consumer.addVertex(pose, dx, dy, z).setUv(u0, 0.0F).setColor(255, 255, 255, 255).setLight(0xF000F0);
            consumer.addVertex(pose, dx, dy + h, z).setUv(u0, 1.0F).setColor(255, 255, 255, 255).setLight(0xF000F0);
            consumer.addVertex(pose, dx + w, dy + h, z).setUv(u1, 1.0F).setColor(255, 255, 255, 255).setLight(0xF000F0);
            consumer.addVertex(pose, dx + w, dy, z).setUv(u1, 0.0F).setColor(255, 255, 255, 255).setLight(0xF000F0);
            ci.cancel();
        } catch (Throwable ignored) {
        }
    }
    // 容器槽位三参重载（seed）：TrMenu 等菜单的图标从这条路出（1.21.1 实机实证）。
    @Inject(method = "renderItem(Lnet/minecraft/world/item/ItemStack;III)V",
            at = @At("HEAD"), cancellable = true)
    private void opendreamcore$overrideIconSeed(ItemStack stack, int x, int y, int seed, CallbackInfo ci) {
        opendreamcore$overrideIcon(stack, x, y, ci);
    }

    // 四参重载：一并拦。处理器参数必须与目标逐个对齐，少写一个 int 就是启动崩溃（11.02 实机教训）。
    @Inject(method = "renderItem(Lnet/minecraft/world/item/ItemStack;IIII)V",
            at = @At("HEAD"), cancellable = true)
    private void opendreamcore$overrideIconSeed4(ItemStack stack, int x, int y, int seed, int extra, CallbackInfo ci) {
        opendreamcore$overrideIcon(stack, x, y, ci);
    }

    // 物品展示框/实体手持经 LivingEntity 重载：一并拦截，否则展示框图标绕过替换。
    @Inject(method = "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;III)V",
            at = @At("HEAD"), cancellable = true)
    private void opendreamcore$overrideIconEntity(net.minecraft.world.entity.LivingEntity entity, ItemStack stack, int x, int y, int seed, CallbackInfo ci) {
        opendreamcore$overrideIcon(stack, x, y, ci);
    }

    // renderFakeItem/fakeItem 直调路径（配方书等）：一并拦截，防绕过。
    @Inject(method = "renderFakeItem(Lnet/minecraft/world/item/ItemStack;II)V",
            at = @At("HEAD"), cancellable = true)
    private void opendreamcore$overrideIconFake(ItemStack stack, int x, int y, CallbackInfo ci) {
        opendreamcore$overrideIcon(stack, x, y, ci);
    }

}
