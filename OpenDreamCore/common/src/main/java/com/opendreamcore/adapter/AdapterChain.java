package com.opendreamcore.adapter;

import com.opendreamcore.adapter.dragoncore.DragonCoreAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

/**
 * 适配器链。
 *
 * 外部格式的规则文件进来时按 priority 从高到低逐个过 accepts()，
 * 第一个认领的做 translate，产物进 ODC 规则库（id 挂 name/ 前缀防撞车）。
 * 全部不认领则保留原文件不动，不做猜测性转换。
 *
 * 注册途径：
 *   1) 内置：adapter/dragoncore/，链头常驻；
 *   2) 附属插件：AdapterChain.register(...)，reload 不清；
 *   3) 服主脚本：extensions/adapters/*.yml 声明，/odc reload 时
 *      clearScripted() 清旧项后由扩展装载器重新注册。
 */
public final class AdapterChain {

    private static final Logger LOGGER = Logger.getLogger(AdapterChain.class.getName());

    /** 链上适配器（按 priority 升序）。 */
    private static final CopyOnWriteArrayList<Adapter> CHAIN = new CopyOnWriteArrayList<>();

    /** 名字索引：防重名，重名的让新人上位。 */
    private static final ConcurrentHashMap<String, Adapter> BY_NAME = new ConcurrentHashMap<>();

    // 静态初始化：内置适配器先注册（其余运行时注册）
    static {
        AdapterChain.register(new DragonCoreAdapter());
    }

    private AdapterChain() {
    }

    /** 注册：priority 小的排前面；同名旧适配器被顶掉。 */
    public static void register(Adapter adapter) {
        if (adapter == null || adapter.name() == null || adapter.name().trim().isEmpty()) {
            return;
        }
        Adapter old = BY_NAME.put(adapter.name(), adapter);
        if (old != null) {
            CHAIN.remove(old);
        }
        CHAIN.add(adapter);
        CHAIN.sort(Comparator.comparingInt(AdapterChain::priorityOf));
        LOGGER.info(() -> "[OpenDreamCore][adapter] 适配器已注册 " + adapter.name()
                + "（当前共 " + CHAIN.size() + " 个）");
    }

    /** 按名字注销（扩展重载时旧版让位用）。 */
    public static void unregister(String name) {
        Adapter old = BY_NAME.remove(name);
        if (old != null) {
            CHAIN.remove(old);
        }
    }

    /** 清场：脚本登记的临时工全拔掉（reload 专用；Java 自带的常驻不动）。 */
    public static void clearScripted() {
        for (Adapter a : CHAIN) {
            if (a.scripted()) {
                BY_NAME.remove(a.name());
                CHAIN.remove(a);
            }
        }
    }

    /** 当前适配器列表（列表/调试用）。 */
    public static List<Adapter> all() {
        return Collections.unmodifiableList(new ArrayList<>(CHAIN));
    }

    /** 当前插着的适配器数量（对齐日志用）。 */
    public static int size() {
        return CHAIN.size();
    }

    /**
     * 翻译入口：挨个问、第一个认领的干活。
     * 返回 Map<目标系统, Map<规则id, IR>>；没人认领/翻译不出东西 = 空 map。
     */
    public static Map<String, Map<String, Map<String, Object>>> translate(
            String fileName, Map<String, Object> rootIr) {
        for (Adapter adapter : CHAIN) {
            try {
                if (!adapter.accepts(fileName, rootIr)) {
                    continue;
                }
                Map<String, Map<String, Map<String, Object>>> out =
                        adapter.translate(fileName, rootIr);
                if (out != null && !out.isEmpty()) {
                    return out;
                }
                // 认领了却没货——当它没认领，接着问下一个
            } catch (Exception e) {
                LOGGER.warning(() -> "[OpenDreamCore][adapter] " + adapter.name()
                        + " 翻译 " + fileName + " 时翻车了: " + e);
            }
        }
        return Collections.emptyMap();
    }

    /** 预检：有没有适配器能认领这个文件。 */
    public static boolean accepts(String fileName, Map<String, Object> rootIr) {
        for (Adapter adapter : CHAIN) {
            try {
                if (adapter.accepts(fileName, rootIr)) {
                    return true;
                }
            } catch (Exception ignored) {
                // accepts 自己闹脾气的，当它不认领
            }
        }
        return false;
    }

    /**
     * 脚本方言改写：按链序把每个适配器的 rewriteScript 叠一遍。
     * 谁也没改 → 原样回来；改坏了不影响解析——执行器该报错还报错。
     */
    public static String rewriteScript(String script) {
        if (script == null) {
            return null;
        }
        String out = script;
        for (Adapter adapter : CHAIN) {
            try {
                String next = adapter.rewriteScript(out);
                if (next != null && !next.equals(out)) {
                    out = next;
                }
            } catch (Exception ignored) {
                // 某个适配器的改写抽风了别连累整段脚本
            }
        }
        return out;
    }

    /** 排序键：没声明 priority 的排最后吃灰（Integer.MAX_VALUE 防溢出用减法）。 */
    private static int priorityOf(Adapter a) {
        return a instanceof Prioritized p ? p.priority() : Integer.MAX_VALUE;
    }

    /** 可选接口：返回更小的 priority 排到前面（内置适配器用小数字占位）。 */
    public interface Prioritized {
        int priority();
    }
}