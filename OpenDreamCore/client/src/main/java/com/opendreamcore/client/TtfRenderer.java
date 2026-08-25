package com.opendreamcore.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.opendreamcore.ui.TtfFont;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TTF 文本渲染器：字形按需渲染进 512x512 图集（DynamicTexture），
 * 绘制时用 GuiGraphics.setColor 染色（字形存白色）+ blit 图块。
 * 支持换行、阴影（偏移两次绘制）、任意颜色/缩放。
 */
public final class TtfRenderer {

    private static final int PAGE_SIZE = 512;
    private static final int PAD = 1;

    /** 图集页：NativeImage + 动态纹理。 */
    private static final class Page {
        final NativeImage image;
        final DynamicTexture texture;
        final ResourceLocation id;
        int cursorX = PAD;
        int cursorY = PAD;
        int rowHeight = 0;

        Page(ResourceLocation id) {
            this.image = new NativeImage(PAGE_SIZE, PAGE_SIZE, true);
            this.texture = CompatRender.newDynamicTexture(image);
            this.id = id;
            net.minecraft.client.Minecraft.getInstance().getTextureManager().register(id, texture);
        }
    }

    /** 图集里的字形位置。 */
    private record Glyph(char c, Page page, int x, int y, int w, int h, int advance) {
    }

    private final TtfFont font;
    private final List<Page> pages = new ArrayList<>();
    private final Map<Character, Glyph> glyphs = new HashMap<>();
    private int pageCounter;

    public TtfRenderer(TtfFont font) {
        this.font = font;
    }

    public TtfFont font() {
        return font;
    }

    /** 文本宽度（含换行取最长行）。 */
    public double measure(String text, double scale) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        double max = 0;
        double line = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                max = Math.max(max, line);
                line = 0;
                continue;
            }
            line += font.advance(c) * scale;
        }
        return Math.max(max, line);
    }

    /** 行高（含行距）。 */
    public double lineHeight(double scale) {
        return font.lineHeight() * scale;
    }

    /** 绘制文本（x,y 为基线起点；换行自动下移）。 */
    public void draw(GuiGraphics g, String text, double x, double y, int color, double scale, boolean shadow) {
        if (text == null || text.isEmpty()) {
            return;
        }
        double alpha = ((color >>> 24) & 0xFF) / 255.0;
        if (alpha <= 0) {
            return;
        }
        float r = ((color >> 16) & 0xFF) / 255.0F;
        float gr = ((color >> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;
        double cx = x;
        double cy = y;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                cx = x;
                cy += font.lineHeight() * scale;
                continue;
            }
            if (c == ' ' || c == '\t' || c == '\r') {
                cx += font.advance(c) * scale;
                continue;
            }
            Glyph glyph = glyphOf(c);
            if (glyph == null) {
                cx += font.advance(c) * scale;
                continue;
            }
            int gw = (int) Math.max(1, glyph.w * scale);
            int gh = (int) Math.max(1, glyph.h * scale);
            int gx = (int) cx;
            int gy = (int) (cy - font.ascent() * scale);
            if (shadow) {
                CompatRender.setDrawColor(g, 0.0F, 0.0F, 0.0F, (float) alpha);
                CompatRender.blit(g, glyph.page.id, gx + 1, gy + 1, gw, gh,
                        glyph.x, glyph.y, glyph.w, glyph.h, PAGE_SIZE, PAGE_SIZE);
            }
            CompatRender.setDrawColor(g, r, gr, b, (float) alpha);
            CompatRender.blit(g, glyph.page.id, gx, gy, gw, gh,
                    glyph.x, glyph.y, glyph.w, glyph.h, PAGE_SIZE, PAGE_SIZE);
            cx += glyph.advance * scale;
        }
        CompatRender.setDrawColor(g, 1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** 取字形（无则渲染进图集）；空白字符返回 null。 */
    private Glyph glyphOf(char c) {
        Glyph cached = glyphs.get(c);
        if (cached != null) {
            return cached;
        }
        BufferedImage img = font.renderGlyph(c);
        if (img == null) {
            return null;
        }
        Page page = ensureSpace(img.getWidth() + PAD, img.getHeight() + PAD);
        // 拷贝到图集（ARGB → RGBA）
        for (int py = 0; py < img.getHeight(); py++) {
            for (int px = 0; px < img.getWidth(); px++) {
                int argb = img.getRGB(px, py);
                int a = (argb >>> 24) & 0xFF;
                if (a == 0) {
                    continue;
                }
                int r = (argb >> 16) & 0xFF;
                int gr = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                CompatRender.nativeSetPixel(page.image, page.cursorX + px, page.cursorY + py,
                        (r << 24) | (gr << 16) | (b << 8) | a);
            }
        }
        Glyph glyph = new Glyph(c, page, page.cursorX, page.cursorY,
                img.getWidth(), img.getHeight(), font.advance(c));
        glyphs.put(c, glyph);
        page.rowHeight = Math.max(page.rowHeight, img.getHeight() + PAD);
        page.cursorX += img.getWidth() + PAD;
        page.texture.upload();
        return glyph;
    }

    /** 保证有空间放 (w,h) 的字形；必要时换行/开新页。 */
    private Page ensureSpace(int w, int h) {
        if (pages.isEmpty()) {
            return newPage();
        }
        Page page = pages.get(pages.size() - 1);
        if (page.cursorX + w > PAGE_SIZE) {
            page.cursorX = PAD;
            page.cursorY += page.rowHeight;
            page.rowHeight = 0;
        }
        if (page.cursorY + h > PAGE_SIZE) {
            return newPage();
        }
        return page;
    }

    private Page newPage() {
        Page page = new Page(CompatRender.rl("opendreamcore",
                "font/" + Integer.toHexString(System.identityHashCode(this)) + "/" + (++pageCounter)));
        pages.add(page);
        return page;
    }
}
