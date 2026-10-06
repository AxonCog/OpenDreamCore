package com.opendreamcore.camera;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 相机触发器（M1.6）：一条规则可以同时挂多种触发。四种形态：
 * <pre>
 * 触发:
 *   按键: R                 # 或 keys: [Ctrl+R]（列表，KeyCombo 归一交客户端）
 *   区域: { pos1: [x,y,z], pos2: [x,y,z], once: true }   # 进入箱体边沿触发
 *   事件: 进世界             # 进世界 | 重生
 * </pre>
 * 命令触发（/odc camera play）不需要配置，天然存在。
 * 纯解析，不摸 MC；坏值一律跳过不抛（触发缺失只是不触发）。
 * JDK8 字节码兼容（common-j8 门禁）：不用 List.of / instanceof 模式匹配 / isBlank。
 */
public final class CameraTrigger {

    /** 按键组合串（已小写化，客户端再做 KeyCombo 归一）；空 = 无按键触发。 */
    public final List<String> keys;
    /** 区域箱体对角两点；null = 无区域触发。 */
    public final double[] pos1;
    public final double[] pos2;
    /** 区域 once：true = 本会话只触发一次；false = 每次进入边沿都触发。缺省 true。 */
    public final boolean once;
    /** 事件名（进世界 / 重生）；null = 无事件触发。 */
    public final String event;

    private CameraTrigger(List<String> keys, double[] pos1, double[] pos2, boolean once, String event) {
        this.keys = keys;
        this.pos1 = pos1;
        this.pos2 = pos2;
        this.once = once;
        this.event = event;
    }

    public static CameraTrigger parse(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof String) {
            return parseShorthand(String.valueOf(o).trim());
        }
        if (o instanceof Map) {
            return parseMap((Map<?, ?>) o);
        }
        if (o instanceof List) {
            return parseList((List<?>) o);
        }
        return null;
    }

    /** 简写：进世界/重生 是事件名，其余当单键。 */
    private static CameraTrigger parseShorthand(String v) {
        if (isEventName(v)) {
            return new CameraTrigger(Collections.<String>emptyList(), null, null, true, v);
        }
        List<String> one = new ArrayList<String>();
        one.add(v.toLowerCase());
        return new CameraTrigger(one, null, null, true, null);
    }

    /** 具名/平铺两形态：区域是子结构时先递归取出。 */
    private static CameraTrigger parseMap(Map<?, ?> m) {
        // 具名形态（常规写法）：触发: { 区域: {pos1.., once..}, 按键: R, 事件: 进世界 }
        // 区域是子结构，先递归取出来；平铺形态（区域字段直接写在触发表上）也兼容。
        double[] p1 = null;
        double[] p2 = null;
        boolean once = true;
        Object areaObj = first(m, "区域", "area");
        if (areaObj instanceof Map) {
            CameraTrigger at = parse(areaObj);
            if (at != null && at.pos1 != null) {
                p1 = at.pos1;
                p2 = at.pos2;
                once = at.once;
            }
        } else {
            p1 = vec(first(m, "pos1", "点1"));
            p2 = vec(first(m, "pos2", "点2"));
            Object onceObj = first(m, "once", "单次");
            if (onceObj instanceof Boolean) {
                once = ((Boolean) onceObj).booleanValue();
            } else if (onceObj instanceof String) {
                once = Boolean.parseBoolean(String.valueOf(onceObj).trim());
            }
        }
        List<String> keys = strings(first(m, "按键", "keys", "key"));
        String ev = str(first(m, "事件", "event"));
        return new CameraTrigger(keys, p1, p2, once, ev);
    }

    /** 列表 = 多个触发合并。 */
    private static CameraTrigger parseList(List<?> list) {
        List<String> keys = new ArrayList<String>();
        double[] p1 = null;
        double[] p2 = null;
        boolean once = true;
        String ev = null;
        for (Object item : list) {
            CameraTrigger t = parse(item);
            if (t == null) {
                continue;
            }
            keys.addAll(t.keys);
            if (t.pos1 != null) {
                p1 = t.pos1;
                p2 = t.pos2;
                once = t.once;
            }
            if (t.event != null) {
                ev = t.event;
            }
        }
        return new CameraTrigger(keys, p1, p2, once, ev);
    }

    /** 事件名集合（进世界 / 重生）；区域/按键走各自字段。 */
    private static boolean isEventName(String s) {
        return "进世界".equals(s) || "重生".equals(s)
                || "join".equalsIgnoreCase(s) || "respawn".equalsIgnoreCase(s);
    }

    /** 纯几何：点是否在箱体内（含边界，两角任意顺序）。 */
    public static boolean contains(double[] p, double[] a1, double[] a2) {
        if (p == null || a1 == null || a2 == null || p.length < 3) {
            return false;
        }
        for (int i = 0; i < 3; i++) {
            double lo = Math.min(a1[i], a2[i]);
            double hi = Math.max(a1[i], a2[i]);
            if (p[i] < lo || p[i] > hi) {
                return false;
            }
        }
        return true;
    }

    /** 列表显示用：有没有任何一种触发。 */
    public boolean present() {
        return !keys.isEmpty() || pos1 != null || event != null;
    }

    private static Object first(Map<?, ?> m, String... names) {
        for (String n : names) {
            Object v = m.get(n);
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    private static List<String> strings(Object o) {
        List<String> out = new ArrayList<String>();
        if (o instanceof String) {
            out.add(String.valueOf(o).trim().toLowerCase());
        } else if (o instanceof List) {
            for (Object x : (List<?>) o) {
                if (x != null) {
                    out.add(String.valueOf(x).trim().toLowerCase());
                }
            }
        }
        return out;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o).trim();
    }

    private static double[] vec(Object o) {
        if (!(o instanceof List)) {
            return null;
        }
        List<?> l = (List<?>) o;
        if (l.size() < 3) {
            return null;
        }
        try {
            return new double[]{num(l.get(0)), num(l.get(1)), num(l.get(2))};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static double num(Object o) {
        if (o instanceof Number) {
            return ((Number) o).doubleValue();
        }
        return Double.parseDouble(String.valueOf(o).trim());
    }
}
