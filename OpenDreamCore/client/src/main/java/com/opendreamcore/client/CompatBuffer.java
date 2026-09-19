package com.opendreamcore.client;

import org.joml.Matrix4f;

import java.lang.reflect.Method;

/**
 * 顶点缓冲方言吸收器（CompatRender.begin 返回）：
 * 现代(≥1.20.2) addVertex(m,x,y,z).setColor/setUv；1.20.1 vertex(m,x,y,z).color/uv + endVertex。
 * 链式方法名与新版一致，调用点零改动；legacy 待决顶点在下一次 addVertex / build 时自动 endVertex。
 * 实现 AutoCloseable：try-with-resources 自动清理，防止异常路径泄漏 unused batches。
 */
public final class CompatBuffer implements AutoCloseable {

    private final Object mode;
    private final Object format;
    private Object bb;              // 现代 BufferBuilder / 1.20.1 BufferBuilder
    private Object byteBuf;         // 自有的 ByteBufferBuilder（独立分配成功时非 null；Tesselator 兜底路径为 null）
    private Object pendingVertex;   // legacy 待决顶点（modern 恒 null）
    private boolean legacy;
    private boolean dead;           // 底层缓冲解析失败：静默失效（不渲染），不拖垮渲染帧

    /** 活跃缓冲注册表：凡 begin 后未被 draw 或 close 的，帧末统一清扫防泄漏。 */
    private static final java.util.Set<CompatBuffer> LIVE = java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** 扫排查日志节流。 */
    private static volatile long lastSweepLog = 0L;
    /** 是否已在注册表：防止重复登记。 */
    private boolean registered;
    /** draw 成功后置 true：帧末清扫时不再动它（已绘制、bb 状态正常）。 */
    private volatile boolean failedAfterDraw;
    /** begin() 时的业务调用者签名，用于定位创建但不绘的泄漏源。 */
    private final String creator;

    CompatBuffer(Object mode, Object format) {
        this.mode = mode;
        this.format = format;
        register();
        creator = captureCreator();
    }

    /** 抓 begin() 时的调用者签名，用于定位为什么会创建但不 draw/close。 */
    private static String captureCreator() {
        try {
            StackTraceElement[] st = new Throwable().getStackTrace();
            // [0]=new Throwable, [1]=构造函数, [2]=CompatRender.begin, [3]=实际调用方
            for (int i = 3; i < Math.min(st.length, 8); i++) {
                StackTraceElement e = st[i];
                String n = e.getClassName();
                if (!n.startsWith("com.opendreamcore.client")) {
                    continue;
                }
                // 跳过登记/惰性创建链，找到真正发起的业务方法
                return n + "." + e.getMethodName() + "(" + e.getLineNumber() + ")";
            }
        } catch (Throwable ignored) {}
        return "unknown";
    }

    /** begin 即登记（底层 bb 惰性创建，但只要 CompatBuffer 存在就盯住，防止漏 draw）。 */
    private void register() {
        if (!registered) {
            registered = true;
            LIVE.add(this);
        }
    }

    /** 帧末清扫：任何仍活着且底层还挂着 builder 的 CompatBuffer 强制 discard/clear。 */
    public static void sweepAll() {
        if (LIVE.isEmpty()) {
            return;
        }
        for (CompatBuffer c : LIVE) {
            if (!c.failedAfterDraw) {
                c.safeDiscardAndClear();
            }
        }
        LIVE.clear();
    }

    /** 惰性创建底层缓冲（名称直查 + 形状兜底，Fabric 生产环境方法名为 intermediary）。 */
    private void ensureBb() {
        if (bb != null || dead) {
            return;
        }
        // 关键修复：每个 CompatBuffer 独立 BufferBuilder，不再共用 Tesselator 单例。
        // 共享单例导致多个 buffer 并存时互相 begin() 清空对方顶点 -> buildOrThrow 抛 InvocationTargetException -> 全丢弃且刷 unused batches 警告。
        try {
            Object byteBufLocal = tryCreateByteBufferBuilder(2048);
            if (byteBufLocal != null) {
                Object built = tryCreateBufferBuilder(byteBufLocal, mode, format);
                if (built != null) {
                    byteBuf = byteBufLocal;
                    bb = built;
                }
            }
        } catch (Throwable ignored) {}
        if (bb == null) {
            // 兜底：老路径走 Tesselator（仅当独立分配失败时）
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
        }
        if (bb == null) {
            dead = true;
        }
    }

    private static Object tryCreateByteBufferBuilder(int capacity) {
        // 生产 Fabric 环境 blaze3d 同样是 intermediary 名（ByteBufferBuilder→class_9799），
        // 裸 Class.forName 必败 → 落到 Tesselator 单例共享 → build 后批次残留 →
        // RenderSystem.flipFrame 每帧 Tesselator.clear() 刷 "unused batches"（1.21.8 实机 295 条实证）。
        // 候选名一律先过 mapClassName（dev/NeoForge 原样返回，Fabric 生产返回 intermediary）。
        for (String cn : new String[]{
                "com.mojang.blaze3d.vertex.ByteBufferBuilder",
                CompatRender.mapClassName("com.mojang.blaze3d.vertex.ByteBufferBuilder")}) {
            try {
                Class<?> c = Class.forName(cn, false, CompatBuffer.class.getClassLoader());
                // 形态匹配（不按简单名判）：(int) 优先，其次 (int,long) / (long)
                var ctors = c.getConstructors();
                for (var ctor : ctors) {
                    var ps = ctor.getParameterTypes();
                    if (ps.length == 1 && ps[0] == int.class) return ctor.newInstance(capacity);
                }
                for (var ctor : ctors) {
                    var ps = ctor.getParameterTypes();
                    if (ps.length == 2 && ps[0] == int.class && ps[1] == long.class)
                        return ctor.newInstance(capacity, 1L << 28);
                    if (ps.length == 1 && ps[0] == long.class) return ctor.newInstance((long) capacity);
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static Object tryCreateBufferBuilder(Object byteBuf, Object mode, Object format) {
        // 同上：名走 mapClassName；构造按 (byteBuf类型, mode类型, format类型) 精确形态匹配，
        // 防将来多一个 3 参构造（如 (int,long,int)）时被盲试猜中抛 ClassCastException。
        try {
            Class<?> byteBufClass = byteBuf.getClass();
            Class<?> modeClass = mode.getClass();
            Class<?> formatClass = format.getClass();
            for (String cn : new String[]{
                    "com.mojang.blaze3d.vertex.BufferBuilder",
                    CompatRender.mapClassName("com.mojang.blaze3d.vertex.BufferBuilder")}) {
                try {
                    Class<?> bbClass = Class.forName(cn, false, CompatBuffer.class.getClassLoader());
                    for (var ctor : bbClass.getConstructors()) {
                        var ps = ctor.getParameterTypes();
                        if (ps.length == 3 && ps[0].isAssignableFrom(byteBufClass)
                                && ps[1].isAssignableFrom(modeClass) && ps[2].isAssignableFrom(formatClass)) {
                            try { return ctor.newInstance(byteBuf, mode, format); } catch (Throwable ignored) {}
                        }
                    }
                } catch (ClassNotFoundException ignored) {}
            }
        } catch (Throwable ignored) {}
        return null;
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
                return this;
            }
            // ≥1.21.8 BufferBuilder 不再收矩阵：手工乘一下再走三浮点入口
            // （原先静默丢顶点 → 空 mesh build 失败 → 共享 Tesselator 残留刷警告）
            org.joml.Vector3f p = m.transformPosition(x, y, z, new org.joml.Vector3f());
            return addVertexRaw(p.x, p.y, p.z);
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

    /**
     * 安全构建并绘制：空 buffer 跳过（防 buildOrThrow 崩溃）。
     * 现代 mesh=buildOrThrow()；旧版 rendered=bb.end()；均走 BufferUploader.drawWithShader。
     * 1.21.9+ 渲染管线重构后 drawWithShader 可能不在：找不到绘制口必须把 mesh 释放掉，
     * 否则底层批次泄漏，下一帧 clear 时刷 "unused batches" 警告。
     */
    public void buildAndDraw() {
        if (dead || bb == null) {
            sweepSelf();
            return;
        }
        flushPending();
        try {
            String buildName = CompatRender.modernBegin() ? "buildOrThrow" : "end";
            Method build = CompatRender.resolveMethod(bb.getClass(), buildName);
            Object mesh = null;
            try { mesh = build == null ? null : build.invoke(bb); } catch (Throwable t) {
                // 任何异常：空缓冲/状态错/版本差异 —— 主动丢弃防 unused batches 泄漏。
                // InvocationTargetException 是反射包装，得扒出一层看真实异常才能定位。
                Throwable real = t;
                if (real instanceof java.lang.reflect.InvocationTargetException ite) {
                    Throwable c = ite.getCause();
                    if (c != null) real = c;
                }
                safeDiscardAndClear();
                return;
            }
            if (mesh == null) {
                safeDiscardAndClear();
                return;
            }
            // BufferUploader 在 >=1.21.8 可能移位/改名；且 Fabric 生产环境类名也是 intermediary：
            // 候选名先原样加载，再经 MappingResolver 映射后加载
            java.util.LinkedHashSet<String> candidates = new java.util.LinkedHashSet<>();
            for (String cn : new String[]{
                    "com.mojang.blaze3d.vertex.BufferUploader",
                    "com.mojang.blaze3d.systems.BufferUploader"}) {
                candidates.add(cn);
                candidates.add(CompatRender.mapClassName(cn));
            }
            boolean drawn = false;
            for (String cn : candidates) {
                Class<?> uploader;
                try { uploader = Class.forName(cn); } catch (ClassNotFoundException ignored) { continue; }
                // 多签名尝试：旧版单参 mesh；新版 1.21.1+ mesh + PoseStack/MatrixStack
                for (String mn : new String[]{"drawWithShader", "draw"}) {
                    // 单参 mesh
                    Method m = CompatRender.resolveMethod(uploader, mn, mesh.getClass());
                    if (m != null) {
                        try { m.invoke(null, mesh); drawn = true; break; } catch (Exception ignored) {}
                    }
                    // 双参 mesh + PoseStack
                    Class<?> pose = CompatRender.poseStackClass();
                    if (pose != null) {
                        m = CompatRender.resolveMethod(uploader, mn, mesh.getClass(), pose);
                        if (m != null) {
                            Object ps = CompatRender.currentPoseStack();
                            if (ps != null) { try { m.invoke(null, mesh, ps); drawn = true; break; } catch (Exception ignored) {} }
                        }
                    }
                    // 双参 mesh + MatrixStack
                    Class<?> mstack = CompatRender.matrixStackClass();
                    if (mstack != null) {
                        m = CompatRender.resolveMethod(uploader, mn, mesh.getClass(), mstack);
                        if (m != null) {
                            Object ms = CompatRender.currentMatrixStack();
                            if (ms != null) { try { m.invoke(null, mesh, ms); drawn = true; break; } catch (Exception ignored) {} }
                        }
                    }
                }
                if (drawn) break;
            }
            if (drawn) {
                failedAfterDraw = false;
                sweepSelf();
                return;
            }
            // 绘制口全落空：1.21.9+ 管线重构后 BufferUploader 已删，
            // 改走 RenderType.draw(MeshData)（原版自刷新路径，新管线兼容）
            if (drawViaRenderType(mesh)) {
                failedAfterDraw = false;
                sweepSelf();
                return;
            }
            // 仍然没画出去：释放 mesh 兜底（AutoCloseable 或同名 release），
            // 否则底层批次泄漏，下一帧 clear 时刷 "unused batches" 警告
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
            sweepSelf();
        } catch (Throwable ignored) {
            // 任何异常：安全跳过，但确保底层 buffer 被丢弃
            safeDiscardAndClear();
        }
    }

    /** 从注册表移除自己（已正常绘制/已关闭/已丢弃）。 */
    private void sweepSelf() {
        LIVE.remove(this);
        if (bb != null) {
            bb = null;
        }
    }

    /** 统一丢弃底层缓冲，防 unused batches 泄漏 / 自持缓冲无限增长。 */
    private void safeDiscardAndClear() {
        Object b = bb;
        Object byteBuf = this.byteBuf;
        if (b != null) {
            // 新版（≥1.21.9）BufferBuilder 自带 discard；1.21.8 无此方法，靠自有 ByteBufferBuilder 兼平
            try { Method discard = CompatRender.resolveMethod(b.getClass(), "discard"); if (discard != null) discard.invoke(b); } catch (Throwable ignored) {}
            try { Method clear = CompatRender.resolveMethod(b.getClass(), "clear"); if (clear != null) clear.invoke(b); } catch (Throwable ignored) {}
            bb = null;
        }
        // 自有 ByteBufferBuilder（独立分配路径才有）：discard 把 resultCount 归零，
        // 否则 build 计数只增不减，自持缓冲越用越大（Tesselator 兜底路径 byteBuf==null，不碰共享单例）。
        if (byteBuf != null) {
            try {
                Method discard = CompatRender.resolveMethod(byteBuf.getClass(), "discard");
                if (discard != null) {
                    discard.invoke(byteBuf);
                }
            } catch (Throwable ignored) {}
            this.byteBuf = null;
        }
        sweepSelf();
    }

    @Override
    public void close() {
        // try-with-resources 兜底：若未显式调用 buildAndDraw()，这里清理防泄漏
        if (bb != null && !dead && !failedAfterDraw) {
            safeDiscardAndClear();
        } else {
            sweepSelf();
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

    /** RenderTypes 持有类：mojmap 名直查 + 历史包路径 + Fabric 映射名兜底。
     *  1.21.11/26.x 在 renderer.rendertype.RenderTypes；某些快照在 renderer.RenderTypes；
     *  1.21.8 无独立 RenderTypes 类，无参工厂（lines()/debugQuads() 等）直接在 RenderType 自身。 */
    private static Class<?> findRenderTypesHolder() {
        String[] bases = {
                "net.minecraft.client.renderer.rendertype.RenderTypes",
                "net.minecraft.client.renderer.RenderTypes",
                "net.minecraft.client.renderer.RenderType"};
        for (String cn : bases) {
            for (String n : new String[]{cn, CompatRender.mapClassName(cn)}) {
                try {
                    return Class.forName(n, false, CompatBuffer.class.getClassLoader());
                } catch (ClassNotFoundException ignored) {
                }
            }
        }
        return null;
    }
}
