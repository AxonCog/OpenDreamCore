package com.opendreamcore.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.Map;

/**
 * 世界全息渲染工具方法（WorldHologram 拆分出的共享工具）。
 * 包内可见，供 WorldHologram / WorldHoloEdit / WorldHoloElements 共用。
 */
final class WorldHoloUtils {

    private WorldHoloUtils() {
    }

    /** 数值解析（Object → double，支持 Number / String）。 */
    static double num(Object v, double fallback) {
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        if (v != null) {
            try {
                return Double.parseDouble(String.valueOf(v));
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    /** 矩形边（四顶点 quad，z=0，单色）。 */
    static void edge(VertexConsumer builder, Matrix4f matrix,
                     float x0, float y0, float x1, float y1,
                     float r, float g, float b, float a) {
        builder.addVertex(matrix, x0, y0, 0).setColor(r, g, b, a);
        builder.addVertex(matrix, x1, y0, 0).setColor(r, g, b, a);
        builder.addVertex(matrix, x1, y1, 0).setColor(r, g, b, a);
        builder.addVertex(matrix, x0, y1, 0).setColor(r, g, b, a);
    }

    /** 安全构建并绘制：空 buffer 时跳过绘制（防条件跳过全部顶点时 buildOrThrow 崩溃）。 */
    static void drawSafe(BufferBuilder builder) {
        try {
            var mesh = builder.buildOrThrow();
            com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(mesh);
        } catch (Exception ignored) {
            // 空 buffer 或其他渲染异常，安全跳过
        }
    }

    /** 页面锚点（options.world.offsetX/Y/Z，默认玩家前方 3 格、eye +0.7）。 */
    static Vec3 anchor(Minecraft mc, Map<String, Object> options) {
        Object worldOpt = options == null ? null : options.get("world");
        double ox = 0, oy = 0.7, oz = -3;
        if (worldOpt instanceof Map<?, ?> w) {
            ox = num(w.get("offsetX"), ox);
            oy = num(w.get("offsetY"), oy);
            oz = num(w.get("offsetZ"), oz);
        }
        Vec3 look = mc.player.getLookAngle();
        Vec3 right = new Vec3(-look.z, 0, look.x).normalize();
        Vec3 forward = new Vec3(look.x, 0, look.z).normalize();
        Vec3 base = mc.player.getEyePosition().add(
                right.x * ox + forward.x * oz,
                oy,
                right.z * ox + forward.z * oz);
        return base;
    }

    /** anchor 数值提取（options.world 子键）。 */
    static double anchorNum(Map<?, ?> world, String key, double fallback) {
        if (world == null) {
            return fallback;
        }
        Object v = world.get(key);
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        if (v instanceof String s) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    /** 带 alpha 乘算的颜色合成。 */
    static int withAlpha(int color, float alphaMul) {
        int a = (int) (((color >>> 24) & 0xFF) * alphaMul);
        return (a << 24) | (color & 0xFFFFFF);
    }

    /** 线性颜色插值。 */
    static int lerpColor(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF, aa = (a >>> 24) & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF, ba = (b >>> 24) & 0xFF;
        int r = (int) (ar + (br - ar) * t);
        int g = (int) (ag + (bg - ag) * t);
        int bl = (int) (ab + (bb - ab) * t);
        int al = (int) (aa + (ba - aa) * t);
        return (al << 24) | (r << 16) | (g << 8) | bl;
    }
}
