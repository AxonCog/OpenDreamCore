package com.opendreamcore.client.visual;

import com.opendreamcore.client.CompatRender;
import com.opendreamcore.client.UiRenderer;
import com.opendreamcore.client.resources.LooseResourceLoader;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * 字符替换统一渲染器。
 *
 * 拆段是核心：把文本按"有贴图的替换字形"切成段，普通段走原版批画、命中段贴图 quad。
 * 前进宽度在拆段时一次算好（替换字形用 fontWidth，普通段走原版测量）——
 * ODC 页面文本（GuiGraphics）和原版文本（MultiBufferSource）各自消费同一份段序列，
 * 绘制宽度与折行测量天然一致，不会错位。
 *
 * 规则命中但贴图没解析到的字符一律回退原版字形（不吞字）——这条在拆段时就滤掉。
 */
public final class GlyphRenderer {

    /** 重入保护：拆段渲染期间普通段回原版绘制会再次进入拦截器，此时不再接管（避免 draw call 翻倍/死循环）。 */
    private static final ThreadLocal<Boolean> RENDERING = ThreadLocal.withInitial(() -> false);

    /** 文本段：普通段（原版字形）或替换字形段。 */
    public record Segment(boolean replaced, VisualFontReplace.CharGlyph glyph,
                          String plain, float advance) {
    }

    private GlyphRenderer() {
    }

    /**
     * 按替换规则把 text 拆段，段内已带前进宽度。
     * 仅当存在"有效替换"（规则命中且贴图已解析）才返回段表；
     * 无可替换字符 / 规则命中但贴图缺失 / 没有规则时返回空表——
     * 调用方整体走原版绘制，避免普通段回原方法再触发拦截导致递归。
     */
    public static List<Segment> split(Font font, String text, boolean shadow) {
        List<Segment> out = new ArrayList<>();
        if (text == null || text.isEmpty() || !VisualFontReplace.hasAny()) {
            return out;
        }
        StringBuilder plain = new StringBuilder();
        boolean hasReplaced = false;
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            VisualFontReplace.CharGlyph g = VisualFontReplace.glyphFor(c);
            // 规则命中但贴图没解析到：回退原版字形（不吞字）
            if (g != null && LooseResourceLoader.lookup(g.texture()) == null) {
                g = null;
            }
            if (g == null) {
                plain.append(c);
                i++;
                continue;
            }
            if (plain.length() > 0) {
                out.add(new Segment(false, null, plain.toString(), font.width(plain.toString())));
                plain.setLength(0);
            }
            out.add(new Segment(true, g, null, g.fontWidth() + (shadow ? 1.0F : 0.0F)));
            hasReplaced = true;
            i++;
        }
        if (!hasReplaced) {
            return List.of();
        }
        if (plain.length() > 0) {
            out.add(new Segment(false, null, plain.toString(), font.width(plain.toString())));
        }
        return out;
    }

    /** GuiGraphics 版绘制（ODC 页面文本走这里）。返回整段是否被接管。 */
    public static boolean renderGui(GuiGraphics g, Font font, String text, int x, int y,
                                    int color, boolean shadow) {
        if (RENDERING.get()) {
            return false; // 重入（普通段回原版）不再接管，直接走原版
        }
        List<Segment> segs = split(font, text, shadow);
        if (segs.isEmpty()) {
            return false;
        }
        RENDERING.set(true);
        try {
            int cx = x;
            for (Segment s : segs) {
                if (!s.replaced()) {
                    g.drawString(font, s.plain(), cx, y, UiRenderer.alphaColor(color), shadow);
                    cx += (int) s.advance();
                } else {
                    drawGlyphGui(g, s.glyph(), cx, y);
                    cx += (int) s.advance();
                }
            }
        } finally {
            RENDERING.set(false);
        }
        return true;
    }
    /** 原版文本（MultiBufferSource）版绘制。返回整段是否被接管。 */
    public static boolean renderBuffer(Font font, String text, float x, float y, int color, boolean shadow,
                                       Matrix4f matrix, MultiBufferSource buffer, Font.DisplayMode mode,
                                       int colorBg, int packedLight) {
        if (RENDERING.get()) {
            return false; // 重入（普通段回原方法）不再接管
        }
        List<Segment> segs = split(font, text, shadow);
        if (segs.isEmpty()) {
            return false;
        }
        RENDERING.set(true);
        try {
            float fx = x;
            for (Segment s : segs) {
                if (!s.replaced()) {
                    font.drawInBatch(s.plain(), fx, y, color, shadow, matrix, buffer, mode, colorBg, packedLight);
                    fx += s.advance();
                } else {
                    drawGlyphBuffer(s.glyph(), fx, y, matrix, buffer, packedLight);
                    fx += s.advance();
                }
            }
        } finally {
            RENDERING.set(false);
        }
        return true;
    }

    /** GuiGraphics 版字形 quad。gif 走帧表切 uv（动画），png 走静态整图。 */
    private static void drawGlyphGui(GuiGraphics g, VisualFontReplace.CharGlyph glyph, int x, int y) {
        LooseResourceLoader.SheetInfo si = LooseResourceLoader.sheetOf(glyph.texture());
        if (si == null) {
            return;
        }
        if (si.frames() > 1) {
            // gif 帧表：横向按当前帧偏移像素 uv（帧宽 = 单帧像素宽）
            CompatRender.blitGuiTextured(g, si.rl(), x, y, glyph.width(), glyph.height(),
                    si.frame() * si.frameW(), 0, si.frameW(), si.frameH(),
                    si.frameW() * si.frames(), si.frameH());
        } else {
            CompatRender.blitGuiTextured(g, si.rl(), x, y, glyph.width(), glyph.height(),
                    glyph.u(), glyph.v(), glyph.frameW(), glyph.frameH(), si.frameW(), si.frameH());
        }
    }

    /** buffer 版字形 quad（gif 走帧表切 uv 动画）。 */
    private static void drawGlyphBuffer(VisualFontReplace.CharGlyph g, float x, float y,
                                        Matrix4f matrix, MultiBufferSource buffer, int packedLight) {
        LooseResourceLoader.SheetInfo si = LooseResourceLoader.sheetOf(g.texture());
        if (si == null) {
            return;
        }
        var consumer = buffer.getBuffer(RenderType.text(si.rl()));
        float w = Math.max(1, g.frameW());
        float h = Math.max(1, g.frameH());
        float u0;
        float v0;
        float u1;
        float v1;
        if (si.frames() > 1) {
            float n = si.frames();
            u0 = si.frame() / n;
            u1 = u0 + 1.0F / n;
            v0 = 0.0F;
            v1 = 1.0F;
        } else {
            float texW = Math.max(1, si.frameW());
            float texH = Math.max(1, si.frameH());
            u0 = g.u() / texW;
            v0 = g.v() / texH;
            u1 = (g.u() + g.frameW()) / texW;
            v1 = (g.v() + g.frameH()) / texH;
        }
        // 顶点 API 版本漂移（1.20.1 vertex/uv/color/light）统一走 CompatRender 全链垫片
        CompatRender.glyphQuad(consumer, matrix, x, y, w, h, u0, v0, u1, v1, packedLight);
    }
}