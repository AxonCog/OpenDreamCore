package com.opendreamcore.model.anim;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 控制器解析产物：状态、层、触发三张表，全部是不可变快照——引擎随便跨线程读。
 *
 * <p>两个来源共用这一份结构：原生写法（states/layers/triggers）和龙核写法
 * （Layer/Trigger）由 {@link ControllerCodec} 嗅探后各自归一到这里，
 * 运行时只认这一种，后面接什么都不用改。
 */
public final class ControllerIR {

    /** 无动作时的家；空串=没有默认（引擎自己兜底）。 */
    public final String defaultAnim;

    public final List<State> states;
    public final List<Layer> layers;
    public final List<Trigger> triggers;

    public ControllerIR(String defaultAnim, List<State> states,
                        List<Layer> layers, List<Trigger> triggers) {
        this.defaultAnim = defaultAnim == null ? "" : defaultAnim;
        this.states = states == null ? new ArrayList<State>() : states;
        this.layers = layers == null ? new ArrayList<Layer>() : layers;
        this.triggers = triggers == null ? new ArrayList<Trigger>() : triggers;
    }

    /** 自动状态：满足 when 条件就切进来；once 播完回默认。 */
    public static final class State {
        public final String name;
        public final String anim;
        /** 随机变体：非空时忽略 anim，每次进入抽一条。 */
        public final List<String> variants;
        /** moving/swinging/sneak/swimming/dead/riding；空=不参与自动判定。 */
        public final String when;
        public final boolean once;
        /** once|loop|hold；空=跟动画自带。 */
        public final String loop;
        public final double transitionTime;
        public final double nextTransitionTime;
        /** 大者赢；同时满足多个 when 时定谁上场。 */
        public final double priority;

        public State(String name, String anim, List<String> variants, String when,
                     boolean once, String loop, double transitionTime,
                     double nextTransitionTime, double priority) {
            this.name = name;
            this.anim = anim == null ? "" : anim;
            this.variants = variants == null ? new ArrayList<String>() : variants;
            this.when = when == null ? "" : when;
            this.once = once;
            this.loop = loop == null ? "" : loop;
            this.transitionTime = transitionTime;
            this.nextTransitionTime = nextTransitionTime;
            this.priority = priority;
        }
    }

    /** 渲染层：OVERRIDE 盖住下面，ADDING 叠上去。 */
    public static final class Layer {
        public final String name;
        public final String blend;
        public final double weight;
        public final boolean lockBodyAngle;
        /** 层内固定播放队列（龙核的 animations 数组语义）。 */
        public final List<String> animQueue;
        public final String next;
        public final double nextTransitionTime;

        public Layer(String name, String blend, double weight, boolean lockBodyAngle,
                     List<String> animQueue, String next, double nextTransitionTime) {
            this.name = name;
            this.blend = blend == null || blend.isEmpty() ? "OVERRIDE" : blend;
            this.weight = weight;
            this.lockBodyAngle = lockBodyAngle;
            this.animQueue = animQueue == null ? new ArrayList<String>() : animQueue;
            this.next = next == null ? "" : next;
            this.nextTransitionTime = nextTransitionTime;
        }
    }

    /** 服务端按触发键名点火的入口；{@link #anims} 保存客户端动画名队列。 */
    public static final class Trigger {
        public final String name;
        public final String layer;
        /** 动画队列；"a > b" 写法在解析期已拆开。 */
        public final List<String> anims;
        public final double transitionTime;
        /** 播放倍率（动作包 speed 字段，1 = 原速）；触发队列推进与采样均按它缩放。 */
        public final double speed;
        /** 动作时长（毫秒，动作包 duration 字段；服务端联动用，客户端只透传）。 */
        public final long durationMs;

        public Trigger(String name, String layer, List<String> anims, double transitionTime) {
            this(name, layer, anims, transitionTime, 1.0, 0);
        }

        public Trigger(String name, String layer, List<String> anims, double transitionTime,
                       double speed, long durationMs) {
            this.name = name;
            this.layer = layer == null || layer.isEmpty() ? "base" : layer;
            this.anims = anims == null ? new ArrayList<String>() : anims;
            this.transitionTime = transitionTime;
            this.speed = speed <= 0 ? 1.0 : speed;
            this.durationMs = durationMs;
        }
    }

    public State state(String name) {
        for (State s : states) {
            if (s.name.equals(name)) {
                return s;
            }
        }
        return null;
    }

    public Layer layer(String name) {
        for (Layer l : layers) {
            if (l.name.equals(name)) {
                return l;
            }
        }
        return null;
    }

    public Trigger trigger(String name) {
        for (Trigger t : triggers) {
            if (t.name.equals(name)) {
                return t;
            }
        }
        return null;
    }
}
