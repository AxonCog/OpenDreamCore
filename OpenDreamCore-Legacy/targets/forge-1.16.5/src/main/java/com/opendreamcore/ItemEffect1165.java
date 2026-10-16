package com.opendreamcore;

import com.opendreamcore.client.visual.LegacyVisualItemEffects;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.vector.Matrix4f;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.opengl.GL11;

/** Shared GUI effect consumer for vanilla and replacement item rendering. */
public final class ItemEffect1165 {
    private ItemEffect1165() { }

    public static void render(ItemStack stack, int x, int y) {
        if (stack == null || stack.isEmpty()) return;
        String id = ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
        LegacyVisualItemEffects.Effect fx = LegacyVisualItemEffects.effectFor(id);
        if (fx == null) return;
        int argb = fx.color;
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
        tt.end();
        com.mojang.blaze3d.systems.RenderSystem.enableTexture();
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
    }
}
