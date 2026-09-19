package com.opendreamcore.mixin;

import com.opendreamcore.client.visual.LegacyVisualItemEffects;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.vector.Matrix4f;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 物品特效（1.16.5）：renderGuiItem 画完图标叠特效层，发光=半透明扩散色块。
 */
@Mixin(ItemRenderer.class)
public abstract class MixinItemEffect {

    @Inject(method = "renderGuiItem(Lnet/minecraft/item/ItemStack;II)V",
            at = @At("RETURN"))
    private void opendreamcore$itemEffect(ItemStack stack, int x, int y, CallbackInfo ci) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        String id = ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
        LegacyVisualItemEffects.Effect fx = LegacyVisualItemEffects.effectFor(id);
        if (fx == null) {
            return;
        }
        drawFx(fx.color, x, y);
    }

    /** 特效色块：发光三层递减 alpha，描边四边条。 */
    private static void drawFx(int argb, int x, int y) {
        float a = ((argb >>> 24) & 0xFF) / 255.0F;
        float r = ((argb >>> 16) & 0xFF) / 255.0F;
        float g = ((argb >>> 8) & 0xFF) / 255.0F;
        float b = (argb & 0xFF) / 255.0F;
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.disableTexture();
        com.mojang.blaze3d.systems.RenderSystem.color4f(r, g, b, Math.max(0.05F, a * 0.20F));
        Matrix4f m = new Matrix4f();
        Tessellator tt = Tessellator.getInstance();
        BufferBuilder bb = tt.getBuilder();
        bb.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        bb.vertex(m, x - 3, y - 3, 0.0F).color(r, g, b, Math.max(0.05F, a * 0.20F)).endVertex();
        bb.vertex(m, x - 3, y + 19, 0.0F).color(r, g, b, Math.max(0.05F, a * 0.20F)).endVertex();
        bb.vertex(m, x + 19, y + 19, 0.0F).color(r, g, b, Math.max(0.05F, a * 0.20F)).endVertex();
        bb.vertex(m, x + 19, y - 3, 0.0F).color(r, g, b, Math.max(0.05F, a * 0.20F)).endVertex();
        Tessellator.getInstance().end();
        com.mojang.blaze3d.systems.RenderSystem.enableTexture();
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
    }
}