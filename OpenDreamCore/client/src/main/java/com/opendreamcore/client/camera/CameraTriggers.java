package com.opendreamcore.client.camera;

import com.opendreamcore.camera.CameraRule;
import com.opendreamcore.camera.CameraStore;
import com.opendreamcore.camera.CameraTrigger;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * 四路触发引擎（M1.6）：命令（/odc camera play）/ 按键（GLFW 物理轮询）/
 * 区域（箱体进入边沿）/ 事件（进世界、重生）。每客户端 tick 跑一遍（tickBindings）。
 *
 * <p>once 语义：trigger.once=true（缺省）时本会话只发起一次——无论哪路触发；
 * once=false 的区域触发在离开箱体后重新武装（进入边沿再次触发）。
 * 所有异常全部吞掉：触发器坏一条不该影响别的规则与游戏本体。
 */
public final class CameraTriggers {

    private static final Logger LOGGER = Logger.getLogger("OpenDreamCore");

    /** 规则 id → 上一 tick 是否在箱体内（区域进入边沿检测）。 */
    private static final Map<String, Boolean> insideArea = new HashMap<String, Boolean>();
    /** once 已发过集合（会话级）。 */
    private static final Set<String> onceFired = new HashSet<String>();
    /** 规则 id|组合 → 上一 tick 是否按下（按键边沿检测）。 */
    private static final Map<String, Boolean> keyPrev = new HashMap<String, Boolean>();
    /** 上一 tick 的 level 实例（进世界边沿检测）。 */
    private static Object lastLevel;
    /** 上一 tick 玩家是否死亡（重生边沿检测，全局算一次）。 */
    private static boolean wasDead;

    private CameraTriggers() {
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) {
            return;
        }
        // 本 tick 的边沿信号先全局算好，规则侧只读（多条规则挂同一事件时不会互相吃掉边沿）
        boolean levelChanged = mc.level != lastLevel;
        boolean deadNow = mc.player.isDeadOrDying();
        boolean respawnEdge = wasDead && !deadNow;
        wasDead = deadNow;

        long win = mc.getWindow() != null ? mc.getWindow().getWindow() : 0L;
        try {
            for (CameraRule rule : CameraStore.get().rules().values()) {
                if (rule.trigger == null || !rule.trigger.present()) {
                    continue;
                }
                if (rule.trigger.once && onceFired.contains(rule.id)) {
                    continue;
                }
                checkKey(rule, win);
                checkArea(mc, rule);
                checkEvent(rule, levelChanged, respawnEdge);
            }
        } catch (Throwable t) {
            LOGGER.info("[运镜触发] tick 异常（已吞）：" + t);
        }
        if (levelChanged) {
            // 换世界：区域臂态与按键态都按世界坐标，跨世界没有意义，清掉重算
            insideArea.clear();
            keyPrev.clear();
            lastLevel = mc.level;
        }
    }

    private static void checkKey(CameraRule rule, long win) {
        List<String> keys = rule.trigger.keys;
        if (keys.isEmpty() || win == 0L) {
            return;
        }
        for (String combo : keys) {
            String stateKey = rule.id + "|" + combo;
            boolean down = comboDown(combo, win);
            Boolean prev = keyPrev.get(stateKey);
            boolean wasDown = prev != null && prev;
            keyPrev.put(stateKey, down);
            if (down && !wasDown) {
                fire(rule, "按键 " + combo);
            }
        }
    }

    /** "ctrl+shift+r" → 修饰键 + 主键；主键先支持字母/数字（够用，F 键后续按需扩）。 */
    private static boolean comboDown(String combo, long win) {
        boolean ctrl = false;
        boolean shift = false;
        boolean alt = false;
        String main = combo;
        String[] parts = combo.split("\\+");
        // 最后一段是主键，其余是修饰键
        for (int i = 0; i < parts.length - 1; i++) {
            String p = parts[i].trim();
            if ("ctrl".equals(p) || "control".equals(p)) {
                ctrl = true;
            } else if ("shift".equals(p)) {
                shift = true;
            } else if ("alt".equals(p)) {
                alt = true;
            }
        }
        main = parts[parts.length - 1].trim();
        if (main.length() != 1) {
            return false;
        }
        char c = main.charAt(0);
        int code;
        if (c >= 'a' && c <= 'z') {
            code = GLFW.GLFW_KEY_A + (c - 'a');
        } else if (c >= '0' && c <= '9') {
            code = GLFW.GLFW_KEY_0 + (c - '0');
        } else {
            return false;
        }
        if (GLFW.glfwGetKey(win, code) != 1) {
            return false;
        }
        if (ctrl && GLFW.glfwGetKey(win, GLFW.GLFW_KEY_LEFT_CONTROL) != 1
                && GLFW.glfwGetKey(win, GLFW.GLFW_KEY_RIGHT_CONTROL) != 1) {
            return false;
        }
        if (shift && GLFW.glfwGetKey(win, GLFW.GLFW_KEY_LEFT_SHIFT) != 1
                && GLFW.glfwGetKey(win, GLFW.GLFW_KEY_RIGHT_SHIFT) != 1) {
            return false;
        }
        if (alt && GLFW.glfwGetKey(win, GLFW.GLFW_KEY_LEFT_ALT) != 1
                && GLFW.glfwGetKey(win, GLFW.GLFW_KEY_RIGHT_ALT) != 1) {
            return false;
        }
        return true;
    }

    private static void checkArea(Minecraft mc, CameraRule rule) {
        double[] p1 = rule.trigger.pos1;
        if (p1 == null) {
            return;
        }
        double[] px = {mc.player.getX(), mc.player.getY(), mc.player.getZ()};
        boolean inside = CameraTrigger.contains(px, p1, rule.trigger.pos2);
        Boolean prev = insideArea.get(rule.id);
        boolean wasIn = prev != null && prev.booleanValue();
        insideArea.put(rule.id, Boolean.valueOf(inside));
        if (inside && !wasIn) {
            fire(rule, "区域");
        }
    }

    private static void checkEvent(CameraRule rule, boolean levelChanged, boolean respawnEdge) {
        String want = rule.trigger.event;
        if (want == null) {
            return;
        }
        boolean join = "进世界".equals(want) || "join".equalsIgnoreCase(want);
        boolean respawn = "重生".equals(want) || "respawn".equalsIgnoreCase(want);
        if (join && levelChanged && lastLevel != null) {
            fire(rule, "事件:进世界");
        }
        if (respawn && respawnEdge) {
            fire(rule, "事件:重生");
        }
    }

    private static void fire(CameraRule rule, String why) {
        onceFired.add(rule.id);
        LOGGER.info("[运镜触发] " + why + " → " + rule.id);
        try {
            CameraPlayback.get().play(rule.id);
        } catch (Throwable t) {
            LOGGER.info("[运镜触发] 发起失败（已吞）：" + rule.id + " - " + t);
        }
    }
}
