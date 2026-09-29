package com.opendreamcore.glyph;

import com.opendreamcore.client.TtfGlyphSource;
import com.opendreamcore.client.resources.LooseResourceLoader;
import com.opendreamcore.client.visual.ReplaceFontProvider;
import com.opendreamcore.client.visual.VisualFontReplace;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 替换字形缓存（1.21.8 代）。
 *
 * 缓存策略直接对应动画语义：
 *   - 静态贴图：几何与 UV 恒定，缓存复用，避免每帧重建对象；
 *   - GIF：UV 随帧变化，**不进缓存**——每次取用都按当前帧新建，动画才不会被首帧卡住；
 *   - 全局字体：位图由共享层缓存，这里再挡一层字形对象缓存。
 *
 * 用带访问顺序的 LinkedHashMap 做 LRU：服务器刷新规则或换字体时键会整体变化，
 * 有上限才不会随长时间挂机无限增长。
 */
public final class OdcGlyphCache {

    private static final int MAX_ENTRIES = 4096;

    private static final Map<Integer, OdcBakedGlyph> BITMAP =
            new LinkedHashMap<Integer, OdcBakedGlyph>(256, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Integer, OdcBakedGlyph> eldest) {
                    return size() > MAX_ENTRIES;
                }
            };

    private static final Map<Integer, OdcBakedGlyph> TTF =
            new LinkedHashMap<Integer, OdcBakedGlyph>(256, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Integer, OdcBakedGlyph> eldest) {
                    return size() > MAX_ENTRIES;
                }
            };

    static {
        // 规则重发与退服时会 clear()，包区/资源云纹理就绪时会 invalidateTextures()，
        // 两者都走失效回调链。这里挂上自己的清空动作：否则缓存里第一帧 UV、
        // 甚至「贴图没就绪导致构建失败」的空结果会一直跟着这个字符，替换看着像没生效。
        ReplaceFontProvider.registerInvalidator(OdcGlyphCache::clear);
    }

    private OdcGlyphCache() {
    }

    /** 单字贴图 / GIF 字形；动画每次新建（保证 UV 是当前帧），静态走缓存。 */
    public static OdcBakedGlyph bitmap(int codePoint, VisualFontReplace.CharGlyph glyph) {
        if (isAnimated(glyph.texture())) {
            return OdcGlyphFactory.bitmap(glyph);
        }
        synchronized (BITMAP) {
            OdcBakedGlyph cached = BITMAP.get(codePoint);
            if (cached != null) {
                return cached;
            }
        }
        OdcBakedGlyph baked = OdcGlyphFactory.bitmap(glyph);
        if (baked != null) {
            synchronized (BITMAP) {
                BITMAP.put(codePoint, baked);
            }
        }
        return baked;
    }

    /** 全局字体字形。 */
    public static OdcBakedGlyph ttf(int codePoint, TtfGlyphSource.Glyph glyph) {
        synchronized (TTF) {
            OdcBakedGlyph cached = TTF.get(codePoint);
            if (cached != null) {
                return cached;
            }
        }
        OdcBakedGlyph baked = OdcGlyphFactory.ttf(glyph);
        if (baked != null) {
            synchronized (TTF) {
                TTF.put(codePoint, baked);
            }
        }
        return baked;
    }

    /** 纹理注册或字体切换后清空，让下一帧按新内容重建。 */
    public static void clear() {
        synchronized (BITMAP) {
            BITMAP.clear();
        }
        synchronized (TTF) {
            TTF.clear();
        }
    }

    /** 该贴图是否为多帧动画。 */
    private static boolean isAnimated(String texture) {
        try {
            LooseResourceLoader.SheetInfo si = LooseResourceLoader.sheetOf(texture);
            return si != null && si.frames() > 1;
        } catch (Throwable t) {
            return false;
        }
    }
}
