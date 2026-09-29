package com.opendreamcore.client.visual;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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

    private static final org.slf4j.Logger LOGGER =
            org.slf4j.LoggerFactory.getLogger("OpenDreamCore");

    /** 字形条目（纯数据）。 */
    public record ReplaceFontGlyph(int codePoint, String texture, float width, float height) {
    }

    private static final Map<Integer, ReplaceFontGlyph> GLYPHS = new HashMap<>();
    /** 各 target 构建好的 BakedGlyph 缓存（刷新时随 clear() 一起清）。 */
    private static final Map<Integer, Object> BAKED = new HashMap<>();
    /** 可用性缓存：只缓存 true——false 永远现查！
     *  历史大坑：包区/资源云纹理都是进服后异步注册的，首次查询瞬间贴图还没就绪，
     *  若把 false 缓存住，纹理到位后也没人失效它 → 替换永远不生效且零日志。
     *  现在语义是「查到就接管、查不到走原版」，纹理就绪后下一帧自愈。 */
    private static final Map<Integer, Boolean> USABLE = new HashMap<>();

    /** 贴图未就绪提示去重（每字符只提示一次，clear 时重置）。 */
    private static final Set<Integer> PENDING_LOGGED = new HashSet<>();

    /** 字形对象缓存的失效回调。
     *  共享层不依赖任何版本的渲染类，所以由各代字形层在首次接管时注册自己的清空动作：
     *  纹理后到、规则刷新、退服时统一触发，避免字形卡在「构建失败」或「旧帧」状态。 */
    private static final java.util.List<Runnable> INVALIDATORS =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    /** 注册一个失效回调（各代字形层调用，重复注册无副作用）。 */
    public static void registerInvalidator(Runnable invalidator) {
        if (invalidator != null && !INVALIDATORS.contains(invalidator)) {
            INVALIDATORS.add(invalidator);
        }
    }

    /** 触发全部失效回调：单个回调出错不影响其他回调与主流程。 */
    private static void fireInvalidators() {
        for (Runnable r : INVALIDATORS) {
            try {
                r.run();
            } catch (Throwable ignored) {
                // 失效失败不阻断主流程：下一次纹理/规则变更还会再试
            }
        }
    }

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

    /** 字符是否可用（贴图已解析）。true 才缓存，false 现查并提示一次。 */
    public static boolean usable(int codePoint) {
        ReplaceFontGlyph g = GLYPHS.get(codePoint);
        if (g == null) {
            return false;
        }
        Boolean u = USABLE.get(codePoint);
        if (u != null) {
            return true;
        }
        boolean ok = com.opendreamcore.client.resources.LooseResourceLoader.staticOf(g.texture()) != null
                || com.opendreamcore.client.resources.LooseResourceLoader.lookup(g.texture()) != null;
        if (ok) {
            USABLE.put(codePoint, Boolean.TRUE);
        } else {
            logPendingOnce(codePoint, g.texture());
        }
        return ok;
    }

    /** 贴图未就绪提示（每字符一次）：这条链以前是全静默的，出问题只能靠猜。 */
    private static void logPendingOnce(int codePoint, String texture) {
        if (PENDING_LOGGED.add(codePoint)) {
            LOGGER.info("[ODC-font] 字形贴图未就绪，等待注册（就绪后自动生效）: {}",
                    texture);
        }
    }

    /** 供各代字形层在「命中规则但贴图还没就绪」时提示一次（保持排障可见性）。 */
    public static void notePending(int codePoint, String texture) {
        logPendingOnce(codePoint, texture);
    }

    /** 清空字符表 + 字形缓存（规则刷新/退服时调用）。 */
    public static void clear() {
        GLYPHS.clear();
        BAKED.clear();
        USABLE.clear();
        PENDING_LOGGED.clear();
        fireInvalidators();
    }

    /** 纹理就绪失效（本地包区/资源云注册、远程下载完成时调用）：
     *  只清判定与烘焙缓存，保留字符表——以前这里误用 clear() 会把 GLYPHS 一并清空，
     *  字形表空了替换照样不生效（等下一轮 5s 规则重发才偶然自愈的隐藏坑）。 */
    public static void invalidateTextures() {
        BAKED.clear();
        USABLE.clear();
        PENDING_LOGGED.clear();
        fireInvalidators();
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