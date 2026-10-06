package com.opendreamcore.client.camera;

import com.opendreamcore.client.WorldPicking;
import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * M1.8 L2：路径点鼠标拖拽 + Ctrl+Z/Y 撤销重做（编辑会话激活时生效）。
 *
 * <p>由 HeadTagRenderer.renderFrame 每帧轮询（世界渲染钩子已存在，零新增注入点）。
 * 拾取/拖拽数学与 WorldEditor 的 billiboard 平面方案一致：
 * 拖拽面 = 过拖拽基准点、法线朝向相机的竖直面，鼠标射线与它求交即得新位置；
 * 按住 Shift 只沿主导轴移动。曲线/标记由 CameraPathHolo 直接读实时点位。
 */
public final class CameraPathDrag {
    private static int dragIndex = -1;
    private static double[] dragBase;
    private static boolean prevDown;
    private static boolean prevZ;
    private static boolean prevY;

    private CameraPathDrag() {
    }

    /** 正在拖拽的点下标（0 基），未拖拽为 -1；全息层用来高亮与画实时折线。 */
    public static int dragIndex() {
        return dragIndex;
    }

    /** 每帧轮询；只在世界内且编辑会话激活时工作。 */
    public static void poll(Minecraft mc, net.minecraft.client.Camera camera) {
        CameraEditSession s = CameraEditSession.get();
        try {
            if (!s.active()) {
                dragIndex = -1;
                prevDown = mc != null && mc.mouseHandler.isLeftPressed();
                return;
            }
            if (mc == null || mc.level == null || mc.player == null) {
                return;
            }
            handleKeys(mc, s);
            boolean down = mc.mouseHandler.isLeftPressed();
            if (dragIndex >= 0) {
                if (down) {
                    updateDrag(camera, mc, s);
                } else {
                    dragIndex = -1;
                    rearmPreview(s);
                }
                prevDown = down;
                return;
            }
            if (down && !prevDown && mc.screen == null) {
                int hit = pick(camera, mc, s);
                if (hit >= 0) {
                    s.pushUndo();
                    dragIndex = hit;
                    dragBase = s.pointAt(hit);
                }
            }
            prevDown = down;
        } catch (Throwable ignored) {
            // 轮询不能打断渲染帧
            prevDown = mc != null && mc.mouseHandler.isLeftPressed();
        }
    }

    /** Ctrl+Z 撤销 / Ctrl+Y 重做（聊天框等界面打开时不抢键）。 */
    private static void handleKeys(Minecraft mc, CameraEditSession s) {
        long win = mc.getWindow().getWindow();
        boolean ctrl = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(win, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
        boolean z = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_Z) == GLFW.GLFW_PRESS;
        boolean y = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_Y) == GLFW.GLFW_PRESS;
        if (mc.screen == null && ctrl && z && !prevZ) {
            say(mc, s.undo() ? "§7已撤销（还剩 " + s.undoDepth() + " 步）" : "§7没有可撤销的操作");
        }
        if (mc.screen == null && ctrl && y && !prevY) {
            say(mc, s.redo() ? "§7已重做" : "§7没有可重做的操作");
        }
        prevZ = z;
        prevY = y;
    }

    /** 拾取：鼠标射线对每个点做球命中（半径 0.6 格），取最近。 */
    private static int pick(net.minecraft.client.Camera camera, Minecraft mc, CameraEditSession s) {
        double[] ray = WorldPicking.mouseRayWorld(mc, camera);
        double ox = ray[0], oy = ray[1], oz = ray[2];
        double dx = ray[3], dy = ray[4], dz = ray[5];
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-9) {
            return -1;
        }
        dx /= len;
        dy /= len;
        dz /= len;
        int best = -1;
        double bestT = Double.MAX_VALUE;
        for (int i = 0; i < s.pointsCount(); i++) {
            double[] p = s.pointAt(i);
            double vx = p[0] - ox, vy = p[1] - oy, vz = p[2] - oz;
            double t = vx * dx + vy * dy + vz * dz;
            if (t < 0) {
                continue;
            }
            double px = vx - dx * t, py = vy - dy * t, pz = vz - dz * t;
            if (px * px + py * py + pz * pz < 0.36 && t < bestT) {
                bestT = t;
                best = i;
            }
        }
        return best;
    }

    /** 拖拽：相机朝向竖直面求交（billboard 平面），Shift 锁主导轴。 */
    private static void updateDrag(net.minecraft.client.Camera camera, Minecraft mc, CameraEditSession s) {
        double[] ray = WorldPicking.mouseRayWorld(mc, camera);
        if (dragBase == null) {
            return;
        }
        org.joml.Matrix4f rot = new org.joml.Matrix4f().rotation(camera.rotation());
        org.joml.Vector3f n = rot.transformDirection(new org.joml.Vector3f(0f, 0f, -1f));
        double denom = ray[3] * n.x + ray[4] * n.y + ray[5] * n.z;
        if (Math.abs(denom) < 1e-9) {
            return;
        }
        double t = ((dragBase[0] - ray[0]) * n.x + (dragBase[1] - ray[1]) * n.y
                + (dragBase[2] - ray[2]) * n.z) / denom;
        if (t < 0) {
            return;
        }
        double hx = ray[0] + ray[3] * t;
        double hy = ray[1] + ray[4] * t;
        double hz = ray[2] + ray[5] * t;
        long win = mc.getWindow().getWindow();
        boolean shift = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(win, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
        double dx = hx - dragBase[0], dy = hy - dragBase[1], dz = hz - dragBase[2];
        if (shift) {
            if (Math.abs(dx) >= Math.abs(dy) && Math.abs(dx) >= Math.abs(dz)) {
                dy = 0;
                dz = 0;
            } else if (Math.abs(dy) >= Math.abs(dz)) {
                dx = 0;
                dz = 0;
            } else {
                dx = 0;
                dy = 0;
            }
        }
        s.setPoint(dragIndex, dragBase[0] + dx, dragBase[1] + dy, dragBase[2] + dz);
    }

    /** 拖拽落子：预览开着就把编辑后的规则重注入（下次 play 用新点位）。 */
    private static void rearmPreview(CameraEditSession s) {
        s.rearmPreview();
    }

    private static void say(Minecraft mc, String text) {
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal(text), false);
        }
    }
}
