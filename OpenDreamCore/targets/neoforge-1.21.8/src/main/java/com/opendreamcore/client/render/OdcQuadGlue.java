package com.opendreamcore.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

/**
 * 手持贴图卡片顶点写入胶水（1.21.1~1.21.8 直调版）。
 * 直接链式调用各代映射后的真实 API，元素顺序 position,color,uv0,uv1,uv2,normal
 * 由编译器在构建期对照格式验证，运行时零反射——反射按形状挑方法在
 * intermediary 运行时四次实机 Missing Normal 的教训。
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
            v.addVertex(mat, x1, y0, 0.0F).setColor(255, 255, 255, 255).setUv(u1, v1)
                    .setOverlay(0).setUv2(light & 0xFFFF, (light >>> 16) & 0xFFFF)
                    .setNormal(0.0F, 0.0F, 1.0F);
            v.addVertex(mat, x0, y0, 0.0F).setColor(255, 255, 255, 255).setUv(u0, v1)
                    .setOverlay(0).setUv2(light & 0xFFFF, (light >>> 16) & 0xFFFF)
                    .setNormal(0.0F, 0.0F, 1.0F);
            v.addVertex(mat, x0, y1, 0.0F).setColor(255, 255, 255, 255).setUv(u0, v0)
                    .setOverlay(0).setUv2(light & 0xFFFF, (light >>> 16) & 0xFFFF)
                    .setNormal(0.0F, 0.0F, 1.0F);
            v.addVertex(mat, x1, y1, 0.0F).setColor(255, 255, 255, 255).setUv(u1, v0)
                    .setOverlay(0).setUv2(light & 0xFFFF, (light >>> 16) & 0xFFFF)
                    .setNormal(0.0F, 0.0F, 1.0F);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
