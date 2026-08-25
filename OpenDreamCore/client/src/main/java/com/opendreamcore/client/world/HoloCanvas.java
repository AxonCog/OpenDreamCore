package com.opendreamcore.client.world;

import com.mojang.blaze3d.vertex.PoseStack;
import com.opendreamcore.client.WorldHologram;
import com.opendreamcore.ui.RenderNode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.gui.Font;

import java.util.List;
import java.util.Map;

/**
 * C3 第二波：世界画布画刷渲染（自 WorldHologram 移出）。
 * 依赖的 WH 共享助手（holo/holoNum/animOf/applyBillboardRotation/applyContentDepth/
 * drawSafe/quadV/quad4/rgba/num）已放宽为 public，经 WorldHologram.xxx 调用。
 */
public final class HoloCanvas {

    private HoloCanvas() {
    }

    public static void renderCanvas(PoseStack pose, MultiBufferSource buffers, RenderNode node,
                                    double fade, String scope, double[] drag, Map<String, Object> pageVars) {
        Map<?, ?> spec = com.opendreamcore.client.UiRenderer.propsMap(node, "canvas");
        Object brushesRaw = spec.get("brushes");
        if (!(brushesRaw instanceof List<?> brushes)) {
            return;
        }
        Map<?, ?> holo = WorldHologram.holo(node);
        double x = WorldHologram.holoNum(holo, "x", 0, pageVars);
        double y = WorldHologram.holoNum(holo, "y", 0, pageVars);
        double z = WorldHologram.holoNum(holo, "z", 0, pageVars);
        double worldW = WorldHologram.holoNum(holo, "width", 1, pageVars);
        double worldH = WorldHologram.holoNum(holo, "height", 1, pageVars);
        double panelW = WorldHologram.num(spec.get("width"), 100);
        double panelH = WorldHologram.num(spec.get("height"), 100);
        if (panelW <= 0 || panelH <= 0 || worldW <= 0 || worldH <= 0) {
            return;
        }
        double sx = worldW / panelW;
        double sy = worldH / panelH;
        double[] anim = WorldHologram.animOf(node, scope);
        Minecraft mc = Minecraft.getInstance();
        pose.pushPose();
        pose.translate(x + (anim == null ? 0 : anim[0]) + (drag == null ? 0 : drag[0]),
                y + (anim == null ? 0 : anim[1]) + (drag == null ? 0 : drag[1]),
                z + (drag == null ? 0 : drag[2]));
        WorldHologram.applyBillboardRotation(pose, holo, anim, pageVars);
        var matrix = pose.last().pose();
        float alphaMul = (float) fade * (float) (anim == null ? 1 : anim[3]);
        com.opendreamcore.client.CompatRender.enableBlend();
        com.opendreamcore.client.CompatRender.defaultBlendFunc();
        WorldHologram.applyContentDepth(); // 内容深度:occluded=深度测试(不穿墙) / always=穿透
        com.opendreamcore.client.CompatRender.setColorShader();
        var builder = com.opendreamcore.client.CompatRender.begin(
                com.mojang.blaze3d.vertex.VertexFormat.Mode.TRIANGLES,
                com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR);
        for (Object brushObj : brushes) {
            if (!(brushObj instanceof Map<?, ?> m)) {
                continue;
            }
            String type = com.opendreamcore.client.UiRenderer.str(m.get("type"));
            if (type == null) {
                continue;
            }
            int color = com.opendreamcore.client.UiStyle.color(m.get("color"), 0xFFFFFFFF);
            switch (type) {
                case "rect" -> canvasRect(builder, matrix, node, m, sx, sy, worldW, worldH, color, alphaMul);
                case "circle" -> canvasCircle(builder, matrix, node, m, sx, sy, worldW, worldH, color, alphaMul);
                case "line" -> canvasLine(builder, matrix, node, m, sx, sy, worldW, worldH, color, alphaMul);
                case "triangle" -> canvasTriangle(builder, matrix, node, m, sx, sy, worldW, worldH, color, alphaMul);
                case "gradient" -> canvasGradient(builder, matrix, node, m, sx, sy, worldW, worldH, alphaMul);
                default -> {
                    // 未知笔刷忽略（text/image 用下面单独路径）
                }
            }
        }
        WorldHologram.drawSafe(builder);
        // 图片/文本笔刷（贴图/字体需要独立绘制）
        for (Object brushObj : brushes) {
            if (!(brushObj instanceof Map<?, ?> m)) {
                continue;
            }
            String type = com.opendreamcore.client.UiRenderer.str(m.get("type"));
            if (type == null) {
                continue;
            }
            switch (type) {
                case "image" -> canvasImage(pose, buffers, node, m, sx, sy, worldW, worldH, alphaMul);
                case "text" -> canvasText(pose, buffers, node, m, sx, sy, worldW, worldH, alphaMul);
                default -> {
                }
            }
        }
        WorldHologram.applyContentDepth();
        com.opendreamcore.client.CompatRender.disableBlend();
        pose.popPose();
    }

    /** 面板像素坐标 → billboard 局部坐标（中心原点，y 向上）。 */
    private static double[] canvasPos(double px, double py, double sx, double sy,
                                      double worldW, double worldH) {
        return new double[]{px * sx - worldW / 2, worldH / 2 - py * sy};
    }

    private static void canvasRect(com.opendreamcore.client.CompatBuffer builder, org.joml.Matrix4f matrix,
                                   RenderNode node, Map<?, ?> m, double sx, double sy,
                                   double worldW, double worldH, int color, float alphaMul) {
        double bx = WorldHologram.num(m.get("x"), 0);
        double by = WorldHologram.num(m.get("y"), 0);
        double bw = WorldHologram.num(m.get("width"), 10);
        double bh = WorldHologram.num(m.get("height"), 10);
        double[] p = canvasPos(bx, by, sx, sy, worldW, worldH);
        double[] p2 = canvasPos(bx + bw, by + bh, sx, sy, worldW, worldH);
        WorldHologram.quadV(builder, matrix, p[0], p2[1], p2[0], p[1], color, alphaMul);
    }

    private static void canvasCircle(com.opendreamcore.client.CompatBuffer builder, org.joml.Matrix4f matrix,
                                     RenderNode node, Map<?, ?> m, double sx, double sy,
                                     double worldW, double worldH, int color, float alphaMul) {
        double cx = WorldHologram.num(m.get("cx"), 0);
        double cy = WorldHologram.num(m.get("cy"), 0);
        double r = WorldHologram.num(m.get("radius"), 10);
        boolean fill = com.opendreamcore.client.UiRenderer.bool(m.get("fill"), true);
        double[] c = canvasPos(cx, cy, sx, sy, worldW, worldH);
        double rw = r * sx;
        double rh = r * sy;
        int segments = 16;
        if (fill) {
            for (int i = 0; i < segments; i++) {
                double a0 = Math.PI * 2 * i / segments;
                double a1 = Math.PI * 2 * (i + 1) / segments;
                canvasVertex(builder, matrix, c[0], c[1], color, alphaMul);
                canvasVertex(builder, matrix, c[0] + rw * Math.cos(a0), c[1] + rh * Math.sin(a0), color, alphaMul);
                canvasVertex(builder, matrix, c[0] + rw * Math.cos(a1), c[1] + rh * Math.sin(a1), color, alphaMul);
            }
        } else {
            // 描边圆：逐段细四边形
            for (int i = 0; i < segments; i++) {
                double a0 = Math.PI * 2 * i / segments;
                double a1 = Math.PI * 2 * (i + 1) / segments;
                double t = Math.min(0.02, Math.min(rw, rh) / 8);
                double x0 = c[0] + rw * Math.cos(a0), y0 = c[1] + rh * Math.sin(a0);
                double x1 = c[0] + rw * Math.cos(a1), y1 = c[1] + rh * Math.sin(a1);
                double x0i = c[0] + (rw - t) * Math.cos(a0), y0i = c[1] + (rh - t) * Math.sin(a0);
                double x1i = c[0] + (rw - t) * Math.cos(a1), y1i = c[1] + (rh - t) * Math.sin(a1);
                WorldHologram.quad4(builder, matrix, x0, y0, x1, y1, x1i, y1i, x0i, y0i, color, alphaMul);
            }
        }
    }

    private static void canvasLine(com.opendreamcore.client.CompatBuffer builder, org.joml.Matrix4f matrix,
                                   RenderNode node, Map<?, ?> m, double sx, double sy,
                                   double worldW, double worldH, int color, float alphaMul) {
        double[] a = canvasPos(WorldHologram.num(m.get("x1"), 0), WorldHologram.num(m.get("y1"), 0), sx, sy, worldW, worldH);
        double[] b = canvasPos(WorldHologram.num(m.get("x2"), 10), WorldHologram.num(m.get("y2"), 10), sx, sy, worldW, worldH);
        double thickness = Math.max(0.01, WorldHologram.num(m.get("width"), 1) * (sx + sy) / 2);
        double dx = b[0] - a[0];
        double dy = b[1] - a[1];
        double len = Math.max(1e-6, Math.sqrt(dx * dx + dy * dy));
        double nx = -dy / len * thickness / 2;
        double ny = dx / len * thickness / 2;
        WorldHologram.quad4(builder, matrix,
                a[0] + nx, a[1] + ny, b[0] + nx, b[1] + ny,
                b[0] - nx, b[1] - ny, a[0] - nx, a[1] - ny,
                color, alphaMul);
    }

    private static void canvasTriangle(com.opendreamcore.client.CompatBuffer builder, org.joml.Matrix4f matrix,
                                       RenderNode node, Map<?, ?> m, double sx, double sy,
                                       double worldW, double worldH, int color, float alphaMul) {
        double[] a = canvasPos(WorldHologram.num(m.get("x1"), 0), WorldHologram.num(m.get("y1"), 0), sx, sy, worldW, worldH);
        double[] b = canvasPos(WorldHologram.num(m.get("x2"), 10), WorldHologram.num(m.get("y2"), 0), sx, sy, worldW, worldH);
        double[] c = canvasPos(WorldHologram.num(m.get("x3"), 0), WorldHologram.num(m.get("y3"), 10), sx, sy, worldW, worldH);
        canvasVertex(builder, matrix, a[0], a[1], color, alphaMul);
        canvasVertex(builder, matrix, b[0], b[1], color, alphaMul);
        canvasVertex(builder, matrix, c[0], c[1], color, alphaMul);
    }

    /** 垂直/水平渐变：8 段细条近似。 */
    private static void canvasGradient(com.opendreamcore.client.CompatBuffer builder, org.joml.Matrix4f matrix,
                                       RenderNode node, Map<?, ?> m, double sx, double sy,
                                       double worldW, double worldH, float alphaMul) {
        double bx = WorldHologram.num(m.get("x"), 0);
        double by = WorldHologram.num(m.get("y"), 0);
        double bw = WorldHologram.num(m.get("width"), 10);
        double bh = WorldHologram.num(m.get("height"), 10);
        int from = com.opendreamcore.client.UiStyle.color(m.get("from"), 0xFFFFFFFF);
        int to = com.opendreamcore.client.UiStyle.color(m.get("to"), 0xFF000000);
        boolean vertical = com.opendreamcore.client.UiRenderer.bool(m.get("vertical"), true);
        int steps = 8;
        double[] p0 = canvasPos(bx, by, sx, sy, worldW, worldH);
        double[] p1 = canvasPos(bx + bw, by + bh, sx, sy, worldW, worldH);
        for (int i = 0; i < steps; i++) {
            double t0 = i / (double) steps;
            double t1 = (i + 1) / (double) steps;
            int c0 = com.opendreamcore.client.UiRenderer.lerpColor(from, to, t0);
            int c1 = com.opendreamcore.client.UiRenderer.lerpColor(from, to, t1);
            double x0, y0, x1, y1, x2, y2, x3, y3;
            if (vertical) {
                double yA = p0[1] + (p1[1] - p0[1]) * t0;
                double yB = p0[1] + (p1[1] - p0[1]) * t1;
                x0 = p0[0]; y0 = yA; x1 = p1[0]; y1 = yA; x2 = p1[0]; y2 = yB; x3 = p0[0]; y3 = yB;
            } else {
                double xA = p0[0] + (p1[0] - p0[0]) * t0;
                double xB = p0[0] + (p1[0] - p0[0]) * t1;
                x0 = xA; y0 = p1[1]; x1 = xB; y1 = p1[1]; x2 = xB; y2 = p0[1]; x3 = xA; y3 = p0[1];
            }
            canvasVertex(builder, matrix, x0, y0, c0, alphaMul);
            canvasVertex(builder, matrix, x1, y1, c0, alphaMul);
            canvasVertex(builder, matrix, x2, y2, c1, alphaMul);
            canvasVertex(builder, matrix, x0, y0, c0, alphaMul);
            canvasVertex(builder, matrix, x2, y2, c1, alphaMul);
            canvasVertex(builder, matrix, x3, y3, c1, alphaMul);
        }
    }

    /** 世界画布图片笔刷：贴图 quad。 */
    private static void canvasImage(PoseStack pose, MultiBufferSource buffers, RenderNode node, Map<?, ?> m,
                                    double sx, double sy, double worldW, double worldH, float alphaMul) {
        String src = com.opendreamcore.client.UiRenderer.str(m.get("src"));
        net.minecraft.resources.ResourceLocation texture = com.opendreamcore.client.UiStyle.texture(src);
        if (texture == null) {
            return;
        }
        double bx = WorldHologram.num(m.get("x"), 0);
        double by = WorldHologram.num(m.get("y"), 0);
        double bw = WorldHologram.num(m.get("width"), 10);
        double bh = WorldHologram.num(m.get("height"), 10);
        double[] p = canvasPos(bx, by, sx, sy, worldW, worldH);
        double[] p2 = canvasPos(bx + bw, by + bh, sx, sy, worldW, worldH);
        var matrix = pose.last().pose();
        com.opendreamcore.client.CompatRender.setShaderTexture(0, texture);
        com.opendreamcore.client.CompatRender.setTextureShader();
        com.opendreamcore.client.CompatRender.shaderColor(1.0F, 1.0F, 1.0F, alphaMul);
        var builder = com.opendreamcore.client.CompatRender.begin(
                com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
                com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX);
        builder.addVertex(matrix, (float) p[0], (float) p2[1], 0).setUv(0, 0);
        builder.addVertex(matrix, (float) p2[0], (float) p2[1], 0).setUv(1, 0);
        builder.addVertex(matrix, (float) p2[0], (float) p[1], 0).setUv(1, 1);
        builder.addVertex(matrix, (float) p[0], (float) p[1], 0).setUv(0, 1);
        WorldHologram.drawSafe(builder);
        com.opendreamcore.client.CompatRender.shaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** 世界画布文本笔刷：billboard 字体绘制。 */
    private static void canvasText(PoseStack pose, MultiBufferSource buffers, RenderNode node, Map<?, ?> m,
                                   double sx, double sy, double worldW, double worldH, float alphaMul) {
        String content = com.opendreamcore.client.UiRenderer.interpolate(
                node, com.opendreamcore.client.UiRenderer.str(m.get("content")), null);
        if (content == null || content.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        double bx = WorldHologram.num(m.get("x"), 0);
        double by = WorldHologram.num(m.get("y"), 0);
        double[] p = canvasPos(bx, by, sx, sy, worldW, worldH);
        int color = com.opendreamcore.client.UiStyle.color(m.get("color"), 0xFFFFFFFF);
        int alpha = (int) (((color >>> 24) & 0xFF) * alphaMul);
        color = (alpha << 24) | (color & 0xFFFFFF);
        pose.pushPose();
        pose.translate(p[0], p[1], 0);
        pose.scale((float) sx, (float) -sx, (float) sx);
        mc.font.drawInBatch(content, -mc.font.width(content) / 2.0F, 0, color,
                com.opendreamcore.client.UiRenderer.bool(m.get("shadow"), false),
                pose.last().pose(), buffers, net.minecraft.client.gui.Font.DisplayMode.NORMAL,
                0, 0xF000F0);
        pose.popPose();
    }

    /** 画布顶点（POSITION_COLOR）。 */
    private static void canvasVertex(com.opendreamcore.client.CompatBuffer builder, org.joml.Matrix4f matrix,
                                     double x, double y, int color, float alphaMul) {
        float[] c = WorldHologram.rgba(color, alphaMul);
        builder.addVertex(matrix, (float) x, (float) y, 0).setColor(c[0], c[1], c[2], c[3]);
    }
}
