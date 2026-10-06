package com.opendreamcore.client;

import org.joml.Matrix4f;

import com.opendreamcore.client.render.CompatVertexAttributes;
import com.opendreamcore.client.render.FixedVertexUv;
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
    /** discard 诊断日志节流。 */
    private static volatile long lastDiscardLog;
    /** 是否已在注册表：防止重复登记。 */
    private boolean registered;
    /** draw 成功后置 true：帧末清扫时不再动它（已绘制、bb 状态正常）。 */
    private volatile boolean failedAfterDraw;
    /** 固定 UV（纯色几何用），语义与开关理由见 FixedVertexUv。 */
    private final FixedVertexUv fixedUv = new FixedVertexUv();
    /** begin() 时的业务调用者签名，用于定位创建但不绘的泄漏源。 */
    /** 调用者签名按需再抓：抓一次要 new Throwable().getStackTrace()，每帧几十个元素就是几十次。 */

    /** 世界语义渲染类型（世界绘制专用；null = 走原有上传/立即模式路径）。 */
    private Object worldRenderType;

    CompatBuffer(Object mode, Object format) {
        this.mode = mode;
        this.format = format;
        register();
    }
    /** 抓 begin() 时的调用者签名，用于定位为什么会创建但不 draw/close（实现在 BufferDiag）。 */
    private static String captureCreator() {
        return com.opendreamcore.client.render.BufferDiag.captureCreator();
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
        // 共享单例导致多个 buffer 并存时互相 begin() 清空对方顶点 -> buildOrThrow 抛
        // InvocationTargetException -> 全丢弃且刷 unused batches 警告。
        try {
            Object byteBufLocal = tryCreateByteBufferBuilder(2048);
            if (byteBufLocal != null) {
                Object built = tryCreateBufferBuilder(byteBufLocal, mode, format);
                if (built != null) {
                    byteBuf = byteBufLocal;
                    bb = built;
                }
            }
        } catch (Throwable ignored) { /* 独立分配失败：走下方兜底路径 */ }
        if (bb == null) {
            if (CompatRender.modernBegin()) {
                beginFromTesselator();
            } else {
                beginFromOwnBuilder();
            }
        }
        if (bb == null) {
            markDead();
        }
    }

    /** 旧世代（1.20.1）兜底：自建 BufferBuilder(int)。
     *  它自己向 MemoryTracker 申请缓冲，等价于原版 Tesselator 的内部做法。绝不能退到
     *  Tesselator 单例：那是原版 GUI 正在用的同一个 builder，我们 begin 之后原版下一帧
     *  begin 就撞 "Already building!"（1.20.1 实机崩溃 2026-09-27 08:00:56，
     *  栈顶 BufferBuilder.m_166779_）。 */
    private void beginFromOwnBuilder() {
        try {
            Object built = tryCreateOwnBufferBuilder(4096);
            if (built != null) {
                Method bm = CompatRender.resolveMethod(built.getClass(), "begin",
                        mode.getClass(), format.getClass());
                if (bm != null) {
                    bm.invoke(built, mode, format);
                    bb = built;
                }
            }
        } catch (Throwable ignored) { /* 自建失败：dead 标记兜底 */ }
    }

    /** ≥1.20.2 兜底：Tesselator.begin 每次新分配一个 BufferBuilder（非共享单例），
     *  保留兜底不会污染原版，仅在独立分配意外失败时才走到。 */
    private void beginFromTesselator() {
        var t = com.mojang.blaze3d.vertex.Tesselator.getInstance();
        Method m = CompatRender.resolveMethod(t.getClass(), "begin",
                mode.getClass(), format.getClass());
        if (m == null) {
            return;
        }
        try {
            bb = m.invoke(t, mode, format);
        } catch (Exception first) {
            // 同帧上一个批次停在 building 态（上游抛异常导致 endBatch 没跑到）时，
            // begin 会抛 Already building!。旧写法直接判死：本批次与它之后的
            // 所有批次全静默消失（实机：名牌血条矩形全没了，而先画的名字文字还在，
            // 因为它在坏批次之前就成功了）。改成先结掉残留再试一次。
            flushPendingTesselator(t);
            try {
                bb = m.invoke(t, mode, format);
            } catch (Exception ignored) { /* 仍失败：dead 标记兜底 */ }
        }
    }

    /** 拿不到缓冲：判死并留一行低频取证。
     *  静默不画是最难查的一类故障（不抛异常、控制台干净），所以这里无条件打点。 */
    private void markDead() {
        dead = true;
        long now = System.currentTimeMillis();
        if (now - lastDeadLog > 8000L) {
            lastDeadLog = now;
            com.opendreamcore.client.OdcDebug.log("[ODC-dead] 批次缓冲创建失败，本次几何静默跳过：mode=" + mode
                    + " format=" + format + " modern=" + CompatRender.modernBegin());
        }
    }

    /**
     * 底层缓冲是否可用（false = 本批次不会产出任何几何）。
     *
     * <p>注意：必须先强制一次懒初始化再判断。ensureBb() 是懒的（第一次加顶点时才建），
     * 所以刚 begin() 就问，bb 必然还是 null——之前就是这里误判成「批次不可用」，
     * 把矩形绘制整段跳过（实机血条底/前景永远画不出来）。
     */
    public boolean usable() {
        ensureBb();
        return !dead && bb != null;
    }

    private static long lastDeadLog;

    /**
     * 供世界渲染自建独立批次源用：新建一个自有 ByteBufferBuilder（拿不到返回 null）。
     * 与 tryCreateByteBufferBuilder(int) 同一条路，只是把入口开放出来。
     */
    static Object newByteBufferBuilder(int capacity) {
        try {
            return tryCreateByteBufferBuilder(capacity);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 释放一个自有 ByteBufferBuilder。
     *
     * <p><b>为什么优先用 AutoCloseable.close() 而不是反射找 discard()：</b>
     * ByteBufferBuilder 的 0 参方法有 build / clear / discard / close 四个，而 Fabric 生产环境
     * 方法名是 intermediary，按名必失后「按形状兜底」就是在四个里抽签：
     * 抽中 clear() → resultCount>0 时逐帧刷 "Clearing BufferBuilder with unused batches"；
     * 抽中 build() → 凭空建出一个没人关的 Result（同样是泄漏，resultCount 只增不减）。
     * 而 close() 是 java.lang.AutoCloseable 的接口方法——名字无关、映射无关，且原版实现里
     * close() → discardResults() 会把 resultCount 归零、不报警。所以原则是：
     * 接口优先，名字精确次之，<b>绝不做形状兜底</b>。
     *
     * <p>（1.20.1 无 ByteBufferBuilder，本方法只会在 null 上被调用 → 直接返回。）
     */
    static void releaseBuffer(Object byteBuf) {
        if (byteBuf == null) {
            return;
        }
        // 首选：接口方法，名字无关。close() 会释放其中全部 Result。
        if (byteBuf instanceof AutoCloseable auto) {
            try {
                auto.close();
                return;
            } catch (Throwable ignored) {
                // 落到下面的名字精确兜底
            }
        }
        // 次选：名字精确（dev / NeoForge mojmap 能命中 discard）。
        try {
            byteBuf.getClass().getMethod("discard").invoke(byteBuf);
        } catch (Throwable ignored) {
            // 名字也拿不到（Fabric 生产）→ 置 null 交 GC，不冒险乱调
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
            } catch (Throwable ignored) { /* 候选类不存在/构造失败：换下一候选或返回 null */ }
        }
        return null;
    }

    /**
     * 旧世代（1.20.1）专用：自建 BufferBuilder。这代 BufferBuilder 只有一个 int 容量构造，
     * 缓冲由它自己向 MemoryTracker 申请，没有独立的 ByteBufferBuilder 对象。
     * 严格按「单 int 参数构造」匹配，不接受其它形状——构造选错会在顶点写入时炸在很远的地方。
     */
    private static Object tryCreateOwnBufferBuilder(int capacity) {
        for (String cn : new String[]{
                "com.mojang.blaze3d.vertex.BufferBuilder",
                CompatRender.mapClassName("com.mojang.blaze3d.vertex.BufferBuilder")}) {
            try {
                Class<?> c = Class.forName(cn, false, CompatBuffer.class.getClassLoader());
                for (java.lang.reflect.Constructor<?> ctor : c.getConstructors()) {
                    Class<?>[] ps = ctor.getParameterTypes();
                    if (ps.length == 1 && ps[0] == int.class) {
                        return ctor.newInstance(capacity);
                    }
                }
            } catch (Throwable ignored) { /* 候选名不存在/无单 int 构造：换下一候选 */ }
        }
        return null;
    }

    /**
     * 找「结束并交付批次」的方法：名字优先，失败则按返回类型认。
     * 不能只按参数形状兜底：1.20.1 生产环境方法名是 SRG（end → m_231168_/m_231175_），
     * 按名字查必失，而零参方法里还有 isBuilding() 这类探针，形状匹配会把它们当 end 选中，
     * 结果是批次从未真正结束、builder 永远停在 building 态——这正是 "Already building!" 的成因。
     * 所以兜底必须认「返回值是批次容器」，与名字无关。
     */
    private static Method resolveBuildMethod(Class<?> owner) {
        for (String n : new String[]{"buildOrThrow", "build", "end"}) {
            Method m = CompatRender.resolveMethod(owner, n);
            if (m != null && isBatchContainer(m.getReturnType())) {
                return m;
            }
        }
        for (Method m : owner.getMethods()) {
            if (m.getDeclaringClass() != Object.class
                    && m.getParameterCount() == 0
                    && isBatchContainer(m.getReturnType())) {
                return m;
            }
        }
        // 最后退回纯名字（老行为）：未知世代宁可试一次，也不要在已可用的版本上退化。
        return CompatRender.resolveMethod(owner, "buildOrThrow");
    }

    /** 批次容器类型判定：1.20.1=RenderedBuffer；1.21.x=MeshData；部分快照=BuiltBuffer。 */
    private static boolean isBatchContainer(Class<?> rt) {
        if (rt == null || rt == void.class || rt.isPrimitive()) {
            return false;
        }
        String n = rt.getSimpleName();
        return n.contains("RenderedBuffer") || n.contains("MeshData") || n.contains("BuiltBuffer");
    }

    private static Object tryCreateBufferBuilder(Object byteBuf, Object mode, Object format) {
        // 名走 mapClassName；构造按 (byteBuf类型, mode类型, format类型) 精确形态匹配，
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
                            try { return ctor.newInstance(byteBuf, mode, format); } catch (Throwable ignored) { /* 形态不符：继续试下一构造 */ }
                        }
                    }
                } catch (ClassNotFoundException ignored) { /* 候选类名不存在：换下一候选 */ }
            }
        } catch (Throwable ignored) { /* 全候选失败：返回 null 由调用方兜底 */ }
        return null;
    }

    /** 强结 Tesselator 上可能停着的批次（零参优先，其次 List 重载）。 */
    private static void flushPendingTesselator(Object tesselator) {
        Method zero = null;
        Method withList = null;
        for (Method mm : tesselator.getClass().getMethods()) {
            if (!"endBatch".equals(mm.getName()) && !"end".equals(mm.getName())) {
                continue;
            }
            if (mm.getParameterCount() == 0) {
                zero = mm;
                break;
            }
            if (mm.getParameterCount() == 1 && mm.getParameterTypes()[0] == java.util.List.class) {
                withList = mm;
            }
        }
        try {
            if (zero != null) {
                zero.invoke(tesselator);
                return;
            }
        } catch (Throwable ignored) {
            // 零参重载失败再试 List 重载
        }
        try {
            if (withList != null) {
                withList.invoke(tesselator, java.util.Collections.emptyList());
            }
        } catch (Throwable ignored) {
            // 结不掉就留给上层判死
        }
    }

    /** 结束上一个待决顶点（仅 legacy 需要）。 */
    private void flushPending() {
        if (pendingVertex != null) {
            Method m = CompatRender.resolveMethod(pendingVertex.getClass(), "endVertex");
            if (m != null) {
                try {
                    m.invoke(pendingVertex);
                } catch (Exception ignored) { /* endVertex 失败：后续 build 会校验状态 */ }
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
                applyFixedUv();
                return this;
            }
            // ≥1.21.8 BufferBuilder 不再收矩阵：手工乘一下再走三浮点入口
            // （原先静默丢顶点 → 空 mesh build 失败 → 共享 Tesselator 残留刷警告）
            org.joml.Vector3f p = m.transformPosition(x, y, z, new org.joml.Vector3f());
            return addVertexRaw(p.x, p.y, p.z);
        } catch (Exception ignored) { /* 顶点反射失败：丢弃该顶点，不拖垮帧 */ }
        return this;
    }

    /** 本批次的固定 UV；纯色几何配白纹理时用（不调就由调用方自己写 UV）。 */
    public CompatBuffer fixedUv(float u, float v) {
        fixedUv.fix(u, v);
        return this;
    }

    private void applyFixedUv() {
        if (!fixedUv.isOn() || pendingVertex == null) {
            return;
        }
        applyToPending(new String[]{"setUv", "uv"},
                new Class<?>[]{float.class, float.class},
                new Object[]{fixedUv.u(), fixedUv.v()});
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
                applyFixedUv();
            }
        } catch (Exception ignored) { /* 顶点反射失败：丢弃该顶点 */ }
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
        CompatVertexAttributes.applyTo(pendingVertex, names, types, args);
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
            Method build = resolveBuildMethod(bb.getClass());
            Object mesh = null;
            try { mesh = build == null ? null : build.invoke(bb); } catch (Throwable t) {
                // 任何异常：空缓冲/状态错/版本差异 —— 主动丢弃防 unused batches 泄漏。
                // InvocationTargetException 是反射包装，扒开一层看真实异常才能定位。
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
            // 世界语义路径优先：这批几何挂着世界渲染类型（不写深度/双面/透明）时，
            // 交给渲染类型自己把网格送出去。它是光影认得的可归类路径，比裸着色器上传安全得多；
            // 成功则不再走下面的 BufferUploader 尝试。
            if (worldRenderType != null
                    && com.opendreamcore.client.render.WorldRenderTypeDispatch.draw(worldRenderType, mesh)) {
                try { ((AutoCloseable) mesh).close(); } catch (Throwable ignored) { /* close 失败无补救手段：静默 */ }
                failedAfterDraw = false;
                sweepSelf();
                return;
            }
            // 上传交给 CompatMeshUploader（类名/签名跨代变动集中在那儿）
            boolean drawn = com.opendreamcore.client.render.CompatMeshUploader.draw(mesh);
            if (drawn) {
                // 关键修复：上传成功后必须释放 mesh（BuiltBuffer.close），
                // 否则底层 ByteBufferBuilder 的 Result 永远不释放，帧末 clear 时
                // resultCount>0 → 每帧 "Clearing BufferBuilder with unused batches" 刷屏。
                try { ((AutoCloseable) mesh).close(); } catch (Throwable ignored) { /* close 失败无补救手段：静默 */ }
                failedAfterDraw = false;
                sweepSelf();
                return;
            }
            // 绘制口全落空：1.21.9+ 管线重构后 BufferUploader 已删，
            // 改走 RenderType.draw(MeshData)（原版自刷新路径，新管线兼容）
            if (drawViaRenderType(mesh)) {
                try { ((AutoCloseable) mesh).close(); } catch (Throwable ignored) { /* close 失败无补救手段：静默 */ }
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
                } catch (Throwable ignored) { /* release 也失败：无法再补救，静默 */ }
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

    /**
     * 排空在途批次：1.21.1 的 BufferBuilder 无 discard()，begin 过但未 build 的 builder
     * 直接 clear() 会触发 vanilla "Clearing BufferBuilder with unused batches" 警告刷屏。
     * 先调 build()（空 mesh 不抛；buildOrThrow 会抛所以只作兜底）把在途批次消费掉并释放
     * 返回的 MeshData，building 状态归位，随后的 clear() 即静默。
     * 1.20.1 无 build/buildOrThrow（叫 end，且自带 discard）→ 找不到方法直接返回，无害。
     */
    private static void drainInFlightBatch(Object b) {
        Method m = resolveBuildMethod(b.getClass());
        if (m != null) {
            try {
                Object mesh = m.invoke(b);
                if (mesh instanceof AutoCloseable c) {
                    try { c.close(); } catch (Throwable ignored) { /* mesh 释放失败：静默 */ }
                }
            } catch (Throwable ignored) {
                // 未在 building 状态 / 空 mesh 版本差异：静默，clear() 会接着收尾
            }
        }
    }

    /** 统一丢弃底层缓冲，防 unused batches 泄漏 / 自持缓冲无限增长。 */
    private void safeDiscardAndClear() {
        long now = System.currentTimeMillis();
        if (now - lastDiscardLog > 4000L) {
            lastDiscardLog = now;
            if (Boolean.getBoolean("odc.debug")) {
                com.opendreamcore.client.OdcDebug.log("[ODC-diag] CompatBuffer.safeDiscardAndClear 来源=" + captureCreator()
                        + " 注册表=" + LIVE.size());
            }
        }
        Object b = bb;
        Object byteBuf = this.byteBuf;
        if (b != null) {
            // 先排空在途批次再 discard/clear：1.21.1 无 discard，builder 仍处 building 状态时
            // 直接 clear() 会每帧刷 "Clearing BufferBuilder with unused batches"（1.21.1 实证）。
            drainInFlightBatch(b);
            // 不用 CompatRender.resolveMethod（名字盲形状兜底）：1.21.1 的 BufferBuilder
            // 0 参公开方法只有 build() / buildOrThrow()（无 discard、无 clear），名字查不到时兜底必然
            // 抽中这两个，而调用方把返回值丢掉 → 每帧凭空泄漏一个 MeshData、resultCount 只增不减，
            // 紧接着的 clear() 就必刷 "Clearing BufferBuilder with unused batches"（本警告的真正成因）。
            // 只有「名字精确命中」时才调（dev / NeoForge mojmap：≥1.21.9 的 BufferBuilder.discard）；
            // 在途批次由上面的 drainInFlightBatch 按「返回类型是批次容器」类型驱动地排空，不靠名字。
            try {
                b.getClass().getMethod("discard").invoke(b);
            } catch (Throwable ignored) {
                // 无此方法 / 名字被映射：交给 drainInFlightBatch + 下面的 byteBuf 接口释放收尾
            }
            bb = null;
        }
        // 自有 ByteBufferBuilder（独立分配路径才有）：用接口方法释放，绝不按名字/形状猜方法。
        // （Tesselator 兜底路径 byteBuf==null，不会碰到共享单例。）
        if (byteBuf != null) {
            // 接口方法 close() 收尾：名字无关、不报警（详见 releaseBuffer 的注释）。
            releaseBuffer(byteBuf);
            this.byteBuf = null;
        }
        sweepSelf();
    }

    /**
     * 把本批次挂上世界语义渲染类型：构建出的网格改走渲染类型自刷新路径（光影可归类），
     * 不再依赖立即模式裸着色器上传。由 {@link CompatRender#beginWorld} 调用。
     */
    void useWorldRenderType(Object renderType) {
        this.worldRenderType = renderType;
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
                } catch (ClassNotFoundException ignored) { /* 候选类不存在：换下一候选 */ }
            }
        }
        return null;
    }
}
