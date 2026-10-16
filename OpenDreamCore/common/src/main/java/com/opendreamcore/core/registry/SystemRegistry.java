package com.opendreamcore.core.registry;

import com.opendreamcore.core.descriptor.RegisteredSystem;
import com.opendreamcore.core.descriptor.Side;
import com.opendreamcore.core.descriptor.Sync;
import com.opendreamcore.core.descriptor.SystemDescriptor;
import com.opendreamcore.core.descriptor.SystemImpl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 系统注册表（第一支柱的落地）：启动时 {@link #bootstrap(ClassLoader)} 一次，之后由框架按 id
 * 调 {@link #applyAll(String, Map)} 应用整份规则集。
 *
 * <p>**不做类路径扫描**：条目来自编译期生成的索引。bootstrap 对每个系统做**一次**反射实例化
 * （N 个系统 = N 次，且只在启动时），零扫描与必须有实例之间的最小代价；
 * 之后所有调用都是接口直调。
 */
public final class SystemRegistry {

    private static final Logger LOGGER = Logger.getLogger(SystemRegistry.class.getName());

    private static final Map<String, RegisteredSystem> BY_ID =
            new LinkedHashMap<String, RegisteredSystem>();
    private static final Map<String, SystemDescriptor> DESCRIPTORS =
            new LinkedHashMap<String, SystemDescriptor>();
    private static boolean bootstrapped;

    private SystemRegistry() {
    }

    /** 读索引并实例化全部系统；可重复调用（第二次直接返回）。 */
    public static synchronized void bootstrap(ClassLoader cl) {
        if (bootstrapped) {
            return;
        }
        List<ServiceEntry> entries = ServiceIndex.load(cl, ServiceKind.SYSTEM);
        for (ServiceEntry e : entries) {
            try {
                SystemDescriptor d = toDescriptor(e);
                SystemImpl impl = instantiate(e, cl);
                BY_ID.put(d.id(), new RegisteredSystem(d, impl));
                DESCRIPTORS.put(d.id(), d);
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, "[OpenDreamCore][core] 系统注册失败 id=" + e.key()
                        + " impl=" + e.implClass(), t);
            }
        }
        bootstrapped = true;
        StringBuilder ids = new StringBuilder();
        for (String id : BY_ID.keySet()) {
            if (ids.length() > 0) {
                ids.append(',');
            }
            ids.append(id);
        }
        System.out.println("[OpenDreamCore][core] 系统注册表就绪：" + BY_ID.size() + " 个系统"
                + (ids.length() == 0 ? "" : "（" + ids + "）"));
    }

    /** 已注册系统（只读视图，顺序 = 索引顺序）。 */
    public static synchronized Collection<RegisteredSystem> all() {
        return Collections.unmodifiableCollection(new ArrayList<RegisteredSystem>(BY_ID.values()));
    }

    /** 按 id 取；没有返回 null。 */
    public static synchronized RegisteredSystem get(String id) {
        return BY_ID.get(id);
    }

    /** 全部系统元信息（生成命令表 / 文档骨架用）。 */
    public static synchronized Map<String, SystemDescriptor> descriptors() {
        return Collections.unmodifiableMap(new LinkedHashMap<String, SystemDescriptor>(DESCRIPTORS));
    }

    /** 按 id 应用一份完整规则集（框架在加载完成 / 热重载 / 收到下发时调）。 */
    public static synchronized void applyAll(String id, Map<String, Map<String, Object>> rules) {
        RegisteredSystem s = BY_ID.get(id);
        if (s == null) {
            LOGGER.warning("[OpenDreamCore][core] 未知系统 id=" + id + "（索引里没有，已忽略）");
            return;
        }
        s.applyRules(rules);
    }

    /** 批量应用（外层 key = 系统 id）。 */
    public static synchronized void applyAll(Map<String, Map<String, Map<String, Object>>> bySystem) {
        if (bySystem == null) {
            return;
        }
        for (Map.Entry<String, Map<String, Map<String, Object>>> e : bySystem.entrySet()) {
            applyAll(e.getKey(), e.getValue());
        }
    }

    /** 单测用：清空注册表（不清索引缓存）。 */
    public static synchronized void resetForTests() {
        BY_ID.clear();
        DESCRIPTORS.clear();
        bootstrapped = false;
    }

    private static SystemDescriptor toDescriptor(ServiceEntry e) {
        return new SystemDescriptor(
                e.key(),
                e.attr("cn", e.key()),
                e.attr("en", e.key()),
                e.attr("snake", e.key()),
                e.implClass(),
                e.attr("schema", ""),
                Side.parse(e.attr("side", "BOTH")),
                Sync.parse(e.attr("sync", "FULL")),
                e.boolAttr("hotReload", true));
    }

    private static SystemImpl instantiate(ServiceEntry e, ClassLoader cl) throws Exception {
        ClassLoader loader = cl != null ? cl : SystemRegistry.class.getClassLoader();
        Class<?> c = Class.forName(e.implClass(), true, loader);
        if (!SystemImpl.class.isAssignableFrom(c)) {
            throw new IllegalStateException(e.implClass() + " 没有实现 SystemImpl");
        }
        Object o = c.getDeclaredConstructor().newInstance();
        return (SystemImpl) o;
    }
}
