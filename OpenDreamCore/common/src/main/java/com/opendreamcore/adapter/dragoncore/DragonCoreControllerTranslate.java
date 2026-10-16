package com.opendreamcore.adapter.dragoncore;

import com.opendreamcore.model.anim.ControllerIR;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 龙核控制器写法 → 控制器 IR 的归一翻译。
 *
 * <p>认这两种节：
 * <pre>
 * Layer:
 *   特效:
 *     blendType: ADDING          # OVERRIDE|ADDING
 *     weight: 0.6
 *     animations: [起手, 循环]    # 层内播放队列
 *     next: 收尾
 *     nextTransitionTime: 0.2
 *     lockBodyAngle: true
 * Trigger:
 *   施法: 施法动作                # 值是动画名，也可以是带 layer 的表
 * </pre>
 * 字段名原样收下，不发明新词——服主从那边搬配置过来零改动。
 */
public final class DragonCoreControllerTranslate {

    private DragonCoreControllerTranslate() {
    }

    public static ControllerIR translate(Map<String, Object> root) {
        List<ControllerIR.Layer> layers = new ArrayList<ControllerIR.Layer>();
        Object layerNode = root.get("Layer");
        if (layerNode instanceof Map) {
            for (Object o : ((Map<?, ?>) layerNode).entrySet()) {
                Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
                layers.add(layer(String.valueOf(e.getKey()), asMap(e.getValue())));
            }
        }
        List<ControllerIR.Trigger> triggers = new ArrayList<ControllerIR.Trigger>();
        Object triggerNode = root.get("Trigger");
        if (triggerNode instanceof Map) {
            for (Object o : ((Map<?, ?>) triggerNode).entrySet()) {
                Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
                triggers.add(trigger(String.valueOf(e.getKey()), e.getValue()));
            }
        }
        // 龙核的 base 层是隐式的：没写就补一条，状态引擎永远有地方站
        if (!hasBase(layers)) {
            layers.add(0, new ControllerIR.Layer("base", "OVERRIDE", 1.0, false, null, "", 0));
        }
        return new ControllerIR("", new ArrayList<ControllerIR.State>(), layers, triggers);
    }

    private static ControllerIR.Layer layer(String name, Map<String, Object> m) {
        String blend = str(m, "blendType");
        if (blend == null) {
            blend = str(m, "blend_type");
        }
        List<String> queue = strList(m.get("animations"));
        return new ControllerIR.Layer(name, blend,
                num(m.get("weight"), 1.0),
                bool(m, "lockBodyAngle") || bool(m, "lock_body_angle"),
                queue,
                str(m, "next"),
                num(m.get("nextTransitionTime"), num(m.get("next_transition_time"), 0)));
    }

    @SuppressWarnings("unchecked")
    private static ControllerIR.Trigger trigger(String name, Object v) {
        if (v instanceof Map) {
            Map<String, Object> m = (Map<String, Object>) v;
            List<String> anims = strList(m.get("animations"));
            if (anims.isEmpty()) {
                String one = str(m, "animation");
                if (one != null) {
                    anims = splitQueue(one);
                }
            }
            return new ControllerIR.Trigger(name, str(m, "layer"),
                    anims, num(m.get("transitionTime"), num(m.get("transition_time"), 0)));
        }
        return new ControllerIR.Trigger(name, "base", splitQueue(String.valueOf(v)), 0);
    }

    /** "起手 > 循环 > 收尾" 拆队列；单个名字也能走。 */
    public static List<String> splitQueue(String s) {
        List<String> out = new ArrayList<String>();
        if (s != null) {
            for (String part : s.split(">")) {
                String t = part.trim();
                if (!t.isEmpty()) {
                    out.add(t);
                }
            }
        }
        return out;
    }

    private static boolean hasBase(List<ControllerIR.Layer> layers) {
        for (ControllerIR.Layer l : layers) {
            if ("base".equals(l.name)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object v) {
        return v instanceof Map ? (Map<String, Object>) v : java.util.Collections.emptyMap();
    }

    private static List<String> strList(Object v) {
        List<String> out = new ArrayList<String>();
        if (v instanceof List) {
            for (Object o : (List<?>) v) {
                if (o != null) {
                    out.add(String.valueOf(o).trim());
                }
            }
        } else if (v instanceof String) {
            out.addAll(splitQueue((String) v));
        }
        return out;
    }

    private static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? null : String.valueOf(v).trim();
    }

    private static boolean bool(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v instanceof Boolean) {
            return (Boolean) v;
        }
        return v != null && ("true".equalsIgnoreCase(String.valueOf(v))
                || "是".equals(v) || "1".equals(v));
    }

    private static double num(Object v, double fallback) {
        if (v instanceof Number) {
            return ((Number) v).doubleValue();
        }
        if (v != null) {
            try {
                return Double.parseDouble(String.valueOf(v));
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }
}
