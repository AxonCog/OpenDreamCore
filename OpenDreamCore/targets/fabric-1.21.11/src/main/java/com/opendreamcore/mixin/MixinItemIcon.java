package com.opendreamcore.mixin;

import com.opendreamcore.client.model.GenItemModelRenderer;
import com.opendreamcore.client.resources.LooseResourceLoader;
import com.opendreamcore.client.visual.VisualItemSkins;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 物品图标覆写（1.21.11 专用）：这版 GuiGraphics 只剩 9 参 blit（uv 比例）。
 * 整图直出零切割：uv 全幅（0-1）就是整张贴图；gif 用帧表当前帧切片
 * （tickAll 推进，自动播动画）；规则按显示名匹配（textureFor 带 hoverName）。
 */
@Mixin(GuiGraphics.class)
public abstract class MixinItemIcon {

    @Inject(method = "renderItem(Lnet/minecraft/world/item/ItemStack;II)V",
            at = @At("HEAD"), cancellable = true)
    private void opendreamcore$overrideIcon(ItemStack stack, int x, int y, CallbackInfo ci) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        VisualItemSkins.Selection selected = VisualItemSkins.select(id, stack);
        if (selected == null) {
            if (GenItemModelRenderer.tryGui((Object) this, stack, x, y)) ci.cancel();
            return;
        }
        var si = LooseResourceLoader.sheetOf(selected.texture());
        if (si == null) {
            if (GenItemModelRenderer.tryGui((Object) this, stack, x, y)) ci.cancel();
            return;
        }
        int size = VisualItemSkins.guiSize(selected);
        int dx = x + (16 - size) / 2;
        int dy = y + (16 - size) / 2;
        int frame = VisualItemSkins.frame(selected, si);
        float step = 1.0F / si.frames();
        float u0 = frame * step;
        float u1 = u0 + step;
        Identifier rl = si.rl();
        GuiGraphics g = (GuiGraphics) (Object) this;
        try {
            g.blit(rl, dx, dy, size, size, u0, 0.0F, u1, 1.0F);
            ci.cancel();
        } catch (Throwable ignored) {
        }
    }
    // 物品展示框/实体手持经 LivingEntity 重载：一并拦截，否则展示框图标绕过替换。
    // 容器槽位三参重载（seed）：TrMenu 等菜单的图标从这条路出（1.21.1 实机实证）。
    @Inject(method = "renderItem(Lnet/minecraft/world/item/ItemStack;III)V",
            at = @At("HEAD"), cancellable = true)
    private void opendreamcore$overrideIconSeed(ItemStack stack, int x, int y, int seed, CallbackInfo ci) {
        opendreamcore$overrideIcon(stack, x, y, ci);
    }

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
