package com.opendreamcore.core.capability;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 能力总线：消费方写
 * {@code CameraCapability cam = Capabilities.of(player, CameraCapability.class); if (cam == null) return;}
 * 就完了 —— 不需要在任何业务代码里判断版本、加载器、附属是否存在。
 *
 * <p>{@link #of} 在**没有能力时静默返回 null**（查询本身要能在热路径上被调用）；
 * 「缺失时记一条 WARN」由消费方决定（不同能力缺失的严重程度不同）。
 */
public final class Capabilities {

    private static final Logger LOGGER = Logger.getLogger(Capabilities.class.getName());

    private static final Map<Class<?>, CapabilityFactory<?>> FACTORIES =
            new ConcurrentHashMap<Class<?>, CapabilityFactory<?>>();

    private Capabilities() {
    }

    /** 登记一个能力（各平台/代际的 bootstrap 调；重复登记会覆盖）。 */
    public static <T> void register(Class<T> type, CapabilityFactory<T> factory) {
        if (type == null || factory == null) {
            return;
        }
        FACTORIES.put(type, factory);
    }

    public static <T> void unregister(Class<T> type) {
        if (type != null) {
            FACTORIES.remove(type);
        }
    }

    /** 查询能力：没有就返回 null。工厂抛异常也按「没有」处理（并记一条 WARN）。 */
    @SuppressWarnings("unchecked")
    public static <T> T of(Object holder, Class<T> type) {
        if (type == null) {
            return null;
        }
        CapabilityFactory<T> f = (CapabilityFactory<T>) FACTORIES.get(type);
        if (f == null) {
            return null;
        }
        try {
            return f.create(holder);
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "[OpenDreamCore][core] 能力工厂异常：" + type.getName(), t);
            return null;
        }
    }

    /** 这个能力有没有被登记（用于「启动时报告能力矩阵」这类诊断）。 */
    public static boolean has(Class<?> type) {
        return type != null && FACTORIES.containsKey(type);
    }

    /** 已登记的能力接口集合。 */
    public static Set<Class<?>> registered() {
        return Collections.unmodifiableSet(new LinkedHashSet<Class<?>>(FACTORIES.keySet()));
    }

    public static int size() {
        return FACTORIES.size();
    }

    public static void clearForTests() {
        FACTORIES.clear();
    }
}
