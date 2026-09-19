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
 * 方言适配器链——热拔插的「排插」。
 *
 * 一个外来文件要进门的时候，链子按优先级从高到低挨个问：
 * 「这文件你熟吗？」第一个答「熟！」的把人领走（accepts → translate），
 * 翻出来的规则直接进 ODC 规则库（id 挂 name/ 前缀，防止跟别的方言撞车）。
 * 要是全链都摇头，那就原样放着吧——不认识的东西不乱猜，这是规矩。
 *
 * 谁都能往排插上插：
 *   1) 内置：adapter/dreamcore/ 的大能，链头常驻；
 *   2) 附属插件：AdapterChain.register(...) 即插即用，reload 也不拔；
 *   3) 服主脚本：extensions/adapters/*.yml 跟脚本声明配对，/odc reload 时
 *      clearScripted() 清旧插头 → 扩展装载器重新插一遍，热拔热插。
 */
public final class AdapterChain {

    private static final Logger LOGGER = Logger.getLogger(AdapterChain.class.getName());

    /** 链上适配器（按 priority 升序排队的快照）。 */
    private static final CopyOnWriteArrayList<Adapter> CHAIN = new CopyOnWriteArrayList<>();

    /** 名字索引：防重名，重名的让新人上位。 */
    private static final ConcurrentHashMap<String, Adapter> BY_NAME = new ConcurrentHashMap<>();

    // 静态初始化：内置适配器先进链（其他都是运行时加塞）
    static {
        AdapterChain.register(new DragonCoreAdapter());
    }

    private AdapterChain() {
    }

    /** 插队（注册）：priority 小的排前面先被问；同名旧插头让位。 */
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
        LOGGER.info(() -> "[OpenDreamCore][adapter] 适配器已插进链子 " + adapter.name()
                + "（链上共 " + CHAIN.size() + " 个）");
    }

    /** 拔插头：按名字注销（扩展重载时旧版让位用）。 */
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

    /** 排插快照（列表/调试用）。 */
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

    /** 可选接口：想插队排前面的适配器，P 越小越靠前（内置方言用小数字占位）。 */
    public interface Prioritized {
        int priority();
    }
}