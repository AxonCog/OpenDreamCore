package com.opendreamcore.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * 手持贴图卡片顶点写入胶水（1.20.1 直调版）。
 * 1.20.1 的 vertex 只有 (double,double,double)，无矩阵重载，矩阵变换自己做；
 * 元素顺序 position,color,uv0,uv1(叠加),uv2(光照),normal，链式直调。
 */
public final class OdcQuadGlue {
    private OdcQuadGlue() {
    }

    /** rect = {x0,x1,y0,y1,u0,u1,v0,v1}；light = packed 光照。 */
    public static boolean quad(Object consumerObj, Matrix4f mat, float[] rect, int light) {
        if (!(consumerObj instanceof VertexConsumer) || mat == null || rect == null || rect.length < 8) {
            return false;
        }
        VertexConsumer v = (VertexConsumer) consumerObj;
        float x0 = rect[0], x1 = rect[1], y0 = rect[2], y1 = rect[3];
        float u0 = rect[4], u1 = rect[5], v0 = rect[6], v1 = rect[7];
        try {
            float[][] pts = {
                    {x1, y0, u1, v1}, {x0, y0, u0, v1},
                    {x0, y1, u0, v0}, {x1, y1, u1, v0},
            };
            for (float[] p : pts) {
                Vector4f t = new Vector4f(p[0], p[1], 0.0F, 1.0F).mul(mat);
                v.vertex(t.x(), t.y(), t.z())
                        .color(255, 255, 255, 255)
                        .uv(p[2], p[3])
                        .overlayCoords(0, 0)
                        .uv2(light & 0xFFFF, (light >>> 16) & 0xFFFF)
                        .normal(0.0F, 0.0F, 1.0F);
            }
            return true;
        } catch (Throwable t2) {
            return false;
        }
    }
}
