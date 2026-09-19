package com.opendreamcore.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 页面变量存储：Screen.设置变量 / 获取变量 的公共后端。
 * client 层持有，双版本 target 共用；key = 页面 id（可空=全局）+ 变量名。
 */
public final class PageVariables {
    private static final Map<String, Object> STORE = new ConcurrentHashMap<>();

    private PageVariables() {
    }

    public static void set(String pageId, String name, Object value) {
        STORE.put(key(pageId, name), value);
    }

    public static Object get(String pageId, String name) {
        return STORE.get(key(pageId, name));
    }

    public static void clear(String pageId) {
        String prefix = (pageId == null ? "global" : pageId) + "/";
        STORE.keySet().removeIf(k -> k.startsWith(prefix));
    }

    public static int count(String pageId) {
        String prefix = (pageId == null ? "global" : pageId) + "/";
        int n = 0;
        for (String k : STORE.keySet()) {
            if (k.startsWith(prefix)) {
                n++;
            }
        }
        return n;
    }

    private static String key(String pageId, String name) {
        return (pageId == null ? "global" : pageId) + "/" + name;
    }
}
