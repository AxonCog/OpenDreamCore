package com.opendreamcore.camera;

import java.util.Locale;
import java.util.Map;

/**
 * 运镜设置：控制相机接管策略。
 *
 * <p>它不属于任何一条规则（规则管「拍什么」，这里管「用哪条路拍」），所以放在
 * {@code visual/Camera.yml} 的保留键 {@link CameraStore#SETTINGS_KEY} 之下——同文件、
 * 一眼能看到，但解析时不当规则：
 *
 * <pre>
 * 运镜设置:
 *   detached: true            # 直接驱动原版相机；false 则使用临时实体路线
 *   yield: true               # 相机已被别的模组接管（cameraEntity 不是玩家）时让位
 *   yield_with_shader: false  # 装了光影（Iris/OptiFine）时是否也让位
 *   priority: 10              # 接管优先级（让位决策与诊断都用它）
 * </pre>
 *
 * <p><b>缺省即安全</b>：字段缺失、类型不对、整块没写，一律取默认值，绝不抛异常——
 * 这文件是给人手改的，写错一个字母不该让运镜整个功能失效。
 */
public final class CameraSettings {

    /** 默认：直接驱动开、被别人占了就让位、光影不让位、优先级 10。 */
    private static final boolean DEF_DETACHED = true;
    private static final boolean DEF_YIELD = true;
    private static final boolean DEF_YIELD_SHADER = false;
    private static final int DEF_PRIORITY = 10;

    /** true = 直接驱动相机；false = 使用临时实体。 */
    public final boolean detached;
    /** 相机已被别的模组接管时是否让位（不抢）。 */
    public final boolean yieldToOthers;
    /** 装了光影时是否让位。默认不让：detached 只改相机姿态，不抢渲染管线。 */
    public final boolean yieldToShader;
    /** 接管优先级（数值越大越优先；目前用于让位决策与日志，给将来多接管者协商留位）。 */
    public final int priority;

    public CameraSettings(boolean detached, boolean yieldToOthers, boolean yieldToShader, int priority) {
        this.detached = detached;
        this.yieldToOthers = yieldToOthers;
        this.yieldToShader = yieldToShader;
        this.priority = priority;
    }

    public static CameraSettings defaults() {
        return new CameraSettings(DEF_DETACHED, DEF_YIELD, DEF_YIELD_SHADER, DEF_PRIORITY);
    }

    /** 从 YAML 解析出来的表里取设置；null/空 → 默认。任何字段坏掉都只影响它自己。 */
    public static CameraSettings parse(Map<String, Object> m) {
        if (m == null || m.isEmpty()) {
            return defaults();
        }
        return new CameraSettings(
                bool(m, "detached", DEF_DETACHED),
                bool(m, "yield", DEF_YIELD),
                bool(m, "yield_with_shader", DEF_YIELD_SHADER),
                number(m, "priority", DEF_PRIORITY));
    }

    /** 一行诊断（进 latest.log，命令侧另有中文展示）。 */
    public String describe() {
        return "detached=" + detached + " yield=" + yieldToOthers
                + " yield_with_shader=" + yieldToShader + " priority=" + priority;
    }

    private static boolean bool(Map<String, Object> m, String key, boolean dflt) {
        Object v = m.get(key);
        if (v instanceof Boolean b) {
            return b;
        }
        if (v instanceof Number n) {
            return n.doubleValue() != 0.0;
        }
        if (v instanceof String s) {
            String t = s.trim().toLowerCase(Locale.ROOT);
            if (t.equals("true") || t.equals("yes") || t.equals("on") || t.equals("1")) {
                return true;
            }
            if (t.equals("false") || t.equals("no") || t.equals("off") || t.equals("0")) {
                return false;
            }
        }
        return dflt;
    }

    private static int number(Map<String, Object> m, String key, int dflt) {
        Object v = m.get(key);
        if (v instanceof Number n) {
            return n.intValue();
        }
        if (v instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
                return dflt;
            }
        }
        return dflt;
    }
}
