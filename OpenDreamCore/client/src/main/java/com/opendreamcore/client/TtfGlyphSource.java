package com.opendreamcore.client;

import com.opendreamcore.client.visual.VisualFontReplace;
import com.opendreamcore.ui.TtfFont;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 全局 TTF 字形源（FontConfig 的 ttf: 键，如微软雅黑）：AWT 软渲染 → CJK 语义缩放 →
 * 每字形一张静态纹理。供各版本字体 mixin 接入（1.21.8 FontSet / 其余 StringRenderOutput / GuiText）。
 * 任何异常/缺字体/无字形都返回 null——调用方回退原版字形，绝不崩渲染线程。
 */
public final class TtfGlyphSource {

    /** 一个可渲染的 TTF 字形：advance=排版宽；image=缩放后 ARGB 位图；top=字形框顶相对基线（负=向上）。 */
    public record Glyph(int codePoint, float advance, BufferedImage image, float top) {
    }

    /** 字形缓存（键=ttf路径#码点；服务端换字体路径自动失效）。 */
    private static final Map<String, Glyph> CACHE = new ConcurrentHashMap<>();
    private static final int CACHE_MAX = 4096;

    /** 纹理缓存（键=码点；纹理内容随字形缓存失效——路径变更时一并清理）。 */
    private static final Map<Integer, ResourceLocation> TEXTURES = new ConcurrentHashMap<>();

    private TtfGlyphSource() {
    }

    /** 全局 TTF 是否可用（快路径短路：没有配置直接 false，零开销）。 */
    public static boolean enabled() {
        String path = VisualFontReplace.defaultTtf();
        return path != null && !path.isBlank();
    }

    /** 取字形（缓存）；无 ttf/无字形/异常 → null。 */
    public static Glyph get(int codePoint) {
        try {
            String path = VisualFontReplace.defaultTtf();
            if (path == null || path.isBlank()) {
                return null;
            }
            String key = path + "#" + codePoint;
            Glyph cached = CACHE.get(key);
            if (cached != null) {
                return cached;
            }
            TtfRenderer renderer = CustomFonts.getByPath(path);
            if (renderer == null) {
                return null;
            }
            Glyph g = render((char) codePoint, renderer);
            if (g == null) {
                return null;
            }
            if (CACHE.size() >= CACHE_MAX) {
                CACHE.clear(); // 防御性上限：重建缓存（毫秒级）
            }
            CACHE.put(key, g);
            return g;
        } catch (Throwable t) {
            return null;
        }
    }

    /** AWT 渲染 + CJK 语义缩放（全角汉字宽 ≈ 9px 字体行高基准）。 */
    private static Glyph render(char ch, TtfRenderer renderer) {
        TtfFont font = renderer.font();
        if (font == null) {
            return null;
        }
        int base = font.advance('中');
        if (base <= 0) {
            base = Math.max(1, font.lineHeight());
        }
        float scale = 9.0F / base;
        BufferedImage glyph = font.renderGlyph(ch);
        if (glyph == null) {
            return null; // 空白/无字形：回退原版
        }
        int w = Math.max(1, Math.round(glyph.getWidth() * scale));
        int h = Math.max(1, Math.round(glyph.getHeight() * scale));
        BufferedImage scaled = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = scaled.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.drawImage(glyph, 0, 0, w, h, null);
        g2.dispose();
        float top = -Math.round(font.ascent() * scale); // 基线对齐
        return new Glyph(ch, Math.max(1, Math.round(font.advance(ch) * scale)), scaled, top);
    }

    /** 字形位图 → 独立静态纹理（每码点一张，缓存复用）；失败返回 null。 */
    public static ResourceLocation textureFor(Glyph g) {
        try {
            ResourceLocation cached = TEXTURES.get(g.codePoint());
            if (cached != null) {
                return cached;
            }
            BufferedImage image = g.image();
            // 走编译期直调的 PNG 路径（跨映射可靠）；逐像素反射仅作双保险
            // （Fabric 生产环境方法名是 intermediary，按 Mojmap 名反射必失 → 字形全透明不可见）
            NativeImage img;
            try {
                img = com.opendreamcore.client.resources.LooseResourceLoader.toPngNative(image);
            } catch (Throwable t) {
                img = new NativeImage(image.getWidth(), image.getHeight(), true);
                for (int y = 0; y < image.getHeight(); y++) {
                    for (int x = 0; x < image.getWidth(); x++) {
                        CompatRender.nativeSetPixel(img, x, y, image.getRGB(x, y));
                    }
                }
            }
            ResourceLocation rl = CompatRender.rl("opendreamcore", "ttf/" + Integer.toHexString(g.codePoint()) + ".png");
            // TEXTURES map 即存在性真相源（对齐 GifPlayer 的 staticTex 模式，
            // 不调 getTextureView/getTexture——老映射没有这些方法）
            Minecraft.getInstance().getTextureManager().register(rl, CompatRender.newDynamicTexture(img));
            TEXTURES.put(g.codePoint(), rl);
            return rl;
        } catch (Throwable t) {
            return null;
        }
    }
}
