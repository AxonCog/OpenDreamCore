package com.opendreamcore.legacy;

import com.opendreamcore.client.visual.LegacyFontReplace;
import com.opendreamcore.client.visual.LegacyTtfSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/**
 * 全局字符替换字体（1.6.4）：与 1.7.10 那版结构一致，换掉 Minecraft.fontRenderer，
 * 聊天/输入/所有文本全接管；命中 FontConfig 规则的字符画彩色贴图
 * （png 静态或 gif 动图，动图由帧表切 uv 出动画）。
 *
 * 这代没 GlStateManager，直接摸 GL11，画完关 blend 回白状态收工。
 *
 * 绘制之外，量宽与裁切路径同样接管：getStringWidth / getCharWidth /
 * trimStringToWidth / splitStringWidth 都按替换字形宽度算。只换绘制的话，
 * 居中与右对齐照原版字符宽去排，行里一出现替换字就整体错位，越靠后偏得越多；
 * 裁切（书本翻页、tooltip 收尾省略号）也会截在错的位置。
 */
public class GlobalFontRenderer extends FontRenderer {

    /** 混合因子四项的 GL 枚举（GL1.4 的 RGB/Alpha 分离形式）：还原时要逐项写回。 */
    private static final int GL_BLEND = 0x0BE2;
    private static final int GL_BLEND_SRC_RGB = 0x80C9;
    private static final int GL_BLEND_DST_RGB = 0x80C8;
    private static final int GL_BLEND_SRC_ALPHA = 0x80CB;
    private static final int GL_BLEND_DST_ALPHA = 0x80CA;

    public GlobalFontRenderer(GameSettings settings, net.minecraft.client.renderer.texture.TextureManager textureManagerIn) {
        super(settings, new ResourceLocation("textures/font/ascii.png"), textureManagerIn,
                settings.language != null && settings.language.equals("zh_CN"));
    }

    @Override
    public int drawString(String text, int x, int y, int color, boolean shadow) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        // 没配替换规则时不能直接整行直通 vanilla：全局字体是渲染期光栅出来的软字形，
        // 不产规则条目，所以 hasAny 为假但配了 ttf 的场景同样得自己走。
        if (!LegacyFontReplace.hasAny() && !LegacyTtfSource.enabled()) {
            return super.drawString(text, x, y, color, shadow);
        }
        if (!containsReplaced(text) && !containsTtf(text)) {
            return super.drawString(text, x, y, color, shadow);
        }
        float fx = x;
        int i = 0;
        StringBuilder seg = new StringBuilder();
        while (i < text.length()) {
            char c = text.charAt(i);
            LegacyFontReplace.Glyph g = LegacyFontReplace.hasAny() ? LegacyFontReplace.glyphFor(c) : null;
            if (g == null) {
                // 没配替换贴图就看有没有全局字体：命中就画 TTF 字形，没命中照样并进普通段丢回原版
                LegacyTtfSource.Glyph tg = LegacyTtfSource.enabled() ? LegacyTtfSource.get(c) : null;
                if (tg == null) {
                    seg.append(c);
                    i++;
                    continue;
                }
                if (seg.length() > 0) {
                    fx += super.drawString(seg.toString(), (int) fx, y, color, shadow);
                    seg.setLength(0);
                }
                drawTtfGlyph(tg, fx, y + 1.0F, color, shadow);
                fx += tg.advance + (shadow ? 1.0F : 0.0F);
                i++;
                continue;
            }
            if (seg.length() > 0) {
                fx += super.drawString(seg.toString(), (int) fx, y, color, shadow);
                seg.setLength(0);
            }
            drawGlyph(g, fx, y + 1.0F, color, shadow);
            fx += g.fontWidth + (shadow ? 1.0F : 0.0F);
            i++;
        }
        if (seg.length() > 0) {
            fx += super.drawString(seg.toString(), (int) fx, y, color, shadow);
        }
        return (int) Math.ceil(fx - x);
    }

    // 1.6.4 原版没有 4 参重载，老代码里到处直接调它，保一个便捷版本（不标 Override）
    public int drawString(String text, int x, int y, int color) {
        return drawString(text, x, y, color, false);
    }

    @Override
    public int getStringWidth(String text) {
        if (text == null || text.isEmpty()
                || (!LegacyFontReplace.hasAny() && !LegacyTtfSource.enabled())) {
            return super.getStringWidth(text);
        }
        int w = 0;
        int i = 0;
        StringBuilder seg = new StringBuilder();
        while (i < text.length()) {
            char c = text.charAt(i);
            // 颜色码整对丢回原版：本身不占宽度，但会影响后文，不能拆开算
            if (c == '\u00a7' && i + 1 < text.length()) {
                seg.append(c).append(text.charAt(i + 1));
                i += 2;
                continue;
            }
            LegacyFontReplace.Glyph g = LegacyFontReplace.hasAny() ? LegacyFontReplace.glyphFor(c) : null;
            if (g == null) {
                // 量宽与绘制取同一份度量（tg.advance），行宽才对得上实际画出来的样子
                LegacyTtfSource.Glyph tg = LegacyTtfSource.enabled() ? LegacyTtfSource.get(c) : null;
                if (tg == null) {
                    seg.append(c);
                    i++;
                    continue;
                }
                if (seg.length() > 0) {
                    w += super.getStringWidth(seg.toString());
                    seg.setLength(0);
                }
                w += tg.advance;
                i++;
                continue;
            }
            if (seg.length() > 0) {
                w += super.getStringWidth(seg.toString());
                seg.setLength(0);
            }
            w += g.fontWidth;
            i++;
        }
        if (seg.length() > 0) {
            w += super.getStringWidth(seg.toString());
        }
        return w;
    }

    @Override
    public int getCharWidth(char c) {
        LegacyFontReplace.Glyph g = LegacyFontReplace.hasAny() ? LegacyFontReplace.glyphFor(c) : null;
        if (g != null) {
            return g.fontWidth;
        }
        LegacyTtfSource.Glyph tg = LegacyTtfSource.enabled() ? LegacyTtfSource.get(c) : null;
        return tg != null ? tg.advance : super.getCharWidth(c);
    }

    /**
     * 按像素宽裁切：量宽与绘制共用同一套替换宽度，截出来的行才不会比实际画出来的宽/窄。
     * 从尾往前收字符，逐字扣宽度，颜色码不占宽度（整对丢掉）。
     */
    @Override
    public String trimStringToWidth(String text, int width, boolean reverse) {
        if (text == null || text.isEmpty()
                || (!LegacyFontReplace.hasAny() && !LegacyTtfSource.enabled())
                || (!containsReplaced(text) && !containsTtf(text))) {
            return super.trimStringToWidth(text, width, reverse);
        }
        if (width <= 0) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        int used = 0;
        int i = reverse ? text.length() - 1 : 0;
        while (reverse ? i >= 0 : i < text.length()) {
            char c = text.charAt(i);
            if (c == '\u00a7' && i + 1 < text.length()) {
                out.append(c).append(text.charAt(i + 1));
                i += reverse ? -2 : 2;
                continue;
            }
            LegacyFontReplace.Glyph g = LegacyFontReplace.hasAny() ? LegacyFontReplace.glyphFor(c) : null;
            int cw;
            if (g != null) {
                cw = g.fontWidth;
            } else {
                LegacyTtfSource.Glyph tg = LegacyTtfSource.enabled() ? LegacyTtfSource.get(c) : null;
                cw = tg != null ? tg.advance : super.getCharWidth(c);
            }
            if (used + cw > width) {
                break;
            }
            used += cw;
            out.append(c);
            i += reverse ? -1 : 1;
        }
        return reverse ? out.reverse().toString() : out.toString();
    }

    @Override
    public String trimStringToWidth(String text, int width) {
        return trimStringToWidth(text, width, false);
    }

    @Override
    public int splitStringWidth(String text, int width) {
        // 原版是按行拆多行的行高口径；替换字只改宽度不改行高，直接透传即可
        return super.splitStringWidth(text, width);
    }

    private static boolean containsReplaced(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (LegacyFontReplace.glyphFor(text.charAt(i)) != null) {
                return true;
            }
        }
        return false;
    }

    /** 整行里有没有需要全局字体绘制的字符（快路径短路用）。 */
    private static boolean containsTtf(String text) {
        if (!LegacyTtfSource.enabled()) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                continue;
            }
            if (LegacyTtfSource.get(c) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * 画一个全局字体字形：共享层已把它光栅成位图并注册成纹理，这里只负责摆位置。
     * 字形是白色位图，颜色由调用方传进来（跟替换贴图同一条通道），
     * 这样同一行里 TTF 字与原版字是同一个颜色。
     */
    private static void drawTtfGlyph(LegacyTtfSource.Glyph tg, float x, float y, int argb, boolean shadow) {
        try {
            String rlStr = LegacyTtfSource.textureOf(tg);
            if (rlStr == null) {
                return;
            }
            ResourceLocation rl = new ResourceLocation(rlStr);
            Minecraft.getMinecraft().getTextureManager().bindTexture(rl);
            float top = y + tg.top;
            drawTintedQuad(x, top, tg.image.getWidth(), tg.image.getHeight(),
                    0.0F, 0.0F, 1.0F, 1.0F, argb, shadow);
        } catch (Throwable ignored) {
            // 上传/绘制失败就当这个字没替换，别把异常带进字体渲染
        }
    }

    /** 把命中字符画成纹理 quad：uv 按横向等分归一化。颜色由调用方给——这代原版字体就是走 GL 当前色染色的。 */
    private static void drawGlyph(LegacyFontReplace.Glyph g, float x, float y, int argb, boolean shadow) {
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
        drawTintedQuad(x, y, w, h, u0, 0.0F, u1, 1.0F, argb, shadow);
    }

    /**
     * 按调用方给的颜色画一个贴图 quad。shadow 为真时先右下偏移一格画一份压暗四分之一的副本，
     * 与原版带影文本「压暗副本 + 正常字」两层的观感一致。
     *
     * <p>颜色一路传到这里是有原因的：原版字体的染色通道就是 GL 当前色，替换字形只要也写当前色，
     * 就和同行的原版字同一个颜色；以前写死白色，彩色文本里的替换字会突兀地变成白字。
     *
     * <p>混合开关与因子画完后按进本方法时的原值写回：字体路径没有世界相位那样的统一总闸
     * （见 RenderStateSnapshot），而替换字形是插在原版文本段之间画的。原版文本渲染结束时混合是开着的，
     * 以前画完无条件关混合，多画一个替换字就把后续 HUD 的混合关掉了。
     */
    private static void drawTintedQuad(float x, float y, float w, float h,
                                       float u0, float v0, float u1, float v1,
                                       int argb, boolean shadow) {
        float a = ((argb >>> 24) & 0xFF) / 255.0F;
        float r = ((argb >> 16) & 0xFF) / 255.0F;
        float g = ((argb >> 8) & 0xFF) / 255.0F;
        float b = (argb & 0xFF) / 255.0F;
        int prevSrcRgb = GL11.glGetInteger(GL_BLEND_SRC_RGB);
        int prevDstRgb = GL11.glGetInteger(GL_BLEND_DST_RGB);
        int prevSrcAlpha = GL11.glGetInteger(GL_BLEND_SRC_ALPHA);
        int prevDstAlpha = GL11.glGetInteger(GL_BLEND_DST_ALPHA);
        boolean blendWasOn = GL11.glGetBoolean(GL_BLEND);
        try {
            GL11.glEnable(GL11.GL_BLEND);
            GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            if (shadow) {
                GL11.glColor4f(r * 0.25F, g * 0.25F, b * 0.25F, a);
                rawQuad(x + 1.0F, y + 1.0F, w, h, u0, v0, u1, v1);
            }
            GL11.glColor4f(r, g, b, a);
            rawQuad(x, y, w, h, u0, v0, u1, v1);
        } finally {
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            if (blendWasOn) {
                GL11.glEnable(GL11.GL_BLEND);
                GL14.glBlendFuncSeparate(prevSrcRgb, prevDstRgb, prevSrcAlpha, prevDstAlpha);
            } else {
                GL11.glDisable(GL11.GL_BLEND);
            }
        }
    }

    /** 裸 quad：颜色由调用前设好的 GL 当前色决定。 */
    private static void rawQuad(float x, float y, float w, float h,
                                float u0, float v0, float u1, float v1) {
        net.minecraft.client.renderer.Tessellator tess = net.minecraft.client.renderer.Tessellator.instance;
        tess.startDrawingQuads();
        tess.addVertexWithUV(x, y, 0.0, u0, v0);
        tess.addVertexWithUV(x, y + h, 0.0, u0, v1);
        tess.addVertexWithUV(x + w, y + h, 0.0, u1, v1);
        tess.addVertexWithUV(x + w, y, 0.0, u1, v0);
        tess.draw();
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
        return new ResourceLocation(OdcLegacy164.MODID, texture);
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
