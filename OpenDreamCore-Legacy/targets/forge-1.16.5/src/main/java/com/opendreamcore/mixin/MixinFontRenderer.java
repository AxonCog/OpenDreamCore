package com.opendreamcore.mixin;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import com.opendreamcore.client.visual.LegacyFontReplace;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 1.16.5 全局字符替换：mc.font 是 final 字段换不了实例，干脆 mixin 到
 * FontRenderer.draw 字符串入口——游戏里所有 `mc.font.draw(...)` 都先过这，
 * 命中 FontConfig 的字符画彩色贴图，其余整段递归回原方法。
 *
 * 拆段递归是安全的：段里没有命中字符时早退走原逻辑，不会死循环。
 */
@Mixin(FontRenderer.class)
public abstract class MixinFontRenderer {

    @Inject(method = "draw(Lcom/mojang/blaze3d/matrix/MatrixStack;Ljava/lang/String;FFF I;)I",
            at = @At("HEAD"), cancellable = true)
    private void opendreamcore$replaceGlyph(MatrixStack stack, String text, float x, float y, int color,
                                            CallbackInfoReturnable<Integer> cir) {
        if (text == null || text.isEmpty() || !LegacyFontReplace.hasAny() || !containsReplaced(text)) {
            return;
        }
        float fx = x;
        int i = 0;
        StringBuilder seg = new StringBuilder();
        while (i < text.length()) {
            char c = text.charAt(i);
            LegacyFontReplace.Glyph g = LegacyFontReplace.glyphFor(c);
            if (g == null) {
                seg.append(c);
                i++;
                continue;
            }
            if (seg.length() > 0) {
                fx += ((FontRenderer) (Object) this).draw(stack, seg.toString(), fx, y, color);
                seg.setLength(0);
            }
            drawGlyph(g, stack, fx, y);
            fx += g.fontWidth + 1.0F;
            i++;
        }
        if (seg.length() > 0) {
            fx += ((FontRenderer) (Object) this).draw(stack, seg.toString(), fx, y, color);
        }
        cir.setReturnValue((int) Math.ceil(fx - x));
    }

    private static boolean containsReplaced(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (LegacyFontReplace.glyphFor(text.charAt(i)) != null) {
                return true;
            }
        }
        return false;
    }

    /** 1.16.5 的 GUI 即时渲染：bind 贴图 + Tessellator 画 quad，UV 按横向等分归一化。 */
    private static void drawGlyph(LegacyFontReplace.Glyph g, MatrixStack stack, float x, float y) {
        ResourceLocation rl = resolve(g.texture);
        net.minecraft.client.Minecraft.getInstance().getTextureManager().bind(rl);
        float w = Math.max(1, g.frameW);
        float h = Math.max(1, g.frameH);
        int frames = Math.max(1, g.totalFrames);
        float u0 = (float) g.u / frames;
        float u1 = (float) (g.u + 1) / frames;
        float v0 = 0.0F;
        float v1 = 1.0F;
        RenderSystem.enableBlend();
        RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        net.minecraft.util.math.vector.Matrix4f pose = stack.last().pose();
        BufferBuilder bb = Tessellator.getInstance().getBuilder();
        bb.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR_TEX);
        bb.vertex(pose, x, y, 0.0F).color(1.0F, 1.0F, 1.0F, 1.0F).uv(u0, v0).endVertex();
        bb.vertex(pose, x, y + h, 0.0F).color(1.0F, 1.0F, 1.0F, 1.0F).uv(u0, v1).endVertex();
        bb.vertex(pose, x + w, y + h, 0.0F).color(1.0F, 1.0F, 1.0F, 1.0F).uv(u1, v1).endVertex();
        bb.vertex(pose, x + w, y, 0.0F).color(1.0F, 1.0F, 1.0F, 1.0F).uv(u1, v0).endVertex();
        Tessellator.getInstance().end();
        RenderSystem.disableBlend();
    }

    private static ResourceLocation resolve(String texture) {
        // 散装贴图（含中文文件名）优先：查动态纹理注册表
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