package com.opendreamcore.legacy;

import com.opendreamcore.client.visual.LegacyFontReplace;
import com.opendreamcore.client.visual.LegacyTtfSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * 全局字符替换字体（1.12.2）：继承原版 FontRenderer 并整体替换
 * Minecraft.fontRenderer——聊天、输入框、书、箱子名、所有 UI 的文本，
 * 只要走字体渲染，命中 FontConfig 规则的字符就被换成彩色贴图
 * （png 静态或 gif 动图，动图由帧表切 uv 出动画）。
 *
 * 实现上不碰 renderStringAtPos 的整段 vanilla 循环（那东西又臭又长，抄一遍
 * 准能抄出颜色码的边角 bug），改在 drawString 收口拆段：
 *   普通段（含颜色码、随机字符之类）整段丢回 super，交给原版处理；
 *   命中字符自己画一张纹理 quad，宽度按 fontWidth 推进。
 * 两段宽度加起来就是整行宽度，原样返回给调用方。
 *
 * 绘制之外，量宽与裁切路径同样接管：viewWidth / getCharWidth /
 * trimStringToWidth 都按替换字形宽度算。只换绘制的话，居中与右对齐
 * 会照原版宽度去排，行里出现替换字就整体错位；裁切也会截在错的位置。
 */
public class GlobalFontRenderer extends FontRenderer {

    /** 混合因子四项的 GL 枚举（GL1.4 的 RGB/Alpha 分离形式）：还原时要逐项写回。 */
    private static final int GL_BLEND = 0x0BE2;
    private static final int GL_BLEND_SRC_RGB = 0x80C9;
    private static final int GL_BLEND_DST_RGB = 0x80C8;
    private static final int GL_BLEND_SRC_ALPHA = 0x80CB;
    private static final int GL_BLEND_DST_ALPHA = 0x80CA;

    public GlobalFontRenderer(GameSettings settings, TextureManager textureManagerIn) {
        super(settings, new ResourceLocation("textures/font/ascii.png"), textureManagerIn,
                settings.language != null && settings.language.equals("zh_CN"));
    }

    @Override
    // 1.12.2 的原版重载全是 float 坐标（int 版压根不存在），这里就接 float。
    public int drawString(String text, float x, float y, int color, boolean shadow) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        // 没配替换规则时不能直接整行直通 vanilla：全局字体是渲染期的软字形，
        // 不产规则条目，所以 hasAny 为假但配了 ttf 的场景同样得自己走
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
            LegacyFontReplace.Glyph g = LegacyFontReplace.glyphFor(c);
            if (g == null) {
                // 没配替换贴图就看有没有全局字体：命中了画 TTF 字形，
                // 没命中照样并进普通段丢回原版，一个字都不多画
                LegacyTtfSource.Glyph tg = LegacyTtfSource.enabled() ? LegacyTtfSource.get(c) : null;
                if (tg == null) {
                    seg.append(c);
                    i++;
                    continue;
                }
                if (seg.length() > 0) {
                    fx += super.drawString(seg.toString(), fx, y, color, shadow);
                    seg.setLength(0);
                }
                drawTtfGlyph(tg, fx, y + 1.0F, color, shadow);
                fx += tg.advance + (shadow ? 1.0F : 0.0F);
                i++;
                continue;
            }
            if (seg.length() > 0) {
                fx += super.drawString(seg.toString(), fx, y, color, shadow);
                seg.setLength(0);
            }
            drawGlyph(g, fx, y + 1.0F, color, shadow);
            fx += g.fontWidth + (shadow ? 1.0F : 0.0F);
            i++;
        }
        if (seg.length() > 0) {
            fx += super.drawString(seg.toString(), fx, y, color, shadow);
        }
        return (int) Math.ceil(fx - x);
    }

    // 1.12.2 的原版没有 int 坐标重载，保个便捷版本给老代码链走（不标 Override）
    public int drawString(String text, int x, int y, int color) {
        return drawString(text, (float) x, (float) y, color, false);
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
            LegacyFontReplace.Glyph g = LegacyFontReplace.glyphFor(c);
            if (g == null) {
                // 没命中替换贴图时按全局字体量宽：绘制与量宽取同一份度量，
                // 行宽才对得上画出来的样子
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
            LegacyFontReplace.Glyph g = LegacyFontReplace.glyphFor(c);
            int cw = g != null ? g.fontWidth : super.getCharWidth(c);
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

    /** 整行里有没有命中字符（快速路过）。 */
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
     * 画一个全局字体字形：位图上传成纹理后画 quad。字形是白色位图，调用方要什么颜色就由 GL 当前色染，
     * 这样带颜色的文本里替换字和原版字是同一个色。
     */
    private static void drawTtfGlyph(LegacyTtfSource.Glyph tg, float x, float y, int argb, boolean shadow) {
        try {
            String rlStr = LegacyTtfSource.textureOf(tg);
            if (rlStr == null) {
                return;
            }
            ResourceLocation rl = new ResourceLocation(rlStr);
            TextureManager tm = Minecraft.getMinecraft().getTextureManager();
            tm.bindTexture(rl);
            float top = y + tg.top;
            float w = tg.image.getWidth();
            float h = tg.image.getHeight();
            drawTintedQuad(x, top, w, h, 0.0F, 0.0F, 1.0F, 1.0F, argb, shadow);
        } catch (Throwable ignored) {
            // 上传/绘制失败就当这个字没替换，别把异常带进字体渲染
        }
    }

    /** 把命中字符画成纹理 quad：uv 按横向等分归一化，跟 image 元素同一套语义。 */
    private static void drawGlyph(LegacyFontReplace.Glyph g, float x, float y, int argb, boolean shadow) {
        ResourceLocation rl = resolve(g.texture);
        TextureManager tm = Minecraft.getMinecraft().getTextureManager();
        try {
            tm.bindTexture(rl);
        } catch (Exception ignored) {
            return; // 贴图加载失败就跳过这个字符，别让整行字体跟着崩
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
     * 与原版阴影的观感一致（原版带影文本就是「压暗副本 + 正常字」两层）。
     *
     * <p>为什么颜色要一路传到这里：这代原版字体的染色通道就是 GL 当前色
     * （FontRenderer.setColor 内部调 GlStateManager.color），替换字形只要也写当前色，
     * 就和同行的原版字同一个颜色——之前写死白色，彩色文本里的替换字会突兀地变成白字。
     *
     * <p>混合开关与因子在画完后按进本方法时的原值写回：字体路径不像世界相位那样有统一总闸
     * （见 RenderStateSnapshot），而替换字形是插在原版文本段之间画的。以前画完无条件
     * disableBlend，而原版文本渲染结束时混合是开着的，于是多画一个替换字就把后续 HUD 的混合
     * 关掉了——同一类“改了不还”的毛病，只不过发生在字体路径上。
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
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            if (shadow) {
                GlStateManager.color(r * 0.25F, g * 0.25F, b * 0.25F, a);
                rawQuad(x + 1.0F, y + 1.0F, w, h, u0, v0, u1, v1);
            }
            GlStateManager.color(r, g, b, a);
            rawQuad(x, y, w, h, u0, v0, u1, v1);
        } finally {
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            if (blendWasOn) {
                GlStateManager.enableBlend();
                GlStateManager.tryBlendFuncSeparate(prevSrcRgb, prevDstRgb, prevSrcAlpha, prevDstAlpha);
            } else {
                GlStateManager.disableBlend();
            }
        }
    }

    /** 裸 quad：颜色由调用前设好的 GL 当前色决定。 */
    private static void rawQuad(float x, float y, float w, float h,
                                float u0, float v0, float u1, float v1) {
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder bb = tess.getBuffer();
        bb.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        bb.pos(x, y, 0.0D).tex(u0, v0).endVertex();
        bb.pos(x, y + h, 0.0D).tex(u0, v1).endVertex();
        bb.pos(x + w, y + h, 0.0D).tex(u1, v1).endVertex();
        bb.pos(x + w, y, 0.0D).tex(u1, v0).endVertex();
        tess.draw();
    }

    /** 全局替换安装：幂等。换的是 Minecraft.fontRenderer 全局实例，一次就够。 */
    private static boolean installed;

    public static void install(Minecraft mc) {
        if (installed || mc.fontRenderer instanceof GlobalFontRenderer) {
            return;
        }
        GlobalFontRenderer gf = new GlobalFontRenderer(mc.gameSettings, mc.renderEngine);
        // 把旧实例的 unicode/双向渲染标志带过来，中文显示行为保持原样
        net.minecraft.client.gui.FontRenderer old = mc.fontRenderer;
        if (old != null) {
            gf.setUnicodeFlag(old.getUnicodeFlag());
            gf.setBidiFlag(old.getBidiFlag());
        }
        mc.fontRenderer = gf;
        installed = true;
    }

    /** 贴图路径 → ResourceLocation：带命名空间直接用，不带就挂模组命名空间。 */
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
        return new ResourceLocation(OdcLegacy.MODID, texture);
    }
}
