package com.opendreamcore.mixin;

import com.mojang.blaze3d.font.GlyphInfo;
import com.opendreamcore.client.TtfGlyphSource;
import com.opendreamcore.client.resources.LooseResourceLoader;
import com.opendreamcore.client.visual.ReplaceFontProvider;
import com.opendreamcore.client.visual.VisualFontReplace;
import com.opendreamcore.glyph.OdcGlyphCache;
import com.opendreamcore.glyph.OdcGlyphInfo;
import com.opendreamcore.glyph.OdcTtfGlyphInfo;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;

/**
 * 字形层接管（1.21.8 代）。
 *
 * 这代的文本管线在图集之外还有一条直通路径：排版阶段先取字形信息拿步进与样式偏移，
 * 真正落笔时再取字形对象来构造绘制实例。所以两个入口都要接管，缺一个就会出现
 * 「宽度对了但字形还是原版」或者「字形换了但排版错位」。
 *
 *   - getGlyphInfo：给出我们自己的字形信息（步进、阴影偏移沿用接口默认实现）；
 *   - getGlyph：给出我们自己的字形对象（贴图绑在自建渲染类型上，不经过原版图集）。
 *
 * 两者都不覆写原版绘制：颜色、粗体加厚、斜体错切、阴影与深度全部由原版渲染器按字形
 * 对象携带的几何与 UV 照常处理，所以样式天然不丢。
 *
 * 命中判定只看我们自己的规则源（单字 / 区间 / 正则三类统一由规则表查询给出），
 * 判定为命中的字符一定走自建字形类；判定不命中一律放行原版。
 *
 * 回退约定：贴图尚未解析到 → 不接管（提示一次并等纹理就绪后自愈）；字体没配或字模
 * 取不到 → 不接管。任何情况下都不会返回空字形，也不会向批处理写不可见顶点。
 */
@Mixin(FontSet.class)
public abstract class FontSetMixin {

    /** 全局字体字形信息缓存（键=字体路径#码点；换字体路径自动失效）。 */
    private static final Map<String, OdcTtfGlyphInfo> TTF_INFO = new HashMap<>();
    private static final int TTF_INFO_MAX = 4096;

    @Inject(method = "getGlyphInfo(IZ)Lcom/mojang/blaze3d/font/GlyphInfo;",
            at = @At("HEAD"), cancellable = true)
    private void odc$replaceGlyphInfo(int codePoint, boolean random, CallbackInfoReturnable<GlyphInfo> cir) {
        VisualFontReplace.CharGlyph rule = odc$bitmapRule(codePoint);
        if (rule != null) {
            cir.setReturnValue(new OdcGlyphInfo(codePoint, rule));
            return;
        }
        OdcTtfGlyphInfo ttf = odc$ttfInfo(codePoint);
        if (ttf != null) {
            cir.setReturnValue(ttf);
        }
    }

    @Inject(method = "getGlyph(I)Lnet/minecraft/client/gui/font/glyphs/BakedGlyph;",
            at = @At("HEAD"), cancellable = true)
    private void odc$replaceGlyph(int codePoint, CallbackInfoReturnable<BakedGlyph> cir) {
        VisualFontReplace.CharGlyph rule = odc$bitmapRule(codePoint);
        if (rule != null) {
            BakedGlyph baked = OdcGlyphCache.bitmap(codePoint, rule);
            if (baked != null) {
                cir.setReturnValue(baked);
            }
            return;
        }
        OdcTtfGlyphInfo ttf = odc$ttfInfo(codePoint);
        if (ttf != null) {
            BakedGlyph baked = OdcGlyphCache.ttf(codePoint, ttf.glyph());
            if (baked != null) {
                cir.setReturnValue(baked);
            }
        }
    }

    /**
     * 查一条贴图规则。未命中返回 null；
     * 命中但贴图还没就绪时提示一次并返回 null（走原版字形，纹理到位后下一帧自愈）。
     */
    private static VisualFontReplace.CharGlyph odc$bitmapRule(int codePoint) {
        if (codePoint < 0 || codePoint > 0xFFFF) {
            return null; // 规则表以 BMP 字符为键，辅助平面不参与贴图替换
        }
        try {
            if (!VisualFontReplace.hasAny()) {
                return null;
            }
            VisualFontReplace.CharGlyph rule = VisualFontReplace.glyphFor((char) codePoint);
            if (rule == null) {
                return null;
            }
            if (LooseResourceLoader.sheetOf(rule.texture()) == null) {
                ReplaceFontProvider.notePending(codePoint, rule.texture());
                return null;
            }
            return rule;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 全局字体字形信息入口；未配置或字模取不到时返回 null（原版字体兜底）。 */
    private static OdcTtfGlyphInfo odc$ttfInfo(int codePoint) {
        try {
            String path = VisualFontReplace.defaultTtf();
            if (path == null || path.isEmpty()) {
                return null;
            }
            String key = path + "#" + codePoint;
            synchronized (TTF_INFO) {
                OdcTtfGlyphInfo cached = TTF_INFO.get(key);
                if (cached != null) {
                    return cached;
                }
            }
            TtfGlyphSource.Glyph glyph = TtfGlyphSource.get(codePoint);
            if (glyph == null) {
                return null;
            }
            OdcTtfGlyphInfo info = new OdcTtfGlyphInfo(codePoint, glyph);
            synchronized (TTF_INFO) {
                if (TTF_INFO.size() >= TTF_INFO_MAX) {
                    TTF_INFO.clear(); // 防御性上限：重建缓存代价是毫秒级
                }
                TTF_INFO.put(key, info);
            }
            return info;
        } catch (Throwable t) {
            return null;
        }
    }
}
