package com.opendreamcore.model.format.anim;

import com.opendreamcore.model.format.CodecUtil;
import com.opendreamcore.model.format.JsonParser;
import com.opendreamcore.model.ir.AnimationIR;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Bedrock 动画 json（animations/*.json，geckolib 同格式）解析。
 *
 * 形态（宽容策略，坏帧跳过不炸整个文件）：
 *  {"animations": {"walk": {"loop": true|"hold_on_last_frame",
 *                           "anim_time_update"|"reverse": 可选,
 *                           "animation_length": 2.0,
 *                           "bones": {"body": {"rotation": <轨道>, "position": <轨道>, "scale": <轨道>}}}}}
 *
 * 轨道形态：
 *  1) [x,y,z] 常量；2) {"0.0":[x,y,z], "2.0":[...]} 逐帧；3) 值为 {"post":...,"pre":...}；
 *  4) 值/分量为 molang 字符串表达式。未知形态跳过该帧。
 */
public final class BedrockAnimCodec {

    private BedrockAnimCodec() {
    }

    /** 解析整个文件，返回动画列表（文件无 animations 或解析失败返回空表）。 */
    public static List<AnimationIR> parse(String text) {
        List<AnimationIR> out = new ArrayList<AnimationIR>();
        if (text == null || text.isEmpty()) {
            return out;
        }
        Object root;
        try {
            root = JsonParser.parse(text);
        } catch (Exception e) {
            return out;
        }
        Map<String, Object> map = CodecUtil.map(root);
        if (map == null) {
            return out;
        }
        Map<String, Object> anims = CodecUtil.map(map.get("animations"));
        if (anims == null) {
            return out;
        }
        for (Map.Entry<String, Object> e : anims.entrySet()) {
            AnimationIR ir = parseOne(e.getKey(), CodecUtil.map(e.getValue()));
            if (ir != null && !ir.isEmpty()) {
                out.add(ir);
            }
        }
        return out;
    }

    private static AnimationIR parseOne(String name, Map<String, Object> a) {
        if (a == null) {
            return null;
        }
        double length = CodecUtil.num(a.get("animation_length"));
        AnimationIR ir = new AnimationIR(name, length, loopOf(a.get("loop")));
        if (Boolean.TRUE.equals(a.get("reverse"))) {
            ir.reverse = true;
        }
        // 动作包扩展字段：倍速 + 打断语义（非标准，缺省即原速/不打断）
        double sp = CodecUtil.num(a.get("speed"));
        if (sp > 0) {
            ir.speed = sp;
        }
        if (Boolean.TRUE.equals(a.get("override_previous_animation"))) {
            ir.overridePrevious = true;
        }
        Map<String, Object> bones = CodecUtil.map(a.get("bones"));
        if (bones == null) {
            return ir;
        }
        for (Map.Entry<String, Object> be : bones.entrySet()) {
            Map<String, Object> channels = CodecUtil.map(be.getValue());
            if (channels == null) {
                continue;
            }
            AnimationIR.BoneAnim ba = ir.ensureBone(be.getKey());
            track(channels.get("rotation"), ba, "rot");
            track(channels.get("position"), ba, "pos");
            track(channels.get("scale"), ba, "scale");
        }
        return ir;
    }

    private static String loopOf(Object raw) {
        if (raw instanceof Boolean) {
            return ((Boolean) raw) ? AnimationIR.LOOP_LOOP : AnimationIR.LOOP_ONCE;
        }
        String s = CodecUtil.str(raw);
        if (s == null) {
            return AnimationIR.LOOP_ONCE;
        }
        if ("hold_on_last_frame".equalsIgnoreCase(s)) {
            return AnimationIR.LOOP_HOLD;
        }
        if ("loop".equalsIgnoreCase(s)) {
            return AnimationIR.LOOP_LOOP;
        }
        return AnimationIR.LOOP_ONCE;
    }

    private static void track(Object raw, AnimationIR.BoneAnim ba, String channel) {
        if (raw == null) {
            return;
        }
        AnimationIR.Track t = new AnimationIR.Track();
        if (raw instanceof Map) {
            fillKeyframes((Map<String, Object>) raw, t);
        } else {
            AnimationIR.Keyframe f = valueFrame(0, raw);
            if (f != null) {
                t.keys.add(f);
            }
        }
        if (!t.keys.isEmpty()) {
            if (channel.equals("rot")) {
                // bedrock→java 轴约定：rotation 的 X/Y 取反、Z 不变（GeckoLib 同款：
                // axis==X||Y → *= -1）。不取反的话身体转向反+低头变仰头，
                // 组合出弯腰驼背怪姿势（右闪避实机截图实锤）
                for (AnimationIR.Keyframe k : t.keys) {
                    flipRotation(k.value, k.expr);
                    if (k.preValue != null) {
                        flipRotation(k.preValue, k.preExpr);
                    }
                }
            }
            t.sort();
            switch (channel) {
                case "rot":
                    ba.rot = t;
                    break;
                case "pos":
                    ba.pos = t;
                    break;
                default:
                    ba.scale = t;
            }
        }
    }

    /** 时间轴形态：键是时间、值是帧/帧表/表达式。 */
    @SuppressWarnings("unchecked")
    private static void fillKeyframes(Map<String, Object> raw, AnimationIR.Track t) {
        for (Map.Entry<String, Object> e : raw.entrySet()) {
            Double time = CodecUtil.tryNum(e.getKey());
            if (time == null) {
                continue;
            }
            Object val = e.getValue();
            if (val instanceof Map) {
                fillFramed(t, time, CodecUtil.map(val));
            } else {
                AnimationIR.Keyframe f = valueFrame(time, val);
                if (f != null) {
                    t.keys.add(f);
                }
            }
        }
    }

    /** 一时刻只保留一个关键帧：进入用 pre，时刻本身和离开用 post。 */
    private static void fillFramed(AnimationIR.Track t, double time, Map<String, Object> kf) {
        AnimationIR.Keyframe pre = valueFrame(time, kf.get("pre"));
        AnimationIR.Keyframe post = valueFrame(time, kf.get("post"));
        AnimationIR.Keyframe f = post != null ? post : pre;
        if (f == null) {
            f = valueFrame(time, kf);
        }
        if (f == null) {
            return;
        }
        if (pre != null && post != null) {
            f.preValue = pre.value;
            f.preExpr = pre.expr;
        }
        Object easing = kf.containsKey("post_easing") ? kf.get("post_easing") : kf.get("easing");
        Object args = kf.containsKey("post_easing_args") ? kf.get("post_easing_args") : kf.get("easing_args");
        f.easing = CodecUtil.str(easing);
        f.easingArgs = easingArgs(args);
        f.preEasing = kf.containsKey("pre_easing") ? CodecUtil.str(kf.get("pre_easing")) : f.easing;
        f.preEasingArgs = kf.containsKey("pre_easing_args") ? easingArgs(kf.get("pre_easing_args")) : f.easingArgs;
        String mode = CodecUtil.str(kf.get("lerp_mode"));
        if (mode != null && !mode.isEmpty()) {
            f.interp = mode;
            f.lerpMode = mode;
        }
        t.keys.add(f);
    }

    private static double[] easingArgs(Object raw) {
        List<Object> args = CodecUtil.list(raw);
        if (args == null || args.isEmpty()) {
            return null;
        }
        double[] result = new double[args.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = CodecUtil.num(args.get(i));
        }
        return result;
    }

    /** 数字/表达式分量原样保留，不能在解析时把表达式静默改成 0。 */
    private static AnimationIR.Keyframe valueFrame(double time, Object raw) {
        List<Object> list = CodecUtil.list(raw);
        Map<String, Object> map = CodecUtil.map(raw);
        boolean scalar = raw instanceof Number || raw instanceof String;
        if (list == null && !scalar && (map == null
                || (!map.containsKey("x") && !map.containsKey("y") && !map.containsKey("z")))) {
            return null;
        }
        AnimationIR.Keyframe f = new AnimationIR.Keyframe(time, new double[3]);
        for (int i = 0; i < 3; i++) {
            Object v = list != null ? (i < list.size() ? list.get(i) : null)
                    : scalar ? raw : map.get(i == 0 ? "x" : i == 1 ? "y" : "z");
            if (v instanceof String) {
                Double number = CodecUtil.tryNum((String) v);
                if (number == null) {
                    f.expr[i] = (String) v;
                } else {
                    f.value[i] = number;
                }
            } else {
                f.value[i] = CodecUtil.num(v);
            }
        }
        return f;
    }

    private static void flipRotation(double[] value, String[] expr) {
        for (int i = 0; i < 2; i++) {
            value[i] = -value[i];
            if (expr != null && expr[i] != null && !expr[i].isEmpty()) {
                expr[i] = "-(" + expr[i] + ")";
            }
        }
    }
}
