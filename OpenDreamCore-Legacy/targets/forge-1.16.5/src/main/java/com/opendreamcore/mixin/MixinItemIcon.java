package com.opendreamcore.mixin;

import com.opendreamcore.client.visual.LegacyVisualItemIcons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.vector.Matrix4f;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 物品图标覆写（1.16.5）：ItemIcon 规则命中就把原版图标换成自定义贴图。
 * renderGuiItem 是 2D 图标入口，画完 cancel 掉原版那套。
 */
@Mixin(ItemRenderer.class)
public abstract class MixinItemIcon {

    @Inject(method = "renderGuiItem(Lnet/minecraft/item/ItemStack;II)V",
            at = @At("HEAD"), cancellable = true)
    private void opendreamcore$overrideIcon(ItemStack stack, int x, int y, CallbackInfo ci) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        String id = ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
        String tex = LegacyVisualItemIcons.textureFor(id);
        if (tex == null) {
            return;
        }
        ResourceLocation rl = resolve(tex);
        Minecraft mc = Minecraft.getInstance();
        try {
            mc.getTextureManager().bind(rl);
        } catch (Exception ignored) {
            return;
        }
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        Matrix4f m = new Matrix4f();
        Tessellator tt = Tessellator.getInstance();
        BufferBuilder bb = tt.getBuilder();
        bb.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR_TEX);
        bb.vertex(m, x, y, 0.0F).color(1.0F, 1.0F, 1.0F, 1.0F).uv(0.0F, 0.0F).endVertex();
        bb.vertex(m, x, y + 16, 0.0F).color(1.0F, 1.0F, 1.0F, 1.0F).uv(0.0F, 1.0F).endVertex();
        bb.vertex(m, x + 16, y + 16, 0.0F).color(1.0F, 1.0F, 1.0F, 1.0F).uv(1.0F, 1.0F).endVertex();
        bb.vertex(m, x + 16, y, 0.0F).color(1.0F, 1.0F, 1.0F, 1.0F).uv(1.0F, 0.0F).endVertex();
        Tessellator.getInstance().end();
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        ci.cancel();
    }

    /** 覆写贴图路径 → RL：散装表优先（中文文件名也能命中），不带命名空间挂 opendreamcore。 */
    private static ResourceLocation resolve(String texture) {
        String loose = com.opendreamcore.client.LooseTextureLoader.lookup(texture);
        if (loose != null) {
            return new ResourceLocation(loose);
        }
        int colon = texture.indexOf(':');
        if (colon > 0) {
            return new ResourceLocation(texture);
        }
        return new ResourceLocation("opendreamcore", texture);
    }
}