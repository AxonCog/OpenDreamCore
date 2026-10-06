package com.opendreamcore.camera;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 相机规则 IR（版本无关纯 Java 对象）。
 *
 * 对应 visual/Camera.yml 的一个顶层键（一条运镜）。字段缺省即安全：
 * 不写 lock_input 就是不锁、不写 player 就是 keep——绝不能让一个字段漏写
 * 就把玩家锁死（规范 §4.1 的硬要求）。表达式值（position/rotation 写成字符串）
 * 本期原样保留在 {@code positionExpr}/{@code rotationExpr}，M1.3 接脚本求值后
 * 编译成闭包缓存；关键帧里的表达式本期直接丢弃（记 bad 规则，宁缺毋错）。
 *
 * 本包禁止 import net.minecraft / com.mojang（机检 R5/R6 管辖）。
 */
public final class CameraRule {

    public enum Type { SHOT, PATH, FREE }

    /** 关键帧（type: path）。t 为秒；pos/rot/fov/look 均可空。 */
    public static final class Keyframe {
        public final double t;
        public final double[] pos;
        public final double[] rot;
        public final Double fov;
        public final String look;
        /** 本帧到下一帧的过渡方式（M1.9）：null/"smooth"=跟随全局 smooth，"linear"=直线，"held"=定格到下一帧。 */
        public final String ease;

        Keyframe(double t, double[] pos, double[] rot, Double fov, String look, String ease) {
            this.t = t;
            this.pos = pos;
            this.rot = rot;
            this.fov = fov;
            this.look = look;
            this.ease = ease == null || ease.isEmpty() ? null : ease.toLowerCase(java.util.Locale.ROOT);
        }
    }

    public final String id;
    public final Type type;
    /** 固定机位世界坐标；表达式写法时为 null，见 positionExpr。 */
    public final double[] position;
    /** [yaw, pitch]；表达式写法时为 null，见 rotationExpr。 */
    public final double[] rotation;
    public final String positionExpr;
    public final String rotationExpr;
    /** 看向目标（如 "实体:剧情NPC_01"），与 rotation 二选一，look 优先；null=不指定。 */
    public final String look;
    /** first_person | third_person_back | third_person_front；null=保持当前。 */
    public final String perspective;
    /** hide_body（假人模式）| follow（随本体）| keep；null=keep。 */
    public final String player;
    public final int transitionIn;
    public final int transitionOut;
    public final boolean lockInput;
    public final boolean hideHud;
    public final double shakeStrength;
    public final int shakeTicks;
    public final double stretch;
    public final List<String> interrupt;
    /** 目标玩家选择器（原样保留，M1.2 服务端裁决时解析）。 */
    public final String players;
    public final boolean smoke;
    public final double duration;
    public final boolean loop;
    public final double speed;
    public final boolean smooth;
    public final List<Keyframe> keyframes;
    /** 触发器（M1.6）：按键/区域/事件自动发起；null = 无自动触发。 */
    public final CameraTrigger trigger;
    /** 时间轴事件点（M1.6）：按演出时刻触发 sound/particle/script。 */
    public final List<CameraEvent> events;

    private CameraRule(String id, Type type, double[] position, double[] rotation,
                       String positionExpr, String rotationExpr, String look, String perspective,
                       String player, int transitionIn, int transitionOut, boolean lockInput,
                       boolean hideHud, double shakeStrength, int shakeTicks, double stretch,
                       List<String> interrupt, String players, boolean smoke, double duration,
                       boolean loop, double speed, boolean smooth, List<Keyframe> keyframes,
                       CameraTrigger trigger, List<CameraEvent> events) {
        this.id = id;
        this.type = type;
        this.position = position;
        this.rotation = rotation;
        this.positionExpr = positionExpr;
        this.rotationExpr = rotationExpr;
        this.look = look;
        this.perspective = perspective;
        this.player = player;
        this.transitionIn = transitionIn;
        this.transitionOut = transitionOut;
        this.lockInput = lockInput;
        this.hideHud = hideHud;
        this.shakeStrength = shakeStrength;
        this.shakeTicks = shakeTicks;
        this.stretch = stretch;
        // JDK8 字节码兼容（common-j8 门禁）：不用 List.copyOf / Map.of / String.isBlank
        this.interrupt = Collections.unmodifiableList(new ArrayList<>(interrupt));
        this.players = players;
        this.smoke = smoke;
        this.duration = duration;
        this.loop = loop;
        this.speed = speed;
        this.smooth = smooth;
        this.keyframes = Collections.unmodifiableList(new ArrayList<>(keyframes));
        this.trigger = trigger;
        this.events = Collections.unmodifiableList(new ArrayList<>(events));
    }

    /** 从单条规则的 YAML 表解析。字段缺失一律取安全缺省；坏值抛异常由上层跳过。 */
    public static CameraRule parse(String id, Map<String, Object> y) {
        List<Keyframe> frames = keyframes(y.get("keyframes"));
        String typeStr = str(y.get("type"));
        Type type;
        if ("path".equalsIgnoreCase(typeStr)) {
            type = Type.PATH;
        } else if ("free".equalsIgnoreCase(typeStr)) {
            type = Type.FREE;
        } else if ("shot".equalsIgnoreCase(typeStr)) {
            type = Type.SHOT;
        } else {
            // 没写 type：有关键帧当路径，否则当固定机位
            type = typeStr == null && !frames.isEmpty() ? Type.PATH : Type.SHOT;
        }
        double[] position = vec(y.get("position"));
        double[] rotation = vec(y.get("rotation"));
        double[] shake = vec(y.get("shake"));
        double[] transition = vec(y.get("transition"));
        List<String> interrupt = new ArrayList<>();
        Object itv = y.get("interrupt");
        if (itv instanceof List<?> l) {
            for (Object o : l) {
                interrupt.add(String.valueOf(o).trim());
            }
        } else if (itv instanceof String s && !s.trim().isEmpty()) {
            for (String piece : s.split("[,，]")) {
                interrupt.add(piece.trim());
            }
        }
        return new CameraRule(id, type, position, rotation,
                position == null ? str(y.get("position")) : null,
                rotation == null ? str(y.get("rotation")) : null,
                str(y.get("look")),
                str(y.get("perspective")),
                str(y.get("player")),
                transition != null && transition.length > 0 ? (int) transition[0] : 0,
                transition != null && transition.length > 1 ? (int) transition[1] : 0,
                boolOr(y.get("lock_input"), false),
                boolOr(y.get("hide_hud"), false),
                shake != null && shake.length > 0 ? shake[0] : 0.0,
                shake != null && shake.length > 1 ? (int) shake[1] : 0,
                numOr(y, "stretch", 1.0),
                interrupt,
                str(y.get("players")),
                boolOr(y.get("smoke"), false),
                numOr(y, "duration", 0.0),
                boolOr(y.get("loop"), false),
                numOr(y, "speed", 1.0),
                boolOr(y.get("smooth"), true),
                frames,
                CameraTrigger.parse(y.get("触发")),
                CameraEvent.parseList(y.get("事件")));
    }

    /** /odc camera list 的单行摘要（纯文本，不着色——着色是客户端层的事）。 */
    public String summaryLine() {
        StringBuilder sb = new StringBuilder(id).append(" [").append(typeLabel()).append("]");
        if (position != null) {
            sb.append(" 位置").append(fmt(position));
        }
        if (rotation != null) {
            sb.append(" 朝向").append(fmt(rotation));
        }
        if (look != null) {
            sb.append(" 看向 ").append(look);
        }
        if (duration > 0) {
            sb.append(" 时长").append(duration).append('s');
        }
        if (!keyframes.isEmpty()) {
            sb.append(' ').append(keyframes.size()).append(" 个关键帧");
        }
        if (lockInput) {
            sb.append(" 锁输入");
        }
        if (hideHud) {
            sb.append(" 隐HUD");
        }
        return sb.toString();
    }

    public String typeLabel() {
        return switch (type) {
            case SHOT -> "固定机位";
            case PATH -> "路径";
            case FREE -> "自由";
        };
    }

    private static String fmt(double[] v) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < v.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(v[i] == Math.rint(v[i]) ? String.valueOf((long) v[i]) : String.valueOf(v[i]));
        }
        return sb.append(']').toString();
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o).trim();
    }

    /** 数值向量：Number → 单元素；List → 全数值才收；表达式字符串等非数值返回 null。 */
    private static double[] vec(Object o) {
        if (o instanceof Number n) {
            return new double[]{n.doubleValue()};
        }
        if (!(o instanceof List<?> l) || l.isEmpty()) {
            return null;
        }
        double[] out = new double[l.size()];
        for (int i = 0; i < l.size(); i++) {
            if (!(l.get(i) instanceof Number n)) {
                return null;
            }
            out[i] = n.doubleValue();
        }
        return out;
    }

    private static double numOr(Map<String, Object> y, String key, double def) {
        Object o = y.get(key);
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        if (o instanceof String s) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }

    private static boolean boolOr(Object o, boolean def) {
        if (o instanceof Boolean b) {
            return b;
        }
        if (o instanceof String s && !s.trim().isEmpty()) {
            return Boolean.parseBoolean(s.trim());
        }
        return def;
    }

    @SuppressWarnings("unchecked")
    private static List<Keyframe> keyframes(Object o) {
        List<Keyframe> out = new ArrayList<>();
        if (!(o instanceof List<?> l)) {
            return out;
        }
        for (Object e : l) {
            if (!(e instanceof Map)) {
                continue;
            }
            Map<String, Object> k = (Map<String, Object>) e;
            // 关键帧内的表达式本期不支持（宁缺毋错）：非数值的 pos/rot 直接丢弃该帧
            double[] pos = vec(k.get("pos"));
            double[] rot = vec(k.get("rot"));
            Double fov = k.get("fov") instanceof Number n ? n.doubleValue() : null;
            out.add(new Keyframe(numOr(k, "t", 0.0), pos, rot, fov, str(k.get("look")), str(k.get("ease"))));
        }
        out.sort(Comparator.comparingDouble(k -> k.t));
        return out;
    }
}
