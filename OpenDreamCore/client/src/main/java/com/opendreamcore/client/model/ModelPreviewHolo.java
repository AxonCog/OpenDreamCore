package com.opendreamcore.client.model;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.opendreamcore.model.bake.BakedModel;
import com.opendreamcore.model.bake.ModelAssetStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * P6 模型全息预览：把某条规则的目标模型以线框（包围盒 + 三角边）画在锚点上，
 * 复用相机路径全息的渲染手段（RenderGuard + CompatRender + 细带 ribbon）。
 *
 * <p>/odc model preview &lt;模型id&gt; 开、preview off 关；锚点 = 开启瞬间玩家面前 3 格。
 * 全息是编辑辅助：任何渲染异常吞掉，下一帧自然重试，绝不打断世界帧。
 */
public final class ModelPreviewHolo {

    private static final int BOX_COLOR = 0xFF3FD2FF;
    private static final int EDGE_COLOR = 0xFFB7F5FF;
    private static final float LINE_W = 0.015F;
    /** 预览最多画的三角形边数预算（防爆面数模型拖死帧率）。 */
    private static final int MAX_TRIS = 400;

    private static volatile String previewModelId;
    private static volatile double[] anchor;

    private ModelPreviewHolo() {
    }

    /** 当前预览的模型 id（null = 未开）。 */
    public static String previewing() {
        return previewModelId;
    }

    /**
     * 开/换预览：锚点定在玩家面前 3 格。
     *
     * @return 给命令的回显行
     */
    public static String start(String modelId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) {
            return "§c还没进入世界";
        }
        previewModelId = modelId;
        var look = mc.player.getLookAngle();
        var pos = mc.player.position();
        anchor = new double[]{
                pos.x + look.x * 3.0,
                pos.y + 1.2 + look.y * 3.0,
                pos.z + look.z * 3.0};
        return "§a模型预览: §f" + modelId + " §7（/odc model preview off 关闭）";
    }

    /** 关闭预览。 */
    public static void stop() {
        previewModelId = null;
        anchor = null;
    }

    /** 每帧渲染（HeadTagRenderer.renderFrame 里调用，与相机全息同位）。 */
    public static void render(Camera camera, float partialTick) {
        String id = previewModelId;
        if (id == null || anchor == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null) {
            return;
        }
        try (com.opendreamcore.client.RenderGuard guard =
                     com.opendreamcore.client.RenderGuard.enterWorldHolo()) {
            com.opendreamcore.client.CompatRender.setColorShader();
            Vec3 cam = camera.getPosition();
            var builder = com.opendreamcore.client.render.WorldSolidRect.begin(
                    VertexFormat.Mode.TRIANGLES);
            builder.fixedUv(0F, 0F);
            Matrix4f m = new Matrix4f();
            drawModel(id, builder, m, cam);
            builder.buildAndDraw();
        } catch (Throwable ignored) {
            // 全息异常不打断世界帧
        }
    }

    /** 包围盒 + 限流三角边。 */
    private static void drawModel(String id, com.opendreamcore.client.CompatBuffer b, Matrix4f m, Vec3 cam) {
        var models = ModelAssetStore.get().byModelId(id);
        if (models == null || models.isEmpty()) {
            return;
        }
        double[] min = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
        double[] max = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        int drawn = 0;
        for (BakedModel bm : models) {
            collectBounds(bm, min, max);
            drawn += drawEdges(bm, b, m, cam, MAX_TRIS - drawn);
            if (drawn >= MAX_TRIS) {
                break;
            }
        }
        if (min[0] > max[0]) {
            return;
        }
        drawBox(b, m, cam, min, max);
    }

    private static void collectBounds(BakedModel bm, double[] min, double[] max) {
        float[] all = bm.quads;
        int stride = 8;
        for (int i = 0; i + stride <= all.length; i += stride) {
            expand(min, max, all[i], all[i + 1], all[i + 2]);
        }
        all = bm.tris;
        for (int i = 0; i + stride <= all.length; i += stride) {
            expand(min, max, all[i], all[i + 1], all[i + 2]);
        }
    }

    private static void expand(double[] min, double[] max, float x, float y, float z) {
        if (x < min[0]) {
            min[0] = x;
        }
        if (y < min[1]) {
            min[1] = y;
        }
        if (z < min[2]) {
            min[2] = z;
        }
        if (x > max[0]) {
            max[0] = x;
        }
        if (y > max[1]) {
            max[1] = y;
        }
        if (z > max[2]) {
            max[2] = z;
        }
    }

    /** 画三角边（ribbon 细带），返回本次画的三角形数。 */
    private static int drawEdges(BakedModel bm, com.opendreamcore.client.CompatBuffer b, Matrix4f m,
                                 Vec3 cam, int budget) {
        float[] arr = bm.tris;
        int count = 0;
        int stride = 8;
        for (int i = 0; i + stride * 3 <= arr.length && count < budget; i += stride * 3) {
            double[] a = rel(arr[i], arr[i + 1], arr[i + 2], cam);
            double[] c = rel(arr[i + stride], arr[i + stride + 1], arr[i + stride + 2], cam);
            double[] d = rel(arr[i + stride * 2], arr[i + stride * 2 + 1], arr[i + stride * 2 + 2], cam);
            ribbon(b, m, a, c, LINE_W, EDGE_COLOR);
            ribbon(b, m, c, d, LINE_W, EDGE_COLOR);
            ribbon(b, m, d, a, LINE_W, EDGE_COLOR);
            count++;
        }
        return count;
    }

    /** 包围盒 12 条棱。 */
    private static void drawBox(com.opendreamcore.client.CompatBuffer b, Matrix4f m, Vec3 cam,
                                double[] min, double[] max) {
        double[][] p = new double[8][];
        int i = 0;
        for (double x : new double[]{min[0], max[0]}) {
            for (double y : new double[]{min[1], max[1]}) {
                for (double z : new double[]{min[2], max[2]}) {
                    p[i++] = rel(x, y, z, cam);
                }
            }
        }
        int[][] edges = {{0, 1}, {2, 3}, {4, 5}, {6, 7}, {0, 2}, {1, 3}, {4, 6}, {5, 7},
                {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        for (int[] e : edges) {
            ribbon(b, m, p[e[0]], p[e[1]], LINE_W, BOX_COLOR);
        }
    }

    // ---------------------------------------------------------------- 图元（绘制路径与 CameraPathHolo 相同）

    private static double[] rel(double x, double y, double z, Vec3 cam) {
        return new double[]{x - cam.x, y - cam.y, z - cam.z};
    }

    /** 细带四边形（两三角形）：沿 XZ 平面法向撑开宽度；垂直段自动退化为 X 向。 */
    private static void ribbon(com.opendreamcore.client.CompatBuffer b, Matrix4f m,
                               double[] a, double[] c, float w, int argb) {
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
}
