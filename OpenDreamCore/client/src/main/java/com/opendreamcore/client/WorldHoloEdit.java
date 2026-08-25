package com.opendreamcore.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.List;
import java.util.Map;

/**
 * 世界全息编辑器辅助渲染（从 WorldHologram 拆分）。
 * 所有方法在编辑模式下绘制 UI 辅助元素（手柄、ghost、网格、涟漪等），
 * 均禁用深度测试（始终可见，穿透方块）。
 */
final class WorldHoloEdit {

    private WorldHoloEdit() {
    }

    /** 编辑模式旋转手柄：中心 → 手柄连线 + 手柄圆环。 */
    public static void renderRotateHandle(Camera camera, Map<String, Object> options,
                                          double[] center, double[] handle) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || center == null || handle == null) {
            return;
        }
        var camPos = camera.getPosition();
        var rot = new Matrix4f().rotation(camera.rotation());
        var c = rot.transformDirection(new org.joml.Vector3f((float) (center[0] - camPos.x),
                (float) (center[1] - camPos.y), (float) (center[2] - camPos.z)));
        var h = rot.transformDirection(new org.joml.Vector3f((float) (handle[0] - camPos.x),
                (float) (handle[1] - camPos.y), (float) (handle[2] - camPos.z)));
        if (h.z > 0) {
            return;
        }
        PoseStack pose = new PoseStack();
        pose.mulPose(mc.gameRenderer.getMainCamera().rotation());
        var matrix = pose.last().pose();
        CompatRender.enableBlend();
        CompatRender.defaultBlendFunc();
        CompatRender.disableDepthTest();
        CompatRender.setColorShader();
        var builder = CompatRender.begin(VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_COLOR);
        float dx = h.x - c.x;
        float dy = h.y - c.y;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len > 0.001F) {
            float px = -dy / len * 0.008F;
            float py = dx / len * 0.008F;
            builder.addVertex(matrix, c.x + px, c.y + py, c.z).setColor(1, 1, 1, 0.55F);
            builder.addVertex(matrix, h.x + px, h.y + py, h.z).setColor(1, 1, 1, 0.55F);
            builder.addVertex(matrix, h.x - px, h.y - py, h.z).setColor(1, 1, 1, 0.55F);
            builder.addVertex(matrix, c.x - px, c.y - py, c.z).setColor(1, 1, 1, 0.55F);
        }
        int segments = 16;
        float r0 = 0.06F, r1 = 0.09F;
        for (int i = 0; i < segments; i++) {
            double a0 = Math.toRadians(360.0 * i / segments);
            double a1 = Math.toRadians(360.0 * (i + 1) / segments);
            float x0 = h.x + (float) Math.cos(a0) * r0;
            float y0 = h.y + (float) Math.sin(a0) * r0;
            float x1 = h.x + (float) Math.cos(a1) * r0;
            float y1 = h.y + (float) Math.sin(a1) * r0;
            float x2 = h.x + (float) Math.cos(a1) * r1;
            float y2 = h.y + (float) Math.sin(a1) * r1;
            float x3 = h.x + (float) Math.cos(a0) * r1;
            float y3 = h.y + (float) Math.sin(a0) * r1;
            builder.addVertex(matrix, x0, y0, h.z).setColor(0.42F, 0.65F, 0.96F, 0.9F);
            builder.addVertex(matrix, x1, y1, h.z).setColor(0.42F, 0.65F, 0.96F, 0.9F);
            builder.addVertex(matrix, x2, y2, h.z).setColor(0.42F, 0.65F, 0.96F, 0.9F);
            builder.addVertex(matrix, x3, y3, h.z).setColor(0.42F, 0.65F, 0.96F, 0.9F);
        }
        builder.buildAndDraw();
        CompatRender.enableDepthTest();
        CompatRender.disableBlend();
    }

    /** 编辑模式缩放手柄：右下角亮色方块。 */
    public static void renderResizeHandle(Camera camera, Map<String, Object> options,
                                          double[] center, double[] handle) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || handle == null) {
            return;
        }
        var camPos = camera.getPosition();
        var rot = new Matrix4f().rotation(camera.rotation());
        var h = rot.transformDirection(new org.joml.Vector3f((float) (handle[0] - camPos.x),
                (float) (handle[1] - camPos.y), (float) (handle[2] - camPos.z)));
        if (h.z > 0) {
            return;
        }
        PoseStack pose = new PoseStack();
        pose.mulPose(mc.gameRenderer.getMainCamera().rotation());
        var matrix = pose.last().pose();
        CompatRender.enableBlend();
        CompatRender.defaultBlendFunc();
        CompatRender.disableDepthTest();
        CompatRender.setColorShader();
        var builder = CompatRender.begin(VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_COLOR);
        float s = 0.05F;
        WorldHoloUtils.edge(builder, matrix, h.x - s, h.y - s, h.x + s, h.y + s, 1, 1, 1, 0.9F);
        WorldHoloUtils.edge(builder, matrix, h.x - s * 0.55F, h.y - s * 0.55F, h.x + s * 0.55F, h.y + s * 0.55F,
                0.42F, 0.65F, 0.96F, 0.95F);
        builder.buildAndDraw();
        CompatRender.enableDepthTest();
        CompatRender.disableBlend();
    }

    /** 编辑模式描边手柄：左边缘菱形（白色外框 + 元素描边色内芯）。 */
    public static void renderBorderHandle(Camera camera, Map<String, Object> options,
                                          double[] center, double[] handle, int borderColor) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || center == null || handle == null) {
            return;
        }
        var camPos = camera.getPosition();
        var rot = new Matrix4f().rotation(camera.rotation());
        var c = rot.transformDirection(new org.joml.Vector3f((float) (center[0] - camPos.x),
                (float) (center[1] - camPos.y), (float) (center[2] - camPos.z)));
        var h = rot.transformDirection(new org.joml.Vector3f((float) (handle[0] - camPos.x),
                (float) (handle[1] - camPos.y), (float) (handle[2] - camPos.z)));
        if (h.z > 0) {
            return;
        }
        PoseStack pose = new PoseStack();
        pose.mulPose(mc.gameRenderer.getMainCamera().rotation());
        var matrix = pose.last().pose();
        CompatRender.enableBlend();
        CompatRender.defaultBlendFunc();
        CompatRender.disableDepthTest();
        CompatRender.setColorShader();
        var builder = CompatRender.begin(VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_COLOR);
        float dx = h.x - c.x;
        float dy = h.y - c.y;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len > 0.001F) {
            float px = -dy / len * 0.008F;
            float py = dx / len * 0.008F;
            builder.addVertex(matrix, c.x + px, c.y + py, c.z).setColor(1, 1, 1, 0.55F);
            builder.addVertex(matrix, h.x + px, h.y + py, h.z).setColor(1, 1, 1, 0.55F);
            builder.addVertex(matrix, h.x - px, h.y - py, h.z).setColor(1, 1, 1, 0.55F);
            builder.addVertex(matrix, c.x - px, c.y - py, c.z).setColor(1, 1, 1, 0.55F);
        }
        float s = 0.065F;
        float ir = ((borderColor >> 16) & 0xFF) / 255.0F;
        float ig = ((borderColor >> 8) & 0xFF) / 255.0F;
        float ib = (borderColor & 0xFF) / 255.0F;
        builder.addVertex(matrix, h.x, h.y + s, h.z).setColor(1, 1, 1, 0.9F);
        builder.addVertex(matrix, h.x + s, h.y, h.z).setColor(1, 1, 1, 0.9F);
        builder.addVertex(matrix, h.x, h.y - s, h.z).setColor(1, 1, 1, 0.9F);
        builder.addVertex(matrix, h.x - s, h.y, h.z).setColor(1, 1, 1, 0.9F);
        float si = s * 0.55F;
        builder.addVertex(matrix, h.x, h.y + si, h.z).setColor(ir, ig, ib, 0.95F);
        builder.addVertex(matrix, h.x + si, h.y, h.z).setColor(ir, ig, ib, 0.95F);
        builder.addVertex(matrix, h.x, h.y - si, h.z).setColor(ir, ig, ib, 0.95F);
        builder.addVertex(matrix, h.x - si, h.y, h.z).setColor(ir, ig, ib, 0.95F);
        builder.buildAndDraw();
        CompatRender.enableDepthTest();
        CompatRender.disableBlend();
    }

    /** 拖拽幽灵影：元素拖拽前原位置的半透明框。 */
    public static void renderDragGhost(Camera camera, Map<String, Object> options,
                                       Vec3 base, double w, double h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || base == null) {
            return;
        }
        PoseStack pose = new PoseStack();
        pose.translate(base.x - camera.getPosition().x,
                base.y - camera.getPosition().y,
                base.z - camera.getPosition().z);
        pose.mulPose(mc.gameRenderer.getMainCamera().rotation());
        var matrix = pose.last().pose();
        CompatRender.enableBlend();
        CompatRender.defaultBlendFunc();
        CompatRender.disableDepthTest();
        CompatRender.setColorShader();
        var builder = CompatRender.begin(VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_COLOR);
        float hw = (float) (w / 2);
        float hh = (float) (h / 2);
        float t = 0.02F;
        float r = 0.88F, g = 0.88F, b = 0.88F, a = 0.35F;
        WorldHoloUtils.edge(builder, matrix, -hw, -hh, hw, -hh + t, r, g, b, a);
        WorldHoloUtils.edge(builder, matrix, -hw, hh - t, hw, hh, r, g, b, a);
        WorldHoloUtils.edge(builder, matrix, -hw, -hh, -hw + t, hh, r, g, b, a);
        WorldHoloUtils.edge(builder, matrix, hw - t, -hh, hw, hh, r, g, b, a);
        builder.buildAndDraw();
        CompatRender.enableDepthTest();
        CompatRender.disableBlend();
    }

    /** 吸附磁吸圈：锚点平面 radius 半径细圆环。 */
    public static void renderMagnetRing(Camera camera, Map<String, Object> options,
                                        Vec3 anchor, double cx, double cy, double radius) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || anchor == null || radius <= 0) {
            return;
        }
        PoseStack pose = new PoseStack();
        pose.translate(anchor.x - camera.getPosition().x,
                anchor.y - camera.getPosition().y,
                anchor.z - camera.getPosition().z);
        pose.mulPose(mc.gameRenderer.getMainCamera().rotation());
        var matrix = pose.last().pose();
        CompatRender.enableBlend();
        CompatRender.defaultBlendFunc();
        CompatRender.disableDepthTest();
        CompatRender.setColorShader();
        var builder = CompatRender.begin(VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_COLOR);
        float r = 1.0F, g = 0.7F, b = 0.0F, a = 0.30F;
        float t = 0.008F;
        int seg = 28;
        for (int i = 0; i < seg; i++) {
            double a0 = Math.toRadians(360.0 * i / seg);
            double a1 = Math.toRadians(360.0 * (i + 1) / seg);
            float x0 = (float) (cx + Math.cos(a0) * radius);
            float y0 = (float) (cy + Math.sin(a0) * radius);
            float x1 = (float) (cx + Math.cos(a1) * radius);
            float y1 = (float) (cy + Math.sin(a1) * radius);
            builder.addVertex(matrix, x0, y0, 0).setColor(r, g, b, a);
            builder.addVertex(matrix, x1, y1, 0).setColor(r, g, b, a);
            builder.addVertex(matrix, x1 + t, y1 + t, 0).setColor(r, g, b, a);
            builder.addVertex(matrix, x0 + t, y0 + t, 0).setColor(r, g, b, a);
        }
        builder.buildAndDraw();
        CompatRender.enableDepthTest();
        CompatRender.disableBlend();
    }

    /** 淡出范围可视化：锚点平面双圆环。 */
    public static void renderFadeRange(Camera camera, Map<String, Object> options,
                                       Vec3 anchor, boolean dim) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || anchor == null) {
            return;
        }
        Object worldOpt = options == null ? null : options.get("world");
        if (!(worldOpt instanceof Map<?, ?> w)) {
            return;
        }
        double fadeDistance = WorldHoloUtils.num(w.get("fadeDistance"), 0);
        if (fadeDistance <= 0) {
            return;
        }
        double range = WorldHoloUtils.num(w.get("fadeRange"), 3);
        PoseStack pose = new PoseStack();
        pose.translate(anchor.x - camera.getPosition().x,
                anchor.y - camera.getPosition().y,
                anchor.z - camera.getPosition().z);
        pose.mulPose(mc.gameRenderer.getMainCamera().rotation());
        var matrix = pose.last().pose();
        CompatRender.enableBlend();
        CompatRender.defaultBlendFunc();
        CompatRender.disableDepthTest();
        CompatRender.setColorShader();
        var builder = CompatRender.begin(VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_COLOR);
        if (dim) {
            drawRangeRing(builder, matrix, fadeDistance, 0.55F, 0.55F, 0.6F, 0.18F);
            drawRangeRing(builder, matrix, fadeDistance + range, 0.55F, 0.35F, 0.35F, 0.18F);
        } else {
            drawRangeRing(builder, matrix, fadeDistance, 1.0F, 0.7F, 0.0F, 0.35F);
            drawRangeRing(builder, matrix, fadeDistance + range, 0.9F, 0.2F, 0.2F, 0.35F);
        }
        builder.buildAndDraw();
        CompatRender.enableDepthTest();
        CompatRender.disableBlend();
    }

    /** 淡出圈环绘制。 */
    private static void drawRangeRing(CompatBuffer builder, Matrix4f matrix, double radius,
                                      float r, float g, float b, float a) {
        if (radius <= 0) {
            return;
        }
        float t = 0.012F;
        int seg = 48;
        for (int i = 0; i < seg; i++) {
            double a0 = Math.toRadians(360.0 * i / seg);
            double a1 = Math.toRadians(360.0 * (i + 1) / seg);
            float x0 = (float) (Math.cos(a0) * radius);
            float y0 = (float) (Math.sin(a0) * radius);
            float x1 = (float) (Math.cos(a1) * radius);
            float y1 = (float) (Math.sin(a1) * radius);
            builder.addVertex(matrix, x0, y0, 0).setColor(r, g, b, a);
            builder.addVertex(matrix, x1, y1, 0).setColor(r, g, b, a);
            builder.addVertex(matrix, x1 + t, y1 + t, 0).setColor(r, g, b, a);
            builder.addVertex(matrix, x0 + t, y0 + t, 0).setColor(r, g, b, a);
        }
    }

    /** 编辑模式锚点指示：聚焦面板锚点十字。 */
    public static void renderAnchorMarker(Camera camera, Map<String, Object> options,
                                          Vec3 anchor) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || anchor == null) {
            return;
        }
        PoseStack pose = new PoseStack();
        pose.translate(anchor.x - camera.getPosition().x,
                anchor.y - camera.getPosition().y,
                anchor.z - camera.getPosition().z);
        pose.mulPose(mc.gameRenderer.getMainCamera().rotation());
        var matrix = pose.last().pose();
        CompatRender.enableBlend();
        CompatRender.defaultBlendFunc();
        CompatRender.disableDepthTest();
        CompatRender.setColorShader();
        var builder = CompatRender.begin(VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_COLOR);
        float arm = 0.5F, t = 0.02F;
        WorldHoloUtils.edge(builder, matrix, -arm, -t, arm, t, 1.0F, 0.7F, 0.0F, 0.55F);
        WorldHoloUtils.edge(builder, matrix, -t, -arm, t, arm, 1.0F, 0.7F, 0.0F, 0.55F);
        WorldHoloUtils.edge(builder, matrix, -0.03F, -0.03F, 0.03F, 0.03F, 1.0F, 1.0F, 1.0F, 0.85F);
        builder.buildAndDraw();
        CompatRender.enableDepthTest();
        CompatRender.disableBlend();
    }

    /** 镜像翻转 ghost 预览。 */
    public static void renderMirrorPreview(Camera camera, Map<String, Object> options,
                                           Vec3 anchor, List<double[]> boxes,
                                           double center, boolean horizontal) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || anchor == null || boxes == null || boxes.isEmpty()) {
            return;
        }
        PoseStack pose = new PoseStack();
        pose.translate(anchor.x - camera.getPosition().x,
                anchor.y - camera.getPosition().y,
                anchor.z - camera.getPosition().z);
        pose.mulPose(mc.gameRenderer.getMainCamera().rotation());
        var matrix = pose.last().pose();
        CompatRender.enableBlend();
        CompatRender.defaultBlendFunc();
        CompatRender.disableDepthTest();
        CompatRender.setColorShader();
        var builder = CompatRender.begin(VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_COLOR);
        float r = horizontal ? 0.4F : 0.3F;
        float g = 0.7F;
        float b = horizontal ? 1.0F : 0.85F;
        float a = 0.30F;
        float t = 0.015F;
        for (double[] box : boxes) {
            double nx = horizontal ? (2 * center - box[0]) : box[0];
            double ny = horizontal ? box[1] : (2 * center - box[1]);
            float x0 = (float) (nx - box[2] / 2);
            float y0 = (float) (ny - box[3] / 2);
            float x1 = (float) (nx + box[2] / 2);
            float y1 = (float) (ny + box[3] / 2);
            WorldHoloUtils.edge(builder, matrix, x0, y0, x1, y0 + t, r, g, b, a);
            WorldHoloUtils.edge(builder, matrix, x0, y1 - t, x1, y1, r, g, b, a);
            WorldHoloUtils.edge(builder, matrix, x0, y0, x0 + t, y1, r, g, b, a);
            WorldHoloUtils.edge(builder, matrix, x1 - t, y0, x1, y1, r, g, b, a);
        }
        builder.buildAndDraw();
        CompatRender.enableDepthTest();
        CompatRender.disableBlend();
    }

    /** 分布 ghost 预览。 */
    public static void renderGhostBoxes(Camera camera, Map<String, Object> options,
                                        Vec3 anchor, List<double[]> boxes) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || anchor == null || boxes == null || boxes.isEmpty()) {
            return;
        }
        PoseStack pose = new PoseStack();
        pose.translate(anchor.x - camera.getPosition().x,
                anchor.y - camera.getPosition().y,
                anchor.z - camera.getPosition().z);
        pose.mulPose(mc.gameRenderer.getMainCamera().rotation());
        var matrix = pose.last().pose();
        CompatRender.enableBlend();
        CompatRender.defaultBlendFunc();
        CompatRender.disableDepthTest();
        CompatRender.setColorShader();
        var builder = CompatRender.begin(VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_COLOR);
        float r = 0.3F, g = 1.0F, b = 0.85F, a = 0.30F;
        float t = 0.015F;
        for (double[] box : boxes) {
            float x0 = (float) (box[0] - box[2] / 2);
            float y0 = (float) (box[1] - box[3] / 2);
            float x1 = (float) (box[0] + box[2] / 2);
            float y1 = (float) (box[1] + box[3] / 2);
            WorldHoloUtils.edge(builder, matrix, x0, y0, x1, y0 + t, r, g, b, a);
            WorldHoloUtils.edge(builder, matrix, x0, y1 - t, x1, y1, r, g, b, a);
            WorldHoloUtils.edge(builder, matrix, x0, y0, x0 + t, y1, r, g, b, a);
            WorldHoloUtils.edge(builder, matrix, x1 - t, y0, x1, y1, r, g, b, a);
        }
        builder.buildAndDraw();
        CompatRender.enableDepthTest();
        CompatRender.disableBlend();
    }

    /** 多选/框选预览包围盒。 */
    public static void renderSelectionBounds(Camera camera, Map<String, Object> options,
                                             Vec3 anchor, double x0, double y0, double x1, double y1) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || anchor == null) {
            return;
        }
        PoseStack pose = new PoseStack();
        pose.translate(anchor.x - camera.getPosition().x,
                anchor.y - camera.getPosition().y,
                anchor.z - camera.getPosition().z);
        pose.mulPose(mc.gameRenderer.getMainCamera().rotation());
        var matrix = pose.last().pose();
        CompatRender.enableBlend();
        CompatRender.defaultBlendFunc();
        CompatRender.disableDepthTest();
        CompatRender.setColorShader();
        var builder = CompatRender.begin(VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_COLOR);
        float r = 1.0F, g = 0.7F, b = 0.0F;
        float t = 0.02F;
        WorldHoloUtils.edge(builder, matrix, (float) x0, (float) y0, (float) x1, (float) (y0 + t), r, g, b, 0.55F);
        WorldHoloUtils.edge(builder, matrix, (float) x0, (float) (y1 - t), (float) x1, (float) y1, r, g, b, 0.55F);
        WorldHoloUtils.edge(builder, matrix, (float) x0, (float) y0, (float) (x0 + t), (float) y1, r, g, b, 0.55F);
        WorldHoloUtils.edge(builder, matrix, (float) (x1 - t), (float) y0, (float) x1, (float) y1, r, g, b, 0.55F);
        WorldHoloUtils.edge(builder, matrix, (float) x0, (float) y0, (float) x1, (float) y1, r, g, b, 0.06F);
        builder.buildAndDraw();
        CompatRender.enableDepthTest();
        CompatRender.disableBlend();
    }

    /** 锁定角标。 */
    public static void renderLockMarker(Camera camera, Map<String, Object> options,
                                        Vec3 anchor, double cx, double cy, double w, double h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || anchor == null) {
            return;
        }
        PoseStack pose = new PoseStack();
        pose.translate(anchor.x - camera.getPosition().x,
                anchor.y - camera.getPosition().y,
                anchor.z - camera.getPosition().z);
        pose.mulPose(mc.gameRenderer.getMainCamera().rotation());
        var matrix = pose.last().pose();
        CompatRender.enableBlend();
        CompatRender.defaultBlendFunc();
        CompatRender.disableDepthTest();
        CompatRender.setColorShader();
        var builder = CompatRender.begin(VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_COLOR);
        double size = Math.min(0.18, Math.min(w, h) / 3);
        float x0 = (float) (cx + w / 2 - size * 1.2);
        float y0 = (float) (cy + h / 2 - size * 1.2);
        float s = (float) size;
        float r = 1.0F, g = 0.55F, b = 0.15F;
        float t = s * 0.26F;
        WorldHoloUtils.edge(builder, matrix, x0, y0, x0 + s, y0 + t, r, g, b, 0.9F);
        WorldHoloUtils.edge(builder, matrix, x0, y0 + s - t, x0 + s, y0 + s, r, g, b, 0.9F);
        WorldHoloUtils.edge(builder, matrix, x0, y0, x0 + t, y0 + s, r, g, b, 0.9F);
        WorldHoloUtils.edge(builder, matrix, x0 + s - t, y0, x0 + s, y0 + s, r, g, b, 0.9F);
        float hole = s * 0.24F;
        float hx = x0 + s / 2 - hole / 2;
        float hy = y0 + s / 2 - hole / 2;
        WorldHoloUtils.edge(builder, matrix, hx, hy, hx + hole, hy + hole, r, g, b, 0.9F);
        builder.buildAndDraw();
        CompatRender.enableDepthTest();
        CompatRender.disableBlend();
    }

    /** 拖拽参考线（吸附线）。 */
    public static void renderDragGuides(Camera camera, Map<String, Object> options,
                                        double[] guides) {
        renderDragGuides(camera, options, guides, null, 0);
    }

    public static void renderDragGuides(Camera camera, Map<String, Object> options,
                                        double[] guides, Vec3 anchorOverride) {
        renderDragGuides(camera, options, guides, anchorOverride, 0);
    }

    /** thickAxis（1 = x 线增粗 / 2 = y 线增粗，0 = 无）。 */
    public static void renderDragGuides(Camera camera, Map<String, Object> options,
                                        double[] guides, Vec3 anchorOverride, int thickAxis) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || guides == null) {
            return;
        }
        Vec3 anchor = anchorOverride != null ? anchorOverride : WorldHoloUtils.anchor(mc, options);
        PoseStack pose = new PoseStack();
        pose.translate(anchor.x - camera.getPosition().x,
                anchor.y - camera.getPosition().y,
                anchor.z - camera.getPosition().z);
        pose.mulPose(mc.gameRenderer.getMainCamera().rotation());
        var matrix = pose.last().pose();
        CompatRender.enableBlend();
        CompatRender.defaultBlendFunc();
        CompatRender.disableDepthTest();
        CompatRender.setColorShader();
        var builder = CompatRender.begin(VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_COLOR);
        float span = 30.0F;
        double gx = guides[0];
        double gy = guides[1];
        if (!Double.isNaN(gx)) {
            float r = 0x42 / 255.0F;
            float g = 0xA5 / 255.0F;
            float b = 0xF5 / 255.0F;
            boolean thick = thickAxis == 1;
            float t = thick ? 0.045F : 0.015F;
            float a = thick ? 0.85F : 0.55F;
            WorldHoloUtils.edge(builder, matrix, (float) gx, -span, (float) (gx + t), span, r, g, b, a);
        }
        if (!Double.isNaN(gy)) {
            float r = 0x26 / 255.0F;
            float g = 0xC6 / 255.0F;
            float b = 0xDA / 255.0F;
            boolean thick = thickAxis == 2;
            float t = thick ? 0.045F : 0.015F;
            float a = thick ? 0.85F : 0.55F;
            WorldHoloUtils.edge(builder, matrix, -span, (float) gy, span, (float) (gy + t), r, g, b, a);
        }
        builder.buildAndDraw();
        CompatRender.enableDepthTest();
        CompatRender.disableBlend();
    }

    /** 编辑网格。 */
    public static void renderEditGrid(Camera camera, Map<String, Object> options,
                                      Vec3 anchor, double step) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || anchor == null || step <= 0) {
            return;
        }
        PoseStack pose = new PoseStack();
        pose.translate(anchor.x - camera.getPosition().x,
                anchor.y - camera.getPosition().y,
                anchor.z - camera.getPosition().z);
        pose.mulPose(mc.gameRenderer.getMainCamera().rotation());
        var matrix = pose.last().pose();
        CompatRender.enableBlend();
        CompatRender.defaultBlendFunc();
        CompatRender.disableDepthTest();
        CompatRender.setColorShader();
        var builder = CompatRender.begin(VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_COLOR);
        double half = 8.0;
        float r = 0.55F, g = 0.72F, b = 0.95F;
        for (double v = -half; v <= half + 1e-9; v += step) {
            boolean major = Math.abs(v) < step / 2;
            float a = major ? 0.30F : 0.12F;
            WorldHoloUtils.edge(builder, matrix, (float) -half, (float) v, (float) half, (float) (v + 0.006), r, g, b, a);
            WorldHoloUtils.edge(builder, matrix, (float) v, (float) -half, (float) (v + 0.006), (float) half, r, g, b, a);
        }
        builder.buildAndDraw();
        CompatRender.enableDepthTest();
        CompatRender.disableBlend();
    }

    /** 点击涟漪。 */
    public static void renderRipples(Camera camera, Map<String, Object> options,
                                     List<double[]> ripples) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || ripples == null || ripples.isEmpty()) {
            return;
        }
        Object worldOpt = options == null ? null : options.get("world");
        int defaultColor = worldOpt instanceof Map<?, ?> w
                ? UiStyle.color(w.get("rippleColor"), 0x80FFFFFF) : 0x80FFFFFF;
        long now = System.currentTimeMillis();
        var camPos = camera.getPosition();
        PoseStack pose = new PoseStack();
        pose.mulPose(mc.gameRenderer.getMainCamera().rotation());
        var matrix = pose.last().pose();
        CompatRender.enableBlend();
        CompatRender.defaultBlendFunc();
        CompatRender.disableDepthTest();
        CompatRender.setColorShader();
        var builder = CompatRender.begin(VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_COLOR);
        double maxRadius = 0.45;
        float ringWidth = 0.035F;
        int segments = 16;
        for (double[] ripple : ripples) {
            float age = (now - (long) ripple[3]) / 400.0F;
            if (age >= 1) {
                continue;
            }
            float p = age;
            float radius = (float) (0.06 + (maxRadius - 0.06) * (1 - (1 - p) * (1 - p)));
            int color = ripple.length > 4 && ripple[4] != 0 ? (int) ripple[4] : defaultColor;
            float cr = ((color >> 16) & 0xFF) / 255.0F;
            float cg = ((color >> 8) & 0xFF) / 255.0F;
            float cb = (color & 0xFF) / 255.0F;
            float alpha = ((color >>> 24) & 0xFF) / 255.0F * (1 - p);
            if (alpha <= 0.01F) {
                continue;
            }
            double wx = ripple[0] - camPos.x;
            double wy = ripple[1] - camPos.y;
            double wz = ripple[2] - camPos.z;
            for (int i = 0; i < segments; i++) {
                double a0 = Math.toRadians(360.0 * i / segments);
                double a1 = Math.toRadians(360.0 * (i + 1) / segments);
                float x0 = (float) (wx + Math.cos(a0) * radius);
                float y0 = (float) (wy + Math.sin(a0) * radius);
                float x1 = (float) (wx + Math.cos(a1) * radius);
                float y1 = (float) (wy + Math.sin(a1) * radius);
                float x2 = (float) (wx + Math.cos(a1) * (radius + ringWidth));
                float y2 = (float) (wy + Math.sin(a1) * (radius + ringWidth));
                float x3 = (float) (wx + Math.cos(a0) * (radius + ringWidth));
                float y3 = (float) (wy + Math.sin(a0) * (radius + ringWidth));
                builder.addVertex(matrix, x0, y0, (float) wz).setColor(cr, cg, cb, alpha);
                builder.addVertex(matrix, x1, y1, (float) wz).setColor(cr, cg, cb, alpha);
                builder.addVertex(matrix, x2, y2, (float) wz).setColor(cr, cg, cb, alpha);
                builder.addVertex(matrix, x3, y3, (float) wz).setColor(cr, cg, cb, alpha);
            }
        }
        builder.buildAndDraw();
        CompatRender.enableDepthTest();
        CompatRender.disableBlend();
    }
}
