package com.opendreamcore.core.script;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 调用句柄缓存：第一次按「类名 + 方法名 + 参数个数」解析（一次反射），之后每次都是句柄直调
 * —— 脚本的 {@code Java.调用} 因此不违反规范 §10 规则 7（热路径禁反射）。
 *
 * <p><b>两条硬规则</b>：
 * <ol>
 *   <li><b>名字必须精确匹配</b>：不按参数个数选择同名但语义不同的方法。
 *       旧版映射缺失时曾误选同形的 {@code polygonMode(int,int)}，导致每帧输出 GL_INVALID_ENUM
 *       并修改多边形模式；因此缓存解析不会使用名称缺失时的宽松兜底。</li>
 * </ol>
 *
 * <p>缓存有上限（4096 条），超了整体清空重来 —— 缓存不允许无界增长（规范 §8 内存规则）。
 */
public final class HandleCache {

    private static final int MAX_ENTRIES = 4096;

    private static final Map<String, Handle> CACHE = new ConcurrentHashMap<String, Handle>();

    private HandleCache() {
    }

    /** 解析并缓存；解析不了就抛 {@link ScriptDeniedException}（消息含类名、方法名、参数个数）。 */
    public static Handle get(String className, String methodName, int arity)
            throws ScriptDeniedException {
        if (className == null || className.trim().isEmpty()
                || methodName == null || methodName.trim().isEmpty()) {
            throw new ScriptDeniedException("需要「类名」与「方法名」两个非空参数");
        }
        String cls = className.trim();
        String mtd = methodName.trim();
        int n = Math.max(arity, 0);
        String key = cls + '#' + mtd + '/' + n;
        Handle cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        Handle h = resolve(cls, mtd, n);
        if (CACHE.size() >= MAX_ENTRIES) {
            CACHE.clear();
        }
        CACHE.put(key, h);
        return h;
    }

    public static int size() {
        return CACHE.size();
    }

    public static void clear() {
        CACHE.clear();
    }

    private static Handle resolve(String className, String methodName, int arity)
            throws ScriptDeniedException {
        Class<?> c;
        try {
            c = Class.forName(className, false, loader());
        } catch (Throwable t) {
            throw new ScriptDeniedException("找不到类：" + className, t);
        }
        Method instanceMatch = null;
        List<Method> candidates = new ArrayList<Method>();
        Set<String> seenParams = new LinkedHashSet<String>();
        for (Method m : c.getMethods()) {
            if (!m.getName().equals(methodName) || m.getParameterCount() != arity) {
                continue;
            }
            if (m.isBridge() || m.isSynthetic()) {
                continue;
            }
            String sig = paramKey(m);
            if (isDangerousName(m.getName())) {
                continue;
            }
            if (!Modifier.isStatic(m.getModifiers())) {
                instanceMatch = m;
                continue;
            }
            if (seenParams.add(sig)) {
                candidates.add(m);
            }
        }
        if (candidates.size() == 1) {
            return build(candidates.get(0), className, methodName);
        }
        if (candidates.size() > 1) {
            StringBuilder sb = new StringBuilder();
            for (Method m : candidates) {
                if (sb.length() > 0) {
                    sb.append(" / ");
                }
                sb.append(m.getName()).append('(').append(paramKey(m)).append(')');
            }
            throw new ScriptDeniedException("有 " + candidates.size() + " 个同参数个数的重载，"
                    + "无法确定用哪个（规矩：绝不猜）：" + className + "#" + methodName
                    + " 可选 " + sb + "；请写一个具名包装方法再调用");
        }
        if (instanceMatch != null) {
            throw new ScriptDeniedException("只支持静态方法：" + className + "#" + methodName
                    + "（实例方法调用暂不支持）");
        }
        throw new ScriptDeniedException("找不到方法：" + className + "#" + methodName
                + "，参数个数 " + arity);
    }

    /** 形参类型简名的稳定字符串：用于重载签名展示与去重。 */
    private static String paramKey(Method m) {
        StringBuilder sb = new StringBuilder();
        for (Class<?> p : m.getParameterTypes()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(p.getSimpleName());
        }
        return sb.toString();
    }

    /** 清理/退出类方法永不开放给脚本（历史教训：这类方法被误调会直接炸进程）。 */
    private static boolean isDangerousName(String name) {
        return "close".equals(name) || "dispose".equals(name) || "destroy".equals(name)
                || "shutdown".equals(name) || "finalize".equals(name) || "exit".equals(name)
                || "halt".equals(name) || "release".equals(name) || "stop".equals(name);
    }

    private static Handle build(Method m, String className, String methodName)
            throws ScriptDeniedException {
        try {
            MethodHandle mh = MethodHandles.lookup().unreflect(m);
            MethodHandle spreader = mh.asSpreader(Object[].class, m.getParameterCount());
            if (m.getReturnType() == void.class) {
                return new VoidHandle(spreader);
            }
            return new ObjectHandle(spreader.asType(
                    MethodType.methodType(Object.class, Object[].class)));
        } catch (Throwable t) {
            throw new ScriptDeniedException("句柄构造失败：" + className + "#" + methodName, t);
        }
    }

    private static ClassLoader loader() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        return cl != null ? cl : HandleCache.class.getClassLoader();
    }

    /** 返回值非 void 的情况。 */
    private static final class ObjectHandle implements Handle {

        private final MethodHandle mh;

        ObjectHandle(MethodHandle mh) {
            this.mh = mh;
        }

        @Override
        public Object call(Object[] args) throws Throwable {
            return mh.invokeExact(args);
        }
    }

    /** 返回值为 void 的情况：调用后返回 null。 */
    private static final class VoidHandle implements Handle {

        private final MethodHandle mh;

        VoidHandle(MethodHandle mh) {
            this.mh = mh;
        }

        @Override
        public Object call(Object[] args) throws Throwable {
            mh.invokeExact(args);
            return null;
        }
    }
}
