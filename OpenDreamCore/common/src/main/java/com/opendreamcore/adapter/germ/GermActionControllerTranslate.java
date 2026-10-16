package com.opendreamcore.adapter.germ;

import com.opendreamcore.model.anim.ControllerIR;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 动作包翻译：萌芽风格「通常动作.yml」→ 控制器 IR。
 *
 * <pre>
 * 前翻滚:
 *   name: 前翻滚
 *   speed: 1
 *   duration: 800
 *   action: |-            # MythicMobs 联动脚本：不翻，服务端侧的事
 *   subject: [...]        # 同上，原样保留不报错
 * </pre>
 *
 * 条目键是外部触发名；条目里的 name 是客户端动画名（缺省时回退为条目键）。触发本身就是
 * 一次性语义（播完回 default）。speed/duration 收进触发字段，可选 next
 * 接到动画队列尾部。
 */
public final class GermActionControllerTranslate {

    private GermActionControllerTranslate() {
    }

    /** 动作包形态：无任何控制器节 + 至少一个「含 action/subject 的表」条目。 */
    public static boolean isActionPack(Map<String, Object> root) {
        if (root == null || root.isEmpty() || root.containsKey("Layer")
                || root.containsKey("Trigger") || root.containsKey("states")
                || root.containsKey("layers") || root.containsKey("triggers")) {
            return false;
        }
        for (Object v : root.values()) {
            if (v instanceof Map) {
                Map<?, ?> m = (Map<?, ?>) v;
                if (m.containsKey("action") || m.containsKey("subject")
                        || (m.containsKey("duration") && m.containsKey("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    public static ControllerIR translate(Map<String, Object> root) {
        List<ControllerIR.Trigger> triggers = new ArrayList<ControllerIR.Trigger>();
        for (Map.Entry<String, Object> e : root.entrySet()) {
            if (!(e.getValue() instanceof Map)) {
                continue;
            }
            Map<?, ?> m = (Map<?, ?>) e.getValue();
            String key = e.getKey();
            String animation = str(m.get("name"));
            if (animation == null || animation.isEmpty()) {
                animation = key;
            }
            List<String> anims = new ArrayList<String>();
            anims.add(animation);
            String next = str(m.get("next"));
            if (next != null) {
                for (String part : next.split(">")) {
                    if (!part.trim().isEmpty()) {
                        anims.add(part.trim());
                    }
                }
            }
            triggers.add(new ControllerIR.Trigger(key, "base", anims, 0.1,
                    num(m.get("speed"), 1.0), (long) num(m.get("duration"), 0)));
        }
        return new ControllerIR("", new ArrayList<ControllerIR.State>(),
                Collections.singletonList(
                        new ControllerIR.Layer("base", "OVERRIDE", 1.0, false, null, "", 0)),
                triggers);
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v).trim();
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
