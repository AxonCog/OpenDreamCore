package com.opendreamcore.core.script;

import com.opendreamcore.core.registry.ServiceEntry;
import com.opendreamcore.core.registry.ServiceIndex;
import com.opendreamcore.core.registry.ServiceKind;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 脚本命名空间登记表（第四支柱的钩子）。
 *
 * <p>索引里的 METHOD 条目在启动时被装进来：**三语名（中文 / 英文 / 下划线）指向同一个实现**，
 * 因此 {@code 相机.播放(...)}、{@code Camera.play(...)}、{@code camera.play(...)} 是同一件事
 * —— 这是「名字不再手写三遍」的落地点。
 *
 * <p>每条方法的实现句柄在 bootstrap 时解析一次（零反射热路径）。
 */
public final class ScriptMethodIndex {

    private static final Logger LOGGER = Logger.getLogger(ScriptMethodIndex.class.getName());

    private static final Map<String, Map<String, ScriptMethod>> NAMESPACES =
            new LinkedHashMap<String, Map<String, ScriptMethod>>();
    private static boolean bootstrapped;

    private ScriptMethodIndex() {
    }

    /** 读索引里的 METHOD 条目并登记三语名；可重复调用（第二次直接返回）。 */
    public static synchronized void bootstrap(ClassLoader cl) {
        if (bootstrapped) {
            return;
        }
        int count = 0;
        for (ServiceEntry e : ServiceIndex.load(cl, ServiceKind.METHOD)) {
            String ns = e.attr("ns", e.key());
            ScriptMethod impl = resolve(e);
            if (ns == null || ns.isEmpty() || impl == null) {
                continue;
            }
            register(ns, e.attr("cn"), e.attr("en"), e.attr("snake"), impl);
            count++;
        }
        bootstrapped = true;
        System.out.println("[OpenDreamCore][core] 脚本命名空间就绪：" + NAMESPACES.size()
                + " 个命名空间 / " + count + " 个方法");
    }

    /** 手工登记（单测或运行期扩展用）；空名字会被跳过。 */
    public static synchronized void register(String namespace, String cn, String en, String snake,
                                             ScriptMethod impl) {
        if (namespace == null || namespace.isEmpty() || impl == null) {
            return;
        }
        Map<String, ScriptMethod> m = NAMESPACES.get(namespace);
        if (m == null) {
            m = new LinkedHashMap<String, ScriptMethod>();
            NAMESPACES.put(namespace, m);
        }
        putIfAbsent(m, cn, impl);
        putIfAbsent(m, en, impl);
        putIfAbsent(m, snake, impl);
    }

    /** 按（命名空间，任意一种名字）查方法；没有返回 null。 */
    public static synchronized ScriptMethod find(String namespace, String name) {
        if (namespace == null || name == null) {
            return null;
        }
        Map<String, ScriptMethod> m = NAMESPACES.get(namespace);
        return m == null ? null : m.get(name);
    }

    /** 已登记的命名空间（中文/英文/下划线三种名都在这里，顺序 = 登记顺序）。 */
    public static synchronized List<String> namespaces() {
        return Collections.unmodifiableList(new ArrayList<String>(NAMESPACES.keySet()));
    }

    /** 某个命名空间下的全部可调用名字（三语名都在，用于脚本补全与报错提示）。 */
    public static synchronized List<String> methodsOf(String namespace) {
        Map<String, ScriptMethod> m = NAMESPACES.get(namespace);
        if (m == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<String>(m.keySet()));
    }

    public static synchronized int methodCount() {
        int n = 0;
        for (Map<String, ScriptMethod> m : NAMESPACES.values()) {
            n += m.size();
        }
        return n;
    }

    public static synchronized void resetForTests() {
        NAMESPACES.clear();
        bootstrapped = false;
    }

    private static void putIfAbsent(Map<String, ScriptMethod> m, String name, ScriptMethod impl) {
        if (name == null || name.isEmpty() || m.containsKey(name)) {
            return;
        }
        m.put(name, impl);
    }

    /** 索引里的 impl 写成 {@code 全类名#方法名}；方法签名必须是 (ScriptContext, Object[]) → Object。 */
    private static ScriptMethod resolve(ServiceEntry e) {
        String impl = e.implClass();
        int hash = impl.lastIndexOf('#');
        if (hash <= 0 || hash == impl.length() - 1) {
            LOGGER.warning("[OpenDreamCore][core] 方法条目的 impl 必须写成 全类名#方法名，已跳过：" + impl);
            return null;
        }
        String cls = impl.substring(0, hash);
        String mtd = impl.substring(hash + 1);
        try {
            Class<?> c = Class.forName(cls, true, ScriptMethodIndex.class.getClassLoader());
            Method m = c.getMethod(mtd, ScriptContext.class, Object[].class);
            if (!java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                LOGGER.warning("[OpenDreamCore][core] 脚本方法必须是静态方法，已跳过：" + impl);
                return null;
            }
            final MethodHandle mh = MethodHandles.lookup().unreflect(m);
            return new ScriptMethod() {
                @Override
                public Object call(ScriptContext ctx, Object[] args) {
                    try {
                        return mh.invokeExact(ctx, args);
                    } catch (Throwable t) {
                        throw new ScriptDeniedException("脚本方法执行失败：" + t, t);
                    }
                }
            };
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "[OpenDreamCore][core] 脚本方法解析失败：" + impl, t);
            return null;
        }
    }
}
