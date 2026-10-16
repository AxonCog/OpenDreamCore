package com.opendreamcore.model.anim;

import com.opendreamcore.adapter.dragoncore.DragonCoreControllerTranslate;
import com.opendreamcore.adapter.germ.GermActionControllerTranslate;
import com.opendreamcore.config.ConfigParseException;
import com.opendreamcore.config.YamlParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 控制器 YAML → {@link ControllerIR}。一份文件两种写法，嗅探分发：
 * 根上有 {@code Layer}/{@code Trigger} 节的按龙核写法归一翻译，有
 * {@code states}/{@code default}/{@code layers}/{@code triggers} 的按原生写法解析，
 * 都不是就报一条能看懂的错（含文件该长什么样），绝不静默吞掉。
 */
public final class ControllerCodec {

    private ControllerCodec() {
    }

    public static ControllerIR parse(String yaml) throws ConfigParseException {
        Map<String, Object> root = new YamlParser().parse(yaml);
        if (root.isEmpty()) {
            throw new ConfigParseException("控制器是空的", 1, 1);
        }
        if (root.containsKey("Layer") || root.containsKey("Trigger")) {
            return DragonCoreControllerTranslate.translate(root);
        }
        if (GermActionControllerTranslate.isActionPack(root)) {
            return GermActionControllerTranslate.translate(root);
        }
        if (root.containsKey("states") || root.containsKey("default")
                || root.containsKey("layers") || root.containsKey("triggers")) {
            return native_(root);
        }
        throw new ConfigParseException(
                "认不出这是控制器：既没有 states/default（原生写法），也没有 Layer/Trigger（龙核写法）", 1, 1);
    }

    @SuppressWarnings("unchecked")
    private static ControllerIR native_(Map<String, Object> root) throws ConfigParseException {
        List<ControllerIR.State> states = parseStates(root.get("states"));
        List<ControllerIR.Layer> layers = parseLayers(root.get("layers"));
        List<ControllerIR.Trigger> triggers = parseTriggers(root.get("triggers"));
        return new ControllerIR(
                root.get("default") == null ? "" : String.valueOf(root.get("default")).trim(),
                states, layers, triggers);
    }

    private static List<ControllerIR.State> parseStates(Object node) throws ConfigParseException {
        List<ControllerIR.State> states = new ArrayList<ControllerIR.State>();
        if (!(node instanceof Map)) {
            return states;
        }
        for (Object o : ((Map<?, ?>) node).entrySet()) {
            Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
            Map<String, Object> m = asMap(e.getValue());
            String name = String.valueOf(e.getKey()).trim();
            List<String> variants = strList(m.get("variants"));
            String anim = m.get("anim") == null ? "" : String.valueOf(m.get("anim")).trim();
            if (variants.isEmpty() && anim.isEmpty()) {
                throw new ConfigParseException("状态 " + name + " 没写 anim 也没写 variants，"
                        + "引擎不知道播什么", 1, 1);
            }
            states.add(new ControllerIR.State(name, anim, variants,
                    m.get("when") == null ? "" : String.valueOf(m.get("when")).trim().toLowerCase(java.util.Locale.ROOT),
                    bool(m.get("once")),
                    m.get("loop") == null ? "" : String.valueOf(m.get("loop")).trim(),
                    num(m.get("transition_time"), 0),
                    num(m.get("next_transition_time"), 0),
                    num(m.get("priority"), 0)));
        }
        return states;
    }

    private static List<ControllerIR.Layer> parseLayers(Object node) {
        List<ControllerIR.Layer> layers = new ArrayList<ControllerIR.Layer>();
        if (node instanceof Map) {
            for (Object o : ((Map<?, ?>) node).entrySet()) {
                Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
                Map<String, Object> m = asMap(e.getValue());
                layers.add(new ControllerIR.Layer(String.valueOf(e.getKey()).trim(),
                        m.get("blend_type") == null ? str(m, "blendType") : str(m, "blend_type"),
                        num(m.get("weight"), 1.0),
                        bool(m.get("lock_body_angle")) || bool(m.get("lockBodyAngle")),
                        strList(m.get("animations")),
                        str(m, "next"),
                        num(m.get("next_transition_time"), num(m.get("nextTransitionTime"), 0))));
            }
        }
        if (layers.isEmpty()) {
            layers.add(new ControllerIR.Layer("base", "OVERRIDE", 1.0, false, null, "", 0));
        } else if (!hasBase(layers)) {
            layers.add(0, new ControllerIR.Layer("base", "OVERRIDE", 1.0, false, null, "", 0));
        }
        return layers;
    }

    private static List<ControllerIR.Trigger> parseTriggers(Object node) {
        List<ControllerIR.Trigger> triggers = new ArrayList<ControllerIR.Trigger>();
        if (!(node instanceof Map)) {
            return triggers;
        }
        for (Object o : ((Map<?, ?>) node).entrySet()) {
            Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
            Map<String, Object> m = asMap(e.getValue());
            List<String> anims = strList(m.get("anim"));
            if (anims.isEmpty()) {
                anims = strList(m.get("animations"));
            }
            triggers.add(new ControllerIR.Trigger(String.valueOf(e.getKey()).trim(),
                    str(m, "layer"), anims, num(m.get("transition_time"), 0)));
        }
        return triggers;
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
                if (o != null && !String.valueOf(o).trim().isEmpty()) {
                    out.add(String.valueOf(o).trim());
                }
            }
        } else if (v instanceof String) {
            for (String part : ((String) v).split(">")) {
                String t = part.trim();
                if (!t.isEmpty()) {
                    out.add(t);
                }
            }
        }
        return out;
    }

    private static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? null : String.valueOf(v).trim();
    }

    private static boolean bool(Object v) {
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
