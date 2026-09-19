package com.opendreamcore.legacy;

import com.opendreamcore.client.visual.LegacyFontReplace;
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
 * 只要走字体渲染，命中 FontConfig 规则的字符就被换成彩色贴图。
 *
 * 实现上不碰 renderStringAtPos 的整段 vanilla 循环（那东西又臭又长，抄一遍
 * 准能抄出颜色码的边角 bug），改在 drawString 收口拆段：
 *   普通段（含颜色码、随机字符之类）整段丢回 super，交给原版处理；
 *   命中字符自己画一张纹理 quad，宽度按 fontWidth 推进。
 * 两段宽度加起来就是整行宽度，原样返回给调用方。
 */
public class GlobalFontRenderer extends FontRenderer {

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
        // 没配替换规则整行直通 vanilla，零额外开销
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

    // 原版全 float，保这些 int 便捷重载给老代码链走（不标 Override）
    public int drawString(String text, int x, int y, int color) {
        return drawString(text, x, y, color, false);
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

    /** 把命中字符画成纹理 quad：uv 按横向等分归一化，跟 image 元素同一套语义。 */
    private static void drawGlyph(LegacyFontReplace.Glyph g, float x, float y, boolean shadow) {
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
        float v0 = 0.0F;
        float v1 = 1.0F;

        GlStateManager.enableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder bb = tess.getBuffer();
        bb.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        bb.pos(x, y, 0.0D).tex(u0, v0).endVertex();
        bb.pos(x, y + h, 0.0D).tex(u0, v1).endVertex();
        bb.pos(x + w, y + h, 0.0D).tex(u1, v1).endVertex();
        bb.pos(x + w, y, 0.0D).tex(u1, v0).endVertex();
        tess.draw();
        GlStateManager.disableBlend();
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