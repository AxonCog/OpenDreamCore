package com.opendreamcore.client.camera;

import com.opendreamcore.camera.CameraRule;
import com.opendreamcore.camera.CameraStore;
import com.opendreamcore.camera.CameraTimeline;
import com.opendreamcore.client.CompatBuffer;
import com.opendreamcore.client.CompatRender;
import com.opendreamcore.client.RenderGuard;
import com.opendreamcore.client.render.WorldSolidRect;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * M1.7 可视化编辑 L1：路径全息（点标记/朝向指示/曲线）。
 *
 * <p>只在编辑会话激活时画；曲线在「预览中」复用 {@link CameraTimeline#sample} 对预览规则采样——
 * 编辑看到的就是播放走的那条线，不存在两套插值。绘制模式与 WorldHologram 同一套：
 * RenderGuard.enterWorldHolo 状态窗口 + TRIANGLES/POSITION_COLOR 批次
 * （buildAndDraw 内部在窗口内自动挂世界渲染类型，光影可归类）。
 *
 * <p>不画文字（世界文字要字体批次，成本高）：点序号用颜色区分，悬停/拖拽交互是 L2 的事。
 */
public final class CameraPathHolo {

    private static final float LINE_W = 0.03F;
    private static final float MARKER_HALF = 0.09F;
    private static final float DIR_LEN = 0.45F;
    private static final int CURVE_COLOR = 0x8033DDFF;
    private static final int DIR_COLOR = 0xB0FFFFFF;

    private static final int[] PALETTE = {
            0xE055FF55, 0xE055CCFF, 0xE0FFFF55, 0xE0FF7755, 0xE0FF55AA, 0xE0AA88FF};

    private CameraPathHolo() {
    }

    /** 每帧调用（HeadTagRenderer.renderFrame 转接）：会话未激活直接返回。 */
    public static void render(net.minecraft.client.Camera camera, float partialTick) {
        CameraEditSession s = CameraEditSession.get();
        if (!s.active() || s.pointsView().isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null) {
            return;
        }
        try (RenderGuard guard = RenderGuard.enterWorldHolo()) {
            CompatRender.setColorShader();
            Vec3 cam = camera.getPosition();
            drawCurve(s, cam);
            drawMarkers(s, camera.rotation(), cam);
        } catch (Throwable ignored) {
            // 全息是编辑辅助，任何渲染异常都不能打断世界帧；下一帧自然重试
        }
    }

    /** 曲线：预览中按预览规则采样（与播放同插值），否则点间直线。 */
    private static void drawCurve(CameraEditSession s, Vec3 cam) {
        double[][] path;
        if (CameraPathDrag.dragIndex() >= 0) {
            // 拖拽中：以实时点位为准（预览规则还是拖拽前的旧数据）
            path = s.pointsView().toArray(new double[0][]);
        } else if (s.previewing()) {
            CameraRule rule = CameraStore.get().byId(CameraEditSession.PREVIEW_ID);
            if (rule == null) {
                return;
            }
            path = sampleRule(rule);
        } else {
            List<double[]> pts = s.pointsView();
            path = pts.toArray(new double[0][]);
        }
        if (path.length < 2) {
            return;
        }
        var builder = WorldSolidRect.begin(
                com.mojang.blaze3d.vertex.VertexFormat.Mode.TRIANGLES);
        builder.fixedUv(0F, 0F);
        Matrix4f m = new Matrix4f();
        for (int i = 1; i < path.length; i++) {
            ribbon(builder, m, rel(path[i - 1], cam), rel(path[i], cam), LINE_W, CURVE_COLOR);
        }
        builder.buildAndDraw();
    }

    /** 点标记（billboard 方块）+ 朝向指示（沿视角伸出的短杆）。 */
    private static void drawMarkers(CameraEditSession s, Quaternionf camRot, Vec3 cam) {
        List<double[]> pts = s.pointsView();
        var builder = WorldSolidRect.begin(
                com.mojang.blaze3d.vertex.VertexFormat.Mode.TRIANGLES);
        builder.fixedUv(0F, 0F);
        Matrix4f m = new Matrix4f();
        for (int i = 0; i < pts.size(); i++) {
            boolean dragged = i == CameraPathDrag.dragIndex();
            int color = dragged ? 0xFFFFFFFF : PALETTE[i % PALETTE.length];
            double[] p = rel(pts.get(i), cam);
            billboard(builder, m, camRot, p, dragged ? MARKER_HALF * 1.6F : MARKER_HALF, color);
            double[] rot = s.rotOf(i);
            if (rot != null) {
                double yaw = Math.toRadians(rot[0]);
                double pitch = Math.toRadians(rot[1]);
                double[] dir = {
                        -Math.sin(yaw) * Math.cos(pitch),
                        -Math.sin(pitch),
                        Math.cos(yaw) * Math.cos(pitch)};
                double[] tip = {p[0] + dir[0] * DIR_LEN, p[1] + dir[1] * DIR_LEN, p[2] + dir[2] * DIR_LEN};
                ribbon(builder, m, p, tip, 0.02F, DIR_COLOR);
            }
        }
        builder.buildAndDraw();
    }

    /** 预览规则采样成折点列（与播放同源）。 */
    private static double[][] sampleRule(CameraRule rule) {
        double total = Math.max(0.05, CameraTimeline.totalSeconds(rule));
        int n = 64;
        double[][] out = new double[n + 1][];
        for (int i = 0; i <= n; i++) {
            CameraTimeline.Pose pose = CameraTimeline.sample(rule, total * i / n);
            out[i] = pose.hasPos() ? pose.pos : null;
        }
        // 有洞（关键帧只有 rot 没有 pos）时跳过洞段：压缩非空点
        int w = 0;
        for (double[] p : out) {
            if (p != null) {
                out[w++] = p;
            }
        }
        double[][] tight = new double[w][];
        System.arraycopy(out, 0, tight, 0, w);
        return tight;
    }

    // ---------------------------------------------------------------- 图元

    /** 细带四边形（两三角形）：沿 XZ 平面法向撑开宽度；垂直段自动退化为 X 向。 */
    private static void ribbon(CompatBuffer b, Matrix4f m, double[] a, double[] c, float w, int argb) {
        double dx = c[0] - a[0];
        double dz = c[2] - a[2];
        double len = Math.sqrt(dx * dx + dz * dz);
        double nx;
        double nz;
        if (len < 1e-6) {
            nx = 1;
            nz = 0;
        } else {
            nx = -dz / len;
            nz = dx / len;
        }
        float ax = (float) (a[0] + nx * w);
        float az = (float) (a[2] + nz * w);
        float bx = (float) (a[0] - nx * w);
        float bz = (float) (a[2] - nz * w);
        float cx = (float) (c[0] + nx * w);
        float cz = (float) (c[2] + nz * w);
        float ex = (float) (c[0] - nx * w);
        float ez = (float) (c[2] - nz * w);
        b.addVertex(m, ax, (float) a[1], az).setColor(argb);
        b.addVertex(m, cx, (float) c[1], cz).setColor(argb);
        b.addVertex(m, ex, (float) c[1], ez).setColor(argb);
        b.addVertex(m, ax, (float) a[1], az).setColor(argb);
        b.addVertex(m, ex, (float) c[1], ez).setColor(argb);
        b.addVertex(m, bx, (float) a[1], bz).setColor(argb);
    }

    /** 相机面向的方块标记：局部 XY 平面方形，随相机旋转永远正对屏幕。 */
    private static void billboard(CompatBuffer b, Matrix4f m, Quaternionf camRot,
                                  double[] p, float half, int argb) {
        Vector3f v = new Vector3f();
        float[][] corners = {{-half, -half}, {half, -half}, {half, half}, {-half, half}};
        float[] xs = new float[4];
        float[] ys = new float[4];
        float[] zs = new float[4];
        for (int i = 0; i < 4; i++) {
            v.set(corners[i][0], corners[i][1], 0).rotate(camRot);
            xs[i] = (float) p[0] + v.x;
            ys[i] = (float) p[1] + v.y;
            zs[i] = (float) p[2] + v.z;
        }
        b.addVertex(m, xs[0], ys[0], zs[0]).setColor(argb);
        b.addVertex(m, xs[1], ys[1], zs[1]).setColor(argb);
        b.addVertex(m, xs[2], ys[2], zs[2]).setColor(argb);
        b.addVertex(m, xs[0], ys[0], zs[0]).setColor(argb);
        b.addVertex(m, xs[2], ys[2], zs[2]).setColor(argb);
        b.addVertex(m, xs[3], ys[3], zs[3]).setColor(argb);
    }

    private static double[] rel(double[] p, Vec3 cam) {
        return new double[]{p[0] - cam.x, p[1] - cam.y, p[2] - cam.z};
    }
}
