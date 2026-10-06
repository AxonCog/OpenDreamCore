package com.opendreamcore.client.render;

import java.lang.reflect.Method;

/**
 * 写入原版 MultiBufferSource 批次（手持/掉落贴图卡片专用，从 CompatRender 迁出）。
 *
 * 立即模式画手持卡片会被后画的手臂模型盖住；把顶点交给调用方的
 * MultiBufferSource（entityTranslucent 层），由原版按阶段顺序统一 flush。
 * 反射按形状解析各代 RenderType 工厂与链式顶点 API（1.21+ addVertex/setColor/setUv…、
 * 1.20.x vertex/color/uv…+endVertex），共享树才能跨 8 代编译。
 */
public final class CompatBatch {

    private CompatBatch() {
    }

    private static volatile Method entityTranslucentMethod;
    private static volatile Method getBufferMethod;

    /** 最近一次 text 批次的格式元素数（<5=无 Normal，调用方据此决定是否传法线）。 */
    public static volatile int lastFormatElements = -1;
    private static volatile boolean diagLogged;
    private static volatile boolean namesLogged;

    private static void diagOnce(String msg) {
        if (SEEN_MSGS.add(msg)) {
            com.opendreamcore.client.OdcDebug.log("[ODC-batch] " + msg);
        }
    }

    private static final java.util.Set<String> SEEN_MSGS =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** 拿到原版批次里的顶点消费者（RenderType.entityTranslucent(rl) → mbs.getBuffer(rt)）。失败返回 null。 */
    /** 定点 flush 指定 rl 的 text 批次（endBatch(RenderType)），找不到就静默跳过。 */
    private static volatile Method endBatchMethod;

    public static void flushBatch(Object mbs, Object rl) {
        if (mbs == null || rl == null) {
            return;
        }
        try {
            Object rt = net.minecraft.client.renderer.RenderType.entityTranslucent(
                    (net.minecraft.resources.ResourceLocation) rl);
            Method m = endBatchMethod;
            if (m == null) {
                for (Method cand : mbs.getClass().getMethods()) {
                    Class<?>[] ps = cand.getParameterTypes();
                    // 纯形状匹配（单参 RT 返 void）：fabric 运行时方法名是 intermediary，
                    // 按名 endBatch 在 fabric 全扑空；Immediate 上该形状唯一
                    if (ps.length == 1 && ps[0].isInstance(rt) && cand.getReturnType() == void.class) {
                        endBatchMethod = m = cand;
                        break;
                    }
                }
            }
            if (m != null) {
                m.invoke(mbs, rt);
            }
        } catch (Throwable ignored) {
            // flush 失败不代表卡片丢失：帧末统一 endBatch 仍会捎带
        }
    }

    public static Object bufferedConsumer(Object mbs, Object rl) {
        if (mbs == null || rl == null) {
            return null;
        }
        try {
            // 顶点写进手臂/物品同一管线的原版 entityTranslucent 批次，帧末统一
            // flush，不自建批次。text 固定批次在手持阶段实测整卡不可见，弃用。
            Object rt = net.minecraft.client.renderer.RenderType.entityTranslucent(
                    (net.minecraft.resources.ResourceLocation) rl);
            if (rt == null) {
                return null;
            }
            lastFormatElements = formatElementCount(rt);
            if (getBufferMethod == null) {
                resolveBufferSource(mbs, rt);
            }
            return getBufferMethod == null ? null : getBufferMethod.invoke(mbs, rt);
        } catch (Throwable t) {
            diagOnce("批次解析/调用异常: " + t);
            return null;
        }
    }

    /** 解析工厂与批次入口（仅首次；工厂是 private static，实测必须 setAccessible）。 */
    private static void resolve(Object mbs, Object rl) {
        synchronized (CompatBatch.class) {
                if (entityTranslucentMethod == null) {
                    // 直接类引用（loom 编译期重映射到各代运行时名）；不能用
                    // Class.forName+mojmap 字符串——fabric 运行时是 intermediary 名。
                    // 同形状工厂很多，且运行时名字全是 method_xxxx，按名字挑
                    // 名字按 contains("text") 匹配不上，
                    // 改为实测：逐个调工厂，反射查返回 RenderType 的 VertexFormat
                    // 元素数，选最少的（text=4：pos+color+uv+light；entity 系≥6
                    // 强制 UV1/UV2/Normal，形状撞车区）。工厂纯函数，调用无副作用。
                    Class<?> rtClass = net.minecraft.client.renderer.RenderType.class;
                    Method best = null;
                    int bestSize = Integer.MAX_VALUE;
                    for (Method m : rtClass.getDeclaredMethods()) {
                        if (!java.lang.reflect.Modifier.isStatic(m.getModifiers())
                                || m.getParameterCount() != 1
                                || m.getParameterTypes()[0] != rl.getClass()
                                || m.getReturnType() != rtClass) {
                            continue;
                        }
                        try {
                            m.setAccessible(true);
                            Object probe = m.invoke(null, rl);
                            if (probe == null) {
                                continue;
                            }
                            int size = formatElementCount(probe);
                            if (size > 0 && size < bestSize) {
                                bestSize = size;
                                best = m;
                            }
                        } catch (Throwable ignored) {
                            // 个别工厂需要 GL 上下文或拒绝该纹理，跳过
                        }
                    }
                    entityTranslucentMethod = best;
                    if (entityTranslucentMethod == null) {
                        diagOnce("RenderType 工厂未命中（形状 static 单参RL→RenderType）");
                    } else {
                        diagOnce("RenderType 工厂选定：格式元素 " + bestSize + " 个");
                    }
                }
                if (entityTranslucentMethod != null && getBufferMethod == null) {
                    for (Method m : mbs.getClass().getMethods()) {
                        if (m.getParameterCount() == 1
                                && entityTranslucentMethod.getReturnType().isAssignableFrom(m.getParameterTypes()[0])
                                && !m.getReturnType().getName().endsWith("void")) {
                            getBufferMethod = m;
                            try {
                                m.setAccessible(true);
                            } catch (Throwable ignored) {
                            }
                            break;
                        }
                    }
                    if (getBufferMethod == null) {
                        diagOnce("getBuffer 未命中（mbs=" + mbs.getClass().getName() + "）");
                    }
                }
        }
    }

    /**
     * 向原版消费者写一张四顶点贴图卡片。坐标已含矩阵变换；任一环失败返回 false。
     * 全程按形状反射，绝不能按方法名：fabric 运行时是 intermediary 名
     * （method_xxxx），按名 getMethod 永远 NoSuchMethod（实机教训）。
     * 顶点入口 = (Matrix4f,float,float,float)→非void；uv = (float,float)→非void；
     * 颜色 = (int×4) 或 (float×4)。入口返回非空对象则在其上续链（1.20.1+ 全链式）；
     * 返回 null 才找消费者上无参 endVertex（更老的链式）。光照/叠加传不进去——
     * 单参 int 方法在运行时无法与其它方法区分（无名字可辨），放弃，
     * 手持卡片全亮可接受。
     */
    /** 每 target 直调胶水（OdcQuadGlue，自家类名不重映射，Class.forName 稳定可达）。 */
    private static volatile Method glueQuad;
    private static volatile boolean glueProbed;

    private static Method glue() {
        if (!glueProbed) {
            glueProbed = true;
            try {
                Class<?> c = Class.forName("com.opendreamcore.client.render.OdcQuadGlue");
                glueQuad = c.getMethod("quad", Object.class, org.joml.Matrix4f.class,
                        float[].class, int.class);
            } catch (Throwable ignored) {
                // 无胶水的 target（legacy / submit 管线）走旧反射循环兜底
            }
        }
        return glueQuad;
    }

    public static boolean quadInto(Object consumer, Object matrix4f, Object normal3f,
                                   float x0, float x1, float y0, float y1,
                                   float u0, float u1, float v0, float v1,
                                   int light, int overlay) {
        Method g = glue();
        if (g != null && matrix4f instanceof org.joml.Matrix4f mat) {
            try {
                Object ok = g.invoke(null, consumer, mat,
                        new float[]{x0, x1, y0, y1, u0, u1, v0, v1}, light);
                if (Boolean.TRUE.equals(ok)) {
                    return true;
                }
                return false;
            } catch (Throwable t) {
                diagOnce("胶水写入异常，退反射: " + t);
            }
        }
        return quadIntoReflect(consumer, matrix4f, normal3f,
                x0, x1, y0, y1, u0, u1, v0, v1, light, overlay);
    }

    private static boolean quadIntoReflect(Object consumer, Object matrix4f, Object normal3f,
                                           float x0, float x1, float y0, float y1,
                                           float u0, float u1, float v0, float v1,
                                           int light, int overlay) {
        if (consumer == null || matrix4f == null) {
            return false;
        }
        try {
            java.util.Map<String, Object> shape = SHAPE_CACHE.get(consumer.getClass());
            if (shape == null) {
                shape = scanShapes(consumer, true);
                SHAPE_CACHE.put(consumer.getClass(), shape);
            }
            Method vtx = (Method) shape.get("vtx"), uvM = (Method) shape.get("uvM"),
                    colI = (Method) shape.get("colI"), colF = (Method) shape.get("colF"),
                    endV = (Method) shape.get("endV"), normM = (Method) shape.get("normM");
            @SuppressWarnings("unchecked")
            java.util.List<Method> intSingles = (java.util.List<Method>) shape.get("intSingles");
            @SuppressWarnings("unchecked")
            java.util.List<Method> intPairs = (java.util.List<Method>) shape.get("intPairs");
            boolean hasNormalMatrix = normal3f != null;
            if (vtx == null || uvM == null || (colI == null && colF == null)) {
                diagOnce("链式 API 形状未命中 vtx=" + (vtx != null) + " uv=" + (uvM != null)
                        + " color=" + (colI != null || colF != null));
                return false;
            }
            // entity 系格式强制 Normal：没抓到 normal 方法就整卡放弃（一个顶点都不写，
            // 回退原版物品；不完整顶点会让 flush 崩溃
            if (normal3f != null && normM == null) {
                diagOnce("normal(Matrix3f,×3) 未命中，放弃卡片走原版");
                return false;
            }
            // text 格式只需 light（单 int）；setNormal 与 addVertex(float×3) 同形状撞车
            // 错抓 addVertex 会 invoke 异常并污染缓冲区，
            // flush 时报 Missing elements）。所以 normal 一律不写，且所有解析通过后
            // 才允许写第一个顶点。
            if (intSingles.isEmpty()) {
                diagOnce("格式元素不齐 单int方法无（light 没法写）");
                return false;
            }
            trySetAccessible(vtx, uvM, colI, colF, endV);
            for (Method m : intSingles) {
                trySetAccessible(m);
            }
            for (Method m : intPairs) {
                trySetAccessible(m);
            }
            org.joml.Matrix4f mat = (org.joml.Matrix4f) matrix4f;
            float[][] pts = {
                    {x1, y0, u1, v1}, // 右下
                    {x0, y0, u0, v1}, // 左下
                    {x0, y1, u0, v0}, // 左上
                    {x1, y1, u1, v0}, // 右上
            };
            for (float[] p : pts) {
                org.joml.Vector4f v = new org.joml.Vector4f(p[0], p[1], 0.0F, 1.0F);
                v.mul(mat);
                Object vertex = vtx.invoke(consumer, mat, v.x(), v.y(), v.z());
                Object target = vertex != null ? vertex : consumer;
                // 每个 setter 写入当前元素并把指针推进一格：顶点的调用次数必须恰等于
                // 格式元素数（position 由 vertex 负责），顺序与格式一致；多写少写都会
                // 把后续顶点冲乱。
                // 元素序：position,color,uv0,[uv1,uv2],normal →
                // color → uv0 → pair×N（第1个=uv1 传 overlay 0，第2个=uv2 传 light）
                // → normal（entity 才有）。intSingles 一律不调（多出的 setColor(int)
                // 会多占一格把后面全冲乱）。
                if (colI != null) {
                    colI.invoke(target, 255, 255, 255, 255);
                } else {
                    colF.invoke(target, 1.0F, 1.0F, 1.0F, 1.0F);
                }
                uvM.invoke(target, p[2], p[3]);
                int pairCount = intPairs.size();
                for (int i = 0; i < pairCount; i++) {
                    if (i == pairCount - 1) {
                        // 末位 pair = uv2/light（1.20.1 无单 int light，必须走这）
                        intPairs.get(i).invoke(target, light & 0xFFFF, (light >>> 16) & 0xFFFF);
                    } else {
                        // 前位 pair = uv1/overlay，NO_OVERLAY
                        intPairs.get(i).invoke(target, 0, 0);
                    }
                }
                if (normM != null) {
                    if (normal3f != null) {
                        normM.invoke(target, normal3f, 0.0F, 0.0F, 1.0F);
                    }
                }
                if (vertex == null && endV != null) {
                    endV.invoke(consumer);
                }
            }
            return true;
        } catch (Throwable t) {
            diagOnce("顶点写入异常: " + t);
            return false;
        }
    }

    /** 查 RenderType 的 VertexFormat 元素数（按形状找 format()/elements() getter，运行时无名字可用）。失败返回 -1。 */
    private static int formatElementCount(Object renderType) {
        try {
            for (Method g : renderType.getClass().getMethods()) {
                if (g.getParameterCount() != 0
                        || g.getReturnType() != com.mojang.blaze3d.vertex.VertexFormat.class) {
                    continue;
                }
                Object fmt = g.invoke(renderType);
                if (fmt == null) {
                    continue;
                }
                for (Method e : fmt.getClass().getMethods()) {
                    if (e.getParameterCount() == 0 && java.util.List.class.isAssignableFrom(e.getReturnType())) {
                        Object list = e.invoke(fmt);
                        return list instanceof java.util.List ? ((java.util.List<?>) list).size() : -1;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return -1;
    }

    private static void trySetAccessible(Method... ms) {
        for (Method m : ms) {
            if (m != null) {
                try {
                    m.setAccessible(true);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /** 手持/掉落物世界侧卡片的朝向修正：位置取原矩阵平移，基向量取主相机右/上方向。
     *  任何一环失败返回 null（调用方回退原矩阵路径）。 */
    public static org.joml.Matrix4f billboardMatrix(org.joml.Matrix4f original) {
        try {
            net.minecraft.client.Camera cam = net.minecraft.client.Minecraft.getInstance()
                    .gameRenderer.getMainCamera();
            if (cam == null || cam.rotation() == null) {
                return null;
            }
            org.joml.Vector3f right = new org.joml.Vector3f(1.0F, 0.0F, 0.0F).rotate(cam.rotation());
            org.joml.Vector3f up = new org.joml.Vector3f(0.0F, 1.0F, 0.0F).rotate(cam.rotation());
            org.joml.Vector3f normal = new org.joml.Vector3f(right).cross(up);
            org.joml.Vector3f t = original.getTranslation(new org.joml.Vector3f());
            return new org.joml.Matrix4f(
                    right.x(), right.y(), right.z(), 0.0F,
                    up.x(), up.y(), up.z(), 0.0F,
                    normal.x(), normal.y(), normal.z(), 0.0F,
                    t.x(), t.y(), t.z(), 1.0F);
        } catch (Throwable t) {
            return null;
        }
    }
    private static final java.util.Map<Class<?>, java.util.Map<String, Object>> SHAPE_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** consumer 形状扫描（按类缓存：每帧每物品扫 getMethods 是卡顿源）。 */
    private static java.util.Map<String, Object> scanShapes(Object consumer, boolean needNormal) {
        Method vtx = null, uvM = null, colI = null, colF = null, endV = null, normM = null;
        java.util.List<Method> intSingles = new java.util.ArrayList<>();
        java.util.List<Method> intPairs = new java.util.ArrayList<>();
        for (Method m : consumer.getClass().getMethods()) {
            Class<?>[] ps = m.getParameterTypes();
            if (needNormal && ps.length == 4
                    && (ps[0].getName().endsWith("Matrix3f") || ps[0].getName().endsWith("Matrix3fc"))
                    && ps[1] == float.class && ps[2] == float.class && ps[3] == float.class) {
                normM = m;
            } else if (ps.length == 4
                    && (ps[0] == org.joml.Matrix4f.class || ps[0].getName().endsWith("Matrix4fc"))
                    && ps[1] == float.class && ps[2] == float.class && ps[3] == float.class
                    && m.getReturnType() != void.class && vtx == null) {
                vtx = m;
            } else if (ps.length == 2 && ps[0] == float.class && ps[1] == float.class
                    && m.getReturnType() != void.class && uvM == null) {
                uvM = m;
            } else if (ps.length == 4 && ps[0] == int.class && ps[1] == int.class
                    && ps[2] == int.class && ps[3] == int.class && colI == null) {
                colI = m;
            } else if (ps.length == 4 && ps[0] == float.class && ps[1] == float.class
                    && ps[2] == float.class && ps[3] == float.class && colF == null) {
                colF = m;
            } else if (ps.length == 0 && m.getReturnType() == void.class && endV == null
                    && m.getName().toLowerCase(java.util.Locale.ROOT).contains("end")) {
                endV = m;
            } else if (ps.length == 3 && ps[0] == float.class && ps[1] == float.class
                    && ps[2] == float.class && normM == null) {
                normM = m;
            } else if (ps.length == 1 && ps[0] == int.class && m.getReturnType() != void.class) {
                intSingles.add(m);
            } else if (ps.length == 2 && ps[0] == int.class && ps[1] == int.class
                    && m.getReturnType() != void.class) {
                intPairs.add(m);
            }
        }
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        map.put("vtx", vtx); map.put("uvM", uvM); map.put("colI", colI);
        map.put("colF", colF); map.put("endV", endV); map.put("normM", normM);
        map.put("intSingles", intSingles); map.put("intPairs", intPairs);
        return map;
    }

    /** 只解析 MultiBufferSource.getBuffer（形状：单参、返回 VertexConsumer）。 */
    private static void resolveBufferSource(Object mbs, Object rt) {
        synchronized (CompatBatch.class) {
            if (getBufferMethod != null) {
                return;
            }
            for (Method m : mbs.getClass().getMethods()) {
                Class<?>[] ps = m.getParameterTypes();
                // 返回类型名不能用于匹配——fabric 运行时是 intermediary（class_4597），
                // 方法名后缀在 intermediary 下不可靠，按形状：
                // 单参 RenderType、返回非 void（getBuffer 与 endBatch(void) 区分）
                if (ps.length == 1 && ps[0].isInstance(rt)
                        && m.getReturnType() != void.class) {
                    getBufferMethod = m;
                    trySetAccessible(m);
                    break;
                }
            }
            if (getBufferMethod == null) {
                diagOnce("getBuffer 形状未命中");
            }
        }
}
}
