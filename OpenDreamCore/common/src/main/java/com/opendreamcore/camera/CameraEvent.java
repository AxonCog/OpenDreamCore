package com.opendreamcore.camera;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 时间轴事件点：演出进行到某时刻触发 sound / particle / script；
 * animation 只保留接口（解析入库，执行侧记录日志）。
 * <pre>
 * 事件:
 *   - 时刻: 2.5
 *     类型: sound
 *     值: minecraft:entity.experience_orb.pickup
 *     音量: 1.0
 *     音调: 1.0
 *   - 时刻: 4
 *     类型: particle
 *     值: minecraft:heart
 *     偏移: [0, 1.5, 0]        # 相对当前机位
 *   - 时刻: 6
 *     类型: script
 *     值: 'UI.提示("中段")'
 * </pre>
 * 纯数据 + 纯窗口计算，不摸 MC（执行在客户端 CameraEventFire）。
 * JDK8 字节码兼容（common-j8 门禁）：不用 List.of / instanceof 模式匹配 / isBlank。
 */
public final class CameraEvent {

    public enum Kind { SOUND, PARTICLE, SCRIPT, ANIMATION }

    /** 触发时刻（秒）。 */
    public final double t;
    public final Kind kind;
    /** 资源 id 或脚本文本。 */
    public final String value;
    public final double volume;
    public final double pitch;
    /** 相对机位偏移（particle 用）；null = 机位原地。 */
    public final double[] offset;

    private CameraEvent(double t, Kind kind, String value, double volume, double pitch, double[] offset) {
        this.t = t;
        this.kind = kind;
        this.value = value;
        this.volume = volume;
        this.pitch = pitch;
        this.offset = offset;
    }

    /** 解析规则的 `事件:` 列表；坏条目跳过。 */
    public static List<CameraEvent> parseList(Object o) {
        List<CameraEvent> out = new ArrayList<CameraEvent>();
        if (!(o instanceof List)) {
            return out;
        }
        for (Object item : (List<?>) o) {
            if (item instanceof Map) {
                CameraEvent e = parseOne((Map<?, ?>) item);
                if (e != null) {
                    out.add(e);
                }
            }
        }
        return out;
    }

    private static CameraEvent parseOne(Map<?, ?> m) {
        Double t = dnum(first(m, "时刻", "时间", "t", "time"));
        if (t == null) {
            return null;
        }
        String kindStr = str(first(m, "类型", "kind", "type"), "sound");
        Kind kind;
        if ("particle".equalsIgnoreCase(kindStr) || "粒子".equals(kindStr)) {
            kind = Kind.PARTICLE;
        } else if ("script".equalsIgnoreCase(kindStr) || "脚本".equals(kindStr)) {
            kind = Kind.SCRIPT;
        } else if ("animation".equalsIgnoreCase(kindStr) || "动画".equals(kindStr)) {
            kind = Kind.ANIMATION;
        } else {
            kind = Kind.SOUND;
        }
        String value = str(first(m, "值", "value", "id"), null);
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        Double vol = dnum(first(m, "音量", "volume"));
        Double pit = dnum(first(m, "音调", "pitch"));
        double[] off = vec(first(m, "偏移", "offset"));
        return new CameraEvent(t, kind, value.trim(),
                vol == null ? 1.0 : vol.doubleValue(), pit == null ? 1.0 : pit.doubleValue(), off);
    }

    /** 窗口内该触发的条目：(prev, now] —— prev 是上一 tick 已推进到的时间。 */
    public static List<CameraEvent> due(List<CameraEvent> events, double prev, double now) {
        List<CameraEvent> out = new ArrayList<CameraEvent>();
        if (events == null || now <= prev) {
            return out;
        }
        for (CameraEvent e : events) {
            if (e.t > prev && e.t <= now) {
                out.add(e);
            }
        }
        return out;
    }

    /** 开场即触发（t<=0）的条目——由播放启动侧调用，不进 due 窗口。 */
    public static List<CameraEvent> startup(List<CameraEvent> events) {
        List<CameraEvent> out = new ArrayList<CameraEvent>();
        if (events == null) {
            return out;
        }
        for (CameraEvent e : events) {
            if (e.t <= 0.0) {
                out.add(e);
            }
        }
        return out;
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

    private static String str(Object o, String fallback) {
        return o == null ? fallback : String.valueOf(o).trim();
    }

    private static Double dnum(Object o) {
        if (o instanceof Number) {
            return Double.valueOf(((Number) o).doubleValue());
        }
        if (o instanceof String) {
            try {
                return Double.valueOf(Double.parseDouble(String.valueOf(o).trim()));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static double[] vec(Object o) {
        if (!(o instanceof List)) {
            return null;
        }
        List<?> l = (List<?>) o;
        if (l.size() < 3) {
            return null;
        }
        Double x = dnum(l.get(0));
        Double y = dnum(l.get(1));
        Double z = dnum(l.get(2));
        if (x == null || y == null || z == null) {
            return null;
        }
        return new double[]{x.doubleValue(), y.doubleValue(), z.doubleValue()};
    }
}
