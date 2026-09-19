package com.opendreamcore.client.api;

import com.opendreamcore.client.render.LegacyRenderer;
import com.opendreamcore.page.Element;
import com.opendreamcore.page.Page;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 元素渲染器注册表（老壳四版共用）：附属想加自己的元素类型，注册到这里。
 *
 * PageRenderer 走到某个元素时先查这张表，命中就交给你画；没命中才进内置那串
 * 类型分支。和现代端 ElementRenderRegistry 一个意思，只是老壳没有 GuiGraphics，
 * 画笔换成各版自己实现的 LegacyRenderer。
 *
 * 类型名一律按规范化后比对（"item_slot" 和 "itemslot" 算同一个），注册时写哪种
 * 都行，取的时候两种都查得到。
 */
public final class LegacyRendererRegistry {

    /** 渲染回调。返回 true 表示这个元素已经画完了。 */
    public interface Renderer {
        boolean render(LegacyRenderer r, Page page, Element element, Map<String, Object> props,
                       double w, double h, double alpha, boolean hovered, boolean pressed);
    }

    private static final Map<String, Renderer> REGISTRY = new LinkedHashMap<>();

    private LegacyRendererRegistry() { }

    /** 注册自定义元素渲染器（同名覆盖）。 */
    public static synchronized void register(String type, Renderer renderer) {
        if (type == null || renderer == null) {
            return;
        }
        REGISTRY.put(norm(type), renderer);
    }

    /** 摘掉某个类型，返回是否真删了。 */
    public static synchronized boolean unregister(String type) {
        return type != null && REGISTRY.remove(norm(type)) != null;
    }

    /** 查渲染器（两种写法都试一遍），没有返回 null。 */
    public static Renderer get(String type) {
        if (type == null) {
            return null;
        }
        String key = norm(type);
        return REGISTRY.get(key);
    }

    /** 已注册的自定义类型名。 */
    public static synchronized Set<String> registeredTypes() {
        return new java.util.LinkedHashSet<>(REGISTRY.keySet());
    }

    /** 类型名规范化：去空格下划线连字符、转小写，和 Painters.normType 同口径。 */
    private static String norm(String type) {
        return type.replace("_", "").replace("-", "").replace(" ", "").toLowerCase(Locale.ROOT);
    }
}
