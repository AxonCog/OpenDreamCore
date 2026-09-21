package com.opendreamcore.client.visual;

import java.util.HashMap;
import java.util.Map;

/**
 * 字形层字符替换（DreamCore 验证过的方案）：
 * 把命中字符注册成自定义 BakedGlyph（彩色纹理），挂在 FontSet.getGlyph 上——
 * 原版字体管线（聊天/UI/任何文本，含两段式 prepareText）全部自动走替换字形。
 *
 * 本类只做数据（字符表）+ BakedGlyph 对象缓存；BakedGlyph 的构建（版本相关 API）
 * 由各 target 的 FontSetMixin 完成（1.20.1 与 1.21.x 的 BakedGlyph 构造不同），
 * 共享层保持零 MC 渲染依赖，全版本可编译。
 */
public final class ReplaceFontProvider {

    /** 字形条目（纯数据）。 */
    public record ReplaceFontGlyph(int codePoint, String texture, float width, float height) {
    }

    private static final Map<Integer, ReplaceFontGlyph> GLYPHS = new HashMap<>();
    /** 各 target 构建好的 BakedGlyph 缓存（刷新时随 clear() 一起清）。 */
    private static final Map<Integer, Object> BAKED = new HashMap<>();
    /** 可用性缓存：贴图已解析的字符才接管（数字等贴图缺失的不进字形层，避免每帧重复查找刷屏）。 */
    private static final Map<Integer, Boolean> USABLE = new HashMap<>();

    private ReplaceFontProvider() {
    }

    /** 注册一个字符替换（codePoint → 贴图字形）。 */
    public static void register(int codePoint, String texture, float width, float height) {
        GLYPHS.put(codePoint, new ReplaceFontGlyph(codePoint, texture, width, height));
    }

    public static ReplaceFontGlyph get(int codePoint) {
        return GLYPHS.get(codePoint);
    }

    public static boolean hasAny() {
        return !GLYPHS.isEmpty();
    }

    /** 字符是否可用（贴图已解析）。首次判定缓存，规则刷新时随 clear() 清空。 */
    public static boolean usable(int codePoint) {
        ReplaceFontGlyph g = GLYPHS.get(codePoint);
        if (g == null) {
            return false;
        }
        Boolean u = USABLE.get(codePoint);
        if (u != null) {
            return u;
        }
        boolean ok = com.opendreamcore.client.resources.LooseResourceLoader.staticOf(g.texture()) != null
                || com.opendreamcore.client.resources.LooseResourceLoader.lookup(g.texture()) != null;
        USABLE.put(codePoint, ok);
        return ok;
    }

    /** 清空字符表 + 字形缓存（规则刷新/退服时调用）。 */
    public static void clear() {
        GLYPHS.clear();
        BAKED.clear();
        USABLE.clear();
    }

    public static Object cachedBaked(int codePoint) {
        return BAKED.get(codePoint);
    }

    public static void cacheBaked(int codePoint, Object baked) {
        if (baked != null) {
            BAKED.put(codePoint, baked);
        }
    }
}