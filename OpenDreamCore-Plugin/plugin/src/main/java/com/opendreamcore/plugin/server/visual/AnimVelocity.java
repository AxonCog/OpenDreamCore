package com.opendreamcore.plugin.server.visual;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动画位移（原生内置，动作包 yml 一行配置，不依赖外部技能插件）：
 *
 *   右闪避:
 *     name: 右闪避
 *     velocity: 1.0        # 位移格数（必填才有位移）
 *     velocity_time: 0.3   # 滑行时长秒（缺省 = 0.3）
 *     velocity_dir: right  # right/front/back/left（缺省 = right，随玩家朝向）
 *
 * 萌芽生态里这件事由外部 MM 技能做；这里等价内置：触发/播放动画的同时
 * 沿指定方向发每 tick 匀速速度脉冲，时长与滑行时间对齐。
 */
public final class AnimVelocity {
    /** 动画/触发名 → {dist, secs, dirCode}；dirCode: 0=right 1=front 2=back 3=left */
    private static final Map<String, double[]> RULES = new ConcurrentHashMap<>();

    private AnimVelocity() {
    }

    public static void clear() {
        RULES.clear();
    }

    /** 从动作包 yml 顶层（条目名 → 条目 map）提取 velocity 三件套。 */
    public static void load(Map<String, Object> root) {
        if (root == null) {
            return;
        }
        for (Map.Entry<String, Object> e : root.entrySet()) {
            if (!(e.getValue() instanceof Map)) {
                continue;
            }
            Map<?, ?> def = (Map<?, ?>) e.getValue();
            Object v = def.get("velocity");
            if (v == null) {
                continue;
            }
            double dist;
            try {
                dist = Double.parseDouble(String.valueOf(v));
            } catch (NumberFormatException ex) {
                continue;
            }
            if (dist <= 0) {
                continue;
            }
            double secs = 0.3;
            Object t = def.get("velocity_time");
            if (t != null) {
                try {
                    secs = Math.max(0.05, Double.parseDouble(String.valueOf(t)));
                } catch (NumberFormatException ignored) {
                }
            }
            String dir = String.valueOf(def.get("velocity_dir")).trim().toLowerCase();
            int code;
            switch (dir) {
                case "front":
                case "前":
                    code = 1;
                    break;
                case "back":
                case "后":
                    code = 2;
                    break;
                case "left":
                case "左":
                    code = 3;
                    break;
                default:
                    code = 0; // right/右/缺省
            }
            RULES.put(String.valueOf(e.getKey()), new double[]{dist, secs, code});
        }
    }

    /** 命中规则返回 true 并开始脉冲；播放/触发动画后调用。 */
    public static boolean apply(Player player, String animOrTriggerName) {
        double[] r = RULES.get(animOrTriggerName);
        if (r == null || !player.isOnline()) {
            return false;
        }
        apply(player, r[0], r[1], (int) r[2]);
        return true;
    }

    /** 每 tick 匀速脉冲：格数/时长 → 格/tick，沿玩家当前朝向的方向向量。 */
    public static void apply(Player player, double dist, double secs, int dirCode) {
        Vector base = player.getLocation().getDirection().setY(0).normalize();
        Vector dir;
        switch (dirCode) {
            case 1:
                dir = base;
                break;
            case 2:
                dir = base.multiply(-1);
                break;
            case 3:
                dir = base.rotateAroundY(Math.PI / 2);
                break;
            default:
                dir = base.rotateAroundY(-Math.PI / 2);
        }
        Vector v = dir.multiply(dist / secs / 20.0);
        int ticks = Math.max(1, (int) Math.round(secs * 20));
        for (int i = 0; i < ticks; i++) {
            Bukkit.getScheduler().runTaskLater(
                    com.opendreamcore.plugin.OpenDreamCorePlugin.get(), () -> {
                        if (player.isOnline()) {
                            player.setVelocity(v);
                        }
                    }, i);
        }
    }

    public static Map<String, double[]> rules() {
        return new LinkedHashMap<>(RULES);
    }
}
