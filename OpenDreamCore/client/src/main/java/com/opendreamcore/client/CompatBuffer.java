package com.opendreamcore.client;

import org.joml.Matrix4f;

import java.lang.reflect.Method;

/**
 * 顶点缓冲方言吸收器（CompatRender.begin 返回）：
 * 现代(≥1.20.2) addVertex(m,x,y,z).setColor/setUv；1.20.1 vertex(m,x,y,z).color/uv + endVertex。
 * 链式方法名与新版一致，调用点零改动；legacy 待决顶点在下一次 addVertex / build 时自动 endVertex。
 */
public final class CompatBuffer {

    private final Object mode;
    private final Object format;
    private Object bb;              // 现代 BufferBuilder / 1.20.1 BufferBuilder
    private Object pendingVertex;   // legacy 待决顶点（modern 恒 null）
    private boolean legacy;
    private boolean dead;           // 底层缓冲解析失败：静默失效（不渲染），不拖垮渲染帧

    CompatBuffer(Object mode, Object format) {
        this.mode = mode;
        this.format = format;
    }

    /** 惰性创建底层缓冲（名称直查 + 形状兜底，Fabric 生产环境方法名为 intermediary）。 */
    private void ensureBb() {
        if (bb != null || dead) {
            return;
        }
        var t = com.mojang.blaze3d.vertex.Tesselator.getInstance();
        if (CompatRender.modernBegin()) {
            Method m = CompatRender.resolveMethod(t.getClass(), "begin",
                    mode.getClass(), format.getClass());
            if (m != null) {
                try {
                    bb = m.invoke(t, mode, format);
                } catch (Exception ignored) {
                }
            }
        } else {
            Method gb = CompatRender.resolveMethod(t.getClass(), "getBuilder");
            if (gb != null) {
                try {
                    bb = gb.invoke(t);
                    Method bm = CompatRender.resolveMethod(bb.getClass(), "begin",
                            mode.getClass(), format.getClass());
                    if (bm != null) {
                        bm.invoke(bb, mode, format);
                    } else {
                        bb = null;
                    }
                } catch (Exception ignored) {
                    bb = null;
                }
            }
        }
        if (bb == null) {
            dead = true;
        }
    }

    /** 结束上一个待决顶点（仅 legacy 需要）。 */
    private void flushPending() {
        if (pendingVertex != null) {
            Method m = CompatRender.resolveMethod(pendingVertex.getClass(), "endVertex");
            if (m != null) {
                try {
                    m.invoke(pendingVertex);
                } catch (Exception ignored) {
                }
            }
            pendingVertex = null;
        }
    }

    public CompatBuffer addVertex(Matrix4f m, float x, float y, float z) {
        ensureBb();
        flushPending();
        if (dead) {
            return this;
        }
        try {
            String name = CompatRender.modernBegin() ? "addVertex" : "vertex";
            Method method = CompatRender.resolveMethod(bb.getClass(), name,
                    Matrix4f.class, float.class, float.class, float.class);
            if (method != null) {
                pendingVertex = method.invoke(bb, m, x, y, z);
                legacy = !CompatRender.modernBegin();
            }
        } catch (Exception ignored) {
        }
        return this;
    }

    /** 无矩阵顶点（CompatRender.bufferAddVertex 对 Matrix3x2f 手工变换后的入口）。 */
    CompatBuffer addVertexRaw(float x, float y, float z) {
        ensureBb();
        flushPending();
        if (dead) {
            return this;
        }
        try {
            String name = CompatRender.modernBegin() ? "addVertex" : "vertex";
            Method m = CompatRender.resolveMethod(bb.getClass(), name,
                    float.class, float.class, float.class);
            if (m != null) {
                pendingVertex = m.invoke(bb, x, y, z);
                legacy = !CompatRender.modernBegin();
            }
        } catch (Exception ignored) {
        }
        return this;
    }

    /**
     * 版本无关矩阵顶点：Matrix4f（旧 GUI/世界 PoseStack）走原路；
     * Matrix3x2f（≥1.21.6 GuiGraphics.pose()）按 x'=m00·x+m10·y+m20 手工变换；null 视为单位阵。
     */
    public CompatBuffer addVertex(Object matrix, float x, float y, float z) {
        if (matrix instanceof org.joml.Matrix3x2f m) {
            float tx = m.m00() * x + m.m10() * y + m.m20();
            float ty = m.m01() * x + m.m11() * y + m.m21();
            return addVertexRaw(tx, ty, z);
        }
        if (matrix instanceof Matrix4f m4) {
            return addVertex(m4, x, y, z);
        }
        return addVertexRaw(x, y, z);
    }

    public CompatBuffer setColor(float r, float g, float b, float a) {
        applyToPending(new String[]{"setColor", "color"},
                new Class<?>[]{float.class, float.class, float.class, float.class},
                new Object[]{r, g, b, a});
        return this;
    }

    public CompatBuffer setColor(int argb) {
        applyToPending(new String[]{"setColor", "color"},
                new Class<?>[]{int.class},
                new Object[]{argb});
        return this;
    }

    public CompatBuffer setUv(float u, float v) {
        applyToPending(new String[]{"setUv", "uv"},
                new Class<?>[]{float.class, float.class},
                new Object[]{u, v});
        return this;
    }

    private void applyToPending(String[] names, Class<?>[] types, Object[] args) {
        if (pendingVertex == null) {
            return;
        }
        String name = CompatRender.modernBegin() ? names[0] : names[1];
        Method m = CompatRender.resolveMethod(pendingVertex.getClass(), name, types);
        if (m != null) {
            try {
                m.invoke(pendingVertex, args);
            } catch (Exception ignored) {
            }
        }
    }

        /** 只报一次的标记：绘制通道全部失配时提醒（避免逐帧刷屏）。 */
    private static volatile boolean warnedNoDrawPath;

    /**
     * 安全构建并绘制：空 buffer 跳过（防 buildOrThrow 崩溃）。
     * 现代 mesh=buildOrThrow()；旧版 rendered=bb.end()；均走 BufferUploader.drawWithShader。
     * 1.21.9+ 渲染管线重构后 drawWithShader 可能不在：找不到绘制口必须把 mesh 释放掉，
     * 否则底层批次泄漏，下一帧 clear 时刷 "unused batches" 警告。
     */
    public void buildAndDraw() {
        if (dead || bb == null) {
            return;
        }
        flushPending();
        try {
            String buildName = CompatRender.modernBegin() ? "buildOrThrow" : "end";
            Method build = CompatRender.resolveMethod(bb.getClass(), buildName);
            Object mesh = build == null ? null : build.invoke(bb);
            if (mesh == null) {
                return;
            }
            // BufferUploader 在 ≥1.21.8 可能移位/改名；且 Fabric 生产环境类名也是 intermediary：
            // 候选名先原样加载，再经 MappingResolver 映射后加载
            java.util.LinkedHashSet<String> candidates = new java.util.LinkedHashSet<>();
            for (String cn : new String[]{
                    "com.mojang.blaze3d.vertex.BufferUploader",
                    "com.mojang.blaze3d.systems.BufferUploader"}) {
                candidates.add(cn);
                candidates.add(CompatRender.mapClassName(cn));
            }
            for (String cn : candidates) {
                Class<?> uploader;
                try {
                    uploader = Class.forName(cn);
                } catch (ClassNotFoundException ignored) {
                    continue;
                }
                for (String mn : new String[]{"drawWithShader", "draw"}) {
                    Method m = CompatRender.resolveMethod(uploader, mn, mesh.getClass());
                    if (m != null) {
                        try {
                            m.invoke(null, mesh);
                            return;
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
            // 绘制口全落空：1.21.9+ 管线重构后 BufferUploader 已删，
            // 改走 RenderType.draw(MeshData)（原版自刷新路径，新管线兼容）
            if (drawViaRenderType(mesh)) {
                return;
            }
            // 仍然没画出去：释放 mesh 兜底（AutoCloseable 或同名 release），
            // 否则底层批次泄漏，下一帧 clear 时刷 "unused batches" 警告
            if (!warnedNoDrawPath) {
                warnedNoDrawPath = true;
                System.out.println("[ODC] 当前版本未找到网格绘制入口，顶点内容已跳过（仅提示一次）");
            }
            try {
                ((AutoCloseable) mesh).close();
            } catch (Throwable e1) {
                try {
                    Method rel = mesh.getClass().getMethod("release");
                    rel.setAccessible(true);
                    rel.invoke(mesh);
                } catch (Throwable ignored) {
                }
            }
        } catch (Exception ignored) {
            // 空 buffer 或渲染异常：安全跳过
        }
    }
    private static volatile Object cachedRenderType;
    private static volatile Method cachedRenderTypeDraw;

    /**
     * 1.21.9+ 兜底绘制：找 RenderTypes.debugQuads() 之类的静态无参 RenderType 工厂，调它的 draw(mesh)。
     * 方法名在生产环境是 intermediary，所以不按名猜——全量扫描静态无参返回 RenderType 的工厂，
     * 逐个试 draw；格式/模式不匹配会抛异常被吞掉换下一个。命中一次即缓存，后续零扫描开销。
     */
    private boolean drawViaRenderType(Object mesh) {
        if (cachedRenderType != null && cachedRenderTypeDraw != null) {
            return invokeRenderTypeDraw(cachedRenderType, cachedRenderTypeDraw, mesh);
        }
        Class<?> holder = findRenderTypesHolder();
        if (holder == null) {
            return false;
        }
        for (Method m : holder.getMethods()) {
            if (m.getParameterCount() != 0
                    || m.getReturnType().isInterface()
                    || !m.getReturnType().getSimpleName().equals("RenderType")) {
                continue;
            }
            Object rt;
            try {
                rt = m.invoke(null);
            } catch (Throwable skipNonStaticOrFail) {
                continue;
            }
            if (rt == null) {
                continue;
            }
            Method draw = CompatRender.resolveMethod(rt.getClass(), "draw", mesh.getClass());
            if (draw == null) {
                continue;
            }
            if (invokeRenderTypeDraw(rt, draw, mesh)) {
                cachedRenderType = rt;
                cachedRenderTypeDraw = draw;
                return true;
            }
        }
        return false;
    }

    private boolean invokeRenderTypeDraw(Object rt, Method draw, Object mesh) {
        try {
            draw.invoke(rt, mesh);
            return true;
        } catch (Throwable wrongFormatOrMode) {
            return false;
        }
    }

    /** RenderTypes 持有类：mojmap 名直查 + Fabric 映射名兜底。 */
    private static Class<?> findRenderTypesHolder() {
        String cn = "net.minecraft.client.renderer.rendertype.RenderTypes";
        String[] names = {cn, CompatRender.mapClassName(cn)};
        for (String n : names) {
            try {
                return Class.forName(n);
            } catch (ClassNotFoundException ignored) {
            }
        }
        return null;
    }
}
