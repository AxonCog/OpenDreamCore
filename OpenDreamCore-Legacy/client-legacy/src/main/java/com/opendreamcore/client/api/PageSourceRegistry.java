package com.opendreamcore.client.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 页面源注册表（老壳）：本地页面的出处，这里可插拔。
 *
 * 附属模组 init 时 register 一个实现，之后进服和 /codc reload 都会连它一起扫。
 * 先注册的先跑，同名的拒收；页面 id 撞车时后写的覆盖先写的。
 */
public final class PageSourceRegistry {

    private static final List<PageSource> SOURCES = new CopyOnWriteArrayList<PageSource>();

    private PageSourceRegistry() { }

    /** 注册页面源。同名或空名返回 false（防重复注册把源表撑爆）。 */
    public static boolean register(PageSource source) {
        if (source == null || source.name() == null || source.name().isEmpty()) {
            return false;
        }
        for (PageSource s : SOURCES) {
            if (s.name().equals(source.name())) {
                return false;
            }
        }
        SOURCES.add(source);
        return true;
    }

    /** 按名字移除，返回是否删掉了。 */
    public static boolean unregister(String name) {
        for (PageSource s : SOURCES) {
            if (s.name().equals(name)) {
                SOURCES.remove(s);
                return true;
            }
        }
        return false;
    }

    /** 当前所有源的名字，按加载顺序。 */
    public static List<String> names() {
        List<String> out = new ArrayList<String>();
        for (PageSource s : SOURCES) {
            out.add(s.name());
        }
        return out;
    }

    /** 核心加载入口用：按注册顺序取源列表（只读）。 */
    public static List<PageSource> sources() {
        return Collections.unmodifiableList(SOURCES);
    }
}
