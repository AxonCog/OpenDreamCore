package com.opendreamcore.legacy;

import com.opendreamcore.client.visual.LegacyFontReplace;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * 全局字符替换字体（1.7.10）：跟 1.12.2 同款思路，换掉 Minecraft.fontRenderer，
 * 聊天/输入/所有文本全接管；命中 FontConfig 规则的字符画彩色贴图。
 *
 * 这代没有 GlStateManager，GL 状态全部直接摸 GL11，画完恢复灰常简单：
 * blend 关掉、颜色回白，别把事情想复杂了。
 */
public class GlobalFontRenderer extends FontRenderer {

    public GlobalFontRenderer(GameSettings settings, net.minecraft.client.renderer.texture.TextureManager textureManagerIn) {
        super(settings, new ResourceLocation("textures/font/ascii.png"), textureManagerIn,
                settings.language != null && settings.language.equals("zh_CN"));
    }

    @Override
    public int drawString(String text, int x, int y, int color, boolean shadow) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        if (!LegacyFontReplace.hasAny() || !containsReplaced(text)) {
            return super.drawString(text, x, y, color, shadow);
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
                fx += super.drawString(seg.toString(), (int) fx, y, color, shadow);
                seg.setLength(0);
            }
            drawGlyph(g, fx, y + 1.0F, shadow);
            fx += g.fontWidth + (shadow ? 1.0F : 0.0F);
            i++;
        }
        if (seg.length() > 0) {
            fx += super.drawString(seg.toString(), (int) fx, y, color, shadow);
        }
        return (int) Math.ceil(fx - x);
    }

    // 老版本还有一堆直接调 4 参的代码，保个便捷重载，不标 Override（原版没有它）
    public int drawString(String text, int x, int y, int color) {
        return drawString(text, x, y, color, false);
    }

    private static boolean containsReplaced(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (LegacyFontReplace.glyphFor(text.charAt(i)) != null) {
                return true;
            }
        }
        return false;
    }

    private static void drawGlyph(LegacyFontReplace.Glyph g, float x, float y, boolean shadow) {
        ResourceLocation rl = resolve(g.texture);
        Minecraft mc = Minecraft.getMinecraft();
        try {
            mc.getTextureManager().bindTexture(rl);
        } catch (Exception ignored) {
            return;
        }
        float w = Math.max(1, g.frameW);
        float h = Math.max(1, g.frameH);
        int frames = Math.max(1, g.totalFrames);
        float u0 = (float) g.u / frames;
        float u1 = (float) (g.u + 1) / frames;
        float v0 = 0.0F;
        float v1 = 1.0F;
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        net.minecraft.client.renderer.Tessellator tess = net.minecraft.client.renderer.Tessellator.instance;
        tess.startDrawingQuads();
        tess.addVertexWithUV(x, y, 0.0, u0, v0);
        tess.addVertexWithUV(x, y + h, 0.0, u0, v1);
        tess.addVertexWithUV(x + w, y + h, 0.0, u1, v1);
        tess.addVertexWithUV(x + w, y, 0.0, u1, v0);
        tess.draw();
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static ResourceLocation resolve(String texture) {
        // 散装贴图（含中文文件名）优先
        String loose = com.opendreamcore.client.LooseTextureLoader.lookup(texture);
        if (loose != null) {
            return new ResourceLocation(loose);
        }
        int colon = texture.indexOf(':');
        if (colon > 0) {
            return new ResourceLocation(texture);
        }
        return new ResourceLocation(OdcLegacy1710.MODID, texture);
    }

    /** 全局替换安装：幂等。 */
    private static boolean installed;

    public static void install(Minecraft mc) {
        if (installed || mc.fontRenderer instanceof GlobalFontRenderer) {
            return;
        }
        GlobalFontRenderer gf = new GlobalFontRenderer(mc.gameSettings, mc.getTextureManager());
        mc.fontRenderer = gf;
        installed = true;
    }
}