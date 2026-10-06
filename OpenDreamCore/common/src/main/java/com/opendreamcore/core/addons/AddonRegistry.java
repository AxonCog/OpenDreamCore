package com.opendreamcore.core.addons;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * addons 注册表（共享底座，任何模块共用）。
 *
 * <p>生命周期：install() 整组替换（reload 语义）；单条 register/remove 支持脚本包等运行期挂载。
 * 同 id 后到者拒绝（先到者优先，保证加载顺序确定）。
 */
public final class AddonRegistry {

    /** 一个已装载扩展。 */
    public static final class Handle {
        public final String id;
        public final String type;
        public final String version;
        /** 扩展本体（AddonProvider 或其业务接口形态，由消费方自行判定）。 */
        public final Object addon;
        /** 拥有该扩展的类加载器（jar 来源时为子加载器，reload 时整组关闭）。 */
        public final ClassLoader loader;

        Handle(String id, String type, String version, Object addon, ClassLoader loader) {
            this.id = id;
            this.type = type;
            this.version = version;
            this.addon = addon;
            this.loader = loader;
        }
    }

    private final Map<String, Handle> byId = new LinkedHashMap<String, Handle>();

    /** 注册；id 重复/空时拒绝并返回 false（先到者优先）。 */
    public synchronized boolean register(String id, String type, String version, Object addon,
                                         ClassLoader loader) {
        if (id == null || id.trim().isEmpty() || byId.containsKey(id)) {
            return false;
        }
        byId.put(id, new Handle(id, type == null ? "unknown" : type,
                version == null ? "?" : version, addon, loader));
        return true;
    }

    public synchronized boolean remove(String id) {
        return byId.remove(id) != null;
    }

    public synchronized Handle get(String id) {
        return byId.get(id);
    }

    public synchronized List<Handle> all() {
        return Collections.unmodifiableList(new ArrayList<Handle>(byId.values()));
    }

    public synchronized List<Handle> byType(String type) {
        List<Handle> out = new ArrayList<Handle>();
        for (Handle h : byId.values()) {
            if (h.type.equals(type)) {
                out.add(h);
            }
        }
        return out;
    }

    public synchronized int size() {
        return byId.size();
    }

    public synchronized void clear() {
        byId.clear();
    }
}
