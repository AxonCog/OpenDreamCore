package com.opendreamcore.client;

import com.opendreamcore.page.Page;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 原版 HUD 层开关：页面 options.hideVanilla 里写要藏的原版层名
 * （health/food/armor/hotbar/crosshair/xp/air/experience/scoreboard...），
 * 各版渲染钩子画原版层之前查一把 isHidden，命中就跳过那层。
 * 取值口径跟现代端 updateVanillaHide 一致——每帧由「本帧真出场的页面」
 * 集合重算，页关了自动失效，服务端 CLOSE 包丢了也能自愈。
 * 键名全小写归一，横线/下划线混写都认：hunger=food、experience=xp。
 */
public final class VanillaHud {

    private VanillaHud() {
    }

    /** 当前生效的隐藏层集合；空 = 全部照常画。 */
    private static final Set<String> HIDDEN = ConcurrentHashMap.newKeySet();

    /** 别名归一：各版本层名口径不一，这里统一收口成小写标准名。 */
    private static String canon(String raw) {
        String k = raw.toLowerCase(Locale.ROOT).replace('-', '_').trim();
        switch (k) {
            case "experience":
            case "xp_bar":
            case "xpbar":
                return "xp";
            case "hunger":
            case "food_level":
                return "food";
            case "cross_hair":
            case "crosshairs":
                return "crosshair";
            case "sidebar":
                return "scoreboard";
            default:
                return k;
        }
    }

    /**
     * 每帧由导演喂本帧出场的页面，重算隐藏集合。
     * 任一页要求藏哪层就藏哪层，集合取并集。
     */
    public static void sync(List<Page> visiblePages) {
        Set<String> merged = new HashSet<>();
        if (visiblePages != null) {
            for (Page p : visiblePages) {
                if (p == null || p.options() == null) {
                    continue;
                }
                collect(p.options().get("hideVanilla"), merged);
            }
        }
        HIDDEN.clear();
        HIDDEN.addAll(merged);
    }

    /** 断线/卸资源时全清。 */
    public static void clear() {
        HIDDEN.clear();
    }

    private static void collect(Object node, Set<String> out) {
        if (node instanceof List) {
            for (Object o : (List<?>) node) {
                collect(o, out);
            }
        } else if (node instanceof Object[]) {
            for (Object o : (Object[]) node) {
                collect(o, out);
            }
        } else if (node != null) {
            String name = canon(String.valueOf(node));
            if (!name.isEmpty()) {
                out.add(name);
            }
        }
    }

    /** 各版渲染钩子查询：这一层要不要藏。 */
    public static boolean isHidden(String layer) {
        return layer != null && !HIDDEN.isEmpty() && HIDDEN.contains(canon(layer));
    }

    /** 快捷判断：整条原版状态栏（血/甲/食/气）要不要全藏。 */
    public static boolean statusBarHidden() {
        return isHidden("health") || isHidden("food") || isHidden("armor") || isHidden("air");
    }
}
