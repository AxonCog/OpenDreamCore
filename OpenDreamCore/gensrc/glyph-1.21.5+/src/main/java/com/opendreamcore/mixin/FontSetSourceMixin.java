package com.opendreamcore.mixin;

import com.opendreamcore.client.TtfGlyphSource;
import com.opendreamcore.client.resources.LooseResourceLoader;
import com.opendreamcore.client.visual.ReplaceFontProvider;
import com.opendreamcore.client.visual.VisualFontReplace;
import com.opendreamcore.glyph.OdcGlyphCache;
import com.opendreamcore.glyph.OdcTtfGlyphInfo;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;

/**
 * 字形层接管（1.21.11 / 26.1.2 两代共用）。
 *
 * 这两代把「字形来源」抽成了独立的取字形对象：
 * 文本布局时向它要一个字形对象，字形对象自己携带渲染实例工厂与排版信息。
 * 因此接管点收敛成一处——取字形对象时把命中的字符换成我们自己的字形类，
 * 贴图绑在自建渲染类型与纹理视图上，不经过原版图集。
 *
 * 不覆写任何原版绘制：颜色、粗体加厚、斜体错切、阴影与深度都由字形对象携带的
 * 几何、UV 与样式照常处理，所以样式天然不丢；聊天、输入框、书、箱子名与全部
 * 原版界面都走同一条布局路径，一处接管即全覆盖。
 *
 * 命中判定只看我们自己的规则源（单字 / 区间 / 正则三类统一由规则表查询给出）。
 * 回退约定：贴图尚未解析到 → 不接管（提示一次并等纹理就绪后自愈）；字体没配或
 * 字模取不到 → 不接管。任何情况下都不会返回空字形，也不会向批处理写不可见顶点。
 */
@Mixin(targets = "net.minecraft.client.gui.font.FontSet$Source")
public abstract class FontSetSourceMixin {

    /** 全局字体字形信息缓存（键=字体路径#码点；换字体路径自动失效）。 */
    private static final Map<String, OdcTtfGlyphInfo> TTF_INFO = new HashMap<>();
    private static final int TTF_INFO_MAX = 4096;

    @Inject(method = "getGlyph(I)Lnet/minecraft/client/gui/font/glyphs/BakedGlyph;",
            at = @At("HEAD"), cancellable = true)
    private void opendreamcore$replaceGlyph(int codePoint, CallbackInfoReturnable<BakedGlyph> cir) {
        VisualFontReplace.CharGlyph rule = opendreamcore$bitmapRule(codePoint);
        if (rule != null) {
            BakedGlyph baked = OdcGlyphCache.bitmap(codePoint, rule);
            if (baked != null) {
                cir.setReturnValue(baked);
            }
            return;
        }
        OdcTtfGlyphInfo ttf = opendreamcore$ttfInfo(codePoint);
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
    private static VisualFontReplace.CharGlyph opendreamcore$bitmapRule(int codePoint) {
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
    private static OdcTtfGlyphInfo opendreamcore$ttfInfo(int codePoint) {
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
            OdcTtfGlyphInfo info = new OdcTtfGlyphInfo(glyph);
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
