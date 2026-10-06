package com.opendreamcore.camera;

import java.util.List;

/**
 * 关键帧时间轴求值（版本无关纯 Java；本包禁止 MC 依赖，机检 R5/R6 管辖）。
 *
 * <p>插值分两档（M1.3 起）：
 * <ul>
 *   <li>{@code smooth: true}（默认）→ 位置与 fov 走 Catmull-Rom 均匀参数化，曲线过每一帧，
 *       端点用重复点夹住（因此两点路径恰好退化成直线，与 M1.2 行为一致）；
 *       相邻两帧位置完全相同的段例外：一律走直线。重复关键帧（含「缺 pos 沿用前帧」）的意图就是
 *       「停住」，而均匀样条会在起跳前向后回抽一下——实測（自检用例 missingPosInFrameInheritsPreviousFrame）
 *       会把该停住的段拉出 -0.625 格的倒走。</li>
 *   <li>{@code smooth: false} → 位置与 fov 按分量线性过渡。</li>
 * </ul>
 * 朝向**始终**走「最短角 + 线性」：角度做样条会在关键帧附近过冲（镜头会甩过目标方向再回来），
 * 代价大于收益。等 M1.5 的看向目标落地、切线改由目标点决定时，再讨论角度样条。
 *
 * <p>缺省即安全（规范 §4.1 硬要求，也是这个类全部的防御逻辑所在）：
 * <ul>
 *   <li>路径少于 2 个关键帧 → 退化成静态姿态，不抛异常、不猜轨迹；</li>
 *   <li>关键帧缺 pos/rot → 沿「本帧往前找最近一个有值的帧 → 往后找第一个有值的帧
 *       → 规则自身的 position/rotation」三级兜底；</li>
 *   <li>三级兜底全空 → 返回 null 分量，由客户端层保持当前相机不动（不是跳到原点）；</li>
 *   <li>{@code speed ≤ 0}／NaN 当 1.0；{@code duration ≤ 0} 当「末帧时间」；</li>
 *   <li>yaw 走最短角（350° → 10° 是 +20°，不是 -340°），pitch 线性。</li>
 * </ul>
 *
 * <p>时间语义：{@code duration} 大于末帧时间时，末帧姿态保持到 {@code duration} 结束。
 */
public final class CameraTimeline {

    /** 浮点比较容差：秒级时间轴不需要更高精度。 */
    private static final double EPS = 1e-9;

    private CameraTimeline() {
    }

    /** 采样结果。pos/rot 为 null 表示「这条规则没定义」，调用方保持当前值。 */
    public static final class Pose {
        public final double[] pos;
        public final double[] rot;
        public final Double fov;

        Pose(double[] pos, double[] rot, Double fov) {
            this.pos = pos;
            this.rot = rot;
            this.fov = fov;
        }

        public boolean hasPos() {
            return pos != null && pos.length >= 3;
        }

        public boolean hasRot() {
            return rot != null && rot.length >= 2;
        }
    }

    /** 路径总时长（秒，已按 speed 缩放）。非路径 / 帧数不足 / 时长为 0 → 0。 */
    public static double totalSeconds(CameraRule rule) {
        if (rule == null || rule.type != CameraRule.Type.PATH) {
            return 0.0;
        }
        List<CameraRule.Keyframe> kfs = rule.keyframes;
        if (kfs.size() < 2) {
            return 0.0;
        }
        double end = rule.duration > 0 ? rule.duration : kfs.get(kfs.size() - 1).t;
        if (end <= 0) {
            return 0.0;
        }
        return end / speedOf(rule);
    }

    /**
     * 播完是否自动停：只有「路径 + 未开循环 + 时长 > 0」才自动停。
     * 固定机位/自由相机永远保持到手动 stop——假人模式自动放手会把玩家晾在原地。
     */
    public static boolean autoStops(CameraRule rule) {
        return rule != null && rule.type == CameraRule.Type.PATH
                && !rule.loop && totalSeconds(rule) > 0;
    }

    /** 静态姿态（固定机位/自由相机/帧数不足的路径）。 */
    public static Pose staticPose(CameraRule rule) {
        if (rule == null) {
            return null;
        }
        return new Pose(copy(rule.position, 3), copy(rule.rotation, 2), null);
    }

    /**
     * 采样。{@code seconds} 是「已扣掉进过渡」的路径时间（秒，未乘 speed，本方法内乘）。
     * 返回 null 仅当 rule 为 null；返回的 Pose 分量仍可能为 null。
     */
    public static Pose sample(CameraRule rule, double seconds) {
        if (rule == null) {
            return null;
        }
        List<CameraRule.Keyframe> kfs = rule.keyframes;
        if (rule.type != CameraRule.Type.PATH || kfs.size() < 2) {
            return staticPose(rule);
        }
        double tf = seconds * speedOf(rule);
        if (tf <= kfs.get(0).t) {
            return framePose(rule, kfs, 0);
        }
        int lastIdx = kfs.size() - 1;
        if (tf >= kfs.get(lastIdx).t) {
            return framePose(rule, kfs, lastIdx);
        }
        for (int i = 0; i < lastIdx; i++) {
            CameraRule.Keyframe a = kfs.get(i);
            CameraRule.Keyframe b = kfs.get(i + 1);
            if (tf >= a.t && tf <= b.t) {
                double span = b.t - a.t;
                double u = span > EPS ? (tf - a.t) / span : 0.0;
                return lerpPose(rule, kfs, i, i + 1, u);
            }
        }
        return framePose(rule, kfs, lastIdx);
    }

    /** 单帧姿态（含三级兜底）。 */
    private static Pose framePose(CameraRule rule, List<CameraRule.Keyframe> kfs, int idx) {
        CameraRule.Keyframe k = kfs.get(idx);
        return new Pose(resolvePos(rule, kfs, idx), resolveRot(rule, kfs, idx), k.fov);
    }

    /** 相邻两帧插值。 */
    private static Pose lerpPose(CameraRule rule, List<CameraRule.Keyframe> kfs, int ia, int ib, double u) {
        double[] pa = resolvePos(rule, kfs, ia);
        double[] pb = resolvePos(rule, kfs, ib);
        double[] ra = resolveRot(rule, kfs, ia);
        double[] rb = resolveRot(rule, kfs, ib);
        Double fa = kfs.get(ia).fov;
        Double fb = kfs.get(ib).fov;

        double[] pos = null;
        if (pa != null && pb != null) {
            // M1.9 分段缓动：帧上的 ease 覆盖全局插值档（held=定格，linear=直线）
            String ease = kfs.get(ia).ease;
            boolean smoothSeg = rule.smooth && !"linear".equals(ease) && !samePos(pa, pb);
            if ("held".equals(ease)) {
                pos = copy(pa, 3);
            } else if (smoothSeg) {
                double[] p0 = neighborPos(rule, kfs, ia - 1, pa);
                double[] p3 = neighborPos(rule, kfs, ib + 1, pb);
                pos = new double[]{
                        catmullRom(p0[0], pa[0], pb[0], p3[0], u),
                        catmullRom(p0[1], pa[1], pb[1], p3[1], u),
                        catmullRom(p0[2], pa[2], pb[2], p3[2], u)};
            } else {
                // 直线档，以及「两帧同位」的暂停段（lerp(a,a,u) 恒等于 a，一点不漂）
                pos = new double[]{lerp(pa[0], pb[0], u), lerp(pa[1], pb[1], u), lerp(pa[2], pb[2], u)};
            }
        } else if (pa != null) {
            pos = copy(pa, 3);
        } else if (pb != null) {
            pos = copy(pb, 3);
        }

        double[] rot = null;
        if (ra != null && rb != null) {
            // yaw 最短角：350° → 10° 取 +20°，否则相机会绕一大圈
            rot = new double[]{lerpAngle(ra[0], rb[0], u), lerp(ra[1], rb[1], u)};
        } else if (ra != null) {
            rot = copy(ra, 2);
        } else if (rb != null) {
            rot = copy(rb, 2);
        }

        Double fov = null;
        if (fa != null && fb != null) {
            double f1 = fa.doubleValue();
            double f2 = fb.doubleValue();
            fov = Double.valueOf(rule.smooth
                    ? catmullRom(neighborFov(kfs, ia - 1, f1), f1, f2, neighborFov(kfs, ib + 1, f2), u)
                    : lerp(f1, f2, u));
        } else if (fa != null) {
            fov = fa;
        } else if (fb != null) {
            fov = fb;
        }
        return new Pose(pos, rot, fov);
    }

    /** pos 三级兜底：往前最近有值 → 往后第一个有值 → 规则自身 position。 */
    private static double[] resolvePos(CameraRule rule, List<CameraRule.Keyframe> kfs, int idx) {
        for (int i = idx; i >= 0; i--) {
            double[] p = kfs.get(i).pos;
            if (p != null && p.length >= 3) {
                return p;
            }
        }
        for (int i = idx + 1; i < kfs.size(); i++) {
            double[] p = kfs.get(i).pos;
            if (p != null && p.length >= 3) {
                return p;
            }
        }
        return rule.position != null && rule.position.length >= 3 ? rule.position : null;
    }

    /** rot 三级兜底（同上）。 */
    private static double[] resolveRot(CameraRule rule, List<CameraRule.Keyframe> kfs, int idx) {
        for (int i = idx; i >= 0; i--) {
            double[] r = kfs.get(i).rot;
            if (r != null && r.length >= 2) {
                return r;
            }
        }
        for (int i = idx + 1; i < kfs.size(); i++) {
            double[] r = kfs.get(i).rot;
            if (r != null && r.length >= 2) {
                return r;
            }
        }
        return rule.rotation != null && rule.rotation.length >= 2 ? rule.rotation : null;
    }

    /** 速度归一：非正数/NaN/无穷一律当 1.0（配置写错不能把时间轴卡死或瞬间跑完）。 */
    private static double speedOf(CameraRule rule) {
        double sp = rule.speed;
        if (Double.isNaN(sp) || Double.isInfinite(sp) || sp <= 0.0) {
            return 1.0;
        }
        return sp;
    }

    /** 两帧位置是否相同（暂停段识别；长度都按 3 判，前面已经保证过）。 */
    private static boolean samePos(double[] a, double[] b) {
        return a != null && b != null && a.length >= 3 && b.length >= 3
                && a[0] == b[0] && a[1] == b[1] && a[2] == b[2];
    }

    /** 相邻帧的 pos（越界时夹到端点自身，所以端点切线不会把曲线甩出去）。 */
    private static double[] neighborPos(CameraRule rule, List<CameraRule.Keyframe> kfs, int idx,
                                        double[] self) {
        if (idx < 0 || idx >= kfs.size()) {
            return self;
        }
        double[] v = resolvePos(rule, kfs, idx);
        return v != null ? v : self;
    }

    /** 相邻帧的 fov（越界或缺失时夹到端点自身）。 */
    private static double neighborFov(List<CameraRule.Keyframe> kfs, int idx, double self) {
        if (idx < 0 || idx >= kfs.size()) {
            return self;
        }
        Double v = kfs.get(idx).fov;
        return v != null ? v.doubleValue() : self;
    }

    /**
     * Catmull-Rom 均匀参数化：曲线过 p1、p2，p0/p3 只决定两端切线。
     * u=0 → p1，u=1 → p2。端点被重复夹住（p0=p1 或 p3=p2）时三项退化，结果恰好是直线。
     */
    private static double catmullRom(double p0, double p1, double p2, double p3, double u) {
        double u2 = u * u;
        double u3 = u2 * u;
        return 0.5 * (2.0 * p1
                + (-p0 + p2) * u
                + (2.0 * p0 - 5.0 * p1 + 4.0 * p2 - p3) * u2
                + (-p0 + 3.0 * p1 - 3.0 * p2 + p3) * u3);
    }

    private static double lerp(double a, double b, double u) {
        return a + (b - a) * u;
    }

    /** 角度线性插值走最短弧（结果可能超出 ±180°，由写入方自行归一）。 */
    private static double lerpAngle(double a, double b, double u) {
        double d = ((b - a) % 360.0 + 540.0) % 360.0 - 180.0;
        return a + d * u;
    }

    private static double[] copy(double[] src, int len) {
        if (src == null || src.length < len) {
            return null;
        }
        double[] out = new double[len];
        System.arraycopy(src, 0, out, 0, len);
        return out;
    }
}
